package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.npc.FoodNPCManager;
import org.tofu.tofunomics.npc.NPCManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食料NPC用GUIシステム
 */
public class FoodGUI implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final FoodNPCManager foodNPCManager;
    
    private final Map<UUID, FoodGUISession> activeSessions = new ConcurrentHashMap<>();
    
    public FoodGUI(TofuNomics plugin, ConfigManager configManager, CurrencyConverter currencyConverter, FoodNPCManager foodNPCManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.foodNPCManager = foodNPCManager;
    }
    
    public static class FoodGUISession {
        private final UUID playerId;
        private final UUID npcId;
        private final String npcName;
        private final Inventory inventory;
        
        public FoodGUISession(UUID playerId, UUID npcId, String npcName, Inventory inventory) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.npcName = npcName;
            this.inventory = inventory;
        }
        
        public UUID getPlayerId() { return playerId; }
        public UUID getNpcId() { return npcId; }
        public String getNpcName() { return npcName; }
        public Inventory getInventory() { return inventory; }
    }
    
    /**
     * 食料NPCのGUIを開く
     */
    public void openFoodGUI(Player player, UUID npcId, String npcName) {
        try {
            // 既存のセッションチェック
            FoodGUISession existingSession = activeSessions.get(player.getUniqueId());
            if (existingSession != null) {
                plugin.getLogger().warning("プレイヤー " + player.getName() + " は既に食料GUIを開いています。重複開起を防止しました。");
                return;
            }
            
            // NPCタイプに応じたタイトル表示
            String npcType = getNPCType(npcId);
            String typeDisplayName = configManager.getFoodNPCTypeName(npcType);
            String title = typeDisplayName;
            Inventory gui = Bukkit.createInventory(null, 54, title);
            
            setupFoodGUIItems(gui, player, npcId);
            
            FoodGUISession session = new FoodGUISession(
                player.getUniqueId(), 
                npcId,
                npcName, 
                gui
            );
            
            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);
            
            plugin.getLogger().info("食料GUIを開きました: " + player.getName() + " -> " + npcName);
            
        } catch (Exception e) {
            plugin.getLogger().severe("食料GUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§cGUIの作成に失敗しました。管理者にお知らせください。");
            e.printStackTrace();
        }
    }
    
    /**
     * GUIアイテムのセットアップ
     */
    private void setupFoodGUIItems(Inventory gui, Player player, UUID npcId) {
        // ヘッダー部分（0-8）
        setupHeaderItems(gui, player);
        
        // 食料アイテム部分（9-44）
        setupFoodItems(gui, player, npcId);
        
        // フッター部分（45-53）
        setupFooterItems(gui);
    }
    
    /**
     * ヘッダーアイテムのセットアップ
     */
    private void setupHeaderItems(Inventory gui, Player player) {
        // 残高表示
        double balance = currencyConverter.getBalance(player.getUniqueId());
        String balanceText = currencyConverter.formatCurrency(balance);
        
        int startHour = configManager.getFoodNPCStartHour();
        int endHour = configManager.getFoodNPCEndHour();
        
        ItemStack balanceItem = createGUIItem(
            Material.GOLD_INGOT,
            "§6現在の残高",
            Arrays.asList(
                "§f残高: §a" + balanceText,
                String.format("§7営業時間: §e%d:00-%d:00", startHour, endHour)
            )
        );
        gui.setItem(4, balanceItem);
        
        // 装飾アイテム
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§7", Arrays.asList());
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                gui.setItem(i, glassPane);
            }
        }
    }
    
    /**
     * 食料アイテムのセットアップ
     */
    private void setupFoodItems(Inventory gui, Player player, UUID npcId) {
        // 常に設定ファイルから商品リストを取得
        Map<Material, Double> itemPrices = getCurrentFoodPrices(npcId);
        plugin.getLogger().info("FoodGUI: 商品データ取得完了 - 商品数: " + itemPrices.size());
        
        Map<Material, Integer> storeInventory = foodNPCManager.getStoreInventory(npcId);
        Map<Material, Integer> playerPurchases = foodNPCManager.getPlayerDailyPurchases(player.getUniqueId());
        int dailyLimit = configManager.getFoodNPCDailyLimitPerItem();
        
        int slot = 9;
        for (Map.Entry<Material, Double> entry : itemPrices.entrySet()) {
            if (slot >= 45) break; // フッター領域に到達したら停止
            
            Material material = entry.getKey();
            double price = entry.getValue();
            
            int stock = storeInventory != null ? storeInventory.getOrDefault(material, 0) : 0;
            int purchased = playerPurchases != null ? playerPurchases.getOrDefault(material, 0) : 0;
            int remaining = dailyLimit - purchased;
            
            // アイテム作成
            ItemStack foodItem = createFoodItem(material, price, stock, remaining);
            gui.setItem(slot, foodItem);
            
            slot++;
        }
    }
    
    /**
     * フッターアイテムのセットアップ
     */
    private void setupFooterItems(Inventory gui) {
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§7", Arrays.asList());
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, glassPane);
        }
        
        // 閉じるボタン
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7クリックしてGUIを閉じます")
        );
        gui.setItem(49, closeItem);
    }
    
    /**
     * 食料アイテムの作成
     */
    private ItemStack createFoodItem(Material material, double price, int stock, int remaining) {
        List<String> lore = new ArrayList<>();
        
        String priceText = currencyConverter.formatCurrency(price);
        lore.add("§e価格: " + priceText);
        
        // 在庫状況
        if (stock > 0) {
            lore.add("§a在庫: " + stock + "個");
        } else {
            lore.add("§c在庫: 売り切れ");
        }
        
        // 購入制限
        if (remaining > 0) {
            lore.add("§7本日残り: " + remaining + "個購入可能");
        } else {
            lore.add("§c本日の購入上限に達しています");
        }
        
        lore.add("");
        lore.add("§f購入数量:");
        lore.add("§7左クリック: §f1個");
        lore.add("§7右クリック: §f5個");
        lore.add("§7Shift+左クリック: §f10個");
        lore.add("§7Shift+右クリック: §f上限まで");
        
        // 購入不可な場合
        if (stock == 0 || remaining == 0) {
            lore.add("");
            lore.add("§c購入できません");
        }
        
        String displayName = "§a" + getItemDisplayName(material);
        return createGUIItem(material, displayName, lore);
    }
    
    /**
     * マテリアルの表示名を取得
     */
    private String getItemDisplayName(Material material) {
        switch (material) {
            case BREAD: return "パン";
            case COOKED_BEEF: return "焼き肉";
            case COOKED_PORKCHOP: return "焼き豚";
            case APPLE: return "りんご";
            case GOLDEN_APPLE: return "金のりんご";
            case COOKED_CHICKEN: return "焼き鳥";
            case BAKED_POTATO: return "ベイクドポテト";
            case CARROT: return "ニンジン";
            case BEETROOT: return "ビートルート";
            case MUSHROOM_STEW: return "キノコシチュー";
            default: return material.toString().toLowerCase().replace("_", " ");
        }
    }
    
    /**
     * 現在の食料価格を取得
     */
    /**
     * NPCのタイプを取得
     */
    private String getNPCType(UUID npcId) {
        FoodNPCManager.FoodStore foodStore = foodNPCManager.getFoodStore(npcId);
        if (foodStore != null) {
            String npcType = foodStore.getNpcType();
            plugin.getLogger().info("FoodGUI: NPCタイプ取得 - NPC ID: " + npcId + ", タイプ: " + npcType);
            return npcType;
        }
        plugin.getLogger().warning("FoodGUI: FoodStoreが見つかりません - NPC ID: " + npcId + ", デフォルトタイプを使用");
        return "general_store"; // フォールバック
    }
    
    /**
     * 現在の食料価格を取得（NPCタイプ考慮）
     */
    private Map<Material, Double> getCurrentFoodPrices(UUID npcId) {
        String npcType = getNPCType(npcId);
        Map<String, Double> configPrices = configManager.getFoodItemPrices();
        Map<Material, Double> prices = new HashMap<>();
        
        double priceMultiplier = configManager.getFoodNPCPriceMultiplier(npcType);
        List<String> allowedItems = configManager.getFoodNPCTypeItems(npcType);
        boolean isGeneral = configManager.isFoodNPCTypeGeneral(npcType);
        
        for (Map.Entry<String, Double> entry : configPrices.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey().toUpperCase());
                double basePrice = entry.getValue();
                
                // 商品フィルタリング
                if (isGeneral || allowedItems.contains(entry.getKey().toLowerCase())) {
                    // 価格倍率を適用
                    double finalPrice = Math.ceil(basePrice * priceMultiplier);
                    prices.put(material, finalPrice);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なマテリアル名: " + entry.getKey());
            }
        }
        
        return prices;
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
        FoodGUISession session = activeSessions.get(player.getUniqueId());
        
        if (session == null || !event.getInventory().equals(session.getInventory())) {
            return;
        }
        
        event.setCancelled(true);
        
        int slot = event.getSlot();
        ClickType clickType = event.getClick();
        
        if (slot == 49) { // 閉じるボタン
            player.closeInventory();
            return;
        }
        
        // ヘッダー・フッター領域は無視
        if (slot < 9 || slot >= 45) {
            return;
        }
        
        // 食料アイテムのクリック処理
        handleFoodItemClick(player, session, slot, clickType);
    }
    
    /**
     * 食料アイテムクリック処理
     */
    private void handleFoodItemClick(Player player, FoodGUISession session, int slot, ClickType clickType) {
        ItemStack clickedItem = session.getInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        
        Material material = clickedItem.getType();
        int amount = getPurchaseAmount(clickType);
        
        if (amount == 0) {
            return;
        }
        
        plugin.getLogger().info("食料購入処理: " + player.getName() + " -> " + material + " x" + amount);
        
        // 購入処理実行
        FoodNPCManager.PurchaseResult result = foodNPCManager.processFoodPurchase(
            player, session.getNpcId(), material, amount
        );
        
        if (result.isSuccess()) {
            player.sendMessage("§a" + getItemDisplayName(material) + " を " + amount + "個購入しました！");
            player.sendMessage("§7支払い: " + currencyConverter.formatCurrency(result.getTotalPrice()));
            
            // GUIを更新
            setupFoodGUIItems(session.getInventory(), player, session.getNpcId());
        } else {
            player.sendMessage("§c購入に失敗しました: " + result.getMessage());
        }
    }
    
    /**
     * クリックタイプから購入数量を決定
     */
    private int getPurchaseAmount(ClickType clickType) {
        switch (clickType) {
            case LEFT: return 1;
            case RIGHT: return 5;
            case SHIFT_LEFT: return 10;
            case SHIFT_RIGHT: return 32; // 上限まで（実際の制限は処理内で適用）
            default: return 0;
        }
    }
    
    /**
     * インベントリクローズイベント
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        FoodGUISession session = activeSessions.get(player.getUniqueId());
        
        if (session != null && event.getInventory().equals(session.getInventory())) {
            activeSessions.remove(player.getUniqueId());
            plugin.getLogger().info("食料GUIを閉じました: " + player.getName());
        }
    }
    
    /**
     * 全GUIを閉じる
     */
    public void closeAllGUIs() {
        for (FoodGUISession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }
    
    /**
     * アクティブセッション数を取得
     */
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
}