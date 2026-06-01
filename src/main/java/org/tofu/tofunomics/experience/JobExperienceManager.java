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
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.tutorial.TutorialEventListener;

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
    private final org.tofu.tofunomics.events.AsyncEventUpdater asyncUpdater;
    
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
                               ExperienceManager experienceManager,
                               org.tofu.tofunomics.events.AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerJobDAO = playerJobDAO;
        this.jobDAO = jobDAO;
        this.jobManager = jobManager;
        this.jobToolManager = jobToolManager;
        this.experienceManager = experienceManager;
        this.asyncUpdater = asyncUpdater;
        
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
    
    private void initializeExperienceTables() {
        // 採掘経験値テーブル
        miningExperience.put(Material.COAL_ORE, 2.5);
        miningExperience.put(Material.IRON_ORE, 5.0);
        miningExperience.put(Material.GOLD_ORE, 8.0);
        miningExperience.put(Material.DIAMOND_ORE, 20.0);
        miningExperience.put(Material.EMERALD_ORE, 20.0);
        miningExperience.put(Material.LAPIS_ORE, 4.0);
        miningExperience.put(Material.REDSTONE_ORE, 3.0);
        miningExperience.put(Material.NETHER_QUARTZ_ORE, 6.0);
        miningExperience.put(Material.ANCIENT_DEBRIS, 50.0);
        // STONE: 自然生成のみ経験値獲得（プレイヤー設置はUnifiedEventHandlerで除外）
        miningExperience.put(Material.STONE, 5.0);  // 0.5 → 5.0（メッセージ表示のため）
        // COBBLESTONE: 経験値なし（設置→採掘での無限経験値防止）
        
        // 伐採経験値テーブル
        loggingExperience.put(Material.OAK_LOG, 3.0);
        loggingExperience.put(Material.BIRCH_LOG, 3.0);
        loggingExperience.put(Material.SPRUCE_LOG, 3.0);
        loggingExperience.put(Material.JUNGLE_LOG, 4.0);
        loggingExperience.put(Material.ACACIA_LOG, 4.0);
        loggingExperience.put(Material.DARK_OAK_LOG, 4.0);
        loggingExperience.put(Material.WARPED_STEM, 5.0);
        loggingExperience.put(Material.CRIMSON_STEM, 5.0);
        
        // 農業経験値テーブル
        farmingExperience.put(Material.WHEAT, 3.0);
        farmingExperience.put(Material.POTATO, 2.5);
        farmingExperience.put(Material.CARROT, 2.5);
        farmingExperience.put(Material.BEETROOT, 3.0);
        farmingExperience.put(Material.PUMPKIN, 3.5);
        farmingExperience.put(Material.MELON, 3.0);
        farmingExperience.put(Material.SUGAR_CANE, 2.0);
        farmingExperience.put(Material.COCOA_BEANS, 3.0);
        farmingExperience.put(Material.NETHER_WART, 3.5);
        
        // 釣り経験値テーブル（上方修正版）
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_FISH, 10.0);      // 5.0 → 10.0 (+100%)
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_ENTITY, 15.0);    // 8.0 → 15.0 (+88%)
        fishingExperience.put(PlayerFishEvent.State.IN_GROUND, 2.0);         // 1.0 → 2.0 (+100%)
        
        // 製作経験値テーブル（鍛冶屋）- 上方修正版
        craftingExperience.put(Material.IRON_INGOT, 5.0);           // 3.0 → 5.0 (+67%)
        craftingExperience.put(Material.GOLD_INGOT, 8.0);           // 5.0 → 8.0 (+60%)
        craftingExperience.put(Material.IRON_SWORD, 12.0);          // 8.0 → 12.0 (+50%)
        craftingExperience.put(Material.IRON_PICKAXE, 15.0);        // 10.0 → 15.0 (+50%)
        craftingExperience.put(Material.IRON_AXE, 15.0);            // 10.0 → 15.0 (+50%)
        craftingExperience.put(Material.IRON_SHOVEL, 9.0);          // 6.0 → 9.0 (+50%)
        craftingExperience.put(Material.DIAMOND_SWORD, 30.0);       // 20.0 → 30.0 (+50%)
        craftingExperience.put(Material.DIAMOND_PICKAXE, 35.0);     // 25.0 → 35.0 (+40%)
        craftingExperience.put(Material.DIAMOND_AXE, 35.0);         // 25.0 → 35.0 (+40%)
        craftingExperience.put(Material.NETHERITE_INGOT, 75.0);     // 50.0 → 75.0 (+50%)
        // 新規追加：防具カテゴリ
        craftingExperience.put(Material.IRON_HELMET, 10.0);
        craftingExperience.put(Material.IRON_CHESTPLATE, 10.0);
        craftingExperience.put(Material.IRON_LEGGINGS, 10.0);
        craftingExperience.put(Material.IRON_BOOTS, 10.0);
        craftingExperience.put(Material.DIAMOND_HELMET, 25.0);
        craftingExperience.put(Material.DIAMOND_CHESTPLATE, 25.0);
        craftingExperience.put(Material.DIAMOND_LEGGINGS, 25.0);
        craftingExperience.put(Material.DIAMOND_BOOTS, 25.0);
        // 新規追加：その他装備・素材
        craftingExperience.put(Material.SHIELD, 8.0);
        craftingExperience.put(Material.GOLDEN_APPLE, 15.0);
        craftingExperience.put(Material.IRON_BLOCK, 5.0);
        
        // 醸造経験値テーブル（上方修正版）
        brewingExperience.put(Material.POTION, 10.0);               // 5.0 → 10.0 (+100%)
        brewingExperience.put(Material.SPLASH_POTION, 15.0);        // 8.0 → 15.0 (+88%)
        brewingExperience.put(Material.LINGERING_POTION, 22.0);     // 12.0 → 22.0 (+83%)
        
        // エンチャント経験値テーブル（エンチャントレベル別）- 上方修正版
        enchantingExperience.put(1, 15.0);      // 10.0 → 15.0 (+50%)
        enchantingExperience.put(2, 25.0);      // 15.0 → 25.0 (+67%)
        enchantingExperience.put(3, 40.0);      // 25.0 → 40.0 (+60%)
        enchantingExperience.put(4, 55.0);      // 35.0 → 55.0 (+57%)
        enchantingExperience.put(5, 75.0);      // 50.0 → 75.0 (+50%)
        
        // 建築経験値テーブル（上方修正版）
        buildingExperience.put(Material.STONE, 0.8);                // 0.2 → 0.8 (+300%)
        buildingExperience.put(Material.COBBLESTONE, 0.5);          // 0.1 → 0.5 (+400%)
        buildingExperience.put(Material.STONE_BRICKS, 1.5);         // 0.5 → 1.5 (+200%)
        buildingExperience.put(Material.QUARTZ_BLOCK, 3.0);         // 1.0 → 3.0 (+200%)
        buildingExperience.put(Material.PRISMARINE, 4.0);           // 1.5 → 4.0 (+167%)
        buildingExperience.put(Material.PURPUR_BLOCK, 5.0);         // 2.0 → 5.0 (+150%)
        buildingExperience.put(Material.END_STONE_BRICKS, 6.0);     // 2.5 → 6.0 (+140%)
        // 新規追加：板材・レンガ・ガラス
        buildingExperience.put(Material.OAK_PLANKS, 0.6);
        buildingExperience.put(Material.BIRCH_PLANKS, 0.6);
        buildingExperience.put(Material.SPRUCE_PLANKS, 0.6);
        buildingExperience.put(Material.JUNGLE_PLANKS, 0.6);
        buildingExperience.put(Material.ACACIA_PLANKS, 0.6);
        buildingExperience.put(Material.DARK_OAK_PLANKS, 0.6);
        buildingExperience.put(Material.BRICKS, 2.0);
        buildingExperience.put(Material.GLASS, 1.0);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
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
                double baseExp = fishingExperience.getOrDefault(event.getState(), 10.0);
                
                // 基本収入（魚自体はNPCで売却可能。直接収入は釣りの待機時間に対する最低保証）
                double baseIncome = 1.0;  // 通常魚の直接収入
                boolean isTreasure = false;

                // 宝物釣りボーナス（エンチャント本、鞍、名札など）
                if (event.getCaught() != null && event.getCaught() instanceof org.bukkit.entity.Item) {
                    org.bukkit.entity.Item item = (org.bukkit.entity.Item) event.getCaught();
                    Material itemType = item.getItemStack().getType();
                    if (itemType == Material.ENCHANTED_BOOK || itemType == Material.SADDLE ||
                        itemType == Material.NAME_TAG || itemType == Material.NAUTILUS_SHELL ||
                        itemType == Material.BOW || itemType.name().contains("FISHING_ROD")) {
                        baseExp += 15.0;  // 宝物釣りボーナス +15exp
                        baseIncome = 5.0;  // 宝物収入（宝物自体の売却が主収入）
                        isTreasure = true;
                        player.sendMessage("§e§l[釣り] §6宝物ボーナス! §e+15経験値 §6+5金塊");
                    }
                }

                // エンティティ釣りボーナス
                if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && !isTreasure) {
                    baseIncome = 1.5;  // エンティティを釣った場合
                }

                // 雨天ボーナス（経験値のみ +20%。収入には適用しない）
                if (player.getWorld().hasStorm()) {
                    baseExp *= 1.20;
                }

                // 深夜ボーナス（経験値のみ +15%。収入には適用しない）- 13000-23000 tick
                long time = player.getWorld().getTime();
                if (time >= 13000 && time <= 23000) {
                    baseExp *= 1.15;
                }
                
                double multipliedExp = applyJobMultiplier(player, "fisherman", baseExp);
                giveJobExperience(player, "fisherman", multipliedExp);
                
                // 収入を銀行預金に加算
                if (asyncUpdater != null && baseIncome > 0) {
                    asyncUpdater.updatePlayerBalance(player.getUniqueId().toString(), baseIncome, "釣り人報酬");
                    if (!isTreasure) {
                        player.sendMessage("§e§l[釣り] §a+" + String.format("%.1f", multipliedExp) + "経験値 §6+" + String.format("%.1f", baseIncome) + "金塊");
                    }
                }
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
        
        // 経験値獲得メッセージ（5経験値以上の場合のみ表示）
        if (experience >= 5.0) {
            player.sendMessage(ChatColor.GREEN + String.format("+ %.1f %s経験値",
                experience, configManager.getJobDisplayName(jobName)));
        }

        // チュートリアル進捗: ステップ2（初収入）完了チェック
        TutorialEventListener tutorialListener = TofuNomics.getInstance().getTutorialEventListener();
        if (tutorialListener != null) {
            tutorialListener.onFirstIncome(player);
        }
    }
    
    /**
     * レベルアップチェックと処理
     */
    private void checkLevelUp(Player player, PlayerJob playerJob, String jobName, int previousLevel) {
        while (canPlayerLevelUp(playerJob)) {
            playerJob.levelUp();
            int newLevel = playerJob.getLevel();
            
            // レベルアップメッセージ
            player.sendMessage(ChatColor.GOLD + "★ レベルアップ！ " + 
                configManager.getJobDisplayName(jobName) + " レベル " + newLevel + " に到達！");
            
            // 新しいツールの付与チェック
            jobToolManager.checkAndGiveNewTools(player, jobName, newLevel);
            
            // 職業レベル最大値チェック
            Job job = jobDAO.getJobByNameSafe(jobName);
            if (job != null && newLevel >= job.getMaxLevel()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "★ おめでとうございます！ " + 
                    configManager.getJobDisplayName(jobName) + " の最大レベルに到達しました！");
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
        
        // レベルが高いほど経験値獲得効率が下がる（config.yml の leveling.experience.level_penalty）
        double penaltyFactor = configManager.getLevelPenaltyFactor();
        double penaltyMinimum = configManager.getLevelPenaltyMinimum();
        double levelPenalty = Math.max(penaltyMinimum, 1.0 - (playerJob.getLevel() * penaltyFactor));
        
        // 設定ファイルの経験値倍率を適用
        Job job = jobDAO.getJobByNameSafe(jobName);
        double configMultiplier = job != null ? 
            configManager.getJobExpMultiplier(jobName) : 1.0;
        
        return baseExperience * levelPenalty * configMultiplier;
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
        
        giveJobExperience(player, jobName, amount);
        return true;
    }
}