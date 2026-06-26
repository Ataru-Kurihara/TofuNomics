package org.tofu.tofunomics.experience;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.FirstAcquisitionDAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobExperienceManager の初回入手ボーナスロジックのテスト。
 * 初回は経験値が倍率分上乗せされ、2回目以降は通常値のまま返ることを検証する。
 */
public class JobExperienceManagerFirstAcquisitionTest {

    private ConfigManager configManager;
    private JobExperienceManager manager;
    private Connection connection;
    private FirstAcquisitionDAO dao;
    private Player player;
    private final UUID playerUuid = UUID.randomUUID();

    @Before
    public void setUp() throws SQLException {
        configManager = mock(ConfigManager.class);
        when(configManager.isFirstAcquisitionBonusEnabled()).thenReturn(true);
        when(configManager.getFirstAcquisitionMultiplier()).thenReturn(5.0);
        when(configManager.getFirstAcquisitionMessage())
            .thenReturn("&6初めての入手！ &e×%multiplier% &aボーナス");

        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_first_acquisitions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT NOT NULL, job_name TEXT NOT NULL, item_key TEXT NOT NULL," +
                    "acquired_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(uuid, job_name, item_key));");
        }
        dao = new FirstAcquisitionDAO(connection);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        manager = new JobExperienceManager(configManager, null, null, null, null, null);
        manager.setFirstAcquisitionDAO(dao);
    }

    @Test
    public void testFirstAcquisitionMultipliesExp() {
        // 初回は 10.0 × 5.0 = 50.0
        double result = manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        assertEquals(50.0, result, 0.0001);
    }

    @Test
    public void testSecondAcquisitionReturnsBaseExp() {
        // 1回目で記録される
        manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        // 2回目は通常値のまま
        double second = manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        assertEquals(10.0, second, 0.0001);
    }

    @Test
    public void testDifferentItemKeyGetsBonusAgain() {
        manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        // 別の鉱石は再び初回ボーナス
        double iron = manager.applyFirstAcquisitionBonus(player, "miner", "IRON_ORE", 8.0);
        assertEquals(40.0, iron, 0.0001);
    }

    @Test
    public void testFishermanOnlyFirstFishOnce() {
        // 釣り人は固定キー "ANY_FISH" のため、1匹目だけボーナス
        double first = manager.applyFirstAcquisitionBonus(player, "fisherman", "ANY_FISH", 6.0);
        double second = manager.applyFirstAcquisitionBonus(player, "fisherman", "ANY_FISH", 6.0);
        assertEquals(30.0, first, 0.0001);
        assertEquals(6.0, second, 0.0001);
    }

    @Test
    public void testFirstAcquisitionSendsMessageOnce() {
        // 初回はメッセージ表示、2回目は表示しない
        manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);

        // 倍率5.0は "5" に整形され、§ に変換されたメッセージが1回だけ送られる
        verify(player, times(1)).sendMessage(contains("×5"));
        verify(player, times(1)).sendMessage(anyString());
    }

    @Test
    public void testDisabledReturnsBaseExp() {
        when(configManager.isFirstAcquisitionBonusEnabled()).thenReturn(false);
        double result = manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        assertEquals(10.0, result, 0.0001);
    }

    @Test
    public void testNullDaoReturnsBaseExp() {
        manager.setFirstAcquisitionDAO(null);
        double result = manager.applyFirstAcquisitionBonus(player, "miner", "COAL_ORE", 10.0);
        assertEquals(10.0, result, 0.0001);
    }
}
