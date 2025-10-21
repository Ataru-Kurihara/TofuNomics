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

/**
 * ルール確認GUI（ページ分割型）
 */
public class RulesGUI implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final RulesManager rulesManager;
    
    // アクティブなGUIセッション
    private final Map<UUID, RulesGUISession> activeSessions = new ConcurrentHashMap<>();
    
    // 最大ページ数
    private static final int MAX_PAGES = 4;
    
    public RulesGUI(TofuNomics plugin, ConfigManager configManager, RulesManager rulesManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rulesManager = rulesManager;
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
        // ページ内容を表示（スロット 10-43）
        List<String> pageContent = plugin.getConfig().getStringList("rules.pages." + page + ".content");
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
