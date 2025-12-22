package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * /citymap コマンド実装
 * プレイヤーに中心都市の案内地図を配布
 * ImageOnMapプラグインと連携して地図アイテムを提供
 */
public class CityMapCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    
    public CityMapCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // プレイヤーのみ実行可能
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 設定から地図IDを取得
        String mapId = configManager.getCityMapId();
        
        if (mapId == null || mapId.isEmpty()) {
            player.sendMessage("§c地図がまだ設定されていません。管理者に連絡してください。");
            return true;
        }
        
        // クールダウンチェック（オプション：連発防止）
        // 今回は実装を簡略化
        
        // ImageOnMapの地図を配布
        // ImageOnMapプラグインのgivemapコマンドを使用
        try {
            // コンソールから実行する形でgivemapコマンドを実行
            String givemapCommand = "givemap " + player.getName() + " " + mapId;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), givemapCommand);
            
            // 成功メッセージ
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§6§l  " + configManager.getCityMapTitle());
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage("§a✓ 中心都市の案内地図を入手しました！");
            player.sendMessage("§7  インベントリを確認してください");
            player.sendMessage("");
            player.sendMessage("§e§l主要施設:");
            player.sendMessage("§7  - §b🏛️ 銀行§7: お金の預け入れ・引き出し");
            player.sendMessage("§7  - §b🏪 取引所§7: アイテムの売買");
            player.sendMessage("§7  - §b🍖 食料店§7: 食料の購入");
            player.sendMessage("§7  - §b🏘️ 商店街§7: プレイヤー間取引");
            player.sendMessage("");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
            
        } catch (Exception e) {
            player.sendMessage("§c地図の配布に失敗しました。");
            player.sendMessage("§cImageOnMapプラグインが導入されているか確認してください。");
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
}
