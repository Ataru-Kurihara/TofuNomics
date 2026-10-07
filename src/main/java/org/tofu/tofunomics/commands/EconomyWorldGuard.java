package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * 経済ワールドの外（ロビーなど）で打たれたコマンドを、案内を出して止めるための共通処理。
 */
public final class EconomyWorldGuard {

    static final String MESSAGE = ChatColor.RED + "このワールドではTofuNomicsの機能を利用できません。"
            + ChatColor.GRAY + "TofuNomicsワールドに入ってから実行してください。";

    private EconomyWorldGuard() {
    }

    /**
     * 実行者が経済ワールドの外にいるプレイヤーなら、案内を出して true を返す（呼び出し側は処理を止める）。
     * コンソールなどプレイヤー以外は止めない。
     */
    public static boolean blockIfOutside(CommandSender sender, ConfigManager configManager) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;
        if (isAllowed(configManager.isEconomyEnabledInWorld(player.getWorld().getName()))) {
            return false;
        }
        player.sendMessage(MESSAGE);
        return true;
    }

    /** そのワールドで経済機能が有効なら実行してよい */
    static boolean isAllowed(boolean economyEnabledInWorld) {
        return economyEnabledInWorld;
    }
}
