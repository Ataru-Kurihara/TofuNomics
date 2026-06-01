package org.tofu.tofunomics.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * チュートリアルコマンドハンドラー
 * /tutorial - 現在の進捗表示
 * /tutorial skip [confirm] - チュートリアルをスキップ
 * /tutorial restart - チュートリアルをやり直し
 * /tutorial reset <player> - 他プレイヤーのチュートリアルをリセット（管理者用）
 */
public class TutorialCommand implements CommandExecutor, TabCompleter {

    private final TofuNomics plugin;
    private final TutorialManager tutorialManager;
    private final ConfigManager configManager;

    public TutorialCommand(TofuNomics plugin, TutorialManager tutorialManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        Player player = (Player) sender;

        if (!tutorialManager.isEnabled()) {
            player.sendMessage(ChatColor.YELLOW + "チュートリアルシステムは現在無効です。");
            return true;
        }

        if (args.length == 0) {
            // /tutorial - 進捗表示
            tutorialManager.sendProgressDisplay(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "skip":
                return handleSkip(player, args);

            case "restart":
                return handleRestart(player);

            case "reset":
                return handleReset(player, args);

            default:
                sendUsage(player);
                return true;
        }
    }

    private boolean handleSkip(Player player, String[] args) {
        boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
        tutorialManager.skipTutorial(player, confirmed);
        return true;
    }

    private boolean handleRestart(Player player) {
        tutorialManager.restartTutorial(player);
        return true;
    }

    private boolean handleReset(Player player, String[] args) {
        // 管理者権限チェック
        if (!player.hasPermission("tofunomics.tutorial.admin")) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使用法: /tutorial reset <プレイヤー名>");
            return true;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);

        if (targetPlayer != null) {
            // オンラインプレイヤー
            tutorialManager.adminResetTutorial(player, targetPlayer.getUniqueId(), targetName);
        } else {
            // オフラインプレイヤー（UUIDを取得してリセット）
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            if (offlinePlayer.hasPlayedBefore()) {
                tutorialManager.adminResetTutorial(player, offlinePlayer.getUniqueId(), targetName);
            } else {
                player.sendMessage(ChatColor.RED + "プレイヤー「" + targetName + "」が見つかりません。");
            }
        }

        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== チュートリアルコマンド ===");
        player.sendMessage(ChatColor.YELLOW + "/tutorial " + ChatColor.WHITE + "- 進捗を表示");
        player.sendMessage(ChatColor.YELLOW + "/tutorial skip " + ChatColor.WHITE + "- スキップ");
        player.sendMessage(ChatColor.YELLOW + "/tutorial restart " + ChatColor.WHITE + "- やり直し");
        if (player.hasPermission("tofunomics.tutorial.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/tutorial reset <player> " + ChatColor.WHITE + "- リセット（管理者）");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("skip", "restart"));
            if (sender.hasPermission("tofunomics.tutorial.admin")) {
                subCommands.add("reset");
            }
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("skip")) {
                if ("confirm".startsWith(args[1].toLowerCase())) {
                    completions.add("confirm");
                }
            } else if (args[0].equalsIgnoreCase("reset") && sender.hasPermission("tofunomics.tutorial.admin")) {
                // オンラインプレイヤー名を補完
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}
