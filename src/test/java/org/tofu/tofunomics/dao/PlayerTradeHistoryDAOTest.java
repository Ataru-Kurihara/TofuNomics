package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.PlayerTradeHistory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * PlayerTradeHistoryDAO 単体テスト
 *
 * 本番 DatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する。
 * 取引所NPCへの売却は trade_chest_id に番兵値を入れて記録するため、
 * その値でも書き込めることを固定する。
 */
public class PlayerTradeHistoryDAOTest {

    private Connection connection;
    private PlayerTradeHistoryDAO dao;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS player_trade_history (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    uuid TEXT NOT NULL," +
                "    trade_chest_id INTEGER NOT NULL," +
                "    item_type TEXT NOT NULL," +
                "    item_amount INTEGER NOT NULL," +
                "    sale_price REAL NOT NULL," +
                "    job_bonus REAL DEFAULT 0.0," +
                "    player_job TEXT," +
                "    player_job_level INTEGER DEFAULT 1," +
                "    traded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new PlayerTradeHistoryDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void 取引所NPCへの売却を記録できる() throws SQLException {
        String uuid = UUID.randomUUID().toString();

        PlayerTradeHistory history = new PlayerTradeHistory(
            uuid,
            PlayerTradeHistoryDAO.TRADING_NPC_CHEST_ID,
            "IRON_ORE",
            32,
            192.0,
            "miner",
            12
        );
        history.setJobBonus(32.0);

        assertTrue("記録できること", dao.insert(history));

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT uuid, trade_chest_id, item_type, item_amount, sale_price, job_bonus,"
                     + " player_job, player_job_level FROM player_trade_history")) {

            assertTrue("行が入っていること", rs.next());
            assertEquals(uuid, rs.getString("uuid"));
            assertEquals(PlayerTradeHistoryDAO.TRADING_NPC_CHEST_ID, rs.getInt("trade_chest_id"));
            assertEquals("IRON_ORE", rs.getString("item_type"));
            assertEquals(32, rs.getInt("item_amount"));
            assertEquals(192.0, rs.getDouble("sale_price"), 0.001);
            assertEquals(32.0, rs.getDouble("job_bonus"), 0.001);
            assertEquals("miner", rs.getString("player_job"));
            assertEquals(12, rs.getInt("player_job_level"));
            assertFalse("1行だけであること", rs.next());
        }
    }

    @Test
    public void 無職での売却も記録できる() throws SQLException {
        PlayerTradeHistory history = new PlayerTradeHistory(
            UUID.randomUUID().toString(),
            PlayerTradeHistoryDAO.TRADING_NPC_CHEST_ID,
            "STONE",
            64,
            64.0,
            null,
            1
        );

        assertTrue("職業が無くても記録できること", dao.insert(history));

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT player_job FROM player_trade_history")) {
            assertTrue(rs.next());
            assertNull(rs.getString("player_job"));
        }
    }
}
