package org.tofu.tofunomics.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * /rules コマンド実装
 * プレイヤーがいつでもルールを確認できるコマンド
 * 外部サイトのURLをクリック可能な形式でチャットに表示
 */
public class RulesCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    
    public RulesCommand(ConfigManager configManager) {
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
        String rulesUrl = configManager.getRulesUrl();
        
        // ヘッダー
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§6§l  TofuNomics サーバールール");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        
        // クリック可能なURL
        TextComponent message = new TextComponent("§a§l➤ ルールを確認する: ");
        TextComponent urlLink = new TextComponent("§b§n" + rulesUrl);
        urlLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, rulesUrl));
        message.addExtra(urlLink);
        player.spigot().sendMessage(message);
        
        player.sendMessage("");
        player.sendMessage("§7§o※ 上記リンクをクリックしてブラウザで開いてください");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━");
        
        return true;
    }
}
