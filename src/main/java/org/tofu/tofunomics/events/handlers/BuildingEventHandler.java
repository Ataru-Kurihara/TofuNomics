package org.tofu.tofunomics.events.handlers;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 建築イベントハンドラ
 * 建築家のブロック設置処理を管理
 */
public class BuildingEventHandler {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;
    
    // 建築材料による報酬
    private final Map<Material, BuildingReward> buildingRewards;
    
    // 建築家専用装飾ブロック
    private final Set<Material> decorativeBlocks;
    
    // 建築プロジェクトの追跡
    private final Map<String, BuildingProject> activeProjects;
    
    public BuildingEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                               JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
        this.buildingRewards = new HashMap<>();
        this.decorativeBlocks = new HashSet<>();
        this.activeProjects = new HashMap<>();
        
        initializeBuildingRewards();
        initializeDecorativeBlocks();
    }
    
    /**
     * 建築報酬テーブルの初期化
     */
    private void initializeBuildingRewards() {
        // 基本建築材料
        buildingRewards.put(Material.STONE, new BuildingReward(0.5, 1.0, "石"));
        buildingRewards.put(Material.COBBLESTONE, new BuildingReward(0.3, 0.8, "丸石"));
        buildingRewards.put(Material.STONE_BRICKS, new BuildingReward(1.0, 2.0, "石レンガ"));
        
        // 木材系
        buildingRewards.put(Material.OAK_PLANKS, new BuildingReward(0.8, 1.5, "オークの板材"));
        buildingRewards.put(Material.BIRCH_PLANKS, new BuildingReward(0.8, 1.5, "シラカバの板材"));
        buildingRewards.put(Material.SPRUCE_PLANKS, new BuildingReward(0.8, 1.5, "トウヒの板材"));
        buildingRewards.put(Material.JUNGLE_PLANKS, new BuildingReward(1.0, 2.0, "ジャングルの板材"));
        buildingRewards.put(Material.ACACIA_PLANKS, new BuildingReward(1.0, 2.0, "アカシアの板材"));
        buildingRewards.put(Material.DARK_OAK_PLANKS, new BuildingReward(1.0, 2.0, "ダークオークの板材"));
        
        // レンガ系
        buildingRewards.put(Material.BRICKS, new BuildingReward(2.0, 4.0, "レンガ"));
        buildingRewards.put(Material.NETHER_BRICKS, new BuildingReward(3.0, 6.0, "ネザーレンガ"));
        buildingRewards.put(Material.RED_NETHER_BRICKS, new BuildingReward(3.5, 7.0, "赤いネザーレンガ"));
        
        // 高級建築材料
        buildingRewards.put(Material.QUARTZ_BLOCK, new BuildingReward(5.0, 10.0, "クォーツブロック"));
        buildingRewards.put(Material.CHISELED_QUARTZ_BLOCK, new BuildingReward(6.0, 12.0, "模様入りクォーツ"));
        buildingRewards.put(Material.QUARTZ_PILLAR, new BuildingReward(6.0, 12.0, "クォーツの柱"));
        
        // 特殊ブロック
        buildingRewards.put(Material.OBSIDIAN, new BuildingReward(10.0, 20.0, "黒曜石"));
        buildingRewards.put(Material.END_STONE, new BuildingReward(8.0, 15.0, "エンドストーン"));
        buildingRewards.put(Material.PURPUR_BLOCK, new BuildingReward(7.0, 14.0, "プルプァブロック"));
        
        // 装飾ブロック
        buildingRewards.put(Material.CHISELED_STONE_BRICKS, new BuildingReward(3.0, 6.0, "模様入り石レンガ"));
        buildingRewards.put(Material.MOSSY_STONE_BRICKS, new BuildingReward(2.5, 5.0, "苔石レンガ"));
        buildingRewards.put(Material.CRACKED_STONE_BRICKS, new BuildingReward(2.0, 4.0, "ひび入り石レンガ"));
        
        // ガラス系
        buildingRewards.put(Material.GLASS, new BuildingReward(1.5, 3.0, "ガラス"));
        buildingRewards.put(Material.WHITE_STAINED_GLASS, new BuildingReward(2.0, 4.0, "白色のガラス"));
        buildingRewards.put(Material.BLUE_STAINED_GLASS, new BuildingReward(2.0, 4.0, "青色のガラス"));
        // 他の色ガラスも同様に設定...
        
        // 1.16以降のブロック
        buildingRewards.put(Material.BLACKSTONE, new BuildingReward(1.0, 2.0, "ブラックストーン"));
        buildingRewards.put(Material.POLISHED_BLACKSTONE, new BuildingReward(2.0, 4.0, "磨かれたブラックストーン"));
        buildingRewards.put(Material.CHISELED_POLISHED_BLACKSTONE, new BuildingReward(3.0, 6.0, "模様入り磨かれたブラックストーン"));
    }
    
    /**
     * 建築家専用装飾ブロックの初期化
     */
    private void initializeDecorativeBlocks() {
        // 高レベル建築家のみ使用可能な装飾ブロック
        decorativeBlocks.add(Material.CHISELED_QUARTZ_BLOCK);
        decorativeBlocks.add(Material.QUARTZ_PILLAR);
        decorativeBlocks.add(Material.CHISELED_STONE_BRICKS);
        decorativeBlocks.add(Material.CHISELED_POLISHED_BLACKSTONE);
        decorativeBlocks.add(Material.CHISELED_SANDSTONE);
        decorativeBlocks.add(Material.CHISELED_RED_SANDSTONE);
        decorativeBlocks.add(Material.CARVED_PUMPKIN);
        decorativeBlocks.add(Material.JACK_O_LANTERN);
        
        // ビーコンやコンジット等の特殊建築ブロック
        decorativeBlocks.add(Material.BEACON);
        decorativeBlocks.add(Material.CONDUIT);
        
        // エンドゲーム装飾
        decorativeBlocks.add(Material.END_ROD);
        decorativeBlocks.add(Material.CHORUS_PLANT);
        decorativeBlocks.add(Material.CHORUS_FLOWER);
    }
    
    /**
     * ブロック設置イベントの処理
     */
    public void handleBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Material material = block.getType();
        
        // 建築家の職業チェック
        PlayerJob builderJob = jobManager.getPlayerJob(player, "builder");
        if (builderJob == null || !builderJob.isActive()) {
            // 建築家でない場合は装飾ブロック制限をチェック
            if (decorativeBlocks.contains(material) && !hasDecorativePermission(player, material)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "このブロックは建築家レベル20以上でないと設置できません！");
                return;
            }
            return;
        }
        
        // 装飾ブロック権限チェック
        if (decorativeBlocks.contains(material) && !canUseDecorativeBlock(builderJob, material)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "このブロックを使用するには建築家レベル" + 
                             getRequiredLevel(material) + "以上が必要です！");
            return;
        }
        
        // 建築報酬を処理
        processBuildingReward(player, builderJob, material, block.getLocation());
    }
    
    /**
     * 建築報酬の処理
     */
    private void processBuildingReward(Player player, PlayerJob builderJob, 
                                      Material material, Location location) {
        BuildingReward reward = buildingRewards.get(material);
        if (reward == null) {
            // 未定義のブロックは基本報酬
            reward = new BuildingReward(0.1, 0.2, material.name().toLowerCase());
        }
        
        // レベルボーナスを適用
        double levelMultiplier = 1.0 + (builderJob.getLevel() * 0.025); // レベル毎に2.5%ボーナス
        
        double finalExperience = reward.getExperience() * levelMultiplier;
        double finalIncome = reward.getIncome() * levelMultiplier;
        
        // 建築プロジェクトボーナスをチェック
        double projectBonus = checkBuildingProjectBonus(player, location, material);
        finalExperience *= projectBonus;
        finalIncome *= projectBonus;
        
        // 非同期で経験値を付与（収入システムは無効化）
        String playerUUID = player.getUniqueId().toString();
        asyncUpdater.updateJobExperience(playerUUID, "builder", finalExperience);
        
        // 建築スキル発動チェック
        checkBuildingSkills(player, builderJob, material, location);
        
        // 大規模建築の場合のみメッセージ表示
        if (reward.getExperience() >= 2.0) {
            String message = String.format(
                "%s%sを設置！ §a+%.1f経験値 §6+%.1f金塊",
                ChatColor.GRAY,
                reward.getDisplayName(),
                finalExperience,
                finalIncome
            );
            player.sendMessage(message);
        }
    }
    
    /**
     * 建築プロジェクトボーナスをチェック
     */
    private double checkBuildingProjectBonus(Player player, Location location, Material material) {
        String playerUUID = player.getUniqueId().toString();
        BuildingProject project = activeProjects.get(playerUUID);
        
        if (project == null) {
            // 新しいプロジェクトを開始
            project = new BuildingProject(location, material);
            activeProjects.put(playerUUID, project);
            return 1.0;
        }
        
        // 既存プロジェクトとの関連性をチェック
        if (project.isRelatedBlock(location, material)) {
            project.addBlock(location, material);
            
            // プロジェクト規模によるボーナス
            int blockCount = project.getBlockCount();
            if (blockCount >= 100) {
                return 2.0; // 大規模建築：100%ボーナス
            } else if (blockCount >= 50) {
                return 1.5; // 中規模建築：50%ボーナス
            } else if (blockCount >= 20) {
                return 1.2; // 小規模建築：20%ボーナス
            }
        } else {
            // 新しいプロジェクト開始
            activeProjects.put(playerUUID, new BuildingProject(location, material));
        }
        
        return 1.0;
    }
    
    /**
     * 建築スキルの発動チェック
     */
    private void checkBuildingSkills(Player player, PlayerJob job, Material material, Location location) {
        int level = job.getLevel();
        
        // レベル15以上：効率建築
        if (level >= 15 && Math.random() < 0.1) { // 10%の確率
            // ブロックを設置する際にスタミナ（満腹度）を回復
            int currentFood = player.getFoodLevel();
            player.setFoodLevel(Math.min(20, currentFood + 1));
            player.sendMessage(ChatColor.GREEN + "✦ 効率建築！建築によりスタミナが回復しました！");
        }
        
        // レベル30以上：材料節約
        if (level >= 30 && Math.random() < 0.15) { // 15%の確率
            // プレイヤーに同じ材料を1個返却
            ItemStack item = new ItemStack(material, 1);
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item);
            } else {
                player.getWorld().dropItem(location, item);
            }
            player.sendMessage(ChatColor.GOLD + "✦ 材料節約！材料が1個返却されました！");
        }
        
        // レベル50以上：建築の達人
        if (level >= 50 && Math.random() < 0.05) { // 5%の確率
            // 周囲に同じブロックを自動配置
            autoPlaceBlocks(player, location, material);
            player.sendMessage(ChatColor.GOLD + "✦ 建築の達人！周囲にブロックが自動配置されました！");
        }
        
        // レベル75以上：建築家の直感
        if (level >= 75 && Math.random() < 0.03) { // 3%の確率
            // プレイヤーに建築に有用な追加材料を付与
            giveBuilderBonus(player, material);
            player.sendMessage(ChatColor.GOLD + "✦ 建築家の直感！追加材料を発見しました！");
        }
    }
    
    /**
     * 自動ブロック配置
     */
    private void autoPlaceBlocks(Player player, Location centerLocation, Material material) {
        // プレイヤーのインベントリに同じ材料があるかチェック
        int availableBlocks = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                availableBlocks += item.getAmount();
            }
        }
        
        if (availableBlocks <= 1) return;
        
        // 周囲のブロックを配置（最大4個）
        int placed = 0;
        Location[] directions = {
            centerLocation.clone().add(1, 0, 0),
            centerLocation.clone().add(-1, 0, 0),
            centerLocation.clone().add(0, 0, 1),
            centerLocation.clone().add(0, 0, -1)
        };
        
        for (Location loc : directions) {
            if (placed >= Math.min(4, availableBlocks - 1)) break;
            
            Block block = loc.getBlock();
            if (block.getType() == Material.AIR) {
                block.setType(material);
                player.getInventory().removeItem(new ItemStack(material, 1));
                placed++;
            }
        }
    }
    
    /**
     * 建築家ボーナス材料を付与
     */
    private void giveBuilderBonus(Player player, Material placedMaterial) {
        // 配置したブロックに関連する材料を付与
        Material bonusMaterial = getRelatedMaterial(placedMaterial);
        if (bonusMaterial != null) {
            ItemStack bonus = new ItemStack(bonusMaterial, 1 + (int)(Math.random() * 3));
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(bonus);
            } else {
                player.getWorld().dropItem(player.getLocation(), bonus);
            }
        }
    }
    
    /**
     * 関連材料を取得
     */
    private Material getRelatedMaterial(Material material) {
        // 材料の種類に応じて関連材料を返す
        String name = material.name();
        if (name.contains("STONE")) {
            return Material.STONE;
        } else if (name.contains("WOOD") || name.contains("PLANK")) {
            return Material.OAK_PLANKS;
        } else if (name.contains("BRICK")) {
            return Material.BRICKS;
        } else if (name.contains("QUARTZ")) {
            return Material.QUARTZ;
        }
        return null;
    }
    
    /**
     * 装飾ブロック使用権限チェック
     */
    private boolean canUseDecorativeBlock(PlayerJob job, Material material) {
        int requiredLevel = getRequiredLevel(material);
        return job.getLevel() >= requiredLevel;
    }
    
    /**
     * 一般プレイヤーの装飾ブロック権限チェック
     */
    private boolean hasDecorativePermission(Player player, Material material) {
        // 一般プレイヤーは基本的な装飾ブロックのみ使用可能
        return !decorativeBlocks.contains(material) || 
               player.hasPermission("tofunomics.builder.decorative");
    }
    
    /**
     * ブロックに必要なレベルを取得
     */
    private int getRequiredLevel(Material material) {
        switch (material) {
            case CHISELED_QUARTZ_BLOCK:
            case QUARTZ_PILLAR:
                return 20;
            case CHISELED_STONE_BRICKS:
            case CHISELED_POLISHED_BLACKSTONE:
                return 30;
            case BEACON:
            case CONDUIT:
                return 50;
            case END_ROD:
            case CHORUS_PLANT:
                return 60;
            default:
                return 20;
        }
    }
    
    /**
     * 建築報酬クラス
     */
    private static class BuildingReward {
        private final double experience;
        private final double income;
        private final String displayName;
        
        public BuildingReward(double experience, double income, String displayName) {
            this.experience = experience;
            this.income = income;
            this.displayName = displayName;
        }
        
        public double getExperience() { return experience; }
        public double getIncome() { return income; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * 建築プロジェクトクラス
     */
    private static class BuildingProject {
        private final Location startLocation;
        private final Material primaryMaterial;
        private final Set<Location> blockLocations;
        private final long startTime;
        
        public BuildingProject(Location startLocation, Material primaryMaterial) {
            this.startLocation = startLocation;
            this.primaryMaterial = primaryMaterial;
            this.blockLocations = new HashSet<>();
            this.startTime = System.currentTimeMillis();
            
            blockLocations.add(startLocation);
        }
        
        public boolean isRelatedBlock(Location location, Material material) {
            // 開始地点から50ブロック以内で、同じ材料系統の場合は関連ブロックとする
            if (location.distance(startLocation) > 50) {
                return false;
            }
            
            // 材料系統の判定（簡略化）
            return isSameMaterialFamily(primaryMaterial, material);
        }
        
        private boolean isSameMaterialFamily(Material mat1, Material mat2) {
            String name1 = mat1.name();
            String name2 = mat2.name();
            
            // 同じ材料系統かチェック
            if (name1.contains("STONE") && name2.contains("STONE")) return true;
            if (name1.contains("WOOD") && name2.contains("WOOD")) return true;
            if (name1.contains("PLANK") && name2.contains("PLANK")) return true;
            if (name1.contains("BRICK") && name2.contains("BRICK")) return true;
            if (name1.contains("QUARTZ") && name2.contains("QUARTZ")) return true;
            
            return mat1 == mat2;
        }
        
        public void addBlock(Location location, Material material) {
            blockLocations.add(location);
        }
        
        public int getBlockCount() {
            return blockLocations.size();
        }
        
        public boolean isExpired() {
            // 1時間で期限切れ
            return System.currentTimeMillis() - startTime > 3600000;
        }
    }
}