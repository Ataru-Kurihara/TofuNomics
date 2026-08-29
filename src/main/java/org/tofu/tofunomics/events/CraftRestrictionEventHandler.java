package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.tofu.tofunomics.TofuNomics;

/**
 * 職業別クラフト制限の唯一の責任を持つイベントハンドラー。
 *
 * UnifiedEventHandler の craft 処理は shouldProcessEvent でゲートされており、
 * 無職・クリエイティブ・権限なしのプレイヤーでは制限がスキップされてしまう。
 * そのため制限ロジックはこのハンドラーに集約する。
 *
 * 優先度を LOWEST にすることで、XP/クエストを付与する UnifiedEventHandler(HIGH)
 * よりも先にキャンセルし、禁止クラフトに対する経験値付与を防ぐ。
 * UnifiedEventHandler 側は ignoreCancelled = true のため、ここでキャンセルされた
 * イベントは処理されない。
 */
public class CraftRestrictionEventHandler implements Listener {

    private final TofuNomics plugin;

    public CraftRestrictionEventHandler(TofuNomics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();

        // ワールドチェック（EventProcessor.isValidWorldと同等の判定）
        // 対象外ワールドでは職業クラフト制限を適用しない
        if (!isCraftRestrictionEnabledWorld(player)) {
            return;
        }

        // JobCraftPermissionManagerの取得
        if (plugin.getJobCraftPermissionManager() == null) {
            plugin.getLogger().warning("JobCraftPermissionManager が null - クラフト制限をスキップ");
            return;
        }

        // クラフト制限チェック
        if (!plugin.getJobCraftPermissionManager().canPlayerCraftItem(player, craftedItem)) {
            // クラフトを禁止
            event.setCancelled(true);

            // 制限メッセージを送信
            String message = plugin.getJobCraftPermissionManager().getCraftDeniedMessage(player, craftedItem);
            player.sendMessage(message);

            plugin.getLogger().info("クラフト制限: " + player.getName() + " が " + craftedItem.name() + " のクラフトを禁止");
        }
    }

    /**
     * クラフト制限を適用すべきワールドかどうかを判定する。
     * 判定は ConfigManager.isJobRestrictionEnabledInWorld() に集約されており、
     * 採掘・植え付け制限（UnifiedEventHandler）と同じ基準になる。
     * 対象外ワールドでは制限を適用しない。
     */
    private boolean isCraftRestrictionEnabledWorld(Player player) {
        if (plugin.getConfigManager() == null) {
            // 設定が取得できない場合は制限なし側に倒す。
            // 他ワールド（ロビー・ミニゲーム等）を巻き込んで壊さないことを優先する。
            // UnifiedEventHandler.isJobRestrictionWorld と同じ方向に揃えている。
            return false;
        }

        return plugin.getConfigManager().isJobRestrictionEnabledInWorld(player.getWorld().getName());
    }
}
