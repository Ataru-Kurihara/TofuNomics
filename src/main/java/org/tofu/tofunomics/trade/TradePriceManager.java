package org.tofu.tofunomics.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.TradeChest;

import java.util.HashMap;
import java.util.Map;

/**
 * 取引価格計算システム
 */
public class TradePriceManager {
    
    private final ConfigManager configManager;
    private final JobManager jobManager;
    
    // 基本価格テーブル（職業別）
    private final Map<String, Map<Material, Double>> jobBasePrices;
    
    // 職業別価格ボーナス倍率
    private final Map<String, Map<Material, Double>> jobPriceMultipliers;
    
    public TradePriceManager(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.jobBasePrices = new HashMap<>();
        this.jobPriceMultipliers = new HashMap<>();
        
        initializePriceTables();
        initializePriceMultipliers();
    }
    
    /**
     * 基本価格テーブルを初期化
     */
    private void initializePriceTables() {
        // 鉱夫の買取アイテム
        Map<Material, Double> minerPrices = new HashMap<>();
        minerPrices.put(Material.COAL, 2.0);
        minerPrices.put(Material.IRON_INGOT, 8.0);
        minerPrices.put(Material.GOLD_INGOT, 15.0);
        minerPrices.put(Material.DIAMOND, 50.0);
        minerPrices.put(Material.EMERALD, 40.0);
        minerPrices.put(Material.LAPIS_LAZULI, 3.0);
        minerPrices.put(Material.REDSTONE, 2.5);
        minerPrices.put(Material.QUARTZ, 4.0);
        minerPrices.put(Material.NETHERITE_INGOT, 200.0);
        minerPrices.put(Material.STONE, 0.1);
        minerPrices.put(Material.COBBLESTONE, 0.05);
        minerPrices.put(Material.OBSIDIAN, 3.0);
        jobBasePrices.put("miner", minerPrices);
        
        // 木こりの買取アイテム
        Map<Material, Double> woodcutterPrices = new HashMap<>();
        woodcutterPrices.put(Material.OAK_LOG, 1.5);
        woodcutterPrices.put(Material.BIRCH_LOG, 1.5);
        woodcutterPrices.put(Material.SPRUCE_LOG, 1.5);
        woodcutterPrices.put(Material.JUNGLE_LOG, 2.0);
        woodcutterPrices.put(Material.ACACIA_LOG, 2.0);
        woodcutterPrices.put(Material.DARK_OAK_LOG, 2.0);
        woodcutterPrices.put(Material.WARPED_STEM, 3.0);
        woodcutterPrices.put(Material.CRIMSON_STEM, 3.0);
        woodcutterPrices.put(Material.OAK_PLANKS, 0.4);
        woodcutterPrices.put(Material.STICK, 0.1);
        jobBasePrices.put("woodcutter", woodcutterPrices);
        
        // 農家の買取アイテム
        Map<Material, Double> farmerPrices = new HashMap<>();
        farmerPrices.put(Material.WHEAT, 1.0);
        farmerPrices.put(Material.POTATO, 0.8);
        farmerPrices.put(Material.CARROT, 0.8);
        farmerPrices.put(Material.BEETROOT, 1.2);
        farmerPrices.put(Material.PUMPKIN, 2.5);
        farmerPrices.put(Material.MELON, 2.0);
        farmerPrices.put(Material.SUGAR_CANE, 0.8);
        farmerPrices.put(Material.COCOA_BEANS, 2.0);
        farmerPrices.put(Material.NETHER_WART, 2.5);
        farmerPrices.put(Material.APPLE, 1.5);
        farmerPrices.put(Material.SWEET_BERRIES, 1.0);
        jobBasePrices.put("farmer", farmerPrices);
        
        // 漁師の買取アイテム
        Map<Material, Double> fishermanPrices = new HashMap<>();
        fishermanPrices.put(Material.COD, 3.0);
        fishermanPrices.put(Material.SALMON, 4.0);
        fishermanPrices.put(Material.TROPICAL_FISH, 6.0);
        fishermanPrices.put(Material.PUFFERFISH, 8.0);
        fishermanPrices.put(Material.COOKED_COD, 4.5);
        fishermanPrices.put(Material.COOKED_SALMON, 6.0);
        fishermanPrices.put(Material.INK_SAC, 2.0);
        fishermanPrices.put(Material.PRISMARINE_SHARD, 3.0);
        fishermanPrices.put(Material.PRISMARINE_CRYSTALS, 5.0);
        jobBasePrices.put("fisherman", fishermanPrices);
        
        // 鍛冶屋の買取アイテム
        Map<Material, Double> blacksmithPrices = new HashMap<>();
        blacksmithPrices.put(Material.IRON_SWORD, 12.0);
        blacksmithPrices.put(Material.IRON_PICKAXE, 15.0);
        blacksmithPrices.put(Material.IRON_AXE, 15.0);
        blacksmithPrices.put(Material.IRON_SHOVEL, 8.0);
        blacksmithPrices.put(Material.IRON_HOE, 10.0);
        blacksmithPrices.put(Material.DIAMOND_SWORD, 30.0);
        blacksmithPrices.put(Material.DIAMOND_PICKAXE, 35.0);
        blacksmithPrices.put(Material.DIAMOND_AXE, 35.0);
        blacksmithPrices.put(Material.NETHERITE_SWORD, 100.0);
        blacksmithPrices.put(Material.IRON_HELMET, 20.0);
        blacksmithPrices.put(Material.IRON_CHESTPLATE, 32.0);
        blacksmithPrices.put(Material.IRON_LEGGINGS, 28.0);
        blacksmithPrices.put(Material.IRON_BOOTS, 16.0);
        jobBasePrices.put("blacksmith", blacksmithPrices);
        
        // 錬金術師の買取アイテム
        Map<Material, Double> alchemistPrices = new HashMap<>();
        alchemistPrices.put(Material.BLAZE_POWDER, 8.0);
        alchemistPrices.put(Material.GHAST_TEAR, 20.0);
        alchemistPrices.put(Material.SPIDER_EYE, 3.0);
        alchemistPrices.put(Material.FERMENTED_SPIDER_EYE, 5.0);
        alchemistPrices.put(Material.MAGMA_CREAM, 6.0);
        alchemistPrices.put(Material.GLISTERING_MELON_SLICE, 4.0);
        alchemistPrices.put(Material.GOLDEN_CARROT, 3.0);
        alchemistPrices.put(Material.RABBIT_FOOT, 7.0);
        alchemistPrices.put(Material.TURTLE_HELMET, 25.0);
        alchemistPrices.put(Material.PHANTOM_MEMBRANE, 10.0);
        jobBasePrices.put("alchemist", alchemistPrices);
        
        // エンチャンターの買取アイテム
        Map<Material, Double> enchanterPrices = new HashMap<>();
        enchanterPrices.put(Material.EXPERIENCE_BOTTLE, 15.0);
        enchanterPrices.put(Material.ENCHANTED_BOOK, 25.0);
        enchanterPrices.put(Material.BOOKSHELF, 12.0);
        enchanterPrices.put(Material.BOOK, 3.0);
        enchanterPrices.put(Material.PAPER, 0.5);
        enchanterPrices.put(Material.LEATHER, 2.0);
        enchanterPrices.put(Material.INK_SAC, 1.5);
        enchanterPrices.put(Material.LAPIS_LAZULI, 4.0);
        jobBasePrices.put("enchanter", enchanterPrices);
        
        // 建築家の買取アイテム
        Map<Material, Double> architectPrices = new HashMap<>();
        architectPrices.put(Material.STONE_BRICKS, 0.8);
        architectPrices.put(Material.QUARTZ_BLOCK, 2.0);
        architectPrices.put(Material.PRISMARINE, 3.0);
        architectPrices.put(Material.PURPUR_BLOCK, 4.0);
        architectPrices.put(Material.END_STONE_BRICKS, 5.0);
        architectPrices.put(Material.NETHER_BRICKS, 1.5);
        architectPrices.put(Material.RED_NETHER_BRICKS, 2.0);
        architectPrices.put(Material.BLACKSTONE, 1.0);
        architectPrices.put(Material.POLISHED_BLACKSTONE, 1.5);
        architectPrices.put(Material.GLASS, 0.3);
        architectPrices.put(Material.WHITE_STAINED_GLASS, 0.5);
        jobBasePrices.put("architect", architectPrices);
    }
    
    /**
     * 職業別価格倍率を初期化
     */
    private void initializePriceMultipliers() {
        // 各職業が得意とするアイテムの価格倍率
        Map<String, Double> minerMultipliers = new HashMap<>();
        // 鉱夫は鉱石系アイテムを高価買取
        jobPriceMultipliers.put("miner", new HashMap<>());
        
        Map<String, Double> woodcutterMultipliers = new HashMap<>(); 
        // 木こりは木材系を高価買取
        jobPriceMultipliers.put("woodcutter", new HashMap<>());
        
        // 他の職業も同様
        jobPriceMultipliers.put("farmer", new HashMap<>());
        jobPriceMultipliers.put("fisherman", new HashMap<>());
        jobPriceMultipliers.put("blacksmith", new HashMap<>());
        jobPriceMultipliers.put("alchemist", new HashMap<>());
        jobPriceMultipliers.put("enchanter", new HashMap<>());
        jobPriceMultipliers.put("architect", new HashMap<>());
    }
    
    /**
     * アイテムの販売価格を計算
     */
    public TradeResult calculateTradePrice(Player player, TradeChest tradeChest, 
                                         Material itemType, int amount) {
        String jobType = tradeChest.getJobType();
        
        // 基本価格を取得
        double basePrice = getBasePrice(jobType, itemType);
        if (basePrice <= 0) {
            return new TradeResult(false, "このチェストではそのアイテムは買取できません。", 0.0, 0.0);
        }
        
        // プレイヤーの職業情報を取得
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobType);
        
        // 職業ボーナス計算
        double jobBonus = calculateJobBonus(player, jobType, itemType, basePrice, amount);
        
        // 合計価格計算
        double totalBasePrice = basePrice * amount;
        double totalPrice = totalBasePrice + jobBonus;
        
        // 設定ファイルの価格倍率を適用
        double globalMultiplier = configManager.getTradePriceMultiplier();
        totalPrice *= globalMultiplier;
        jobBonus *= globalMultiplier;
        
        String message = String.format("§a%s x%d を %.2f金塊で買取します", 
                                     getItemDisplayName(itemType), amount, totalPrice);
        
        if (jobBonus > 0) {
            message += String.format(" §7(職業ボーナス: +%.2f)", jobBonus);
        }
        
        return new TradeResult(true, message, totalPrice - jobBonus, jobBonus);
    }
    
    /**
     * 基本価格を取得
     */
    private double getBasePrice(String jobType, Material itemType) {
        Map<Material, Double> jobPrices = jobBasePrices.get(jobType);
        if (jobPrices == null) {
            return 0.0;
        }
        
        return jobPrices.getOrDefault(itemType, 0.0);
    }
    
    /**
     * 職業ボーナスを計算
     */
    private double calculateJobBonus(Player player, String jobType, Material itemType, 
                                   double basePrice, int amount) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobType);
        if (playerJob == null) {
            return 0.0;
        }
        
        // レベル別ボーナス率（最大20%まで）
        double levelBonusRate = Math.min(playerJob.getLevel() * 0.005, 0.20);
        
        // 職業専門性ボーナス
        double specialtyBonus = getSpecialtyBonus(jobType, itemType);
        
        // 設定ファイルの価格倍率
        double configMultiplier = configManager.getJobPriceMultiplier(jobType);
        
        // 合計ボーナス率
        double totalBonusRate = (levelBonusRate + specialtyBonus) * configMultiplier;
        
        return basePrice * amount * totalBonusRate;
    }
    
    /**
     * 職業専門性ボーナスを取得
     */
    private double getSpecialtyBonus(String jobType, Material itemType) {
        // 各職業が得意とするアイテムのボーナス率
        switch (jobType.toLowerCase()) {
            case "miner":
                if (isMiningItem(itemType)) return 0.15;
                break;
            case "woodcutter":
                if (isWoodItem(itemType)) return 0.15;
                break;
            case "farmer":
                if (isFarmItem(itemType)) return 0.15;
                break;
            case "fisherman":
                if (isFishItem(itemType)) return 0.15;
                break;
            case "blacksmith":
                if (isSmithingItem(itemType)) return 0.15;
                break;
            case "alchemist":
                if (isAlchemyItem(itemType)) return 0.15;
                break;
            case "enchanter":
                if (isEnchantingItem(itemType)) return 0.15;
                break;
            case "architect":
                if (isBuildingItem(itemType)) return 0.15;
                break;
        }
        return 0.0;
    }
    
    /**
     * アイテム種別判定メソッド群
     */
    
    private boolean isMiningItem(Material material) {
        return material == Material.COAL || material == Material.IRON_INGOT || 
               material == Material.GOLD_INGOT || material == Material.DIAMOND ||
               material == Material.EMERALD || material == Material.LAPIS_LAZULI ||
               material == Material.REDSTONE || material == Material.QUARTZ ||
               material == Material.NETHERITE_INGOT;
    }
    
    private boolean isWoodItem(Material material) {
        String name = material.name();
        return name.contains("LOG") || name.contains("PLANKS") || 
               name.contains("WOOD") || material == Material.STICK;
    }
    
    private boolean isFarmItem(Material material) {
        return material == Material.WHEAT || material == Material.POTATO ||
               material == Material.CARROT || material == Material.BEETROOT ||
               material == Material.PUMPKIN || material == Material.MELON ||
               material == Material.SUGAR_CANE || material == Material.COCOA_BEANS ||
               material == Material.NETHER_WART;
    }
    
    private boolean isFishItem(Material material) {
        return material == Material.COD || material == Material.SALMON ||
               material == Material.TROPICAL_FISH || material == Material.PUFFERFISH ||
               material == Material.COOKED_COD || material == Material.COOKED_SALMON;
    }
    
    private boolean isSmithingItem(Material material) {
        String name = material.name();
        return name.contains("SWORD") || name.contains("PICKAXE") || 
               name.contains("AXE") || name.contains("SHOVEL") || 
               name.contains("HOE") || name.contains("HELMET") ||
               name.contains("CHESTPLATE") || name.contains("LEGGINGS") ||
               name.contains("BOOTS");
    }
    
    private boolean isAlchemyItem(Material material) {
        return material == Material.BLAZE_POWDER || material == Material.GHAST_TEAR ||
               material == Material.SPIDER_EYE || material == Material.FERMENTED_SPIDER_EYE ||
               material == Material.MAGMA_CREAM || material == Material.GLISTERING_MELON_SLICE ||
               material == Material.GOLDEN_CARROT || material == Material.RABBIT_FOOT;
    }
    
    private boolean isEnchantingItem(Material material) {
        return material == Material.EXPERIENCE_BOTTLE || material == Material.ENCHANTED_BOOK ||
               material == Material.BOOKSHELF || material == Material.BOOK ||
               material == Material.LAPIS_LAZULI;
    }
    
    private boolean isBuildingItem(Material material) {
        String name = material.name();
        return name.contains("BRICK") || name.contains("STONE") ||
               material == Material.QUARTZ_BLOCK || material == Material.PRISMARINE ||
               material == Material.PURPUR_BLOCK || material == Material.GLASS;
    }
    
    /**
     * アイテム表示名を取得
     */
    private String getItemDisplayName(Material material) {
        // 簡略実装（実際にはローカライズ対応が必要）
        return material.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * 取引結果クラス
     */
    public static class TradeResult {
        private final boolean success;
        private final String message;
        private final double basePrice;
        private final double jobBonus;
        
        public TradeResult(boolean success, String message, double basePrice, double jobBonus) {
            this.success = success;
            this.message = message;
            this.basePrice = basePrice;
            this.jobBonus = jobBonus;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public double getBasePrice() { return basePrice; }
        public double getJobBonus() { return jobBonus; }
        public double getTotalPrice() { return basePrice + jobBonus; }
    }
    
    /**
     * NPCシステム用の最終価格計算メソッド
     */
    public double calculateFinalPrice(String itemName, String jobType, double basePrice) {
        if (jobType == null || jobType.isEmpty()) {
            return basePrice;
        }
        
        // 職業倍率を適用
        double jobMultiplier = configManager.getJobPriceMultiplier(jobType);
        double finalPrice = basePrice * jobMultiplier;
        
        // グローバル倍率を適用
        double globalMultiplier = configManager.getTradePriceMultiplier();
        finalPrice *= globalMultiplier;
        
        return finalPrice;
    }
}