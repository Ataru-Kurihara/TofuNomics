package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.MarketBuyOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * MarketBuyOrderDAO 単体テスト
 *
 * 本番 DatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する
 * （AUTOINCREMENT・DEFAULT CURRENT_TIMESTAMP・epoch millis 比較など SQLite 固有挙動を本番同等に再現）。
 */
public class MarketBuyOrderDAOTest {

    private Connection connection;
    private MarketBuyOrderDAO dao;
    private final double DELTA = 0.001;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS market_buy_orders (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    requester_uuid TEXT NOT NULL," +
                "    requester_name TEXT NOT NULL," +
                "    material TEXT NOT NULL," +
                "    amount INTEGER NOT NULL," +
                "    price REAL NOT NULL," +
                "    status TEXT NOT NULL DEFAULT 'open'," +
                "    supplier_uuid TEXT," +
                "    item_data TEXT," +
                "    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "    expires_at INTEGER," +
                "    fulfilled_at TIMESTAMP" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new MarketBuyOrderDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * テスト用の open 募集を作成して id を返すヘルパー
     */
    private int insertOpen(UUID requester, String requesterName, int amount, double price, Long expiresAtMillis) throws SQLException {
        MarketBuyOrder order = new MarketBuyOrder(
                requester, requesterName, "DIAMOND", amount, price, expiresAtMillis);
        return dao.insertBuyOrder(order);
    }

    @Test
    public void testInsertAndGetById() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, null);

        assertTrue("生成された id は正の値であるべき", id > 0);

        MarketBuyOrder fetched = dao.getById(id);
        assertNotNull("挿入した募集が取得できるべき", fetched);
        assertEquals(requester, fetched.getRequesterUuid());
        assertEquals("Alice", fetched.getRequesterName());
        assertEquals("DIAMOND", fetched.getMaterial());
        assertEquals(10, fetched.getAmount());
        assertEquals(100.0, fetched.getPrice(), DELTA);
        assertEquals(MarketBuyOrder.STATUS_OPEN, fetched.getStatus());
        assertNull("無期限募集の expires_at は null であるべき", fetched.getExpiresAtMillis());
        assertNull("未成立の supplier_uuid は null であるべき", fetched.getSupplierUuid());
        assertNull("未成立の item_data は null であるべき", fetched.getItemData());
        assertNull("未成立の fulfilled_at は null であるべき", fetched.getFulfilledAt());
        assertNotNull("listed_at は DEFAULT で自動設定されるべき", fetched.getListedAt());
    }

    @Test
    public void testGetByIdNotFound() throws SQLException {
        assertNull("存在しない id は null を返すべき", dao.getById(9999));
    }

    @Test
    public void testGetOpenOrders() throws SQLException {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        int id1 = insertOpen(r1, "Alice", 10, 100.0, null);
        insertOpen(r2, "Bob", 5, 200.0, null);

        // 1件を fulfilled にする → open 一覧から除外される
        dao.markAsFulfilled(id1, UUID.randomUUID(), "SUPPLIED_BASE64", System.currentTimeMillis());

        List<MarketBuyOrder> open = dao.getOpenOrders();
        assertEquals("open のみ取得されるべき", 1, open.size());
        assertEquals("Bob", open.get(0).getRequesterName());
    }

    @Test
    public void testGetOrdersByRequester() throws SQLException {
        UUID requester = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        int id1 = insertOpen(requester, "Alice", 10, 100.0, null);
        insertOpen(requester, "Alice", 8, 150.0, null);
        insertOpen(other, "Bob", 5, 200.0, null);

        // status 問わず自分の募集を全件取得
        dao.markAsFulfilled(id1, UUID.randomUUID(), "SUPPLIED_BASE64", System.currentTimeMillis());

        List<MarketBuyOrder> mine = dao.getOrdersByRequester(requester);
        assertEquals("自分の募集は status 問わず全件取得されるべき", 2, mine.size());
    }

    @Test
    public void testCountOpenByRequester() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id1 = insertOpen(requester, "Alice", 10, 100.0, null);
        insertOpen(requester, "Alice", 8, 150.0, null);

        assertEquals(2, dao.countOpenByRequester(requester));

        // 1件 fulfilled にすると open カウントから外れる
        dao.markAsFulfilled(id1, UUID.randomUUID(), "SUPPLIED_BASE64", System.currentTimeMillis());
        assertEquals(1, dao.countOpenByRequester(requester));
    }

    /**
     * 二重供給防止の核心：markAsFulfilled は open の行のみ更新し、影響行数 1 のときだけ true。
     */
    @Test
    public void testMarkAsFulfilledOptimisticLock() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, null);

        UUID supplier1 = UUID.randomUUID();
        UUID supplier2 = UUID.randomUUID();

        boolean first = dao.markAsFulfilled(id, supplier1, "ITEM1", System.currentTimeMillis());
        boolean second = dao.markAsFulfilled(id, supplier2, "ITEM2", System.currentTimeMillis());

        assertTrue("最初の供給は成功するべき", first);
        assertFalse("2回目の供給は失敗するべき（二重供給防止）", second);

        MarketBuyOrder fetched = dao.getById(id);
        assertEquals(MarketBuyOrder.STATUS_FULFILLED, fetched.getStatus());
        assertEquals("供給者は最初の供給者であるべき", supplier1, fetched.getSupplierUuid());
        assertEquals("供給アイテムは最初のものであるべき", "ITEM1", fetched.getItemData());
        assertNotNull(fetched.getFulfilledAt());
    }

    @Test
    public void testGetExpiredOpenOrders() throws SQLException {
        UUID requester = UUID.randomUUID();
        insertOpen(requester, "Alice", 10, 100.0, 1000L);   // 失効=epoch 1000
        insertOpen(requester, "Alice", 5, 200.0, null);     // 無期限
        insertOpen(requester, "Alice", 3, 300.0, 5000L);    // 失効=epoch 5000

        assertEquals("now=500 では期限切れ0件", 0, dao.getExpiredOpenOrders(500L).size());

        // now=2000 → epoch 1000 のみ期限切れ。無期限・未来失効は対象外
        List<MarketBuyOrder> expired = dao.getExpiredOpenOrders(2000L);
        assertEquals("now=2000 では期限切れ1件", 1, expired.size());
        assertEquals(100.0, expired.get(0).getPrice(), DELTA);
    }

    /**
     * 期限切れ募集は fulfilled になっていると対象外（既に成立した募集は返金対象にしない）。
     */
    @Test
    public void testGetExpiredOpenOrdersExcludesFulfilled() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, 1000L);
        dao.markAsFulfilled(id, UUID.randomUUID(), "ITEM", System.currentTimeMillis());

        assertEquals("fulfilled 済みは期限切れ取得の対象外", 0, dao.getExpiredOpenOrders(2000L).size());
    }

    @Test
    public void testUpdateStatusConditionalCancel() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, null);

        // open → cancelled（キャンセル）
        boolean ok = dao.updateStatusConditional(id, MarketBuyOrder.STATUS_OPEN, MarketBuyOrder.STATUS_CANCELLED);
        assertTrue("open からのキャンセルは成功するべき", ok);
        assertEquals(MarketBuyOrder.STATUS_CANCELLED, dao.getById(id).getStatus());

        // 既に cancelled のため再度の open 起点の遷移は失敗
        boolean again = dao.updateStatusConditional(id, MarketBuyOrder.STATUS_OPEN, MarketBuyOrder.STATUS_CANCELLED);
        assertFalse("期待ステータス不一致の遷移は失敗するべき", again);
    }

    @Test
    public void testUpdateStatusConditionalExpire() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, 1000L);

        // open → expired（期限切れ）。返金は Manager 側で行うため DAO は状態遷移のみ
        boolean ok = dao.updateStatusConditional(id, MarketBuyOrder.STATUS_OPEN, MarketBuyOrder.STATUS_EXPIRED);
        assertTrue("open からの期限切れ化は成功するべき", ok);
        assertEquals(MarketBuyOrder.STATUS_EXPIRED, dao.getById(id).getStatus());
    }

    @Test
    public void testReclaimFulfilledOrder() throws SQLException {
        UUID requester = UUID.randomUUID();
        int id = insertOpen(requester, "Alice", 10, 100.0, null);
        dao.markAsFulfilled(id, UUID.randomUUID(), "SUPPLIED_BASE64", System.currentTimeMillis());

        // fulfilled → reclaimed（回収）
        boolean ok = dao.updateStatusConditional(id, MarketBuyOrder.STATUS_FULFILLED, MarketBuyOrder.STATUS_RECLAIMED);
        assertTrue("fulfilled からの回収は成功するべき", ok);
        assertEquals(MarketBuyOrder.STATUS_RECLAIMED, dao.getById(id).getStatus());

        // 二重回収防止
        boolean again = dao.updateStatusConditional(id, MarketBuyOrder.STATUS_FULFILLED, MarketBuyOrder.STATUS_RECLAIMED);
        assertFalse("二重回収は失敗するべき", again);
    }
}
