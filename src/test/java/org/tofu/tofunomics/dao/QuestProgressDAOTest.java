package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.QuestProgress;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * QuestProgressDAO 単体テスト
 *
 * 本番 DatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する
 * （AUTOINCREMENT・DEFAULT CURRENT_TIMESTAMP・UNIQUE 制約・楽観ロックによる二重納品防止を本番同等に再現）。
 */
public class QuestProgressDAOTest {

    private Connection connection;
    private QuestProgressDAO dao;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS quest_progress (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    player_uuid TEXT NOT NULL," +
                "    quest_id TEXT NOT NULL," +
                "    status TEXT NOT NULL DEFAULT 'accepted'," +
                "    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "    completed_at TIMESTAMP," +
                "    UNIQUE(player_uuid, quest_id)" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new QuestProgressDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testInsertAndIsAccepted() throws SQLException {
        UUID player = UUID.randomUUID();
        int id = dao.insertProgress(new QuestProgress(player, "rotten_flesh_1"));

        assertTrue("生成された id は正の値であるべき", id > 0);
        assertTrue("受注直後は accepted であるべき", dao.isAccepted(player, "rotten_flesh_1"));

        QuestProgress fetched = dao.getByPlayerAndQuest(player, "rotten_flesh_1");
        assertNotNull("挿入した受注が取得できるべき", fetched);
        assertEquals(player, fetched.getPlayerUuid());
        assertEquals("rotten_flesh_1", fetched.getQuestId());
        assertEquals(QuestProgress.STATUS_ACCEPTED, fetched.getStatus());
        assertNotNull("accepted_at は DEFAULT で自動設定されるべき", fetched.getAcceptedAt());
        assertNull("未完了の completed_at は null であるべき", fetched.getCompletedAt());
    }

    @Test(expected = SQLException.class)
    public void testDuplicateInsertViolatesUnique() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.insertProgress(new QuestProgress(player, "bone_1"));
        // 同一プレイヤー・同一クエストの二重受注は UNIQUE 制約違反
        dao.insertProgress(new QuestProgress(player, "bone_1"));
    }

    @Test
    public void testGetByPlayerAndQuestNotFound() throws SQLException {
        assertNull("存在しない受注は null を返すべき",
                dao.getByPlayerAndQuest(UUID.randomUUID(), "unknown_quest"));
    }

    @Test
    public void testCountAndGetAcceptedByPlayer() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.insertProgress(new QuestProgress(player, "rotten_flesh_1"));
        dao.insertProgress(new QuestProgress(player, "bone_1"));
        dao.insertProgress(new QuestProgress(player, "string_1"));
        // 別プレイヤーの受注はカウントに含めない
        dao.insertProgress(new QuestProgress(UUID.randomUUID(), "rotten_flesh_1"));

        assertEquals("対象プレイヤーの受注中は3件", 3, dao.countAcceptedByPlayer(player));

        List<QuestProgress> accepted = dao.getAcceptedByPlayer(player);
        assertEquals("受注中リストは3件", 3, accepted.size());
    }

    @Test
    public void testMarkAsCompletedOnceAndPreventsDouble() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.insertProgress(new QuestProgress(player, "gunpowder_1"));

        long now = 1_700_000_000_000L;
        boolean first = dao.markAsCompleted(player, "gunpowder_1", now);
        assertTrue("初回の納品完了は成功するべき", first);

        // 二重納品防止: 既に completed なので2回目は失敗（楽観ロック）
        boolean second = dao.markAsCompleted(player, "gunpowder_1", now);
        assertFalse("2回目の納品完了は失敗するべき", second);

        assertFalse("完了後は accepted ではない", dao.isAccepted(player, "gunpowder_1"));

        QuestProgress fetched = dao.getByPlayerAndQuest(player, "gunpowder_1");
        assertEquals(QuestProgress.STATUS_COMPLETED, fetched.getStatus());
        assertNotNull("completed_at が設定されるべき", fetched.getCompletedAt());
    }

    @Test
    public void testMarkAsCompletedWithoutAcceptReturnsFalse() throws SQLException {
        // 受注していないクエストの完了化は何も更新しない
        boolean ok = dao.markAsCompleted(UUID.randomUUID(), "spider_eye_1", 1_700_000_000_000L);
        assertFalse("未受注クエストの完了化は失敗するべき", ok);
    }

    @Test
    public void testDeleteAllowsReaccept() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.insertProgress(new QuestProgress(player, "ender_pearl_1"));
        dao.markAsCompleted(player, "ender_pearl_1", 1_700_000_000_000L);

        // 完了行を削除すれば同一クエストを再受注できる（リピート受注）
        boolean deleted = dao.deleteByPlayerAndQuest(player, "ender_pearl_1");
        assertTrue("受注行の削除に成功するべき", deleted);
        assertNull("削除後は行が存在しない", dao.getByPlayerAndQuest(player, "ender_pearl_1"));

        int newId = dao.insertProgress(new QuestProgress(player, "ender_pearl_1"));
        assertTrue("削除後は同一クエストを再受注できるべき", newId > 0);
        assertTrue("再受注後は accepted であるべき", dao.isAccepted(player, "ender_pearl_1"));
    }

    @Test
    public void testDeleteNonExistentReturnsFalse() throws SQLException {
        assertFalse("存在しない受注の削除は false を返すべき",
                dao.deleteByPlayerAndQuest(UUID.randomUUID(), "nope"));
    }

    @Test
    public void testIsAcceptedFalseForOtherQuest() throws SQLException {
        UUID player = UUID.randomUUID();
        dao.insertProgress(new QuestProgress(player, "rotten_flesh_1"));
        assertFalse("受注していない別クエストは accepted ではない",
                dao.isAccepted(player, "bone_1"));
    }
}
