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
import org.tofu.tofunomics.economy.ItemManager;

public class DepositCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final ItemManager itemManager;
    private final BankLocationManager bankLocationManager;
    
    public DepositCommand(ConfigManager configManager, CurrencyConverter currencyConverter, ItemManager itemManager, BankLocationManager bankLocationManager) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.itemManager = itemManager;
        this.bankLocationManager = bankLocationManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "使用法: /deposit <金額|all>");
            return true;
        }
        
        Player player = (Player) sender;

        // ワールド制限チェック
        if (!configManager.isEconomyEnabledInWorld(player.getWorld().getName())) {
            player.sendMessage(ChatColor.RED + "このワールドではTofuNomicsの経済機能を利用できません。");
            return true;
        }

        // 場所制限チェック
        if (!bankLocationManager.isPlayerNearBankOrAtm(player)) {
            String deniedMessage = bankLocationManager.getLocationDeniedMessage(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + deniedMessage));
            return true;
        }
        
        String amountString = args[0];
        
        int availableNuggets = itemManager.countGoldNuggetsInInventory(player);
        
        if (availableNuggets == 0) {
            player.sendMessage(ChatColor.RED + "インベントリに有効なTofuCoinまたはTofuGoldがありません。");
            player.sendMessage(ChatColor.GRAY + "※ TofuGold は通貨専用の金インゴットです（1 個 = TofuCoin 9 枚分）");
            return true;
        }
        
        int nuggetsToDeposit;
        
        if ("all".equalsIgnoreCase(amountString)) {
            nuggetsToDeposit = availableNuggets;
        } else {
            try {
                double amount = Double.parseDouble(amountString);
                
                if (amount <= 0) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
                    return true;
                }
                
                nuggetsToDeposit = currencyConverter.convertBalanceToNuggets(amount);
                
                if (nuggetsToDeposit > availableNuggets) {
                    player.sendMessage(ChatColor.RED + "手持ちの現金（TofuCoin・TofuGold）が足りません。");
                    player.sendMessage(ChatColor.GRAY + "手持ちの現金: "
                        + currencyConverter.formatCurrency(currencyConverter.convertNuggetsToBalance(availableNuggets))
                        + " " + configManager.getCurrencySymbol());
                    return true;
                }
                
                double maxDeposit = configManager.getMaxDeposit();
                if (amount > maxDeposit) {
                    player.sendMessage(ChatColor.RED + "一度に預け入れ可能な最大金額は " + 
                        currencyConverter.formatCurrency(maxDeposit) + " " + 
                        configManager.getCurrencySymbol() + " です。");
                    return true;
                }
                
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
                return true;
            }
        }
        
        if (currencyConverter.depositGoldNuggets(player, nuggetsToDeposit)) {
            double depositedAmount = currencyConverter.convertNuggetsToBalance(nuggetsToDeposit);
            String formattedAmount = currencyConverter.formatCurrency(depositedAmount);
            String currencySymbol = configManager.getCurrencySymbol();
            
            String message = configManager.getMessage("economy.deposit_success",
                "amount", formattedAmount,
                "currency", currencySymbol);
            
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + message));
            
            // スコアボードを即座に更新
            TofuNomics plugin = TofuNomics.getInstance();
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().updatePlayerScoreboard(player);
            }
            
        } else {
            player.sendMessage(ChatColor.RED + "預け入れ処理中にエラーが発生しました。しばらくしてから再度お試しください。");
        }
        
        return true;
    }
}