package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.Job;

public class JobsCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final ExperienceManager experienceManager;
    
    public JobsCommand(ConfigManager configManager, JobManager jobManager, ExperienceManager experienceManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.experienceManager = experienceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "list":
                return handleJobsList(player);
            case "join":
                return handleJobJoin(player, args);
            case "leave":
                return handleJobLeave(player, args);
            case "stats":
                return handleJobStats(player, args);
            case "info":
                return handleJobInfo(player, args);
            case "debug":
                return handleJobDebug(player);
            default:
                sendHelpMessage(player);
                return true;
        }
    }

    private boolean handleJobsList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 利用可能な職業 ===");
        
        for (String jobName : jobManager.getJobNames()) {
            String displayName = jobManager.getJobDisplayName(jobName);
            String description = configManager.getJobDescription(jobName);
            int maxLevel = configManager.getJobMaxLevel(jobName);
            double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
            
            player.sendMessage(ChatColor.YELLOW + "▶ " + displayName + ChatColor.GRAY + " (" + jobName + ")");
            if (description != null && !description.isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "  " + description);
            }
            player.sendMessage(ChatColor.AQUA + "  最大レベル: " + maxLevel + 
                             " | 収入倍率: " + String.format("%.1f", incomeMultiplier) + "x");
            
            // プレイヤーがこの職業に就いているかチェック
            if (jobManager.hasJob(player, jobName)) {
                PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
                if (playerJob != null) {
                    int currentLevel = playerJob.getLevel();
                    double currentExp = playerJob.getExperience();
                    int requiredExp = configManager.calculateRequiredExperience(currentLevel + 1);
                    
                    player.sendMessage(ChatColor.GREEN + "  ★ 現在就職中 - レベル " + currentLevel + 
                                     " (経験値: " + (int)currentExp + "/" + requiredExp + ")");
                }
            }
            player.sendMessage("");
        }
        
        player.sendMessage(ChatColor.GOLD + "職業に就くには: " + ChatColor.WHITE + "/jobs join <職業名>");
        return true;
    }

    private boolean handleJobJoin(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs join <職業名>");
            return true;
        }
        
        String jobName = args[1].toLowerCase();
        
        if (!jobManager.isValidJobName(jobName)) {
            player.sendMessage(ChatColor.RED + "存在しない職業です: " + jobName);
            player.sendMessage(ChatColor.YELLOW + "利用可能な職業: " + String.join(", ", jobManager.getJobNames()));
            return true;
        }
        
        JobManager.JobJoinResult result = jobManager.joinJob(player, jobName);
        
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                String message = configManager.getMessage("jobs.job_joined", "job", displayName);
                
                // メッセージが見つからない場合のフォールバック
                if (message.startsWith("メッセージが見つかりません:")) {
                    message = "&a職業「" + displayName + "」に就職しました。";
                }
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + message));
                break;
                
            case ALREADY_HAS_JOB:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("jobs.already_have_job")));
                break;
                
            case JOB_NOT_FOUND:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("jobs.job_not_found")));
                break;
                
            case DAILY_LIMIT_EXCEEDED:
                player.sendMessage(ChatColor.RED + "職業変更は1日1回までです。明日になったら再度お試しください。");
                break;
                
            case MAX_JOBS_REACHED:
                int maxJobs = configManager.getMaxJobsPerPlayer();
                player.sendMessage(ChatColor.RED + "同時に就ける職業数の上限(" + maxJobs + ")に達しています。");
                break;
                
            case DATABASE_ERROR:
                player.sendMessage(ChatColor.RED + "データベースエラーが発生しました。しばらくしてから再度お試しください。");
                break;
        }
        
        return true;
    }

    private boolean handleJobLeave(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs leave <職業名>");
            return true;
        }
        
        String jobName = args[1].toLowerCase();
        
        if (!jobManager.hasJob(player, jobName)) {
            player.sendMessage(ChatColor.RED + "その職業には就いていません: " + jobName);
            return true;
        }
        
        JobManager.JobLeaveResult result = jobManager.leaveJob(player, jobName);
        
        if (result == JobManager.JobLeaveResult.SUCCESS) {
            String displayName = jobManager.getJobDisplayName(jobName);
            String message = configManager.getMessage("jobs.job_left", "job", displayName);
            
            // メッセージが見つからない場合のフォールバック
            if (message.startsWith("メッセージが見つかりません:")) {
                message = "&a職業「" + displayName + "」を辞職しました。";
            }
            
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
        } else {
            player.sendMessage(ChatColor.RED + "職業の辞職に失敗しました。");
        }
        
        return true;
    }

    private boolean handleJobStats(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs stats [職業名]");
            return true;
        }
        
        if (args.length == 1) {
            // すべての職業の統計を表示
            player.sendMessage(ChatColor.GOLD + "=== あなたの職業統計 ===");
            
            for (PlayerJob playerJob : jobManager.getPlayerJobs(player)) {
                Job job = jobManager.getJobById(playerJob.getJobId());
                if (job != null) {
                    String displayName = job.getDisplayName();
                    int level = playerJob.getLevel();
                    double experience = playerJob.getExperience();
                    int requiredExp = configManager.calculateRequiredExperience(level + 1);
                    
                    player.sendMessage(ChatColor.YELLOW + "▶ " + displayName);
                    player.sendMessage(ChatColor.WHITE + "  レベル: " + level + " (経験値: " + (int)experience + "/" + requiredExp + ")");
                }
            }
        } else {
            // 特定の職業の統計を表示
            String jobName = args[1].toLowerCase();
            if (!jobManager.hasJob(player, jobName)) {
                player.sendMessage(ChatColor.RED + "その職業には就いていません: " + jobName);
                return true;
            }
            
            PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
            if (playerJob != null) {
                String displayName = jobManager.getJobDisplayName(jobName);
                int level = playerJob.getLevel();
                double experience = playerJob.getExperience();
                int requiredExp = configManager.calculateRequiredExperience(level + 1);
                
                player.sendMessage(ChatColor.GOLD + "=== " + displayName + " 統計 ===");
                player.sendMessage(ChatColor.WHITE + "レベル: " + level);
                player.sendMessage(ChatColor.WHITE + "経験値: " + (int)experience + "/" + requiredExp);
            }
        }
        
        return true;
    }

    private boolean handleJobInfo(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs info <職業名>");
            return true;
        }
        
        String jobName = args[1].toLowerCase();
        
        if (!jobManager.isValidJobName(jobName)) {
            player.sendMessage(ChatColor.RED + "存在しない職業です: " + jobName);
            return true;
        }
        
        String displayName = jobManager.getJobDisplayName(jobName);
        String description = configManager.getJobDescription(jobName);
        int maxLevel = configManager.getJobMaxLevel(jobName);
        double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
        double expMultiplier = configManager.getJobExpMultiplier(jobName);
        
        player.sendMessage(ChatColor.GOLD + "=== " + displayName + " 情報 ===");
        if (description != null && !description.isEmpty()) {
            player.sendMessage(ChatColor.WHITE + "説明: " + description);
        }
        player.sendMessage(ChatColor.WHITE + "最大レベル: " + maxLevel);
        player.sendMessage(ChatColor.WHITE + "収入倍率: " + String.format("%.1f", incomeMultiplier) + "x");
        player.sendMessage(ChatColor.WHITE + "経験値倍率: " + String.format("%.1f", expMultiplier) + "x");
        
        if (jobManager.hasJob(player, jobName)) {
            PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
            if (playerJob != null) {
                int currentLevel = playerJob.getLevel();
                player.sendMessage(ChatColor.GREEN + "現在のレベル: " + currentLevel);
            }
        }
        
        return true;
    }



    private boolean handleJobDebug(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 職業デバッグ情報 ===");
        
        // プレイヤーUUID表示
        player.sendMessage(ChatColor.YELLOW + "プレイヤーUUID: " + ChatColor.WHITE + player.getUniqueId().toString());
        
        // JobManagerから直接職業を取得
        String currentJob = jobManager.getPlayerJob(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "現在の職業 (JobManager): " + ChatColor.WHITE + (currentJob != null ? currentJob : "無職"));
        
        // すべてのプレイヤー職業を表示
        player.sendMessage(ChatColor.YELLOW + "所持している職業一覧:");
        for (PlayerJob playerJob : jobManager.getPlayerJobs(player)) {
            Job job = jobManager.getJobById(playerJob.getJobId());
            if (job != null) {
                player.sendMessage(ChatColor.WHITE + "  - " + job.getName() + " (ID: " + job.getId() + ", Level: " + playerJob.getLevel() + ")");
            }
        }
        
        // データベース確認
        player.sendMessage(ChatColor.YELLOW + "データベース状態確認中...");
        // この部分は実際のデータベース状態を確認するためのもの
        
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Jobs コマンドヘルプ ===");
        player.sendMessage(ChatColor.YELLOW + "/jobs list " + ChatColor.WHITE + "- 利用可能な職業一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs join <職業名> " + ChatColor.WHITE + "- 指定した職業に就く");
        player.sendMessage(ChatColor.YELLOW + "/jobs leave <職業名> " + ChatColor.WHITE + "- 指定した職業を辞める");
        player.sendMessage(ChatColor.YELLOW + "/jobs stats [職業名] " + ChatColor.WHITE + "- 職業の統計を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs info <職業名> " + ChatColor.WHITE + "- 職業の詳細情報を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs debug " + ChatColor.WHITE + "- 職業の詳細デバッグ情報を表示");
    }
}