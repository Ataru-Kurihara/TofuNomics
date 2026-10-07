package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.economy.ItemManager;
import org.tofu.tofunomics.economy.ItemNameText;
import org.tofu.tofunomics.jobs.JobCraftPermissionManager;

import java.util.function.Predicate;

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
 *
 * 通貨（TofuCoin / TofuGold）を材料にしたクラフトもここで止める。
 * バニラのレシピは名前や説明文を見ないので、止めないと TofuCoin 9 枚から
 * 金インゴットを作って取引所で 9 より高く売る、といった形でお金が増える。
 * 通貨はどのワールドでも通貨なので、こちらはワールドを問わず止める。
 *
 * 自動クラフター（1.21）はプレイヤーを介さないため CraftItemEvent が発火しない。
 * CrafterCraftEvent で、通貨入りのクラフトと職業専売品のクラフトを止める
 * （誰の職業か判定できないので、専売品はクラフターでは作れない）。
 */
public class CraftRestrictionEventHandler implements Listener {

    private final TofuNomics plugin;

    public CraftRestrictionEventHandler(TofuNomics plugin) {
        this.plugin = plugin;
    }

    /**
     * 材料に通貨が 1 つでも含まれるかを判定する。
     */
    public static boolean containsCurrency(ItemStack[] ingredients, Predicate<ItemStack> isCurrency) {
        if (ingredients == null) {
            return false;
        }
        for (ItemStack ingredient : ingredients) {
            if (ingredient != null && isCurrency.test(ingredient)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCurrency(ItemStack[] ingredients) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return false;
        }
        return containsCurrency(ingredients, itemManager::isCurrencyItem);
    }

    /**
     * 通貨が材料に入っているあいだは、完成品を表示しない。
     * 他プラグインが結果を書き換えても最後に空へ戻せるよう HIGHEST で処理する。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsCurrency(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    /**
     * 自動クラフターでのクラフト。通貨入りと職業専売品を止める。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        BlockState state = event.getBlock().getState();
        if (state instanceof Container
                && containsCurrency(((Container) state).getInventory().getContents())) {
            event.setCancelled(true);
            return;
        }

        JobCraftPermissionManager permissionManager = plugin.getJobCraftPermissionManager();
        if (permissionManager == null || plugin.getConfigManager() == null) {
            return;
        }
        if (!plugin.getConfigManager().isJobRestrictionEnabledInWorld(event.getBlock().getWorld().getName())) {
            return;
        }
        if (permissionManager.isJobRestrictedItem(event.getRecipe().getResult().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCraftItem(CraftItemEvent event) {
        // 通貨入りのクラフトは、結果の表示を消していても念のためここでも止める
        if (containsCurrency(event.getInventory().getMatrix())) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§c通貨（TofuCoin・TofuGold）はクラフトの材料にできません。");
            return;
        }

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
            ItemNameText.send(player, message, JobCraftPermissionManager.ITEM_PLACEHOLDER, craftedItem);

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
