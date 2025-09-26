package org.tofu.tofunomics.jobs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.*;

/**
 * 職業別クラフト制限管理クラス
 */
public class JobCraftPermissionManager {
    
    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final ConfigManager configManager;
    private final Map<String, Set<Material>> jobCraftableItems;
    private final Set<Material> publicCraftableItems;
    
    public JobCraftPermissionManager(JavaPlugin plugin, JobManager jobManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.jobCraftableItems = new HashMap<>();
        this.publicCraftableItems = new HashSet<>();
        
        initializeJobCraftableItems();
        initializePublicCraftableItems();
    }
    
    /**
     * 職業ごとのクラフト可能アイテムを初期化
     */
    private void initializeJobCraftableItems() {
        // 鍛冶屋 (blacksmith) - 防具、武器、かまど
        Set<Material> blacksmithItems = new HashSet<>(Arrays.asList(
            // 防具類
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            // 武器類
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, 
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            // 設備類
            Material.FURNACE, Material.BLAST_FURNACE, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.GRINDSTONE, Material.SMITHING_TABLE
        ));
        jobCraftableItems.put("blacksmith", blacksmithItems);
        
        // 鉱夫 (miner) - つるはし、石関連
        Set<Material> minerItems = new HashSet<>(Arrays.asList(
            // つるはし類
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, 
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            // 石関連
            Material.STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.POLISHED_GRANITE,
            Material.POLISHED_DIORITE, Material.POLISHED_ANDESITE, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.STONE_STAIRS, Material.STONE_BRICK_STAIRS,
            Material.COBBLESTONE_STAIRS, Material.STONE_SLAB, Material.STONE_BRICK_SLAB,
            Material.COBBLESTONE_SLAB, Material.STONE_BRICK_WALL, Material.COBBLESTONE_WALL,
            Material.STONE_BRICK_WALL
        ));
        jobCraftableItems.put("miner", minerItems);
        
        // 農家 (farmer) - くわ、食料関連
        Set<Material> farmerItems = new HashSet<>(Arrays.asList(
            // くわ類
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, 
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            // 食料関連
            Material.BREAD, Material.CAKE, Material.COOKIE, Material.PUMPKIN_PIE,
            Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.BEETROOT_SOUP,
            Material.SUSPICIOUS_STEW, Material.HONEY_BOTTLE,
            // 農業関連設備
            Material.COMPOSTER, Material.FLOWER_POT, Material.CAULDRON,
            Material.BARREL, Material.SMOKER
        ));
        jobCraftableItems.put("farmer", farmerItems);
        
        // 木こり (woodcutter) - 斧、木関連
        Set<Material> woodcutterItems = new HashSet<>(Arrays.asList(
            // 斧類
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            // 木材関連
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.CRIMSON_PLANKS, Material.WARPED_PLANKS,
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS, Material.JUNGLE_STAIRS,
            Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS, Material.CRIMSON_STAIRS, Material.WARPED_STAIRS,
            Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB, Material.JUNGLE_SLAB,
            Material.ACACIA_SLAB, Material.DARK_OAK_SLAB, Material.CRIMSON_SLAB, Material.WARPED_SLAB,
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE, Material.DARK_OAK_FENCE, Material.CRIMSON_FENCE, Material.WARPED_FENCE,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            Material.CHEST, Material.TRAPPED_CHEST, Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE,
            Material.FLETCHING_TABLE, Material.LOOM, Material.LECTERN
        ));
        jobCraftableItems.put("woodcutter", woodcutterItems);
        
        // 釣り人 (fisherman) - 船、釣竿
        Set<Material> fishermanItems = new HashSet<>(Arrays.asList(
            // 釣竿
            Material.FISHING_ROD,
            // 船類
            Material.OAK_BOAT, Material.SPRUCE_BOAT, Material.BIRCH_BOAT, Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT, Material.DARK_OAK_BOAT
        ));
        jobCraftableItems.put("fisherman", fishermanItems);
        
        // ポーション屋 (alchemist) - ポーション、醸造関連
        Set<Material> alchemistItems = new HashSet<>(Arrays.asList(
            // 醸造関連設備
            Material.BREWING_STAND, Material.CAULDRON,
            // ポーション関連（基本的なもの）
            Material.GLASS_BOTTLE, Material.FERMENTED_SPIDER_EYE, Material.GLISTERING_MELON_SLICE,
            Material.GOLDEN_CARROT, Material.MAGMA_CREAM, Material.BLAZE_POWDER
        ));
        jobCraftableItems.put("alchemist", alchemistItems);
        
        // エンチャンター (enchanter) - エンチャント関連
        Set<Material> enchanterItems = new HashSet<>(Arrays.asList(
            // エンチャント関連
            Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.BOOK,
            Material.WRITABLE_BOOK, Material.WRITTEN_BOOK, Material.PAPER,
            Material.ITEM_FRAME, Material.ITEM_FRAME
        ));
        jobCraftableItems.put("enchanter", enchanterItems);
        
        // 建築家 (builder) - 建築関連ブロック
        Set<Material> builderItems = new HashSet<>(Arrays.asList(
            // 装飾ブロック
            Material.BRICKS, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
            Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.CHISELED_QUARTZ_BLOCK,
            Material.QUARTZ_PILLAR, Material.QUARTZ_STAIRS, Material.QUARTZ_SLAB,
            Material.BRICK_STAIRS, Material.BRICK_SLAB, Material.BRICK_WALL,
            Material.NETHER_BRICK_STAIRS, Material.NETHER_BRICK_SLAB, Material.NETHER_BRICK_WALL,
            Material.RED_NETHER_BRICK_STAIRS, Material.RED_NETHER_BRICK_SLAB, Material.RED_NETHER_BRICK_WALL,
            // 各種階段・ハーフブロック・塀
            Material.SANDSTONE_STAIRS, Material.SANDSTONE_SLAB, Material.SANDSTONE_WALL,
            Material.RED_SANDSTONE_STAIRS, Material.RED_SANDSTONE_SLAB, Material.RED_SANDSTONE_WALL,
            Material.PRISMARINE_STAIRS, Material.PRISMARINE_SLAB, Material.PRISMARINE_WALL,
            // ガラス関連
            Material.GLASS, Material.GLASS_PANE, Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS, Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS, Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS, Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS, Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS, Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS, Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS, Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS, Material.BLACK_STAINED_GLASS_PANE
        ));
        jobCraftableItems.put("builder", builderItems);
    }
    
    /**
     * 誰でもクラフト可能なアイテムを初期化
     */
    private void initializePublicCraftableItems() {
        publicCraftableItems.addAll(Arrays.asList(
            // 基本的なサバイバル用品
            Material.STICK, Material.TORCH, Material.LADDER, 
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
            Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
            Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED,
            // 基本ツール（木製のみ）
            Material.WOODEN_SWORD, Material.WOODEN_PICKAXE, Material.WOODEN_AXE, 
            Material.WOODEN_SHOVEL, Material.WOODEN_HOE,
            // 基本的な食料
            Material.BOWL, Material.BUCKET,
            // その他生活必需品
            Material.COAL, Material.CHARCOAL, Material.STRING, Material.LEATHER
        ));
    }
    
    /**
     * プレイヤーが指定されたアイテムをクラフトできるかチェック
     */
    public boolean canPlayerCraftItem(Player player, Material material) {
        // null チェック
        if (player == null || material == null) {
            plugin.getLogger().warning("canPlayerCraftItem: player または material が null です");
            return false;
        }
        
        // publicCraftableItems の null チェック
        if (publicCraftableItems == null) {
            plugin.getLogger().warning("publicCraftableItems が初期化されていません");
            return false;
        }
        
        // パブリック（誰でもクラフト可能）アイテムはチェック不要
        if (publicCraftableItems.contains(material)) {
            return true;
        }
        
        // jobManager の null チェック
        if (jobManager == null) {
            plugin.getLogger().warning("jobManager が初期化されていません");
            return false;
        }
        
        // プレイヤーの職業を取得
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        
        // 無職の場合はパブリックアイテムのみ
        if (playerJob == null) {
            return false;
        }
        
        // jobCraftableItems の null チェック
        if (jobCraftableItems == null) {
            plugin.getLogger().warning("jobCraftableItems が初期化されていません");
            return false;
        }
        
        // 該当職業でクラフト可能かチェック
        Set<Material> jobItems = jobCraftableItems.get(playerJob);
        return jobItems != null && jobItems.contains(material);
    }
    
    /**
     * クラフト制限時のメッセージを取得
     */
    public String getCraftDeniedMessage(Player player, Material material) {
        // null チェック
        if (player == null || material == null) {
            plugin.getLogger().warning("getCraftDeniedMessage: player または material が null です");
            return "§cクラフト制限エラー: 無効なパラメータです。";
        }
        
        if (jobManager == null) {
            plugin.getLogger().warning("jobManager が初期化されていません");
            return "§cシステムエラー: 職業管理システムが利用できません。";
        }
        
        if (configManager == null) {
            plugin.getLogger().warning("configManager が初期化されていません");
            return "§cシステムエラー: 設定管理システムが利用できません。";
        }
        
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        
        if (playerJob == null) {
            String message = configManager.getMessage("messages.craft.no_job_required");
            
            // フォールバック処理
            if (message.startsWith("メッセージが見つかりません:")) {
                return "§c職業に就いていないため、このアイテムをクラフトできません。";
            }
            return message;
        }
        
        // どの職業でクラフト可能かを調べる
        String requiredJob = null;
        for (Map.Entry<String, Set<Material>> entry : jobCraftableItems.entrySet()) {
            if (entry.getValue().contains(material)) {
                requiredJob = entry.getKey();
                break;
            }
        }
        
        plugin.getLogger().info("必要職業: " + (requiredJob != null ? requiredJob : "なし"));
        
        if (requiredJob != null) {
            String message = configManager.getMessage("messages.craft.wrong_job_required")
                .replace("{item}", material.name().toLowerCase())
                .replace("{required_job}", configManager.getJobDisplayName(requiredJob))
                .replace("{current_job}", configManager.getJobDisplayName(playerJob));
            
            plugin.getLogger().info("職業違いメッセージ: " + message);
            
            // フォールバック処理
            if (message.startsWith("メッセージが見つかりません:")) {
                String fallbackMessage = "§c" + material.name().toLowerCase() + "をクラフトするには" + 
                    configManager.getJobDisplayName(requiredJob) + "である必要があります。（現在: " + 
                    configManager.getJobDisplayName(playerJob) + "）";
                plugin.getLogger().warning("設定メッセージが見つからないため、フォールバックメッセージを使用: " + fallbackMessage);
                return fallbackMessage;
            }
            return message;
        }
        
        String message = configManager.getMessage("messages.craft.item_not_craftable");
        plugin.getLogger().info("クラフト不可メッセージ: " + message);
        
        // フォールバック処理
        if (message.startsWith("メッセージが見つかりません:")) {
            String fallbackMessage = "§cこのアイテムはクラフトできません。";
            plugin.getLogger().warning("設定メッセージが見つからないため、フォールバックメッセージを使用: " + fallbackMessage);
            return fallbackMessage;
        }
        return message;
    }
    
    /**
     * デバッグ用：職業のクラフト可能アイテム数を表示
     */
    public void logJobCraftableItemCounts() {
        plugin.getLogger().info("=== 職業別クラフト可能アイテム数 ===");
        for (Map.Entry<String, Set<Material>> entry : jobCraftableItems.entrySet()) {
            plugin.getLogger().info(entry.getKey() + ": " + entry.getValue().size() + "種類");
        }
        plugin.getLogger().info("パブリック: " + publicCraftableItems.size() + "種類");
    }
}