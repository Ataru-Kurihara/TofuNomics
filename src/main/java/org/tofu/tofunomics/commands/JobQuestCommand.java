package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.quests.JobQuestManager;
import org.tofu.tofunomics.quests.JobQuest;
import org.tofu.tofunomics.quests.PlayerQuestProgress;

import java.util.List;

/**
 * 職業クエストコマンド
 */
public class JobQuestCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final JobQuestManager jobQuestManager;
    
    public JobQuestCommand(ConfigManager configManager, JobQuestManager jobQuestManager) {
        this.configManager = configManager;
        this.jobQuestManager = jobQuestManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showActiveQuests(player);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "list":
                return handleQuestList(player, args);
            case "accept":
                return handleQuestAccept(player, args);
            case "progress":
                return handleQuestProgress(player);
            default:
                sendHelpMessage(player);
                return true;
        }
    }
    
    private void showActiveQuests(Player player) {
        List<PlayerQuestProgress> activeQuests = jobQuestManager.getActiveQuests(player);
        
        if (activeQuests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "現在進行中のクエストはありません。");
            player.sendMessage(ChatColor.GRAY + "/quest list <職業名> で利用可能なクエストを確認できます。");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "==================== 進行中のクエスト ====================");
        
        for (PlayerQuestProgress progress : activeQuests) {
            // クエスト情報を表示（実装は簡略化）
            player.sendMessage(ChatColor.GREEN + "クエスト ID: " + progress.getQuestId() + 
                " - 進行度: " + progress.getCurrentProgress());
        }
        
        player.sendMessage(ChatColor.GOLD + "========================================================");
    }
    
    private boolean handleQuestList(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /quest list <職業名>");
            return true;
        }
        
        String jobName = args[1].toLowerCase();
        List<JobQuest> availableQuests = jobQuestManager.getAvailableQuests(player, jobName);
        
        if (availableQuests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + configManager.getJobDisplayName(jobName) + 
                " の利用可能なクエストはありません。");
            return true;
        }
        
        player.sendMessage(ChatColor.GOLD + "==================== " + 
            configManager.getJobDisplayName(jobName) + " クエスト ====================");
        
        for (JobQuest quest : availableQuests) {
            player.sendMessage(String.format("%sID:%d %s[%s] %s%s", 
                ChatColor.YELLOW, quest.getQuestId(),
                ChatColor.GREEN, quest.getDifficultyString(),
                ChatColor.WHITE, quest.getQuestName()));
            
            player.sendMessage(ChatColor.GRAY + "  " + quest.getDescription());
            player.sendMessage(String.format("  %s報酬: %s%.0f経験値 + %.0f%s", 
                ChatColor.AQUA, ChatColor.WHITE,
                quest.getExperienceReward(), quest.getIncomeReward(),
                configManager.getCurrencySymbol()));
        }
        
        player.sendMessage(ChatColor.GOLD + "====================================================");
        
        return true;
    }
    
    private boolean handleQuestAccept(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "使用法: /quest accept <クエストID>");
            return true;
        }
        
        try {
            int questId = Integer.parseInt(args[1]);
            
            if (jobQuestManager.acceptQuest(player, questId)) {
                player.sendMessage(ChatColor.GREEN + "クエストを受諾しました。");
            } else {
                player.sendMessage(ChatColor.RED + "クエストの受諾に失敗しました。");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "無効なクエストIDです。");
        }
        
        return true;
    }
    
    private boolean handleQuestProgress(Player player) {
        showActiveQuests(player);
        return true;
    }
    
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "==================== クエストコマンド ====================");
        player.sendMessage(ChatColor.YELLOW + "/quest - 進行中のクエストを表示");
        player.sendMessage(ChatColor.YELLOW + "/quest list <職業名> - 利用可能なクエスト一覧");
        player.sendMessage(ChatColor.YELLOW + "/quest accept <ID> - クエストを受諾");
        player.sendMessage(ChatColor.YELLOW + "/quest progress - 進行状況を確認");
        player.sendMessage(ChatColor.GOLD + "=========================================================");
    }
}