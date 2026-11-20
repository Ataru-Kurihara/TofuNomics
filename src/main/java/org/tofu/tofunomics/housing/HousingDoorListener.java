package org.tofu.tofunomics.housing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.tofu.tofunomics.testing.TestModeManager;

/**
 * 借りられている物件の「元からあるドア」の扱い。
 *
 * - ドアは誰も開けられない（開いた隙に他の人がついて入れないように）
 * - 借主がクリックすると、ドアの反対側（外なら中、中なら外）へ移動する
 * - 借主以外には「契約者だけが入れます」と伝える
 * - 鉄のドアもボタン・レバーで開かない
 *
 * 空き物件のドアと、借主が自分で置いたドアは普通に開く。管理者（tofunomics.housing.admin）は対象外。
 */
public class HousingDoorListener implements Listener {

    private static final String PERM_ADMIN = "tofunomics.housing.admin";

    private final HousingRentalManager rentalManager;
    private final TestModeManager testModeManager;

    public HousingDoorListener(HousingRentalManager rentalManager, TestModeManager testModeManager) {
        this.rentalManager = rentalManager;
        this.testModeManager = testModeManager;
    }

    /** ドア（トラップドアは含まない） */
    static boolean isDoor(Material material) {
        return material != null && material.name().endsWith("_DOOR");
    }

    /**
     * ドアの反対側の向き。
     *
     * @param facing ドアの向き
     * @param dx     プレイヤーの位置 − ドアのブロックの中心（X）
     * @param dz     同じく Z
     * @return 移動先がドアのブロックから見てどちらか
     */
    static BlockFace exitSide(BlockFace facing, double dx, double dz) {
        double dot = dx * facing.getModX() + dz * facing.getModZ();
        return dot > 0 ? facing.getOppositeFace() : facing;
    }

    /** ドアの下半分のブロック */
    private static Block bottomHalf(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Bisected && ((Bisected) data).getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    private boolean isAdmin(Player player) {
        return testModeManager != null
                ? testModeManager.hasEffectivePermission(player, PERM_ADMIN)
                : player.hasPermission(PERM_ADMIN);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDoorClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (!isDoor(clicked.getType())) {
            return;
        }
        HousingRentalManager.RentedArea area = rentalManager.findRentedAreaForDoor(clicked.getLocation());
        if (area == null) {
            return; // 空き物件・物件の外は普通のドア
        }
        Player player = event.getPlayer();
        if (isAdmin(player)) {
            return;
        }
        Block door = bottomHalf(clicked);
        if (rentalManager.isPlacedByTenant(area, door)) {
            return; // 借主が自分で置いたドアは普通に開く
        }

        // 元からあるドアは開けさせない（手に持った物の使用も止める）
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        // 右手と左手で 2 回呼ばれるので、移動は右手の 1 回だけ
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!area.getTenantUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cこの物件は契約者だけが入れます");
            return;
        }
        passThrough(player, door);
    }

    private void passThrough(Player player, Block door) {
        if (!(door.getBlockData() instanceof Door)) {
            return;
        }
        BlockFace facing = ((Door) door.getBlockData()).getFacing();
        Location center = door.getLocation().add(0.5, 0, 0.5);
        BlockFace side = exitSide(facing,
                player.getLocation().getX() - center.getX(),
                player.getLocation().getZ() - center.getZ());
        Block feet = door.getRelative(side);
        Block head = feet.getRelative(BlockFace.UP);
        if (!feet.isPassable() || !head.isPassable()) {
            player.sendMessage("§cドアの反対側がふさがっていて通れません");
            return;
        }
        Location dest = feet.getLocation().add(0.5, 0, 0.5);
        // 進む向きを向かせる
        dest.setDirection(new org.bukkit.util.Vector(side.getModX(), 0, side.getModZ()));
        dest.setPitch(player.getLocation().getPitch());
        player.teleport(dest);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDoorRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (!isDoor(block.getType())) {
            return;
        }
        HousingRentalManager.RentedArea area = rentalManager.findRentedAreaForDoor(block.getLocation());
        if (area == null || rentalManager.isPlacedByTenant(area, bottomHalf(block))) {
            return;
        }
        // 鉄のドアなどをボタン・レバーで開けさせない
        event.setNewCurrent(event.getOldCurrent());
    }
}
