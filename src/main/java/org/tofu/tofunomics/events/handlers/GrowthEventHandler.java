package org.tofu.tofunomics.events.handlers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockGrowEvent;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 作物成長イベントハンドラ
 * 農家の作物成長処理を管理
 */
public class GrowthEventHandler {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;
    
    // 作物の所有者を追跡（位置 -> プレイヤーUUID）
    private final Map<Location, UUID> cropOwners;
    
    // 作物種別による報酬
    private final Map<Material, GrowthReward> growthRewards;
    
    public GrowthEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                             JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
        this.cropOwners = new HashMap<>();
        this.growthRewards = new HashMap<>();
        
        initializeGrowthRewards();
    }
    
    /**
     * 成長報酬テーブルの初期化
     */
    private void initializeGrowthRewards() {
        // 基本作物
        growthRewards.put(Material.WHEAT, new GrowthReward(1.0, 1.5, "小麦"));
        growthRewards.put(Material.CARROTS, new GrowthReward(1.0, 1.5, "ニンジン"));
        growthRewards.put(Material.POTATOES, new GrowthReward(1.0, 1.5, "ジャガイモ"));
        growthRewards.put(Material.BEETROOTS, new GrowthReward(1.2, 2.0, "ビートルート"));
        
        // 特殊作物
        growthRewards.put(Material.PUMPKIN, new GrowthReward(3.0, 5.0, "カボチャ"));
        growthRewards.put(Material.MELON, new GrowthReward(2.5, 4.0, "スイカ"));
        growthRewards.put(Material.SUGAR_CANE, new GrowthReward(0.5, 1.0, "サトウキビ"));
        growthRewards.put(Material.CACTUS, new GrowthReward(0.8, 1.5, "サボテン"));
        
        // 樹木
        growthRewards.put(Material.OAK_SAPLING, new GrowthReward(5.0, 8.0, "オークの苗木"));
        growthRewards.put(Material.BIRCH_SAPLING, new GrowthReward(5.0, 8.0, "シラカバの苗木"));
        growthRewards.put(Material.SPRUCE_SAPLING, new GrowthReward(5.0, 8.0, "トウヒの苗木"));
        growthRewards.put(Material.JUNGLE_SAPLING, new GrowthReward(8.0, 12.0, "ジャングルの苗木"));
        growthRewards.put(Material.ACACIA_SAPLING, new GrowthReward(6.0, 10.0, "アカシアの苗木"));
        growthRewards.put(Material.DARK_OAK_SAPLING, new GrowthReward(7.0, 11.0, "ダークオークの苗木"));
        
        // ネザー関連
        growthRewards.put(Material.NETHER_WART, new GrowthReward(4.0, 6.0, "ネザーウォート"));
        growthRewards.put(Material.WARPED_FUNGUS, new GrowthReward(10.0, 15.0, "歪んだキノコ"));
        growthRewards.put(Material.CRIMSON_FUNGUS, new GrowthReward(10.0, 15.0, "真紅のキノコ"));
        
        // キノコ
        growthRewards.put(Material.BROWN_MUSHROOM, new GrowthReward(2.0, 3.0, "茶キノコ"));
        growthRewards.put(Material.RED_MUSHROOM, new GrowthReward(2.0, 3.0, "赤キノコ"));
        
        // 1.17以降の作物（1.16.5では利用不可）
        // growthRewards.put(Material.SWEET_BERRIES, new GrowthReward(1.5, 2.5, "スイートベリー"));
    }
    
    /**
     * 作物成長イベントの処理
     */
    public void handleBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        Location location = block.getLocation();
        
        // 報酬対象の作物かチェック
        GrowthReward reward = growthRewards.get(material);
        if (reward == null) {
            // 新しく成長したブロックの種類をチェック
            Material newMaterial = event.getNewState().getType();
            reward = growthRewards.get(newMaterial);
            if (reward == null) {
                return;
            }
        }
        
        // 作物の所有者を取得
        UUID ownerUUID = cropOwners.get(location);
        if (ownerUUID == null) {
            // 近くの農家を探す
            ownerUUID = findNearestFarmer(location);
            if (ownerUUID == null) {
                return;
            }
            // 所有者として記録
            cropOwners.put(location, ownerUUID);
        }
        
        Player player = Bukkit.getPlayer(ownerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // 農家の職業チェック
        PlayerJob farmerJob = jobManager.getPlayerJob(player, "farmer");
        if (farmerJob == null || !farmerJob.isActive()) {
            return;
        }
        
        // 報酬を処理
        processGrowthReward(player, farmerJob, reward, location);
    }
    
    /**
     * 成長報酬の処理
     */
    private void processGrowthReward(Player player, PlayerJob farmerJob, 
                                    GrowthReward reward, Location location) {
        // レベルボーナスを適用
        double levelMultiplier = 1.0 + (farmerJob.getLevel() * 0.02); // レベル毎に2%ボーナス
        
        double finalExperience = reward.getExperience() * levelMultiplier;
        double finalIncome = reward.getIncome() * levelMultiplier;
        
        // 環境ボーナスをチェック
        double bonusMultiplier = checkEnvironmentalBonuses(player, location);
        finalExperience *= bonusMultiplier;
        finalIncome *= bonusMultiplier;
        
        // 非同期で報酬を付与
        String playerUUID = player.getUniqueId().toString();
        asyncUpdater.updateJobExperience(playerUUID, "farmer", finalExperience);
        asyncUpdater.updatePlayerBalance(playerUUID, finalIncome, "作物成長報酬");
        
        // メッセージ表示（頻繁になりすぎないように制限）
        if (Math.random() < 0.3) { // 30%の確率で表示
            String message = String.format(
                "%s%sが成長しました！ §a+%.1f経験値 §6+%.1f金塊",
                ChatColor.GREEN,
                reward.getDisplayName(),
                finalExperience,
                finalIncome
            );
            player.sendMessage(message);
        }
        
        // 農家スキル発動チェック
        checkFarmerGrowthSkills(player, farmerJob, location, reward);
    }
    
    /**
     * 環境ボーナスをチェック
     */
    private double checkEnvironmentalBonuses(Player player, Location location) {
        double bonusMultiplier = 1.0;
        
        // バイオームボーナス
        String biome = location.getBlock().getBiome().name();
        if (biome.contains("PLAINS") || biome.contains("FOREST")) {
            bonusMultiplier *= 1.1; // 平原・森林は10%ボーナス
        } else if (biome.contains("JUNGLE")) {
            bonusMultiplier *= 1.2; // ジャングルは20%ボーナス
        }
        
        // 天候ボーナス
        if (player.getWorld().hasStorm()) {
            bonusMultiplier *= 1.15; // 雨天時は15%ボーナス
        }
        
        // 時間帯ボーナス
        long time = player.getWorld().getTime();
        if (time >= 1000 && time <= 5000) { // 朝の時間帯
            bonusMultiplier *= 1.05; // 朝は5%ボーナス
        }
        
        // 月齢ボーナス（満月近くで成長ボーナス）
        if (time >= 18000 && location.getBlock().getLightFromSky() > 10) {
            bonusMultiplier *= 1.1; // 満月の夜は10%ボーナス
        }
        
        return bonusMultiplier;
    }
    
    /**
     * 農家の成長スキル発動チェック
     */
    private void checkFarmerGrowthSkills(Player player, PlayerJob job, 
                                        Location location, GrowthReward reward) {
        int level = job.getLevel();
        
        // レベル20以上：連鎖成長
        if (level >= 20 && Math.random() < 0.15) { // 15%の確率
            triggerChainGrowth(location, reward);
            player.sendMessage(ChatColor.GOLD + "✦ 連鎖成長！周囲の作物も成長しました！");
        }
        
        // レベル35以上：豊作の恵み
        if (level >= 35 && Math.random() < 0.1) { // 10%の確率
            // 追加報酬を付与
            asyncUpdater.updatePlayerBalance(player.getUniqueId().toString(), 
                                           reward.getIncome() * 0.5, "豊作ボーナス");
            player.sendMessage(ChatColor.GOLD + "✦ 豊作の恵み！追加収入を獲得しました！");
        }
        
        // レベル50以上：生命の祝福
        if (level >= 50 && Math.random() < 0.05) { // 5%の確率
            // 周囲の作物の成長を促進
            accelerateNearbyGrowth(location);
            player.sendMessage(ChatColor.GOLD + "✦ 生命の祝福！周囲の作物の成長が加速されました！");
        }
    }
    
    /**
     * 連鎖成長を発動
     */
    private void triggerChainGrowth(Location centerLocation, GrowthReward reward) {
        // 周囲3x3の範囲で同じ作物を成長させる
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // 中心は除外
                
                Location nearbyLocation = centerLocation.clone().add(x, 0, z);
                Block nearbyBlock = nearbyLocation.getBlock();
                
                // 同じ種類の作物があれば成長ステージを進める
                if (nearbyBlock.getType() == centerLocation.getBlock().getType()) {
                    // ブロックデータを操作して成長ステージを進める
                    // （実際の実装はより複雑になる）
                    // ここでは簡略化
                }
            }
        }
    }
    
    /**
     * 周囲の作物成長を加速
     */
    private void accelerateNearbyGrowth(Location centerLocation) {
        // 5x5の範囲で作物の成長を加速
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location nearbyLocation = centerLocation.clone().add(x, 0, z);
                Block nearbyBlock = nearbyLocation.getBlock();
                
                // 成長可能な作物があれば成長ステージを進める
                if (growthRewards.containsKey(nearbyBlock.getType())) {
                    // 実際の成長処理は複雑なため簡略化
                    // ボーンミールを使用したような効果を模擬
                }
            }
        }
    }
    
    /**
     * 最も近い農家を探す
     */
    private UUID findNearestFarmer(Location location) {
        double nearestDistance = Double.MAX_VALUE;
        UUID nearestFarmer = null;
        
        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(location);
            if (distance < 50 && distance < nearestDistance) { // 50ブロック以内
                PlayerJob job = jobManager.getPlayerJob(player, "farmer");
                if (job != null && job.isActive()) {
                    nearestDistance = distance;
                    nearestFarmer = player.getUniqueId();
                }
            }
        }
        
        return nearestFarmer;
    }
    
    /**
     * 作物の所有者を設定
     */
    public void setCropOwner(Location location, Player player) {
        cropOwners.put(location, player.getUniqueId());
    }
    
    /**
     * 作物の所有者を削除
     */
    public void removeCropOwner(Location location) {
        cropOwners.remove(location);
    }
    
    /**
     * 古い所有者データをクリーンアップ
     */
    public void cleanupOldOwners() {
        // 定期的に呼び出して、古い所有者データを削除
        cropOwners.entrySet().removeIf(entry -> {
            UUID playerUUID = entry.getValue();
            Player player = Bukkit.getPlayer(playerUUID);
            return player == null || !player.isOnline();
        });
    }
    
    /**
     * 成長報酬クラス
     */
    private static class GrowthReward {
        private final double experience;
        private final double income;
        private final String displayName;
        
        public GrowthReward(double experience, double income, String displayName) {
            this.experience = experience;
            this.income = income;
            this.displayName = displayName;
        }
        
        public double getExperience() { return experience; }
        public double getIncome() { return income; }
        public String getDisplayName() { return displayName; }
    }
}