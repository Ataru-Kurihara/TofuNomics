package org.tofu.tofunomics.stats;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.rewards.JobLevelRewardManager;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * 職業別統計・進捗表示システム
 */
public class JobStatsManager {
    
    private final ConfigManager configManager;
    private final PlayerJobDAO playerJobDAO;
    private final JobManager jobManager;
    private final JobLevelRewardManager rewardManager;
    private final DecimalFormat decimalFormat;
    
    public JobStatsManager(ConfigManager configManager, PlayerJobDAO playerJobDAO, 
                          JobManager jobManager, JobLevelRewardManager rewardManager) {
        this.configManager = configManager;
        this.playerJobDAO = playerJobDAO;
        this.jobManager = jobManager;
        this.rewardManager = rewardManager;
        this.decimalFormat = new DecimalFormat("#,##0.0");
    }
    
    /**
     * プレイヤーの全職業統計を表示
     */
    public void showAllJobStats(Player player) {
        List<PlayerJob> playerJobs = jobManager.getPlayerJobs(player);
        
        if (playerJobs.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "現在就職していません。");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬ 職業統計 ▬▬▬▬▬▬▬▬▬▬▬");
        
        for (PlayerJob playerJob : playerJobs) {
            String jobName = getJobNameById(playerJob.getJobId());
            showSingleJobStats(player, playerJob, jobName, false);
        }
        
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * 特定職業の詳細統計を表示
     */
    public void showJobStats(Player player, String jobName) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        
        if (playerJob == null) {
            player.sendMessage(ChatColor.RED + "職業「" + 
                configManager.getJobDisplayName(jobName) + "」に就職していません。");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬ " + 
            configManager.getJobDisplayName(jobName) + " 統計 ▬▬▬▬▬▬");
        
        showSingleJobStats(player, playerJob, jobName, true);
        
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * 単一職業の統計表示（内部メソッド）
     */
    private void showSingleJobStats(Player player, PlayerJob playerJob, String jobName, boolean detailed) {
        String displayName = configManager.getJobDisplayName(jobName);
        int currentLevel = playerJob.getLevel();
        double currentExp = playerJob.getExperience();
        
        // レベル情報
        String levelInfo = String.format("%s%s: %sレベル %d", 
            ChatColor.AQUA, displayName, ChatColor.WHITE, currentLevel);
        player.sendMessage(levelInfo);
        
        // 経験値情報
        double requiredExp = calculateRequiredExperience(currentLevel + 1);
        double progressPercent = (currentExp / requiredExp) * 100.0;
        
        String expInfo = String.format("  %s経験値: %s%s / %s (%.1f%%)", 
            ChatColor.GREEN, 
            ChatColor.YELLOW, decimalFormat.format(currentExp),
            decimalFormat.format(requiredExp), progressPercent);
        player.sendMessage(expInfo);
        
        // 経験値バー表示
        if (detailed) {
            player.sendMessage("  " + createProgressBar(progressPercent));
        }
        
        // 次のレベルまでの必要経験値
        double remainingExp = requiredExp - currentExp;
        if (remainingExp > 0) {
            player.sendMessage(String.format("  %s次のレベルまで: %s%s経験値", 
                ChatColor.GRAY, ChatColor.WHITE, decimalFormat.format(remainingExp)));
        }
        
        // 次の報酬レベル情報
        int nextRewardLevel = rewardManager.getNextRewardLevel(jobName, currentLevel);
        if (nextRewardLevel != -1) {
            player.sendMessage(String.format("  %s次の報酬: %sレベル %d", 
                ChatColor.LIGHT_PURPLE, ChatColor.GOLD, nextRewardLevel));
        }
        
        // 詳細統計（詳細表示時のみ）
        if (detailed) {
            showDetailedJobStats(player, playerJob, jobName);
        }
    }
    
    /**
     * 詳細職業統計表示
     */
    private void showDetailedJobStats(Player player, PlayerJob playerJob, String jobName) {
        // 職業転職制限情報
        if (configManager.isDailyJobChangeLimitEnabled()) {
            boolean canChange = jobManager.canChangeJobToday(player);
            String changeStatus = canChange ? 
                ChatColor.GREEN + "転職可能" : ChatColor.RED + "転職不可（24時間制限中）";
            player.sendMessage("  " + ChatColor.GRAY + "転職状況: " + changeStatus);
        }
        
        // 職業固有統計
        showJobSpecificStats(player, playerJob, jobName);
        
        // 到達済み報酬レベル
        List<Integer> allRewardLevels = rewardManager.getAllRewardLevels(jobName);
        if (!allRewardLevels.isEmpty()) {
            StringBuilder achievedRewards = new StringBuilder();
            achievedRewards.append("  ").append(ChatColor.YELLOW).append("取得済み報酬: ");
            
            boolean hasAchieved = false;
            for (int rewardLevel : allRewardLevels) {
                if (playerJob.getLevel() >= rewardLevel) {
                    if (hasAchieved) achievedRewards.append(", ");
                    achievedRewards.append(ChatColor.GOLD).append("Lv").append(rewardLevel);
                    hasAchieved = true;
                }
            }
            
            if (!hasAchieved) {
                achievedRewards.append(ChatColor.GRAY).append("なし");
            }
            
            player.sendMessage(achievedRewards.toString());
        }
        
        // 職業ランキング情報
        showJobRanking(player, playerJob, jobName);
    }
    
    /**
     * 職業固有の統計情報表示
     */
    private void showJobSpecificStats(Player player, PlayerJob playerJob, String jobName) {
        switch (jobName.toLowerCase()) {
            case "miner":
                player.sendMessage(String.format("  %s採掘効率: %s+%.1f%%", 
                    ChatColor.DARK_GRAY, ChatColor.GREEN, playerJob.getLevel() * 2.0));
                break;
            case "woodcutter":
                player.sendMessage(String.format("  %s伐採効率: %s+%.1f%%", 
                    ChatColor.GREEN, ChatColor.GREEN, playerJob.getLevel() * 1.5));
                break;
            case "farmer":
                player.sendMessage(String.format("  %s作物成長: %s+%.1f%%", 
                    ChatColor.YELLOW, ChatColor.GREEN, playerJob.getLevel() * 1.2));
                break;
            case "fisherman":
                player.sendMessage(String.format("  %s釣り運: %s+%.1f%%", 
                    ChatColor.AQUA, ChatColor.GREEN, playerJob.getLevel() * 1.8));
                break;
            case "blacksmith":
                player.sendMessage(String.format("  %s製作効率: %s+%.1f%%", 
                    ChatColor.GRAY, ChatColor.GREEN, playerJob.getLevel() * 2.5));
                break;
            case "alchemist":
                player.sendMessage(String.format("  %sポーション効果: %s+%.1f%%", 
                    ChatColor.LIGHT_PURPLE, ChatColor.GREEN, playerJob.getLevel() * 1.3));
                break;
            case "enchanter":
                player.sendMessage(String.format("  %sエンチャント成功率: %s+%.1f%%", 
                    ChatColor.BLUE, ChatColor.GREEN, playerJob.getLevel() * 1.0));
                break;
            case "architect":
                player.sendMessage(String.format("  %s建築速度: %s+%.1f%%", 
                    ChatColor.WHITE, ChatColor.GREEN, playerJob.getLevel() * 2.2));
                break;
        }
    }
    
    /**
     * 職業別ランキング情報表示
     */
    private void showJobRanking(Player player, PlayerJob playerJob, String jobName) {
        // 実装は簡略化（実際にはデータベースから他プレイヤーとの比較）
        List<PlayerJob> topPlayers;
        try {
            topPlayers = playerJobDAO.getTopPlayersByJobLevel(playerJob.getJobId(), 10);
        } catch (Exception e) {
            topPlayers = Arrays.asList();
        }
        
        int playerRank = 1;
        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerJob topPlayer = topPlayers.get(i);
            if (topPlayer.getUuid().toString().equals(player.getUniqueId().toString())) {
                playerRank = i + 1;
                break;
            }
        }
        
        player.sendMessage(String.format("  %s%sランキング: %s%d位 / %d人", 
            ChatColor.GOLD, configManager.getJobDisplayName(jobName),
            ChatColor.WHITE, playerRank, topPlayers.size()));
    }
    
    /**
     * 経験値プログレスバーの生成
     */
    private String createProgressBar(double percentage) {
        int totalBars = 20;
        int filledBars = (int) Math.round(percentage / 100.0 * totalBars);
        
        StringBuilder progressBar = new StringBuilder();
        progressBar.append(ChatColor.GREEN);
        
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                progressBar.append("█");
            } else {
                progressBar.append(ChatColor.GRAY).append("░");
            }
        }
        
        progressBar.append(ChatColor.WHITE).append(" ").append(String.format("%.1f%%", percentage));
        
        return progressBar.toString();
    }
    
    /**
     * 職業別トップランキングを表示
     */
    public void showJobTopRanking(Player player, String jobName, int limit) {
        int jobId = getJobIdByName(jobName);
        if (jobId == -1) {
            player.sendMessage(ChatColor.RED + "存在しない職業名です。");
            return;
        }
        
        List<PlayerJob> topPlayers;
        try {
            topPlayers = playerJobDAO.getTopPlayersByJobLevel(jobId, limit);
        } catch (Exception e) {
            topPlayers = Arrays.asList();
        }
        
        if (topPlayers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "ランキングデータがありません。");
            return;
        }
        
        String displayName = configManager.getJobDisplayName(jobName);
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬ " + displayName + " ランキング TOP " + limit + " ▬▬▬▬▬");
        
        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerJob playerJob = topPlayers.get(i);
            int rank = i + 1;
            
            String playerName = getPlayerNameByUUID(playerJob.getUuid().toString());
            ChatColor rankColor = getRankColor(rank);
            
            player.sendMessage(String.format("%s%d位 %s%s %sLv.%d (%s経験値)", 
                rankColor, rank, ChatColor.WHITE, playerName,
                ChatColor.YELLOW, playerJob.getLevel(),
                decimalFormat.format(playerJob.getExperience())));
        }
        
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * レベルアップに必要な経験値を計算
     */
    private double calculateRequiredExperience(int level) {
        return Math.pow(level, 2.2) * 100.0;
    }
    
    /**
     * ランクの色を取得
     */
    private ChatColor getRankColor(int rank) {
        switch (rank) {
            case 1: return ChatColor.GOLD;
            case 2: return ChatColor.GRAY;
            case 3: return ChatColor.DARK_RED;
            default: return ChatColor.YELLOW;
        }
    }
    
    /**
     * jobIdから職業名を取得（簡略実装）
     */
    private String getJobNameById(int jobId) {
        switch (jobId) {
            case 1: return "miner";
            case 2: return "woodcutter";
            case 3: return "farmer";
            case 4: return "fisherman";
            case 5: return "blacksmith";
            case 6: return "alchemist";
            case 7: return "enchanter";
            case 8: return "architect";
            default: return "unknown";
        }
    }
    
    /**
     * 職業名からjobIdを取得（簡略実装）
     */
    private int getJobIdByName(String jobName) {
        switch (jobName.toLowerCase()) {
            case "miner": return 1;
            case "woodcutter": return 2;
            case "farmer": return 3;
            case "fisherman": return 4;
            case "blacksmith": return 5;
            case "alchemist": return 6;
            case "enchanter": return 7;
            case "architect": return 8;
            default: return -1;
        }
    }
    
    /**
     * UUIDからプレイヤー名を取得（簡略実装）
     */
    private String getPlayerNameByUUID(String uuid) {
        // 実際の実装では Bukkit.getOfflinePlayer() を使用
        return "Player"; // プレースホルダー
    }
}