package org.tofu.tofunomics.models;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * FarmPlot の範囲判定・所有判定テスト
 */
public class FarmPlotTest {

    private Location loc(String worldName, int x, int y, int z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(x);
        when(location.getBlockY()).thenReturn(y);
        when(location.getBlockZ()).thenReturn(z);
        return location;
    }

    private FarmPlot plot() {
        // (10,60,10) - (20,70,20) の立方体
        return new FarmPlot("A", "world", 10, 60, 10, 20, 70, 20, "farmplot_a");
    }

    @Test
    public void testContainsInside() {
        assertTrue(plot().contains(loc("world", 15, 65, 15)));
        assertTrue(plot().contains(loc("world", 10, 60, 10))); // 境界
        assertTrue(plot().contains(loc("world", 20, 70, 20))); // 境界
    }

    @Test
    public void testContainsOutside() {
        assertFalse(plot().contains(loc("world", 9, 65, 15)));   // x範囲外
        assertFalse(plot().contains(loc("world", 15, 59, 15)));  // y範囲外
        assertFalse(plot().contains(loc("world", 15, 65, 21)));  // z範囲外
    }

    @Test
    public void testContainsDifferentWorld() {
        assertFalse(plot().contains(loc("nether", 15, 65, 15)));
    }

    @Test
    public void testContainsNull() {
        assertFalse(plot().contains(null));
    }

    @Test
    public void testIsAssigned() {
        FarmPlot p = plot();
        assertFalse(p.isAssigned());
        p.setOwnerUuid("");
        assertFalse(p.isAssigned());
        p.setOwnerUuid("abc-uuid");
        assertTrue(p.isAssigned());
    }
}
