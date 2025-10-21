package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.rules.RulesManager;

/**
 * /rules コマンド実装
 * プレイヤーがいつでもルールを確認できるコマンド
 */
public class RulesCommand implements CommandExecutor {
    
    private final RulesManager rulesManager;
    
    public RulesCommand(RulesManager rulesManager) {
        this.rulesManager = rulesManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // プレイヤーのみ実行可能
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }
        
        Player player = (Player) sender;
        
        // reset サブコマンド
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            // 権限チェック
            if (!player.hasPermission("tofunomics.rules.reset")) {
                player.sendMessage("§cこのコマンドを実行する権限がありません。");
                return true;
            }
            
            // ルール同意状態をリセット
            boolean success = rulesManager.resetPlayerRules(player.getUniqueId());
            
            if (success) {
                player.sendMessage("§aルール同意状態をリセットしました。次回ログイン時にルールが表示されます。");
            } else {
                player.sendMessage("§cルール同意状態のリセットに失敗しました。");
            }
            
            return true;
        }
        
        // 引数なし（またはreset以外）: ルールGUIを開く
        rulesManager.getRulesGUI().openRulesGUI(player, 1);
        
        return true;
    }
}
