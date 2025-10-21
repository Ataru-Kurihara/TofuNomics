package org.tofu.tofunomics.housing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.testing.TestModeManager;

/**
 * 住居賃貸システムのイベントリスナー
 */
public class HousingListener implements Listener {
    private final TofuNomics plugin;
    private final SelectionManager selectionManager;
    private final TestModeManager testModeManager;

    public HousingListener(TofuNomics plugin, SelectionManager selectionManager, TestModeManager testModeManager) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
        this.testModeManager = testModeManager;
    }

    /**
     * 木の斧での範囲選択
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // 管理者権限チェック（テストモードを考慮）
        if (!testModeManager.hasEffectivePermission(player, "tofunomics.housing.admin")) {
            return;
        }

        // 木の斧をチェック
        if (event.getItem() == null || event.getItem().getType() != selectionManager.getSelectionTool()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // 左クリック: 第1座標設定
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionManager.setFirstPosition(player, clickedBlock.getLocation());
            event.setCancelled(true); // ブロック破壊をキャンセル
        }
        // 右クリック: 第2座標設定
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionManager.setSecondPosition(player, clickedBlock.getLocation());
            event.setCancelled(true); // ブロック設置をキャンセル
        }
    }
}
