package org.tofu.tofunomics.jobs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.*;

/**
 * 職業別ブロック採掘権限管理システム
 * 基本ブロックは全職業で採掘可能、専門ブロックは対応職業のみ採掘可能
 */
public class JobBlockPermissionManager {
    
    private final ConfigManager configManager;
    private final JobManager jobManager;
    
    // 基本ブロック（全職業で採掘可能）
    private final Set<Material> basicBlocks;
    
    // 職業専用ブロック
    private final Map<String, Set<Material>> jobRestrictedBlocks;

    // 職業専用 植え付けブロック（種まきで設置されるブロック）
    private final Map<String, Set<Material>> jobPlantingBlocks;

    public JobBlockPermissionManager(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.basicBlocks = new HashSet<>();
        this.jobRestrictedBlocks = new HashMap<>();
        this.jobPlantingBlocks = new HashMap<>();

        initializeBasicBlocks();
        initializeJobRestrictedBlocks();
        initializeJobPlantingBlocks();
    }
    
    /**
     * 基本ブロック（全職業採掘可能）を初期化
     */
    private void initializeBasicBlocks() {
        // 基本的な建築・整地用ブロック
        basicBlocks.addAll(Arrays.asList(
            Material.STONE,
            Material.COBBLESTONE,
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.SAND,
            Material.GRAVEL,
            Material.SANDSTONE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.NETHERRACK,
            Material.END_STONE,
            Material.OBSIDIAN,
            Material.BEDROCK,
            // 原木類（建築基本素材として無職でも採取可能）
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG
        ));
    }
    
    /**
     * 職業別制限ブロックを初期化
     */
    private void initializeJobRestrictedBlocks() {
        // 鉱夫専用ブロック（鉱石類）- Minecraft 1.16.5対応
        Set<Material> minerBlocks = new HashSet<>(Arrays.asList(
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.LAPIS_ORE,
            Material.REDSTONE_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.COPPER_ORE  // 1.17追加。深層岩鉱石は正規化で本セットへ寄せる
        ));
        jobRestrictedBlocks.put("miner", minerBlocks);
        
        // 木こり専用ブロック（木材類）- Minecraft 1.16.5対応
        Set<Material> woodcutterBlocks = new HashSet<>(Arrays.asList(
// 葉ブロック類
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            // キノコブロック類
            Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK,
            Material.MUSHROOM_STEM
        ));
        jobRestrictedBlocks.put("woodcutter", woodcutterBlocks);
        // 農家専用ブロック（農作物・畜産関連）- Minecraft 1.16.5対応
        Set<Material> farmerBlocks = new HashSet<>(Arrays.asList(
            // 作物ブロック（成熟状態）
            Material.WHEAT,
            Material.POTATOES,
            Material.CARROTS,
            Material.BEETROOTS,
            Material.PUMPKIN,
            Material.MELON,
            // 特殊農業ブロック
            Material.COCOA,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            // 畜産関連ブロック
            Material.HAY_BLOCK,
            Material.DRIED_KELP_BLOCK
        ));
        jobRestrictedBlocks.put("farmer", farmerBlocks);
        jobRestrictedBlocks.put("fisherman", new HashSet<>());
        jobRestrictedBlocks.put("blacksmith", new HashSet<>());
        jobRestrictedBlocks.put("alchemist", new HashSet<>());
        jobRestrictedBlocks.put("enchanter", new HashSet<>());
        jobRestrictedBlocks.put("architect", new HashSet<>());
    }

    /**
     * 職業別 植え付け制限ブロックを初期化
     * 種まき時に設置されるブロック（成長ブロック・苗）を対象とする。
     * 注意: 装飾用フルブロック（PUMPKIN/MELON/HAY_BLOCK等）は含めない（誰でも設置可）
     */
    private void initializeJobPlantingBlocks() {
        // 農家専用の植え付けブロック
        Set<Material> farmerPlantingBlocks = new HashSet<>(Arrays.asList(
            // 種まきで設置される作物ブロック
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            // カボチャ・スイカの種は苗（STEM）として設置される
            Material.PUMPKIN_STEM,
            Material.MELON_STEM,
            // その他の作物
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.BAMBOO_SAPLING
        ));
        jobPlantingBlocks.put("farmer", farmerPlantingBlocks);
    }

    /**
     * プレイヤーが指定ブロックを採掘する権限があるかチェック
     * 
     * @param player プレイヤー
     * @param blockType ブロックタイプ
     * @return 採掘可能な場合true
     */
    public boolean canPlayerBreakBlock(Player player, Material blockType) {
        // 職業制限システムが無効の場合は常に許可
        if (!configManager.isJobBlockRestrictionEnabled()) {
            return true;
        }

        // 管理者権限を持つ場合は常に許可
        if (player.hasPermission("tofunomics.admin.break")) {
            return true;
        }

        // 深層岩鉱石・深層岩石材を通常版へ正規化（深層岩鉱石の採掘抜け穴を塞ぐ）
        blockType = org.tofu.tofunomics.util.BlockNormalizer.normalizeForJob(blockType);

        // 基本ブロックの場合は常に許可
        if (basicBlocks.contains(blockType)) {
            return true;
        }

        // 制限ブロックかチェック
        String requiredJob = getRequiredJobForBlock(blockType);
        if (requiredJob == null) {
            // 制限されていないブロックは誰でも採掘可能
            return true;
        }

        // プレイヤーが必要な職業を持っているかチェック
        return jobManager.hasJob(player, requiredJob);
    }
    
    /**
     * ブロック採掘に必要な職業を取得
     * 
     * @param blockType ブロックタイプ
     * @return 必要な職業名、制限がない場合はnull
     */
    private String getRequiredJobForBlock(Material blockType) {
        for (Map.Entry<String, Set<Material>> entry : jobRestrictedBlocks.entrySet()) {
            if (entry.getValue().contains(blockType)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * 職業制限によって採掘が拒否された場合のメッセージを取得
     * 
     * @param player プレイヤー
     * @param blockType ブロックタイプ
     * @return 表示メッセージ
     */
    public String getDeniedMessage(Player player, Material blockType) {
        String requiredJob = getRequiredJobForBlock(blockType);
        if (requiredJob == null) {
            return "§cこのブロックは採掘できません。";
        }
        
        String jobDisplayName = jobManager.getJobDisplayName(requiredJob);
        return "§c" + blockType.name() + " を採掘するには " + jobDisplayName + " の職業が必要です。";
    }

    /**
     * プレイヤーが指定ブロックを植え付け（設置）する権限があるかチェック
     *
     * @param player プレイヤー
     * @param blockType 設置されるブロックタイプ
     * @return 植え付け可能な場合true
     */
    public boolean canPlayerPlantBlock(Player player, Material blockType) {
        // 植え付け制限システムが無効の場合は常に許可
        if (!configManager.isJobPlantingRestrictionEnabled()) {
            return true;
        }

        // 管理者権限を持つ場合は常に許可
        if (player.hasPermission("tofunomics.admin.place")) {
            return true;
        }

        // 植え付け制限対象かチェック
        String requiredJob = getRequiredJobForPlanting(blockType);
        if (requiredJob == null) {
            // 制限されていないブロックは誰でも植え付け可能
            return true;
        }

        // プレイヤーが必要な職業を持っているかチェック
        return jobManager.hasJob(player, requiredJob);
    }

    /**
     * ブロック植え付けに必要な職業を取得
     *
     * @param blockType ブロックタイプ
     * @return 必要な職業名、制限がない場合はnull
     */
    private String getRequiredJobForPlanting(Material blockType) {
        for (Map.Entry<String, Set<Material>> entry : jobPlantingBlocks.entrySet()) {
            if (entry.getValue().contains(blockType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 職業制限によって植え付けが拒否された場合のメッセージを取得
     *
     * @param player プレイヤー
     * @param blockType ブロックタイプ
     * @return 表示メッセージ
     */
    public String getPlantingDeniedMessage(Player player, Material blockType) {
        String requiredJob = getRequiredJobForPlanting(blockType);
        if (requiredJob == null) {
            return "§cこのブロックは植えられません。";
        }

        String jobDisplayName = jobManager.getJobDisplayName(requiredJob);
        return "§c" + blockType.name() + " を植えるには " + jobDisplayName + " の職業が必要です。";
    }

    /**
     * 基本ブロックセットを取得
     */
    public Set<Material> getBasicBlocks() {
        return new HashSet<>(basicBlocks);
    }
    
    /**
     * 職業別制限ブロックマップを取得
     */
    public Map<String, Set<Material>> getJobRestrictedBlocks() {
        return new HashMap<>(jobRestrictedBlocks);
    }
    
    /**
     * 特定職業の制限ブロックを取得
     */
    public Set<Material> getRestrictedBlocksForJob(String jobName) {
        return jobRestrictedBlocks.getOrDefault(jobName, new HashSet<>());
    }
    
    /**
     * ブロックが制限対象かチェック
     */
    public boolean isRestrictedBlock(Material blockType) {
        return getRequiredJobForBlock(blockType) != null;
    }
    
    /**
     * システムのリロード（設定変更時に呼び出し）
     */
    public void reload() {
        // 必要に応じて設定ファイルから動的読み込み
        // 現在は静的定義のため処理なし
    }
}