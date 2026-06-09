package org.tofu.tofunomics.food;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * 食材を食べた時に職業経験値ブーストバフを付与するリスナー。
 *
 * PlayerItemConsumeEvent は実際に食料が食べられた時のみ発火するため、
 * 満腹時には通常食料でバフが付かない（自然な制約）。
 */
public class FoodConsumeListener implements Listener {

    private final ConfigManager configManager;
    private final FoodBuffManager foodBuffManager;

    public FoodConsumeListener(ConfigManager configManager, FoodBuffManager foodBuffManager) {
        this.configManager = configManager;
        this.foodBuffManager = foodBuffManager;
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!configManager.isFoodBuffEnabled()) {
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
}
