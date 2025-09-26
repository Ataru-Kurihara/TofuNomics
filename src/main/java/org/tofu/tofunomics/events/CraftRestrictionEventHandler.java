package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.tofu.tofunomics.TofuNomics;

/**
 * 専用のクラフト制限イベントハンドラー
 * UnifiedEventHandlerの問題回避のための緊急対応
 */
public class CraftRestrictionEventHandler implements Listener {
    
    private final TofuNomics plugin;
    
    public CraftRestrictionEventHandler(TofuNomics plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        plugin.getLogger().info("=== CraftRestrictionEventHandler 開始 ===");
        
        if (!(event.getWhoClicked() instanceof Player)) {
            plugin.getLogger().info("プレイヤー以外のクリック - 処理スキップ");
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();
        
        plugin.getLogger().info("プレイヤー: " + player.getName());
        plugin.getLogger().info("クラフト対象: " + craftedItem.name());
        
        // JobCraftPermissionManagerの取得
        if (plugin.getJobCraftPermissionManager() == null) {
            plugin.getLogger().warning("JobCraftPermissionManager が null - 制限をスキップ");
            return;
        }
        
        plugin.getLogger().info("JobCraftPermissionManager 確認完了");
        
        // クラフト制限チェック
        if (!plugin.getJobCraftPermissionManager().canPlayerCraftItem(player, craftedItem)) {
            // クラフトを禁止
            event.setCancelled(true);
            
            // 制限メッセージを送信
            String message = plugin.getJobCraftPermissionManager().getCraftDeniedMessage(player, craftedItem);
            player.sendMessage(message);
            
            plugin.getLogger().info("クラフト制限実行: " + player.getName() + " が " + craftedItem.name() + " のクラフトを禁止");
            return;
        }
        
        plugin.getLogger().info("クラフト許可: " + player.getName() + " が " + craftedItem.name() + " のクラフトを許可");
    }
}