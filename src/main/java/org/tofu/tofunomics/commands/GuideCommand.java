package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.guide.GuideGUI;
import org.tofu.tofunomics.guide.GuideTopic;

import java.util.ArrayList;
import java.util.List;

/**
 * /guide コマンド。引数なしで遊び方ガイドのメニューを開き、
 * /guide <項目> でその項目の本を直接開く。
 */
public class GuideCommand implements CommandExecutor, TabCompleter {

    private final GuideGUI guideGUI;

    public GuideCommand(GuideGUI guideGUI) {
        this.guideGUI = guideGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            guideGUI.open(player);
            return true;
        }
        GuideTopic topic = guideGUI.findTopic(args[0]);
        if (topic == null) {
            List<String> ids = new ArrayList<>();
            for (GuideTopic t : guideGUI.getTopics()) {
                ids.add(t.getId());
            }
            player.sendMessage("§c項目が見つかりません。§7使える項目: §f" + String.join(", ", ids));
            return true;
        }
        guideGUI.openTopic(player, topic);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (GuideTopic topic : guideGUI.getTopics()) {
                if (topic.getId().toLowerCase().startsWith(prefix)) {
                    result.add(topic.getId());
                }
            }
        }
        return result;
    }
}
