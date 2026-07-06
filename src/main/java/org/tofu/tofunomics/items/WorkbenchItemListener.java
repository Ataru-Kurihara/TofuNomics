package org.tofu.tofunomics.items;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.integration.WorldGuardIntegration;

/**
 * 作業台アイテムを持って右クリックすると、設置せずに作業台GUIを開くリスナー
 *
 * WorldGuard保護区域内（建築不可の街など）でのみ機能し、
 * 区域外では従来通りブロックとして設置できる。
 */
public class WorkbenchItemListener implements Listener {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final WorldGuardIntegration worldGuardIntegration;

    public WorkbenchItemListener(TofuNomics plugin, ConfigManager configManager, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.worldGuardIntegration = worldGuardIntegration;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // config で無効化されている場合は何もしない
        if (!configManager.isWorkbenchItemEnabled()) {
            return;
        }

        // メインハンドのみ処理（オフハンドとの二重発火を防止）
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // 手に持っているアイテムが作業台でなければ対象外
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CRAFTING_TABLE) {
            return;
        }

        // 右クリック以外は対象外
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // インタラクト可能ブロック（チェスト・かまど・既設の作業台・ドア等）を
        // 右クリックした場合は、そのブロック本来の操作を優先する
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
                return;
            }
        }

        // WorldGuard保護区域内でのみ機能させる（区域外は従来通り設置）
        Player player = event.getPlayer();
        if (worldGuardIntegration == null || !worldGuardIntegration.isInProtectedRegion(player.getLocation())) {
            return;
        }

        // 設置をキャンセルして作業台GUIを開く
        event.setCancelled(true);
        player.openWorkbench(null, true);
    }
}
