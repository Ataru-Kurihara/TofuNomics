package org.tofu.tofunomics.players;

import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * tofuNomics で死んだときの復活先のテスト。
 * 以前は survival で寝た人が tofuNomics で死ぬと survival のベッドに復活していた。
 * また tofuNomics で寝ると survival のベッドが上書きされ、survival で死ぬと tofuNomics に復活しえた。
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

    @Test
    public void ベッドの記録は文字列で往復できる() {
        assertArrayEquals(new int[]{-96, 76, -247},
                TofuNomicsRespawnListener.decodeBed(TofuNomicsRespawnListener.encodeBed(-96, 76, -247)));
    }

    @Test
    public void 壊れたベッドの記録は使わない() {
        assertNull(TofuNomicsRespawnListener.decodeBed(null));
        assertNull(TofuNomicsRespawnListener.decodeBed("1,2"));
        assertNull(TofuNomicsRespawnListener.decodeBed("a,b,c"));
    }

    @Test
    public void ベッドの上に復活させる() {
        World world = mock(World.class);
        Location loc = TofuNomicsRespawnListener.bedStandLocation(world, 10, 64, -5);
        assertEquals(10.5, loc.getX(), 0.0001);
        assertEquals(64.5625, loc.getY(), 0.0001);
        assertEquals(-4.5, loc.getZ(), 0.0001);
    }

    private TofuNomicsRespawnListener listener() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("TofuNomics");
        return new TofuNomicsRespawnListener(plugin, mock(ConfigManager.class), Logger.getLogger("test"));
    }

    /** Location は World を弱参照で持つので、テスト中に消えないよう持っておく */
    private final java.util.List<World> worlds = new java.util.ArrayList<>();

    private Location locationIn(String worldName, int x, int y, int z) {
        World world = mock(World.class);
        worlds.add(world);
        when(world.getName()).thenReturn(worldName);
        return new Location(world, x, y, z);
    }

    @Test
    public void tofuNomicsのベッドは本体のベッド地点を変えずに記録する() {
        Player player = mock(Player.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(data);
        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(player, locationIn("tofuNomics", -96, 76, -247), false, PlayerSpawnChangeEvent.Cause.BED);

        listener().onSetSpawn(event);

        assertTrue(event.isCancelled());
        verify(data).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), eq("-96,76,-247"));
    }

    @Test
    public void survivalのベッドは今までどおり本体に任せる() {
        Player player = mock(Player.class);
        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(player, locationIn("survival", 0, 64, 0), false, PlayerSpawnChangeEvent.Cause.BED);

        listener().onSetSpawn(event);

        assertFalse(event.isCancelled());
        verify(player, never()).getPersistentDataContainer();
    }

    @Test
    public void tofuNomicsのリスポーンアンカーは復活地点にしない() {
        Player player = mock(Player.class);
        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(player, locationIn("tofuNomics", 0, 64, 0), false, PlayerSpawnChangeEvent.Cause.RESPAWN_ANCHOR);

        listener().onSetSpawn(event);

        assertTrue(event.isCancelled());
        verify(player, never()).getPersistentDataContainer();
    }
}
