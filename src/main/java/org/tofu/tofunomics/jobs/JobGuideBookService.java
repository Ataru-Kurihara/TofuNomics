package org.tofu.tofunomics.jobs;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 職業ガイドブック（WRITTEN_BOOK）の生成・配布を担う共通サービス。
 *
 * GUI（{@link org.tofu.tofunomics.jobs.gui.JobDetailGUI}）とコマンド
 * （{@link org.tofu.tofunomics.commands.JobsCommand}）の両方から呼ばれる。
 * config の {@code jobs.job_settings.<job>.guide_book.*} を読み取り、
 * 色コード（&）を変換して本を作成する。
 */
public class JobGuideBookService {

    private final ConfigManager configManager;
    private final JobManager jobManager;

    public JobGuideBookService(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    /**
     * 指定職業のガイドブックを生成してプレイヤーに渡す。
     * インベントリに空きがなければドロップせず警告する。
     *
     * @return 入手に成功した場合 true。未用意・空き不足の場合はメッセージを出して false。
     */
    public boolean giveGuideBook(Player player, String jobName) {
        List<String> pageContents = configManager.getJobGuideBookPages(jobName);
        if (pageContents.isEmpty()) {
            player.sendMessage(ChatColor.RED + "この職業のガイドブックは用意されていません。");
            return false;
        }

        ItemStack book = createGuideBook(jobName, pageContents);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        if (!leftover.isEmpty()) {
            player.sendMessage(ChatColor.RED + "インベントリに空きがありません。整理してから再度お試しください。");
            return false;
        }

        String displayName = jobManager.getJobDisplayName(jobName);
        player.sendMessage(ChatColor.GREEN + "「" + ChatColor.stripColor(displayName)
                + "」のガイドブックを入手しました。右クリックで読めます。");
        return true;
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
