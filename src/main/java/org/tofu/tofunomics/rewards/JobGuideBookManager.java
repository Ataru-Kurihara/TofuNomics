package org.tofu.tofunomics.rewards;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.TofuNomics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 職業ガイドブック管理システム
 * 各職業に就職した際にガイドブック（Written Book）を配布する
 */
public class JobGuideBookManager {
    
    private final ConfigManager configManager;
    
    // ガイドブック識別用CustomModelData
    private static final int GUIDE_BOOK_MODEL_DATA = 2001;
    
    // TofuNomicsガイドブック識別用Lore接頭辞
    private static final String GUIDE_BOOK_IDENTIFIER = "§8[TofuNomics JobGuide]";
    
    // 配布済みプレイヤーのキャッシュ（サーバー再起動でリセット）
    // 永続化が必要な場合はDBに保存を検討
    private final Map<UUID, List<String>> distributedBooks;
    
    public JobGuideBookManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.distributedBooks = new HashMap<>();
    }
    
    /**
     * FileConfigurationを取得するヘルパーメソッド
     */
    private FileConfiguration getConfig() {
        return TofuNomics.getInstance().getConfig();
    }
    
    /**
     * ガイドブック機能が有効かどうか
     */
    public boolean isEnabled() {
        return getConfig().getBoolean("guide_book_settings.enabled", true);
    }
    
    /**
     * 指定職業のガイドブック機能が有効かどうか
     */
    public boolean isJobGuideEnabled(String jobName) {
        return getConfig().getBoolean("jobs.job_settings." + jobName + ".guide_book.enabled", true);
    }
    
    /**
     * 職業ガイドブックを作成する
     * @param jobName 職業名（英語キー）
     * @return 作成されたガイドブック、設定がない場合はnull
     */
    public ItemStack createJobGuideBook(String jobName) {
        if (!isEnabled() || !isJobGuideEnabled(jobName)) {
            return null;
        }
        
        String basePath = "jobs.job_settings." + jobName + ".guide_book";
        
        // タイトルと著者を取得
        String title = getConfig().getString(basePath + ".title", "&6&l職業ガイドブック");
        String author = getConfig().getString(basePath + ".author", "TofuNomics職業組合");
        
        // ページ内容を取得
        List<String> pages = getConfig().getStringList(basePath + ".pages");
        if (pages.isEmpty()) {
            // デフォルトページを生成
            pages = generateDefaultPages(jobName);
        }
        
        // Written Bookを作成
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        
        if (bookMeta == null) {
            return null;
        }
        
        // タイトルと著者を設定（カラーコード変換）
        bookMeta.setTitle(ChatColor.translateAlternateColorCodes('&', title));
        bookMeta.setAuthor(ChatColor.translateAlternateColorCodes('&', author));
        
        // ページを追加（カラーコード変換）
        for (String page : pages) {
            String coloredPage = ChatColor.translateAlternateColorCodes('&', page);
            bookMeta.addPage(coloredPage);
        }
        
        // ガイドブック識別用のLoreを設定
        List<String> lore = new ArrayList<>();
        lore.add(GUIDE_BOOK_IDENTIFIER);
        lore.add("§7職業: " + jobName);
        bookMeta.setLore(lore);
        
        // CustomModelDataを設定（識別用）
        bookMeta.setCustomModelData(GUIDE_BOOK_MODEL_DATA);
        
        book.setItemMeta(bookMeta);
        
        return book;
    }
    
    /**
     * デフォルトのガイドブックページを生成
     */
    private List<String> generateDefaultPages(String jobName) {
        List<String> pages = new ArrayList<>();
        
        String displayName = getConfig().getString(
            "jobs.job_settings." + jobName + ".display_name", 
            jobName
        );
        String description = getConfig().getString(
            "jobs.job_settings." + jobName + ".description", 
            "職業の説明がありません"
        );
        
        // ページ1: 表紙・概要
        StringBuilder page1 = new StringBuilder();
        page1.append("§0§l【").append(displayName).append("】\n\n");
        page1.append("§0ようこそ！\n");
        page1.append("この本は").append(displayName).append("として\n");
        page1.append("活動するための\n");
        page1.append("ガイドブックです。\n\n");
        page1.append("§8").append(description);
        pages.add(page1.toString());
        
        // ページ2: 基本情報
        StringBuilder page2 = new StringBuilder();
        page2.append("§0§l【基本情報】\n\n");
        page2.append("§0この職業では\n");
        page2.append("以下の活動で\n");
        page2.append("報酬と経験値を\n");
        page2.append("獲得できます。\n\n");
        page2.append("詳細はゲーム内で\n");
        page2.append("/jobs info\n");
        page2.append("を確認してください。");
        pages.add(page2.toString());
        
        // ページ3: コマンド一覧
        StringBuilder page3 = new StringBuilder();
        page3.append("§0§l【便利なコマンド】\n\n");
        page3.append("§0/jobs stats\n");
        page3.append("§8→ 職業の統計情報\n\n");
        page3.append("§0/jobs info\n");
        page3.append("§8→ 職業の詳細\n\n");
        page3.append("§0/jobs guide\n");
        page3.append("§8→ ガイドブック再取得");
        pages.add(page3.toString());
        
        return pages;
    }
    
    /**
     * プレイヤーにガイドブックを配布する
     * @param player 配布対象プレイヤー
     * @param jobName 職業名
     * @return 配布成功ならtrue
     */
    public boolean giveGuideBook(Player player, String jobName) {
        if (!isEnabled()) {
            return false;
        }
        
        // 重複配布チェック
        boolean preventDuplicate = getConfig().getBoolean(
            "guide_book_settings.prevent_duplicate", true
        );
        
        if (preventDuplicate && hasGuideBookInInventory(player, jobName)) {
            // 既にガイドブックを持っている場合はスキップ
            return false;
        }
        
        ItemStack guideBook = createJobGuideBook(jobName);
        if (guideBook == null) {
            return false;
        }
        
        // インベントリに空きがあるか確認
        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(guideBook);
        
        if (!leftOver.isEmpty()) {
            // インベントリが満杯
            boolean dropWhenFull = getConfig().getBoolean(
                "guide_book_settings.drop_when_full", true
            );
            
            if (dropWhenFull) {
                // 足元にドロップ
                player.getWorld().dropItemNaturally(player.getLocation(), guideBook);
                player.sendMessage(ChatColor.YELLOW + "インベントリが満杯のため、ガイドブックを足元にドロップしました。");
            } else {
                return false;
            }
        }
        
        // 配布メッセージを送信
        String displayName = getConfig().getString(
            "jobs.job_settings." + jobName + ".display_name", 
            jobName
        );
        String message = getConfig().getString(
            "guide_book_settings.give_message",
            "&a【職業ガイド】&f%job%のガイドブックを受け取りました！"
        );
        message = message.replace("%job%", displayName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        
        // 配布記録を更新
        recordDistribution(player.getUniqueId(), jobName);
        
        return true;
    }
    
    /**
     * プレイヤーがインベントリ内に指定職業のガイドブックを持っているか確認
     */
    public boolean hasGuideBookInInventory(Player player, String jobName) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isGuideBookForJob(item, jobName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * アイテムがTofuNomicsのガイドブックかどうか判定
     */
    public boolean isGuideBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        
        return lore.get(0).equals(GUIDE_BOOK_IDENTIFIER);
    }
    
    /**
     * アイテムが指定職業のガイドブックかどうか判定
     */
    public boolean isGuideBookForJob(ItemStack item, String jobName) {
        if (!isGuideBook(item)) {
            return false;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) {
            return false;
        }
        
        String jobLine = lore.get(1);
        return jobLine.equals("§7職業: " + jobName);
    }
    
    /**
     * 配布記録を登録
     */
    private void recordDistribution(UUID playerId, String jobName) {
        distributedBooks.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (!distributedBooks.get(playerId).contains(jobName)) {
            distributedBooks.get(playerId).add(jobName);
        }
    }
    
    /**
     * このセッションでガイドブックを配布済みかどうか確認
     * （サーバー再起動でリセットされる）
     */
    public boolean hasDistributedThisSession(UUID playerId, String jobName) {
        List<String> jobs = distributedBooks.get(playerId);
        return jobs != null && jobs.contains(jobName);
    }
    
    /**
     * ガイドブックの再取得（/jobs guide用）
     * 重複チェックをスキップして強制的に配布
     */
    public boolean giveGuideBookForced(Player player, String jobName) {
        if (!isEnabled()) {
            player.sendMessage(ChatColor.RED + "ガイドブック機能は現在無効です。");
            return false;
        }
        
        ItemStack guideBook = createJobGuideBook(jobName);
        if (guideBook == null) {
            player.sendMessage(ChatColor.RED + "この職業のガイドブックは設定されていません。");
            return false;
        }
        
        // インベントリに空きがあるか確認
        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(guideBook);
        
        if (!leftOver.isEmpty()) {
            // インベントリが満杯 - 足元にドロップ
            player.getWorld().dropItemNaturally(player.getLocation(), guideBook);
            player.sendMessage(ChatColor.YELLOW + "インベントリが満杯のため、ガイドブックを足元にドロップしました。");
        }
        
        // 配布メッセージを送信
        String displayName = getConfig().getString(
            "jobs.job_settings." + jobName + ".display_name", 
            jobName
        );
        player.sendMessage(ChatColor.GREEN + "【職業ガイド】" + ChatColor.WHITE + 
            displayName + "のガイドブックを受け取りました！");
        
        return true;
    }
}
