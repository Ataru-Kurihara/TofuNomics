package org.tofu.tofunomics.experience;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobExperienceManager.isCropFullyGrown の成熟判定テスト
 */
public class JobExperienceManagerCropMaturityTest {

    private Block mockCrop(Material type, int age, int maxAge) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        Ageable data = mock(Ageable.class);
        when(data.getAge()).thenReturn(age);
        when(data.getMaximumAge()).thenReturn(maxAge);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    @Test
    public void testMatureCropReturnsTrue() {
        // 完全成長した小麦（age 7/7）は経験値対象
        assertTrue(JobExperienceManager.isCropFullyGrown(mockCrop(Material.WHEAT, 7, 7)));
        assertTrue(JobExperienceManager.isCropFullyGrown(mockCrop(Material.CARROTS, 7, 7)));
        assertTrue(JobExperienceManager.isCropFullyGrown(mockCrop(Material.BEETROOTS, 3, 3)));
        assertTrue(JobExperienceManager.isCropFullyGrown(mockCrop(Material.NETHER_WART, 3, 3)));
    }

    @Test
    public void testImmatureCropReturnsFalse() {
        // 未成熟の作物は経験値対象外
        assertFalse(JobExperienceManager.isCropFullyGrown(mockCrop(Material.WHEAT, 0, 7)));
        assertFalse(JobExperienceManager.isCropFullyGrown(mockCrop(Material.WHEAT, 6, 7)));
        assertFalse(JobExperienceManager.isCropFullyGrown(mockCrop(Material.POTATOES, 3, 7)));
        assertFalse(JobExperienceManager.isCropFullyGrown(mockCrop(Material.COCOA, 1, 2)));
    }

    @Test
    public void testNonMaturityCropAlwaysTrue() {
        // 成熟概念のない作物は常に経験値対象（BlockDataを参照しない）
        Block pumpkin = mock(Block.class);
        when(pumpkin.getType()).thenReturn(Material.PUMPKIN);
        assertTrue(JobExperienceManager.isCropFullyGrown(pumpkin));

        Block sugarCane = mock(Block.class);
        when(sugarCane.getType()).thenReturn(Material.SUGAR_CANE);
        assertTrue(JobExperienceManager.isCropFullyGrown(sugarCane));

        Block melon = mock(Block.class);
        when(melon.getType()).thenReturn(Material.MELON);
        assertTrue(JobExperienceManager.isCropFullyGrown(melon));
    }

    @Test
    public void testNullBlockReturnsTrue() {
        assertTrue(JobExperienceManager.isCropFullyGrown(null));
    }
}
