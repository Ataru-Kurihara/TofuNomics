package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.config.ConfigValidator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * フェーズ6 - 設定リロード・検証コマンド
 * リアルタイム設定更新機能を提供
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final ConfigValidator configValidator;
    
    public ReloadCommand(TofuNomics plugin, ConfigManager configManager, ConfigValidator configValidator) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.configValidator = configValidator;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tofunomics.admin.reload")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
        
        if (args.length == 0) {
            // デフォルト: 基本的な設定リロード
            performBasicReload(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "config":
                performConfigReload(sender);
                break;
            case "validate":
                performValidation(sender);
                break;
            case "fix":
                performAutoFix(sender);
                break;
            case "full":
                performFullReload(sender);
                break;
            case "help":
                showHelp(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンド: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "使用方法: /tofunomics reload [config|validate|fix|full|help]");
                break;
        }
        
        return true;
    }
    
    /**
     * 基本的な設定リロードを実行
     */
    private void performBasicReload(CommandSender sender) {
        long startTime = System.currentTimeMillis();
        
        sender.sendMessage(ChatColor.YELLOW + "設定をリロード中...");
        
        try {
            configManager.reloadConfig();
            
            long endTime = System.currentTimeMillis();
            
            sender.sendMessage(ChatColor.GREEN + "設定のリロードが完了しました。");
            sender.sendMessage(ChatColor.GRAY + "処理時間: " + (endTime - startTime) + "ms");
            
            plugin.getLogger().info("設定が " + sender.getName() + " によってリロードされました。");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定のリロードに失敗しました: " + e.getMessage());
            plugin.getLogger().severe("設定リロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 設定ファイルのみのリロードを実行
     */
    private void performConfigReload(CommandSender sender) {
        long startTime = System.currentTimeMillis();
        
        sender.sendMessage(ChatColor.YELLOW + "設定ファイルをリロード中...");
        
        try {
            plugin.reloadConfig();
            configManager.reloadConfig();
            
            long endTime = System.currentTimeMillis();
            
            sender.sendMessage(ChatColor.GREEN + "設定ファイルのリロードが完了しました。");
            sender.sendMessage(ChatColor.GRAY + "処理時間: " + (endTime - startTime) + "ms");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定ファイルのリロードに失敗しました: " + e.getMessage());
            plugin.getLogger().severe("設定ファイルリロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 設定値の検証のみを実行
     */
    private void performValidation(CommandSender sender) {
        long startTime = System.currentTimeMillis();
        
        sender.sendMessage(ChatColor.YELLOW + "設定ファイルを検証中...");
        
        try {
            boolean isValid = configValidator.validateConfiguration();
            List<ConfigValidator.ValidationError> errors = configValidator.getValidationErrors();
            
            long endTime = System.currentTimeMillis();
            
            if (isValid) {
                sender.sendMessage(ChatColor.GREEN + "設定ファイルの検証が完了しました。問題は見つかりませんでした。");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "設定ファイルに " + String.valueOf(errors.size()) + " 個の問題が見つかりました:");
                
                // エラー詳細の表示（最初の5つまで）
                int displayCount = Math.min(errors.size(), 5);
                for (int i = 0; i < displayCount; i++) {
                    ConfigValidator.ValidationError error = errors.get(i);
                    sender.sendMessage(ChatColor.RED + "  " + (i + 1) + ". " + error.getConfigPath());
                    sender.sendMessage(ChatColor.GRAY + "     " + error.getMessage());
                    sender.sendMessage(ChatColor.GRAY + "     推奨値: " + error.getSuggestedValue());
                }
                
                if (errors.size() > 5) {
                    sender.sendMessage(ChatColor.GRAY + "  ... 他 " + String.valueOf(errors.size() - 5) + " 個のエラー");
                }
                
                sender.sendMessage(ChatColor.YELLOW + "修正するには '/tofunomics reload fix' を実行してください。");
            }
            
            sender.sendMessage(ChatColor.GRAY + "処理時間: " + (endTime - startTime) + "ms");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定ファイルの検証に失敗しました: " + e.getMessage());
            plugin.getLogger().severe("設定検証中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 設定エラーの自動修正を実行
     */
    private void performAutoFix(CommandSender sender) {
        long startTime = System.currentTimeMillis();
        
        sender.sendMessage(ChatColor.YELLOW + "設定エラーの自動修正中...");
        
        try {
            // まず検証を実行
            configValidator.validateConfiguration();
            List<ConfigValidator.ValidationError> errors = configValidator.getValidationErrors();
            
            if (errors.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "修正すべきエラーはありません。");
                return;
            }
            
            sender.sendMessage(ChatColor.YELLOW + String.valueOf(errors.size()) + " 個のエラーを修正中...");
            
            // 自動修正を実行
            configValidator.autoFixAllErrors();
            
            long endTime = System.currentTimeMillis();
            
            sender.sendMessage(ChatColor.GREEN + "設定エラーの自動修正が完了しました。");
            sender.sendMessage(ChatColor.GRAY + "処理時間: " + (endTime - startTime) + "ms");
            
            plugin.getLogger().info("設定エラーが " + sender.getName() + " によって自動修正されました。");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定エラーの自動修正に失敗しました: " + e.getMessage());
            plugin.getLogger().severe("設定自動修正中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 完全なリロード（検証 + 修正 + リロード）を実行
     */
    private void performFullReload(CommandSender sender) {
        long startTime = System.currentTimeMillis();
        
        sender.sendMessage(ChatColor.YELLOW + "完全なリロード処理を開始中...");
        
        try {
            // ステップ1: 設定検証
            sender.sendMessage(ChatColor.YELLOW + "[1/3] 設定ファイルを検証中...");
            configValidator.validateConfiguration();
            
            // ステップ2: エラー修正
            List<ConfigValidator.ValidationError> errors = configValidator.getValidationErrors();
            if (!errors.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "[2/3] " + String.valueOf(errors.size()) + " 個のエラーを修正中...");
                configValidator.autoFixAllErrors();
            } else {
                sender.sendMessage(ChatColor.GREEN + "[2/3] 修正すべきエラーはありません。");
            }
            
            // ステップ3: 設定リロード
            sender.sendMessage(ChatColor.YELLOW + "[3/3] 設定をリロード中...");
            configManager.reloadConfig();
            
            long endTime = System.currentTimeMillis();
            
            sender.sendMessage(ChatColor.GREEN + "完全なリロード処理が完了しました。");
            sender.sendMessage(ChatColor.GRAY + "処理時間: " + (endTime - startTime) + "ms");
            
            plugin.getLogger().info("完全な設定リロードが " + sender.getName() + " によって実行されました。");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "完全リロード処理に失敗しました: " + e.getMessage());
            plugin.getLogger().severe("完全リロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ヘルプメッセージを表示
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TofuNomics リロードコマンド ===");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload " + ChatColor.WHITE + "- 基本的な設定リロード");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload config " + ChatColor.WHITE + "- 設定ファイルのみリロード");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload validate " + ChatColor.WHITE + "- 設定値の検証のみ実行");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload fix " + ChatColor.WHITE + "- 設定エラーの自動修正");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload full " + ChatColor.WHITE + "- 完全リロード（検証+修正+リロード）");
        sender.sendMessage(ChatColor.YELLOW + "/tofunomics reload help " + ChatColor.WHITE + "- このヘルプを表示");
        sender.sendMessage(ChatColor.GRAY + "権限: tofunomics.admin.reload");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tofunomics.admin.reload")) {
            return Arrays.asList();
        }
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                "config", "validate", "fix", "full", "help"
            );
            
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return Arrays.asList();
    }
}