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
import org.tofu.tofunomics.dao.MarketBuyOrderDAO;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.models.MarketBuyOrder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 募集（買い注文）一覧 GUI。募集中（open）の全募集をカテゴリでフィルタし、ページングして表示する。
 * クリックで手持ちから供給して成立させる。クリックイベントの受信は {@link MarketGUIListener} が担当し、
 * 本クラスは描画とクリック処理ロジックを提供する。
 *
 * レイアウト: スロット 0〜7 カテゴリボタン、9〜44 募集（36件/ページ）、45〜53 ナビゲーション。
 * （売り一覧 {@link MarketBrowseGUI} の鏡像）
 */
public class BuyOrderBrowseGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEM_START_SLOT = 9;
    private static final int ITEM_END_SLOT = 44;
    private static final int ITEMS_PER_PAGE = ITEM_END_SLOT - ITEM_START_SLOT + 1; // 36
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketBuyOrderDAO buyOrderDAO;
    private final MarketGUIListener listener;

    public BuyOrderBrowseGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                             MarketBuyOrderDAO buyOrderDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.buyOrderDAO = buyOrderDAO;
        this.listener = listener;
    }

    /**
     * 募集一覧 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - 募集一覧");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.BUY_BROWSE, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("募集一覧GUIの表示に失敗しました: " + e.getMessage());
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

        List<MarketBuyOrder> all;
        try {
            all = buyOrderDAO.getOpenOrders();
        } catch (SQLException e) {
            plugin.getLogger().warning("募集一覧の取得に失敗しました: " + e.getMessage());
            all = new ArrayList<>();
        }

        // カテゴリでフィルタ
        MarketCategory category = session.getCategory();
        List<MarketBuyOrder> orders = new ArrayList<>();
        for (MarketBuyOrder order : all) {
            if (category.matches(Material.matchMaterial(order.getMaterial()))) {
                orders.add(order);
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil(orders.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, orders.size());

        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            MarketBuyOrder order = orders.get(i);
            gui.setItem(slot, buildDisplayItem(order));
            session.putSlotBuyOrder(slot, order);
            slot++;
        }

        setupCategoryButtons(gui, category);
        setupNavigation(gui, session, orders.size(), totalPages, all.size());
    }

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
                                 int totalPages, int totalOpen) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, MarketGUIUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, MarketGUIUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, MarketGUIUtil.createButton(Material.WRITABLE_BOOK, "§6募集情報",
                Arrays.asList(
                        "§fカテゴリ: " + session.getCategory().getDisplayName(),
                        "§f表示中: §a" + filteredCount + " §f/ 全 " + totalOpen + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages,
                        "§7募集をクリックして手持ちから供給"
                )));

        gui.setItem(SLOT_CLOSE, MarketGUIUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = MarketGUIUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
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
     * 募集から表示用アイテムを生成する（募集対象 Material の見本アイテムに募集情報を lore で付す）。
     */
    private ItemStack buildDisplayItem(MarketBuyOrder order) {
        Material material = Material.matchMaterial(order.getMaterial());
        if (material == null) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(order.getAmount(), 64)));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + MarketGUIUtil.prettifyMaterial(order.getMaterial()));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7募集者: §f" + order.getRequesterName());
            lore.add("§7希望個数: §f" + order.getAmount());
            lore.add("§7支払総額: §e" + MarketGUIUtil.formatPrice(order.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("§7供給時受取: §a" + marketManager.calculateSellerProceeds(order.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("");
            lore.add("§eクリックで手持ちから供給");
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

        MarketBuyOrder order = session.getBuyOrderAtSlot(slot);
        if (order == null) {
            return;
        }

        MarketResult result = marketManager.fulfillBuyOrder(player, order.getId());
        sendResultMessage(player, result, order);

        // 募集が成立して一覧から消えるため再描画
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketBuyOrder order) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(order.getMaterial()),
                "amount", String.valueOf(order.getAmount()),
                "price", MarketGUIUtil.formatPrice(order.getPrice()),
                "proceeds", String.valueOf(marketManager.calculateSellerProceeds(order.getPrice())),
                "currency", configManager.getCurrencyName()));
    }
}
