package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.MarketServiceRequest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * MarketServiceRequestDAO 単体テスト
 *
 * 本番 DatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する
 * （AUTOINCREMENT・DEFAULT CURRENT_TIMESTAMP・epoch millis 比較など SQLite 固有挙動を本番同等に再現）。
 */
public class MarketServiceRequestDAOTest {

    private Connection connection;
    private MarketServiceRequestDAO dao;
    private final double DELTA = 0.001;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS market_service_requests (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    requester_uuid TEXT NOT NULL," +
                "    requester_name TEXT NOT NULL," +
                "    service_type TEXT NOT NULL," +
                "    material TEXT NOT NULL," +
                "    item_data TEXT NOT NULL," +
                "    enchant_type TEXT," +
                "    enchant_level INTEGER," +
                "    price REAL NOT NULL," +
                "    status TEXT NOT NULL DEFAULT 'open'," +
                "    worker_uuid TEXT," +
                "    result_item_data TEXT," +
                "    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "    expires_at INTEGER," +
                "    fulfilled_at TIMESTAMP" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new MarketServiceRequestDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private int insertRepair(UUID requester, String name, double price, Long expiresAtMillis) throws SQLException {
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester, name, "DIAMOND_PICKAXE", "REPAIR_ITEM_DATA", price);
        req.setExpiresAt(expiresAtMillis);
        return dao.insertServiceRequest(req);
    }

    private int insertEnchant(UUID requester, String name, String enchant, int level, double price) throws SQLException {
        MarketServiceRequest req = MarketServiceRequest.createEnchant(
                requester, name, "DIAMOND_SWORD", "ENCHANT_ITEM_DATA", enchant, level, price);
        return dao.insertServiceRequest(req);
    }

    @Test
    public void testInsertAndGetByIdRepair() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertRepair(requester, "Alice", 100.0, null);

        assertTrue("生成された id は正の値であるべき", id > 0);

        MarketServiceRequest fetched = dao.getById(id);
        assertNotNull("挿入した依頼が取得できるべき", fetched);
        assertEquals(requester, fetched.getRequesterUuid());
        assertEquals("Alice", fetched.getRequesterName());
        assertEquals(MarketServiceRequest.TYPE_REPAIR, fetched.getServiceType());
        assertTrue(fetched.isRepair());
        assertEquals("DIAMOND_PICKAXE", fetched.getMaterial());
        assertEquals("REPAIR_ITEM_DATA", fetched.getItemData());
        assertEquals(100.0, fetched.getPrice(), DELTA);
        assertEquals(MarketServiceRequest.STATUS_OPEN, fetched.getStatus());
        assertNull("修理依頼の enchant_type は null であるべき", fetched.getEnchantType());
        assertNull("修理依頼の enchant_level は null であるべき", fetched.getEnchantLevel());
        assertNull("無期限依頼の expires_at は null であるべき", fetched.getExpiresAt());
        assertNull("未成立の worker_uuid は null であるべき", fetched.getWorkerUuid());
        assertNull("未成立の result_item_data は null であるべき", fetched.getResultItemData());
        assertNull("未成立の fulfilled_at は null であるべき", fetched.getFulfilledAt());
        assertNotNull("listed_at は DEFAULT で自動設定されるべき", fetched.getListedAt());
    }

    @Test
    public void testInsertAndGetByIdEnchant() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertEnchant(requester, "Bob", "sharpness", 5, 200.0);

        MarketServiceRequest fetched = dao.getById(id);
        assertNotNull(fetched);
        assertEquals(MarketServiceRequest.TYPE_ENCHANT, fetched.getServiceType());
        assertTrue(fetched.isEnchant());
        assertEquals("sharpness", fetched.getEnchantType());
        assertNotNull("エンチャントレベルが保存されるべき", fetched.getEnchantLevel());
        assertEquals(5, (int) fetched.getEnchantLevel());
        assertEquals(200.0, fetched.getPrice(), DELTA);
    }

    @Test
    public void testGetByIdNotFound() throws SQLException {
        assertNull("存在しないIDはnullを返すべき", dao.getById(9999));
    }

    @Test
    public void testGetOpenRequests() throws SQLException {
        insertRepair(UUID.randomUUID(), "Alice", 100.0, null);
        insertEnchant(UUID.randomUUID(), "Bob", "unbreaking", 3, 50.0);

        List<MarketServiceRequest> open = dao.getOpenRequests();
        assertEquals("open 依頼が2件取得できるべき", 2, open.size());
    }

    @Test
    public void testCountOpenByRequester() throws SQLException {
        UUID requester = UUID.randomUUID();
        insertRepair(requester, "Alice", 100.0, null);
        insertRepair(requester, "Alice", 120.0, null);
        insertRepair(UUID.randomUUID(), "Other", 80.0, null);

        assertEquals("対象プレイヤーの open 依頼は2件", 2, dao.countOpenByRequester(requester));
    }

    @Test
    public void testGetByRequester() throws SQLException {
        UUID requester = UUID.randomUUID();
        insertRepair(requester, "Alice", 100.0, null);
        insertEnchant(requester, "Alice", "sharpness", 1, 30.0);
        insertRepair(UUID.randomUUID(), "Other", 80.0, null);

        List<MarketServiceRequest> mine = dao.getByRequester(requester);
        assertEquals("対象プレイヤーの依頼は2件", 2, mine.size());
    }

    @Test
    public void testMarkAsFulfilledSucceedsOnceAndPreventsDouble() throws SQLException {
        UUID requester = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        int id = insertRepair(requester, "Alice", 100.0, null);

        long now = 1_700_000_000_000L;
        boolean first = dao.markAsFulfilled(id, worker, "RESULT_DATA", now);
        assertTrue("初回の成立は成功するべき", first);

        // 二重成立防止: 既に fulfilled なので2回目は失敗
        boolean second = dao.markAsFulfilled(id, UUID.randomUUID(), "OTHER_DATA", now);
        assertFalse("2回目の成立は楽観ロックで失敗するべき", second);

        MarketServiceRequest fetched = dao.getById(id);
        assertEquals(MarketServiceRequest.STATUS_FULFILLED, fetched.getStatus());
        assertEquals(worker, fetched.getWorkerUuid());
        assertEquals("RESULT_DATA", fetched.getResultItemData());
        assertNotNull(fetched.getFulfilledAt());
    }

    @Test
    public void testUpdateStatusConditional() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertRepair(requester, "Alice", 100.0, null);

        boolean ok = dao.updateStatusConditional(id, MarketServiceRequest.STATUS_OPEN, MarketServiceRequest.STATUS_CANCELLED);
        assertTrue("open → cancelled は成功するべき", ok);

        // 既に cancelled なので open 前提の更新は失敗
        boolean fail = dao.updateStatusConditional(id, MarketServiceRequest.STATUS_OPEN, MarketServiceRequest.STATUS_EXPIRED);
        assertFalse("状態不一致の更新は失敗するべき", fail);

        assertEquals(MarketServiceRequest.STATUS_CANCELLED, dao.getById(id).getStatus());
    }

    @Test
    public void testGetExpiredOpenRequests() throws SQLException {
        long now = 1_700_000_000_000L;
        // 期限切れ（過去）
        insertRepair(UUID.randomUUID(), "Expired", 100.0, now - 1000L);
        // 未来期限（対象外）
        insertRepair(UUID.randomUUID(), "Future", 100.0, now + 1_000_000L);
        // 無期限（対象外）
        insertRepair(UUID.randomUUID(), "NoExpiry", 100.0, null);

        List<MarketServiceRequest> expired = dao.getExpiredOpenRequests(now);
        assertEquals("期限切れの open 依頼は1件のみ", 1, expired.size());
        assertEquals("Expired", expired.get(0).getRequesterName());
    }

    @Test
    public void testExpiredExcludesNonOpen() throws SQLException {
        long now = 1_700_000_000_000L;
        int id = insertRepair(UUID.randomUUID(), "Done", 100.0, now - 1000L);
        // 既に成立済みなら期限切れ対象から除外される
        dao.markAsFulfilled(id, UUID.randomUUID(), "RESULT", now - 500L);

        List<MarketServiceRequest> expired = dao.getExpiredOpenRequests(now);
        assertEquals("fulfilled 済みは期限切れ対象に含めない", 0, expired.size());
    }
}
