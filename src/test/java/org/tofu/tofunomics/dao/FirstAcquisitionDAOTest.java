package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * FirstAcquisitionDAO 単体テスト
 *
 * 本番 DatabaseManager / HikariDatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する
 * （UNIQUE(uuid, job_name, item_key) 制約・INSERT OR IGNORE による二重登録防止・メモリキャッシュ整合性を再現）。
 */
public class FirstAcquisitionDAOTest {

    private Connection connection;
    private FirstAcquisitionDAO dao;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS player_first_acquisitions (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    uuid TEXT NOT NULL," +
                "    job_name TEXT NOT NULL," +
                "    item_key TEXT NOT NULL," +
                "    acquired_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    UNIQUE(uuid, job_name, item_key)" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new FirstAcquisitionDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testFirstThenRecordedBecomesNotFirst() {
        UUID player = UUID.randomUUID();

        assertTrue("未記録なら初回であるべき", dao.isFirstAcquisition(player, "miner", "COAL_ORE"));

        dao.recordAcquisition(player, "miner", "COAL_ORE");

        assertFalse("記録後は初回でないべき", dao.isFirstAcquisition(player, "miner", "COAL_ORE"));
    }

    @Test
    public void testDifferentItemKeyIsIndependent() {
        UUID player = UUID.randomUUID();
        dao.recordAcquisition(player, "miner", "COAL_ORE");

        assertTrue("別アイテム種類は別々に初回判定されるべき",
                dao.isFirstAcquisition(player, "miner", "IRON_ORE"));
    }

    @Test
    public void testDifferentJobIsIndependent() {
        UUID player = UUID.randomUUID();
        // 同じ Material 名でも職業が違えば別判定（建築家のSTONE設置と鉱夫の採掘など）
        dao.recordAcquisition(player, "miner", "STONE");

        assertTrue("職業が異なれば別々に初回判定されるべき",
                dao.isFirstAcquisition(player, "architect", "STONE"));
    }

    @Test
    public void testDifferentPlayerIsIndependent() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        dao.recordAcquisition(playerA, "miner", "COAL_ORE");

        assertTrue("別プレイヤーは別々に初回判定されるべき",
                dao.isFirstAcquisition(playerB, "miner", "COAL_ORE"));
    }

    @Test
    public void testFishermanFixedKeyOnlyFirstOnce() {
        UUID player = UUID.randomUUID();
        // 釣り人は固定キーで「最初の1匹だけ」を表現する
        assertTrue(dao.isFirstAcquisition(player, "fisherman", "ANY_FISH"));
        dao.recordAcquisition(player, "fisherman", "ANY_FISH");
        assertFalse("2匹目以降は初回でないべき", dao.isFirstAcquisition(player, "fisherman", "ANY_FISH"));
    }

    @Test
    public void testRecordTwiceDoesNotDuplicate() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.recordAcquisition(player, "miner", "COAL_ORE");
        dao.recordAcquisition(player, "miner", "COAL_ORE");

        try (Statement statement = connection.createStatement()) {
            java.sql.ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM player_first_acquisitions WHERE uuid = '" + player + "'");
            assertTrue(rs.next());
            assertEquals("重複登録されないべき", 1, rs.getInt(1));
        }
    }

    @Test
    public void testCacheReflectsDatabaseAfterReload() {
        UUID player = UUID.randomUUID();
        dao.recordAcquisition(player, "woodcutter", "OAK_LOG");

        // 別インスタンス（キャッシュ未保持）でも DB から正しくロードされること
        FirstAcquisitionDAO freshDao = new FirstAcquisitionDAO(connection);
        assertFalse("再起動相当でも永続化された記録が反映されるべき",
                freshDao.isFirstAcquisition(player, "woodcutter", "OAK_LOG"));
    }

    @Test
    public void testUnloadCacheReloadsFromDatabase() {
        UUID player = UUID.randomUUID();
        dao.recordAcquisition(player, "miner", "COAL_ORE");
        dao.unloadCache(player);

        // キャッシュ解放後も DB から復元され、初回でないと判定されるべき
        assertFalse(dao.isFirstAcquisition(player, "miner", "COAL_ORE"));
    }
}
