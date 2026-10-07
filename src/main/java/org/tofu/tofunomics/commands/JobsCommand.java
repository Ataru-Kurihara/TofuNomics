package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.jobs.JobGuideBookService;
import org.tofu.tofunomics.jobs.gui.JobsHubGUI;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.stats.JobStatsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JobsCommand implements CommandExecutor, TabCompleter {

    /** 誰でも使えるサブコマンド（Tab 補完に出す順） */
    private static final List<String> PLAYER_SUB_COMMANDS =
            Arrays.asList("list", "join", "leave", "stats", "info", "guide", "help");
    /** 管理者だけに見せるサブコマンド */
    private static final List<String> ADMIN_SUB_COMMANDS = Arrays.asList("admin", "debug");
    private static final String ADMIN_PERMISSION = "tofunomics.jobs.admin";

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final ExperienceManager experienceManager;
    private final JobsHubGUI jobsHubGUI;
    private final JobStatsManager jobStatsManager;
    private final JobGuideBookService guideBookService;

    public JobsCommand(ConfigManager configManager, JobManager jobManager,
                       ExperienceManager experienceManager, JobsHubGUI jobsHubGUI,
                       JobStatsManager jobStatsManager, JobGuideBookService guideBookService) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.experienceManager = experienceManager;
        this.jobsHubGUI = jobsHubGUI;
        this.jobStatsManager = jobStatsManager;
        this.guideBookService = guideBookService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        Player player = (Player) sender;

        // 経済ワールドの外（ロビーなど）では使えない。管理用の /jobs admin だけは場所を問わない
        boolean adminSubCommand = args.length > 0 && args[0].equalsIgnoreCase("admin");
        if (!adminSubCommand && EconomyWorldGuard.blockIfOutside(player, configManager)) {
            return true;
        }

        if (args.length == 0) {
            // 引数なしは GUI を開く。GUI 初期化失敗時は従来のヘルプにフォールバック。
            if (jobsHubGUI != null) {
                jobsHubGUI.open(player);
            } else {
                sendHelpMessage(player);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelpMessage(player);
                return true;
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
            case "guide":
                return handleJobGuide(player, args);
            case "debug":
                return handleJobDebug(player);
            case "admin":
                return handleJobAdmin(sender, args);
            default:
                sendHelpMessage(player);
                return true;
        }
    }

    private boolean handleJobsList(Player player) {
        boolean clickable = configManager.isClickableMessagesEnabled();
        player.sendMessage(ChatColor.GOLD + "=== 利用可能な職業 ===");

        for (String jobName : jobManager.getJobNames()) {
            String displayName = jobManager.getJobDisplayName(jobName);
            String description = configManager.getJobDescription(jobName);
            int maxLevel = configManager.getJobMaxLevel(jobName);
            double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
            boolean employed = jobManager.hasJob(player, jobName);
            // 上級職業の判定（GUIのJobsHubGUIと同じロジック）
            boolean advanced = configManager.isAdvancedJob(jobName);
            // 上級職業が未解禁か（未就職かつLv50到達経験なし）
            boolean advancedLocked = advanced && !employed && !jobManager.hasReachedLevel50(player);

            if (clickable) {
                // ホバーで詳細（説明・最大レベル・収入倍率）を表示するツールチップを構築
                StringBuilder hover = new StringBuilder();
                if (advanced) {
                    hover.append("&6&l【上級職業】\n");
                }
                hover.append("&e").append(displayName).append(" &7(").append(jobName).append(")\n");
                if (description != null && !description.isEmpty()) {
                    hover.append("&f").append(description).append("\n");
                }
                hover.append("&b最大レベル: ").append(maxLevel)
                     .append(" &7| &b収入倍率: ").append(String.format("%.1f", incomeMultiplier)).append("x");
                if (advanced) {
                    hover.append("\n&7※いずれかの職業でLv50到達後に就職できます");
                }

                String label = advanced
                    ? "&6▶ ★[上級] " + displayName + " &7(" + jobName + ")  "
                    : "&e▶ " + displayName + " &7(" + jobName + ")  ";

                org.tofu.tofunomics.util.RichMessageBuilder builder =
                    org.tofu.tofunomics.util.RichMessageBuilder.create()
                        .hoverText(label, hover.toString());

                if (employed) {
                    PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
                    int currentLevel = playerJob != null ? playerJob.getLevel() : 0;
                    builder.text("&a[現在就職中 Lv." + currentLevel + "]");
                } else if (advancedLocked) {
                    // 未解禁の上級職業はクリック不可のテキスト表示
                    builder.text("&c[未解禁] &7Lv50到達で解禁");
                } else {
                    // クリックで就職できるボタン
                    builder.runButton("&a&l[就職する]", "/jobs join " + jobName,
                        "&aクリックで " + displayName + " に就職します");
                }
                builder.sendTo(player);
            } else {
                if (advanced) {
                    player.sendMessage(ChatColor.GOLD + "▶ ★[上級] " + displayName + ChatColor.GRAY + " (" + jobName + ")");
                    player.sendMessage(ChatColor.GOLD + "  【上級職業】");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "▶ " + displayName + ChatColor.GRAY + " (" + jobName + ")");
                }
                if (description != null && !description.isEmpty()) {
                    player.sendMessage(ChatColor.WHITE + "  " + description);
                }
                player.sendMessage(ChatColor.AQUA + "  最大レベル: " + maxLevel +
                                 " | 収入倍率: " + String.format("%.1f", incomeMultiplier) + "x");

                if (employed) {
                    PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
                    if (playerJob != null) {
                        int currentLevel = playerJob.getLevel();
                        String expRatio = experienceManager.getExperienceRatioText(playerJob, jobName);

                        player.sendMessage(ChatColor.GREEN + "  ★ 現在就職中 - レベル " + currentLevel +
                                         " (経験値: " + expRatio + ")");
                    }
                } else if (advancedLocked) {
                    player.sendMessage(ChatColor.RED + "  ✖ 未解禁 - いずれかの職業でLv50到達後に就職できます");
                }
                player.sendMessage("");
            }
        }

        player.sendMessage(ChatColor.GOLD + "職業に就くには: " + ChatColor.WHITE + "/jobs"
                + ChatColor.GRAY + "（画面から選ぶ）または " + ChatColor.WHITE + "/jobs join <職業名>");
        player.sendMessage(ChatColor.RED + "※職業は Lv50 になるまで選び直せません。");
        return true;
    }

    /** 内部名 → 表示名（登録順） */
    private Map<String, String> jobDisplayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Job job : jobManager.getAllJobs()) {
            names.put(job.getName(), job.getDisplayName());
        }
        return names;
    }

    /**
     * プレイヤーが打った職業名（内部名 miner でも表示名 鉱夫 でもよい）を内部名に直す。
     * 該当が無ければ、打たれた文字を小文字にして返す（後段の「存在しない職業」判定に任せる）。
     */
    private String resolveJobName(String input) {
        String resolved = JobNameResolver.resolve(input, jobDisplayNames());
        return resolved != null ? resolved : input.toLowerCase(Locale.ROOT);
    }

    private void sendUnknownJob(Player player, String typed) {
        player.sendMessage(ChatColor.RED + "存在しない職業です: " + typed);
        List<String> displayNames = new ArrayList<>();
        for (String displayName : jobDisplayNames().values()) {
            displayNames.add(JobNameResolver.plain(displayName));
        }
        player.sendMessage(ChatColor.YELLOW + "利用可能な職業: " + String.join(", ", displayNames));
        player.sendMessage(ChatColor.YELLOW + "/jobs" + ChatColor.GRAY + " と入力すると、画面から選べます。");
    }

    private boolean handleJobJoin(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs join <職業名>");
            return true;
        }
        
        String jobName = resolveJobName(args[1]);
        
        if (!jobManager.isValidJobName(jobName)) {
            sendUnknownJob(player, args[1]);
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
                
            case LEVEL_TOO_LOW:
                player.sendMessage(ChatColor.RED + "転職するには現在の職業レベルが50以上必要です。");
                break;
                
            case MAX_JOBS_REACHED:
                int maxJobs = configManager.getMaxJobsPerPlayer();
                player.sendMessage(ChatColor.RED + "同時に就ける職業数の上限(" + maxJobs + ")に達しています。");
                break;

            case ADVANCED_JOB_LOCKED:
                player.sendMessage(ChatColor.RED + "「" + jobManager.getJobDisplayName(jobName)
                    + "」は上級職業です。いずれかの職業でレベル50に到達すると就職できます。");
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
        
        String jobName = resolveJobName(args[1]);
        
        if (!jobManager.hasJob(player, jobName)) {
            player.sendMessage(ChatColor.RED + "その職業には就いていません: " + args[1]);
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

    /**
     * /jobs stats は /jobstats のエイリアス。表示を一本化するため JobStatsManager に委譲する。
     * 使用法は /jobstats と同一:
     *   /jobs stats              … 全職業の統計
     *   /jobs stats &lt;職業名&gt;     … 指定職業の詳細統計
     *   /jobs stats top &lt;職業名&gt; … 指定職業のランキング
     * （args[0] は "stats"。以降を /jobstats と同じ引数として扱う）
     */
    private boolean handleJobStats(Player player, String[] args) {
        if (args.length == 1) {
            jobStatsManager.showAllJobStats(player);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("top")) {
            jobStatsManager.showJobTopRanking(player, resolveJobName(args[2]), 10);
        } else if (args.length == 2 && !args[1].equalsIgnoreCase("top")) {
            jobStatsManager.showJobStats(player, resolveJobName(args[1]));
        } else {
            player.sendMessage(ChatColor.RED + "使用法: /jobs stats [職業名|top <職業名>]");
        }
        return true;
    }

    private boolean handleJobInfo(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /jobs info <職業名>");
            return true;
        }
        
        String jobName = resolveJobName(args[1]);
        
        if (!jobManager.isValidJobName(jobName)) {
            sendUnknownJob(player, args[1]);
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

    /**
     * /jobs guide [職業名] - 職業ガイドブックを入手する。
     * GUI（職業詳細 スロット22）と同じく「その職業に就職中」のみ入手可能。
     *   /jobs guide          … 就職中の全職業のガイドをまとめて入手
     *   /jobs guide <職業名> … 指定職業（就職中のもの）のガイドを入手
     */
    private boolean handleJobGuide(Player player, String[] args) {
        // 引数なし: 就職中の職業のガイドをまとめて配布
        if (args.length == 1) {
            int given = 0;
            for (PlayerJob playerJob : jobManager.getPlayerJobs(player)) {
                Job job = jobManager.getJobById(playerJob.getJobId());
                if (job == null) {
                    continue;
                }
                String jobName = job.getName();
                if (configManager.isJobGuideBookEnabled(jobName)
                        && !configManager.getJobGuideBookPages(jobName).isEmpty()) {
                    if (guideBookService.giveGuideBook(player, jobName)) {
                        given++;
                    }
                }
            }
            if (given == 0) {
                player.sendMessage(ChatColor.YELLOW + "ガイドブックを入手するには、まず職業に就職してください。");
                player.sendMessage(ChatColor.YELLOW + "使用法: " + ChatColor.WHITE + "/jobs guide <職業名>");
            }
            return true;
        }

        // /jobs guide <職業名>
        if (args.length == 2) {
            String jobName = resolveJobName(args[1]);

            if (!jobManager.isValidJobName(jobName)) {
                sendUnknownJob(player, args[1]);
                return true;
            }

            if (!jobManager.hasJob(player, jobName)) {
                player.sendMessage(ChatColor.RED + "「" + jobManager.getJobDisplayName(jobName)
                        + "」に就職していません。ガイドブックは就職後に入手できます。");
                player.sendMessage(ChatColor.YELLOW + "就職するには: " + ChatColor.WHITE + "/jobs"
                        + ChatColor.GRAY + "（画面から選ぶ）");
                return true;
            }

            guideBookService.giveGuideBook(player, jobName);
            return true;
        }

        player.sendMessage(ChatColor.RED + "使用法: /jobs guide [職業名]");
        return true;
    }

    private boolean handleJobDebug(Player player) {
        // 内部のIDなどを出すので管理者限定
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
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
            sender.sendMessage(ChatColor.YELLOW + "  forceleave <player> <jobName> - プレイヤーを強制的に辞職させる");
            sender.sendMessage(ChatColor.YELLOW + "  reset - 自分の全職業データを完全リセット（テスト用）");
            return true;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "forceleave":
                return handleJobAdminForceLeave(sender, args);
            case "reset":
                return handleJobAdminReset(sender);
            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
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
        String jobName = resolveJobName(args[3]);
        
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
     * 管理者による自分の全職業リセットコマンド（テストプレイ用）。
     * 実行者自身の player_jobs / job_history / job_changes をすべて削除し初期状態に戻す。
     */
    private boolean handleJobAdminReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;

        if (jobManager.resetAllJobs(player)) {
            player.sendMessage(ChatColor.GREEN + "全職業データをリセットしました（レベル・経験値・履歴をすべて初期化）。");
        } else {
            player.sendMessage(ChatColor.RED + "職業データのリセットに失敗しました。サーバーログを確認してください。");
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Jobs コマンドヘルプ ===");
        player.sendMessage(ChatColor.YELLOW + "/jobs " + ChatColor.WHITE + "- 職業メニューを開く（ここから職業を選べます）");
        player.sendMessage(ChatColor.YELLOW + "/jobs list " + ChatColor.WHITE + "- 利用可能な職業一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs join <職業名> " + ChatColor.WHITE + "- 指定した職業に就く");
        player.sendMessage(ChatColor.YELLOW + "/jobs leave <職業名> " + ChatColor.WHITE + "- 指定した職業を辞める（Lv50 以上）");
        player.sendMessage(ChatColor.YELLOW + "/jobs stats [職業名|top <職業名>] " + ChatColor.WHITE + "- 職業の統計を表示（/jobstats と同じ）");
        player.sendMessage(ChatColor.YELLOW + "/jobs info <職業名> " + ChatColor.WHITE + "- 職業の詳細情報を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobs guide [職業名] " + ChatColor.WHITE + "- 職業のガイドブックを入手（就職中のみ）");
        player.sendMessage(ChatColor.RED + "※職業は Lv50 になるまで選び直せません。");
        if (player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(ChatColor.GRAY + "/jobs debug - 職業のデバッグ情報（管理者）");
            player.sendMessage(ChatColor.GRAY + "/jobs admin <forceleave|reset> - 管理用（管理者）");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        boolean admin = player.hasPermission(ADMIN_PERMISSION);

        if (args.length == 1) {
            return filterByPrefix(subCommandsFor(admin), args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (subCommand) {
                case "join":
                    return JobNameResolver.suggest(args[1], joinableJobDisplayNames(player));
                case "info":
                    return JobNameResolver.suggest(args[1], jobDisplayNames());
                case "leave":
                case "guide":
                    return JobNameResolver.suggest(args[1], currentJobDisplayNames(player));
                case "stats":
                    List<String> candidates = filterByPrefix(Collections.singletonList("top"), args[1]);
                    candidates.addAll(JobNameResolver.suggest(args[1], jobDisplayNames()));
                    return candidates;
                case "admin":
                    return admin ? filterByPrefix(Arrays.asList("forceleave", "reset"), args[1])
                            : Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3 && subCommand.equals("stats") && args[1].equalsIgnoreCase("top")) {
            return JobNameResolver.suggest(args[2], jobDisplayNames());
        }
        if (admin && subCommand.equals("admin") && args[1].equalsIgnoreCase("forceleave")) {
            if (args.length == 3) {
                return null; // プレイヤー名（Bukkit 既定の補完）
            }
            if (args.length == 4) {
                return JobNameResolver.suggest(args[3], jobDisplayNames());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Tab 補完に出すサブコマンド。管理用のものは権限がある人にだけ出す。
     */
    static List<String> subCommandsFor(boolean admin) {
        List<String> subCommands = new ArrayList<>(PLAYER_SUB_COMMANDS);
        if (admin) {
            subCommands.addAll(ADMIN_SUB_COMMANDS);
        }
        return subCommands;
    }

    private static List<String> filterByPrefix(List<String> candidates, String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matched.add(candidate);
            }
        }
        return matched;
    }

    /** 今そのプレイヤーが就職できる職業（Lv50 未到達なら上級職を除く） */
    private Map<String, String> joinableJobDisplayNames(Player player) {
        Map<String, String> names = jobDisplayNames();
        if (!jobManager.hasReachedLevel50(player)) {
            names.keySet().removeIf(configManager::isAdvancedJob);
        }
        return names;
    }

    /** 今そのプレイヤーが就いている職業 */
    private Map<String, String> currentJobDisplayNames(Player player) {
        Map<String, String> names = new LinkedHashMap<>();
        for (PlayerJob playerJob : jobManager.getPlayerJobs(player)) {
            Job job = jobManager.getJobById(playerJob.getJobId());
            if (job != null) {
                names.put(job.getName(), job.getDisplayName());
            }
        }
        return names;
    }
}
