package org.tofu.tofunomics.jobs.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.Arrays;
import java.util.Collections;

/**
 * 就職/辞職の確認ダイアログ GUI。
 *
 * 「はい」で {@link JobManager#joinJob} / {@link JobManager#leaveJob} を呼び、
 * 結果に応じたメッセージを送ってハブへ戻す。「いいえ」で詳細画面へ戻す。
 */
public class JobConfirmGUI {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_YES = 11;
    private static final int SLOT_NO = 15;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final JobsGUIListener listener;

    private JobsHubGUI hubGUI;
    private JobDetailGUI detailGUI;

    public JobConfirmGUI(TofuNomics plugin, ConfigManager configManager,
                         JobManager jobManager, JobsGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.listener = listener;
    }

    public void setGUIs(JobsHubGUI hubGUI, JobDetailGUI detailGUI) {
        this.hubGUI = hubGUI;
        this.detailGUI = detailGUI;
    }

    /**
     * 確認ダイアログを開く。
     *
     * @param type {@link JobsGUISession.Type#CONFIRM_JOIN} または {@link JobsGUISession.Type#CONFIRM_LEAVE}
     */
    public void open(Player player, String jobName, JobsGUISession.Type type) {
        try {
            boolean join = (type == JobsGUISession.Type.CONFIRM_JOIN);
            String displayName = jobManager.getJobDisplayName(jobName);
            Inventory gui = Bukkit.createInventory(null, SIZE,
                    join ? "§6就職の確認" : "§6辞職の確認");
            JobsGUISession session = new JobsGUISession(player.getUniqueId(), type, gui);
            session.setTargetJobName(jobName);
            render(gui, jobName, displayName, join);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("職業確認GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "職業GUIの表示に失敗しました。");
        }
    }

    private void render(Inventory gui, String jobName, String displayName, boolean join) {
        String question = join
                ? "§e本当に「" + ChatColor.stripColor(displayName) + "」に就職しますか？"
                : "§e本当に「" + ChatColor.stripColor(displayName) + "」を辞職しますか？";
        gui.setItem(SLOT_INFO, GuiUtil.createButton(JobsGUIIconMapper.getIcon(jobName),
                "§f" + displayName,
                join
                        ? Arrays.asList(question, "§7※他の職業からの転職にはレベル50が必要です")
                        : Arrays.asList(question, "§7※レベル50以上で辞職できます")));

        gui.setItem(SLOT_YES, GuiUtil.createButton(Material.LIME_WOOL, "§a§lはい",
                Collections.singletonList(join ? "§7就職を確定します" : "§7辞職を確定します")));
        gui.setItem(SLOT_NO, GuiUtil.createButton(Material.RED_WOOL, "§c§lいいえ",
                Collections.singletonList("§7前の画面へ戻ります")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = GuiUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = 0; i < SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    /**
     * クリック処理（{@link JobsGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, JobsGUISession session, int slot) {
        String jobName = session.getTargetJobName();
        if (jobName == null) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_NO) {
            // 詳細画面へ戻る
            if (detailGUI != null) {
                detailGUI.open(player, jobName);
            } else {
                player.closeInventory();
            }
            return;
        }

        if (slot == SLOT_YES) {
            player.closeInventory();
            if (session.getType() == JobsGUISession.Type.CONFIRM_JOIN) {
                handleJoin(player, jobName);
            } else {
                handleLeave(player, jobName);
            }
            if (hubGUI != null) {
                hubGUI.reopen(player);
            }
        }
    }

    private void handleJoin(Player player, String jobName) {
        JobManager.JobJoinResult result = jobManager.joinJob(player, jobName);
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                String message = configManager.getMessage("jobs.job_joined", "job", displayName);
                if (message.startsWith("メッセージが見つかりません:")) {
                    message = "&a職業「" + displayName + "」に就職しました。";
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        configManager.getMessagePrefix() + message));
                break;
            case ALREADY_HAS_JOB:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        configManager.getMessagePrefix() + configManager.getMessage("jobs.already_have_job")));
                break;
            case JOB_NOT_FOUND:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        configManager.getMessagePrefix() + configManager.getMessage("jobs.job_not_found")));
                break;
            case LEVEL_TOO_LOW:
                player.sendMessage(ChatColor.RED + "転職するには現在の職業レベルが50以上必要です。");
                break;
            case MAX_JOBS_REACHED:
                int maxJobs = configManager.getMaxJobsPerPlayer();
                player.sendMessage(ChatColor.RED + "同時に就ける職業数の上限(" + maxJobs + ")に達しています。");
                break;
            case DATABASE_ERROR:
            default:
                player.sendMessage(ChatColor.RED + "データベースエラーが発生しました。しばらくしてから再度お試しください。");
                break;
        }
    }

    private void handleLeave(Player player, String jobName) {
        // 就職状態の確認は leaveJob 内（NO_SUCH_JOB）に委ね、結果のみで分岐する
        JobManager.JobLeaveResult result = jobManager.leaveJob(player, jobName);
        switch (result) {
            case SUCCESS:
                String displayName = jobManager.getJobDisplayName(jobName);
                String message = configManager.getMessage("jobs.job_left", "job", displayName);
                if (message.startsWith("メッセージが見つかりません:")) {
                    message = "&a職業「" + displayName + "」を辞職しました。";
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        configManager.getMessagePrefix() + message));
                break;
            case LEVEL_TOO_LOW:
                player.sendMessage(ChatColor.RED + "レベル50未満の職業は辞職できません。レベル50に達してから辞職してください。");
                break;
            case DAILY_LIMIT_EXCEEDED:
                player.sendMessage(ChatColor.RED + "本日はすでに職業を変更しています。明日再度お試しください。");
                break;
            case NO_SUCH_JOB:
            case DATABASE_ERROR:
            default:
                player.sendMessage(ChatColor.RED + "職業の辞職に失敗しました。");
                break;
        }
    }
}
