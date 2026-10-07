package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.economy.TransactionContext;
import org.tofu.tofunomics.economy.TransactionRecorder;
import org.tofu.tofunomics.economy.TransactionType;
import org.tofu.tofunomics.models.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * お金の記録（transaction_log）のテスト。本番と同じ SQLite（メモリ上）で確かめる。
 *
 * 検証観点:
 * - 表は本番と同じ定義（CREATE TABLE IF NOT EXISTS）で作れ、2 回実行しても失敗しない
 * - 残高を書き換える保存処理（PlayerDAO）を通ると、増減が理由つきで自動的に残る
 * - 残高が変わらない保存では記録しない／記録に失敗してもお金の操作は止まらない
 */
public class TransactionLogDAOTest {

    private Connection connection;
    private TransactionLogDAO logDAO;
    private PlayerDAO playerDAO;

    @Before
    public void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                "uuid TEXT PRIMARY KEY," +
                "balance REAL NOT NULL DEFAULT 0.0," +
                "bank_balance REAL NOT NULL DEFAULT 0.0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
            // 起動のたびに実行されるので、2 回実行しても失敗しないこと
            statement.execute(TransactionLogDAO.CREATE_TABLE_SQL);
            statement.execute(TransactionLogDAO.CREATE_INDEX_SQL);
            statement.execute(TransactionLogDAO.CREATE_TABLE_SQL);
            statement.execute(TransactionLogDAO.CREATE_INDEX_SQL);
        }
        logDAO = new TransactionLogDAO(connection);
        playerDAO = new PlayerDAO(connection);
        TransactionRecorder.install(new TransactionRecorder(logDAO, Logger.getLogger("test")));
    }

    @After
    public void tearDown() throws Exception {
        TransactionRecorder.install(null);
        connection.close();
    }

    private Player newPlayer(UUID uuid, double bankBalance) throws Exception {
        Player player = new Player(uuid, 0.0);
        player.setBankBalance(bankBalance);
        playerDAO.createPlayer(player);
        return player;
    }

    @Test
    public void 保存と読み出し_新しい順と件数の上限() throws Exception {
        UUID uuid = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            logDAO.insert(new TransactionLogDAO.Entry(1000L + i, uuid, "Tester", "DEPOSIT",
                TransactionLogDAO.ACCOUNT_BANK, i, (double) (100 + i), null, null));
        }
        logDAO.insert(new TransactionLogDAO.Entry(2000L, UUID.randomUUID(), "Other", "DEPOSIT",
            TransactionLogDAO.ACCOUNT_BANK, 999, 999.0, null, null));

        List<TransactionLogDAO.Entry> recent = logDAO.findRecentByPlayer(uuid, 3);

        assertEquals(3, recent.size());
        assertEquals(5.0, recent.get(0).getAmount(), 0.001);
        assertEquals(3.0, recent.get(2).getAmount(), 0.001);
    }

    @Test
    public void 手持ちだけの操作は操作後の残高が空のまま読める() throws Exception {
        UUID uuid = UUID.randomUUID();
        logDAO.insert(new TransactionLogDAO.Entry(1L, uuid, null, "NPC_BUY",
            TransactionLogDAO.ACCOUNT_CASH, -30, null, "NPC:食料品店", "BREAD x3"));

        TransactionLogDAO.Entry entry = logDAO.findRecentByPlayer(uuid, 10).get(0);

        assertNull(entry.getBalanceAfter());
        assertEquals(TransactionLogDAO.ACCOUNT_CASH, entry.getAccount());
        assertEquals("NPC:食料品店", entry.getCounterparty());
        assertEquals("BREAD x3", entry.getDetail());
    }

    @Test
    public void 名前からUUIDを引ける_オフラインの相手を名前で指定するため() throws Exception {
        UUID uuid = UUID.randomUUID();
        logDAO.insert(new TransactionLogDAO.Entry(1L, uuid, "Tester", "DEPOSIT",
            TransactionLogDAO.ACCOUNT_BANK, 1, 1.0, null, null));

        assertEquals(uuid, logDAO.findUuidByName("tester"));
        assertNull(logDAO.findUuidByName("nobody"));
    }

    @Test
    public void 残高の保存を通ると増減が理由つきで残る() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = newPlayer(uuid, 1000.0);

        player.removeBankBalance(300.0);
        try (TransactionContext.Scope scope = TransactionContext.open(TransactionType.WITHDRAW, null, "銀行")) {
            assertTrue(playerDAO.updatePlayerData(player));
        }

        List<TransactionLogDAO.Entry> entries = logDAO.findRecentByPlayer(uuid, 10);
        TransactionLogDAO.Entry latest = entries.get(0);
        assertEquals("WITHDRAW", latest.getType());
        assertEquals(-300.0, latest.getAmount(), 0.001);
        assertEquals(Double.valueOf(700.0), latest.getBalanceAfter());
        assertEquals(TransactionLogDAO.ACCOUNT_BANK, latest.getAccount());
        // 新規作成時の 1000 も「0 からの増加」として残っている
        assertEquals(1000.0, entries.get(1).getAmount(), 0.001);
    }

    @Test
    public void 理由の宣言が無い呼び出し元はその他として残る() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = newPlayer(uuid, 0.0);

        player.addBankBalance(50.0);
        playerDAO.updatePlayer(player);

        TransactionLogDAO.Entry latest = logDAO.findRecentByPlayer(uuid, 10).get(0);
        assertEquals("OTHER", latest.getType());
        assertEquals(50.0, latest.getAmount(), 0.001);
    }

    @Test
    public void 預金が変わらない保存は記録しない() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = newPlayer(uuid, 0.0);

        // 作業収入などで balance（預金ではない列）だけが動く保存
        player.addBalance(5.0);
        playerDAO.updatePlayerData(player);
        playerDAO.updatePlayerData(player);

        assertTrue(logDAO.findRecentByPlayer(uuid, 10).isEmpty());
    }

    @Test
    public void 送金は送る側と受け取る側の両方に相手つきで残る() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        newPlayer(from, 500.0);
        newPlayer(to, 0.0);

        assertTrue(playerDAO.transferBalance(from, to, 200.0));

        TransactionLogDAO.Entry sent = logDAO.findRecentByPlayer(from, 10).get(0);
        assertEquals("PAY_SEND", sent.getType());
        assertEquals(-200.0, sent.getAmount(), 0.001);
        assertEquals(to.toString(), sent.getCounterparty());
        assertEquals(Double.valueOf(300.0), sent.getBalanceAfter());

        TransactionLogDAO.Entry received = logDAO.findRecentByPlayer(to, 10).get(0);
        assertEquals("PAY_RECEIVE", received.getType());
        assertEquals(200.0, received.getAmount(), 0.001);
        assertEquals(from.toString(), received.getCounterparty());
    }

    @Test
    public void 残高不足で送金できなければ何も残らない() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        newPlayer(from, 0.0);
        newPlayer(to, 0.0);

        assertFalse(playerDAO.transferBalance(from, to, 200.0));

        assertTrue(logDAO.findRecentByPlayer(from, 10).isEmpty());
        assertTrue(logDAO.findRecentByPlayer(to, 10).isEmpty());
    }

    @Test
    public void 記録に失敗してもお金の操作は止まらない() throws Exception {
        UUID uuid = UUID.randomUUID();
        Player player = newPlayer(uuid, 100.0);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE transaction_log");
        }

        player.addBankBalance(50.0);

        assertTrue("記録の表が壊れていても残高の保存は成功する", playerDAO.updatePlayerData(player));
        assertEquals(150.0, playerDAO.getPlayer(uuid).getBankBalance(), 0.001);
    }

    @Test
    public void 記録係が登録されていなければ何もしない() throws Exception {
        TransactionRecorder.install(null);
        UUID uuid = UUID.randomUUID();
        Player player = newPlayer(uuid, 100.0);
        player.addBankBalance(50.0);

        assertTrue(playerDAO.updatePlayerData(player));
        assertTrue(logDAO.findRecentByPlayer(uuid, 10).isEmpty());
    }
}
