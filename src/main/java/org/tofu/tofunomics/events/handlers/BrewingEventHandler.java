package org.tofu.tofunomics.events.handlers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 醸造イベントハンドラ
 * 調合師の醸造処理を管理
 */
public class BrewingEventHandler {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;
    
    // 醸造台の所有者を追跡
    private final Map<Location, UUID> brewingStandOwners;
    
    // ポーション種別による経験値と収入
    private final Map<Material, PotionReward> potionRewards;
    
    public BrewingEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                              JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
        this.brewingStandOwners = new HashMap<>();
        this.potionRewards = new HashMap<>();
        
        initializePotionRewards();
    }
    
    /**
     * ポーション報酬テーブルの初期化
     */
    private void initializePotionRewards() {
        // 通常ポーション
        potionRewards.put(Material.POTION, new PotionReward(2.0, 3.0));
        
        // スプラッシュポーション
        potionRewards.put(Material.SPLASH_POTION, new PotionReward(3.0, 5.0));
        
        // 残留ポーション
        potionRewards.put(Material.LINGERING_POTION, new PotionReward(5.0, 8.0));
        
        // 特殊アイテム（ブレイズパウダー製作など）
        potionRewards.put(Material.BLAZE_POWDER, new PotionReward(1.0, 2.0));
        potionRewards.put(Material.MAGMA_CREAM, new PotionReward(2.0, 3.0));
    }
    
    /**
     * 醸造イベントの処理
     */
    public void handleBrew(BrewEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof BrewingStand)) {
            return;
        }
        
        Location location = block.getLocation();
        
        // 醸造台の所有者を取得
        UUID ownerUUID = brewingStandOwners.get(location);
        if (ownerUUID == null) {
            // 近くのプレイヤーから所有者を推定
            ownerUUID = findNearestAlchemist(location);
            if (ownerUUID == null) {
                return;
            }
        }
        
        Player player = Bukkit.getPlayer(ownerUUID);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // 調合師の職業チェック
        PlayerJob alchemistJob = jobManager.getPlayerJob(player, "alchemist");
        if (alchemistJob == null || !alchemistJob.isActive()) {
            return;
        }
        
        // 醸造結果を処理
        processBrewingResult(player, alchemistJob, event);
    }
    
    /**
     * 醸造結果の処理
     */
    private void processBrewingResult(Player player, PlayerJob alchemistJob, BrewEvent event) {
        BrewingStand brewingStand = (BrewingStand) event.getBlock().getState();
        
        double totalExperience = 0;
        double totalIncome = 0;
        int potionCount = 0;
        
        // 結果スロットをチェック（0-2番スロット）
        for (int i = 0; i < 3; i++) {
            ItemStack item = event.getContents().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                PotionReward reward = potionRewards.get(item.getType());
                if (reward != null) {
                    // レベルボーナスを適用
                    double levelMultiplier = 1.0 + (alchemistJob.getLevel() * 0.02); // レベル毎に2%ボーナス
                    
                    totalExperience += reward.getExperience() * levelMultiplier;
                    totalIncome += reward.getIncome() * levelMultiplier;
                    potionCount++;
                }
            }
        }
        
        if (potionCount > 0) {
            // 経験値とお金を付与
            applyRewards(player, alchemistJob, totalExperience, totalIncome, potionCount);
            
            // 醸造成功率ボーナス（高レベルほど失敗を防ぐ）
            if (alchemistJob.getLevel() >= 20) {
                applyBrewingBonus(event, alchemistJob.getLevel());
            }
        }
    }
    
    /**
     * 報酬を適用
     */
    private void applyRewards(Player player, PlayerJob job, double experience, 
                             double income, int potionCount) {
        String playerUUID = player.getUniqueId().toString();
        
        // 非同期で経験値を更新（収入システムは無効化）
        asyncUpdater.updateJobExperience(playerUUID, "alchemist", experience);
        
        // メッセージ表示
        String message = String.format(
            "%s§f%d個のポーションを醸造しました！ " +
            "§a+%.1f経験値 §6+%.1f金塊",
            ChatColor.LIGHT_PURPLE,
            potionCount,
            experience,
            income
        );
        player.sendMessage(message);
        
        // レベルアップチェックとスキル発動
        checkLevelUpAndSkills(player, job);
    }
    
    /**
     * 醸造ボーナスの適用
     */
    private void applyBrewingBonus(BrewEvent event, int level) {
        // 高レベルの調合師は追加効果を得る
        if (level >= 50) {
            // 50%の確率で材料を消費しない
            if (Math.random() < 0.5) {
                ItemStack ingredient = event.getContents().getIngredient();
                if (ingredient != null) {
                    ingredient.setAmount(ingredient.getAmount() + 1);
                }
            }
        } else if (level >= 30) {
            // 30%の確率で材料を消費しない
            if (Math.random() < 0.3) {
                ItemStack ingredient = event.getContents().getIngredient();
                if (ingredient != null) {
                    ingredient.setAmount(ingredient.getAmount() + 1);
                }
            }
        }
    }
    
    /**
     * レベルアップとスキルチェック
     */
    private void checkLevelUpAndSkills(Player player, PlayerJob job) {
        // スキル発動チェック
        if (job.getLevel() >= 10) {
            // ダブル醸造スキル（10%の確率で醸造物が倍になる）
            if (Math.random() < 0.10) {
                player.sendMessage(ChatColor.GOLD + "⚗ ダブル醸造スキル発動！追加のポーションを獲得しました！");
                // 実際のアイテム付与はメインスレッドで行う必要がある
                Bukkit.getScheduler().runTask(configManager.getPlugin(), () -> {
                    giveExtraPotions(player);
                });
            }
        }
        
        if (job.getLevel() >= 25) {
            // 完璧な醸造スキル（ポーション効果時間延長）
            if (Math.random() < 0.15) {
                player.sendMessage(ChatColor.GOLD + "⚗ 完璧な醸造！ポーション効果が強化されました！");
            }
        }
    }
    
    /**
     * 追加ポーションを付与
     */
    private void giveExtraPotions(Player player) {
        // プレイヤーのインベントリに空きがある場合、ランダムなポーションを追加
        ItemStack potion = new ItemStack(Material.POTION);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(potion);
        } else {
            player.getWorld().dropItem(player.getLocation(), potion);
        }
    }
    
    /**
     * 最も近い調合師を探す
     */
    private UUID findNearestAlchemist(Location location) {
        double nearestDistance = Double.MAX_VALUE;
        UUID nearestAlchemist = null;
        
        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(location);
            if (distance < 10 && distance < nearestDistance) { // 10ブロック以内
                PlayerJob job = jobManager.getPlayerJob(player, "alchemist");
                if (job != null && job.isActive()) {
                    nearestDistance = distance;
                    nearestAlchemist = player.getUniqueId();
                }
            }
        }
        
        return nearestAlchemist;
    }
    
    /**
     * 醸造台の所有者を設定
     */
    public void setBrewingStandOwner(Location location, Player player) {
        brewingStandOwners.put(location, player.getUniqueId());
    }
    
    /**
     * 醸造台の所有者を削除
     */
    public void removeBrewingStandOwner(Location location) {
        brewingStandOwners.remove(location);
    }
    
    /**
     * ポーション報酬クラス
     */
    private static class PotionReward {
        private final double experience;
        private final double income;
        
        public PotionReward(double experience, double income) {
            this.experience = experience;
            this.income = income;
        }
        
        public double getExperience() { return experience; }
        public double getIncome() { return income; }
    }
}