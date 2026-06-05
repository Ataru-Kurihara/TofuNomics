package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;

import java.util.List;
import java.util.UUID;

public class BalanceTopCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final PlayerDAO playerDAO;
    
    public BalanceTopCommand(ConfigManager configManager, CurrencyConverter currencyConverter, PlayerDAO playerDAO) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.playerDAO = playerDAO;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = 10;
        
        if (args.length == 1) {
            try {
                int requestedLimit = Integer.parseInt(args[0]);
                if (requestedLimit > 0 && requestedLimit <= 50) {
                    limit = requestedLimit;
                } else {
                    sender.sendMessage(ChatColor.RED + "表示数は1から50までの範囲で指定してください。");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "無効な数値です。使用法: /balancetop [表示数]");
                return true;
            }
        } else if (args.length > 1) {
            sender.sendMessage(ChatColor.RED + "使用法: /balancetop [表示数]");
            return true;
        }
        
        List<org.tofu.tofunomics.models.Player> topPlayers = playerDAO.getTopPlayersByBalance(limit);
        
        if (topPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "残高データが見つかりませんでした。");
            return true;
        }
        
        String currencySymbol = configManager.getCurrencySymbol();
        
        sender.sendMessage(ChatColor.GOLD + "==================== 残高ランキング TOP " + limit + " ====================");

        // Playerかつ演出有効時はホバーで詳細を表示するリッチテキストを使用
        boolean richDisplay = (sender instanceof org.bukkit.entity.Player)
            && configManager.isClickableMessagesEnabled();

        for (int i = 0; i < topPlayers.size(); i++) {
            org.tofu.tofunomics.models.Player tofuPlayer = topPlayers.get(i);
            int rank = i + 1;

            String playerName = getPlayerName(tofuPlayer.getUuid().toString());
            String formattedBalance = currencyConverter.formatCurrency(tofuPlayer.getBalance());

            ChatColor rankColor = getRankColor(rank);

            String rankText = getRankText(rank);

            String rowText = String.format("%s%s %s%s: %s%s %s",
                rankColor, rankText,
                ChatColor.WHITE, playerName,
                ChatColor.GREEN, formattedBalance, currencySymbol);

            if (richDisplay) {
                String hover = String.format("&6%s位&7: &f%s\n&a残高: %s %s",
                    rank, playerName, formattedBalance, currencySymbol);
                org.tofu.tofunomics.util.RichMessageBuilder.create()
                    .hoverText(rowText, hover)
                    .sendTo((org.bukkit.entity.Player) sender);
            } else {
                sender.sendMessage(rowText);
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "================================================================");
        
        return true;
    }
    
    private String getPlayerName(String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            return name != null ? name : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private ChatColor getRankColor(int rank) {
        switch (rank) {
            case 1:
                return ChatColor.GOLD;
            case 2:
                return ChatColor.GRAY;
            case 3:
                return ChatColor.DARK_RED;
            default:
                return ChatColor.YELLOW;
        }
    }
    
    private String getRankText(int rank) {
        switch (rank) {
            case 1:
                return "🥇 1位";
            case 2:
                return "🥈 2位";
            case 3:
                return "🥉 3位";
            default:
                return rank + "位";
        }
    }
}