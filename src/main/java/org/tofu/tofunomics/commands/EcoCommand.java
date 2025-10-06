package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;

public class EcoCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final PlayerDAO playerDAO;
    
    public EcoCommand(ConfigManager configManager, CurrencyConverter currencyConverter, PlayerDAO playerDAO) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.playerDAO = playerDAO;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "give":
                return handleGive(sender, args);
            case "take":
                return handleTake(sender, args);
            case "set":
                return handleSet(sender, args);
            case "setcoinvalue":
                return handleSetCoinValue(sender, args);
            case "getcoinvalue":
                return handleGetCoinValue(sender);
            case "resetcoinvalue":
                return handleResetCoinValue(sender);
            case "reset":
                return handleReset(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco give <プレイヤー名> <金額>");
            return true;
        }
        
        String targetPlayerName = args[1];
        String amountString = args[2];
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
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
        
        if (amount <= 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
            return true;
        }
        
        String uuid = targetPlayer.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(0.0); // 持ち歩き現金は0に設定
            tofuPlayer.setBankBalance(amount); // 銀行預金を設定
            
            if (playerDAO.insertPlayer(tofuPlayer)) {
                String formattedAmount = currencyConverter.formatCurrency(amount);
                String currencySymbol = configManager.getCurrencySymbol();
                
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " に " + 
                    formattedAmount + " " + currencySymbol + " を付与しました。");
                    
                targetPlayer.sendMessage(ChatColor.GREEN + "管理者により " + 
                    formattedAmount + " " + currencySymbol + " が付与されました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        } else {
            tofuPlayer.addBankBalance(amount); // 銀行預金に追加
            
            if (playerDAO.updatePlayerData(tofuPlayer)) {
                String formattedAmount = currencyConverter.formatCurrency(amount);
                String currencySymbol = configManager.getCurrencySymbol();
                
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " に " + 
                    formattedAmount + " " + currencySymbol + " を付与しました。");
                    
                targetPlayer.sendMessage(ChatColor.GREEN + "管理者により " + 
                    formattedAmount + " " + currencySymbol + " が付与されました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        }
        
        return true;
    }
    
    private boolean handleTake(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco take <プレイヤー名> <金額>");
            return true;
        }
        
        String targetPlayerName = args[1];
        String amountString = args[2];
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
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
        
        if (amount <= 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
            return true;
        }
        
        String uuid = targetPlayer.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            sender.sendMessage(ChatColor.RED + "対象プレイヤーのデータが見つかりません。");
            return true;
        }
        
        tofuPlayer.removeBankBalance(amount); // 銀行預金から減算
        
        if (playerDAO.updatePlayerData(tofuPlayer)) {
            String formattedAmount = currencyConverter.formatCurrency(amount);
            String currencySymbol = configManager.getCurrencySymbol();
            
            sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " から " + 
                formattedAmount + " " + currencySymbol + " を取り上げました。");
                
            targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により " + 
                formattedAmount + " " + currencySymbol + " が取り上げられました。");
        } else {
            sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
        }
        
        return true;
    }
    
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco set <プレイヤー名> <金額>");
            return true;
        }
        
        String targetPlayerName = args[1];
        String amountString = args[2];
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
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
        
        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "残高は負の値にできません。");
            return true;
        }
        
        String uuid = targetPlayer.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(0.0); // 持ち歩き現金は0に設定
            tofuPlayer.setBankBalance(amount); // 銀行預金を設定
            
            if (playerDAO.insertPlayer(tofuPlayer)) {
                String formattedAmount = currencyConverter.formatCurrency(amount);
                String currencySymbol = configManager.getCurrencySymbol();
                
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " の残高を " + 
                    formattedAmount + " " + currencySymbol + " に設定しました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        } else {
            tofuPlayer.setBankBalance(amount); // 銀行預金を設定
            
            if (playerDAO.updatePlayerData(tofuPlayer)) {
                String formattedAmount = currencyConverter.formatCurrency(amount);
                String currencySymbol = configManager.getCurrencySymbol();
                
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " の残高を " + 
                    formattedAmount + " " + currencySymbol + " に設定しました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        }
        
        return true;
    }
    
    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco reset <プレイヤー名>");
            return true;
        }
        
        String targetPlayerName = args[1];
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
            return true;
        }
        
        String uuid = targetPlayer.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(0.0);
            tofuPlayer.setBankBalance(0.0);
            
            if (playerDAO.insertPlayer(tofuPlayer)) {
                String currencySymbol = configManager.getCurrencySymbol();
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " の所持金と預金をリセットしました。（残高: 0.0 " + currencySymbol + "）");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により所持金と預金がリセットされました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        } else {
            tofuPlayer.setBalance(0.0);
            tofuPlayer.setBankBalance(0.0);
            
            if (playerDAO.updatePlayerData(tofuPlayer)) {
                String currencySymbol = configManager.getCurrencySymbol();
                sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " の所持金と預金をリセットしました。（残高: 0.0 " + currencySymbol + "）");
                targetPlayer.sendMessage(ChatColor.YELLOW + "管理者により所持金と預金がリセットされました。");
            } else {
                sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            }
        }
        
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        try {
            configManager.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "設定ファイルをリロードしました。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定ファイルのリロードに失敗しました: " + e.getMessage());
        }
        
        return true;
    }

    
    private boolean handleSetCoinValue(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c使用法: /eco setcoinvalue <価値>");
            return true;
        }
        
        try {
            double newValue = Double.parseDouble(args[1]);
            
            if (!configManager.isDynamicValueEnabled()) {
                sender.sendMessage("§c通貨価値の変更は無効になっています。");
                return true;
            }
            
            if (newValue < configManager.getMinCoinValue() || newValue > configManager.getMaxCoinValue()) {
                sender.sendMessage(String.format("§c通貨価値は %.1f から %.1f の範囲内である必要があります。", 
                    configManager.getMinCoinValue(), configManager.getMaxCoinValue()));
                return true;
            }
            
            configManager.setCoinValue(newValue);
            sender.sendMessage(String.format("§a通貨価値を %.1f に設定しました。（1コイン = $%.1f）", 
                newValue, newValue));
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§c無効な数値です。");
        } catch (Exception e) {
            sender.sendMessage("§c設定の更新に失敗しました: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleGetCoinValue(CommandSender sender) {
        double currentValue = configManager.getCoinValue();
        sender.sendMessage("§6=== 通貨価値情報 ===");
        sender.sendMessage(String.format("§e現在の価値: §f1コイン = $%.1f", currentValue));
        sender.sendMessage(String.format("§e最小価値: §f$%.1f", configManager.getMinCoinValue()));
        sender.sendMessage(String.format("§e最大価値: §f$%.1f", configManager.getMaxCoinValue()));
        sender.sendMessage(String.format("§e変更可能: §f%s", 
            configManager.isDynamicValueEnabled() ? "有効" : "無効"));
        return true;
    }
    
    private boolean handleResetCoinValue(CommandSender sender) {
        if (!configManager.isDynamicValueEnabled()) {
            sender.sendMessage("§c通貨価値の変更は無効になっています。");
            return true;
        }
        
        try {
            configManager.setCoinValue(10.0); // デフォルト値
            sender.sendMessage("§a通貨価値をデフォルト値（1コイン = $10.0）にリセットしました。");
        } catch (Exception e) {
            sender.sendMessage("§c設定のリセットに失敗しました: " + e.getMessage());
        }
        
        return true;
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics 経済コマンド ===");
        sender.sendMessage("§e/eco give <プレイヤー> <金額> §7- プレイヤーに金額を付与");
        sender.sendMessage("§e/eco take <プレイヤー> <金額> §7- プレイヤーから金額を減額");
        sender.sendMessage("§e/eco set <プレイヤー> <金額> §7- プレイヤーの残高を設定");
        sender.sendMessage("§e/eco setcoinvalue <価値> §7- 通貨価値を設定");
        sender.sendMessage("§e/eco getcoinvalue §7- 現在の通貨価値を表示");
        sender.sendMessage("§e/eco resetcoinvalue §7- 通貨価値をデフォルトに戻す");
        sender.sendMessage("§e/eco reset <プレイヤー> §7- プレイヤーの所持金と預金をリセット");
        sender.sendMessage("§e/eco reload §7- 設定ファイルを再読み込み");
    }
}