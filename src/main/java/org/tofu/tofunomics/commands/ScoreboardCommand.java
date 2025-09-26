package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.scoreboard.ScoreboardManager;

/**
 * スコアボードのON/OFF切替コマンド
 */
public class ScoreboardCommand implements CommandExecutor {
    
    private final ScoreboardManager scoreboardManager;
    private final ConfigManager configManager;
    
    public ScoreboardCommand(ScoreboardManager scoreboardManager, ConfigManager configManager) {
        this.scoreboardManager = scoreboardManager;
        this.configManager = configManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("tofunomics.scoreboard.toggle")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("no_permission")));
            return true;
        }
        
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            return handleToggle(player);
        } else if (args[0].equalsIgnoreCase("on")) {
            return handleEnable(player);
        } else if (args[0].equalsIgnoreCase("off")) {
            return handleDisable(player);
        } else {
            sendHelpMessage(player);
            return true;
        }
    }
    
    private boolean handleToggle(Player player) {
        boolean newState = scoreboardManager.toggleScoreboard(player);
        
        String message;
        if (newState) {
            message = configManager.getMessage("scoreboard.enabled", "スコアボードを有効にしました。");
        } else {
            message = configManager.getMessage("scoreboard.disabled", "スコアボードを無効にしました。");
        }
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            configManager.getMessagePrefix() + message));
        
        return true;
    }
    
    private boolean handleEnable(Player player) {
        if (scoreboardManager.isScoreboardEnabled(player)) {
            String message = configManager.getMessage("scoreboard.already_enabled", "スコアボードは既に有効です。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
        } else {
            scoreboardManager.enableScoreboard(player);
            String message = configManager.getMessage("scoreboard.enabled", "スコアボードを有効にしました。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
        }
        
        return true;
    }
    
    private boolean handleDisable(Player player) {
        if (!scoreboardManager.isScoreboardEnabled(player)) {
            String message = configManager.getMessage("scoreboard.already_disabled", "スコアボードは既に無効です。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
        } else {
            scoreboardManager.disableScoreboard(player);
            String message = configManager.getMessage("scoreboard.disabled", "スコアボードを無効にしました。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
        }
        
        return true;
    }
    
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "==================== スコアボードコマンド ====================");
        player.sendMessage(ChatColor.YELLOW + "/scoreboard または /scoreboard toggle - スコアボード表示を切り替え");
        player.sendMessage(ChatColor.YELLOW + "/scoreboard on - スコアボード表示を有効化");
        player.sendMessage(ChatColor.YELLOW + "/scoreboard off - スコアボード表示を無効化");
        player.sendMessage(ChatColor.GOLD + "==========================================================");
    }
}