package org.tofu.tofunomics.market;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.models.MarketListing;
import org.tofu.tofunomics.models.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * MarketManager のコアロジック単体テスト。
 *
 * Bukkit 非依存の決済コア（executeCreateListing / executePurchaseTransaction / executeReclaim /
 * 手数料計算 / 価格検証）を、SQLite インメモリ DB と Mockito 化した ConfigManager で検証する。
 * Player/ItemStack/インベントリに依存するラッパーは E2E 検証に委ねる。
 */
public class MarketManagerTest {

    private Connection connection;
    private PlayerDAO playerDAO;
    private MarketListingDAO listingDAO;
    private ConfigManager configManager;
    private MarketManager manager;
    private final double DELTA = 0.001;

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "balance REAL NOT NULL DEFAULT 0.0," +
                    "bank_balance REAL NOT NULL DEFAULT 0.0," +
                    "created_at TIMESTAMP," +
                    "updated_at TIMESTAMP);");
            st.execute("CREATE TABLE IF NOT EXISTS market_listings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "seller_uuid TEXT NOT NULL," +
                    "seller_name TEXT NOT NULL," +
                    "item_data TEXT NOT NULL," +
                    "display_name TEXT," +
                    "material TEXT NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "price REAL NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'active'," +
                    "buyer_uuid TEXT," +
                    "listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at INTEGER," +
                    "sold_at TIMESTAMP);");
        }

        playerDAO = new PlayerDAO(connection);
        listingDAO = new MarketListingDAO(connection);

        configManager = mock(ConfigManager.class);
        when(configManager.isMarketEnabled()).thenReturn(true);
        when(configManager.getMarketFeeRate()).thenReturn(0.05);
        when(configManager.getMarketMinPrice()).thenReturn(1.0);
        when(configManager.getMarketMaxPrice()).thenReturn(1000000.0);
        when(configManager.getMarketMaxListingsPerPlayer()).thenReturn(10);
        when(configManager.getMarketListingDurationDays()).thenReturn(7);
        when(configManager.isMarketAllowSelfPurchase()).thenReturn(false);

        // currencyConverter は購入決済コア（executePurchaseTransaction）では未使用のため null。
        // 現金回収は Bukkit 依存ラッパー（purchaseListing）側で行うため E2E 検証に委ねる。
        manager = new MarketManager(connection, playerDAO, listingDAO, configManager,
                null, Logger.getLogger("MarketManagerTest"));
    }

    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // ---- テスト用ヘルパー ----

    private UUID createPlayer(double bankBalance) throws SQLException {
        UUID uuid = UUID.randomUUID();
        Player player = new Player(uuid, 0.0);
        player.setBankBalance(bankBalance);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        player.setCreatedAt(now);
        player.setUpdatedAt(now);
        playerDAO.createPlayer(player);
        return uuid;
    }

    private int createActiveListing(UUID seller, double price) {
        return manager.executeCreateListing(seller, "Seller", "DUMMY_BASE64", "テスト", "DIAMOND", 1,
                price, System.currentTimeMillis()) == MarketResult.LISTED
                ? lastListingIdOf(seller) : -1;
    }

    private int lastListingIdOf(UUID seller) {
        try {
            return listingDAO.getListingsBySeller(seller).get(0).getId();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private double bankOf(UUID uuid) throws SQLException {
        return playerDAO.getPlayer(uuid).getBankBalance();
    }

    // ---- 価格検証・手数料計算 ----

    @Test
    public void testIsValidPrice() {
        assertFalse("0は無効", manager.isValidPrice(0));
        assertTrue("1は有効", manager.isValidPrice(1));
        assertTrue("上限は有効", manager.isValidPrice(1000000));
        assertFalse("上限超過は無効", manager.isValidPrice(1000001));
        assertFalse("非整数は無効", manager.isValidPrice(100.5));
    }

    @Test
    public void testCalculateSellerProceeds() {
        assertEquals("floor(100*0.95)=95", 95L, manager.calculateSellerProceeds(100));
        assertEquals("floor(101*0.95)=95 (95.95切り捨て)", 95L, manager.calculateSellerProceeds(101));
        assertEquals("floor(1*0.95)=0", 0L, manager.calculateSellerProceeds(1));
    }

    // ---- 出品 ----

    @Test
    public void testExecuteCreateListingSuccess() throws SQLException {
        UUID seller = createPlayer(0);
        MarketResult result = manager.executeCreateListing(
                seller, "Seller", "DATA", "名前", "DIAMOND", 3, 100, System.currentTimeMillis());
        assertEquals(MarketResult.LISTED, result);
        assertEquals(1, listingDAO.countActiveBySeller(seller));
    }

    @Test
    public void testExecuteCreateListingInvalidPrice() throws SQLException {
        UUID seller = createPlayer(0);
        assertEquals(MarketResult.INVALID_PRICE, manager.executeCreateListing(
                seller, "Seller", "DATA", "名前", "DIAMOND", 1, 0, System.currentTimeMillis()));
    }

    @Test
    public void testExecuteCreateListingLimit() throws SQLException {
        UUID seller = createPlayer(0);
        when(configManager.getMarketMaxListingsPerPlayer()).thenReturn(2);
        assertEquals(MarketResult.LISTED, createListingResult(seller, 100));
        assertEquals(MarketResult.LISTED, createListingResult(seller, 100));
        assertEquals("上限到達で出品拒否", MarketResult.LISTING_LIMIT, createListingResult(seller, 100));
    }

    private MarketResult createListingResult(UUID seller, double price) {
        return manager.executeCreateListing(seller, "Seller", "DATA", "名前", "DIAMOND", 1, price,
                System.currentTimeMillis());
    }

    // ---- 購入決済 ----

    @Test
    public void testPurchaseSuccess() throws SQLException {
        UUID seller = createPlayer(0);
        UUID buyer = createPlayer(1000);
        int id = createActiveListing(seller, 100);

        PurchaseOutcome outcome = manager.executePurchaseTransaction(buyer, id, System.currentTimeMillis());

        assertTrue("購入成功", outcome.isSuccess());
        assertEquals("出品者受取=floor(100*0.95)=95", 95L, outcome.getSellerProceeds());
        // 購入者は現金（金塊）で支払うため、決済コアは bank_balance を変更しない
        assertEquals("購入者の銀行残高は不変", 1000.0, bankOf(buyer), DELTA);
        assertEquals("出品者残高=0+95", 95.0, bankOf(seller), DELTA);
        assertEquals(MarketListing.STATUS_SOLD, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testPurchaseSelfRejected() throws SQLException {
        UUID seller = createPlayer(1000);
        int id = createActiveListing(seller, 100);

        PurchaseOutcome outcome = manager.executePurchaseTransaction(seller, id, System.currentTimeMillis());

        assertEquals("自己購入は拒否", MarketResult.NOT_AVAILABLE, outcome.getResult());
        assertEquals(MarketListing.STATUS_ACTIVE, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testPurchaseSelfAllowedByConfig() throws SQLException {
        when(configManager.isMarketAllowSelfPurchase()).thenReturn(true);
        UUID seller = createPlayer(1000);
        int id = createActiveListing(seller, 100);

        PurchaseOutcome outcome = manager.executePurchaseTransaction(seller, id, System.currentTimeMillis());

        assertTrue("config許可時は自己購入成功", outcome.isSuccess());
    }

    @Test
    public void testDoublePurchaseRejected() throws SQLException {
        UUID seller = createPlayer(0);
        UUID buyer1 = createPlayer(1000);
        UUID buyer2 = createPlayer(1000);
        int id = createActiveListing(seller, 100);

        PurchaseOutcome first = manager.executePurchaseTransaction(buyer1, id, System.currentTimeMillis());
        PurchaseOutcome second = manager.executePurchaseTransaction(buyer2, id, System.currentTimeMillis());

        assertTrue("1人目は成功", first.isSuccess());
        assertEquals("2人目は売り切れ", MarketResult.ALREADY_SOLD, second.getResult());
        // 出品者への二重入金が無いこと（95のみ）
        assertEquals("出品者受取は1回分のみ", 95.0, bankOf(seller), DELTA);
    }

    // ---- キャンセル・回収 ----

    @Test
    public void testCancelSuccess() throws SQLException {
        UUID seller = createPlayer(0);
        int id = createActiveListing(seller, 100);

        MarketResult result = manager.executeReclaim(seller, id, MarketListing.STATUS_ACTIVE, true);

        assertEquals(MarketResult.CANCELLED, result);
        assertEquals(MarketListing.STATUS_RECLAIMED, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testCancelNotOwner() throws SQLException {
        UUID seller = createPlayer(0);
        UUID other = createPlayer(0);
        int id = createActiveListing(seller, 100);

        MarketResult result = manager.executeReclaim(other, id, MarketListing.STATUS_ACTIVE, true);

        assertEquals(MarketResult.NOT_OWNER, result);
        assertEquals(MarketListing.STATUS_ACTIVE, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testCancelInventoryFull() throws SQLException {
        UUID seller = createPlayer(0);
        int id = createActiveListing(seller, 100);

        MarketResult result = manager.executeReclaim(seller, id, MarketListing.STATUS_ACTIVE, false);

        assertEquals(MarketResult.INVENTORY_FULL, result);
        assertEquals("満杯なら出品維持", MarketListing.STATUS_ACTIVE, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testReclaimExpired() throws SQLException {
        UUID seller = createPlayer(0);
        int id = createActiveListing(seller, 100);
        listingDAO.updateStatusConditional(id, MarketListing.STATUS_ACTIVE, MarketListing.STATUS_EXPIRED);

        MarketResult result = manager.executeReclaim(seller, id, MarketListing.STATUS_EXPIRED, true);

        assertEquals(MarketResult.RECLAIMED, result);
        assertEquals(MarketListing.STATUS_RECLAIMED, listingDAO.getById(id).getStatus());
    }

    @Test
    public void testReclaimWrongStatus() throws SQLException {
        UUID seller = createPlayer(0);
        int id = createActiveListing(seller, 100);

        // active の出品を expired 起点で回収しようとすると拒否
        MarketResult result = manager.executeReclaim(seller, id, MarketListing.STATUS_EXPIRED, true);

        assertEquals(MarketResult.NOT_AVAILABLE, result);
    }
}
