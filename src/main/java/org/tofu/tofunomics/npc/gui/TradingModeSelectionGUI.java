package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.npc.TradingNPCManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 取引モード選択GUI
 * プレイヤーが取引NPCをクリックした時に、売却・購入のモードを選択させる
 */
public class TradingModeSelectionGUI implements Listener {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final TradingGUI tradingGUI;

    // アクティブなモード選択セッション
    private final Map<UUID, ModeSelectionSession> activeSessions = new ConcurrentHashMap<>();

    public TradingModeSelectionGUI(TofuNomics plugin, ConfigManager configManager, TradingGUI tradingGUI) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tradingGUI = tradingGUI;
    }

    /**
     * モード選択セッション
     */
    private static class ModeSelectionSession {
        private final UUID playerId;
        private final TradingNPCManager.TradingPost tradingPost;
        private final Inventory inventory;
        private final long createdTime;

        public ModeSelectionSession(UUID playerId, TradingNPCManager.TradingPost tradingPost, Inventory inventory) {
            this.playerId = playerId;
            this.tradingPost = tradingPost;
            this.inventory = inventory;
            this.createdTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public TradingNPCManager.TradingPost getTradingPost() { return tradingPost; }
        public Inventory getInventory() { return inventory; }
        public long getCreatedTime() { return createdTime; }
    }

    /**
     * モード選択GUIを開く
     */
    public void openModeSelectionGUI(Player player, TradingNPCManager.TradingPost tradingPost) {
        try {
            // GUIタイトル
            String title = "§6" + tradingPost.getName() + " - モード選択";

            // インベントリ作成（27スロット = 3行）
            Inventory gui = Bukkit.createInventory(null, 27, title);

            // セッション作成
            ModeSelectionSession session = new ModeSelectionSession(
                player.getUniqueId(),
                tradingPost,
                gui
            );

            // GUIアイテム設定
            setupModeSelectionItems(gui, tradingPost);

            // セッション登録
            activeSessions.put(player.getUniqueId(), session);

            // GUI開起
            player.openInventory(gui);

        } catch (Exception e) {
            plugin.getLogger().severe("モード選択GUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c画面の表示中にエラーが発生しました。");
        }
    }

    /**
     * モード選択GUIのアイテム設定
     */
    private void setupModeSelectionItems(Inventory gui, TradingNPCManager.TradingPost tradingPost) {
        gui.clear();

        // 売却モードボタン（スロット11）
        ItemStack sellModeItem = createGUIItem(
            Material.EMERALD,
            "§a§l売却モード",
            Arrays.asList(
                "§7アイテムを売ってお金を得る",
                "",
                "§eクリックで売却画面を開く"
            )
        );
        gui.setItem(11, sellModeItem);

        // 購入モードボタン（スロット15）
        // purchase_pricesが設定されている場合のみ表示
        if (tradingPost.getPurchasePrices() != null && !tradingPost.getPurchasePrices().isEmpty()) {
            ItemStack buyModeItem = createGUIItem(
                Material.GOLD_INGOT,
                "§e§l購入モード",
                Arrays.asList(
                    "§7お金を使ってアイテムを買う",
                    "",
                    "§eクリックで購入画面を開く"
                )
            );
            gui.setItem(15, buyModeItem);
        }

        // 閉じるボタン（スロット22）
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7このメニューを閉じます")
        );
        gui.setItem(22, closeItem);

        // 装飾（グレーのガラス板）
        fillEmptySlots(gui);
    }

    /**
     * GUIアイテム作成ヘルパー
     */
    private ItemStack createGUIItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 空きスロットを装飾で埋める
     */
    private void fillEmptySlots(Inventory gui) {
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glassPane);
            }
        }
    }

    /**
     * インベントリクリックイベント処理
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // このプレイヤーのセッションを取得
        ModeSelectionSession session = activeSessions.get(playerId);
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }

        // クリックをキャンセル（アイテム移動を防止）
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();

        try {
            handleModeSelection(player, session, slot, clickedItem.getType());
        } catch (Exception e) {
            plugin.getLogger().severe("モード選択処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    /**
     * モード選択処理
     */
    private void handleModeSelection(Player player, ModeSelectionSession session, int slot, Material material) {
        TradingNPCManager.TradingPost tradingPost = session.getTradingPost();

        switch (slot) {
            case 11: // 売却モード
                if (material == Material.EMERALD) {
                    player.closeInventory();
                    // 売却モードで取引GUIを開く
                    tradingGUI.openTradingGUIWithMode(player, tradingPost, TradingGUI.TradingMode.SELL);
                }
                break;

            case 15: // 購入モード
                if (material == Material.GOLD_INGOT) {
                    player.closeInventory();
                    // 購入モードで取引GUIを開く
                    tradingGUI.openTradingGUIWithMode(player, tradingPost, TradingGUI.TradingMode.BUY);
                }
                break;

            case 22: // 閉じる
                if (material == Material.BARRIER) {
                    player.closeInventory();
                }
                break;

            default:
                // その他のスロットは何もしない
                break;
        }
    }

    /**
     * インベントリ閉じるイベント処理
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // セッションを削除
        activeSessions.remove(playerId);
    }

    /**
     * 全てのGUIを閉じる（プラグインリロード時など）
     */
    public void closeAllGUIs() {
        for (ModeSelectionSession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    /**
     * アクティブなセッション数を取得
     */
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
}
