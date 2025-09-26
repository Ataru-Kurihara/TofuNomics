package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.stats.JobStatsManager;

/**
 * 職業統計表示コマンド
 */
public class JobStatsCommand implements CommandExecutor {
    
    private final JobStatsManager jobStatsManager;
    
    public JobStatsCommand(JobStatsManager jobStatsManager) {
        this.jobStatsManager = jobStatsManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // 全職業の統計を表示
            jobStatsManager.showAllJobStats(player);
        } else if (args.length == 1) {
            String jobName = args[0].toLowerCase();
            
            // 特定職業の詳細統計を表示
            jobStatsManager.showJobStats(player, jobName);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            String jobName = args[1].toLowerCase();
            
            // 職業別ランキングを表示
            jobStatsManager.showJobTopRanking(player, jobName, 10);
        } else {
            sendHelpMessage(player);
        }
        
        return true;
    }
    
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "==================== 職業統計コマンド ====================");
        player.sendMessage(ChatColor.YELLOW + "/jobstats - 全職業の統計を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobstats <職業名> - 指定職業の詳細統計を表示");
        player.sendMessage(ChatColor.YELLOW + "/jobstats top <職業名> - 指定職業のランキングを表示");
        player.sendMessage(ChatColor.GOLD + "=======================================================");
    }
}