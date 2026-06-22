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
 * 自分の募集管理 GUI。操作が必要な募集（募集中・成立の回収待ち）のみをページングして表示する。
 * 完了済み（回収済み・キャンセル済み・期限切れ返金済み）は一覧から除外する。
 * open をクリックでキャンセル（前払い返金）、fulfilled をクリックで供給アイテムを回収する。
 * クリックイベントの受信は {@link MarketGUIListener} が担当する。
 * （自分の出品 {@link MyListingsGUI} の鏡像）
 */
public class MyBuyOrdersGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketBuyOrderDAO buyOrderDAO;
    private final MarketGUIListener listener;

    public MyBuyOrdersGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                          MarketBuyOrderDAO buyOrderDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.buyOrderDAO = buyOrderDAO;
        this.listener = listener;
    }

    /**
     * 自分の募集 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - あなたの募集");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.MY_BUY_ORDERS, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("自分の募集GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(configManager.getMarketMessage("error"));
        }
    }

    /**
     * 現在ページの内容を描画する。
     */
    public void render(Player player, MarketGUISession session) {
        Inventory gui = session.getInventory();
        gui.clear();
        session.clearSlotListings();

        List<MarketBuyOrder> orders;
        try {
            orders = buyOrderDAO.getOrdersByRequester(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("自分の募集一覧の取得に失敗しました: " + e.getMessage());
            orders = new ArrayList<>();
        }

        // 完了済み（回収済み・キャンセル済み・期限切れ返金済み）は除外し、操作が必要なもの（募集中・成立の回収待ち）のみ表示する
        orders.removeIf(order -> !order.isOpen() && !order.isFulfilled());

        int totalPages = Math.max(1, (int) Math.ceil(orders.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, orders.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            MarketBuyOrder order = orders.get(i);
            gui.setItem(slot, buildDisplayItem(order));
            session.putSlotBuyOrder(slot, order);
            slot++;
        }

        setupNavigation(gui, session, orders.size(), totalPages);
    }

    private void setupNavigation(Inventory gui, MarketGUISession session, int totalOrders, int totalPages) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, MarketGUIUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, MarketGUIUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, MarketGUIUtil.createButton(Material.WRITABLE_BOOK, "§6あなたの募集",
                Arrays.asList(
                        "§f募集数: §a" + totalOrders + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages,
                        "§7募集中をクリックでキャンセル＋返金",
                        "§7成立済みをクリックでアイテム回収"
                )));

        gui.setItem(SLOT_CLOSE, MarketGUIUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = MarketGUIUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = ITEMS_PER_PAGE; i < INVENTORY_SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

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
            lore.add("§7希望個数: §f" + order.getAmount());
            lore.add("§7前払い額: §e" + MarketGUIUtil.formatPrice(order.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("§7状態: " + statusLabel(order.getStatus()));
            lore.add("");
            lore.addAll(actionHint(order));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String statusLabel(String status) {
        switch (status) {
            case MarketBuyOrder.STATUS_OPEN:
                return "§a募集中";
            case MarketBuyOrder.STATUS_FULFILLED:
                return "§e成立（回収待ち）";
            case MarketBuyOrder.STATUS_RECLAIMED:
                return "§7回収済み";
            case MarketBuyOrder.STATUS_CANCELLED:
                return "§7キャンセル済み";
            case MarketBuyOrder.STATUS_EXPIRED:
                return "§c期限切れ（返金済み）";
            default:
                return "§7" + status;
        }
    }

    private List<String> actionHint(MarketBuyOrder order) {
        if (order.isOpen()) {
            return Collections.singletonList("§eクリックでキャンセルして前払いを返金");
        }
        if (order.isFulfilled()) {
            return Collections.singletonList("§eクリックで供給されたアイテムを回収");
        }
        return Collections.singletonList("§7操作できません");
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

        MarketBuyOrder order = session.getBuyOrderAtSlot(slot);
        if (order == null) {
            return;
        }

        MarketResult result;
        if (order.isOpen()) {
            result = marketManager.cancelBuyOrder(player, order.getId());
        } else if (order.isFulfilled()) {
            result = marketManager.reclaimBuyOrder(player, order.getId());
        } else {
            // reclaimed / cancelled / expired は操作不可
            return;
        }

        sendResultMessage(player, result, order);
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketBuyOrder order) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(order.getMaterial()),
                "amount", String.valueOf(order.getAmount()),
                "price", MarketGUIUtil.formatPrice(order.getPrice()),
                "currency", configManager.getCurrencyName()));
    }
}
