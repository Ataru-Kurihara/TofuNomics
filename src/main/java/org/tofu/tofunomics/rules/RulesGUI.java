package org.tofu.tofunomics.rules;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RulesGUI implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final RulesManager rulesManager;
    private final org.tofu.tofunomics.jobs.JobManager jobManager;
    
    // アクティブなGUIセッション
    private final Map<UUID, RulesGUISession> activeSessions = new ConcurrentHashMap<>();
    
    // 最大ページ数
    private static final int MAX_PAGES = 4;
    
    public RulesGUI(TofuNomics plugin, ConfigManager configManager, RulesManager rulesManager, org.tofu.tofunomics.jobs.JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rulesManager = rulesManager;
        this.jobManager = jobManager;
    }
    
    /**
     * GUIセッション情報
     */
    private static class RulesGUISession {
        private final UUID playerId;
        private int currentPage;
        private final Inventory inventory;
        
        public RulesGUISession(UUID playerId, int initialPage, Inventory inventory) {
            this.playerId = playerId;
            this.currentPage = initialPage;
            this.inventory = inventory;
        }
        
        public UUID getPlayerId() { return playerId; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int page) { this.currentPage = page; }
        public Inventory getInventory() { return inventory; }
    }
    
    /**
     * ページ内容を動的に生成
     */
    private List<String> generateDynamicPageContent(int page) {
        List<String> content = new ArrayList<>();
        
        try {
            if (page == 2) {
                // ページ2: 経済システム（動的生成）
                String currencyName = plugin.getConfig().getString("economy.currency.name", "金塊");
                String currencySymbol = plugin.getConfig().getString("economy.currency.symbol", "G");
                int startingBalance = plugin.getConfig().getInt("economy.starting_balance", 100);
                
                content.add("§e▼ 通貨システム");
                content.add("§f通貨: §6" + currencyName + " (" + currencySymbol + ")");
                content.add("§f新規プレイヤーは" + startingBalance + currencySymbol + "からスタート");
                content.add("");
                content.add("§e▼ 銀行システム");
                content.add("§f・§b/balance §f- 残高確認");
                content.add("§f・§b/deposit <金額> §f- 金塊を預ける");
                content.add("§f・§b/withdraw <金額> §f- 金塊を引き出す");
                content.add("§f・銀行NPC周辺でのみ取引可能");
                content.add("");
                content.add("§e▼ 送金");
                content.add("§f・§b/pay <プレイヤー名> <金額>");
                content.add("§f・他のプレイヤーにお金を送れます");
                content.add("");
                content.add("§e▼ NPCとの取引");
                content.add("§f・取引NPCで物を売買できます");
                content.add("§f・ジョブレベルで価格ボーナスあり");
                content.add("§f・食料NPCで食べ物を購入できます");
                
            } else if (page == 3) {
                // ページ3: ジョブ・スキルシステム（動的生成）
                int maxJobsPerPlayer = plugin.getConfig().getInt("jobs.general.max_jobs_per_player", 1);
                
                content.add("§e▼ ジョブの種類");
                
                if (plugin.getConfig().isConfigurationSection("jobs.job_settings")) {
                    Set<String> jobKeys = plugin.getConfig().getConfigurationSection("jobs.job_settings").getKeys(false);
                    List<String> jobOrder = Arrays.asList("miner", "woodcutter", "farmer", "fisherman", "blacksmith", "alchemist", "enchanter", "architect");
                    int maxLevel = 100;
                    
                    for (String jobKey : jobOrder) {
                        if (jobKeys.contains(jobKey)) {
                            String displayName = plugin.getConfig().getString("jobs.job_settings." + jobKey + ".display_name", jobKey);
                            String description = plugin.getConfig().getString("jobs.job_settings." + jobKey + ".description", "");
                            maxLevel = plugin.getConfig().getInt("jobs.job_settings." + jobKey + ".max_level", 100);
                            content.add("§f・" + displayName + "（" + capitalize(jobKey) + "） - " + description);
                        }
                    }
                    
                    if (maxJobsPerPlayer == 1) {
                        content.add("§f※ 同時に就けるジョブは1つのみです");
                    } else {
                        content.add("§f※ 同時に" + maxJobsPerPlayer + "つのジョブに就けます");
                    }
                    content.add("");
                    content.add("§e▼ レベルアップ");
                    content.add("§f・ジョブ関連の作業で経験値を獲得");
                    content.add("§f・レベルが上がるとスキルを習得");
                    content.add("§f・最大レベル: " + maxLevel);
                } else {
                    throw new Exception("ジョブ設定が見つかりません");
                }
                
                content.add("");
                content.add("§e▼ スキル");
                content.add("§f・各ジョブ専用のスキルが習得可能");
                content.add("§f・作業効率アップや特殊能力を獲得");
                content.add("");
                content.add("§e▼ ジョブ変更");
                content.add("§f・§b/jobs leave §fで現在のジョブを辞める");
                content.add("§f・§b/jobs join <ジョブ名> §fで新しいジョブに就く");
                content.add("§f・レベル50に達すると他の職業に転職できます");
            } else {
                // その他のページは静的に読み込み
                content = plugin.getConfig().getStringList("rules.pages." + page + ".content");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("ページ" + page + "の動的生成に失敗しました。フォールバックします: " + e.getMessage());
            content = plugin.getConfig().getStringList("rules.pages." + page + ".content");
        }
        
        return content;
    }
    
    /**
     * 文字列の最初の文字を大文字にする
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * ルールGUIを開く
     */
    public void openRulesGUI(Player player, int page) {
        try {
            String title = configManager.getMessage("rules.gui_title") + " - ページ " + page + "/" + MAX_PAGES;
            Inventory gui = Bukkit.createInventory(null, 54, title);
            
            setupRulesGUIItems(gui, player, page);
            
            RulesGUISession session = new RulesGUISession(
                player.getUniqueId(),
                page,
                gui
            );
            
            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);
            
            plugin.getLogger().info("ルールGUIを開きました: " + player.getName() + " - ページ " + page);
            
        } catch (Exception e) {
            plugin.getLogger().severe("ルールGUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§cルールGUIの表示中にエラーが発生しました");
            e.printStackTrace();
        }
    }
    
    /**
     * GUIアイテムをセットアップ
     */
    private void setupRulesGUIItems(Inventory gui, Player player, int page) {
        // ページ内容を動的に生成
        List<String> pageContent = generateDynamicPageContent(page);
        String pageTitle = plugin.getConfig().getString("rules.pages." + page + ".title");
        
        // タイトル表示
        ItemStack titleItem = createGUIItem(
            Material.BOOK,
            pageTitle,
            pageContent
        );
        gui.setItem(13, titleItem);
        
        // ページナビゲーション
        if (page > 1) {
            // 前のページボタン
            ItemStack prevButton = createGUIItem(
                Material.ARROW,
                "§e◀ 前のページ",
                Arrays.asList("§7クリックで前のページへ")
            );
            gui.setItem(48, prevButton);
        }
        
        if (page < MAX_PAGES) {
            // 次のページボタン
            ItemStack nextButton = createGUIItem(
                Material.ARROW,
                "§e次のページ ▶",
                Arrays.asList("§7クリックで次のページへ")
            );
            gui.setItem(50, nextButton);
        }
        
        // 最終ページの場合、同意ボタンを表示
        if (page == MAX_PAGES) {
            // 同意するボタン
            ItemStack agreeButton = createGUIItem(
                Material.LIME_WOOL,
                "§a§l✔ ルールに同意する",
                Arrays.asList(
                    "§7クリックしてルールに同意し、",
                    "§7プレイを開始します"
                )
            );
            gui.setItem(51, agreeButton);
            
            // 同意しないボタン
            ItemStack disagreeButton = createGUIItem(
                Material.RED_WOOL,
                "§c§l✘ 同意しない",
                Arrays.asList(
                    "§7クリックしてGUIを閉じます",
                    "§7※ 同意するまでプレイできません"
                )
            );
            gui.setItem(47, disagreeButton);
        }
        
        // 閉じるボタン
        ItemStack closeButton = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7クリックでGUIを閉じます")
        );
        gui.setItem(49, closeButton);
    }
    
    /**
     * GUIアイテムを作成
     */
    private ItemStack createGUIItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * GUIクリックイベント
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        
        // このプレイヤーのセッションがあるか確認
        RulesGUISession session = activeSessions.get(playerId);
        if (session == null) return;
        
        // インベントリが一致するか確認
        if (!event.getInventory().equals(session.getInventory())) return;
        
        // クリックキャンセル
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        int slot = event.getSlot();
        int currentPage = session.getCurrentPage();
        
        // ボタンの処理
        if (slot == 48 && currentPage > 1) {
            // 前のページ
            player.closeInventory();
            openRulesGUI(player, currentPage - 1);
        } else if (slot == 50 && currentPage < MAX_PAGES) {
            // 次のページ
            player.closeInventory();
            openRulesGUI(player, currentPage + 1);
        } else if (slot == 49) {
            // 閉じる
            player.closeInventory();
        } else if (slot == 51 && currentPage == MAX_PAGES) {
            // 同意する
            player.closeInventory();
            rulesManager.agreeToRules(player);
        } else if (slot == 47 && currentPage == MAX_PAGES) {
            // 同意しない
            player.closeInventory();
            player.sendMessage(configManager.getMessage("rules.disagreed"));
        }
    }
    
    /**
     * GUIを閉じたときの処理
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // セッションがあれば削除
        RulesGUISession session = activeSessions.get(playerId);
        if (session != null && event.getInventory().equals(session.getInventory())) {
            activeSessions.remove(playerId);
        }
    }
}
