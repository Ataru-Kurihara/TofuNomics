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
 * サービス依頼（修理・エンチャント募集）一覧 GUI。募集中（open）の全依頼をカテゴリでフィルタし、
 * ページングして表示する。クリックで引き受けてシステムが仮想加工する（システム自動代行）。
 * クリックイベントの受信は {@link MarketGUIListener} が担当する。
 * （募集一覧 {@link BuyOrderBrowseGUI} の鏡像）
 *
 * レイアウト: スロット 0〜7 カテゴリボタン、9〜44 依頼（36件/ページ）、45〜53 ナビゲーション。
 */
public class ServiceBrowseGUI {

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
    private final MarketServiceRequestDAO serviceRequestDAO;
    private final MarketGUIListener listener;

    public ServiceBrowseGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                            MarketServiceRequestDAO serviceRequestDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.serviceRequestDAO = serviceRequestDAO;
        this.listener = listener;
    }

    /**
     * サービス依頼一覧 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - 修理・エンチャント募集");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.SERVICE_BROWSE, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("サービス依頼一覧GUIの表示に失敗しました: " + e.getMessage());
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

        List<MarketServiceRequest> all;
        try {
            all = serviceRequestDAO.getOpenRequests();
        } catch (SQLException e) {
            plugin.getLogger().warning("サービス依頼一覧の取得に失敗しました: " + e.getMessage());
            all = new ArrayList<>();
        }

        // カテゴリでフィルタ
        MarketCategory category = session.getCategory();
        List<MarketServiceRequest> requests = new ArrayList<>();
        for (MarketServiceRequest req : all) {
            if (category.matches(Material.matchMaterial(req.getMaterial()))) {
                requests.add(req);
            }
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

        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            MarketServiceRequest req = requests.get(i);
            gui.setItem(slot, buildDisplayItem(req));
            session.putSlotServiceRequest(slot, req);
            slot++;
        }

        setupCategoryButtons(gui, category);
        setupNavigation(gui, session, requests.size(), totalPages, all.size());
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
                        "§7依頼をクリックして引き受け（自動加工）"
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
     * サービス依頼から表示用アイテムを生成する。預けた実アイテムを復元して表示し（現耐久・既存エンチャントが見える）、
     * 依頼情報を lore に付す。
     */
    private ItemStack buildDisplayItem(MarketServiceRequest req) {
        ItemStack item = MarketItemSerializer.deserialize(req.getItemData());
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
            lore.add("§7依頼者: §f" + req.getRequesterName());
            if (req.isRepair()) {
                lore.add("§7内容: §b修理（耐久全回復）");
                lore.add("§7必要: §f経験値 " + configManager.getMarketServiceRepairExpCost() + " レベル");
            } else {
                int level = req.getEnchantLevel() != null ? req.getEnchantLevel() : 1;
                lore.add("§7内容: §dエンチャント §f" + prettifyEnchant(req.getEnchantType()) + " " + level);
                lore.add("§7必要: §f経験値 " + (configManager.getMarketServiceEnchantExpCostPerLevel() * level)
                        + " レベル ＋ ラピス " + configManager.getMarketServiceEnchantLapisCost() + " 個");
            }
            lore.add("§7報酬総額: §e" + MarketGUIUtil.formatPrice(req.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("§7引受時受取: §a" + marketManager.calculateSellerProceeds(req.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("");
            lore.add("§eクリックで引き受け（自動加工）");
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

        MarketServiceRequest req = session.getServiceRequestAtSlot(slot);
        if (req == null) {
            return;
        }

        MarketResult result = marketManager.fulfillServiceRequest(player, req.getId());
        sendResultMessage(player, result, req);

        // 依頼が成立して一覧から消えるため再描画
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketServiceRequest req) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(req.getMaterial()),
                "service", req.isRepair() ? "修理" : "エンチャント",
                "price", MarketGUIUtil.formatPrice(req.getPrice()),
                "proceeds", String.valueOf(marketManager.calculateSellerProceeds(req.getPrice())),
                "currency", configManager.getCurrencyName()));
    }
}
