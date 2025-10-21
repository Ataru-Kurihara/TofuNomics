package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.items.ClockItemManager;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 時計アイテム関連コマンド
 * /clock buy - 時計を購入
 * /clock info - 時計の情報を表示
 */
public class ClockCommand implements CommandExecutor, TabCompleter {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final ClockItemManager clockItemManager;
    private final PlayerDAO playerDAO;
    
    public ClockCommand(TofuNomics plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.clockItemManager = plugin.getClockItemManager();
        this.playerDAO = plugin.getPlayerDAO();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!configManager.isClockItemEnabled()) {
            sender.sendMessage("§c時計アイテムシステムは無効化されています。");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "buy":
            case "purchase":
                return handleBuyCommand(player);
            
            case "info":
                return handleInfoCommand(player);
            
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    /**
     * 時計購入処理
     */
    private boolean handleBuyCommand(Player player) {
        // 既に所持しているかチェック
        if (clockItemManager.hasClockItem(player)) {
            player.sendMessage(configManager.getClockItemAlreadyOwnedMessage());
            return true;
        }
        
        // 購入価格を取得
        double price = configManager.getClockItemPurchasePrice();
        
        try {
            // プレイヤーの残高を確認
            org.tofu.tofunomics.models.Player playerData = playerDAO.getPlayer(player.getUniqueId());
            if (playerData == null) {
                player.sendMessage("§cプレイヤーデータが見つかりません。");
                return true;
            }
            
            double balance = playerData.getBalance();
            if (balance < price) {
                player.sendMessage(configManager.getClockItemInsufficientFundsMessage(price));
                return true;
            }
            
            // 残高から購入価格を差し引く
            playerDAO.updateBalance(player.getUniqueId(), balance - price);
            
            // 時計アイテムを付与
            clockItemManager.giveClockItem(player);
            
            player.sendMessage("§a時計を " + price + configManager.getCurrencySymbol() + " で購入しました！");
            
        } catch (Exception e) {
            player.sendMessage("§c時計の購入中にエラーが発生しました。");
            plugin.getLogger().severe("時計購入エラー: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    /**
     * 時計情報表示
     */
    private boolean handleInfoCommand(Player player) {
        double price = configManager.getClockItemPurchasePrice();
        boolean hasClockItem = clockItemManager.hasClockItem(player);
        
        player.sendMessage("§6§l=== TofuNomics時計情報 ===");
        player.sendMessage("§e購入価格: §f" + price + configManager.getCurrencySymbol());
        player.sendMessage("§e所持状態: " + (hasClockItem ? "§a所持している" : "§c未所持"));
        player.sendMessage("");
        player.sendMessage("§e機能:");
        player.sendMessage("§7- アクションバーに時刻を常時表示");
        player.sendMessage("§7- 右クリックで詳細情報を表示");
        player.sendMessage("§7- 取引所の営業状況を確認");
        player.sendMessage("");
        
        if (!hasClockItem) {
            player.sendMessage("§a購入するには: §f/clock buy");
        }
        
        player.sendMessage("§6§l======================");
        
        return true;
    }
    
    /**
     * コマンド使用方法を表示
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== 時計コマンド ===");
        sender.sendMessage("§e/clock buy §7- 時計を購入");
        sender.sendMessage("§e/clock info §7- 時計の情報を表示");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("buy", "info"));
        }
        
        return completions;
    }
}
