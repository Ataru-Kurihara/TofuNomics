package org.tofu.tofunomics.market;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.models.MarketListing;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * プレイヤー間マーケットのビジネスロジック層。
 *
 * 出品・購入・キャンセル・回収の各操作を提供する。Bukkit（Player/ItemStack/インベントリ）
 * 依存の薄いラッパーと、ユニットテスト可能な DB 決済コアを分離している。
 *
 * 購入・回収の DB トランザクションは {@code synchronized(connection)} で囲み、
 * 共有 Connection への非同期書き込み（PlayerJoinHandler 等）との競合を防ぐ（Risk B 対策）。
 */
public class MarketManager {

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private final Connection connection;
    private final PlayerDAO playerDAO;
    private final MarketListingDAO listingDAO;
    private final ConfigManager configManager;
    private final Logger logger;

    public MarketManager(Connection connection, PlayerDAO playerDAO, MarketListingDAO listingDAO,
                         ConfigManager configManager, Logger logger) {
        this.connection = connection;
        this.playerDAO = playerDAO;
        this.listingDAO = listingDAO;
        this.configManager = configManager;
        this.logger = logger;
    }

    // ========================================================================
    // テスト可能なコアロジック（Bukkit 非依存）
    // ========================================================================

    /**
     * 出品価格が妥当か（整数かつ min_price 〜 max_price の範囲内）。
     */
    public boolean isValidPrice(double price) {
        if (price != Math.floor(price) || Double.isInfinite(price)) {
            return false;
        }
        return price >= configManager.getMarketMinPrice() && price <= configManager.getMarketMaxPrice();
    }

    /**
     * 出品者の受取額（手数料控除後・端数切り捨て）を計算する。
     */
    public long calculateSellerProceeds(double price) {
        return (long) Math.floor(price * (1.0 - configManager.getMarketFeeRate()));
    }

    /**
     * 失効時刻（epoch millis）を計算する。listing_duration_days が 0 以下なら無期限（null）。
     */
    private Long calculateExpiresAtMillis(long nowMillis) {
        int days = configManager.getMarketListingDurationDays();
        if (days <= 0) {
            return null;
        }
        return nowMillis + days * MILLIS_PER_DAY;
    }

    /**
     * 出品の DB 登録コア（Bukkit 非依存）。
     * バリデーション → 出品上限 → INSERT を行う。アイテムのシリアライズ・手持ち除去は呼び出し側。
     */
    public MarketResult executeCreateListing(UUID sellerUuid, String sellerName, String itemData,
                                             String displayName, String material, int amount,
                                             double price, long nowMillis) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (itemData == null) {
            return MarketResult.INVALID_ITEM;
        }
        if (!isValidPrice(price)) {
            return MarketResult.INVALID_PRICE;
        }

        synchronized (connection) {
            try {
                if (listingDAO.countActiveBySeller(sellerUuid) >= configManager.getMarketMaxListingsPerPlayer()) {
                    return MarketResult.LISTING_LIMIT;
                }
                Long expiresAt = calculateExpiresAtMillis(nowMillis);
                MarketListing listing = new MarketListing(
                        sellerUuid, sellerName, itemData, displayName, material, amount, price, expiresAt);
                int id = listingDAO.insertListing(listing);
                return id > 0 ? MarketResult.LISTED : MarketResult.ERROR;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "マーケット出品の登録に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 購入決済コア（Bukkit 非依存）。手動トランザクションで楽観ロック・残高移動・手数料を処理する。
     *
     * @param buyerHasInventorySpace 購入者インベントリに空きがあるか（呼び出し側が判定して渡す）
     */
    public PurchaseOutcome executePurchaseTransaction(UUID buyerUuid, int listingId,
                                                      boolean buyerHasInventorySpace, long nowMillis) {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                MarketListing listing = listingDAO.getById(listingId);
                if (listing == null || !listing.isActive()) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.ALREADY_SOLD);
                }

                UUID sellerUuid = listing.getSellerUuid();

                // 自己購入チェック
                if (!configManager.isMarketAllowSelfPurchase() && sellerUuid.equals(buyerUuid)) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.NOT_AVAILABLE);
                }

                // 残高チェック
                org.tofu.tofunomics.models.Player buyer = playerDAO.getPlayer(buyerUuid);
                if (buyer == null || buyer.getBankBalance() < listing.getPrice()) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.INSUFFICIENT_FUNDS);
                }

                // インベントリ空きチェック（満杯なら購入させない＝アイテム喪失防止）
                if (!buyerHasInventorySpace) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.INVENTORY_FULL);
                }

                // 楽観ロック：active の行のみ sold 化。影響行数 1 以外は二重購入として中止
                if (!listingDAO.markAsSold(listingId, buyerUuid, nowMillis)) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.ALREADY_SOLD);
                }

                // 購入者から代金を引き、出品者へ手数料控除後を入金
                buyer.removeBankBalance(listing.getPrice());
                playerDAO.updatePlayer(buyer);

                long proceeds = calculateSellerProceeds(listing.getPrice());
                org.tofu.tofunomics.models.Player seller = playerDAO.getOrCreatePlayer(sellerUuid);
                seller.addBankBalance(proceeds);
                playerDAO.updatePlayer(seller);

                connection.commit();
                return PurchaseOutcome.success(listing.getItemData(), sellerUuid, proceeds);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "マーケット購入の決済に失敗しました", e);
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "ロールバックに失敗しました", rollbackEx);
                }
                return PurchaseOutcome.failure(MarketResult.ERROR);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "setAutoCommit(true) に失敗しました", e);
                }
            }
        }
    }

    /**
     * キャンセル/回収のステータス遷移コア（Bukkit 非依存）。
     * 所有権・期待ステータス・インベントリ空きを検証し、楽観ロックで status を reclaimed に遷移させる。
     *
     * @param expectedStatus active（キャンセル）または expired（回収）
     */
    public MarketResult executeReclaim(UUID sellerUuid, int listingId, String expectedStatus,
                                       boolean hasInventorySpace) {
        synchronized (connection) {
            try {
                MarketListing listing = listingDAO.getById(listingId);
                if (listing == null) {
                    return MarketResult.NOT_AVAILABLE;
                }
                if (!listing.getSellerUuid().equals(sellerUuid)) {
                    return MarketResult.NOT_OWNER;
                }
                if (!expectedStatus.equals(listing.getStatus())) {
                    return MarketResult.NOT_AVAILABLE;
                }
                if (!hasInventorySpace) {
                    return MarketResult.INVENTORY_FULL;
                }
                if (!listingDAO.updateStatusConditional(listingId, expectedStatus, MarketListing.STATUS_RECLAIMED)) {
                    return MarketResult.NOT_AVAILABLE;
                }
                return MarketListing.STATUS_ACTIVE.equals(expectedStatus)
                        ? MarketResult.CANCELLED : MarketResult.RECLAIMED;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "マーケット出品の回収に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 期限切れの active 出品を一括で expired 化する（定期タスクから呼ぶ薄いラッパー）。
     * 共有 Connection への非同期書き込みとの競合を防ぐため {@code synchronized(connection)} で囲む。
     *
     * @return 期限切れ化した件数（失敗時は 0）
     */
    public int expireListings() {
        synchronized (connection) {
            try {
                return listingDAO.expireActiveListings(System.currentTimeMillis());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "期限切れ出品の一括処理に失敗しました", e);
                return 0;
            }
        }
    }

    // ========================================================================
    // Bukkit 依存のラッパー（コマンド/GUI から呼ぶ）
    // ========================================================================

    /**
     * メインハンドのアイテムを出品する。DB 登録成功後に手持ちから除去する（喪失防止のため順序厳守）。
     */
    public MarketResult createListing(Player seller, double price) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }

        ItemStack item = seller.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return MarketResult.INVALID_ITEM;
        }

        String itemData = MarketItemSerializer.serialize(item);
        if (itemData == null) {
            return MarketResult.ERROR;
        }

        String displayName = resolveDisplayName(item);
        String material = item.getType().name();
        int amount = item.getAmount();

        MarketResult result = executeCreateListing(
                seller.getUniqueId(), seller.getName(), itemData, displayName, material, amount,
                price, System.currentTimeMillis());

        if (result == MarketResult.LISTED) {
            // DB 保存成功を確認してから手持ちを除去する
            seller.getInventory().setItemInMainHand(null);
        }
        return result;
    }

    /**
     * 出品を購入する。決済成功後にアイテムを付与し、出品者がオンラインなら通知する。
     */
    public MarketResult purchaseListing(Player buyer, int listingId) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }

        boolean hasSpace = buyer.getInventory().firstEmpty() != -1;
        PurchaseOutcome outcome = executePurchaseTransaction(
                buyer.getUniqueId(), listingId, hasSpace, System.currentTimeMillis());

        if (outcome.isSuccess()) {
            giveItem(buyer, outcome.getItemData());
            notifySellerSold(outcome);
        }
        return outcome.getResult();
    }

    /**
     * active 出品をキャンセルし、アイテムを回収する。
     */
    public MarketResult cancelListing(Player seller, int listingId) {
        return reclaimInternal(seller, listingId, MarketListing.STATUS_ACTIVE);
    }

    /**
     * expired 出品からアイテムを回収する。
     */
    public MarketResult reclaimListing(Player seller, int listingId) {
        return reclaimInternal(seller, listingId, MarketListing.STATUS_EXPIRED);
    }

    private MarketResult reclaimInternal(Player seller, int listingId, String expectedStatus) {
        MarketListing listing;
        try {
            listing = listingDAO.getById(listingId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "マーケット出品の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (listing == null) {
            return MarketResult.NOT_AVAILABLE;
        }

        boolean hasSpace = seller.getInventory().firstEmpty() != -1;
        MarketResult result = executeReclaim(seller.getUniqueId(), listingId, expectedStatus, hasSpace);

        if (result == MarketResult.CANCELLED || result == MarketResult.RECLAIMED) {
            giveItem(seller, listing.getItemData());
        }
        return result;
    }

    // ========================================================================
    // ヘルパー
    // ========================================================================

    /**
     * アイテムを付与する。インベントリに入りきらない分はプレイヤー足元にドロップする（喪失防止）。
     */
    private void giveItem(Player player, String itemData) {
        ItemStack item = MarketItemSerializer.deserialize(itemData);
        if (item == null) {
            logger.warning("マーケットアイテムのデシリアライズに失敗しました（listingのitem_dataが不正）");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            Location loc = player.getLocation();
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItem(loc, drop);
            }
        }
    }

    /**
     * 出品者がオンラインなら売却を通知する。
     */
    private void notifySellerSold(PurchaseOutcome outcome) {
        Player seller = Bukkit.getPlayer(outcome.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(configManager.getMarketMessage("sold_notify",
                    "amount", outcome.getSellerProceeds()));
        }
    }

    /**
     * GUI 表示用の名前を解決する（カスタム名があればそれ、無ければ Material 名）。
     */
    private String resolveDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }
}
