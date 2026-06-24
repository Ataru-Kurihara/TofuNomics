package org.tofu.tofunomics.experience;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobExperienceManager.isInteractHarvestable の採取成立判定テスト。
 * 右クリック採取（スイートベリー・グローベリー・はちみつ）が実際に収穫物を得られる
 * 状態のときのみtrueを返し、空振りでは付与対象外になることを検証する。
 */
public class JobExperienceManagerInteractHarvestTest {

    private Block mockSweetBerry(int age) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.SWEET_BERRY_BUSH);
        Ageable data = mock(Ageable.class);
        when(data.getAge()).thenReturn(age);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private Block mockCaveVines(Material type, boolean berries) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        CaveVinesPlant data = mock(CaveVinesPlant.class);
        when(data.isBerries()).thenReturn(berries);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private Block mockBeehive(Material type, int honeyLevel, int maxHoneyLevel) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        Beehive data = mock(Beehive.class);
        when(data.getHoneyLevel()).thenReturn(honeyLevel);
        when(data.getMaximumHoneyLevel()).thenReturn(maxHoneyLevel);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private Player mockPlayerHolding(Material mainHand) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mainHand);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    @Test
    public void testSweetBerryHarvestable() {
        // 実がドロップする成熟段階（age >= 2）のみ採取成立
        assertTrue(JobExperienceManager.isInteractHarvestable(null, mockSweetBerry(3)));
        assertTrue(JobExperienceManager.isInteractHarvestable(null, mockSweetBerry(2)));
        assertFalse(JobExperienceManager.isInteractHarvestable(null, mockSweetBerry(1)));
        assertFalse(JobExperienceManager.isInteractHarvestable(null, mockSweetBerry(0)));
    }

    @Test
    public void testCaveVinesHarvestable() {
        // 実が成っているときのみ採取成立
        assertTrue(JobExperienceManager.isInteractHarvestable(null, mockCaveVines(Material.CAVE_VINES, true)));
        assertTrue(JobExperienceManager.isInteractHarvestable(null, mockCaveVines(Material.CAVE_VINES_PLANT, true)));
        assertFalse(JobExperienceManager.isInteractHarvestable(null, mockCaveVines(Material.CAVE_VINES, false)));
    }

    @Test
    public void testBeehiveHarvestable() {
        Player withBottle = mockPlayerHolding(Material.GLASS_BOTTLE);
        Player withShears = mockPlayerHolding(Material.SHEARS);
        Player withoutTool = mockPlayerHolding(Material.DIRT);

        // 満杯（honey_level == 最大）かつガラス瓶（はちみつ）またはハサミ（ハニカム）所持で採取成立
        assertTrue(JobExperienceManager.isInteractHarvestable(withBottle, mockBeehive(Material.BEEHIVE, 5, 5)));
        assertTrue(JobExperienceManager.isInteractHarvestable(withBottle, mockBeehive(Material.BEE_NEST, 5, 5)));
        assertTrue(JobExperienceManager.isInteractHarvestable(withShears, mockBeehive(Material.BEEHIVE, 5, 5)));
        assertTrue(JobExperienceManager.isInteractHarvestable(withShears, mockBeehive(Material.BEE_NEST, 5, 5)));
        // 満杯でない巣は対象外
        assertFalse(JobExperienceManager.isInteractHarvestable(withBottle, mockBeehive(Material.BEEHIVE, 4, 5)));
        assertFalse(JobExperienceManager.isInteractHarvestable(withShears, mockBeehive(Material.BEEHIVE, 4, 5)));
        // 満杯でも採取道具（瓶/ハサミ）を持っていなければ対象外
        assertFalse(JobExperienceManager.isInteractHarvestable(withoutTool, mockBeehive(Material.BEEHIVE, 5, 5)));
    }

    @Test
    public void testUnrelatedBlockReturnsFalse() {
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        assertFalse(JobExperienceManager.isInteractHarvestable(null, stone));
    }
}
