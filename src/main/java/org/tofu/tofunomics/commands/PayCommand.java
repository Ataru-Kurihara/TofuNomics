package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;

public class PayCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    
    public PayCommand(ConfigManager configManager, CurrencyConverter currencyConverter) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /pay <プレイヤー名> <金額>");
            return true;
        }
        
        Player fromPlayer = (Player) sender;
        String targetPlayerName = args[0];
        String amountString = args[1];
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
            return true;
        }
        
        if (fromPlayer.getUniqueId().equals(targetPlayer.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "自分自身には送金できません。");
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
            return true;
        }
        
        double minAmount = configManager.getMinimumPayAmount();
        if (amount < minAmount) {
            sender.sendMessage(ChatColor.RED + "最低送金額は " + 
                currencyConverter.formatCurrency(minAmount) + " " + 
                configManager.getCurrencySymbol() + " です。");
            return true;
        }
        
        double maxAmount = configManager.getMaximumPayAmount();
        if (maxAmount > 0 && amount > maxAmount) {
            sender.sendMessage(ChatColor.RED + "最高送金額は " + 
                currencyConverter.formatCurrency(maxAmount) + " " + 
                configManager.getCurrencySymbol() + " です。");
            return true;
        }
        
        double fee = amount * (configManager.getPayFeePercentage() / 100.0);
        double totalCost = amount + fee;
        
        if (!currencyConverter.canAfford(fromPlayer, totalCost)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("insufficient_balance")));
            return true;
        }
        
        // 送金額のみを受取人に送金し、手数料は送金者から差し引く
        if (currencyConverter.transfer(fromPlayer, targetPlayer, amount)) {
            // 手数料がある場合は送金者から追加で差し引く
            if (fee > 0) {
                currencyConverter.subtractBalance(fromPlayer.getUniqueId(), fee);
            }
            
            String formattedAmount = currencyConverter.formatCurrency(amount);
            String currencySymbol = configManager.getCurrencySymbol();
            
            String senderMessage = configManager.getMessage("economy.pay_sent",
                "player", targetPlayer.getName(),
                "amount", formattedAmount,
                "currency", currencySymbol);
            
            String receiverMessage = configManager.getMessage("economy.pay_received",
                "player", fromPlayer.getName(),
                "amount", formattedAmount,
                "currency", currencySymbol);
            
            fromPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + senderMessage));
            
            targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + receiverMessage));
            
            if (fee > 0) {
                fromPlayer.sendMessage(ChatColor.YELLOW + "送金手数料: " + 
                    currencyConverter.formatCurrency(fee) + " " + currencySymbol);
            }
            
        } else {
            sender.sendMessage(ChatColor.RED + "送金に失敗しました。しばらくしてから再度お試しください。");
        }
        
        return true;
    }
}