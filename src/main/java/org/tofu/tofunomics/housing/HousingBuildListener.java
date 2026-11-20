package org.tofu.tofunomics.housing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.market.MarketItemSerializer;
import org.tofu.tofunomics.testing.TestModeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 借主による物件内の設置・破壊を絞るリスナー。
 *
 * WorldGuard の賃貸リージョンは build / block-break を「借主（members）だけ allow」にしてあり、
 * 借主以外は親リージョンの deny で止まる。ここでは借主に対して、さらに次を守らせる。
 * - 置けるのは空気の所だけ（{@link TenantBuildRules}）。置いたブロックは記録する
 * - 壊せるのは自分が置いたブロックだけ（元からある建物は壊せない）
 * - 水・溶岩などのバケツ、額縁・絵画・防具立て、看板の書き換え、斧・クワ等で元のブロックを変えるのは不可
 *
 * 判定は LOW（他のプラグインの経験値付与などより前）、記録は MONITOR（取り消されなかった物だけ）で行う。
 * 管理者（tofunomics.housing.admin）は対象外。
 */
public class HousingBuildListener implements Listener {

    private static final String PERM_ADMIN = "tofunomics.housing.admin";

    private final HousingRentalManager rentalManager;
    private final TestModeManager testModeManager;
    private final TenantBuildRules rules;

    public HousingBuildListener(TofuNomics plugin, HousingRentalManager rentalManager,
                                TestModeManager testModeManager, TenantBuildRules rules) {
        this.rentalManager = rentalManager;
        this.testModeManager = testModeManager;
        this.rules = rules;
    }

    /**
     * その場所が、このプレイヤーが借りている物件の中なら、その物件（借主でない・管理者なら null）。
     * 借主以外は WorldGuard が止めるので、ここでは扱わない。
     */
    private HousingRentalManager.RentedArea tenantArea(Player player, Block block) {
        HousingRentalManager.RentedArea area = rentalManager.findRentedAreaAt(block.getLocation());
        if (area == null || !area.getTenantUuid().equals(player.getUniqueId())) {
            return null;
        }
        if (testModeManager != null
                ? testModeManager.hasEffectivePermission(player, PERM_ADMIN)
                : player.hasPermission(PERM_ADMIN)) {
            return null;
        }
        return area;
    }

    // ===== 設置 =====

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (tenantArea(player, block) == null) {
            return;
        }
        List<Material> replaced = new ArrayList<>();
        for (Block b : placedBlocks(event)) {
            if (b.equals(block)) {
                replaced.add(event.getBlockReplacedState().getType());
            } else {
                replaced.add(replacedTypeOf(event, b));
            }
        }
        String reason = rules.checkPlace(block.getType(), replaced);
        if (reason != null) {
            event.setCancelled(true);
            player.sendMessage("§c" + reason);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceRecord(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        HousingRentalManager.RentedArea area = tenantArea(player, block);
        if (area == null) {
            return;
        }
        String itemData = MarketItemSerializer.serialize(itemToKeep(event.getItemInHand()));
        boolean first = true;
        for (Block b : placedBlocks(event)) {
            if (!area.contains(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())) {
                continue;
            }
            // アイテムは 1 個目のブロックにだけ付ける（ドア・ベッドで 2 個返さないため）
            rentalManager.recordTenantPlacement(area, b, first ? itemData : null);
            first = false;
        }
    }

    /**
     * 1 回の設置で置かれる全ブロック（ベッドは 2 マス、ドア・背の高い花は上半分も）。
     */
    private List<Block> placedBlocks(BlockPlaceEvent event) {
        List<Block> blocks = new ArrayList<>();
        Block block = event.getBlockPlaced();
        blocks.add(block);
        if (event instanceof BlockMultiPlaceEvent) {
            for (BlockState state : ((BlockMultiPlaceEvent) event).getReplacedBlockStates()) {
                Block b = state.getBlock();
                if (!blocks.contains(b)) {
                    blocks.add(b);
                }
            }
        }
        BlockData data = block.getBlockData();
        if (data instanceof Bisected && ((Bisected) data).getHalf() == Bisected.Half.BOTTOM) {
            Block upper = block.getRelative(BlockFace.UP);
            if (!blocks.contains(upper)) {
                blocks.add(upper);
            }
        }
        return blocks;
    }

    private Material replacedTypeOf(BlockPlaceEvent event, Block b) {
        if (event instanceof BlockMultiPlaceEvent) {
            for (BlockState state : ((BlockMultiPlaceEvent) event).getReplacedBlockStates()) {
                if (state.getBlock().equals(b)) {
                    return state.getType();
                }
            }
        }
        // ドアの上半分などは、設置の判定の時点ではまだ置かれていないので、今のブロックが置き換え前
        return b.getType();
    }

    /**
     * 退去時に返すアイテム（1 個）。シュルカーボックス等の「中身入りのアイテム」は、
     * 中身を別に預かるので、空の物にして二重にならないようにする。
     */
    static ItemStack itemToKeep(ItemStack inHand) {
        if (inHand == null || inHand.getType().isAir()) {
            return null;
        }
        if (inHand.getItemMeta() instanceof BlockStateMeta) {
            return new ItemStack(inHand.getType(), 1);
        }
        ItemStack copy = inHand.clone();
        copy.setAmount(1);
        return copy;
    }

    // ===== 破壊 =====

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakCheck(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        HousingRentalManager.RentedArea area = tenantArea(player, block);
        if (area == null) {
            return;
        }
        if (!rentalManager.isPlacedByTenant(area, block)) {
            event.setCancelled(true);
            player.sendMessage("§c物件に元からあるブロックは壊せません（壊せるのは自分で置いた物だけです）");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakRecord(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (tenantArea(player, block) == null) {
            return;
        }
        rentalManager.forgetTenantPlacement(block);
        // 2 マスのブロックは、もう片方も一緒に消える
        BlockData data = block.getBlockData();
        if (data instanceof Bisected) {
            BlockFace face = ((Bisected) data).getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN;
            rentalManager.forgetTenantPlacement(block.getRelative(face));
        } else if (data instanceof Bed) {
            Bed bed = (Bed) data;
            BlockFace face = bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace();
            rentalManager.forgetTenantPlacement(block.getRelative(face));
        }
    }

    // ===== 元のブロックを変える操作 =====

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (tenantArea(event.getPlayer(), event.getBlock()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c物件の中ではバケツは使えません");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (tenantArea(event.getPlayer(), event.getBlock()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c物件の中ではバケツは使えません");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();
        HousingRentalManager.RentedArea area = tenantArea(player, clicked);
        if (area == null) {
            return;
        }
        // 畑を踏み荒らす
        if (event.getAction() == Action.PHYSICAL) {
            if (clicked.getType() == Material.FARMLAND && !rentalManager.isPlacedByTenant(area, clicked)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return;
        }
        if (!TenantBuildRules.isModifyingTool(event.getItem().getType())) {
            return;
        }
        if (rentalManager.isPlacedByTenant(area, clicked)) {
            return;
        }
        // 道具の使用だけ止める（ドアを開ける等のブロック操作はそのまま）
        event.setUseItemInHand(Event.Result.DENY);
        if (!clicked.getType().isInteractable()) {
            player.sendMessage("§c物件に元からあるブロックは変えられません");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        HousingRentalManager.RentedArea area = tenantArea(event.getPlayer(), event.getBlock());
        if (area != null && !rentalManager.isPlacedByTenant(area, event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c物件に元からある看板は書き換えられません");
        }
    }

    // ===== 額縁・絵画・防具立てなど（記録できないので不可） =====

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && tenantArea(player, event.getBlock()) != null) {
            event.setCancelled(true);
            player.sendMessage("§c物件の中には額縁・絵画は置けません");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && tenantArea(player, event.getBlock()) != null) {
            event.setCancelled(true);
            player.sendMessage("§c物件の中には置けません");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player
                && tenantArea((Player) event.getRemover(), event.getEntity().getLocation().getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof Hanging) && !(target instanceof ArmorStand)) {
            return;
        }
        if (event.getDamager() instanceof Player
                && tenantArea((Player) event.getDamager(), target.getLocation().getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (target instanceof Hanging
                && tenantArea(event.getPlayer(), target.getLocation().getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (tenantArea(event.getPlayer(), event.getRightClicked().getLocation().getBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
