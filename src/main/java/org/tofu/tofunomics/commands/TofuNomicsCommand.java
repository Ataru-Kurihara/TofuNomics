package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.npc.BankNPCManager;
import org.tofu.tofunomics.npc.NPCManager;
import org.tofu.tofunomics.npc.TradingNPCManager;
import org.tofu.tofunomics.npc.FoodNPCManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TofuNomicsCommand implements CommandExecutor, TabCompleter {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCCommand npcCommand;
    
    public TofuNomicsCommand(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager, 
                        BankNPCManager bankNPCManager, TradingNPCManager tradingNPCManager, FoodNPCManager foodNPCManager) {
    this.plugin = plugin;
    this.configManager = configManager;
    this.npcCommand = new NPCCommand(plugin, configManager, npcManager, bankNPCManager, tradingNPCManager, foodNPCManager);
}
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReloadCommand(sender);
            case "status":
                return handleStatusCommand(sender);
            case "version":
                return handleVersionCommand(sender);
            case "config":
                // configサブコマンドに処理を委譲
                return handleConfigCommand(sender, args);
            case "npc":
                // NPCサブコマンドに処理を委譲
                String[] npcArgs = new String[args.length - 1];
                System.arraycopy(args, 1, npcArgs, 0, npcArgs.length);
                return npcCommand.onCommand(sender, command, label, npcArgs);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("tofunomics.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        sender.sendMessage("§eTofuNomicsプラグインをリロード中...");
        
        try {
            // 設定リロード
            plugin.reloadConfig();
            
            sender.sendMessage("§aTofuNomicsプラグインのリロードが完了しました。");
            sender.sendMessage("§e注意: 完全なリロードにはプラグインの再起動が推奨されます。");
            plugin.getLogger().info("プラグインがリロードされました（実行者: " + sender.getName() + "）");
        } catch (Exception e) {
            sender.sendMessage("§cリロード中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("リロード中にエラーが発生: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleStatusCommand(CommandSender sender) {
        if (!sender.hasPermission("tofunomics.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        sender.sendMessage("§6=== TofuNomics ステータス ===");
        sender.sendMessage("§eバージョン: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eデータベース: §f" + (plugin.getDatabaseManager().isConnected() ? "接続中" : "切断"));
        sender.sendMessage("§eNPCシステム: §f" + (configManager.isNPCSystemEnabled() ? "有効" : "無効"));
        
        if (plugin.getNPCManager() != null) {
            sender.sendMessage("§e管理NPC数: §f" + plugin.getNPCManager().getAllNPCs().size() + " 個");
        }
        
        return true;
    }
    
    private boolean handleVersionCommand(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics 情報 ===");
        sender.sendMessage("§eプラグイン名: §f" + plugin.getDescription().getName());
        sender.sendMessage("§eバージョン: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§e作者: §f" + plugin.getDescription().getAuthors().toString());
        sender.sendMessage("§e説明: §f" + plugin.getDescription().getDescription());
        return true;
    }

    
    private boolean handleConfigCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sendConfigUsage(sender);
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "generate":
                return handleConfigGenerate(sender);
            case "fix":
                return handleConfigFix(sender);
            case "validate":
                return handleConfigValidate(sender);
            case "backup":
                return handleConfigBackup(sender);
            case "messages":
                return handleConfigMessages(sender, args);
            default:
                sendConfigUsage(sender);
                return true;
        }
    }
    
    private boolean handleConfigGenerate(CommandSender sender) {
        sender.sendMessage("§e完全なデフォルト設定ファイルを生成中...");
        sender.sendMessage("§c警告: 既存の設定は上書きされます！");
        
        try {
            boolean success = configManager.generateDefaultConfig();
            if (success) {
                sender.sendMessage("§a完全なデフォルト設定ファイルの生成が完了しました。");
                sender.sendMessage("§e既存の設定はバックアップされました。");
            } else {
                sender.sendMessage("§c設定ファイルの生成に失敗しました。詳細はコンソールを確認してください。");
            }
        } catch (Exception e) {
            sender.sendMessage("§c設定ファイル生成中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("設定ファイル生成エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleConfigFix(CommandSender sender) {
        sender.sendMessage("§e設定の問題を自動修正中...");
        
        try {
            boolean success = configManager.fixConfigIssues();
            if (success) {
                sender.sendMessage("§a設定の自動修正が完了しました。");
                sender.sendMessage("§e詳細な修正内容はコンソールを確認してください。");
            } else {
                sender.sendMessage("§c設定の自動修正に失敗しました。詳細はコンソールを確認してください。");
            }
        } catch (Exception e) {
            sender.sendMessage("§c設定の自動修正中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("設定自動修正エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleConfigValidate(CommandSender sender) {
        sender.sendMessage("§e設定の妥当性をチェック中...");
        
        try {
            ConfigManager.ConfigValidationResult result = configManager.validateConfigSettings();
            
            if (result.isValid()) {
                sender.sendMessage("§a設定は正常です！");
            } else {
                sender.sendMessage("§c設定に問題が見つかりました:");
                
                for (String error : result.getErrors()) {
                    sender.sendMessage("§c  エラー: " + error);
                }
            }
            
            if (result.hasWarnings()) {
                sender.sendMessage("§e警告:");
                for (String warning : result.getWarnings()) {
                    sender.sendMessage("§e  警告: " + warning);
                }
            }
            
            sender.sendMessage("§7=== 検証結果 ===");
            sender.sendMessage("§7エラー数: " + result.getErrors().size());
            sender.sendMessage("§7警告数: " + result.getWarnings().size());
            
        } catch (Exception e) {
            sender.sendMessage("§c設定の検証中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("設定検証エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleConfigBackup(CommandSender sender) {
        sender.sendMessage("§e設定ファイルのバックアップを作成中...");
        
        try {
            boolean success = configManager.createConfigBackup();
            if (success) {
                sender.sendMessage("§a設定ファイルのバックアップが作成されました。");
            } else {
                sender.sendMessage("§cバックアップの作成に失敗しました。詳細はコンソールを確認してください。");
            }
        } catch (Exception e) {
            sender.sendMessage("§cバックアップ作成中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("バックアップ作成エラー: " + e.getMessage());
        }
        
        return true;
    }

    
    private boolean handleConfigMessages(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessagesUsage(sender);
            return true;
        }
        
        switch (args[2].toLowerCase()) {
            case "reload":
                return handleMessagesReload(sender);
            case "validate":
                return handleMessagesValidate(sender);
            case "test":
                return handleMessagesTest(sender, args);
            default:
                sendMessagesUsage(sender);
                return true;
        }
    }
    
    private boolean handleMessagesReload(CommandSender sender) {
        sender.sendMessage("§eNPCメッセージを再読み込み中...");
        
        try {
            boolean success = configManager.reloadMessages();
            if (success) {
                sender.sendMessage("§aNPCメッセージの再読み込みが完了しました。");
            } else {
                sender.sendMessage("§cNPCメッセージの再読み込みに失敗しました。詳細はコンソールを確認してください。");
            }
        } catch (Exception e) {
            sender.sendMessage("§cNPCメッセージ再読み込み中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("NPCメッセージ再読み込みエラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleMessagesValidate(CommandSender sender) {
        sender.sendMessage("§eNPCメッセージの妥当性をチェック中...");
        
        try {
            ConfigManager.MessageValidationResult result = configManager.validateMessages();
            
            if (result.isValid()) {
                sender.sendMessage("§aNPCメッセージは正常です！");
            } else {
                sender.sendMessage("§cNPCメッセージに問題が見つかりました:");
                
                for (String error : result.getErrors()) {
                    sender.sendMessage("§c  エラー: " + error);
                }
            }
            
            if (result.hasWarnings()) {
                sender.sendMessage("§e警告:");
                for (String warning : result.getWarnings()) {
                    sender.sendMessage("§e  警告: " + warning);
                }
            }
            
            sender.sendMessage("§7=== NPCメッセージ検証結果 ===");
            sender.sendMessage("§7エラー数: " + result.getErrors().size());
            sender.sendMessage("§7警告数: " + result.getWarnings().size());
            
        } catch (Exception e) {
            sender.sendMessage("§cNPCメッセージの検証中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("NPCメッセージ検証エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleMessagesTest(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。");
            return true;
        }
        
        if (args.length < 5) {
            sender.sendMessage("§c使用方法: /tofunomics config messages test <NPCタイプ> <メッセージタイプ>");
            sender.sendMessage("§7例: /tofunomics config messages test central_market greeting");
            sender.sendMessage("§7利用可能なNPCタイプ: central_market, mining_post, wood_market, farm_market, fishing_dock");
            sender.sendMessage("§7利用可能なメッセージタイプ: greeting, no_job, job_not_accepted, welcome");
            return true;
        }
        
        String npcType = args[3];
        String messageType = args[4];
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        
        try {
            configManager.testNPCMessage(player, npcType, messageType);
        } catch (Exception e) {
            sender.sendMessage("§cメッセージテスト中にエラーが発生: " + e.getMessage());
            plugin.getLogger().severe("NPCメッセージテストエラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private void sendMessagesUsage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics Config Messages コマンド ===");
        sender.sendMessage("§f/tofunomics config messages reload §7- NPCメッセージを再読み込み");
        sender.sendMessage("§f/tofunomics config messages validate §7- NPCメッセージの妥当性をチェック");
        sender.sendMessage("§f/tofunomics config messages test <NPC> <メッセージ> §7- NPCメッセージをテスト");
        sender.sendMessage("§7例: §f/tofunomics config messages test central_market greeting");
    }
    
    private void sendConfigUsage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics Config コマンド ===");
        sender.sendMessage("§f/tofunomics config generate §7- 完全なデフォルト設定を生成");
        sender.sendMessage("§f/tofunomics config fix §7- 既存設定の問題を自動修正");
        sender.sendMessage("§f/tofunomics config validate §7- 設定の妥当性をチェック");
        sender.sendMessage("§f/tofunomics config backup §7- 設定のバックアップを作成");
        sender.sendMessage("§f/tofunomics config messages <サブコマンド> §7- NPCメッセージ管理");
        sender.sendMessage("§7messagesサブコマンド: reload, validate, test");
        sender.sendMessage("§c注意: generate は既存設定を上書きします");
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics コマンド ===");
        sender.sendMessage("§f/tofunomics reload §7- プラグイン設定をリロード");
        sender.sendMessage("§f/tofunomics status §7- プラグイン状態を表示");
        sender.sendMessage("§f/tofunomics version §7- バージョン情報を表示");
        sender.sendMessage("§f/tofunomics config <サブコマンド> §7- 設定管理機能");
        sender.sendMessage("§f/tofunomics npc <サブコマンド> §7- NPC管理機能");
        sender.sendMessage("§7使用可能なconfigサブコマンド:");
        sender.sendMessage("§7  generate, fix, validate, backup, messages");
        sender.sendMessage("§7使用可能なNPCサブコマンド:");
        sender.sendMessage("§7  list, cleanup, reload, info");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "status", "version", "config", "npc");
        } else if (args.length == 2) {
            if ("config".equals(args[0].toLowerCase())) {
                return Arrays.asList("generate", "fix", "validate", "backup", "messages");
            } else if ("npc".equals(args[0].toLowerCase())) {
                return npcCommand.onTabComplete(sender, command, alias, new String[]{args[1]});
            }
        } else if (args.length == 3) {
            if ("config".equals(args[0].toLowerCase()) && "messages".equals(args[1].toLowerCase())) {
                return Arrays.asList("reload", "validate", "test");
            }
        } else if (args.length == 4) {
            if ("config".equals(args[0].toLowerCase()) && "messages".equals(args[1].toLowerCase()) && "test".equals(args[2].toLowerCase())) {
                return Arrays.asList("central_market", "mining_post", "wood_market", "farm_market", "fishing_dock");
            }
        } else if (args.length == 5) {
            if ("config".equals(args[0].toLowerCase()) && "messages".equals(args[1].toLowerCase()) && "test".equals(args[2].toLowerCase())) {
                return Arrays.asList("greeting", "no_job", "job_not_accepted", "welcome");
            }
        }
        
        return new ArrayList<>();
    }
}