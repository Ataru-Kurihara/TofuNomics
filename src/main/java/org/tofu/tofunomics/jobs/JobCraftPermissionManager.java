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
            Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            // 1.21追加武器（メイス）
            Material.MACE,
            // 防御・鉄製基本道具
            Material.SHIELD, Material.SHEARS, Material.FLINT_AND_STEEL, Material.ARROW,
            // 鉄製ツール（コンパス）
            Material.COMPASS, Material.RECOVERY_COMPASS,
            // 設備類
            Material.FURNACE, Material.BLAST_FURNACE, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.GRINDSTONE, Material.SMITHING_TABLE,
            // 作業台
            Material.CRAFTING_TABLE
        ));
        jobCraftableItems.put("blacksmith", blacksmithItems);
        
        // 鉱夫 (miner) - つるはし、石関連
        Set<Material> minerItems = new HashSet<>(Arrays.asList(
            // つるはし類
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            // シャベル類（採掘・整地用）
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            // 石関連
            Material.STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.POLISHED_GRANITE,
            Material.POLISHED_DIORITE, Material.POLISHED_ANDESITE, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.STONE_STAIRS, Material.STONE_BRICK_STAIRS,
            Material.COBBLESTONE_STAIRS, Material.STONE_SLAB, Material.STONE_BRICK_SLAB,
            Material.COBBLESTONE_SLAB, Material.STONE_BRICK_WALL, Material.COBBLESTONE_WALL,
            Material.STONE_BRICK_WALL,
            // 照明
            Material.LANTERN,
            // 鉱石の収納ブロック
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.COAL_BLOCK,
            Material.REDSTONE_BLOCK, Material.LAPIS_BLOCK, Material.EMERALD_BLOCK,
            Material.COPPER_BLOCK,
            // 分解レシピ結果（ブロック→素材、インゴット→塊）
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.REDSTONE,
            Material.LAPIS_LAZULI, Material.EMERALD, Material.NETHERITE_INGOT, Material.COPPER_INGOT,
            Material.IRON_NUGGET, Material.GOLD_NUGGET,
            // 生鉱石の収納ブロック・分解
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
            Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,
            // レール・トロッコ（採掘・運搬）
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL,
            Material.MINECART, Material.CHEST_MINECART, Material.HOPPER_MINECART,
            Material.FURNACE_MINECART, Material.TNT_MINECART,
            // 作業台
            Material.CRAFTING_TABLE
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
            // 農業のタネ類（バニラレシピでクラフト可能）
            Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            // 農業の生産・畜産関連
            Material.BONE_MEAL, Material.HAY_BLOCK, Material.SUGAR, Material.LEAD,
            // 動物装備（鞍・革の馬鎧）
            Material.SADDLE, Material.LEATHER_HORSE_ARMOR,
            // 農業関連設備
            Material.COMPOSTER, Material.FLOWER_POT, Material.CAULDRON,
            Material.SMOKER,
            // 作業台
            Material.CRAFTING_TABLE
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
            Material.FLETCHING_TABLE, Material.LOOM, Material.LECTERN, Material.BARREL,
            // 収納容器（バンドル）
            Material.BUNDLE,
            // 看板類
            Material.OAK_SIGN, Material.SPRUCE_SIGN, Material.BIRCH_SIGN, Material.JUNGLE_SIGN,
            Material.ACACIA_SIGN, Material.DARK_OAK_SIGN, Material.CRIMSON_SIGN, Material.WARPED_SIGN,
            Material.OAK_HANGING_SIGN, Material.SPRUCE_HANGING_SIGN, Material.BIRCH_HANGING_SIGN, Material.JUNGLE_HANGING_SIGN,
            Material.ACACIA_HANGING_SIGN, Material.DARK_OAK_HANGING_SIGN, Material.CRIMSON_HANGING_SIGN, Material.WARPED_HANGING_SIGN,
            // 開閉・装置系（落とし戸・ボタン・感圧板）
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE
        ));
        jobCraftableItems.put("woodcutter", woodcutterItems);
        
        // 釣り人 (fisherman) - 船、釣竿
        Set<Material> fishermanItems = new HashSet<>(Arrays.asList(
            // 釣竿
            Material.FISHING_ROD,
            // 船類
            Material.OAK_BOAT, Material.SPRUCE_BOAT, Material.BIRCH_BOAT, Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT, Material.DARK_OAK_BOAT,
            Material.CHERRY_BOAT, Material.MANGROVE_BOAT, Material.BAMBOO_RAFT,
            // チェスト付きの船
            Material.OAK_CHEST_BOAT, Material.SPRUCE_CHEST_BOAT, Material.BIRCH_CHEST_BOAT, Material.JUNGLE_CHEST_BOAT,
            Material.ACACIA_CHEST_BOAT, Material.DARK_OAK_CHEST_BOAT, Material.CHERRY_CHEST_BOAT,
            Material.MANGROVE_CHEST_BOAT, Material.BAMBOO_CHEST_RAFT,
            // 作業台
            Material.CRAFTING_TABLE
        ));
        jobCraftableItems.put("fisherman", fishermanItems);
        
        // ポーション屋 (alchemist) - ポーション、醸造関連
        Set<Material> alchemistItems = new HashSet<>(Arrays.asList(
            // 醸造関連設備
            Material.BREWING_STAND, Material.CAULDRON,
            // ポーション関連（基本的なもの）
            Material.GLASS_BOTTLE, Material.FERMENTED_SPIDER_EYE, Material.GLISTERING_MELON_SLICE,
            Material.GOLDEN_CARROT, Material.MAGMA_CREAM, Material.BLAZE_POWDER,
            // ポーション効果増幅用
            Material.GLOWSTONE,
            // 作業台
            Material.CRAFTING_TABLE
        ));
        jobCraftableItems.put("alchemist", alchemistItems);
        
        // エンチャンター (enchanter) - エンチャント関連
        Set<Material> enchanterItems = new HashSet<>(Arrays.asList(
            // エンチャント関連
            Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.CHISELED_BOOKSHELF, Material.BOOK,
            Material.WRITABLE_BOOK, Material.WRITTEN_BOOK, Material.PAPER,
            Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
            // 作業台
            Material.CRAFTING_TABLE
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
            // 各種階段・ハーフブロック・壁
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
            Material.BLACK_STAINED_GLASS, Material.BLACK_STAINED_GLASS_PANE,
            // 鉄格子
            Material.IRON_BARS,
            // レッドストーン装置
            Material.LEVER, Material.REDSTONE_TORCH, Material.REPEATER, Material.COMPARATOR,
            Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER, Material.DROPPER,
            Material.OBSERVER, Material.REDSTONE_LAMP, Material.NOTE_BLOCK,
            Material.DAYLIGHT_DETECTOR, Material.TARGET, Material.TRIPWIRE_HOOK, Material.HOPPER,
            // 羊毛（全16色）
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL,
            // カーペット（全16色）
            Material.WHITE_CARPET, Material.ORANGE_CARPET, Material.MAGENTA_CARPET, Material.LIGHT_BLUE_CARPET,
            Material.YELLOW_CARPET, Material.LIME_CARPET, Material.PINK_CARPET, Material.GRAY_CARPET,
            Material.LIGHT_GRAY_CARPET, Material.CYAN_CARPET, Material.PURPLE_CARPET, Material.BLUE_CARPET,
            Material.BROWN_CARPET, Material.GREEN_CARPET, Material.RED_CARPET, Material.BLACK_CARPET,
            // 作業台
            Material.CRAFTING_TABLE
        ));
        // 銅建材（装飾・1.21新建材含む。酸化段階×waxed有無を網羅）
        addCopperBuildingMaterials(builderItems);
        jobCraftableItems.put("builder", builderItems);
    }

    /**
     * 銅系建材を網羅的に追加する。
     * 酸化4段階（通常/exposed/weathered/oxidized）× waxed有無の全バリアントを対象とし、
     * APIバージョンに存在しない定数は安全にスキップする。
     */
    private void addCopperBuildingMaterials(Set<Material> items) {
        // 酸化段階プレフィックス（"" は通常）
        String[] oxidation = {"", "EXPOSED_", "WEATHERED_", "OXIDIZED_"};
        // 銅建材のベース名
        String[] bases = {
            "CUT_COPPER", "CUT_COPPER_STAIRS", "CUT_COPPER_SLAB",
            "CHISELED_COPPER", "COPPER_GRATE", "COPPER_BULB",
            "COPPER_DOOR", "COPPER_TRAPDOOR"
        };
        for (String ox : oxidation) {
            for (String base : bases) {
                addMaterialIfExists(items, ox + base);           // 非waxed
                addMaterialIfExists(items, "WAXED_" + ox + base); // waxed
            }
        }
        // 銅ブロック本体のwaxed版・避雷針
        String[] extras = {
            "WAXED_COPPER_BLOCK", "WAXED_EXPOSED_COPPER", "WAXED_WEATHERED_COPPER", "WAXED_OXIDIZED_COPPER",
            "LIGHTNING_ROD"
        };
        for (String name : extras) {
            addMaterialIfExists(items, name);
        }
    }

    /**
     * 指定名のMaterial定数が存在すればSetに追加する（存在しなければスキップ）。
     */
    private void addMaterialIfExists(Set<Material> items, String name) {
        try {
            items.add(Material.valueOf(name));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().fine("Material定数が存在しないためスキップ: " + name);
        }
    }
    
    /**
     * 誰でもクラフト可能なアイテムを初期化
     */
    private void initializePublicCraftableItems() {
        publicCraftableItems.addAll(Arrays.asList(
            // 基本的な武器
            Material.WOODEN_SWORD,
            // 基本的なサバイバル用品
            Material.STICK, Material.TORCH, Material.LADDER, 
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
            Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
            Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED,
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