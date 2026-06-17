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
import org.tofu.tofunomics.models.PlayerJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 職業の統合ハブ GUI。
 *
 * {@code /jobs}（無引数）で開く入口。職業アイコンをグリッド状に並べ、クリックで詳細（就職/辞職）へ遷移する。
 * 最下段に自分のステータス・閉じるボタンを配置する。
 */
public class JobsHubGUI {

    private static final int SIZE = 27;
    private static final int SLOT_JOB_START = 9;   // 9..16 に職業アイコンを配置
    private static final int SLOT_JOB_END = 16;
    private static final int SLOT_STATS = 22;
    private static final int SLOT_CLOSE = 26;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final JobsGUIListener listener;

    // 遷移先 GUI（循環依存回避のためセッターで後注入）
    private JobDetailGUI detailGUI;
    private JobStatsGUI statsGUI;

    public JobsHubGUI(TofuNomics plugin, ConfigManager configManager,
                      JobManager jobManager, JobsGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.listener = listener;
    }

    public void setGUIs(JobDetailGUI detailGUI, JobStatsGUI statsGUI) {
        this.detailGUI = detailGUI;
        this.statsGUI = statsGUI;
    }

    /**
     * ハブ GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6職業");
            JobsGUISession session = new JobsGUISession(
                    player.getUniqueId(), JobsGUISession.Type.HUB, gui);
            render(player, session, gui);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("職業ハブGUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "職業GUIの表示に失敗しました。");
        }
    }

    private void render(Player player, JobsGUISession session, Inventory gui) {
        gui.clear();
        session.clearSlotJobNames();

        String[] jobNames = jobManager.getJobNames();
        int slot = SLOT_JOB_START;
        for (String jobName : jobNames) {
            if (slot > SLOT_JOB_END) {
                break; // 8 枠を超える職業は表示しない（想定外）
            }
            gui.setItem(slot, buildJobIcon(player, jobName));
            session.putSlotJobName(slot, jobName);
            slot++;
        }

        // 最下段: ステータス・閉じる
        gui.setItem(SLOT_STATS, GuiUtil.createButton(Material.WRITABLE_BOOK, "§b§l自分のステータス",
                java.util.Arrays.asList("§7就職中の職業のレベル・経験値を確認します", "§eクリックで開く")));
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

    /**
     * ハブに並べる職業アイコンを生成する。就職中ならレベルを表示する。
     */
    private ItemStack buildJobIcon(Player player, String jobName) {
        String displayName = jobManager.getJobDisplayName(jobName);
        String description = configManager.getJobDescription(jobName);
        int maxLevel = configManager.getJobMaxLevel(jobName);
        double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
        boolean employed = jobManager.hasJob(player, jobName);
        boolean advanced = configManager.isAdvancedJob(jobName);
        // 上級職業が未解禁か（未就職かつLv50到達経験なし）
        boolean advancedLocked = advanced && !employed && !jobManager.hasReachedLevel50(player);

        List<String> lore = new ArrayList<>();
        if (advanced) {
            lore.add("§6§l【上級職業】");
        }
        if (description != null && !description.isEmpty()) {
            lore.add("§7" + description);
        }
        lore.add("§b最大レベル: §f" + maxLevel);
        lore.add("§b収入倍率: §f" + String.format("%.1f", incomeMultiplier) + "x");
        lore.add("");
        if (employed) {
            PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
            int currentLevel = playerJob != null ? playerJob.getLevel() : 0;
            lore.add("§a現在就職中 Lv." + currentLevel);
            lore.add("§eクリックで詳細・辞職");
        } else if (advancedLocked) {
            lore.add("§c§l未解禁");
            lore.add("§7いずれかの職業でLv50に到達すると就職できます");
        } else {
            lore.add("§eクリックで詳細・就職");
            lore.add("§7※転職にはレベル50が必要です");
        }

        Material icon = advancedLocked ? Material.BARRIER : JobsGUIIconMapper.getIcon(jobName);
        String name = (employed ? "§a§l" : (advancedLocked ? "§c§l" : "§f§l")) + displayName + " §7(" + jobName + ")";
        return GuiUtil.createButton(icon, name, lore);
    }

    /**
     * クリック処理（{@link JobsGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, JobsGUISession session, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_STATS) {
            if (statsGUI != null) {
                statsGUI.open(player);
            }
            return;
        }
        // 職業アイコン
        String jobName = session.getJobNameAtSlot(slot);
        if (jobName != null && detailGUI != null) {
            detailGUI.open(player, jobName);
        }
    }

    /**
     * 1 tick 後にハブ GUI を再表示する（closeInventory 直後の再 open を避ける）。
     */
    public void reopen(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        }, 1L);
    }
}
