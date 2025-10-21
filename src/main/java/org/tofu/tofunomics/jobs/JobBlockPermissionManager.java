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
    
    public JobBlockPermissionManager(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.basicBlocks = new HashSet<>();
        this.jobRestrictedBlocks = new HashMap<>();
        
        initializeBasicBlocks();
        initializeJobRestrictedBlocks();
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
            Material.NETHER_QUARTZ_ORE
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
     * プレイヤーが指定ブロックを採掘する権限があるかチェック
     * 
     * @param player プレイヤー
     * @param blockType ブロックタイプ
     * @return 採掘可能な場合true
     */
    public boolean canPlayerBreakBlock(Player player, Material blockType) {
        System.out.println("=== canPlayerBreakBlock デバッグ開始 ===");
        System.out.println("プレイヤー: " + player.getName());
        System.out.println("ブロックタイプ: " + blockType.name());
        
        // 職業制限システムが無効の場合は常に許可
        boolean isRestrictionEnabled = configManager.isJobBlockRestrictionEnabled();
        System.out.println("ブロック制限システム有効: " + isRestrictionEnabled);
        if (!isRestrictionEnabled) {
            System.out.println("判定結果: ブロック制限システムが無効のため許可");
            return true;
        }
        
        // 管理者権限を持つ場合は常に許可
        boolean hasAdminPermission = player.hasPermission("tofunomics.admin.break");
        System.out.println("管理者権限: " + hasAdminPermission);
        if (hasAdminPermission) {
            System.out.println("判定結果: 管理者権限のため許可");
            return true;
        }
        
        // 基本ブロックの場合は常に許可
        boolean isBasicBlock = basicBlocks.contains(blockType);
        System.out.println("基本ブロック: " + isBasicBlock);
        if (isBasicBlock) {
            System.out.println("判定結果: 基本ブロックのため許可");
            return true;
        }
        
        // 制限ブロックかチェック
        String requiredJob = getRequiredJobForBlock(blockType);
        System.out.println("必要な職業: " + (requiredJob != null ? requiredJob : "なし"));
        if (requiredJob == null) {
            // 制限されていないブロックは誰でも採掘可能
            System.out.println("判定結果: 制限されていないブロックのため許可");
            return true;
        }
        
        // プレイヤーが必要な職業を持っているかチェック
        boolean hasRequiredJob = jobManager.hasJob(player, requiredJob);
        System.out.println("必要職業の保有: " + hasRequiredJob);
        System.out.println("判定結果: " + (hasRequiredJob ? "必要職業保有のため許可" : "必要職業なしのため拒否"));
        return hasRequiredJob;
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