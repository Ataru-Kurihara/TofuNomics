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
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.tutorial.TutorialEventListener;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.rewards.JobGuideBookManager;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;

public class JobsCommand implements CommandExecutor, TabCompleter {
    
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final ExperienceManager experienceManager;
    private final PlayerJobDAO playerJobDAO;
    
    public JobsCommand(ConfigManager configManager, JobManager jobManager, ExperienceManager experienceManager, PlayerJobDAO playerJobDAO) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.experienceManager = experienceManager;
        this.playerJobDAO = playerJobDAO;
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
            case "admin":
                return handleJobAdmin(sender, args);
            case "guide":
                return handleJobGuide(player, args);
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

                // チュートリアル進捗: ステップ1（職業就職）完了チェック
                TutorialEventListener tutorialListener = TofuNomics.getInstance().getTutorialEventListener();
                if (tutorialListener != null) {
                    tutorialListener.onJobJoined(player);
                }
                
                // ガイドブックを配布
                JobGuideBookManager guideBookManager = TofuNomics.getInstance().getJobGuideBookManager();
                if (guideBookManager != null) {
                    guideBookManager.giveGuideBook(player, jobName);
                }
                break;
                
            case ALREADY_HAS_JOB:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("jobs.already_have_job")));
                break;
                
            case JOB_NOT_FOUND:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("jobs.job_not_found")));
                break;
                
            case LEVEL_TOO_LOW:
                player.sendMessage(ChatColor.RED + "転職するには現在の職業レベルが50以上必要です。");
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
        
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                String message = configManager.getMessage("jobs.job_left", "job", displayName);
                
                // メッセージが見つからない場合のフォールバック
                if (message.startsWith("メッセージが見つかりません:")) {
                    message = "&a職業「" + displayName + "」を辞職しました。";
                }
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + message));
                break;
                
            case LEVEL_TOO_LOW:
                player.sendMessage(ChatColor.RED + "レベル50未満の職業は辞職できません。レベル50に達してから辞職してください。");
                break;
                
            case DAILY_LIMIT_EXCEEDED:
                player.sendMessage(ChatColor.RED + "本日はすでに職業を変更しています。明日再度お試しください。");
                break;
                
            case NO_SUCH_JOB:
            case DATABASE_ERROR:
            default:
                player.sendMessage(ChatColor.RED + "職業の辞職に失敗しました。");
                break;
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

    /**
     * 管理者コマンドのハンドラー
     */
    private boolean handleJobAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.jobs.admin")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin <サブコマンド>");
            sender.sendMessage(ChatColor.YELLOW + "  forceleave <player> <job> - プレイヤーを強制辞職させる");
            sender.sendMessage(ChatColor.YELLOW + "  forcejoin <player> <job> - プレイヤーを強制就職させる");
            sender.sendMessage(ChatColor.YELLOW + "  setlevel <player> <job|all> <level> - レベルを設定");
            sender.sendMessage(ChatColor.YELLOW + "  addexp <player> <job|all> <amount> - 経験値を追加");
            sender.sendMessage(ChatColor.YELLOW + "  setexp <player> <job|all> <amount> - 経験値を設定");
            sender.sendMessage(ChatColor.YELLOW + "  reset <player> <job|all> - 職業をリセット");
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "forceleave":
                return handleJobAdminForceLeave(sender, args);
            case "setlevel":
                return handleJobAdminSetLevel(sender, args);
            case "addexp":
                return handleJobAdminAddExp(sender, args);
            case "setexp":
                return handleJobAdminSetExp(sender, args);
            case "forcejoin":
                return handleJobAdminForceJoin(sender, args);
            case "reset":
                return handleJobAdminReset(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "利用可能なサブコマンド: forceleave, setlevel, addexp, setexp, forcejoin, reset");
                return true;
        }
    }
    
    /**
     * 管理者による強制辞職コマンド
     */
    private boolean handleJobAdminForceLeave(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin forceleave <player> <jobName>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        
        Player targetPlayer = sender.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        if (!jobManager.hasJob(targetPlayer, jobName)) {
            sender.sendMessage(ChatColor.RED + "そのプレイヤーはその職業に就いていません: " + jobName);
            return true;
        }
        
        JobManager.JobLeaveResult result = jobManager.forceLeaveJob(targetPlayer, jobName);
        
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                sender.sendMessage(ChatColor.GREEN + "プレイヤー " + playerName + " を職業「" + displayName + "」から強制的に辞職させました。");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」から辞職させられました。");
                break;
                
            case DAILY_LIMIT_EXCEEDED:
                sender.sendMessage(ChatColor.RED + "本日はすでに職業を変更しています。");
                break;
                
            case NO_SUCH_JOB:
            case DATABASE_ERROR:
            default:
                sender.sendMessage(ChatColor.RED + "職業の強制辞職に失敗しました。");
                break;
        }
        
        return true;
    }
    
    /**
     * 管理者によるレベル設定コマンド
     */
    private boolean handleJobAdminSetLevel(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin setlevel <player> <job|all> <level>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        int level;
        
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "レベルは数値で指定してください。");
            return true;
        }
        
        if (level < 1) {
            sender.sendMessage(ChatColor.RED + "レベルは1以上で指定してください。");
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        if (jobName.equals("all")) {
            // 全職業にレベルを設定
            List<PlayerJob> jobs = jobManager.getPlayerJobs(targetPlayer);
            if (jobs.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは職業に就いていません。");
                return true;
            }
            for (PlayerJob pj : jobs) {
                Job job = jobManager.getJobById(pj.getJobId());
                if (job != null) {
                    experienceManager.setLevel(pj, job.getName(), level);
                }
            }
            sender.sendMessage(ChatColor.GREEN + playerName + " の全職業レベルを " + level + " に設定しました。");
            targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により全職業レベルが " + level + " に設定されました。");
        } else {
            PlayerJob playerJob = jobManager.getPlayerJob(targetPlayer, jobName);
            if (playerJob == null) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーはその職業に就いていません: " + jobName);
                return true;
            }
            if (experienceManager.setLevel(playerJob, jobName, level)) {
                String displayName = jobManager.getJobDisplayName(jobName);
                sender.sendMessage(ChatColor.GREEN + playerName + " の職業「" + displayName + "」レベルを " + level + " に設定しました。");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」のレベルが " + level + " に設定されました。");
            } else {
                sender.sendMessage(ChatColor.RED + "レベルの設定に失敗しました。");
            }
        }
        
        return true;
    }
    
    /**
     * 管理者による経験値追加コマンド
     */
    private boolean handleJobAdminAddExp(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin addexp <player> <job|all> <amount>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        double amount;
        
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "経験値は数値で指定してください。");
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "経験値は正の数で指定してください。");
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        if (jobName.equals("all")) {
            List<PlayerJob> jobs = jobManager.getPlayerJobs(targetPlayer);
            if (jobs.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは職業に就いていません。");
                return true;
            }
            for (PlayerJob pj : jobs) {
                Job job = jobManager.getJobById(pj.getJobId());
                if (job != null) {
                    experienceManager.addExperience(pj, job.getName(), amount);
                }
            }
            sender.sendMessage(ChatColor.GREEN + playerName + " の全職業に経験値 " + amount + " を追加しました。");
            targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により全職業に経験値 " + amount + " が追加されました。");
        } else {
            PlayerJob playerJob = jobManager.getPlayerJob(targetPlayer, jobName);
            if (playerJob == null) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーはその職業に就いていません: " + jobName);
                return true;
            }
            ExperienceManager.ExperienceAddResult result = experienceManager.addExperience(playerJob, jobName, amount);
            String displayName = jobManager.getJobDisplayName(jobName);
            switch (result) {
                case SUCCESS:
                case LEVEL_UP:
                    sender.sendMessage(ChatColor.GREEN + playerName + " の職業「" + displayName + "」に経験値 " + amount + " を追加しました。");
                    targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」に経験値 " + amount + " が追加されました。");
                    break;
                case MAX_LEVEL_REACHED:
                    sender.sendMessage(ChatColor.YELLOW + playerName + " は職業「" + displayName + "」で最大レベルに達しています。");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "経験値の追加に失敗しました。");
                    break;
            }
        }
        
        return true;
    }
    
    /**
     * 管理者による経験値直接設定コマンド
     */
    private boolean handleJobAdminSetExp(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin setexp <player> <job|all> <amount>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        double amount;
        
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "経験値は数値で指定してください。");
            return true;
        }
        
        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "経験値は0以上で指定してください。");
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        if (jobName.equals("all")) {
            List<PlayerJob> jobs = jobManager.getPlayerJobs(targetPlayer);
            if (jobs.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは職業に就いていません。");
                return true;
            }
            for (PlayerJob pj : jobs) {
                Job job = jobManager.getJobById(pj.getJobId());
                if (job != null) {
                    experienceManager.setExperience(pj, job.getName(), amount);
                }
            }
            sender.sendMessage(ChatColor.GREEN + playerName + " の全職業の経験値を " + amount + " に設定しました。");
            targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により全職業の経験値が " + amount + " に設定されました。");
        } else {
            PlayerJob playerJob = jobManager.getPlayerJob(targetPlayer, jobName);
            if (playerJob == null) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーはその職業に就いていません: " + jobName);
                return true;
            }
            if (experienceManager.setExperience(playerJob, jobName, amount)) {
                String displayName = jobManager.getJobDisplayName(jobName);
                sender.sendMessage(ChatColor.GREEN + playerName + " の職業「" + displayName + "」の経験値を " + amount + " に設定しました。");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」の経験値が " + amount + " に設定されました。");
            } else {
                sender.sendMessage(ChatColor.RED + "経験値の設定に失敗しました。");
            }
        }
        
        return true;
    }
    
    /**
     * 管理者による強制就職コマンド（レベル50制限なし）
     */
    private boolean handleJobAdminForceJoin(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin forcejoin <player> <job>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        JobManager.JobJoinResult result = jobManager.forceJoinJob(targetPlayer, jobName);
        
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                sender.sendMessage(ChatColor.GREEN + playerName + " を職業「" + displayName + "」に強制就職させました。");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」に就職しました。");
                break;
            case JOB_NOT_FOUND:
                sender.sendMessage(ChatColor.RED + "職業が見つかりません: " + jobName);
                break;
            case ALREADY_HAS_JOB:
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは既にその職業に就いています。");
                break;
            case MAX_JOBS_REACHED:
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは最大職業数に達しています。");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "強制就職に失敗しました。");
                break;
        }
        
        return true;
    }
    
    /**
     * 管理者による職業リセットコマンド
     */
    private boolean handleJobAdminReset(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "使用法: /jobs admin reset <player> <job|all>");
            return true;
        }
        
        String playerName = args[2];
        String jobName = args[3].toLowerCase();
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + playerName);
            return true;
        }
        
        if (jobName.equals("all")) {
            List<PlayerJob> jobs = jobManager.getPlayerJobs(targetPlayer);
            if (jobs.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーは職業に就いていません。");
                return true;
            }
            for (PlayerJob pj : jobs) {
                pj.setLevel(1);
                pj.setExperience(0.0);
                playerJobDAO.updatePlayerJobData(pj);
            }
            sender.sendMessage(ChatColor.GREEN + playerName + " の全職業をリセットしました（レベル1・経験値0）。");
            targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により全職業がリセットされました（レベル1・経験値0）。");
        } else {
            PlayerJob playerJob = jobManager.getPlayerJob(targetPlayer, jobName);
            if (playerJob == null) {
                sender.sendMessage(ChatColor.RED + "そのプレイヤーはその職業に就いていません: " + jobName);
                return true;
            }
            playerJob.setLevel(1);
            playerJob.setExperience(0.0);
            if (playerJobDAO.updatePlayerJobData(playerJob)) {
                String displayName = jobManager.getJobDisplayName(jobName);
                sender.sendMessage(ChatColor.GREEN + playerName + " の職業「" + displayName + "」をリセットしました（レベル1・経験値0）。");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により職業「" + displayName + "」がリセットされました（レベル1・経験値0）。");
            } else {
                sender.sendMessage(ChatColor.RED + "リセットに失敗しました。");
            }
        }
        
        return true;
    }
    
    /**
     * /jobs guide コマンド - ガイドブックを取得
     */
    private boolean handleJobGuide(Player player, String[] args) {
        JobGuideBookManager guideBookManager = TofuNomics.getInstance().getJobGuideBookManager();
        if (guideBookManager == null || !guideBookManager.isEnabled()) {
            player.sendMessage(ChatColor.RED + "ガイドブック機能は現在無効です。");
            return true;
        }
        
        // 現在の職業を取得
        List<PlayerJob> playerJobs = jobManager.getPlayerJobs(player);
        if (playerJobs == null || playerJobs.isEmpty()) {
            player.sendMessage(ChatColor.RED + "職業に就いていないため、ガイドブックを取得できません。");
            player.sendMessage(ChatColor.YELLOW + "まずは /jobs join <職業名> で職業に就いてください。");
            return true;
        }
        
        String targetJob;
        
        if (args.length >= 2) {
            // 指定された職業のガイドブックを取得
            targetJob = args[1].toLowerCase();
            
            // その職業に就いているか確認
            boolean hasJob = playerJobs.stream()
                .anyMatch(pj -> {
                    Job job = jobManager.getJobById(pj.getJobId());
                    return job != null && job.getName().equalsIgnoreCase(targetJob);
                });
            
            if (!hasJob) {
                String displayName = jobManager.getJobDisplayName(targetJob);
                if (displayName == null) {
                    player.sendMessage(ChatColor.RED + "存在しない職業です: " + targetJob);
                    return true;
                }
                player.sendMessage(ChatColor.RED + "あなたは「" + displayName + "」に就いていないため、");
                player.sendMessage(ChatColor.RED + "そのガイドブックを取得できません。");
                return true;
            }
        } else {
            // 最初の職業のガイドブックを取得（複数職業の場合）
            Job firstJob = jobManager.getJobById(playerJobs.get(0).getJobId());
            targetJob = (firstJob != null) ? firstJob.getName() : null;
            
            if (targetJob == null) {
                player.sendMessage(ChatColor.RED + "職業情報の取得に失敗しました。");
                return true;
            }
            
            if (playerJobs.size() > 1) {
                player.sendMessage(ChatColor.YELLOW + "複数の職業に就いています。特定の職業のガイドブックが欲しい場合は:");
                player.sendMessage(ChatColor.YELLOW + "/jobs guide <職業名> を使用してください。");
                player.sendMessage("");
            }
        }
        
        // ガイドブックを配布
        String displayName = jobManager.getJobDisplayName(targetJob);
        if (guideBookManager.giveGuideBookForced(player, targetJob)) {
            // メッセージはgiveGuideBookForcedで送信済み
        }
        
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Jobs コマンドヘルプ ===");
        player.sendMessage(ChatColor.YELLOW + "/jobs list " + ChatColor.WHITE + "- 利用可能な職業一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs join <職業名> " + ChatColor.WHITE + "- 指定した職業に就く");
        player.sendMessage(ChatColor.YELLOW + "/jobs leave <職業名> " + ChatColor.WHITE + "- 指定した職業を辞める");
        player.sendMessage(ChatColor.YELLOW + "/jobs stats [職業名] " + ChatColor.WHITE + "- 職業の統計を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs info <職業名> " + ChatColor.WHITE + "- 職業の詳細情報を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs guide [職業名] " + ChatColor.WHITE + "- 職業ガイドブックを取得");
        player.sendMessage(ChatColor.YELLOW + "/jobs debug " + ChatColor.WHITE + "- 職業の詳細デバッグ情報を表示");
        
        // 管理者向けヘルプ
        if (player.hasPermission("tofunomics.jobs.admin")) {
            player.sendMessage(ChatColor.GOLD + "=== 管理者コマンド ===");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin forceleave <player> <job>" + ChatColor.WHITE + " - 強制辞職");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin forcejoin <player> <job>" + ChatColor.WHITE + " - 強制就職");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin setlevel <player> <job|all> <level>" + ChatColor.WHITE + " - レベル設定");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin addexp <player> <job|all> <amount>" + ChatColor.WHITE + " - 経験値追加");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin setexp <player> <job|all> <amount>" + ChatColor.WHITE + " - 経験値設定");
            player.sendMessage(ChatColor.YELLOW + "/jobs admin reset <player> <job|all>" + ChatColor.WHITE + " - 職業リセット");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // サブコマンド補完
            List<String> subCommands = new ArrayList<>();
            subCommands.add("list");
            subCommands.add("join");
            subCommands.add("leave");
            subCommands.add("stats");
            subCommands.add("info");
            subCommands.add("guide");
            subCommands.add("debug");
            if (sender.hasPermission("tofunomics.jobs.admin")) {
                subCommands.add("admin");
            }
            String input = args[0].toLowerCase();
            completions = subCommands.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("join") || subCommand.equals("leave") || 
                subCommand.equals("stats") || subCommand.equals("info") ||
                subCommand.equals("guide")) {
                // 職業名補完
                String input = args[1].toLowerCase();
                completions = Arrays.stream(jobManager.getJobNames())
                        .filter(s -> s.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("admin") && sender.hasPermission("tofunomics.jobs.admin")) {
                // 管理者サブコマンド補完
                List<String> adminSubCommands = new ArrayList<>();
                adminSubCommands.add("forceleave");
                adminSubCommands.add("forcejoin");
                adminSubCommands.add("setlevel");
                adminSubCommands.add("addexp");
                adminSubCommands.add("setexp");
                adminSubCommands.add("reset");
                String input = args[1].toLowerCase();
                completions = adminSubCommands.stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && 
                   sender.hasPermission("tofunomics.jobs.admin")) {
            // プレイヤー名補完
            String input = args[2].toLowerCase();
            completions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && 
                   sender.hasPermission("tofunomics.jobs.admin")) {
            String adminSubCommand = args[1].toLowerCase();
            String input = args[3].toLowerCase();
            
            // 職業名 + all 補完
            if (adminSubCommand.equals("setlevel") || adminSubCommand.equals("addexp") || 
                adminSubCommand.equals("setexp") || adminSubCommand.equals("reset")) {
                List<String> options = new ArrayList<>(Arrays.asList(jobManager.getJobNames()));
                options.add("all");
                completions = options.stream()
                        .filter(s -> s.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            } else if (adminSubCommand.equals("forceleave") || adminSubCommand.equals("forcejoin")) {
                // 職業名のみ
                completions = Arrays.stream(jobManager.getJobNames())
                        .filter(s -> s.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }
        
        return completions;
    }
}