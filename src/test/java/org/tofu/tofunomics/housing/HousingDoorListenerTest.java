package org.tofu.tofunomics.housing;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 賃貸物件のドアの判定のテスト。
 */
public class HousingDoorListenerTest {

    @Test
    public void ドアだけを対象にし_トラップドアは含まない() {
        assertTrue(HousingDoorListener.isDoor(Material.OAK_DOOR));
        assertTrue(HousingDoorListener.isDoor(Material.IRON_DOOR));
        assertTrue(HousingDoorListener.isDoor(Material.COPPER_DOOR));
        assertFalse(HousingDoorListener.isDoor(Material.OAK_TRAPDOOR));
        assertFalse(HousingDoorListener.isDoor(Material.OAK_FENCE_GATE));
        assertFalse(HousingDoorListener.isDoor(null));
    }

    @Test
    public void プレイヤーがいる側の反対へ出る() {
        // 北向きのドア（Z が減る向き）。南側（Z が大きい）にいれば北へ、北側にいれば南へ
        assertEquals(BlockFace.NORTH, HousingDoorListener.exitSide(BlockFace.NORTH, 0.1, 0.8));
        assertEquals(BlockFace.SOUTH, HousingDoorListener.exitSide(BlockFace.NORTH, 0.0, -0.8));
        // 東向きのドア
        assertEquals(BlockFace.WEST, HousingDoorListener.exitSide(BlockFace.EAST, 0.9, 0.3));
        assertEquals(BlockFace.EAST, HousingDoorListener.exitSide(BlockFace.EAST, -0.9, -0.3));
    }
}
