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
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自分の職業ステータス GUI。
 *
 * 就職中の各職業について、レベル・経験値・次レベルまでの必要経験値・進捗バーを表示する。
 */
public class JobStatsGUI {

    private static final int SIZE = 27;
    private static final int SLOT_JOB_START = 9;
    private static final int SLOT_JOB_END = 16;
    private static final int SLOT_INFO = 4;  // 就職していない場合の案内表示にのみ使用
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final JobsGUIListener listener;

    private JobsHubGUI hubGUI;

    public JobStatsGUI(TofuNomics plugin, ConfigManager configManager,
                       JobManager jobManager, JobsGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.listener = listener;
    }

    public void setGUIs(JobsHubGUI hubGUI) {
        this.hubGUI = hubGUI;
    }

    /**
     * ステータス GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6職業ステータス");
            JobsGUISession session = new JobsGUISession(
                    player.getUniqueId(), JobsGUISession.Type.STATS, gui);
            render(player, gui);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("職業ステータスGUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "職業GUIの表示に失敗しました。");
        }
    }

    private void render(Player player, Inventory gui) {
        List<PlayerJob> playerJobs = jobManager.getPlayerJobs(player);

        if (playerJobs.isEmpty()) {
            gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.PAPER, "§7現在就職していません",
                    Collections.singletonList("§7職業一覧から就職してください")));
        } else {
            int slot = SLOT_JOB_START;
            for (PlayerJob playerJob : playerJobs) {
                if (slot > SLOT_JOB_END) {
                    break;
                }
                Job job = jobManager.getJobById(playerJob.getJobId());
                if (job == null) {
                    continue;
                }
                gui.setItem(slot, buildStatsIcon(job, playerJob));
                slot++;
            }
        }

        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.ARROW, "§e戻る",
                Collections.singletonList("§7職業一覧へ戻ります")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

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

    private ItemStack buildStatsIcon(Job job, PlayerJob playerJob) {
        String jobName = job.getName();
        String displayName = job.getDisplayName();
        int level = playerJob.getLevel();
        double experience = playerJob.getExperience();
        int requiredExp = configManager.calculateRequiredExperience(level + 1);

        List<String> lore = new ArrayList<>();
        lore.add("§bレベル: §f" + level);
        lore.add("§b経験値: §f" + (int) experience + " / " + requiredExp);
        lore.add("§7" + buildProgressBar(experience, requiredExp));

        return GuiUtil.createButton(JobsGUIIconMapper.getIcon(jobName),
                "§a§l" + displayName + " §7(" + jobName + ")", lore);
    }

    /**
     * 経験値の進捗バーを生成する（20 マス）。
     */
    private String buildProgressBar(double experience, int requiredExp) {
        int total = 20;
        double ratio = requiredExp > 0 ? Math.min(1.0, experience / requiredExp) : 0.0;
        int filled = (int) Math.round(ratio * total);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < total; i++) {
            if (i == filled) {
                sb.append("§7");
            }
            sb.append("■");
        }
        sb.append(" §f").append((int) Math.round(ratio * 100)).append("%");
        return sb.toString();
    }

    /**
     * クリック処理（{@link JobsGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, JobsGUISession session, int slot) {
        switch (slot) {
            case SLOT_BACK:
                if (hubGUI != null) {
                    hubGUI.open(player);
                }
                break;
            case SLOT_CLOSE:
                player.closeInventory();
                break;
            default:
                break;
        }
    }
}
