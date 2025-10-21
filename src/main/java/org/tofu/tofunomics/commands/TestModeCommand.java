package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.testing.TestModeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * テストモードコマンド
 * OP権限を持つ管理者が権限のないユーザーの挙動をテストするためのコマンド
 */
public class TestModeCommand implements CommandExecutor, TabCompleter {
    private final TofuNomics plugin;
    private final TestModeManager testModeManager;

    public TestModeCommand(TofuNomics plugin, TestModeManager testModeManager) {
        this.plugin = plugin;
        this.testModeManager = testModeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // プレイヤーのみ実行可能
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }

        Player player = (Player) sender;

        // OP権限チェック（テストモードの切り替え自体にはOPが必要）
        if (!player.isOp()) {
            player.sendMessage("§cこのコマンドはOP権限が必要です");
            return true;
        }

        // 引数なしの場合は使用法を表示
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on":
            case "enable":
                handleEnableCommand(player);
                break;
            case "off":
            case "disable":
                handleDisableCommand(player);
                break;
            case "status":
                handleStatusCommand(player);
                break;
            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    /**
     * テストモード有効化
     */
    private void handleEnableCommand(Player player) {
        if (testModeManager.isInTestMode(player)) {
            player.sendMessage("§e既にテストモードが有効化されています");
            return;
        }

        testModeManager.enableTestMode(player);
        player.sendMessage("§a=============================");
        player.sendMessage("§aテストモードを有効化しました");
        player.sendMessage("§7権限のないプレイヤーとして挙動をテストできます");
        player.sendMessage("§7例: WorldGuardで保護された扉やトラップドアのテスト");
        player.sendMessage("§7無効化: /testmode off");
        player.sendMessage("§a=============================");
    }

    /**
     * テストモード無効化
     */
    private void handleDisableCommand(Player player) {
        if (!testModeManager.isInTestMode(player)) {
            player.sendMessage("§eテストモードは有効化されていません");
            return;
        }

        testModeManager.disableTestMode(player);
        player.sendMessage("§a=============================");
        player.sendMessage("§aテストモードを無効化しました");
        player.sendMessage("§7通常のOP権限に戻りました");
        player.sendMessage("§a=============================");
    }

    /**
     * テストモード状態表示
     */
    private void handleStatusCommand(Player player) {
        player.sendMessage("§6===== テストモード状態 =====");
        if (testModeManager.isInTestMode(player)) {
            player.sendMessage("§eあなたの状態: §a有効");
            player.sendMessage("§7現在、権限のないプレイヤーとして扱われています");
        } else {
            player.sendMessage("§eあなたの状態: §c無効");
            player.sendMessage("§7通常のOP権限で動作しています");
        }
        
        int totalTestModePlayers = testModeManager.getTestModePlayerCount();
        player.sendMessage("§eテストモード中のプレイヤー数: §f" + totalTestModePlayers);
        player.sendMessage("§6============================");
    }

    /**
     * 使用法を表示
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6===== テストモードコマンド =====");
        sender.sendMessage("§e/testmode on §7- テストモードを有効化");
        sender.sendMessage("§e/testmode off §7- テストモードを無効化");
        sender.sendMessage("§e/testmode status §7- 現在の状態を表示");
        sender.sendMessage("§6================================");
        sender.sendMessage("§7※ テストモード中は全ての権限が無効化されます");
        sender.sendMessage("§7※ WorldGuard保護のテストなどに使用できます");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("on", "off", "status", "enable", "disable"));
        }

        return completions;
    }
}
