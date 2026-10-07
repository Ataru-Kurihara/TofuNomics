package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.quests.JobQuestManager;

import java.util.Arrays;
import java.util.List;

/**
 * /quest コマンド。
 * コマンドのクエストは廃止したので、クエストの受け方（街の「依頼受付所」）を案内するだけにする。
 * サブコマンド（list / accept / progress など）を付けても同じ案内を出す。
 */
public class JobQuestCommand implements CommandExecutor {

    private final ConfigManager configManager;

    public JobQuestCommand(ConfigManager configManager, JobQuestManager jobQuestManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 経済ワールドの外（ロビーなど）では、ほかのコマンドと同じ案内を出して止める
        if (EconomyWorldGuard.blockIfOutside(sender, configManager)) {
            return true;
        }
        for (String line : guidanceLines()) {
            sender.sendMessage(line);
        }
        return true;
    }

    /**
     * クエストの受け方の案内文。
     */
    static List<String> guidanceLines() {
        return Arrays.asList(
                ChatColor.GOLD + "▬▬▬▬▬▬ クエストの受け方 ▬▬▬▬▬▬",
                ChatColor.WHITE + "クエストは、街の" + ChatColor.AQUA + "「依頼受付所」" + ChatColor.WHITE + "で受けられます。",
                ChatColor.GRAY + "依頼受付所の人を右クリックすると、受けられる依頼の一覧が開きます。",
                ChatColor.GRAY + "（/quest コマンドでのクエストは終了しました）",
                ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}
