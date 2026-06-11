package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.MarketListing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * MarketListingDAO 単体テスト
 *
 * 本番 DatabaseManager と同一の DDL を SQLite インメモリ DB に作成して検証する
 * （AUTOINCREMENT・DEFAULT CURRENT_TIMESTAMP・epoch millis 比較など SQLite 固有挙動を本番同等に再現）。
 */
public class MarketListingDAOTest {

    private Connection connection;
    private MarketListingDAO dao;
    private final double DELTA = 0.001;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        String createTable = "CREATE TABLE IF NOT EXISTS market_listings (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    seller_uuid TEXT NOT NULL," +
                "    seller_name TEXT NOT NULL," +
                "    item_data TEXT NOT NULL," +
                "    display_name TEXT," +
                "    material TEXT NOT NULL," +
                "    amount INTEGER NOT NULL," +
                "    price REAL NOT NULL," +
                "    status TEXT NOT NULL DEFAULT 'active'," +
                "    buyer_uuid TEXT," +
                "    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "    expires_at INTEGER," +
                "    sold_at TIMESTAMP" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }

        dao = new MarketListingDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * テスト用の active 出品を作成して id を返すヘルパー
     */
    private int insertActive(UUID seller, String sellerName, double price, Long expiresAtMillis) throws SQLException {
        MarketListing listing = new MarketListing(
                seller, sellerName, "DUMMY_BASE64", "テストアイテム", "DIAMOND", 5, price, expiresAtMillis);
        return dao.insertListing(listing);
    }

    @Test
    public void testInsertAndGetById() throws SQLException {
        UUID seller = UUID.randomUUID();
        int id = insertActive(seller, "Alice", 100.0, null);

        assertTrue("生成された id は正の値であるべき", id > 0);

        MarketListing fetched = dao.getById(id);
        assertNotNull("挿入した出品が取得できるべき", fetched);
        assertEquals(seller, fetched.getSellerUuid());
        assertEquals("Alice", fetched.getSellerName());
        assertEquals("DUMMY_BASE64", fetched.getItemData());
        assertEquals("DIAMOND", fetched.getMaterial());
        assertEquals(5, fetched.getAmount());
        assertEquals(100.0, fetched.getPrice(), DELTA);
        assertEquals(MarketListing.STATUS_ACTIVE, fetched.getStatus());
        assertNull("無期限出品の expires_at は null であるべき", fetched.getExpiresAtMillis());
        assertNull("未売却の buyer_uuid は null であるべき", fetched.getBuyerUuid());
        assertNotNull("listed_at は DEFAULT で自動設定されるべき", fetched.getListedAt());
    }

    @Test
    public void testGetByIdNotFound() throws SQLException {
        assertNull("存在しない id は null を返すべき", dao.getById(9999));
    }

    @Test
    public void testGetActiveListings() throws SQLException {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        int id1 = insertActive(s1, "Alice", 100.0, null);
        insertActive(s2, "Bob", 200.0, null);

        // 1件を sold にする → active 一覧から除外される
        dao.markAsSold(id1, UUID.randomUUID(), System.currentTimeMillis());

        List<MarketListing> active = dao.getActiveListings();
        assertEquals("active のみ取得されるべき", 1, active.size());
        assertEquals("Bob", active.get(0).getSellerName());
    }

    @Test
    public void testGetListingsBySeller() throws SQLException {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        int id1 = insertActive(seller, "Alice", 100.0, null);
        insertActive(seller, "Alice", 150.0, null);
        insertActive(other, "Bob", 200.0, null);

        // status 問わず自分の出品を全件取得
        dao.markAsSold(id1, UUID.randomUUID(), System.currentTimeMillis());

        List<MarketListing> mine = dao.getListingsBySeller(seller);
        assertEquals("自分の出品は status 問わず全件取得されるべき", 2, mine.size());
    }

    @Test
    public void testCountActiveBySeller() throws SQLException {
        UUID seller = UUID.randomUUID();
        int id1 = insertActive(seller, "Alice", 100.0, null);
        insertActive(seller, "Alice", 150.0, null);

        assertEquals(2, dao.countActiveBySeller(seller));

        // 1件 sold にすると active カウントから外れる
        dao.markAsSold(id1, UUID.randomUUID(), System.currentTimeMillis());
        assertEquals(1, dao.countActiveBySeller(seller));
    }

    /**
     * 二重購入防止の核心：markAsSold は active の行のみ更新し、影響行数 1 のときだけ true。
     */
    @Test
    public void testMarkAsSoldOptimisticLock() throws SQLException {
        UUID seller = UUID.randomUUID();
        int id = insertActive(seller, "Alice", 100.0, null);

        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();

        boolean first = dao.markAsSold(id, buyer1, System.currentTimeMillis());
        boolean second = dao.markAsSold(id, buyer2, System.currentTimeMillis());

        assertTrue("最初の購入は成功するべき", first);
        assertFalse("2回目の購入は失敗するべき（二重購入防止）", second);

        MarketListing fetched = dao.getById(id);
        assertEquals(MarketListing.STATUS_SOLD, fetched.getStatus());
        assertEquals("購入者は最初の購入者であるべき", buyer1, fetched.getBuyerUuid());
        assertNotNull(fetched.getSoldAt());
    }

    @Test
    public void testExpireActiveListings() throws SQLException {
        UUID seller = UUID.randomUUID();
        int expiringId = insertActive(seller, "Alice", 100.0, 1000L);   // 失効=epoch 1000
        int permanentId = insertActive(seller, "Alice", 200.0, null);   // 無期限
        int futureId = insertActive(seller, "Alice", 300.0, 5000L);     // 失効=epoch 5000

        // now=2000 → expiringId のみ期限切れ
        int expiredCount = dao.expireActiveListings(2000L);
        assertEquals("期限切れ化される件数は1件であるべき", 1, expiredCount);

        assertEquals(MarketListing.STATUS_EXPIRED, dao.getById(expiringId).getStatus());
        assertEquals("無期限は対象外", MarketListing.STATUS_ACTIVE, dao.getById(permanentId).getStatus());
        assertEquals("未来失効は対象外", MarketListing.STATUS_ACTIVE, dao.getById(futureId).getStatus());
    }

    @Test
    public void testGetExpiredActiveListings() throws SQLException {
        UUID seller = UUID.randomUUID();
        insertActive(seller, "Alice", 100.0, 1000L);
        insertActive(seller, "Alice", 200.0, null);

        assertEquals("now=500 では期限切れ0件", 0, dao.getExpiredActiveListings(500L).size());
        assertEquals("now=2000 では期限切れ1件", 1, dao.getExpiredActiveListings(2000L).size());
    }

    @Test
    public void testUpdateStatusConditional() throws SQLException {
        UUID seller = UUID.randomUUID();
        int id = insertActive(seller, "Alice", 100.0, null);

        // active → reclaimed（キャンセル）
        boolean ok = dao.updateStatusConditional(id, MarketListing.STATUS_ACTIVE, MarketListing.STATUS_RECLAIMED);
        assertTrue("active からの状態遷移は成功するべき", ok);
        assertEquals(MarketListing.STATUS_RECLAIMED, dao.getById(id).getStatus());

        // 既に reclaimed のため再度の active 起点の遷移は失敗
        boolean again = dao.updateStatusConditional(id, MarketListing.STATUS_ACTIVE, MarketListing.STATUS_RECLAIMED);
        assertFalse("期待ステータス不一致の遷移は失敗するべき", again);
    }

    @Test
    public void testReclaimExpiredListing() throws SQLException {
        UUID seller = UUID.randomUUID();
        int id = insertActive(seller, "Alice", 100.0, 1000L);
        dao.expireActiveListings(2000L);

        // expired → reclaimed（回収）
        boolean ok = dao.updateStatusConditional(id, MarketListing.STATUS_EXPIRED, MarketListing.STATUS_RECLAIMED);
        assertTrue("expired からの回収は成功するべき", ok);
        assertEquals(MarketListing.STATUS_RECLAIMED, dao.getById(id).getStatus());
    }
}
