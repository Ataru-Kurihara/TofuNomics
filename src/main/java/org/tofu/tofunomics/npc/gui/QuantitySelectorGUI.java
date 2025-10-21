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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 汎用数量選択GUIシステム
 * 各種NPCで再利用可能な数量選択インターフェース
 */
public class QuantitySelectorGUI implements Listener {

    private final TofuNomics plugin;
    private final Map<UUID, QuantitySelectorSession> activeSessions = new ConcurrentHashMap<>();

    public QuantitySelectorGUI(TofuNomics plugin) {
        this.plugin = plugin;
    }

    /**
     * 数量選択セッション
     */
    public static class QuantitySelectorSession {
        private final UUID playerId;
        private final Inventory inventory;
        private int currentQuantity;
        private final int minQuantity;
        private final int maxQuantity;
        private final String itemName;
        private final Material displayMaterial;
        private final BiConsumer<Player, Integer> onConfirm;
        private final Runnable onCancel;

        public QuantitySelectorSession(UUID playerId, Inventory inventory, int initialQuantity,
                                      int minQuantity, int maxQuantity, String itemName,
                                      Material displayMaterial, BiConsumer<Player, Integer> onConfirm,
                                      Runnable onCancel) {
            this.playerId = playerId;
            this.inventory = inventory;
            this.currentQuantity = initialQuantity;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.itemName = itemName;
            this.displayMaterial = displayMaterial;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        public UUID getPlayerId() { return playerId; }
        public Inventory getInventory() { return inventory; }
        public int getCurrentQuantity() { return currentQuantity; }
        public void setCurrentQuantity(int quantity) {
            this.currentQuantity = Math.max(minQuantity, Math.min(maxQuantity, quantity));
        }
        public int getMinQuantity() { return minQuantity; }
        public int getMaxQuantity() { return maxQuantity; }
        public String getItemName() { return itemName; }
        public Material getDisplayMaterial() { return displayMaterial; }
        public void executeConfirm(Player player) { onConfirm.accept(player, currentQuantity); }
        public void executeCancel() { if (onCancel != null) onCancel.run(); }
    }

    /**
     * 数量選択GUIを開く
     *
     * @param player プレイヤー
     * @param title GUIタイトル
     * @param itemName 選択対象アイテム名
     * @param displayMaterial 表示用マテリアル
     * @param initialQuantity 初期数量
     * @param minQuantity 最小数量
     * @param maxQuantity 最大数量
     * @param additionalInfo 追加情報（Loreに表示）
     * @param onConfirm 確定時のコールバック (Player, Integer) -> void
     * @param onCancel キャンセル時のコールバック
     */
    public void openQuantitySelector(Player player, String title, String itemName,
                                     Material displayMaterial, int initialQuantity,
                                     int minQuantity, int maxQuantity,
                                     List<String> additionalInfo,
                                     BiConsumer<Player, Integer> onConfirm,
                                     Runnable onCancel) {
        try {
            // 既存のセッションチェック
            QuantitySelectorSession existingSession = activeSessions.get(player.getUniqueId());
            if (existingSession != null) {
                plugin.getLogger().warning("プレイヤー " + player.getName() + " は既に数量選択GUIを開いています。");
                return;
            }

            // 数量の範囲チェック
            int safeInitial = Math.max(minQuantity, Math.min(maxQuantity, initialQuantity));

            Inventory gui = Bukkit.createInventory(null, 27, title);

            QuantitySelectorSession session = new QuantitySelectorSession(
                player.getUniqueId(),
                gui,
                safeInitial,
                minQuantity,
                maxQuantity,
                itemName,
                displayMaterial,
                onConfirm,
                onCancel
            );

            setupQuantitySelectorGUI(gui, session, additionalInfo);

            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);

            plugin.getLogger().info("数量選択GUIを開きました: " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("数量選択GUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§cGUIの作成に失敗しました。");
            e.printStackTrace();
        }
    }

    /**
     * GUIのセットアップ
     */
    private void setupQuantitySelectorGUI(Inventory gui, QuantitySelectorSession session, List<String> additionalInfo) {
        // 背景ガラス
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ", Arrays.asList());
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glassPane);
        }

        // 情報表示エリア（0-8）
        setupInfoDisplay(gui, session, additionalInfo);

        // 操作ボタン（9-17）
        setupControlButtons(gui, session);

        // 確定・キャンセルボタン（18-26）
        setupConfirmCancelButtons(gui, session);
    }

    /**
     * 情報表示エリアのセットアップ
     */
    private void setupInfoDisplay(Inventory gui, QuantitySelectorSession session, List<String> additionalInfo) {
        List<String> lore = new ArrayList<>();
        lore.add("§f対象: §e" + session.getItemName());
        lore.add("§f選択数量: §a" + session.getCurrentQuantity() + "個");
        lore.add("§f範囲: §7" + session.getMinQuantity() + "個 ～ " + session.getMaxQuantity() + "個");

        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            lore.add("");
            lore.addAll(additionalInfo);
        }

        ItemStack infoItem = createGUIItem(
            session.getDisplayMaterial(),
            "§6数量選択",
            lore
        );

        gui.setItem(4, infoItem);
    }

    /**
     * 操作ボタンのセットアップ
     */
    private void setupControlButtons(Inventory gui, QuantitySelectorSession session) {
        // -10ボタン
        ItemStack minus10 = createGUIItem(
            Material.RED_CONCRETE,
            "§c-10",
            Arrays.asList("§710個減らす")
        );
        gui.setItem(10, minus10);

        // -1ボタン
        ItemStack minus1 = createGUIItem(
            Material.ORANGE_CONCRETE,
            "§6-1",
            Arrays.asList("§71個減らす")
        );
        gui.setItem(11, minus1);

        // 現在の数量表示
        updateQuantityDisplay(gui, session);

        // +1ボタン
        ItemStack plus1 = createGUIItem(
            Material.LIME_CONCRETE,
            "§a+1",
            Arrays.asList("§71個増やす")
        );
        gui.setItem(15, plus1);

        // +10ボタン
        ItemStack plus10 = createGUIItem(
            Material.GREEN_CONCRETE,
            "§2+10",
            Arrays.asList("§710個増やす")
        );
        gui.setItem(16, plus10);
    }

    /**
     * 数量表示の更新
     */
    private void updateQuantityDisplay(Inventory gui, QuantitySelectorSession session) {
        List<String> lore = new ArrayList<>();
        lore.add("§f現在の選択数量");
        lore.add("");
        lore.add("§e" + session.getCurrentQuantity() + "個");

        ItemStack quantityDisplay = createGUIItem(
            Material.PAPER,
            "§6選択数量",
            lore
        );

        gui.setItem(13, quantityDisplay);
    }

    /**
     * 確定・キャンセルボタンのセットアップ
     */
    private void setupConfirmCancelButtons(Inventory gui, QuantitySelectorSession session) {
        // キャンセルボタン
        ItemStack cancelButton = createGUIItem(
            Material.BARRIER,
            "§cキャンセル",
            Arrays.asList("§7数量選択をキャンセルします")
        );
        gui.setItem(21, cancelButton);

        // 確定ボタン
        ItemStack confirmButton = createGUIItem(
            Material.EMERALD,
            "§a確定",
            Arrays.asList(
                "§7選択した数量で処理を実行します",
                "",
                "§e選択数量: §a" + session.getCurrentQuantity() + "個"
            )
        );
        gui.setItem(23, confirmButton);
    }

    /**
     * GUIアイテムの作成
     */
    private ItemStack createGUIItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * インベントリクリックイベント
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        QuantitySelectorSession session = activeSessions.get(player.getUniqueId());

        if (session == null || !event.getInventory().equals(session.getInventory())) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getSlot();

        switch (slot) {
            case 10: // -10
                session.setCurrentQuantity(session.getCurrentQuantity() - 10);
                updateQuantitySelectorGUI(session);
                break;

            case 11: // -1
                session.setCurrentQuantity(session.getCurrentQuantity() - 1);
                updateQuantitySelectorGUI(session);
                break;

            case 15: // +1
                session.setCurrentQuantity(session.getCurrentQuantity() + 1);
                updateQuantitySelectorGUI(session);
                break;

            case 16: // +10
                session.setCurrentQuantity(session.getCurrentQuantity() + 10);
                updateQuantitySelectorGUI(session);
                break;

            case 21: // キャンセル
                handleCancel(player, session);
                break;

            case 23: // 確定
                handleConfirm(player, session);
                break;
        }
    }

    /**
     * GUIの更新
     */
    private void updateQuantitySelectorGUI(QuantitySelectorSession session) {
        updateQuantityDisplay(session.getInventory(), session);
        setupConfirmCancelButtons(session.getInventory(), session);
    }

    /**
     * キャンセル処理
     */
    private void handleCancel(Player player, QuantitySelectorSession session) {
        player.closeInventory();
        session.executeCancel();
    }

    /**
     * 確定処理
     */
    private void handleConfirm(Player player, QuantitySelectorSession session) {
        player.closeInventory();
        session.executeConfirm(player);
    }

    /**
     * インベントリクローズイベント
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        QuantitySelectorSession session = activeSessions.get(player.getUniqueId());

        if (session != null && event.getInventory().equals(session.getInventory())) {
            activeSessions.remove(player.getUniqueId());
            plugin.getLogger().info("数量選択GUIを閉じました: " + player.getName());
        }
    }

    /**
     * 全GUIを閉じる
     */
    public void closeAllGUIs() {
        for (QuantitySelectorSession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }
}
