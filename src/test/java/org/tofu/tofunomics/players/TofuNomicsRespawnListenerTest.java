package org.tofu.tofunomics.players;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * tofuNomics で死んだときの復活先のテスト。
 * 以前は survival で寝た人が tofuNomics で死ぬと survival のベッドに復活していた。
 */
public class TofuNomicsRespawnListenerTest {

    @Test
    public void tofuNomicsで死んでsurvivalに復活するなら戻す() {
        assertTrue(TofuNomicsRespawnListener.needsRedirect("tofuNomics", "survival"));
    }

    @Test
    public void 復活先のワールドが分からないときも戻す() {
        assertTrue(TofuNomicsRespawnListener.needsRedirect("tofuNomics", null));
    }

    @Test
    public void tofuNomicsの中のベッドへの復活はそのまま() {
        assertFalse(TofuNomicsRespawnListener.needsRedirect("tofuNomics", "tofuNomics"));
    }

    @Test
    public void ほかのワールドで死んだときは触らない() {
        assertFalse(TofuNomicsRespawnListener.needsRedirect("survival", "survival"));
        assertFalse(TofuNomicsRespawnListener.needsRedirect("TeamDeathMatch", "world"));
    }

    @Test
    public void スポーン設定がtofuNomicsを指していればその座標に復活する() {
        World world = mock(World.class);
        Location loc = TofuNomicsRespawnListener.resolveSpawn(world, true, "tofuNomics", -97, 76, -253, 90f, 5f);
        assertSame(world, loc.getWorld());
        assertEquals(-96.5, loc.getX(), 0.0001);
        assertEquals(76.0, loc.getY(), 0.0001);
        assertEquals(-252.5, loc.getZ(), 0.0001);
        assertEquals(90f, loc.getYaw(), 0.0001);
        verify(world, never()).getSpawnLocation();
    }

    @Test
    public void スポーン設定が無効ならワールドのスポーンに復活する() {
        World world = mock(World.class);
        Location worldSpawn = new Location(world, 1, 2, 3);
        when(world.getSpawnLocation()).thenReturn(worldSpawn);
        assertSame(worldSpawn, TofuNomicsRespawnListener.resolveSpawn(world, false, "tofuNomics", 0, 64, 0, 0f, 0f));
    }

    @Test
    public void スポーン設定が別ワールドを指していればワールドのスポーンに復活する() {
        World world = mock(World.class);
        Location worldSpawn = new Location(world, 1, 2, 3);
        when(world.getSpawnLocation()).thenReturn(worldSpawn);
        assertSame(worldSpawn, TofuNomicsRespawnListener.resolveSpawn(world, true, "world", 0, 64, 0, 0f, 0f));
    }
}
