package org.tofu.tofunomics.rules;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ルール確認・同意機能を管理するクラス
 * - ルールブックの配布
 * - 未同意プレイヤーの行動制限
 * - ルール同意の記録
 */
public class RulesManager implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final RulesGUI rulesGUI;
    
    // 未同意プレイヤーのUUIDを記録（制限対象）
    private final Set<UUID> unagreedPlayers = ConcurrentHashMap.newKeySet();
    
    public RulesManager(TofuNomics plugin, ConfigManager configManager, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.rulesGUI = new RulesGUI(plugin, configManager, this);
    }
    
    /**
     * ルールブックアイテムを生成
     */
    public ItemStack createRulebook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            // 本の基本情報
            String bookName = configManager.getMessage("rules.book_name");
            meta.setTitle(bookName);
            meta.setAuthor("§6TofuNomics Server");
            
            // ページ内容を追加
            List<String> pages = new ArrayList<>();
            
            // ページ1: 初心者ガイド
            List<String> page1Content = plugin.getConfig().getStringList("rules.pages.1.content");
            pages.add(String.join("\n", page1Content));
            
            // ページ2: 経済システム
            List<String> page2Content = plugin.getConfig().getStringList("rules.pages.2.content");
            pages.add(String.join("\n", page2Content));
            
            // ページ3: ジョブ・スキルシステム
            List<String> page3Content = plugin.getConfig().getStringList("rules.pages.3.content");
            pages.add(String.join("\n", page3Content));
            
            // ページ4: 禁止事項
            List<String> page4Content = plugin.getConfig().getStringList("rules.pages.4.content");
            pages.add(String.join("\n", page4Content));
            
            // 最終ページ: GUI案内
            pages.add("§6§l【ルール確認】\n\n§7このルールブックを右クリックするか、\n§7/rules コマンドを実行すると、\n§7詳細なルールGUIが開きます。\n\n§eルールに同意してプレイを開始しましょう！");
            
            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * プレイヤーにルールブックを配布
     */
    public void giveRulebook(Player player) {
        ItemStack rulebook = createRulebook();
        player.getInventory().addItem(rulebook);
        player.sendMessage(configManager.getMessage("rules.book_given"));
    }
    
    /**
     * プレイヤーがルールに同意しているか確認
     */
    public boolean hasAgreedToRules(UUID uuid) {
        return playerDAO.hasAgreedToRules(uuid);
    }
    
    /**
     * プレイヤーをルール未同意リストに追加（行動制限対象）
     */
    public void markAsUnagreed(UUID uuid) {
        unagreedPlayers.add(uuid);
    }
    
    /**
     * プレイヤーのルール同意を記録し、制限を解除
     */
    public void agreeToRules(Player player) {
        UUID uuid = player.getUniqueId();
        
        // データベースに記録
        playerDAO.setRulesAgreed(uuid, true);
        
        // 制限リストから削除
        unagreedPlayers.remove(uuid);
        
        // メッセージ送信
        player.sendMessage(configManager.getMessage("rules.agreed"));
        
        plugin.getLogger().info("プレイヤー " + player.getName() + " がルールに同意しました");
    }

    /**
     * プレイヤーのルール同意状態をリセットし、未同意状態に戻す（テスト用）
     * @param uuid プレイヤーのUUID
     * @return リセット成功時 true
     */
    public boolean resetPlayerRules(UUID uuid) {
        try {
            // データベースの同意状態を FALSE に更新
            playerDAO.setRulesAgreed(uuid, false);
            
            // 未同意プレイヤーリストに追加（キャッシュ更新）
            unagreedPlayers.add(uuid);
            
            plugin.getLogger().info("プレイヤー " + uuid + " のルール同意状態をリセットしました");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("ルール同意状態のリセットに失敗しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * RulesGUIを取得
     */
    public RulesGUI getRulesGUI() {
        return rulesGUI;
    }
    
    /**
     * プレイヤーが未同意リストに含まれているか確認
     */
    public boolean isUnagreed(UUID uuid) {
        return unagreedPlayers.contains(uuid);
    }
    
    // ========== イベントハンドラ：未同意プレイヤーの行動制限 ==========
    
    /**
     * 移動制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId())) {
            // 移動先と移動元が異なるブロックの場合のみキャンセル
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
                player.sendMessage(configManager.getMessage("rules.action_blocked"));
            }
        }
    }
    
    /**
     * ブロック破壊制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }
    
    /**
     * ブロック設置制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }
    
    /**
     * アイテム使用制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId())) {
            // ルールブック使用は許可（右クリックでGUI開く）
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && meta.hasTitle()) {
                    String bookTitle = meta.getTitle();
                    String expectedTitle = configManager.getMessage("rules.book_name");
                    if (bookTitle.equals(expectedTitle)) {
                        // ルールブックの右クリック → GUI表示
                        event.setCancelled(true);
                        rulesGUI.openRulesGUI(player, 1);
                        return;
                    }
                }
            }
            
            // その他のアイテム使用は制限
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }
    
    /**
     * コマンド実行制限（/rulesのみ許可）
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId())) {
            String command = event.getMessage().toLowerCase();
            
            // /rules コマンドは許可
            if (command.startsWith("/rules")) {
                return;
            }
            
            // その他のコマンドは制限
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.must_agree"));
        }
    }
}
