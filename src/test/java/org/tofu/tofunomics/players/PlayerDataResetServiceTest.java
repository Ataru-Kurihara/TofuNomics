package org.tofu.tofunomics.players;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.database.DatabaseManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * PlayerDataResetService 単体テスト
 *
 * 本番 DatabaseManager でテーブルを作り（マイグレーションで足される列も含めて本番と同じ形）、
 * 対象プレイヤーの行だけが消え、他プレイヤーと設備の行が残ることを確かめる。
 */
public class PlayerDataResetServiceTest {

    private File dbFile;
    private DatabaseManager databaseManager;
    private Connection connection;
    private PlayerDataResetService service;

    private final UUID target = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        dbFile = File.createTempFile("tofunomics-reset-test", ".db");
        databaseManager = new DatabaseManager(dbFile.getAbsolutePath(), Logger.getLogger("test"));
        assertTrue(databaseManager.connect());
        databaseManager.createTables();
        connection = databaseManager.getConnection();
        service = new PlayerDataResetService(connection);

        for (UUID uuid : new UUID[] {target, other}) {
            seedPlayer(uuid);
        }
    }

    @After
    public void tearDown() {
        databaseManager.disconnect();
        dbFile.delete();
    }

    /** 1 プレイヤー分の行を、UUID を持つ全テーブルに入れる */
    private void seedPlayer(UUID uuid) throws SQLException {
        String u = uuid.toString();
        exec("INSERT INTO players (uuid, balance, bank_balance, rules_agreed, rules_agreed_at, name) "
            + "VALUES ('" + u + "', 12, 3456, 1, CURRENT_TIMESTAMP, 'name-" + u + "')");
        exec("INSERT INTO jobs (name, display_name) VALUES ('job-" + u + "', 'job')");
        int jobId = queryInt("SELECT id FROM jobs WHERE name = 'job-" + u + "'");
        exec("INSERT INTO player_jobs (uuid, job_id, level, experience) VALUES ('" + u + "', " + jobId + ", 30, 100)");
        exec("INSERT INTO job_history (uuid, job_id, max_level) VALUES ('" + u + "', " + jobId + ", 50)");
        exec("INSERT INTO job_changes (uuid, last_change_date) VALUES ('" + u + "', '2026-10-07')");
        exec("INSERT INTO player_skills (uuid, skill_id) VALUES ('" + u + "', 1)");
        exec("INSERT INTO player_first_acquisitions (uuid, job_name, item_key) VALUES ('" + u + "', 'miner', 'COAL')");
        exec("INSERT INTO trade_chests (world_name, x, y, z, job_type, created_by) "
            + "VALUES ('world', " + jobId + ", 64, 0, 'miner', '" + u + "')");
        exec("INSERT INTO player_trade_history (uuid, trade_chest_id, item_type, item_amount, sale_price) "
            + "VALUES ('" + u + "', 1, 'COAL', 1, 1)");
        exec("INSERT INTO quest_progress (player_uuid, quest_id) VALUES ('" + u + "', 'q1')");
        exec("INSERT INTO market_listings (seller_uuid, seller_name, item_data, material, amount, price) "
            + "VALUES ('" + u + "', 'n', 'data', 'COAL', 1, 10)");
        exec("INSERT INTO market_buy_orders (requester_uuid, requester_name, material, amount, price) "
            + "VALUES ('" + u + "', 'n', 'COAL', 1, 10)");
        exec("INSERT INTO market_service_requests (requester_uuid, requester_name, service_type, material, item_data, price) "
            + "VALUES ('" + u + "', 'n', 'repair', 'IRON_PICKAXE', 'data', 10)");
        exec("INSERT INTO land_ownership (owner_uuid, world_name, x1, y1, z1, x2, y2, z2, purchase_price) "
            + "VALUES ('" + u + "', 'world', 0, 0, 0, 1, 1, 1, 100)");
        exec("INSERT INTO housing_properties (property_name, world_name, daily_rent, is_available) "
            + "VALUES ('house-" + u + "', 'world', 10, 0)");
        int propertyId = queryInt("SELECT id FROM housing_properties WHERE property_name = 'house-" + u + "'");
        exec("INSERT INTO housing_rentals (property_id, tenant_uuid, rental_period, rental_days, total_cost, "
            + "start_date, end_date, status) VALUES (" + propertyId + ", '" + u + "', 'daily', 1, 10, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'active')");
        exec("INSERT INTO housing_rental_history (rental_id, property_id, tenant_uuid, action_type) "
            + "VALUES (1, " + propertyId + ", '" + u + "', 'rent')");
        exec("INSERT INTO farm_plots (plot_name, world_name, x1, y1, z1, x2, y2, z2, owner_uuid) "
            + "VALUES ('plot-" + u + "', 'world', 0, 0, 0, 1, 1, 1, '" + u + "')");
        exec("INSERT INTO player_inventories (player_uuid, inventory_data) VALUES ('" + u + "', 'inv')");
    }

    @Test
    public void 対象プレイヤーの行がすべて消える() throws SQLException {
        Map<String, Integer> affected = service.resetPlayerData(target, 1000.0);

        String[][] cleared = {
            {"player_jobs", "uuid"}, {"job_history", "uuid"}, {"job_changes", "uuid"},
            {"player_skills", "uuid"}, {"player_first_acquisitions", "uuid"},
            {"player_trade_history", "uuid"}, {"quest_progress", "player_uuid"},
            {"market_listings", "seller_uuid"}, {"market_buy_orders", "requester_uuid"},
            {"market_service_requests", "requester_uuid"}, {"land_ownership", "owner_uuid"},
            {"housing_rental_history", "tenant_uuid"}, {"housing_rentals", "tenant_uuid"},
            {"farm_plots", "owner_uuid"},
        };
        for (String[] table : cleared) {
            assertEquals(table[0] + " に対象の行が残っている", 0, count(table[0], table[1], target));
            assertEquals(table[0] + " の変更行数", Integer.valueOf(1), affected.get(table[0]));
        }
    }

    @Test
    public void players行は残り残高と規約同意が初期値に戻る() throws SQLException {
        service.resetPlayerData(target, 1000.0);

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance, bank_balance, rules_agreed, rules_agreed_at, name FROM players WHERE uuid = ?")) {
            statement.setString(1, target.toString());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0.0, result.getDouble("balance"), 0.0);
                assertEquals(1000.0, result.getDouble("bank_balance"), 0.0);
                assertFalse(result.getBoolean("rules_agreed"));
                assertNull(result.getString("rules_agreed_at"));
                assertEquals("name-" + target, result.getString("name"));
            }
        }
    }

    @Test
    public void 契約中の物件は空きに戻り区画と設備は残る() throws SQLException {
        service.resetPlayerData(target, 1000.0);

        assertEquals(1, queryInt(
            "SELECT is_available FROM housing_properties WHERE property_name = 'house-" + target + "'"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM farm_plots WHERE plot_name = 'plot-" + target + "'"));
        assertEquals(1, count("trade_chests", "created_by", target));
        assertEquals(1, count("player_inventories", "player_uuid", target));
    }

    @Test
    public void 他のプレイヤーの行は変わらない() throws SQLException {
        service.resetPlayerData(target, 1000.0);

        String[][] tables = {
            {"players", "uuid"}, {"player_jobs", "uuid"}, {"job_history", "uuid"}, {"job_changes", "uuid"},
            {"player_skills", "uuid"}, {"player_first_acquisitions", "uuid"},
            {"player_trade_history", "uuid"}, {"quest_progress", "player_uuid"},
            {"market_listings", "seller_uuid"}, {"market_buy_orders", "requester_uuid"},
            {"market_service_requests", "requester_uuid"}, {"land_ownership", "owner_uuid"},
            {"housing_rental_history", "tenant_uuid"}, {"housing_rentals", "tenant_uuid"},
            {"farm_plots", "owner_uuid"},
        };
        for (String[] table : tables) {
            assertEquals(table[0] + " から他プレイヤーの行が消えた", 1, count(table[0], table[1], other));
        }
        assertEquals(3456.0, queryDouble("SELECT bank_balance FROM players WHERE uuid = '" + other + "'"), 0.0);
        assertEquals(0, queryInt(
            "SELECT is_available FROM housing_properties WHERE property_name = 'house-" + other + "'"));
    }

    @Test
    public void 途中で失敗したら何も消えない() throws SQLException {
        // 処理の最後に更新する players を無くして失敗させる
        exec("ALTER TABLE players RENAME TO players_hidden");
        try {
            service.resetPlayerData(target, 1000.0);
            fail("SQLException が投げられるはず");
        } catch (SQLException expected) {
            // 期待どおり
        }
        exec("ALTER TABLE players_hidden RENAME TO players");

        assertEquals(1, count("player_jobs", "uuid", target));
        assertEquals(1, count("market_listings", "seller_uuid", target));
        assertEquals(1, count("farm_plots", "owner_uuid", target));
        assertTrue(connection.getAutoCommit());
    }

    private void exec(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int count(String table, String column, UUID uuid) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = '" + uuid + "'");
    }

    private int queryInt(String sql) throws SQLException {
        return (int) queryDouble(sql);
    }

    private double queryDouble(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getDouble(1);
        }
    }
}
