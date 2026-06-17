package org.tofu.tofunomics.jobs.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 個別職業の詳細 GUI（list + info 統合）。
 *
 * 職業の説明・最大レベル・収入倍率・経験値倍率・自分のレベルを表示し、
 * 未就職なら「就職する」、就職中なら「辞職する」ボタンを表示する。
 * 就職/辞職は {@link JobConfirmGUI} の確認を経て {@link JobManager} を呼ぶ。
 */
public class JobDetailGUI {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_JOIN = 11;
    private static final int SLOT_LEAVE = 15;
    private static final int SLOT_GUIDE = 22;   // 就職中のみ表示（ガイドブック入手）
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final JobsGUIListener listener;

    private JobsHubGUI hubGUI;
    private JobConfirmGUI confirmGUI;

    public JobDetailGUI(TofuNomics plugin, ConfigManager configManager,
                        JobManager jobManager, JobsGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.listener = listener;
    }

    public void setGUIs(JobsHubGUI hubGUI, JobConfirmGUI confirmGUI) {
        this.hubGUI = hubGUI;
        this.confirmGUI = confirmGUI;
    }

    /**
     * 指定職業の詳細 GUI を開く。
     */
    public void open(Player player, String jobName) {
        try {
            String displayName = jobManager.getJobDisplayName(jobName);
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6職業: " + ChatColor.stripColor(displayName));
            JobsGUISession session = new JobsGUISession(
                    player.getUniqueId(), JobsGUISession.Type.JOB_DETAIL, gui);
            session.setTargetJobName(jobName);
            render(player, gui, jobName);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("職業詳細GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "職業GUIの表示に失敗しました。");
        }
    }

    private void render(Player player, Inventory gui, String jobName) {
        String displayName = jobManager.getJobDisplayName(jobName);
        String description = configManager.getJobDescription(jobName);
        int maxLevel = configManager.getJobMaxLevel(jobName);
        double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
        double expMultiplier = configManager.getJobExpMultiplier(jobName);
        boolean employed = jobManager.hasJob(player, jobName);
        boolean advanced = configManager.isAdvancedJob(jobName);
        // 上級職業が未解禁か（未就職かつLv50到達経験なし）
        boolean advancedLocked = advanced && !employed && !jobManager.hasReachedLevel50(player);

        // 詳細アイコン
        List<String> infoLore = new ArrayList<>();
        if (advanced) {
            infoLore.add("§6§l【上級職業】");
        }
        if (description != null && !description.isEmpty()) {
            infoLore.add("§7" + description);
        }
        infoLore.add("");
        infoLore.add("§b最大レベル: §f" + maxLevel);
        infoLore.add("§b収入倍率: §f" + String.format("%.1f", incomeMultiplier) + "x");
        infoLore.add("§b経験値倍率: §f" + String.format("%.1f", expMultiplier) + "x");
        if (employed) {
            PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
            if (playerJob != null) {
                int level = playerJob.getLevel();
                double experience = playerJob.getExperience();
                int requiredExp = configManager.calculateRequiredExperience(level + 1);
                infoLore.add("");
                infoLore.add("§a現在のレベル: §f" + level + " §7(経験値: " + (int) experience + "/" + requiredExp + ")");
            }
        }
        gui.setItem(SLOT_INFO, GuiUtil.createButton(JobsGUIIconMapper.getIcon(jobName),
                (employed ? "§a§l" : "§f§l") + displayName + " §7(" + jobName + ")", infoLore));

        // 就職/辞職ボタン
        if (employed) {
            gui.setItem(SLOT_LEAVE, GuiUtil.createButton(Material.RED_BED, "§c§l辞職する",
                    java.util.Arrays.asList(
                            "§7この職業を辞職します",
                            "§7※レベル50以上で辞職できます",
                            "§eクリックで確認画面へ")));
            // 就職中のみ、ガイドブックが用意されていれば入手ボタンを表示
            if (configManager.isJobGuideBookEnabled(jobName)
                    && !configManager.getJobGuideBookPages(jobName).isEmpty()) {
                gui.setItem(SLOT_GUIDE, GuiUtil.createButton(Material.WRITTEN_BOOK, "§6§lガイドブックを入手",
                        java.util.Arrays.asList(
                                "§7この職業のガイドブックを受け取ります",
                                "§7仕事内容・稼ぎ方・経験値の上げ方を確認できます",
                                "§eクリックで入手")));
            }
        } else if (advancedLocked) {
            gui.setItem(SLOT_JOIN, GuiUtil.createButton(Material.BARRIER, "§c§l未解禁",
                    java.util.Arrays.asList(
                            "§7この職業は上級職業です",
                            "§7いずれかの職業でレベル50に到達すると",
                            "§7就職できるようになります")));
        } else {
            gui.setItem(SLOT_JOIN, GuiUtil.createButton(Material.LIME_DYE, "§a§l就職する",
                    java.util.Arrays.asList(
                            "§7この職業に就職します",
                            "§7※他の職業からの転職にはレベル50が必要です",
                            "§eクリックで確認画面へ")));
        }

        // 戻る・閉じる
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

    /**
     * クリック処理（{@link JobsGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, JobsGUISession session, int slot) {
        String jobName = session.getTargetJobName();
        switch (slot) {
            case SLOT_JOIN:
                // 未就職時のみ就職ボタンを表示しているが、状態をここでも再確認する
                if (jobName != null && confirmGUI != null && !jobManager.hasJob(player, jobName)) {
                    // 上級職業の未解禁時は確認画面を開かない（joinJob 側でも弾かれるが二重ガード）
                    if (configManager.isAdvancedJob(jobName) && !jobManager.hasReachedLevel50(player)) {
                        player.sendMessage(ChatColor.RED + "「" + jobManager.getJobDisplayName(jobName)
                                + "」は上級職業です。いずれかの職業でレベル50に到達すると就職できます。");
                        break;
                    }
                    confirmGUI.open(player, jobName, JobsGUISession.Type.CONFIRM_JOIN);
                }
                break;
            case SLOT_LEAVE:
                // 就職中のみ辞職ボタンを表示しているが、状態をここでも再確認する
                if (jobName != null && confirmGUI != null && jobManager.hasJob(player, jobName)) {
                    confirmGUI.open(player, jobName, JobsGUISession.Type.CONFIRM_LEAVE);
                }
                break;
            case SLOT_GUIDE:
                // 就職中の職業のガイドブックのみ入手可能
                if (jobName != null && jobManager.hasJob(player, jobName)
                        && configManager.isJobGuideBookEnabled(jobName)) {
                    giveGuideBook(player, jobName);
                }
                break;
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

    /**
     * 指定職業のガイドブックを生成してプレイヤーに渡す。
     * インベントリに空きがなければドロップせず警告する。
     */
    private void giveGuideBook(Player player, String jobName) {
        List<String> pageContents = configManager.getJobGuideBookPages(jobName);
        if (pageContents.isEmpty()) {
            player.sendMessage(ChatColor.RED + "この職業のガイドブックは用意されていません。");
            return;
        }

        ItemStack book = createGuideBook(jobName, pageContents);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        if (!leftover.isEmpty()) {
            player.sendMessage(ChatColor.RED + "インベントリに空きがありません。整理してから再度お試しください。");
            return;
        }

        String displayName = jobManager.getJobDisplayName(jobName);
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "「" + ChatColor.stripColor(displayName)
                + "」のガイドブックを入手しました。右クリックで読めます。");
    }

    /**
     * config の guide_book 設定から WRITTEN_BOOK を生成する。色コード（&）は変換する。
     */
    private ItemStack createGuideBook(String jobName, List<String> pageContents) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(ChatColor.translateAlternateColorCodes('&',
                    configManager.getJobGuideBookTitle(jobName)));
            meta.setAuthor(ChatColor.translateAlternateColorCodes('&',
                    configManager.getJobGuideBookAuthor(jobName)));
            List<String> pages = new ArrayList<>();
            for (String page : pageContents) {
                pages.add(ChatColor.translateAlternateColorCodes('&', page));
            }
            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        return book;
    }
}
