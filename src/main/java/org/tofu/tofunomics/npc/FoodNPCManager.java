package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食料NPC管理システム
 * 農家プレイヤーがいない時間帯の緊急食料供給を担当
 */
public class FoodNPCManager {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final CurrencyConverter currencyConverter;
    private final PlayerDAO playerDAO;
    
    // 食料商店データ
    private final Map<UUID, FoodStore> foodStores = new ConcurrentHashMap<>();
    
    // プレイヤーの日次購入履歴
    private final Map<UUID, Map<Material, Integer>> dailyPurchases = new ConcurrentHashMap<>();
    
    // 在庫管理
    private final Map<UUID, Map<Material, Integer>> storeInventories = new ConcurrentHashMap<>();
    
    // 最終リセット日付
    private String lastResetDate = "";
    
    public FoodNPCManager(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager,
                         CurrencyConverter currencyConverter, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.currencyConverter = currencyConverter;
        this.playerDAO = playerDAO;
        
        initializeResetDate();
    }
    
    /**
     * 食料商店データクラス
     */
    public static class FoodStore {
        private final UUID npcId;
        private final String name;
        private final Location location;
        private final Map<Material, Double> itemPrices;
        private final String npcType; // NPCタイプを追加
        
        public FoodStore(UUID npcId, String name, Location location, Map<Material, Double> itemPrices, String npcType) {
            this.npcId = npcId;
            this.name = name;
            this.location = location;
            this.itemPrices = itemPrices;
            this.npcType = npcType != null ? npcType : "general_store"; // デフォルトは総合店
        }
        
        public UUID getNpcId() { return npcId; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public Map<Material, Double> getItemPrices() { return itemPrices; }
        public String getNpcType() { return npcType; }
        
        public double getItemPrice(Material material) {
            return itemPrices.getOrDefault(material, 0.0);
        }
        
        public boolean sellsItem(Material material) {
            return itemPrices.containsKey(material) && itemPrices.get(material) > 0;
        }
    }
    
    /**
     * 食料NPCシステムの初期化
     */
    public void initializeFoodNPCs() {
        try {
            if (!configManager.isFoodNPCEnabled()) {
                plugin.getLogger().info("食料NPCシステムは無効化されています");
                return;
            }
            
            spawnFoodNPCs();
            checkDailyReset();
            plugin.getLogger().info("食料NPCシステムを初期化しました");
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 食料NPCをスポーンする
     */
    private void spawnFoodNPCs() {
        plugin.getLogger().info("=== 食料NPC生成開始 ===");
        
        // 既存の食料店データをクリア
        foodStores.clear();
        plugin.getLogger().info("既存の食料店データをクリアしました");
        
        List<Map<?, ?>> foodNPCConfigs = configManager.getFoodNPCConfigs();
        plugin.getLogger().info("設定から " + foodNPCConfigs.size() + " 個の食料NPCを読み込み");
        
        for (Map<?, ?> config : foodNPCConfigs) {
            try {
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                int x = (Integer) config.get("x");
                int y = (Integer) config.get("y");
                int z = (Integer) config.get("z");
                
                if (name == null || world == null) {
                    plugin.getLogger().warning("食料NPCの設定が不完全です: " + config);
                    continue;
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5);
                plugin.getLogger().info("食料NPCを生成中: " + name + " at [" + x + ", " + y + ", " + z + "] (world: " + world + ")");
                
                Villager foodNPC = npcManager.createNPC(location, "food_merchant", name);
                
                if (foodNPC != null) {
                    setupFoodNPC(foodNPC);
                    
                    String npcType = "general_store"; // デフォルトタイプ
                    Map<Material, Double> prices = buildFoodItemPrices(npcType);
                    FoodStore foodStore = new FoodStore(foodNPC.getUniqueId(), name, location, prices, npcType);
                    foodStores.put(foodNPC.getUniqueId(), foodStore);
                    
                    // 在庫を初期化
                    initializeStoreInventory(foodNPC.getUniqueId());
                    
                    plugin.getLogger().info("========================================");
                    plugin.getLogger().info("食料NPC配置成功:");
                    plugin.getLogger().info("  名前: " + name);
                    plugin.getLogger().info("  UUID: " + foodNPC.getUniqueId());
                    plugin.getLogger().info("  座標: [" + x + ", " + y + ", " + z + "]");
                    plugin.getLogger().info("  ワールド: " + world);
                    plugin.getLogger().info("========================================");
                } else {
                    plugin.getLogger().severe("食料NPCの生成に失敗しました: " + name + " at [" + x + ", " + y + ", " + z + "]");
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("食料NPC生成中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 食料NPCの設定
     */
    private void setupFoodNPC(Villager npc) {
        npc.setProfession(Villager.Profession.BUTCHER);
        npc.setVillagerType(Villager.Type.PLAINS);
        npc.setVillagerLevel(5);
    }
    
    /**
     * 食料アイテムの価格マップを構築
     */
    /**
     * NPCタイプに応じた商品価格を構築
     */
    private Map<Material, Double> buildFoodItemPrices(String npcType) {
        Map<Material, Double> allPrices = buildBaseFoodItemPrices();
        Map<Material, Double> filteredPrices = new HashMap<>();
        
        double priceMultiplier = configManager.getFoodNPCPriceMultiplier(npcType);
        List<String> allowedItems = configManager.getFoodNPCTypeItems(npcType);
        boolean isGeneral = configManager.isFoodNPCTypeGeneral(npcType);
        
        for (Map.Entry<Material, Double> entry : allPrices.entrySet()) {
            Material material = entry.getKey();
            double basePrice = entry.getValue();
            
            // 商品フィルタリング
            String materialName = material.toString().toLowerCase();
            boolean isAllowed = isGeneral || allowedItems.contains(materialName);
            
            if (isAllowed) {
                // 価格倍率を適用
                double finalPrice = basePrice * priceMultiplier;
                filteredPrices.put(material, finalPrice);
            }
        }
        
        plugin.getLogger().info("NPCタイプ " + npcType + " の商品価格構築完了: " + filteredPrices.size() + "アイテム (倍率: " + priceMultiplier + ")");
        return filteredPrices;
    }
    
    /**
     * 基本食料価格を構築（旧buildFoodItemPricesメソッド）
     */
    private Map<Material, Double> buildBaseFoodItemPrices() {
        Map<Material, Double> prices = new HashMap<>();
        Map<String, Double> configPrices = configManager.getFoodItemPrices();
        
        for (Map.Entry<String, Double> entry : configPrices.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey().toUpperCase());
                double price = entry.getValue();
                prices.put(material, price);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なマテリアル名です: " + entry.getKey());
            }
        }
        
        return prices;
    }
    
    /**
     * 店舗在庫の初期化
     */
    private void initializeStoreInventory(UUID storeId) {
        Map<Material, Integer> inventory = new HashMap<>();
        Map<String, Double> configPrices = configManager.getFoodItemPrices();
        int stockLimit = configManager.getFoodNPCDailyStockLimit();
        
        for (String itemName : configPrices.keySet()) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                inventory.put(material, stockLimit);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なマテリアル名です: " + itemName);
            }
        }
        
        storeInventories.put(storeId, inventory);
    }
    
    /**
     * 食料NPC相互作用の処理
     */
    public boolean handleFoodNPCInteraction(Player player, UUID npcId) {
        plugin.getLogger().info("=== 食料NPC相互作用開始 ===");
        plugin.getLogger().info("プレイヤー: " + player.getName() + ", NPC ID: " + npcId);
        
        try {
            // NPCデータ取得
            FoodStore foodStore = foodStores.get(npcId);
            if (foodStore == null) {
                plugin.getLogger().warning("食料店データが見つかりません: " + npcId);
                plugin.getLogger().warning("登録済み食料店数: " + foodStores.size());
                plugin.getLogger().warning("登録済み食料店ID: " + foodStores.keySet());
                return false;
            }
            
            // 営業時間チェック
            if (!isOperatingHours()) {
                player.sendMessage("§c「申し訳ありません。営業時間は22:00-8:00です」");
                return true;
            }
            
            // 日次リセットチェック
            checkDailyReset();
            
            // 挨拶メッセージ
            player.sendMessage("§6「夜分にすみません、" + player.getName() + "さん」");
            player.sendMessage("§e「緊急時の食料でしたら、こちらで調達できますよ」");
            
            // 購入GUIを表示
            if (plugin.getFoodGUI() != null) {
                plugin.getFoodGUI().openFoodGUI(player, npcId, foodStore.getName());
            } else {
                // フォールバック: チャットメニュー
                showFoodMenu(player, foodStore);
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPC処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。管理者にお知らせください。");
            e.printStackTrace();
            return true;
        }
    }
    
    /**
     * 営業時間チェック
     */
    private boolean isOperatingHours() {
        if (!configManager.isFoodNPCOperatingHoursEnabled()) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int startHour = configManager.getFoodNPCStartHour();
        int endHour = configManager.getFoodNPCEndHour();
        
        // 22:00-8:00の場合の処理
        if (startHour > endHour) {
            return currentHour >= startHour || currentHour <= endHour;
        } else {
            return currentHour >= startHour && currentHour <= endHour;
        }
    }
    
    /**
     * 食料メニューの表示
     */
    private void showFoodMenu(Player player, FoodStore foodStore) {
        player.sendMessage("§a=== 食料品メニュー ===");
        
        Map<Material, Integer> storeInventory = storeInventories.get(foodStore.getNpcId());
        Map<Material, Integer> playerPurchases = dailyPurchases.getOrDefault(player.getUniqueId(), new HashMap<>());
        int dailyLimit = configManager.getFoodNPCDailyLimitPerItem();
        
        for (Map.Entry<Material, Double> entry : foodStore.getItemPrices().entrySet()) {
            Material material = entry.getKey();
            double price = entry.getValue();
            
            int stock = storeInventory.getOrDefault(material, 0);
            int purchased = playerPurchases.getOrDefault(material, 0);
            int remaining = dailyLimit - purchased;
            
            String stockStatus = stock > 0 ? "§a在庫あり" : "§c売り切れ";
            String limitStatus = remaining > 0 ? "§7(残り" + remaining + "個購入可能)" : "§c(本日上限達成)";
            
            String formattedPrice = currencyConverter.formatCurrency(price);
            player.sendMessage("§f• " + material.toString().toLowerCase() + ": §e" + formattedPrice + " " + stockStatus + " " + limitStatus);
        }
        
        player.sendMessage("§e手に持ったアイテムと同じものを右クリックで購入できます");
        player.sendMessage("§7例: パンを持ってNPCを右クリック→パンを購入");
    }
    
    /**
     * 食料アイテムの購入処理
     */
    public PurchaseResult processFoodPurchase(Player player, UUID npcId, Material itemType, int amount) {
        FoodStore foodStore = foodStores.get(npcId);
        if (foodStore == null) {
            return new PurchaseResult(false, "食料店が見つかりません", 0.0);
        }
        
        // 営業時間チェック
        if (!isOperatingHours()) {
            return new PurchaseResult(false, "営業時間外です（22:00-8:00）", 0.0);
        }
        
        // アイテム販売チェック
        if (!foodStore.sellsItem(itemType)) {
            return new PurchaseResult(false, itemType.toString() + "は販売していません", 0.0);
        }
        
        // 在庫チェック
        Map<Material, Integer> storeInventory = storeInventories.get(npcId);
        int currentStock = storeInventory.getOrDefault(itemType, 0);
        if (currentStock < amount) {
            return new PurchaseResult(false, itemType.toString() + "の在庫が不足しています（在庫: " + currentStock + "個）", 0.0);
        }
        
        // 購入制限チェック
        Map<Material, Integer> playerPurchases = dailyPurchases.getOrDefault(player.getUniqueId(), new HashMap<>());
        int alreadyPurchased = playerPurchases.getOrDefault(itemType, 0);
        int dailyLimit = configManager.getFoodNPCDailyLimitPerItem();
        
        if (alreadyPurchased + amount > dailyLimit) {
            int remaining = dailyLimit - alreadyPurchased;
            return new PurchaseResult(false, "1日の購入上限を超えています（残り購入可能: " + remaining + "個）", 0.0);
        }
        
        // 価格計算
        double unitPrice = foodStore.getItemPrice(itemType);
        double totalPrice = unitPrice * amount;
        
        // 所持金チェック
        if (!currencyConverter.canAffordWithCash(player, totalPrice)) {
            return new PurchaseResult(false, "所持金が不足しています（必要: " + currencyConverter.formatCurrency(totalPrice) + "）", 0.0);
        }
        
        // 購入処理実行（所持金から支払い）
        if (!currencyConverter.payWithCash(player, totalPrice)) {
            return new PurchaseResult(false, "支払い処理に失敗しました", 0.0);
        }
        
        // アイテム付与
        ItemStack item = new ItemStack(itemType, amount);
        player.getInventory().addItem(item);
        
        // 在庫減少
        storeInventory.put(itemType, currentStock - amount);
        
        // 購入履歴更新
        playerPurchases.put(itemType, alreadyPurchased + amount);
        dailyPurchases.put(player.getUniqueId(), playerPurchases);
        
        return new PurchaseResult(true, "購入完了", totalPrice);
    }
    
    /**
     * 日次リセット処理
     */
    private void checkDailyReset() {
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        if (!currentDate.equals(lastResetDate)) {
            plugin.getLogger().info("食料NPCの日次リセットを実行: " + currentDate);
            
            // 購入履歴リセット
            dailyPurchases.clear();
            
            // 在庫リセット
            for (UUID storeId : foodStores.keySet()) {
                initializeStoreInventory(storeId);
            }
            
            lastResetDate = currentDate;
            plugin.getLogger().info("食料NPCの日次リセット完了");
        }
    }
    
    /**
     * リセット日付の初期化
     */
    private void initializeResetDate() {
        lastResetDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    /**
     * 購入結果クラス
     */
    public static class PurchaseResult {
        private final boolean success;
        private final String message;
        private final double totalPrice;
        
        public PurchaseResult(boolean success, String message, double totalPrice) {
            this.success = success;
            this.message = message;
            this.totalPrice = totalPrice;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public double getTotalPrice() { return totalPrice; }
    }
    
    /**
     * 食料NPCの削除
     */
    public void removeFoodNPCs() {
        Collection<NPCManager.NPCData> foodNPCs = npcManager.getNPCsByType("food_merchant");
        for (NPCManager.NPCData npcData : foodNPCs) {
            npcManager.removeNPC(npcData.getEntityId());
        }
        foodStores.clear();
        storeInventories.clear();
        dailyPurchases.clear();
        plugin.getLogger().info("全ての食料NPCを削除しました");
    }
    
    /**
     * 食料NPCのリロード
     */
    public void reloadFoodNPCs() {
        plugin.getLogger().info("食料NPCをリロードしています...");
        removeFoodNPCs();
        initializeFoodNPCs();
        plugin.getLogger().info("食料NPCのリロードが完了しました");
    }
    
    /**
     * 指定プレイヤーの購入履歴取得
     */
    public Map<Material, Integer> getPlayerDailyPurchases(UUID playerId) {
        return dailyPurchases.getOrDefault(playerId, new HashMap<>());
    }
    
    /**
     * 指定店舗の在庫情報取得
     */
    public Map<Material, Integer> getStoreInventory(UUID storeId) {
        return storeInventories.getOrDefault(storeId, new HashMap<>());
    }

    /**
     * 手動スポーンされたNPCをFoodNPCManagerに登録
     */
    public void registerFoodNPC(Villager villager, String name, String npcType) {
        UUID npcId = villager.getUniqueId();
        Location location = villager.getLocation();
        
        plugin.getLogger().info("=== 食料NPC登録開始 ===");
        plugin.getLogger().info("手動スポーンされた食料NPCを登録中: " + name + " (ID: " + npcId + ")");
        plugin.getLogger().info("Location: " + location.toString());
        
        try {
            // Villagerの設定
            setupFoodNPC(villager);
            plugin.getLogger().info("VillagerのセットアップOK");
            
            // NPCタイプの検証
            if (npcType == null || npcType.isEmpty()) {
                npcType = "general_store"; // デフォルト
            }
            
            // 有効なNPCタイプかチェック
            if (!configManager.getFoodNPCTypes().contains(npcType)) {
                plugin.getLogger().warning("無効なNPCタイプ: " + npcType + "。general_storeを使用します。");
                npcType = "general_store";
            }
            
            // FoodStoreデータを作成
            Map<Material, Double> prices = buildFoodItemPrices(npcType);
            plugin.getLogger().info("価格データ作成OK: " + prices.size() + "アイテム");
            
            FoodStore foodStore = new FoodStore(npcId, name, location, prices, npcType);
            foodStores.put(npcId, foodStore);
            plugin.getLogger().info("FoodStoreデータ登録OK - 現在の登録数: " + foodStores.size());
            
            // 在庫を初期化
            initializeStoreInventory(npcId);
            plugin.getLogger().info("在庫初期化OK");
            
            plugin.getLogger().info("=== 食料NPCの登録が完了しました: " + name + " ===");
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPC登録中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}