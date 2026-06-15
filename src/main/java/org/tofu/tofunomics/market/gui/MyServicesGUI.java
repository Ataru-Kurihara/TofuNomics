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
import org.tofu.tofunomics.dao.MarketServiceRequestDAO;
import org.tofu.tofunomics.market.MarketItemSerializer;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.models.MarketServiceRequest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 自分のサービス依頼（修理・エンチャント募集）管理 GUI。依頼者の全依頼（status 問わず）をページングして表示する。
 * open をクリックでキャンセル（道具＋前払い返却）、fulfilled/expired をクリックでアイテムを回収する。
 * クリックイベントの受信は {@link MarketGUIListener} が担当する。
 * （自分の募集 {@link MyBuyOrdersGUI} の鏡像）
 */
public class MyServicesGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketServiceRequestDAO serviceRequestDAO;
    private final MarketGUIListener listener;

    public MyServicesGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                         MarketServiceRequestDAO serviceRequestDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.serviceRequestDAO = serviceRequestDAO;
        this.listener = listener;
    }

    /**
     * 自分のサービス依頼 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - あなたの依頼");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.MY_SERVICES, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("自分のサービス依頼GUIの表示に失敗しました: " + e.getMessage());
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

        List<MarketServiceRequest> requests;
        try {
            requests = serviceRequestDAO.getByRequester(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("自分のサービス依頼一覧の取得に失敗しました: " + e.getMessage());
            requests = new ArrayList<>();
        }

        int totalPages = Math.max(1, (int) Math.ceil(requests.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, requests.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            MarketServiceRequest req = requests.get(i);
            gui.setItem(slot, buildDisplayItem(req));
            session.putSlotServiceRequest(slot, req);
            slot++;
        }

        setupNavigation(gui, session, requests.size(), totalPages);
    }

    private void setupNavigation(Inventory gui, MarketGUISession session, int totalRequests, int totalPages) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, MarketGUIUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, MarketGUIUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, MarketGUIUtil.createButton(Material.WRITABLE_BOOK, "§6あなたの依頼",
                Arrays.asList(
                        "§f依頼数: §a" + totalRequests + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages,
                        "§7募集中をクリックでキャンセル＋返却",
                        "§7完了・期限切れをクリックで回収"
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

    private ItemStack buildDisplayItem(MarketServiceRequest req) {
        // 完成済みなら完成品、それ以外は預けた元道具を表示する
        String data = MarketServiceRequest.STATUS_FULFILLED.equals(req.getStatus())
                ? req.getResultItemData() : req.getItemData();
        ItemStack item = data != null ? MarketItemSerializer.deserialize(data) : null;
        if (item == null) {
            Material material = Material.matchMaterial(req.getMaterial());
            item = new ItemStack(material != null ? material : Material.BARRIER);
        } else {
            item = item.clone();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (req.isRepair()) {
                lore.add("§7内容: §b修理（耐久全回復）");
            } else {
                int level = req.getEnchantLevel() != null ? req.getEnchantLevel() : 1;
                lore.add("§7内容: §dエンチャント §f" + prettifyEnchant(req.getEnchantType()) + " " + level);
            }
            lore.add("§7前払い額: §e" + MarketGUIUtil.formatPrice(req.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("§7状態: " + statusLabel(req.getStatus()));
            lore.add("");
            lore.addAll(actionHint(req));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String prettifyEnchant(String enchantKey) {
        if (enchantKey == null) {
            return "";
        }
        return enchantKey.replace('_', ' ');
    }

    private String statusLabel(String status) {
        switch (status) {
            case MarketServiceRequest.STATUS_OPEN:
                return "§a募集中";
            case MarketServiceRequest.STATUS_FULFILLED:
                return "§e完了（受け取り待ち）";
            case MarketServiceRequest.STATUS_RECLAIMED:
                return "§7受け取り済み";
            case MarketServiceRequest.STATUS_CANCELLED:
                return "§7キャンセル済み";
            case MarketServiceRequest.STATUS_EXPIRED:
                return "§c期限切れ（返金済み・要回収）";
            default:
                return "§7" + status;
        }
    }

    private List<String> actionHint(MarketServiceRequest req) {
        String status = req.getStatus();
        if (MarketServiceRequest.STATUS_OPEN.equals(status)) {
            return Collections.singletonList("§eクリックでキャンセルして道具と前払いを返却");
        }
        if (MarketServiceRequest.STATUS_FULFILLED.equals(status)) {
            return Collections.singletonList("§eクリックで完成した道具を回収");
        }
        if (MarketServiceRequest.STATUS_EXPIRED.equals(status)) {
            return Collections.singletonList("§eクリックで預けた道具を回収");
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

        MarketServiceRequest req = session.getServiceRequestAtSlot(slot);
        if (req == null) {
            return;
        }

        String status = req.getStatus();
        MarketResult result;
        if (MarketServiceRequest.STATUS_OPEN.equals(status)) {
            result = marketManager.cancelServiceRequest(player, req.getId());
        } else if (MarketServiceRequest.STATUS_FULFILLED.equals(status)
                || MarketServiceRequest.STATUS_EXPIRED.equals(status)) {
            result = marketManager.reclaimServiceRequest(player, req.getId());
        } else {
            // reclaimed / cancelled は操作不可
            return;
        }

        sendResultMessage(player, result, req);
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketServiceRequest req) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(req.getMaterial()),
                "service", req.isRepair() ? "修理" : "エンチャント",
                "price", MarketGUIUtil.formatPrice(req.getPrice()),
                "currency", configManager.getCurrencyName()));
    }
}
