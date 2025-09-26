package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;

public class BalanceCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    
    public BalanceCommand(ConfigManager configManager, CurrencyConverter currencyConverter) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleSelfBalance(sender);
        } else if (args.length == 1) {
            return handleOtherBalance(sender, args[0]);
        } else {
            sender.sendMessage(ChatColor.RED + "使用法: /balance [プレイヤー名]");
            return true;
        }
    }
    
    private boolean handleSelfBalance(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        double cashBalance = currencyConverter.getCashBalance(player);
        double bankBalance = currencyConverter.getBankBalance(player);
        double totalBalance = cashBalance + bankBalance;
        
        String currencySymbol = configManager.getCurrencySymbol();
        String formattedCash = currencyConverter.formatCurrency(cashBalance);
        String formattedBank = currencyConverter.formatCurrency(bankBalance);
        String formattedTotal = currencyConverter.formatCurrency(totalBalance);
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            configManager.getMessagePrefix() + "&6==== 残高情報 ===="));
        player.sendMessage(ChatColor.GREEN + "現金（金塊）: " + ChatColor.WHITE + formattedCash + currencySymbol);
        player.sendMessage(ChatColor.GOLD + "預金（銀行）: " + ChatColor.WHITE + formattedBank + currencySymbol);
        player.sendMessage(ChatColor.YELLOW + "総資産: " + ChatColor.WHITE + formattedTotal + currencySymbol);
        
        return true;
    }
    
    private boolean handleOtherBalance(CommandSender sender, String targetPlayerName) {
        if (!sender.hasPermission("tofunomics.balance.others")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("no_permission")));
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
            return true;
        }
        
        double cashBalance = currencyConverter.getCashBalance(targetPlayer);
        double bankBalance = currencyConverter.getBankBalance(targetPlayer);
        double totalBalance = cashBalance + bankBalance;
        
        String currencySymbol = configManager.getCurrencySymbol();
        String formattedCash = currencyConverter.formatCurrency(cashBalance);
        String formattedBank = currencyConverter.formatCurrency(bankBalance);
        String formattedTotal = currencyConverter.formatCurrency(totalBalance);
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
            configManager.getMessagePrefix() + "&6==== " + targetPlayer.getName() + "の残高情報 ===="));
        sender.sendMessage(ChatColor.GREEN + "現金（金塊）: " + ChatColor.WHITE + formattedCash + currencySymbol);
        sender.sendMessage(ChatColor.GOLD + "預金（銀行）: " + ChatColor.WHITE + formattedBank + currencySymbol);
        sender.sendMessage(ChatColor.YELLOW + "総資産: " + ChatColor.WHITE + formattedTotal + currencySymbol);
        
        return true;
    }
}