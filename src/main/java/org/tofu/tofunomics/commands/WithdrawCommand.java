package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.BankLocationManager;
import org.tofu.tofunomics.economy.CurrencyConverter;

public class WithdrawCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final BankLocationManager bankLocationManager;
    
    public WithdrawCommand(ConfigManager configManager, CurrencyConverter currencyConverter, BankLocationManager bankLocationManager) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.bankLocationManager = bankLocationManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "使用法: /withdraw <金額>");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 場所制限チェック
        if (!bankLocationManager.isPlayerNearBankOrAtm(player)) {
            String deniedMessage = bankLocationManager.getLocationDeniedMessage(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + deniedMessage));
            return true;
        }
        
        String amountString = args[0];
        
        double amount;
        try {
            amount = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
            return true;
        }
        
        double maxWithdraw = configManager.getMaxWithdraw();
        if (amount > maxWithdraw) {
            sender.sendMessage(ChatColor.RED + "一度に引き出し可能な最大金額は " + 
                currencyConverter.formatCurrency(maxWithdraw) + " " + 
                configManager.getCurrencySymbol() + " です。");
            return true;
        }
        
        CurrencyConverter.WithdrawResult result = currencyConverter.withdrawToGoldNuggets(player, amount);
        
        switch (result) {
            case SUCCESS:
                String formattedAmount = currencyConverter.formatCurrency(amount);
                String currencySymbol = configManager.getCurrencySymbol();
                
                String message = configManager.getMessage("economy.withdraw_success",
                    "amount", formattedAmount,
                    "currency", currencySymbol);
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + message));
                
                // スコアボードを即座に更新
                TofuNomics plugin = TofuNomics.getInstance();
                if (plugin.getScoreboardManager() != null) {
                    plugin.getScoreboardManager().updatePlayerScoreboard(player);
                }
                break;
                
            case INSUFFICIENT_BALANCE:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("insufficient_balance")));
                break;
                
            case INSUFFICIENT_INVENTORY_SPACE:
                player.sendMessage(ChatColor.RED + "インベントリに十分な空きスペースがありません。");
                break;
                
            case INVALID_AMOUNT:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
                break;
                
            case DATABASE_ERROR:
                player.sendMessage(ChatColor.RED + "引き出し処理中にエラーが発生しました。しばらくしてから再度お試しください。");
                break;
        }
        
        return true;
    }
}