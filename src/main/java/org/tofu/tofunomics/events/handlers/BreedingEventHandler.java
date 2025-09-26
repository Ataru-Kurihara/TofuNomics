package org.tofu.tofunomics.events.handlers;

import org.bukkit.ChatColor;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityBreedEvent;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.Map;

/**
 * 繁殖イベントハンドラ
 * 農家の動物繁殖処理を管理
 */
public class BreedingEventHandler {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;
    
    // 動物種別による経験値と収入
    private final Map<EntityType, BreedingReward> breedingRewards;
    
    public BreedingEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                               JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
        this.breedingRewards = new HashMap<>();
        
        initializeBreedingRewards();
    }
    
    /**
     * 繁殖報酬テーブルの初期化
     */
    private void initializeBreedingRewards() {
        // 基本的な家畜
        breedingRewards.put(EntityType.COW, new BreedingReward(5.0, 8.0, "牛"));
        breedingRewards.put(EntityType.PIG, new BreedingReward(4.0, 6.0, "豚"));
        breedingRewards.put(EntityType.SHEEP, new BreedingReward(4.0, 6.0, "羊"));
        breedingRewards.put(EntityType.CHICKEN, new BreedingReward(3.0, 4.0, "鶏"));
        
        // より価値の高い動物
        breedingRewards.put(EntityType.HORSE, new BreedingReward(15.0, 25.0, "馬"));
        breedingRewards.put(EntityType.DONKEY, new BreedingReward(12.0, 20.0, "ロバ"));
        breedingRewards.put(EntityType.MULE, new BreedingReward(18.0, 30.0, "ラバ"));
        breedingRewards.put(EntityType.LLAMA, new BreedingReward(10.0, 15.0, "ラマ"));
        
        // 特殊な動物
        breedingRewards.put(EntityType.RABBIT, new BreedingReward(2.0, 3.0, "ウサギ"));
        breedingRewards.put(EntityType.OCELOT, new BreedingReward(8.0, 12.0, "ヤマネコ"));
        breedingRewards.put(EntityType.WOLF, new BreedingReward(10.0, 15.0, "オオカミ"));
        
        // 1.16以降の動物
        breedingRewards.put(EntityType.HOGLIN, new BreedingReward(20.0, 35.0, "ホグリン"));
        breedingRewards.put(EntityType.STRIDER, new BreedingReward(25.0, 40.0, "ストライダー"));
        
        // 水生動物（1.16.5では利用不可）
        // breedingRewards.put(EntityType.AXOLOTL, new BreedingReward(30.0, 50.0, "ウーパールーパー"));
    }
    
    /**
     * 繁殖イベントの処理
     */
    public void handleBreeding(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getBreeder();
        Animals child = (Animals) event.getEntity();
        EntityType entityType = child.getType();
        
        // 農家の職業チェック
        PlayerJob farmerJob = jobManager.getPlayerJob(player, "farmer");
        if (farmerJob == null || !farmerJob.isActive()) {
            // 農家でない場合は報酬なし（通常の繁殖は許可）
            return;
        }
        
        // 繁殖報酬をチェック
        BreedingReward reward = breedingRewards.get(entityType);
        if (reward == null) {
            // 未対応の動物の場合は基本報酬
            reward = new BreedingReward(2.0, 3.0, entityType.name().toLowerCase());
        }
        
        // 報酬を処理
        processBreedingReward(player, farmerJob, reward, child);
    }
    
    /**
     * 繁殖報酬の処理
     */
    private void processBreedingReward(Player player, PlayerJob farmerJob, 
                                      BreedingReward reward, Animals child) {
        // レベルボーナスを適用
        double levelMultiplier = 1.0 + (farmerJob.getLevel() * 0.025); // レベル毎に2.5%ボーナス
        
        double finalExperience = reward.getExperience() * levelMultiplier;
        double finalIncome = reward.getIncome() * levelMultiplier;
        
        // 特殊ボーナスをチェック
        double bonusMultiplier = checkSpecialBonuses(player, farmerJob, child.getType());
        finalExperience *= bonusMultiplier;
        finalIncome *= bonusMultiplier;
        
        // 非同期で報酬を付与
        String playerUUID = player.getUniqueId().toString();
        asyncUpdater.updateJobExperience(playerUUID, "farmer", finalExperience);
        asyncUpdater.updatePlayerBalance(playerUUID, finalIncome, "動物繁殖報酬");
        
        // メッセージ表示
        displayBreedingResult(player, reward, finalExperience, finalIncome, bonusMultiplier > 1.0);
        
        // 農家スキル発動チェック
        checkFarmerSkills(player, farmerJob, child);
    }
    
    /**
     * 特殊ボーナスをチェック
     */
    private double checkSpecialBonuses(Player player, PlayerJob job, EntityType entityType) {
        double bonusMultiplier = 1.0;
        
        // 夜間繁殖ボーナス
        long time = player.getWorld().getTime();
        if (time > 12000 && time < 24000) { // 夜間
            bonusMultiplier *= 1.2;
            player.sendMessage(ChatColor.DARK_BLUE + "✦ 夜間繁殖ボーナス！(+20%)");
        }
        
        // 雨天ボーナス
        if (player.getWorld().hasStorm()) {
            bonusMultiplier *= 1.15;
            player.sendMessage(ChatColor.AQUA + "✦ 雨天繁殖ボーナス！(+15%)");
        }
        
        // 希少動物ボーナス
        if (isRareAnimal(entityType)) {
            bonusMultiplier *= 1.5;
            player.sendMessage(ChatColor.GOLD + "✦ 希少動物繁殖ボーナス！(+50%)");
        }
        
        // 連続繁殖ボーナス（プレイヤーのメタデータで管理）
        if (player.hasMetadata("breeding_streak")) {
            int streak = player.getMetadata("breeding_streak").get(0).asInt();
            if (streak >= 5) {
                double streakBonus = Math.min(streak * 0.05, 0.5); // 最大50%
                bonusMultiplier *= (1.0 + streakBonus);
                player.sendMessage(ChatColor.YELLOW + "✦ 連続繁殖ボーナス！(+" + 
                                 String.format("%.0f", streakBonus * 100) + "%)");
            }
        }
        
        return bonusMultiplier;
    }
    
    /**
     * 希少動物かチェック
     */
    private boolean isRareAnimal(EntityType entityType) {
        switch (entityType) {
            case HORSE:
            case MULE:
            case HOGLIN:
            case STRIDER:
            // case AXOLOTL: // 1.16.5では利用不可
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 農家スキルの発動チェック
     */
    private void checkFarmerSkills(Player player, PlayerJob job, Animals child) {
        int level = job.getLevel();
        
        // レベル15以上：双子の奇跡
        if (level >= 15 && Math.random() < 0.1) { // 10%の確率
            spawnTwin(player, child);
            player.sendMessage(ChatColor.GOLD + "✦ 双子の奇跡！もう1匹生まれました！");
        }
        
        // レベル30以上：成長促進
        if (level >= 30 && Math.random() < 0.2) { // 20%の確率
            if (child.isAdult()) {
                child.setAge(0); // 即座に成体にする
            } else {
                child.setAge(child.getAge() + 1200); // 成長を早める
            }
            player.sendMessage(ChatColor.GREEN + "✦ 成長促進！動物の成長が早まりました！");
        }
        
        // レベル50以上：品種改良
        if (level >= 50 && Math.random() < 0.05) { // 5%の確率
            // 動物に特殊なメタデータを付与（改良品種）
            child.setCustomName(ChatColor.GOLD + "改良品種の" + child.getType().name().toLowerCase());
            child.setCustomNameVisible(true);
            player.sendMessage(ChatColor.GOLD + "✦ 品種改良成功！品質の高い動物が生まれました！");
        }
    }
    
    /**
     * 双子を生成
     */
    private void spawnTwin(Player player, Animals originalChild) {
        // 同じ場所に同じタイプの動物を生成
        Animals twin = (Animals) player.getWorld().spawnEntity(
            originalChild.getLocation(), 
            originalChild.getType()
        );
        
        // 双子の設定
        twin.setBaby();
        twin.setAgeLock(false);
        
        // 双子であることを示すカスタム名
        twin.setCustomName(ChatColor.AQUA + "双子の" + originalChild.getType().name().toLowerCase());
        twin.setCustomNameVisible(true);
    }
    
    /**
     * 繁殖結果を表示
     */
    private void displayBreedingResult(Player player, BreedingReward reward,
                                      double experience, double income, boolean hasBonus) {
        String message = String.format(
            "%s%sの繁殖成功！ §a+%.1f経験値 §6+%.1f金塊",
            ChatColor.GREEN,
            reward.getDisplayName(),
            experience,
            income
        );
        
        if (hasBonus) {
            message += ChatColor.YELLOW + " (ボーナス適用)";
        }
        
        player.sendMessage(message);
    }
    
    /**
     * 繁殖報酬クラス
     */
    private static class BreedingReward {
        private final double experience;
        private final double income;
        private final String displayName;
        
        public BreedingReward(double experience, double income, String displayName) {
            this.experience = experience;
            this.income = income;
            this.displayName = displayName;
        }
        
        public double getExperience() { return experience; }
        public double getIncome() { return income; }
        public String getDisplayName() { return displayName; }
    }
}