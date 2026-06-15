package org.tofu.tofunomics.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.market.MarketItemSerializer;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.models.MarketListing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * マーケット一覧 GUI。出品中（active）の全出品をカテゴリでフィルタし、ページングして表示する。
 * クリックで購入する。クリックイベントの受信は {@link MarketGUIListener} が担当し、
 * 本クラスは描画とクリック処理ロジックを提供する。
 *
 * レイアウト: スロット 0〜7 カテゴリボタン、9〜44 出品（36件/ページ）、45〜53 ナビゲーション。
 */
public class MarketBrowseGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEM_START_SLOT = 9;   // 出品表示の開始スロット（1段目はカテゴリボタン）
    private static final int ITEM_END_SLOT = 44;    // 出品表示の終端スロット（含む）
    private static final int ITEMS_PER_PAGE = ITEM_END_SLOT - ITEM_START_SLOT + 1; // 36
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketListingDAO listingDAO;
    private final MarketGUIListener listener;

    public MarketBrowseGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                           MarketListingDAO listingDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.listingDAO = listingDAO;
        this.listener = listener;
    }

    /**
     * 一覧 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - 出品一覧");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.BROWSE, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("マーケット一覧GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(configManager.getMarketMessage("error"));
        }
    }

    /**
     * 現在ページ・カテゴリの内容を描画する。
     */
    public void render(Player player, MarketGUISession session) {
        Inventory gui = session.getInventory();
        gui.clear();
        session.clearSlotListings();

        List<MarketListing> all;
        try {
            all = listingDAO.getActiveListings();
        } catch (SQLException e) {
            plugin.getLogger().warning("マーケット出品一覧の取得に失敗しました: " + e.getMessage());
            all = new ArrayList<>();
        }

        // カテゴリでフィルタ
        MarketCategory category = session.getCategory();
        List<MarketListing> listings = new ArrayList<>();
        for (MarketListing listing : all) {
            if (category.matches(Material.matchMaterial(listing.getMaterial()))) {
                listings.add(listing);
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, listings.size());

        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            MarketListing listing = listings.get(i);
            gui.setItem(slot, buildDisplayItem(listing));
            session.putSlotListing(slot, listing);
            slot++;
        }

        setupCategoryButtons(gui, category);
        setupNavigation(gui, session, listings.size(), totalPages, all.size());
    }

    /**
     * カテゴリボタンを上段（スロット 0〜7）に配置する。
     */
    private void setupCategoryButtons(Inventory gui, MarketCategory current) {
        MarketCategory[] categories = MarketCategory.values();
        for (int i = 0; i < categories.length && i < 9; i++) {
            MarketCategory category = categories[i];
            boolean selected = category == current;
            gui.setItem(i, MarketGUIUtil.createButton(
                    category.getIcon(),
                    (selected ? "§a§l▶ " : "§f") + category.getDisplayName(),
                    Arrays.asList(
                            selected ? "§a選択中" : "§7クリックで絞り込み"
                    )));
        }
    }

    private void setupNavigation(Inventory gui, MarketGUISession session, int filteredCount,
                                 int totalPages, int totalActive) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, MarketGUIUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, MarketGUIUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, MarketGUIUtil.createButton(Material.BOOK, "§6マーケット情報",
                Arrays.asList(
                        "§fカテゴリ: " + session.getCategory().getDisplayName(),
                        "§f表示中: §a" + filteredCount + " §f/ 全 " + totalActive + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages,
                        "§7アイテムをクリックして購入"
                )));

        gui.setItem(SLOT_CLOSE, MarketGUIUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = MarketGUIUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            // カテゴリボタン段の余り（8）と最下段の空きを装飾で埋める
            if (gui.getItem(8) == null) {
                gui.setItem(8, glass);
            }
            for (int i = 45; i < INVENTORY_SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    /**
     * 出品から表示用アイテムを生成する（実アイテムをデシリアライズし、出品情報を lore に追記）。
     */
    private ItemStack buildDisplayItem(MarketListing listing) {
        ItemStack item = MarketItemSerializer.deserialize(listing.getItemData());
        if (item == null) {
            Material material = Material.matchMaterial(listing.getMaterial());
            if (material == null) {
                material = Material.BARRIER;
            }
            item = new ItemStack(material, Math.max(1, Math.min(listing.getAmount(), 64)));
        } else {
            item = item.clone();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.setDisplayName("§f" + MarketGUIUtil.prettifyMaterial(listing.getMaterial()));
            }
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7出品者: §f" + listing.getSellerName());
            lore.add("§7個数: §f" + listing.getAmount());
            lore.add("§7価格: §e" + MarketGUIUtil.formatPrice(listing.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("");
            lore.add("§eクリックで購入");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * クリック処理（{@link MarketGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, MarketGUISession session, int slot, ClickType clickType) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV) {
            session.setPage(session.getPage() - 1);
            render(player, session);
            return;
        }
        if (slot == SLOT_NEXT) {
            session.setPage(session.getPage() + 1);
            render(player, session);
            return;
        }

        // カテゴリボタン（スロット 0〜7）
        if (slot >= 0 && slot < MarketCategory.values().length && slot <= 8) {
            MarketCategory selected = MarketCategory.values()[slot];
            if (selected != session.getCategory()) {
                session.setCategory(selected);
                session.setPage(0);
                render(player, session);
            }
            return;
        }

        MarketListing listing = session.getListingAtSlot(slot);
        if (listing == null) {
            return;
        }

        MarketResult result = marketManager.purchaseListing(player, listing.getId());
        sendResultMessage(player, result, listing);

        // 在庫が変化したので再描画（売却済みアイテムを一覧から除去）
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketListing listing) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(listing.getMaterial()),
                "price", MarketGUIUtil.formatPrice(listing.getPrice()),
                "currency", configManager.getCurrencyName()));
    }
}
