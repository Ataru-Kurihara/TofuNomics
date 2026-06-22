package org.tofu.tofunomics.market;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketBuyOrderDAO;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.models.MarketBuyOrder;
import org.tofu.tofunomics.models.MarketListing;
import org.tofu.tofunomics.models.MarketServiceRequest;
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
    private MarketBuyOrderDAO buyOrderDAO;
    private org.tofu.tofunomics.dao.MarketServiceRequestDAO serviceRequestDAO;
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
            st.execute("CREATE TABLE IF NOT EXISTS market_buy_orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "requester_uuid TEXT NOT NULL," +
                    "requester_name TEXT NOT NULL," +
                    "material TEXT NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "price REAL NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'open'," +
                    "supplier_uuid TEXT," +
                    "item_data TEXT," +
                    "listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at INTEGER," +
                    "fulfilled_at TIMESTAMP);");
            st.execute("CREATE TABLE IF NOT EXISTS market_service_requests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "requester_uuid TEXT NOT NULL," +
                    "requester_name TEXT NOT NULL," +
                    "service_type TEXT NOT NULL," +
                    "material TEXT NOT NULL," +
                    "item_data TEXT NOT NULL," +
                    "enchant_type TEXT," +
                    "enchant_level INTEGER," +
                    "price REAL NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'open'," +
                    "worker_uuid TEXT," +
                    "result_item_data TEXT," +
                    "listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at INTEGER," +
                    "fulfilled_at TIMESTAMP);");
        }

        playerDAO = new PlayerDAO(connection);
        listingDAO = new MarketListingDAO(connection);
        buyOrderDAO = new MarketBuyOrderDAO(connection);
        serviceRequestDAO = new org.tofu.tofunomics.dao.MarketServiceRequestDAO(connection);

        configManager = mock(ConfigManager.class);
        when(configManager.isMarketEnabled()).thenReturn(true);
        when(configManager.getMarketFeeRate()).thenReturn(0.05);
        when(configManager.getMarketMinPrice()).thenReturn(1.0);
        when(configManager.getMarketMaxPrice()).thenReturn(1000000.0);
        when(configManager.getMarketListingDurationDays()).thenReturn(7);
        when(configManager.isMarketAllowSelfPurchase()).thenReturn(false);
        when(configManager.isMarketServiceEnabled()).thenReturn(true);
        when(configManager.getMarketServiceMaxRequestsPerPlayer()).thenReturn(10);

        // currencyConverter は購入/供給の決済コアでは未使用のため null。
        // 現金回収・返金は Bukkit 依存ラッパー側で行うため E2E 検証に委ねる。
        manager = new MarketManager(connection, playerDAO, listingDAO, buyOrderDAO, serviceRequestDAO,
                configManager, null, Logger.getLogger("MarketManagerTest"));
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

    // ====================================================================
    // 買い注文（募集）コアロジック
    // ====================================================================

    /**
     * 募集を 1 件登録して id を返すヘルパー
     */
    private int createOpenOrder(UUID requester, String material, int amount, double price) {
        MarketResult r = manager.executeCreateBuyOrder(
                requester, "Requester", material, amount, price, System.currentTimeMillis());
        if (r != MarketResult.REQUESTED) {
            throw new IllegalStateException("募集登録に失敗: " + r);
        }
        try {
            return buyOrderDAO.getOrdersByRequester(requester).get(0).getId();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 募集登録 ----

    @Test
    public void testExecuteCreateBuyOrderSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        MarketResult result = manager.executeCreateBuyOrder(
                requester, "Requester", "DIAMOND", 10, 100, System.currentTimeMillis());
        assertEquals(MarketResult.REQUESTED, result);
        assertEquals(1, buyOrderDAO.countOpenByRequester(requester));
    }

    @Test
    public void testExecuteCreateBuyOrderInvalidPrice() throws SQLException {
        UUID requester = createPlayer(0);
        assertEquals(MarketResult.INVALID_PRICE, manager.executeCreateBuyOrder(
                requester, "Requester", "DIAMOND", 10, 0, System.currentTimeMillis()));
    }

    @Test
    public void testExecuteCreateBuyOrderInvalidAmount() throws SQLException {
        UUID requester = createPlayer(0);
        assertEquals(MarketResult.INVALID_ITEM, manager.executeCreateBuyOrder(
                requester, "Requester", "DIAMOND", 0, 100, System.currentTimeMillis()));
    }

    private MarketResult createBuyOrderResult(UUID requester, double price) {
        return manager.executeCreateBuyOrder(requester, "Requester", "DIAMOND", 5, price,
                System.currentTimeMillis());
    }

    // ---- 供給決済 ----

    @Test
    public void testFulfillSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);

        FulfillOutcome outcome = manager.executeFulfillTransaction(
                supplier, id, "SUPPLIED_BASE64", System.currentTimeMillis());

        assertTrue("供給成立", outcome.isSuccess());
        assertEquals("供給者受取=floor(100*0.95)=95", 95L, outcome.getSupplierProceeds());
        assertEquals("通知先は募集者", requester, outcome.getRequesterUuid());
        assertEquals("供給者の銀行残高=0+95", 95.0, bankOf(supplier), DELTA);
        assertEquals("募集者の残高は不変", 0.0, bankOf(requester), DELTA);

        MarketBuyOrder fulfilled = buyOrderDAO.getById(id);
        assertEquals(MarketBuyOrder.STATUS_FULFILLED, fulfilled.getStatus());
        assertEquals(supplier, fulfilled.getSupplierUuid());
        assertEquals("SUPPLIED_BASE64", fulfilled.getItemData());
    }

    /**
     * 二重供給防止：同一募集への 2 回目の供給決済は楽観ロックで弾かれる。
     */
    @Test
    public void testFulfillDoubleSupplyRejected() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier1 = createPlayer(0);
        UUID supplier2 = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);

        FulfillOutcome first = manager.executeFulfillTransaction(supplier1, id, "ITEM1", System.currentTimeMillis());
        FulfillOutcome second = manager.executeFulfillTransaction(supplier2, id, "ITEM2", System.currentTimeMillis());

        assertTrue("最初の供給は成功", first.isSuccess());
        assertEquals("2回目は成立済みで拒否", MarketResult.ORDER_NOT_AVAILABLE, second.getResult());
        assertEquals("供給者は最初の供給者", supplier1, buyOrderDAO.getById(id).getSupplierUuid());
        assertEquals("2人目の残高は不変", 0.0, bankOf(supplier2), DELTA);
    }

    // ---- キャンセル ----

    @Test
    public void testCancelBuyOrderSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);

        MarketResult result = manager.executeCancelBuyOrder(requester, id);

        assertEquals(MarketResult.ORDER_CANCELLED, result);
        assertEquals(MarketBuyOrder.STATUS_CANCELLED, buyOrderDAO.getById(id).getStatus());
    }

    @Test
    public void testCancelBuyOrderNotOwner() throws SQLException {
        UUID requester = createPlayer(0);
        UUID other = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);

        MarketResult result = manager.executeCancelBuyOrder(other, id);

        assertEquals("他人の募集はキャンセル不可", MarketResult.ORDER_NOT_OWNER, result);
        assertEquals(MarketBuyOrder.STATUS_OPEN, buyOrderDAO.getById(id).getStatus());
    }

    @Test
    public void testCancelBuyOrderAlreadyFulfilled() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);
        manager.executeFulfillTransaction(supplier, id, "ITEM", System.currentTimeMillis());

        // 成立済み（open でない）はキャンセル不可
        MarketResult result = manager.executeCancelBuyOrder(requester, id);

        assertEquals(MarketResult.ORDER_NOT_AVAILABLE, result);
    }

    // ---- 回収 ----

    @Test
    public void testReclaimBuyOrderSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);
        manager.executeFulfillTransaction(supplier, id, "SUPPLIED", System.currentTimeMillis());

        MarketResult result = manager.executeReclaimBuyOrder(requester, id, true);

        assertEquals(MarketResult.ORDER_RECLAIMED, result);
        assertEquals(MarketBuyOrder.STATUS_RECLAIMED, buyOrderDAO.getById(id).getStatus());
    }

    @Test
    public void testReclaimBuyOrderNotFulfilled() throws SQLException {
        UUID requester = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);

        // まだ open（未成立）は回収不可
        MarketResult result = manager.executeReclaimBuyOrder(requester, id, true);

        assertEquals(MarketResult.ORDER_NOT_AVAILABLE, result);
    }

    @Test
    public void testReclaimBuyOrderInventoryFull() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier = createPlayer(0);
        int id = createOpenOrder(requester, "DIAMOND", 10, 100);
        manager.executeFulfillTransaction(supplier, id, "SUPPLIED", System.currentTimeMillis());

        // インベントリ空き無し → 回収拒否（status は fulfilled のまま）
        MarketResult result = manager.executeReclaimBuyOrder(requester, id, false);

        assertEquals(MarketResult.INVENTORY_FULL, result);
        assertEquals(MarketBuyOrder.STATUS_FULFILLED, buyOrderDAO.getById(id).getStatus());
    }

    // ---- 期限切れ返金 ----

    @Test
    public void testExpireBuyOrdersRefundsToBank() throws SQLException {
        UUID requester = createPlayer(0);
        // 期限切れにするため listing_duration_days=0（無期限）を避け、過去失効を直接挿入する
        MarketBuyOrder expiring = new MarketBuyOrder(requester, "Requester", "DIAMOND", 10, 100.0, 1000L);
        buyOrderDAO.insertBuyOrder(expiring);
        MarketBuyOrder permanent = new MarketBuyOrder(requester, "Requester", "DIAMOND", 5, 200.0, null);
        buyOrderDAO.insertBuyOrder(permanent);

        int count = manager.expireBuyOrders();

        assertEquals("期限切れ返金は1件", 1, count);
        assertEquals("前払い100が bank へ返金される", 100.0, bankOf(requester), DELTA);
        assertEquals(MarketBuyOrder.STATUS_EXPIRED, buyOrderDAO.getById(expiring.getId()).getStatus());
        assertEquals("無期限は対象外", MarketBuyOrder.STATUS_OPEN, buyOrderDAO.getById(permanent.getId()).getStatus());
    }

    @Test
    public void testExpireBuyOrdersSkipsFulfilled() throws SQLException {
        UUID requester = createPlayer(0);
        UUID supplier = createPlayer(0);
        MarketBuyOrder order = new MarketBuyOrder(requester, "Requester", "DIAMOND", 10, 100.0, 1000L);
        buyOrderDAO.insertBuyOrder(order);
        manager.executeFulfillTransaction(supplier, order.getId(), "ITEM", System.currentTimeMillis());

        int count = manager.expireBuyOrders();

        assertEquals("成立済みは期限切れ返金の対象外", 0, count);
        assertEquals("募集者へ二重返金されない", 0.0, bankOf(requester), DELTA);
    }

    // ========================================================================
    // サービス依頼（修理・エンチャント募集）コアロジック
    // ========================================================================

    /** open の修理依頼を登録して id を返す（report 用ヘルパー） */
    private int createOpenServiceRequest(UUID requester, double price) {
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM_DATA", price);
        MarketResult r = manager.executeCreateServiceRequest(req, System.currentTimeMillis());
        assertEquals(MarketResult.SERVICE_REQUESTED, r);
        return req.getId();
    }

    @Test
    public void testExecuteCreateServiceRequestSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);

        MarketServiceRequest fetched = serviceRequestDAO.getById(id);
        assertNotNull(fetched);
        assertEquals(MarketServiceRequest.TYPE_REPAIR, fetched.getServiceType());
        assertEquals(MarketServiceRequest.STATUS_OPEN, fetched.getStatus());
    }

    @Test
    public void testExecuteCreateServiceRequestInvalidPrice() throws SQLException {
        UUID requester = createPlayer(0);
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM_DATA", 0);
        assertEquals(MarketResult.INVALID_PRICE,
                manager.executeCreateServiceRequest(req, System.currentTimeMillis()));
    }

    @Test
    public void testExecuteCreateServiceRequestLimit() throws SQLException {
        UUID requester = createPlayer(0);
        when(configManager.getMarketServiceMaxRequestsPerPlayer()).thenReturn(2);
        createOpenServiceRequest(requester, 100);
        createOpenServiceRequest(requester, 100);
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM_DATA", 100);
        assertEquals("上限到達で依頼拒否", MarketResult.SERVICE_LIMIT,
                manager.executeCreateServiceRequest(req, System.currentTimeMillis()));
    }

    @Test
    public void testFulfillServiceSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        UUID worker = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);

        ServiceFulfillOutcome outcome = manager.executeFulfillServiceTransaction(
                worker, id, "RESULT_BASE64", System.currentTimeMillis());

        assertTrue("加工成立", outcome.isSuccess());
        assertEquals("worker受取=floor(100*0.95)=95", 95L, outcome.getWorkerProceeds());
        assertEquals("workerの銀行残高=95", 95.0, bankOf(worker), DELTA);
        assertEquals("依頼者の残高は不変", 0.0, bankOf(requester), DELTA);

        MarketServiceRequest fulfilled = serviceRequestDAO.getById(id);
        assertEquals(MarketServiceRequest.STATUS_FULFILLED, fulfilled.getStatus());
        assertEquals(worker, fulfilled.getWorkerUuid());
        assertEquals("RESULT_BASE64", fulfilled.getResultItemData());
    }

    /** 二重成立防止：同一依頼への2回目の加工決済は楽観ロックで弾かれる */
    @Test
    public void testFulfillServiceDoubleRejected() throws SQLException {
        UUID requester = createPlayer(0);
        UUID worker1 = createPlayer(0);
        UUID worker2 = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);

        ServiceFulfillOutcome first = manager.executeFulfillServiceTransaction(worker1, id, "R1", System.currentTimeMillis());
        ServiceFulfillOutcome second = manager.executeFulfillServiceTransaction(worker2, id, "R2", System.currentTimeMillis());

        assertTrue("最初の成立は成功", first.isSuccess());
        assertEquals("2回目は成立済みで拒否", MarketResult.SERVICE_NOT_AVAILABLE, second.getResult());
        assertEquals("workerは最初の引受人", worker1, serviceRequestDAO.getById(id).getWorkerUuid());
        assertEquals("2人目の残高は不変", 0.0, bankOf(worker2), DELTA);
    }

    @Test
    public void testCancelServiceRequestSuccess() throws SQLException {
        UUID requester = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);

        MarketResult result = manager.executeCancelServiceRequest(requester, id);

        assertEquals(MarketResult.SERVICE_CANCELLED, result);
        assertEquals(MarketServiceRequest.STATUS_CANCELLED, serviceRequestDAO.getById(id).getStatus());
    }

    @Test
    public void testCancelServiceRequestNotOwner() throws SQLException {
        UUID requester = createPlayer(0);
        UUID other = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);

        MarketResult result = manager.executeCancelServiceRequest(other, id);

        assertEquals("他人の依頼はキャンセル不可", MarketResult.SERVICE_NOT_OWNER, result);
        assertEquals(MarketServiceRequest.STATUS_OPEN, serviceRequestDAO.getById(id).getStatus());
    }

    @Test
    public void testReclaimServiceFulfilled() throws SQLException {
        UUID requester = createPlayer(0);
        UUID worker = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);
        manager.executeFulfillServiceTransaction(worker, id, "RESULT", System.currentTimeMillis());

        MarketResult result = manager.executeReclaimServiceRequest(requester, id, true);

        assertEquals(MarketResult.SERVICE_RECLAIMED, result);
        assertEquals(MarketServiceRequest.STATUS_RECLAIMED, serviceRequestDAO.getById(id).getStatus());
    }

    @Test
    public void testReclaimServiceCancelledNotReclaimable() throws SQLException {
        UUID requester = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);
        manager.executeCancelServiceRequest(requester, id);

        // cancelled はキャンセル時に道具を即時返却済みのため回収対象外
        MarketResult result = manager.executeReclaimServiceRequest(requester, id, true);

        assertEquals(MarketResult.SERVICE_NOT_AVAILABLE, result);
    }

    @Test
    public void testReclaimServiceInventoryFull() throws SQLException {
        UUID requester = createPlayer(0);
        UUID worker = createPlayer(0);
        int id = createOpenServiceRequest(requester, 100);
        manager.executeFulfillServiceTransaction(worker, id, "RESULT", System.currentTimeMillis());

        MarketResult result = manager.executeReclaimServiceRequest(requester, id, false);

        assertEquals(MarketResult.INVENTORY_FULL, result);
        assertEquals(MarketServiceRequest.STATUS_FULFILLED, serviceRequestDAO.getById(id).getStatus());
    }

    @Test
    public void testExpireServiceRequestsRefundsToBank() throws SQLException {
        UUID requester = createPlayer(0);
        MarketServiceRequest expiring = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM", 100.0);
        expiring.setExpiresAt(1000L);
        serviceRequestDAO.insertServiceRequest(expiring);
        MarketServiceRequest permanent = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM", 200.0);
        permanent.setExpiresAt(null);
        serviceRequestDAO.insertServiceRequest(permanent);

        int count = manager.expireServiceRequests();

        assertEquals("期限切れ返金は1件", 1, count);
        assertEquals("前払い100がbankへ返金される", 100.0, bankOf(requester), DELTA);
        assertEquals(MarketServiceRequest.STATUS_EXPIRED, serviceRequestDAO.getById(expiring.getId()).getStatus());
        assertEquals("無期限は対象外", MarketServiceRequest.STATUS_OPEN, serviceRequestDAO.getById(permanent.getId()).getStatus());
    }

    @Test
    public void testExpireServiceRequestsSkipsFulfilled() throws SQLException {
        UUID requester = createPlayer(0);
        UUID worker = createPlayer(0);
        MarketServiceRequest req = MarketServiceRequest.createRepair(
                requester, "Requester", "DIAMOND_PICKAXE", "ITEM", 100.0);
        req.setExpiresAt(1000L);
        serviceRequestDAO.insertServiceRequest(req);
        manager.executeFulfillServiceTransaction(worker, req.getId(), "RESULT", System.currentTimeMillis());

        int count = manager.expireServiceRequests();

        assertEquals("成立済みは期限切れ返金の対象外", 0, count);
        assertEquals("依頼者へ二重返金されない", 0.0, bankOf(requester), DELTA);
    }
}
