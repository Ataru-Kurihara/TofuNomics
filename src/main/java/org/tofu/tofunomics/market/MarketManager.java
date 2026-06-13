package org.tofu.tofunomics.market;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketBuyOrderDAO;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.dao.MarketServiceRequestDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.models.MarketBuyOrder;
import org.tofu.tofunomics.models.MarketListing;
import org.tofu.tofunomics.models.MarketServiceRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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
    private final MarketBuyOrderDAO buyOrderDAO;
    private final MarketServiceRequestDAO serviceRequestDAO;
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final Logger logger;

    public MarketManager(Connection connection, PlayerDAO playerDAO, MarketListingDAO listingDAO,
                         MarketBuyOrderDAO buyOrderDAO, MarketServiceRequestDAO serviceRequestDAO,
                         ConfigManager configManager,
                         CurrencyConverter currencyConverter, Logger logger) {
        this.connection = connection;
        this.playerDAO = playerDAO;
        this.listingDAO = listingDAO;
        this.buyOrderDAO = buyOrderDAO;
        this.serviceRequestDAO = serviceRequestDAO;
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
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
     * 購入決済コア（Bukkit 非依存）。手動トランザクションで楽観ロック・出品者への入金・手数料を処理する。
     *
     * 購入者の支払い（現金回収）は Bukkit 依存のため呼び出し側（{@link #purchaseListing}）が行う。
     * 本コアは「楽観ロックで sold 化 → 出品者の bank_balance へ手数料控除後を入金」のみを担当する。
     */
    public PurchaseOutcome executePurchaseTransaction(UUID buyerUuid, int listingId, long nowMillis) {
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

                // 楽観ロック：active の行のみ sold 化。影響行数 1 以外は二重購入として中止
                if (!listingDAO.markAsSold(listingId, buyerUuid, nowMillis)) {
                    connection.rollback();
                    return PurchaseOutcome.failure(MarketResult.ALREADY_SOLD);
                }

                // 出品者へ手数料控除後を入金（オフライン可・UUID 直指定）
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
    // 買い注文（募集）コアロジック（Bukkit 非依存）
    // ========================================================================

    /**
     * 募集の DB 登録コア（Bukkit 非依存）。
     * バリデーション → 募集上限 → INSERT を行う。前払い現金の回収は呼び出し側（ラッパー）。
     *
     * 通貨保存則上、現金回収はこの INSERT より前に行い、INSERT 失敗時は呼び出し側が返金する。
     */
    public MarketResult executeCreateBuyOrder(UUID requesterUuid, String requesterName, String material,
                                              int amount, double price, long nowMillis) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (material == null || amount <= 0) {
            return MarketResult.INVALID_ITEM;
        }
        if (!isValidPrice(price)) {
            return MarketResult.INVALID_PRICE;
        }

        synchronized (connection) {
            try {
                if (buyOrderDAO.countOpenByRequester(requesterUuid) >= configManager.getMarketMaxBuyOrdersPerPlayer()) {
                    return MarketResult.ORDER_LIMIT;
                }
                Long expiresAt = calculateExpiresAtMillis(nowMillis);
                MarketBuyOrder order = new MarketBuyOrder(
                        requesterUuid, requesterName, material, amount, price, expiresAt);
                int id = buyOrderDAO.insertBuyOrder(order);
                return id > 0 ? MarketResult.REQUESTED : MarketResult.ERROR;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "買い注文の登録に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 供給決済コア（Bukkit 非依存）。手動トランザクションで楽観ロック・供給者への入金・手数料を処理する。
     *
     * 供給者の手持ちからのアイテム除去・シリアライズは呼び出し側（{@link #fulfillBuyOrder}）が行い、
     * 本コアは「楽観ロックで fulfilled 化（supplier/item_data 設定）→ 供給者の bank_balance へ
     * 手数料控除後を入金」のみを担当する。差額は再生成しないため自動的に消却される。
     */
    public FulfillOutcome executeFulfillTransaction(UUID supplierUuid, int orderId, String itemData, long nowMillis) {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                MarketBuyOrder order = buyOrderDAO.getById(orderId);
                if (order == null || !order.isOpen()) {
                    connection.rollback();
                    return FulfillOutcome.failure(MarketResult.ORDER_NOT_AVAILABLE);
                }

                // 楽観ロック：open の行のみ fulfilled 化。影響行数 1 以外は二重供給として中止
                if (!buyOrderDAO.markAsFulfilled(orderId, supplierUuid, itemData, nowMillis)) {
                    connection.rollback();
                    return FulfillOutcome.failure(MarketResult.ORDER_NOT_AVAILABLE);
                }

                // 供給者へ手数料控除後を入金（オフライン可・UUID 直指定）。差額は消却。
                long proceeds = calculateSellerProceeds(order.getPrice());
                org.tofu.tofunomics.models.Player supplier = playerDAO.getOrCreatePlayer(supplierUuid);
                supplier.addBankBalance(proceeds);
                playerDAO.updatePlayer(supplier);

                connection.commit();
                return FulfillOutcome.success(order.getRequesterUuid(), proceeds);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "買い注文の供給決済に失敗しました", e);
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "ロールバックに失敗しました", rollbackEx);
                }
                return FulfillOutcome.failure(MarketResult.ERROR);
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
     * 募集キャンセルのステータス遷移コア（Bukkit 非依存）。
     * 所有権・open 状態を検証し、楽観ロックで status を cancelled に遷移させる。
     * 前払い分の返金（現金）は呼び出し側（{@link #cancelBuyOrder}）が遷移成功後に行う。
     */
    public MarketResult executeCancelBuyOrder(UUID requesterUuid, int orderId) {
        synchronized (connection) {
            try {
                MarketBuyOrder order = buyOrderDAO.getById(orderId);
                if (order == null) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                if (!order.getRequesterUuid().equals(requesterUuid)) {
                    return MarketResult.ORDER_NOT_OWNER;
                }
                if (!order.isOpen()) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                if (!buyOrderDAO.updateStatusConditional(orderId, MarketBuyOrder.STATUS_OPEN, MarketBuyOrder.STATUS_CANCELLED)) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                return MarketResult.ORDER_CANCELLED;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "買い注文のキャンセルに失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 成立した募集のアイテム回収のステータス遷移コア（Bukkit 非依存）。
     * 所有権・fulfilled 状態・インベントリ空きを検証し、楽観ロックで status を reclaimed に遷移させる。
     * アイテム付与は呼び出し側（{@link #reclaimBuyOrder}）が遷移成功後に行う。
     */
    public MarketResult executeReclaimBuyOrder(UUID requesterUuid, int orderId, boolean hasInventorySpace) {
        synchronized (connection) {
            try {
                MarketBuyOrder order = buyOrderDAO.getById(orderId);
                if (order == null) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                if (!order.getRequesterUuid().equals(requesterUuid)) {
                    return MarketResult.ORDER_NOT_OWNER;
                }
                if (!order.isFulfilled()) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                if (!hasInventorySpace) {
                    return MarketResult.INVENTORY_FULL;
                }
                if (!buyOrderDAO.updateStatusConditional(orderId, MarketBuyOrder.STATUS_FULFILLED, MarketBuyOrder.STATUS_RECLAIMED)) {
                    return MarketResult.ORDER_NOT_AVAILABLE;
                }
                return MarketResult.ORDER_RECLAIMED;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "買い注文のアイテム回収に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 期限切れの open 募集を expired 化し、前払い分を募集者の bank へ返金する（定期タスクから呼ぶ）。
     *
     * 売りの一括 UPDATE と異なり返金を伴うため、取得 → ループで注文ごとに楽観ロック UPDATE ＋
     * 返金を行う。全体を 1 トランザクションで囲み、途中失敗時はロールバックして二重返金を防ぐ。
     *
     * @return 期限切れ返金した件数（失敗時は 0）
     */
    public int expireBuyOrders() {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);
                long now = System.currentTimeMillis();
                List<MarketBuyOrder> expired = buyOrderDAO.getExpiredOpenOrders(now);
                int count = 0;
                for (MarketBuyOrder order : expired) {
                    // 楽観ロック：open 限定 UPDATE。成功した注文のみ返金（二重返金防止）
                    if (buyOrderDAO.updateStatusConditional(order.getId(),
                            MarketBuyOrder.STATUS_OPEN, MarketBuyOrder.STATUS_EXPIRED)) {
                        org.tofu.tofunomics.models.Player requester = playerDAO.getOrCreatePlayer(order.getRequesterUuid());
                        requester.addBankBalance(order.getPrice());
                        playerDAO.updatePlayer(requester);
                        count++;
                    }
                }
                connection.commit();
                return count;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "期限切れ買い注文の返金処理に失敗しました", e);
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "ロールバックに失敗しました", rollbackEx);
                }
                return 0;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "setAutoCommit(true) に失敗しました", e);
                }
            }
        }
    }

    // ========================================================================
    // サービス依頼（修理・エンチャント募集）コアロジック（Bukkit 非依存部分）
    // ========================================================================

    /**
     * サービス依頼の DB 登録コア。バリデーション → 依頼上限 → INSERT を行う。
     * アイテムのシリアライズ・手持ち除去・前払い回収は呼び出し側が担当する。
     */
    public MarketResult executeCreateServiceRequest(MarketServiceRequest req, long nowMillis) {
        if (!configManager.isMarketEnabled() || !configManager.isMarketServiceEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (req == null || req.getItemData() == null) {
            return MarketResult.INVALID_SERVICE_ITEM;
        }
        if (!isValidPrice(req.getPrice())) {
            return MarketResult.INVALID_PRICE;
        }
        synchronized (connection) {
            try {
                if (serviceRequestDAO.countOpenByRequester(req.getRequesterUuid())
                        >= configManager.getMarketServiceMaxRequestsPerPlayer()) {
                    return MarketResult.SERVICE_LIMIT;
                }
                req.setExpiresAt(calculateExpiresAtMillis(nowMillis));
                int id = serviceRequestDAO.insertServiceRequest(req);
                return id > 0 ? MarketResult.SERVICE_REQUESTED : MarketResult.ERROR;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "サービス依頼の登録に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 加工決済コア（Bukkit 非依存）。手動トランザクションで楽観ロック・worker への入金を処理する。
     * worker のリソース消費・アイテム加工・完成品のシリアライズは呼び出し側が行い、
     * 本コアは「楽観ロックで fulfilled 化（worker/result_item_data 設定）→ worker へ手数料控除後を入金」のみ担当。
     */
    public ServiceFulfillOutcome executeFulfillServiceTransaction(UUID workerUuid, int requestId, String resultItemData, long nowMillis) {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                MarketServiceRequest req = serviceRequestDAO.getById(requestId);
                if (req == null || !MarketServiceRequest.STATUS_OPEN.equals(req.getStatus())) {
                    connection.rollback();
                    return ServiceFulfillOutcome.failure(MarketResult.SERVICE_NOT_AVAILABLE);
                }

                // 楽観ロック：open の行のみ fulfilled 化。影響行数 1 以外は二重成立として中止
                if (!serviceRequestDAO.markAsFulfilled(requestId, workerUuid, resultItemData, nowMillis)) {
                    connection.rollback();
                    return ServiceFulfillOutcome.failure(MarketResult.SERVICE_NOT_AVAILABLE);
                }

                long proceeds = calculateSellerProceeds(req.getPrice());
                org.tofu.tofunomics.models.Player worker = playerDAO.getOrCreatePlayer(workerUuid);
                worker.addBankBalance(proceeds);
                playerDAO.updatePlayer(worker);

                connection.commit();
                return ServiceFulfillOutcome.success(proceeds);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "サービス依頼の加工決済に失敗しました", e);
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "ロールバックに失敗しました", rollbackEx);
                }
                return ServiceFulfillOutcome.failure(MarketResult.ERROR);
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
     * 依頼キャンセルのステータス遷移コア（Bukkit 非依存）。
     * 所有権・open 状態を検証し、楽観ロックで status を cancelled に遷移させる。
     * 道具・前払いの返却は呼び出し側が遷移成功後に行う。
     */
    public MarketResult executeCancelServiceRequest(UUID requesterUuid, int requestId) {
        synchronized (connection) {
            try {
                MarketServiceRequest req = serviceRequestDAO.getById(requestId);
                if (req == null) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                if (!req.getRequesterUuid().equals(requesterUuid)) {
                    return MarketResult.SERVICE_NOT_OWNER;
                }
                if (!MarketServiceRequest.STATUS_OPEN.equals(req.getStatus())) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                if (!serviceRequestDAO.updateStatusConditional(requestId,
                        MarketServiceRequest.STATUS_OPEN, MarketServiceRequest.STATUS_CANCELLED)) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                return MarketResult.SERVICE_CANCELLED;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "サービス依頼のキャンセルに失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 完成品/返却アイテムの回収ステータス遷移コア（Bukkit 非依存）。
     * fulfilled（完成品）または expired（期限切れの元道具）からの回収を許可し、
     * 楽観ロックで status を reclaimed に遷移させる。アイテム付与は呼び出し側が遷移成功後に行う。
     * （cancelled はキャンセル時に道具を即時返却済みのため対象外）
     */
    public MarketResult executeReclaimServiceRequest(UUID requesterUuid, int requestId, boolean hasInventorySpace) {
        synchronized (connection) {
            try {
                MarketServiceRequest req = serviceRequestDAO.getById(requestId);
                if (req == null) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                if (!req.getRequesterUuid().equals(requesterUuid)) {
                    return MarketResult.SERVICE_NOT_OWNER;
                }
                String status = req.getStatus();
                boolean reclaimable = MarketServiceRequest.STATUS_FULFILLED.equals(status)
                        || MarketServiceRequest.STATUS_EXPIRED.equals(status);
                if (!reclaimable) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                if (!hasInventorySpace) {
                    return MarketResult.INVENTORY_FULL;
                }
                if (!serviceRequestDAO.updateStatusConditional(requestId, status, MarketServiceRequest.STATUS_RECLAIMED)) {
                    return MarketResult.SERVICE_NOT_AVAILABLE;
                }
                return MarketResult.SERVICE_RECLAIMED;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "サービス依頼のアイテム回収に失敗しました", e);
                return MarketResult.ERROR;
            }
        }
    }

    /**
     * 期限切れの open サービス依頼を expired 化し、前払い分を依頼者の bank へ返金する（定期タスクから呼ぶ）。
     * 預けた道具は expired のまま残し、依頼者が後で /market reclaimservice で回収する。
     * 全体を 1 トランザクションで囲み、楽観ロック UPDATE 成功時のみ返金して二重返金を防ぐ。
     *
     * @return 期限切れ返金した件数（失敗時は 0）
     */
    public int expireServiceRequests() {
        synchronized (connection) {
            try {
                connection.setAutoCommit(false);
                long now = System.currentTimeMillis();
                List<MarketServiceRequest> expired = serviceRequestDAO.getExpiredOpenRequests(now);
                int count = 0;
                for (MarketServiceRequest req : expired) {
                    if (serviceRequestDAO.updateStatusConditional(req.getId(),
                            MarketServiceRequest.STATUS_OPEN, MarketServiceRequest.STATUS_EXPIRED)) {
                        org.tofu.tofunomics.models.Player requester = playerDAO.getOrCreatePlayer(req.getRequesterUuid());
                        requester.addBankBalance(req.getPrice());
                        playerDAO.updatePlayer(requester);
                        count++;
                    }
                }
                connection.commit();
                return count;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "期限切れサービス依頼の返金処理に失敗しました", e);
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "ロールバックに失敗しました", rollbackEx);
                }
                return 0;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "setAutoCommit(true) に失敗しました", e);
                }
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
     * 出品を購入する。購入者は手持ちの現金（金塊）で支払い、出品者は bank_balance で受け取る。
     *
     * 喪失防止のため、先に現金を回収（チェック＋削除を原子的に実行）してから DB 決済を行い、
     * 決済が失敗した場合は現金を返金する。付与アイテムは {@link #giveItem} が入りきらない分を
     * 足元へドロップするため、インベントリ満杯でも喪失しない。
     */
    public MarketResult purchaseListing(Player buyer, int listingId) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }

        // 価格・自己購入の事前確認（現金を回収する前に弾く）
        MarketListing listing;
        try {
            listing = listingDAO.getById(listingId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "マーケット出品の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (listing == null || !listing.isActive()) {
            return MarketResult.ALREADY_SOLD;
        }
        if (!configManager.isMarketAllowSelfPurchase()
                && listing.getSellerUuid().equals(buyer.getUniqueId())) {
            return MarketResult.NOT_AVAILABLE;
        }

        double price = listing.getPrice();

        // 現金を先に回収（所持チェックと削除を原子的に行う）。不足なら資金不足
        if (!currencyConverter.payWithCash(buyer, price)) {
            return MarketResult.INSUFFICIENT_FUNDS;
        }

        // DB 決済（楽観ロック＋出品者入金）
        PurchaseOutcome outcome = executePurchaseTransaction(
                buyer.getUniqueId(), listingId, System.currentTimeMillis());

        if (!outcome.isSuccess()) {
            // 売却失敗 → 回収した現金を返金（スペースチェックは不要＝直前に同額を取り除いている）
            currencyConverter.receiveCash(buyer, price, true);
            return outcome.getResult();
        }

        giveItem(buyer, outcome.getItemData());
        notifySellerSold(outcome, org.tofu.tofunomics.market.gui.MarketGUIUtil.prettifyMaterial(listing.getMaterial()));
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
    // 買い注文（募集）Bukkit 依存ラッパー
    // ========================================================================

    /**
     * 買い注文（募集）を登録する。前払いの現金（金塊）を先に回収し、DB 登録失敗時は返金する。
     *
     * 安全順序：募集上限の事前確認 → 現金回収 → INSERT。INSERT が失敗した場合は現金を返金する。
     */
    public MarketResult createBuyOrder(Player requester, Material material, int amount, double price) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (material == null || material == Material.AIR || amount <= 0) {
            return MarketResult.INVALID_ITEM;
        }
        if (!isValidPrice(price)) {
            return MarketResult.INVALID_PRICE;
        }

        // 現金を回収する前に募集上限を確認する（無駄な回収→返金の往復を避ける）
        synchronized (connection) {
            try {
                if (buyOrderDAO.countOpenByRequester(requester.getUniqueId())
                        >= configManager.getMarketMaxBuyOrdersPerPlayer()) {
                    return MarketResult.ORDER_LIMIT;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "募集数の確認に失敗しました", e);
                return MarketResult.ERROR;
            }
        }

        // 前払いの現金を先に回収（所持チェックと削除を原子的に行う）。不足なら資金不足
        if (!currencyConverter.payWithCash(requester, price)) {
            return MarketResult.INSUFFICIENT_FUNDS;
        }

        MarketResult result = executeCreateBuyOrder(
                requester.getUniqueId(), requester.getName(), material.name(), amount,
                price, System.currentTimeMillis());

        if (result != MarketResult.REQUESTED) {
            // 登録失敗 → 回収した前払いを返金（スペースチェックは不要＝直前に同額を取り除いている）
            currencyConverter.receiveCash(requester, price, true);
        }
        return result;
    }

    /**
     * 募集に供給して成立させる。手持ちから対象 Material を amount 個除去し、
     * DB 決済（楽観ロック＋供給者入金）を行う。決済失敗時は除去したアイテムを返却する。
     */
    public MarketResult fulfillBuyOrder(Player supplier, int orderId) {
        if (!configManager.isMarketEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }

        MarketBuyOrder order;
        try {
            order = buyOrderDAO.getById(orderId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "買い注文の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (order == null || !order.isOpen()) {
            return MarketResult.ORDER_NOT_AVAILABLE;
        }
        // 自己供給チェック（自分の募集には供給不可）
        if (!configManager.isMarketAllowSelfPurchase()
                && order.getRequesterUuid().equals(supplier.getUniqueId())) {
            return MarketResult.ORDER_NOT_AVAILABLE;
        }

        Material material = Material.matchMaterial(order.getMaterial());
        if (material == null) {
            logger.warning("買い注文の Material 名が不正です: " + order.getMaterial());
            return MarketResult.ERROR;
        }
        int amount = order.getAmount();

        // 手持ちに供給可能な個数があるか
        if (countMaterial(supplier, material) < amount) {
            return MarketResult.NO_MATCHING_ITEM;
        }

        // 手持ちから除去し、供給アイテムをシリアライズ（除去 → serialize → DB の安全順序）
        removeMaterial(supplier, material, amount);
        ItemStack supplied = new ItemStack(material, amount);
        String itemData = MarketItemSerializer.serialize(supplied);
        if (itemData == null) {
            // シリアライズ失敗 → 除去したアイテムを返却して中止
            giveItemStack(supplier, supplied);
            return MarketResult.ERROR;
        }

        FulfillOutcome outcome = executeFulfillTransaction(
                supplier.getUniqueId(), orderId, itemData, System.currentTimeMillis());

        if (!outcome.isSuccess()) {
            // 決済失敗 → 除去したアイテムを返却
            giveItem(supplier, itemData);
            return outcome.getResult();
        }

        notifyRequesterFulfilled(outcome.getRequesterUuid(),
                org.tofu.tofunomics.market.gui.MarketGUIUtil.prettifyMaterial(order.getMaterial()), amount);
        return outcome.getResult();
    }

    /**
     * open の募集をキャンセルし、前払い分を現金で返金する。
     */
    public MarketResult cancelBuyOrder(Player requester, int orderId) {
        MarketBuyOrder order;
        try {
            order = buyOrderDAO.getById(orderId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "買い注文の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (order == null) {
            return MarketResult.ORDER_NOT_AVAILABLE;
        }

        MarketResult result = executeCancelBuyOrder(requester.getUniqueId(), orderId);

        if (result == MarketResult.ORDER_CANCELLED) {
            // 状態遷移成功後に前払いを返金（本人操作＝オンライン前提）
            currencyConverter.receiveCash(requester, order.getPrice(), true);
        }
        return result;
    }

    /**
     * fulfilled の募集から供給アイテムを回収する。
     */
    public MarketResult reclaimBuyOrder(Player requester, int orderId) {
        MarketBuyOrder order;
        try {
            order = buyOrderDAO.getById(orderId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "買い注文の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (order == null) {
            return MarketResult.ORDER_NOT_AVAILABLE;
        }

        boolean hasSpace = requester.getInventory().firstEmpty() != -1;
        MarketResult result = executeReclaimBuyOrder(requester.getUniqueId(), orderId, hasSpace);

        if (result == MarketResult.ORDER_RECLAIMED) {
            giveItem(requester, order.getItemData());
        }
        return result;
    }

    /**
     * 手持ちの道具を修理依頼として登録する（道具と前払い報酬をエスクロー）。
     */
    public MarketResult createRepairRequest(Player requester, double price) {
        if (!configManager.isMarketEnabled() || !configManager.isMarketServiceEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (!isValidPrice(price)) {
            return MarketResult.INVALID_PRICE;
        }
        ItemStack item = requester.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return MarketResult.INVALID_SERVICE_ITEM;
        }
        if (!ServiceProcessor.isRepairable(item)) {
            return MarketResult.INVALID_SERVICE_ITEM;
        }
        String itemData = MarketItemSerializer.serialize(item);
        if (itemData == null) {
            return MarketResult.ERROR;
        }
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester.getUniqueId(), requester.getName(), item.getType().name(), itemData, price);
        return submitServiceRequest(requester, req, price);
    }

    /**
     * 手持ちの道具をエンチャント依頼として登録する（道具と前払い報酬をエスクロー）。
     */
    public MarketResult createEnchantRequest(Player requester, String enchantKey, int level, double price) {
        if (!configManager.isMarketEnabled() || !configManager.isMarketServiceEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }
        if (!isValidPrice(price)) {
            return MarketResult.INVALID_PRICE;
        }
        if (level <= 0 || level > configManager.getMarketServiceMaxEnchantLevel()) {
            return MarketResult.INVALID_ENCHANT;
        }
        Enchantment enchantment = ServiceProcessor.resolveEnchantment(enchantKey);
        if (enchantment == null
                || !ServiceProcessor.isEnchantAllowed(enchantKey, configManager.getMarketServiceAllowedEnchantments())) {
            return MarketResult.INVALID_ENCHANT;
        }
        ItemStack item = requester.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return MarketResult.INVALID_SERVICE_ITEM;
        }
        String itemData = MarketItemSerializer.serialize(item);
        if (itemData == null) {
            return MarketResult.ERROR;
        }
        // 正規化された minecraft key で保存する
        String canonicalKey = enchantment.getKey().getKey();
        MarketServiceRequest req = MarketServiceRequest.createEnchant(
                requester.getUniqueId(), requester.getName(), item.getType().name(),
                itemData, canonicalKey, level, price);
        return submitServiceRequest(requester, req, price);
    }

    /**
     * サービス依頼の共通登録処理。依頼上限の事前チェック → 前払い回収 → DB 登録 →
     * 成功時に手持ちの道具を除去、失敗時に前払いを返金する。
     */
    private MarketResult submitServiceRequest(Player requester, MarketServiceRequest req, double price) {
        synchronized (connection) {
            try {
                if (serviceRequestDAO.countOpenByRequester(requester.getUniqueId())
                        >= configManager.getMarketServiceMaxRequestsPerPlayer()) {
                    return MarketResult.SERVICE_LIMIT;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "依頼数の確認に失敗しました", e);
                return MarketResult.ERROR;
            }
        }

        if (!currencyConverter.payWithCash(requester, price)) {
            return MarketResult.INSUFFICIENT_FUNDS;
        }

        MarketResult result = executeCreateServiceRequest(req, System.currentTimeMillis());

        if (result == MarketResult.SERVICE_REQUESTED) {
            // DB 登録成功後に手持ちの道具を除去（喪失防止：DB に確実に保存してから除去）
            requester.getInventory().setItemInMainHand(null);
        } else {
            // 登録失敗 → 回収した前払いを返金
            currencyConverter.receiveCash(requester, price, true);
        }
        return result;
    }

    /**
     * サービス依頼を引き受けて加工を成立させる（システム自動代行）。
     * worker が必要リソース（経験値・ラピス）を所持していれば、システムが仮想加工して完成品を保管する。
     * リソース消費は DB コミット成功後に行う（アイテムは常にシステム保管のため不正取得は不可能）。
     */
    public MarketResult fulfillServiceRequest(Player worker, int requestId) {
        if (!configManager.isMarketEnabled() || !configManager.isMarketServiceEnabled()) {
            return MarketResult.MARKET_DISABLED;
        }

        MarketServiceRequest req;
        try {
            req = serviceRequestDAO.getById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "サービス依頼の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (req == null || !MarketServiceRequest.STATUS_OPEN.equals(req.getStatus())) {
            return MarketResult.SERVICE_NOT_AVAILABLE;
        }
        // 自己成立チェック（自分の依頼は引き受け不可）
        if (!configManager.isMarketAllowSelfPurchase()
                && req.getRequesterUuid().equals(worker.getUniqueId())) {
            return MarketResult.SERVICE_NOT_AVAILABLE;
        }

        // 預かりアイテムを復元
        ItemStack original = MarketItemSerializer.deserialize(req.getItemData());
        if (original == null) {
            logger.warning("サービス依頼の item_data デシリアライズに失敗しました（id=" + requestId + "）");
            return MarketResult.ERROR;
        }

        // 加工と必要リソースの算出
        int expCost;
        int lapisCost = 0;
        ItemStack processed;
        if (req.isRepair()) {
            expCost = configManager.getMarketServiceRepairExpCost();
            processed = ServiceProcessor.applyRepair(original);
            if (processed == null) {
                return MarketResult.INVALID_SERVICE_ITEM;
            }
        } else if (req.isEnchant()) {
            Enchantment enchantment = ServiceProcessor.resolveEnchantment(req.getEnchantType());
            if (enchantment == null) {
                return MarketResult.INVALID_ENCHANT;
            }
            int level = req.getEnchantLevel() != null ? req.getEnchantLevel() : 1;
            expCost = configManager.getMarketServiceEnchantExpCostPerLevel() * level;
            lapisCost = configManager.getMarketServiceEnchantLapisCost();
            processed = ServiceProcessor.applyEnchant(original, enchantment, level);
            if (processed == null) {
                return MarketResult.INVALID_SERVICE_ITEM;
            }
        } else {
            return MarketResult.ERROR;
        }

        // リソース所持チェック（消費は DB コミット成功後）
        if (worker.getLevel() < expCost) {
            return MarketResult.INSUFFICIENT_RESOURCES;
        }
        if (lapisCost > 0 && countMaterial(worker, Material.LAPIS_LAZULI) < lapisCost) {
            return MarketResult.INSUFFICIENT_RESOURCES;
        }

        String resultData = MarketItemSerializer.serialize(processed);
        if (resultData == null) {
            return MarketResult.ERROR;
        }

        ServiceFulfillOutcome outcome = executeFulfillServiceTransaction(
                worker.getUniqueId(), requestId, resultData, System.currentTimeMillis());

        if (!outcome.isSuccess()) {
            return outcome.getResult();
        }

        // 成立後に worker のリソースを消費する
        worker.setLevel(Math.max(0, worker.getLevel() - expCost));
        if (lapisCost > 0) {
            removeMaterial(worker, Material.LAPIS_LAZULI, lapisCost);
        }

        notifyRequesterServiceFulfilled(req);
        return outcome.getResult();
    }

    /**
     * open のサービス依頼をキャンセルし、預けた道具と前払い分を即時返却する（依頼者本人＝オンライン前提）。
     */
    public MarketResult cancelServiceRequest(Player requester, int requestId) {
        MarketServiceRequest req;
        try {
            req = serviceRequestDAO.getById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "サービス依頼の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (req == null) {
            return MarketResult.SERVICE_NOT_AVAILABLE;
        }

        MarketResult result = executeCancelServiceRequest(requester.getUniqueId(), requestId);

        if (result == MarketResult.SERVICE_CANCELLED) {
            // 状態遷移成功後に道具を返却し、前払いを返金
            giveItem(requester, req.getItemData());
            currencyConverter.receiveCash(requester, req.getPrice(), true);
        }
        return result;
    }

    /**
     * 完成品（fulfilled）または期限切れの元道具（expired）を回収する。
     */
    public MarketResult reclaimServiceRequest(Player requester, int requestId) {
        MarketServiceRequest req;
        try {
            req = serviceRequestDAO.getById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "サービス依頼の取得に失敗しました", e);
            return MarketResult.ERROR;
        }
        if (req == null) {
            return MarketResult.SERVICE_NOT_AVAILABLE;
        }

        boolean hasSpace = requester.getInventory().firstEmpty() != -1;
        String statusBefore = req.getStatus();
        MarketResult result = executeReclaimServiceRequest(requester.getUniqueId(), requestId, hasSpace);

        if (result == MarketResult.SERVICE_RECLAIMED) {
            // fulfilled → 完成品、expired → 預けた元道具
            String data = MarketServiceRequest.STATUS_FULFILLED.equals(statusBefore)
                    ? req.getResultItemData() : req.getItemData();
            giveItem(requester, data);
        }
        return result;
    }

    /**
     * 依頼者がオンラインなら加工完了を通知する。
     */
    private void notifyRequesterServiceFulfilled(MarketServiceRequest req) {
        Player requester = Bukkit.getPlayer(req.getRequesterUuid());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(configManager.getMarketMessage("service_fulfilled_notify",
                    "service", serviceLabel(req),
                    "item", org.tofu.tofunomics.market.gui.MarketGUIUtil.prettifyMaterial(req.getMaterial())));
        }
    }

    /**
     * サービス種別の表示ラベル（%service% 用）。
     */
    private String serviceLabel(MarketServiceRequest req) {
        return req.isRepair() ? "修理" : "エンチャント";
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
     *
     * @param itemName 売れたアイテムの表示名（%item% 用）
     */
    private void notifySellerSold(PurchaseOutcome outcome, String itemName) {
        Player seller = Bukkit.getPlayer(outcome.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(configManager.getMarketMessage("sold_notify",
                    "item", itemName,
                    "amount", String.valueOf(outcome.getSellerProceeds()),
                    "currency", configManager.getCurrencyName()));
        }
    }

    /**
     * 単一の ItemStack を付与する。入りきらない分は足元へドロップする（喪失防止）。
     */
    private void giveItemStack(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            Location loc = player.getLocation();
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItem(loc, drop);
            }
        }
    }

    /**
     * インベントリ内の指定 Material の合計個数を数える（NBT 無視・Material のみで集計）。
     */
    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * インベントリから指定 Material を合計 amount 個だけ除去する。
     * 呼び出し前に {@link #countMaterial} で所持数を確認していることを前提とする。
     */
    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    /**
     * 募集者がオンラインなら供給成立を通知する。
     *
     * @param itemName 供給されたアイテムの表示名（%item% 用）
     * @param amount   供給個数（%amount% 用）
     */
    private void notifyRequesterFulfilled(UUID requesterUuid, String itemName, int amount) {
        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(configManager.getMarketMessage("fulfilled_notify",
                    "item", itemName,
                    "amount", String.valueOf(amount)));
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
