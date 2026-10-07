package org.tofu.tofunomics.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 保存の入口（saveInventory）が、復元前のプレイヤーを DB に書かないことの回帰テスト。
 * ワールド退出・切断・自動保存・プラグイン停止の 4 経路はすべてここを通る。
 */
public class PlayerInventoryManagerSaveGuardTest {

    private MockedStatic<Bukkit> bukkit;
    private Connection connection;
    private Player player;
    private PlayerInventoryManager manager;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        connection = mock(Connection.class);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("tester");
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));

        manager = new PlayerInventoryManager(plugin, connection);
    }

    @After
    public void tearDown() {
        bukkit.close();
    }

    @Test
    public void 復元前の保存はDBに触らず見送る() throws Exception {
        assertFalse(manager.saveInventory(player));
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    public void 復元済みなら保存する() throws Exception {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        manager.getRestoreTracker().markRestored(player.getUniqueId());

        assertTrue(manager.saveInventory(player));
        verify(stmt).executeUpdate();
    }

    @Test
    public void 読み込みに失敗したら保存できないまま() throws Exception {
        when(connection.prepareStatement(anyString())).thenThrow(new java.sql.SQLException("読めない"));

        assertEquals(InventoryRestoreTracker.LoadResult.FAILED, manager.loadInventory(player));
        assertFalse(manager.getRestoreTracker().canSave(player.getUniqueId()));
        assertFalse(manager.saveInventory(player));
    }

    @Test
    public void 壊れた保存データは手持ちに反映せず保存もできない() throws Exception {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("inventory_data")).thenReturn("これはBase64ではない!!");

        assertEquals(InventoryRestoreTracker.LoadResult.FAILED, manager.loadInventory(player));
        verify(player.getInventory(), never()).setContents(any());
        assertFalse(manager.getRestoreTracker().canSave(player.getUniqueId()));
    }

    @Test
    public void 保存データが無い初回入場は以後保存できる() throws Exception {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertEquals(InventoryRestoreTracker.LoadResult.NO_SAVED_DATA, manager.loadInventory(player));
        assertTrue(manager.getRestoreTracker().canSave(player.getUniqueId()));
    }
}
