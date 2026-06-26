package org.tofu.tofunomics.farming;

import org.bukkit.ChatColor;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.tofu.tofunomics.models.FarmPlot;

/**
 * 畑区画(farmplot)内のフェンスゲート/ドア/トラップドアの開閉を所有者に限定するリスナー。
 *
 * WorldGuardのメンバー機構はブロックの破壊・設置は保護するが、フェンスゲート/ドアの
 * 「開閉(右クリック)」はデフォルト設定では制御されないことが多い。本リスナーで、
 * 区画内のゲート/ドアは当該区画の所有者(owner_uuid)以外には開かせない。
 *
 * 仕様:
 * - 区画内 かつ プレイヤーが当該区画の所有者でない → 開閉をキャンセル
 * - 未割当(owner_uuid=null)区画は所有者が存在しないため、誰も開けない
 * - 管理者バイパスなし(OPでも所有者でなければ開けない)。WGのbypass権限ですり抜けるのを防ぐため、
 *   WorldGuardのcanInteractではなくDBのowner_uuidを直接比較する。
 */
public class FarmPlotProtectionListener implements Listener {

    private final FarmPlotManager farmPlotManager;

    public FarmPlotProtectionListener(FarmPlotManager farmPlotManager) {
        this.farmPlotManager = farmPlotManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractGate(PlayerInteractEvent event) {
        // メインハンドのみ処理(オフハンドでの二重発火を防止)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // ブロックの右クリックのみ対象
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // 対象はフェンスゲート/ドア/トラップドア(鉄ドア・鉄トラップドア含む。Tagで全種網羅)
        if (!isGateOrDoor(block)) {
            return;
        }

        FarmPlot plot = farmPlotManager.getPlotAt(block.getLocation());
        if (plot == null) {
            // 区画外は通常動作
            return;
        }

        Player player = event.getPlayer();
        String owner = plot.getOwnerUuid();
        if (owner != null && owner.equals(player.getUniqueId().toString())) {
            // 区画の所有者は開閉可能
            return;
        }

        // 非所有者・未割当区画は全員拒否
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "ここは他の農家の畑区画です。所有者しか開けられません。");
    }

    private boolean isGateOrDoor(Block block) {
        return Tag.FENCE_GATES.isTagged(block.getType())
                || Tag.DOORS.isTagged(block.getType())
                || Tag.TRAPDOORS.isTagged(block.getType());
    }
}
