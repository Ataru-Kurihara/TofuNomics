package org.tofu.tofunomics.quests;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.*;

/**
 * 職業別クエスト・タスク管理システム
 */
public class JobQuestManager implements Listener {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    
    // クエストデータ（実際の実装ではデータベース管理）
    private final Map<String, List<JobQuest>> jobQuests;
    private final Map<String, List<PlayerQuestProgress>> playerQuests;
    
    public JobQuestManager(ConfigManager configManager, PlayerDAO playerDAO, JobManager jobManager) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.jobQuests = new HashMap<>();
        this.playerQuests = new HashMap<>();
        
        initializeJobQuests();
    }
    
    private void initializeJobQuests() {
        // 鉱夫のクエスト
        List<JobQuest> minerQuests = new ArrayList<>();
        
        JobQuest coalQuest = new JobQuest();
        coalQuest.setQuestId(1);
        coalQuest.setJobName("miner");
        coalQuest.setQuestName("石炭採掘任務");
        coalQuest.setDescription("石炭を32個採掘してください");
        coalQuest.setQuestType(JobQuest.QuestType.MINE);
        coalQuest.setTargetMaterial(Material.COAL_ORE);
        coalQuest.setTargetAmount(32);
        coalQuest.setExperienceReward(50.0);
        coalQuest.setIncomeReward(100.0);
        coalQuest.setRequiredLevel(1);
        coalQuest.setCooldownHours(24);
        coalQuest.setDaily(true);
        minerQuests.add(coalQuest);
        
        JobQuest ironQuest = new JobQuest();
        ironQuest.setQuestId(2);
        ironQuest.setJobName("miner");
        ironQuest.setQuestName("鉄鉱石採掘任務");
        ironQuest.setDescription("鉄鉱石を16個採掘してください");
        ironQuest.setQuestType(JobQuest.QuestType.MINE);
        ironQuest.setTargetMaterial(Material.IRON_ORE);
        ironQuest.setTargetAmount(16);
        ironQuest.setExperienceReward(80.0);
        ironQuest.setIncomeReward(200.0);
        ironQuest.setRequiredLevel(5);
        ironQuest.setCooldownHours(24);
        ironQuest.setDaily(true);
        minerQuests.add(ironQuest);
        
        jobQuests.put("miner", minerQuests);
        
        // 木こりのクエスト
        List<JobQuest> woodcutterQuests = new ArrayList<>();
        
        JobQuest logQuest = new JobQuest();
        logQuest.setQuestId(3);
        logQuest.setJobName("woodcutter");
        logQuest.setQuestName("原木伐採任務");
        logQuest.setDescription("どの種類でも原木を64個伐採してください");
        logQuest.setQuestType(JobQuest.QuestType.MINE);
        logQuest.setTargetMaterial(Material.OAK_LOG); // 任意の原木
        logQuest.setTargetAmount(64);
        logQuest.setExperienceReward(60.0);
        logQuest.setIncomeReward(120.0);
        logQuest.setRequiredLevel(1);
        logQuest.setCooldownHours(24);
        logQuest.setDaily(true);
        woodcutterQuests.add(logQuest);
        
        jobQuests.put("woodcutter", woodcutterQuests);
        
        // 農家のクエスト
        List<JobQuest> farmerQuests = new ArrayList<>();
        
        JobQuest wheatQuest = new JobQuest();
        wheatQuest.setQuestId(4);
        wheatQuest.setJobName("farmer");
        wheatQuest.setQuestName("小麦収穫任務");
        wheatQuest.setDescription("小麦を48個収穫してください");
        wheatQuest.setQuestType(JobQuest.QuestType.MINE);
        wheatQuest.setTargetMaterial(Material.WHEAT);
        wheatQuest.setTargetAmount(48);
        wheatQuest.setExperienceReward(40.0);
        wheatQuest.setIncomeReward(80.0);
        wheatQuest.setRequiredLevel(1);
        wheatQuest.setCooldownHours(24);
        wheatQuest.setDaily(true);
        farmerQuests.add(wheatQuest);
        
        jobQuests.put("farmer", farmerQuests);
        
        // 釣り人のクエスト
        List<JobQuest> fishermanQuests = new ArrayList<>();
        
        JobQuest fishQuest = new JobQuest();
        fishQuest.setQuestId(5);
        fishQuest.setJobName("fisherman");
        fishQuest.setQuestName("魚釣り任務");
        fishQuest.setDescription("魚を20匹釣ってください");
        fishQuest.setQuestType(JobQuest.QuestType.FISH);
        fishQuest.setTargetMaterial(Material.COD);
        fishQuest.setTargetAmount(20);
        fishQuest.setExperienceReward(70.0);
        fishQuest.setIncomeReward(150.0);
        fishQuest.setRequiredLevel(1);
        fishQuest.setCooldownHours(24);
        fishQuest.setDaily(true);
        fishermanQuests.add(fishQuest);
        
        jobQuests.put("fisherman", fishermanQuests);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        updateQuestProgress(player, JobQuest.QuestType.MINE, blockType, 1);
    }
    
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        
        org.bukkit.entity.Player player = event.getPlayer();
        if (event.getCaught() instanceof org.bukkit.entity.Item) {
            ItemStack caughtItem = ((org.bukkit.entity.Item) event.getCaught()).getItemStack();
            updateQuestProgress(player, JobQuest.QuestType.FISH, caughtItem.getType(), caughtItem.getAmount());
        }
    }
    
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();
        Material craftedType = event.getRecipe().getResult().getType();
        int amount = event.getRecipe().getResult().getAmount();
        
        updateQuestProgress(player, JobQuest.QuestType.CRAFT, craftedType, amount);
    }
    
    /**
     * クエスト進行状況を更新
     */
    private void updateQuestProgress(org.bukkit.entity.Player player, JobQuest.QuestType questType, Material material, int amount) {
        String uuid = player.getUniqueId().toString();
        List<PlayerQuestProgress> activeQuests = playerQuests.getOrDefault(uuid, new ArrayList<>());
        
        for (PlayerQuestProgress progress : activeQuests) {
            if (!progress.isActive()) continue;
            
            JobQuest quest = getQuestById(progress.getQuestId());
            if (quest == null) continue;
            
            // プレイヤーがその職業に就いているかチェック
            if (!jobManager.hasJob(player, quest.getJobName())) continue;
            
            // クエスト条件にマッチするかチェック
            if (quest.getQuestType() == questType && 
                (quest.getTargetMaterial() == material || isAnyLogType(quest, material))) {
                
                progress.addProgress(amount);
                
                // 進行度メッセージ
                player.sendMessage(ChatColor.YELLOW + String.format("クエスト進行: %s (%d/%d)", 
                    quest.getQuestName(), progress.getCurrentProgress(), quest.getTargetAmount()));
                
                // 完了チェック
                if (progress.getCurrentProgress() >= quest.getTargetAmount()) {
                    completeQuest(player, quest, progress);
                }
            }
        }
    }
    
    /**
     * 原木系統の判定（木こりクエスト用）
     */
    private boolean isAnyLogType(JobQuest quest, Material material) {
        if (quest.getJobName().equals("woodcutter") && quest.getTargetMaterial() == Material.OAK_LOG) {
            return material == Material.OAK_LOG || material == Material.BIRCH_LOG ||
                   material == Material.SPRUCE_LOG || material == Material.JUNGLE_LOG ||
                   material == Material.ACACIA_LOG || material == Material.DARK_OAK_LOG;
        }
        return false;
    }
    
    /**
     * クエスト完了処理
     */
    private void completeQuest(org.bukkit.entity.Player player, JobQuest quest, PlayerQuestProgress progress) {
        progress.complete();
        
        // 報酬付与
        giveQuestRewards(player, quest);
        
        // 完了メッセージ
        player.sendMessage(ChatColor.GOLD + "★ クエスト完了: " + quest.getQuestName());
        player.sendMessage(ChatColor.GREEN + "報酬: " + quest.getExperienceReward() + "経験値, " + 
                          quest.getIncomeReward() + configManager.getCurrencySymbol());
    }
    
    /**
     * クエスト報酬付与
     */
    private void giveQuestRewards(org.bukkit.entity.Player player, JobQuest quest) {
        String uuid = player.getUniqueId().toString();
        
        // 経験値報酬（実装は簡略化）
        // jobManager.giveJobExperience(player, quest.getJobName(), quest.getExperienceReward());
        
        // 金銭報酬
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(configManager.getStartingBalance());
            playerDAO.insertPlayer(tofuPlayer);
        }
        
        tofuPlayer.addBalance(quest.getIncomeReward());
        playerDAO.updatePlayerData(tofuPlayer);
    }
    
    /**
     * プレイヤーがクエストを受諾
     */
    public boolean acceptQuest(org.bukkit.entity.Player player, int questId) {
        JobQuest quest = getQuestById(questId);
        if (quest == null) return false;
        
        if (!jobManager.hasJob(player, quest.getJobName())) {
            player.sendMessage(ChatColor.RED + "このクエストには " + 
                configManager.getJobDisplayName(quest.getJobName()) + " の職業が必要です。");
            return false;
        }
        
        PlayerJob playerJob = jobManager.getPlayerJob(player, quest.getJobName());
        if (playerJob == null || !quest.canPlayerAccept(playerJob.getLevel())) {
            player.sendMessage(ChatColor.RED + "職業レベル " + quest.getRequiredLevel() + " 以上が必要です。");
            return false;
        }
        
        String uuid = player.getUniqueId().toString();
        List<PlayerQuestProgress> playerQuestList = playerQuests.computeIfAbsent(uuid, k -> new ArrayList<>());
        
        // 既に受諾済みかチェック
        for (PlayerQuestProgress progress : playerQuestList) {
            if (progress.getQuestId() == questId && progress.isActive()) {
                player.sendMessage(ChatColor.RED + "既に受諾済みのクエストです。");
                return false;
            }
        }
        
        PlayerQuestProgress progress = new PlayerQuestProgress(uuid, questId);
        playerQuestList.add(progress);
        
        player.sendMessage(ChatColor.GREEN + "クエスト受諾: " + quest.getQuestName());
        player.sendMessage(ChatColor.YELLOW + quest.getDescription());
        
        return true;
    }
    
    /**
     * プレイヤーの利用可能なクエスト一覧を取得
     */
    public List<JobQuest> getAvailableQuests(org.bukkit.entity.Player player, String jobName) {
        List<JobQuest> available = new ArrayList<>();
        List<JobQuest> jobQuestList = jobQuests.get(jobName);
        
        if (jobQuestList == null) return available;
        
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) return available;
        
        for (JobQuest quest : jobQuestList) {
            if (quest.canPlayerAccept(playerJob.getLevel())) {
                available.add(quest);
            }
        }
        
        return available;
    }
    
    /**
     * IDからクエストを取得
     */
    private JobQuest getQuestById(int questId) {
        for (List<JobQuest> quests : jobQuests.values()) {
            for (JobQuest quest : quests) {
                if (quest.getQuestId() == questId) {
                    return quest;
                }
            }
        }
        return null;
    }
    
    /**
     * プレイヤーのアクティブクエスト一覧を取得
     */
    public List<PlayerQuestProgress> getActiveQuests(org.bukkit.entity.Player player) {
        String uuid = player.getUniqueId().toString();
        List<PlayerQuestProgress> playerQuestList = playerQuests.get(uuid);
        
        if (playerQuestList == null) return new ArrayList<>();
        
        List<PlayerQuestProgress> active = new ArrayList<>();
        for (PlayerQuestProgress progress : playerQuestList) {
            if (progress.isActive()) {
                active.add(progress);
            }
        }
        
        return active;
    }
}