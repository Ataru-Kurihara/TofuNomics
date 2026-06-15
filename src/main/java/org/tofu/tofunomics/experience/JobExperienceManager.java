package org.tofu.tofunomics.experience;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.ChatColor;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.tools.JobToolManager;
import org.tofu.tofunomics.util.NotificationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業別経験値獲得システム
 */
public class JobExperienceManager implements Listener {
    
    private final ConfigManager configManager;
    private final PlayerJobDAO playerJobDAO;
    private final JobDAO jobDAO;
    private final JobManager jobManager;
    private final JobToolManager jobToolManager;
    private final ExperienceManager experienceManager;
    private final NotificationManager notificationManager = new NotificationManager();

    // 職業レベルBossBarへの即時反映用（setter注入。null許容）
    private org.tofu.tofunomics.scoreboard.JobLevelBossBarManager jobLevelBossBarManager;

    // 食事による経験値ブーストバフ用（setter注入。null許容）
    private org.tofu.tofunomics.food.FoodBuffManager foodBuffManager;

    // 経験値テーブル
    private final Map<Material, Double> miningExperience;
    private final Map<Material, Double> loggingExperience;
    private final Map<Material, Double> farmingExperience;
    private final Map<PlayerFishEvent.State, Double> fishingExperience;
    private final Map<Material, Double> craftingExperience;
    private final Map<Material, Double> brewingExperience;
    private final Map<Integer, Double> enchantingExperience;
    private final Map<Material, Double> buildingExperience;
    
    public JobExperienceManager(ConfigManager configManager, PlayerJobDAO playerJobDAO, 
                               JobDAO jobDAO, JobManager jobManager, JobToolManager jobToolManager,
                               ExperienceManager experienceManager) {
        this.configManager = configManager;
        this.playerJobDAO = playerJobDAO;
        this.jobDAO = jobDAO;
        this.jobManager = jobManager;
        this.jobToolManager = jobToolManager;
        this.experienceManager = experienceManager;
        
        this.miningExperience = new HashMap<>();
        this.loggingExperience = new HashMap<>();
        this.farmingExperience = new HashMap<>();
        this.fishingExperience = new HashMap<>();
        this.craftingExperience = new HashMap<>();
        this.brewingExperience = new HashMap<>();
        this.enchantingExperience = new HashMap<>();
        this.buildingExperience = new HashMap<>();
        
        initializeExperienceTables();
    }

    /**
     * 職業レベルBossBar即時反映用のJobLevelBossBarManagerを注入する
     */
    public void setJobLevelBossBarManager(org.tofu.tofunomics.scoreboard.JobLevelBossBarManager jobLevelBossBarManager) {
        this.jobLevelBossBarManager = jobLevelBossBarManager;
    }

    /**
     * 食事による経験値ブーストバフ管理を注入する
     */
    public void setFoodBuffManager(org.tofu.tofunomics.food.FoodBuffManager foodBuffManager) {
        this.foodBuffManager = foodBuffManager;
    }

    private void initializeExperienceTables() {
        // 採掘経験値テーブル
        miningExperience.put(Material.COAL_ORE, 2.0);
        miningExperience.put(Material.IRON_ORE, 5.0);
        miningExperience.put(Material.GOLD_ORE, 8.0);
        miningExperience.put(Material.DIAMOND_ORE, 25.0);
        miningExperience.put(Material.EMERALD_ORE, 20.0);
        miningExperience.put(Material.LAPIS_ORE, 4.0);
        miningExperience.put(Material.REDSTONE_ORE, 3.0);
        miningExperience.put(Material.NETHER_QUARTZ_ORE, 6.0);
        miningExperience.put(Material.COPPER_ORE, 5.0);  // 1.17追加。鉄鉱石相当
        miningExperience.put(Material.ANCIENT_DEBRIS, 50.0);
        // STONE: 自然生成のみ経験値獲得（プレイヤー設置はUnifiedEventHandlerで除外）
        miningExperience.put(Material.STONE, 5.0);  // 0.5 → 5.0（メッセージ表示のため）
        // COBBLESTONE: 経験値なし（設置→採掘での無限経験値防止）
        
        // 伐採経験値テーブル
        loggingExperience.put(Material.OAK_LOG, 2.0);
        loggingExperience.put(Material.BIRCH_LOG, 2.0);
        loggingExperience.put(Material.SPRUCE_LOG, 2.0);
        loggingExperience.put(Material.JUNGLE_LOG, 3.0);
        loggingExperience.put(Material.ACACIA_LOG, 3.0);
        loggingExperience.put(Material.DARK_OAK_LOG, 3.0);
        loggingExperience.put(Material.WARPED_STEM, 4.0);
        loggingExperience.put(Material.CRIMSON_STEM, 4.0);
        loggingExperience.put(Material.MANGROVE_LOG, 3.0);  // 1.19追加。jungle/acacia相当
        loggingExperience.put(Material.CHERRY_LOG, 3.0);    // 1.20追加。jungle/acacia相当
        
        // 農業経験値テーブル
        farmingExperience.put(Material.WHEAT, 1.5);
        farmingExperience.put(Material.POTATO, 1.2);
        farmingExperience.put(Material.CARROT, 1.2);
        farmingExperience.put(Material.BEETROOT, 2.0);
        farmingExperience.put(Material.PUMPKIN, 3.0);
        farmingExperience.put(Material.MELON, 2.5);
        farmingExperience.put(Material.SUGAR_CANE, 1.0);
        farmingExperience.put(Material.COCOA_BEANS, 2.5);
        farmingExperience.put(Material.NETHER_WART, 3.0);
        
        // 釣り経験値テーブル
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_FISH, 5.0);
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_ENTITY, 8.0);
        fishingExperience.put(PlayerFishEvent.State.IN_GROUND, 1.0);
        
        // 製作経験値テーブル（鍛冶屋）
        craftingExperience.put(Material.IRON_INGOT, 3.0);
        craftingExperience.put(Material.GOLD_INGOT, 5.0);
        craftingExperience.put(Material.IRON_SWORD, 8.0);
        craftingExperience.put(Material.IRON_PICKAXE, 10.0);
        craftingExperience.put(Material.IRON_AXE, 10.0);
        craftingExperience.put(Material.IRON_SHOVEL, 6.0);
        craftingExperience.put(Material.DIAMOND_SWORD, 20.0);
        craftingExperience.put(Material.DIAMOND_PICKAXE, 25.0);
        craftingExperience.put(Material.DIAMOND_AXE, 25.0);
        craftingExperience.put(Material.NETHERITE_INGOT, 50.0);
        
        // 醸造経験値テーブル
        brewingExperience.put(Material.POTION, 5.0);
        brewingExperience.put(Material.SPLASH_POTION, 8.0);
        brewingExperience.put(Material.LINGERING_POTION, 12.0);
        
        // エンチャント経験値テーブル（エンチャントレベル別）
        enchantingExperience.put(1, 10.0);
        enchantingExperience.put(2, 15.0);
        enchantingExperience.put(3, 25.0);
        enchantingExperience.put(4, 35.0);
        enchantingExperience.put(5, 50.0);
        
        // 建築経験値テーブル
        buildingExperience.put(Material.STONE, 0.2);
        buildingExperience.put(Material.COBBLESTONE, 0.1);
        buildingExperience.put(Material.STONE_BRICKS, 0.5);
        buildingExperience.put(Material.QUARTZ_BLOCK, 1.0);
        buildingExperience.put(Material.PRISMARINE, 1.5);
        buildingExperience.put(Material.PURPUR_BLOCK, 2.0);
        buildingExperience.put(Material.END_STONE_BRICKS, 2.5);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // 深層岩鉱石・深層岩石材を通常版へ正規化してから経験値マップを引く
        Material blockType = org.tofu.tofunomics.util.BlockNormalizer.normalizeForJob(event.getBlock().getType());

        // 鉱夫の採掘経験値
        if (jobManager.hasJob(player, "miner") && miningExperience.containsKey(blockType)) {
            double baseExp = miningExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "miner", baseExp);
            giveJobExperience(player, "miner", multipliedExp);
        }
        
        // 木こりの伐採経験値
        if (jobManager.hasJob(player, "woodcutter") && loggingExperience.containsKey(blockType)) {
            double baseExp = loggingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "woodcutter", baseExp);
            giveJobExperience(player, "woodcutter", multipliedExp);
        }
        
        // 農家の収穫経験値
        if (jobManager.hasJob(player, "farmer") && farmingExperience.containsKey(blockType)) {
            double baseExp = farmingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "farmer", baseExp);
            giveJobExperience(player, "farmer", multipliedExp);
        }
    }
    
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || 
            event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            
            Player player = event.getPlayer();
            if (jobManager.hasJob(player, "fisherman")) {
                double baseExp = fishingExperience.getOrDefault(event.getState(), 5.0);
                double multipliedExp = applyJobMultiplier(player, "fisherman", baseExp);
                giveJobExperience(player, "fisherman", multipliedExp);
            }
        }
    }
    
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();
        
        if (jobManager.hasJob(player, "blacksmith") && craftingExperience.containsKey(craftedItem)) {
            double baseExp = craftingExperience.get(craftedItem);
            double multipliedExp = applyJobMultiplier(player, "blacksmith", baseExp);
            giveJobExperience(player, "blacksmith", multipliedExp);
        }
    }
    
    @EventHandler
    public void onBrew(BrewEvent event) {
        // 醸造台の使用者を特定する必要がある（近くのプレイヤーをチェック）
        event.getBlock().getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distance(event.getBlock().getLocation()) <= 5.0)
            .forEach(player -> {
            if (jobManager.hasJob(player, "alchemist")) {
                double baseExp = brewingExperience.getOrDefault(Material.POTION, 5.0);
                double multipliedExp = applyJobMultiplier(player, "alchemist", baseExp);
                giveJobExperience(player, "alchemist", multipliedExp);
            }
        });
    }
    
    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (jobManager.hasJob(player, "enchanter")) {
            int totalLevels = event.getEnchantsToAdd().values().stream()
                .mapToInt(Integer::intValue).sum();
            
            double baseExp = enchantingExperience.getOrDefault(totalLevels, 10.0);
            double multipliedExp = applyJobMultiplier(player, "enchanter", baseExp);
            giveJobExperience(player, "enchanter", multipliedExp);
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        if (jobManager.hasJob(player, "architect") && buildingExperience.containsKey(blockType)) {
            double baseExp = buildingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "architect", baseExp);
            giveJobExperience(player, "architect", multipliedExp);
        }
    }
    
    /**
     * プレイヤーに職業経験値を付与
     */
    private void giveJobExperience(Player player, String jobName, double experience) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) return;
        
        double currentExp = playerJob.getExperience();
        int currentLevel = playerJob.getLevel();
        
        playerJob.addExperience(experience);
        
        // レベルアップチェック
        checkLevelUp(player, playerJob, jobName, currentLevel);
        
        // データベース更新
        playerJobDAO.updatePlayerJobData(playerJob);

        // 職業レベルBossBarへ即時反映（経験値獲得・レベルアップを即座に表示）
        if (jobLevelBossBarManager != null) {
            jobLevelBossBarManager.refresh(player);
        }

        // 経験値獲得メッセージ（5経験値以上の場合のみ表示）
        if (experience >= 5.0) {
            player.sendMessage(ChatColor.GREEN + String.format("+ %.1f %s経験値", 
                experience, configManager.getJobDisplayName(jobName)));
        }
    }
    
    /**
     * レベルアップチェックと処理
     */
    private void checkLevelUp(Player player, PlayerJob playerJob, String jobName, int previousLevel) {
        while (canPlayerLevelUp(playerJob)) {
            playerJob.levelUp();
            int newLevel = playerJob.getLevel();
            String displayName = configManager.getJobDisplayName(jobName);

            // レベルアップメッセージ（ログとしてチャットにも残す）
            player.sendMessage(ChatColor.GOLD + "★ レベルアップ！ " +
                displayName + " レベル " + newLevel + " に到達！");

            // Title演出（画面中央に大きく表示）
            if (configManager.isLevelUpTitleEnabled()) {
                notificationManager.sendTitle(player,
                    "&6&l★ LEVEL UP ★",
                    "&e" + displayName + " &fLv." + newLevel,
                    10, 50, 20);
            }

            // パーティクル演出
            if (configManager.isLevelUpParticleEnabled()) {
                notificationManager.playCelebrationEffect(player);
            }

            // 新しいツールの付与チェック
            jobToolManager.checkAndGiveNewTools(player, jobName, newLevel);

            // 職業レベル最大値チェック
            Job job = jobDAO.getJobByNameSafe(jobName);
            if (job != null && newLevel >= job.getMaxLevel()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "★ おめでとうございます！ " +
                    displayName + " の最大レベルに到達しました！");
                if (configManager.isLevelUpTitleEnabled()) {
                    notificationManager.sendTitle(player,
                        "&d&l★ MAX LEVEL ★",
                        "&d" + displayName + " &f最大レベル到達！",
                        10, 60, 20);
                }
                break;
            }
        }
    }
    
    /**
     * 職業レベルによる経験値倍率を適用
     */
    private double applyJobMultiplier(Player player, String jobName, double baseExperience) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) return baseExperience;
        
        // レベルが高いほど経験値獲得効率が下がる（現実的な成長曲線）
        double levelPenalty = Math.max(0.1, 1.0 - (playerJob.getLevel() * 0.01));
        
        // 設定ファイルの経験値倍率を適用
        Job job = jobDAO.getJobByNameSafe(jobName);
        double configMultiplier = job != null ?
            configManager.getJobExpMultiplier(jobName) : 1.0;

        // 食事バフ倍率を適用（バフ無し・失効時は1.0で従来と同一）
        double foodBuffMultiplier = (foodBuffManager != null) ?
            foodBuffManager.getMultiplier(player) : 1.0;

        return baseExperience * levelPenalty * configMultiplier * foodBuffMultiplier;
    }
    
    /**
     * プレイヤーがレベルアップ可能かチェック
     */
    private boolean canPlayerLevelUp(PlayerJob playerJob) {
        double currentExp = playerJob.getExperience();
        double requiredExp = experienceManager.calculateRequiredExperience(playerJob.getLevel() + 1);
        return currentExp >= requiredExp;
    }
    
    /**
     * 手動で経験値を付与（管理者用）
     */
    public boolean giveExperienceManual(Player player, String jobName, double amount) {
        if (!jobManager.hasJob(player, jobName)) {
            return false;
        }

        // 食事バフ倍率を適用（Mob討伐経験値などの経路でも一貫して反映。バフ無し時は1.0）
        double foodBuffMultiplier = (foodBuffManager != null) ?
            foodBuffManager.getMultiplier(player) : 1.0;

        giveJobExperience(player, jobName, amount * foodBuffMultiplier);
        return true;
    }
}