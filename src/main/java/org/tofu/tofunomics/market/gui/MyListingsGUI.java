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
 * 自分の出品管理 GUI。出品者の全出品（status 問わず）をページングして表示する。
 * active をクリックでキャンセル回収、expired をクリックで回収する。
 * クリックイベントの受信は {@link MarketGUIListener} が担当する。
 */
public class MyListingsGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketListingDAO listingDAO;
    private final MarketGUIListener listener;

    public MyListingsGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                         MarketListingDAO listingDAO, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.listingDAO = listingDAO;
        this.listener = listener;
    }

    /**
     * 自分の出品 GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6マーケット - あなたの出品");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.MY_LISTINGS, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("自分の出品GUIの表示に失敗しました: " + e.getMessage());
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

        List<MarketListing> listings;
        try {
            listings = listingDAO.getListingsBySeller(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("自分の出品一覧の取得に失敗しました: " + e.getMessage());
            listings = new ArrayList<>();
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

        int slot = 0;
        for (int i = start; i < end; i++) {
            MarketListing listing = listings.get(i);
            gui.setItem(slot, buildDisplayItem(listing));
            session.putSlotListing(slot, listing);
            slot++;
        }

        setupNavigation(gui, session, listings.size(), totalPages);
    }

    private void setupNavigation(Inventory gui, MarketGUISession session, int totalListings, int totalPages) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, MarketGUIUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, MarketGUIUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, MarketGUIUtil.createButton(Material.BOOK, "§6あなたの出品",
                Arrays.asList(
                        "§f出品数: §a" + totalListings + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages,
                        "§7出品中をクリックでキャンセル回収",
                        "§7期限切れをクリックで回収"
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
            lore.add("§7個数: §f" + listing.getAmount());
            lore.add("§7価格: §e" + MarketGUIUtil.formatPrice(listing.getPrice())
                    + " " + configManager.getCurrencyName());
            lore.add("§7状態: " + statusLabel(listing.getStatus()));
            lore.add("");
            lore.addAll(actionHint(listing));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String statusLabel(String status) {
        switch (status) {
            case MarketListing.STATUS_ACTIVE:
                return "§a出品中";
            case MarketListing.STATUS_EXPIRED:
                return "§c期限切れ";
            case MarketListing.STATUS_SOLD:
                return "§e売却済み";
            case MarketListing.STATUS_RECLAIMED:
                return "§7回収済み";
            default:
                return "§7" + status;
        }
    }

    private List<String> actionHint(MarketListing listing) {
        if (listing.isActive()) {
            return Collections.singletonList("§eクリックでキャンセルしてアイテムを回収");
        }
        if (listing.isExpired()) {
            return Collections.singletonList("§eクリックでアイテムを回収");
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

        MarketListing listing = session.getListingAtSlot(slot);
        if (listing == null) {
            return;
        }

        MarketResult result;
        if (listing.isActive()) {
            result = marketManager.cancelListing(player, listing.getId());
        } else if (listing.isExpired()) {
            result = marketManager.reclaimListing(player, listing.getId());
        } else {
            // sold / reclaimed は操作不可
            return;
        }

        sendResultMessage(player, result, listing);
        render(player, session);
    }

    private void sendResultMessage(Player player, MarketResult result, MarketListing listing) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(listing.getMaterial()),
                "price", MarketGUIUtil.formatPrice(listing.getPrice()),
                "currency", configManager.getCurrencyName()));
    }
}
