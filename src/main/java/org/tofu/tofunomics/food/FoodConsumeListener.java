package org.tofu.tofunomics.food;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食材を食べた時に職業経験値ブーストバフを付与するリスナー。
 *
 * PlayerItemConsumeEvent は実際に食料が食べられた時のみ発火するため、
 * 満腹時には通常食料でバフが付かない（自然な制約）。
 * 満腹で「食べたのに無反応」に見えるのを防ぐため、満腹時に対象食料を
 * 右クリックした場合はヒントメッセージのみ表示する（バフは付与しない）。
 */
public class FoodConsumeListener implements Listener {

    // 満腹時ヒントメッセージのスパム防止クールダウン（ミリ秒）
    private static final long SATIATED_HINT_COOLDOWN_MILLIS = 5000L;
    // 満腹判定の満腹度しきい値（最大値）
    private static final int FULL_FOOD_LEVEL = 20;

    private final ConfigManager configManager;
    private final FoodBuffManager foodBuffManager;

    // プレイヤーUUID -> 最後に満腹ヒントを送った時刻（クールダウン用）
    private final Map<UUID, Long> lastSatiatedHintAt = new ConcurrentHashMap<>();

    public FoodConsumeListener(ConfigManager configManager, FoodBuffManager foodBuffManager) {
        this.configManager = configManager;
        this.foodBuffManager = foodBuffManager;
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!configManager.isFoodBuffEnabled()) {
            return;
        }
        // 経済機能が有効なワールド以外ではバフを付与しない
        if (!configManager.isEconomyEnabledInWorld(event.getPlayer().getWorld().getName())) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }

        Material material = event.getItem().getType();
        FoodCategory category = FoodCategory.fromMaterial(material);
        if (category == null) {
            // バフ対象外の食材
            return;
        }

        Player player = event.getPlayer();
        foodBuffManager.applyBuff(player, category);
    }

    /**
     * 満腹で食料を食べられずバフが付かない場合に、理由をヒントとして通知する。
     *
     * 満腹時は PlayerItemConsumeEvent が発火しないため検知できない。
     * バフ対象食料を右クリックしたが満腹だった場合のみ、メッセージを表示する。
     * （バフは付与せず、挙動は変えない。）
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // メインハンドのみ・右クリック動作のみを対象にする（重複発火防止）
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!configManager.isFoodBuffEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!configManager.isEconomyEnabledInWorld(player.getWorld().getName())) {
            return;
        }

        // 満腹でない場合は通常通り食べられる（PlayerItemConsumeEvent でバフ付与）ので何もしない
        if (player.getFoodLevel() < FULL_FOOD_LEVEL) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        FoodCategory category = FoodCategory.fromMaterial(item.getType());
        if (category == null) {
            // バフ対象外の食材
            return;
        }

        sendSatiatedHint(player);
    }

    /**
     * 満腹ヒントメッセージを送信する（クールダウン付き）。
     */
    private void sendSatiatedHint(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long last = lastSatiatedHintAt.get(uuid);
        if (last != null && (now - last) < SATIATED_HINT_COOLDOWN_MILLIS) {
            return;
        }

        String template = configManager.getFoodBuffBlockedSatiatedMessage();
        if (template == null || template.isEmpty()) {
            return;
        }

        lastSatiatedHintAt.put(uuid, now);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', template));
    }
}
