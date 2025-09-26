package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.models.PlayerTradeHistory;
import org.tofu.tofunomics.npc.TradingNPCManager;
import org.tofu.tofunomics.trade.TradeChestManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 取引関連コマンド処理（GUI取引専用）
 */
public class TradeCommand implements CommandExecutor, TabCompleter {
    
    private final TradingNPCManager tradingNPCManager;
    private final ConfigManager configManager;
    private final TradeChestManager tradeChestManager;
    private final SimpleDateFormat dateFormat;
    
    private static final String[] VALID_JOBS = {
        "miner", "woodcutter", "farmer", "fisherman",
        "blacksmith", "alchemist", "enchanter", "architect"
    };
    
    public TradeCommand(TradingNPCManager tradingNPCManager, ConfigManager configManager, TradeChestManager tradeChestManager) {
        this.tradingNPCManager = tradingNPCManager;
        this.configManager = configManager;
        this.tradeChestManager = tradeChestManager;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showTradeHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "history":
                return handleHistoryCommand(player, args);
            case "reload":
                return handleReloadCommand(player, args);
            default:
                showTradeHelp(player);
                return true;
        }
    }
    
    
    /**
     * 取引履歴表示コマンド
     */
    private boolean handleHistoryCommand(Player player, String[] args) {
        if (!player.hasPermission("tofunomics.trade.history.self") && 
            !player.hasPermission("tofunomics.trade.history.others")) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
        
        String targetUUID = player.getUniqueId().toString();
        String targetName = player.getName();
        int limit = 10;
        
        // 引数処理
        if (args.length >= 2) {
            if (player.hasPermission("tofunomics.trade.history.others")) {
                // 他のプレイヤーの履歴を見る権限がある場合
                targetName = args[1];
                // 実際の実装では、プレイヤー名からUUIDを取得する必要がある
                // targetUUID = getPlayerUUIDByName(targetName);
            }
        }
        
        if (args.length >= 3) {
            try {
                limit = Integer.parseInt(args[2]);
                limit = Math.max(1, Math.min(limit, 50)); // 1-50の範囲に制限
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "無効な履歴件数です。数値を入力してください。");
                return true;
            }
        }
        
        // TradeChestManagerから取引履歴を取得
        List<PlayerTradeHistory> histories = tradeChestManager.getPlayerTradeHistory(targetUUID, limit);
        
        if (histories.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + targetName + "さんの取引履歴はありません。");
            return true;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== " + targetName + "さんのNPC取引履歴 ===");
        
        for (PlayerTradeHistory history : histories) {
            String itemName = formatItemName(history.getItemType());
            String dateStr = dateFormat.format(history.getTradedAt());
            String jobInfo = history.getPlayerJob() != null ? 
                String.format(" [%s Lv.%d]", configManager.getJobDisplayName(history.getPlayerJob()), 
                             history.getPlayerJobLevel()) : "";
            
            player.sendMessage(String.format("%s%s %sx%d §f→ §a%.2f金塊%s", 
                ChatColor.YELLOW, dateStr, itemName, history.getItemAmount(),
                history.getTotalPrice(), jobInfo));
            
            if (history.getJobBonus() > 0) {
                player.sendMessage(String.format("  %sボーナス: +%.2f金塊 (%.1f%%)", 
                    ChatColor.GRAY, history.getJobBonus(), history.getBonusRate()));
            }
        }
        
        return true;
    }
    
    /**
     * 設定リロードコマンド
     */
    private boolean handleReloadCommand(Player player, String[] args) {
        if (!player.hasPermission("tofunomics.trade.reload")) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
        
        // TradeChestManagerのリロード
        tradeChestManager.reload();
        player.sendMessage(ChatColor.GREEN + "NPC取引システムの設定を再読み込みしました。");
        
        return true;
    }
    
    /**
     * ヘルプ表示
     */
    private void showTradeHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== TofuNomics NPC取引システム ===");
        player.sendMessage(ChatColor.YELLOW + "/trade history [プレイヤー名] [件数] " + ChatColor.GRAY + "- NPC取引履歴表示");
        player.sendMessage(ChatColor.YELLOW + "/trade reload " + ChatColor.GRAY + "- 設定再読み込み");
        player.sendMessage(ChatColor.GRAY + "取引はNPCを右クリックして行います。");
        player.sendMessage(ChatColor.GRAY + "職業: " + String.join(", ", VALID_JOBS));
    }
    
    /**
     * ユーティリティメソッド群
     */
    
    private String formatItemName(String itemType) {
        return itemType.toLowerCase().replace("_", " ");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String[] subCommands = {"history", "reload"};
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        }
        
        return completions;
    }
}