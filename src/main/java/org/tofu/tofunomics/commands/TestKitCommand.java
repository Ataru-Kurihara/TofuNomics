package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.economy.ItemManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.rewards.JobLevelRewardManager;
import org.tofu.tofunomics.util.NPCPurchaseMarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理者テスト支援コマンド `/tntest`。
 *
 * リリース前テスト（GitHub issues #105〜#117）の「前提条件」を一発で整えるための
 * 管理者専用コマンド。職業レベルの即時設定・残高セットアップ・テストアイテム配布・
 * 時刻/在庫リセットを提供し、実機テストの所要時間を短縮する。
 *
 * 全サブコマンドはOP権限（tofunomics.admin）を必須とする。残高・職業を改変する
 * 強力なコマンドのため、破壊的操作はサーバーログに記録する。
 */
public class TestKitCommand implements CommandExecutor, TabCompleter {

    private final TofuNomics plugin;
    private final JobManager jobManager;
    private final ExperienceManager experienceManager;
    private final CurrencyConverter currencyConverter;
    private final JobLevelRewardManager jobLevelRewardManager;
    private final ConfigManager configManager;
    private final ItemManager itemManager;

    /** 職業名一覧（プリセット配布・補完・検証の基準）。先頭5つが通常職、後ろ3つが上級職。 */
    private static final List<String> JOBS = Arrays.asList(
        "miner", "woodcutter", "farmer", "fisherman", "blacksmith",
        "alchemist", "enchanter", "architect");

    public TestKitCommand(TofuNomics plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.experienceManager = plugin.getExperienceManager();
        this.currencyConverter = plugin.getCurrencyConverter();
        this.jobLevelRewardManager = plugin.getJobLevelRewardManager();
        this.configManager = plugin.getConfigManager();
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }
        Player player = (Player) sender;

        // テスト用の強力なコマンドのためOP必須（testmode中は権限が剥奪されるため弾かれる点に注意）
        if (!player.isOp()) {
            player.sendMessage("§cこのコマンドはOP権限が必要です");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "job":
                handleJob(player, args);
                break;
            case "setlevel":
                handleSetLevel(player, args);
                break;
            case "lv50":
                handleLv50(player, args);
                break;
            case "reward":
                handleReward(player, args);
                break;
            case "money":
                handleMoney(player, args);
                break;
            case "reset":
                handleReset(player, args);
                break;
            case "kit":
                handleKit(player, args);
                break;
            case "time":
                handleTime(player, args);
                break;
            case "stockreset":
                handleStockReset(player);
                break;
            default:
                sendUsage(player);
                break;
        }
        return true;
    }

    // ===== job: 指定職業に就かせて任意レベルに即設定 =====
    private void handleJob(Player sender, String[] args) {
        // /tntest job <jobName> <level> [player]
        if (args.length < 3) {
            sender.sendMessage("§c使用法: /tntest job <職業> <レベル> [プレイヤー]");
            return;
        }
        String jobName = args[1].toLowerCase();
        if (jobManager.getJobByName(jobName) == null) {
            sender.sendMessage("§c職業 '" + jobName + "' は存在しません。指定可能: " + String.join(", ", JOBS));
            return;
        }
        Integer level = parseLevel(sender, args[2]);
        if (level == null) {
            return;
        }
        Player target = resolveTarget(sender, args.length >= 4 ? args[3] : null);
        if (target == null) {
            return;
        }

        // 就職制限（上級職ロック・転職Lv50制限等）を無視して強制就職 → レベル設定
        PlayerJob playerJob = jobManager.adminForceJoinJob(target, jobName);
        if (playerJob == null) {
            sender.sendMessage("§c職業の設定に失敗しました（DBエラー）");
            return;
        }
        if (!experienceManager.setLevel(playerJob, jobName, level)) {
            sender.sendMessage("§cレベル設定に失敗しました");
            return;
        }

        int maxLevel = configManager.getJobMaxLevel(jobName);
        int appliedLevel = Math.min(level, maxLevel);
        String advanced = configManager.isAdvancedJob(jobName) ? " §d[上級職]" : "";
        sender.sendMessage("§a" + target.getName() + " を §e" + jobName + "§a Lv" + appliedLevel + " に設定しました" + advanced);
        if (appliedLevel != level) {
            sender.sendMessage("§7（最大レベル " + maxLevel + " にクランプされました）");
        }
        if (!sender.equals(target)) {
            target.sendMessage("§7管理者により職業が " + jobName + " Lv" + appliedLevel + " に設定されました");
        }
        logAction(sender, target, "job " + jobName + " Lv" + appliedLevel);
    }

    // ===== setlevel: 現在就いている職業のレベルだけを変更（リセットなし） =====
    private void handleSetLevel(Player sender, String[] args) {
        // /tntest setlevel <level> [player]
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /tntest setlevel <レベル> [プレイヤー]");
            sender.sendMessage("§7現職を保持したままレベルのみ変更します（職業データ・履歴・畑区画は維持）");
            return;
        }
        Integer level = parseLevel(sender, args[1]);
        if (level == null) {
            return;
        }
        Player target = resolveTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) {
            return;
        }

        PlayerJob playerJob = jobManager.getCurrentJob(target.getUniqueId());
        if (playerJob == null) {
            sender.sendMessage("§c" + target.getName() + " は職業に就いていません（先に /tntest job で就職させてください）");
            return;
        }
        org.tofu.tofunomics.models.Job job = jobManager.getJobById(playerJob.getJobId());
        if (job == null) {
            sender.sendMessage("§c現在の職業情報を取得できませんでした");
            return;
        }
        String jobName = job.getName();
        if (!experienceManager.setLevel(playerJob, jobName, level)) {
            sender.sendMessage("§cレベル設定に失敗しました");
            return;
        }

        int maxLevel = configManager.getJobMaxLevel(jobName);
        int appliedLevel = Math.min(level, maxLevel);
        sender.sendMessage("§a" + target.getName() + " の §e" + jobName + "§a を Lv" + appliedLevel + " に変更しました");
        if (appliedLevel != level) {
            sender.sendMessage("§7（最大レベル " + maxLevel + " にクランプされました）");
        }
        if (!sender.equals(target)) {
            target.sendMessage("§7管理者により " + jobName + " が Lv" + appliedLevel + " に変更されました");
        }
        logAction(sender, target, "setlevel " + jobName + " Lv" + appliedLevel);
    }

    // ===== lv50: 上級職解禁の前提「いずれかの職でLv50到達」を即達成 =====
    private void handleLv50(Player sender, String[] args) {
        // /tntest lv50 [player]
        Player target = resolveTarget(sender, args.length >= 2 ? args[1] : null);
        if (target == null) {
            return;
        }
        // 任意の基本職(miner)にLv50で就かせる。これで hasReachedLevel50 が true になり上級職が解禁される
        String baseJob = "miner";
        PlayerJob playerJob = jobManager.adminForceJoinJob(target, baseJob);
        if (playerJob == null || !experienceManager.setLevel(playerJob, baseJob, 50)) {
            sender.sendMessage("§cLv50到達状態の設定に失敗しました");
            return;
        }
        sender.sendMessage("§a" + target.getName() + " を " + baseJob + " Lv50 に設定しました（上級職が解禁されます）");
        sender.sendMessage("§7このまま /jobs join <上級職> で就職できます");
        if (!sender.equals(target)) {
            target.sendMessage("§7管理者により " + baseJob + " Lv50（上級職解禁状態）に設定されました");
        }
        logAction(sender, target, "lv50 (" + baseJob + ")");
    }

    // ===== reward: レベルアップ報酬を手動発火 =====
    private void handleReward(Player sender, String[] args) {
        // /tntest reward <jobName> <level> [player]
        if (args.length < 3) {
            sender.sendMessage("§c使用法: /tntest reward <職業> <レベル> [プレイヤー]");
            return;
        }
        String jobName = args[1].toLowerCase();
        if (jobManager.getJobByName(jobName) == null) {
            sender.sendMessage("§c職業 '" + jobName + "' は存在しません");
            return;
        }
        Integer level = parseLevel(sender, args[2]);
        if (level == null) {
            return;
        }
        Player target = resolveTarget(sender, args.length >= 4 ? args[3] : null);
        if (target == null) {
            return;
        }
        double amount = jobLevelRewardManager.getRewardAmount(jobName, level);
        if (amount <= 0) {
            sender.sendMessage("§eLv" + level + " は報酬対象外です（報酬は5レベルおき・Lv5〜100）");
            return;
        }
        boolean given = jobLevelRewardManager.giveJobLevelReward(target, jobName, level);
        if (given) {
            sender.sendMessage("§a" + target.getName() + " に " + jobName + " Lv" + level
                + " の報酬 " + (int) amount + configManager.getCurrencySymbol() + " を付与しました");
        } else {
            sender.sendMessage("§c報酬の付与に失敗しました（残高上限到達など）");
        }
        logAction(sender, target, "reward " + jobName + " Lv" + level + " (" + (int) amount + ")");
    }

    // ===== money: 現金（金塊）または預金（銀行）をセット =====
    private void handleMoney(Player sender, String[] args) {
        // /tntest money <cash|bank> <amount> [player]
        if (args.length < 3) {
            sender.sendMessage("§c使用法: /tntest money <cash|bank> <金額> [プレイヤー]");
            sender.sendMessage("§7cash=現金（金塊を付与）/ bank=預金（銀行残高を指定額に設定）");
            return;
        }
        String type = args[1].toLowerCase();
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください");
            return;
        }
        if (amount < 0) {
            sender.sendMessage("§c金額は0以上で指定してください");
            return;
        }
        Player target = resolveTarget(sender, args.length >= 4 ? args[3] : null);
        if (target == null) {
            return;
        }
        String symbol = configManager.getCurrencySymbol();

        if (type.equals("cash")) {
            // 現金=インベントリの金塊として付与（スペースチェックはスキップ）
            if (currencyConverter.receiveCash(target, amount, true)) {
                sender.sendMessage("§a" + target.getName() + " に現金 " + (int) amount + symbol + " を付与しました");
            } else {
                sender.sendMessage("§c現金の付与に失敗しました");
            }
        } else if (type.equals("bank")) {
            // 預金=銀行残高を指定額に「設定」
            if (setBankBalance(target, amount)) {
                sender.sendMessage("§a" + target.getName() + " の預金を " + (int) amount + symbol + " に設定しました");
            } else {
                sender.sendMessage("§c預金の設定に失敗しました");
            }
        } else {
            sender.sendMessage("§c種別は cash または bank を指定してください");
            return;
        }
        logAction(sender, target, "money " + type + " " + (int) amount);
    }

    // ===== reset: 新規プレイヤー相当に初期化 =====
    private void handleReset(Player sender, String[] args) {
        // /tntest reset [player]
        Player target = resolveTarget(sender, args.length >= 2 ? args[1] : null);
        if (target == null) {
            return;
        }
        // 職業データを全削除
        jobManager.resetAllJobs(target);
        // 預金を初期残高に、レガシー現金フィールドを0に
        double starting = configManager.getStartingBalance();
        org.tofu.tofunomics.models.Player tofuPlayer =
            plugin.getPlayerDAO().getPlayerByUUID(target.getUniqueId().toString());
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player(target.getUniqueId(), 0.0, starting);
            plugin.getPlayerDAO().insertPlayer(tofuPlayer);
        } else {
            tofuPlayer.setBalance(0.0);
            tofuPlayer.setBankBalance(starting);
            plugin.getPlayerDAO().updatePlayerData(tofuPlayer);
        }
        // インベントリの金塊（現金）をクリア
        int nuggets = itemManager.countGoldNuggetsInInventory(target);
        if (nuggets > 0) {
            itemManager.removeGoldNuggetsFromInventory(target, nuggets);
        }

        String symbol = configManager.getCurrencySymbol();
        sender.sendMessage("§a" + target.getName() + " を新規プレイヤー相当に初期化しました");
        sender.sendMessage("§7職業: なし / 預金: " + (int) starting + symbol + " / 現金(金塊): 0");
        if (!sender.equals(target)) {
            target.sendMessage("§7管理者により新規プレイヤー状態に初期化されました");
        }
        logAction(sender, target, "reset");
    }

    // ===== kit: テストアイテムのプリセット配布 =====
    private void handleKit(Player sender, String[] args) {
        // /tntest kit <jobName|marker> [player]
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /tntest kit <職業|marker> [プレイヤー]");
            sender.sendMessage("§7職業=売却テスト用アイテム / marker=転売防止マーカー付きアイテム");
            return;
        }
        String preset = args[1].toLowerCase();
        Player target = resolveTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) {
            return;
        }

        List<ItemStack> items = buildKit(preset);
        if (items == null) {
            sender.sendMessage("§c不明なキット '" + preset + "'。指定可能: " + String.join(", ", JOBS) + ", marker");
            return;
        }
        for (ItemStack item : items) {
            target.getInventory().addItem(item);
        }
        sender.sendMessage("§a" + target.getName() + " に '" + preset + "' のテストアイテムを配布しました");
        sender.sendMessage("§7※ 正確な売却対象・価格はサーバーの取引所(trading_posts)設定に従います");
        logAction(sender, target, "kit " + preset);
    }

    /**
     * プリセット名に対応するテストアイテム一覧を生成する。不明な場合はnull。
     */
    private List<ItemStack> buildKit(String preset) {
        List<ItemStack> items = new ArrayList<>();
        switch (preset) {
            case "miner":
                addAll(items, 64, Material.COAL, Material.IRON_ORE, Material.GOLD_ORE,
                    Material.DIAMOND, Material.EMERALD, Material.REDSTONE);
                break;
            case "woodcutter":
                // 加工テスト用に原木を多めに
                addAll(items, 64, Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
                    Material.DARK_OAK_LOG, Material.OAK_SAPLING);
                break;
            case "farmer":
                addAll(items, 64, Material.WHEAT, Material.CARROT, Material.POTATO,
                    Material.BEETROOT, Material.PUMPKIN, Material.MELON_SLICE);
                break;
            case "fisherman":
                addAll(items, 64, Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
                break;
            case "blacksmith":
                addAll(items, 1, Material.IRON_SWORD, Material.DIAMOND_PICKAXE, Material.IRON_AXE,
                    Material.DIAMOND_SWORD, Material.IRON_CHESTPLATE);
                break;
            case "alchemist":
                addAll(items, 1, Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);
                break;
            case "enchanter":
                addAll(items, 1, Material.ENCHANTED_BOOK);
                addAll(items, 1, Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE);
                break;
            case "architect":
                addAll(items, 64, Material.STONE_BRICKS, Material.QUARTZ_BLOCK, Material.SMOOTH_STONE,
                    Material.POLISHED_ANDESITE, Material.BRICKS);
                break;
            case "marker":
                // 転売防止マーカー付きアイテム（取引所で売却拒否されることの検証用）
                items.add(NPCPurchaseMarker.mark(plugin, new ItemStack(Material.BREAD, 16)));
                items.add(NPCPurchaseMarker.mark(plugin, new ItemStack(Material.COOKED_BEEF, 16)));
                // 比較用にマーカー無しの同種アイテムも配布
                items.add(new ItemStack(Material.BREAD, 16));
                break;
            default:
                return null;
        }
        return items;
    }

    private void addAll(List<ItemStack> items, int amount, Material... materials) {
        for (Material material : materials) {
            items.add(new ItemStack(material, amount));
        }
    }

    // ===== time: 営業時間内/外/任意時刻にワールド時刻を設定 =====
    private void handleTime(Player sender, String[] args) {
        // /tntest time <open|close|hour> [hour]
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /tntest time <open|close|hour> [時(0-23)]");
            sender.sendMessage("§7open=営業時間内(朝) / close=営業時間外(夜) / hour=指定時刻");
            return;
        }
        World world = sender.getWorld();
        String mode = args[1].toLowerCase();
        long ticks;
        String desc;
        switch (mode) {
            case "open":
                ticks = 1000L; // 約7時（営業時間6:00-22:00内）
                desc = "営業時間内（朝7時相当）";
                break;
            case "close":
                ticks = 16000L; // 約22時以降（営業時間外）
                desc = "営業時間外（夜22時相当）";
                break;
            case "hour":
                if (args.length < 3) {
                    sender.sendMessage("§c使用法: /tntest time hour <時(0-23)>");
                    return;
                }
                Integer hour = parseInt(sender, args[2]);
                if (hour == null) {
                    return;
                }
                if (hour < 0 || hour > 23) {
                    sender.sendMessage("§c時は 0〜23 で指定してください");
                    return;
                }
                // ワールド時刻 tick = ((hour - 6 + 24) % 24) * 1000（Minecraftは6:00=0tick基準）
                ticks = ((hour - 6 + 24) % 24) * 1000L;
                desc = hour + "時相当";
                break;
            default:
                sender.sendMessage("§c指定可能: open / close / hour <時>");
                return;
        }
        world.setTime(ticks);
        sender.sendMessage("§aワールド '" + world.getName() + "' の時刻を " + desc + " に設定しました");
        logAction(sender, sender, "time " + mode + " (tick=" + ticks + ")");
    }

    // ===== stockreset: 食料NPCの日次在庫・購入制限を強制リセット =====
    private void handleStockReset(Player sender) {
        if (plugin.getFoodNPCManager() == null) {
            sender.sendMessage("§c食料NPCシステムが無効です");
            return;
        }
        plugin.getFoodNPCManager().forceDailyReset();
        sender.sendMessage("§a食料NPCの在庫・購入制限を手動リセットしました");
        logAction(sender, sender, "stockreset");
    }

    // ===== 共通ユーティリティ =====

    /** 預金（銀行残高）を指定額に設定する。 */
    private boolean setBankBalance(Player target, double amount) {
        org.tofu.tofunomics.models.Player tofuPlayer =
            plugin.getPlayerDAO().getPlayerByUUID(target.getUniqueId().toString());
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player(target.getUniqueId(), 0.0, amount);
            return plugin.getPlayerDAO().insertPlayer(tofuPlayer);
        }
        tofuPlayer.setBankBalance(amount);
        return plugin.getPlayerDAO().updatePlayerData(tofuPlayer);
    }

    /** 対象プレイヤーを解決する。名前指定があればオンラインプレイヤーを探し、無ければ実行者自身。 */
    private Player resolveTarget(Player sender, String name) {
        if (name == null) {
            return sender;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage("§cプレイヤー '" + name + "' はオンラインではありません");
        }
        return target;
    }

    private Integer parseLevel(Player sender, String raw) {
        Integer level = parseInt(sender, raw);
        if (level == null) {
            return null;
        }
        if (level < 1) {
            sender.sendMessage("§cレベルは1以上で指定してください");
            return null;
        }
        return level;
    }

    private Integer parseInt(Player sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c数値で指定してください: " + raw);
            return null;
        }
    }

    private void logAction(CommandSender executor, Player target, String action) {
        plugin.getLogger().info(String.format("[TestKit] %s が %s に対して実行: %s",
            executor.getName(), target.getName(), action));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6===== /tntest 管理者テスト支援コマンド =====");
        sender.sendMessage("§e/tntest job <職業> <Lv> [対象] §7- 指定職業に就かせLvを即設定（リセットあり・上級職OK）");
        sender.sendMessage("§e/tntest setlevel <Lv> [対象] §7- 現職のレベルのみ変更（リセットなし）");
        sender.sendMessage("§e/tntest lv50 [対象] §7- 上級職解禁状態（いずれかの職でLv50）にする");
        sender.sendMessage("§e/tntest reward <職業> <Lv> [対象] §7- レベルアップ報酬を手動付与");
        sender.sendMessage("§e/tntest money <cash|bank> <額> [対象] §7- 現金付与/預金設定");
        sender.sendMessage("§e/tntest reset [対象] §7- 新規プレイヤー相当に初期化");
        sender.sendMessage("§e/tntest kit <職業|marker> [対象] §7- テストアイテム配布");
        sender.sendMessage("§e/tntest time <open|close|hour> [時] §7- ワールド時刻を設定");
        sender.sendMessage("§e/tntest stockreset §7- 食料NPCの在庫/購入制限をリセット");
        sender.sendMessage("§6========================================");
        sender.sendMessage("§7※ OP権限が必要です。testmode中は権限が無効化され弾かれます");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            return filter(Arrays.asList(
                "job", "setlevel", "lv50", "reward", "money", "reset", "kit", "time", "stockreset", "help"), args[0]);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "job":
                case "reward":
                    return filter(JOBS, args[1]);
                case "money":
                    return filter(Arrays.asList("cash", "bank"), args[1]);
                case "kit":
                    List<String> kits = new ArrayList<>(JOBS);
                    kits.add("marker");
                    return filter(kits, args[1]);
                case "time":
                    return filter(Arrays.asList("open", "close", "hour"), args[1]);
                case "lv50":
                case "reset":
                    return filter(onlinePlayerNames(), args[1]);
                default:
                    return result;
            }
        }
        if (args.length == 4 && (sub.equals("job") || sub.equals("reward") || sub.equals("money"))) {
            return filter(onlinePlayerNames(), args[3]);
        }
        if (args.length == 3 && (sub.equals("kit") || sub.equals("setlevel"))) {
            return filter(onlinePlayerNames(), args[2]);
        }
        return result;
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase();
        return candidates.stream()
            .filter(c -> c.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }
}
