package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * フェーズ7 - PlayerDAO単体テスト
 * H2インメモリデータベースを使用したDAO操作の完全テスト
 */
public class PlayerDAOTest {

    private Connection connection;
    private PlayerDAO playerDAO;
    private final double DELTA = 0.001;
    
    @Before
    public void setUp() throws SQLException {
        // H2インメモリデータベースの設定
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        
        // テーブル作成
        String createTableQuery = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "balance DOUBLE NOT NULL DEFAULT 0.0, " +
                "bank_balance DOUBLE NOT NULL DEFAULT 0.0, " +
                "created_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP NOT NULL" +
                ")";
        
        try (PreparedStatement statement = connection.prepareStatement(createTableQuery)) {
            statement.executeUpdate();
        }
        
        playerDAO = new PlayerDAO(connection);
    }
    
    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // テーブル削除
            try (PreparedStatement statement = connection.prepareStatement("DROP TABLE IF EXISTS players")) {
                statement.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    public void testCreatePlayer() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        double testBalance = 1000.0;
        Player player = new Player(testUuid, testBalance);
        
        playerDAO.createPlayer(player);
        
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        assertNotNull("作成したプレイヤーが取得できるべき", retrievedPlayer);
        assertEquals("UUIDが正しく保存されるべき", testUuid, retrievedPlayer.getUuid());
        assertEquals("残高が正しく保存されるべき", testBalance, retrievedPlayer.getBalance(), DELTA);
        assertEquals("銀行預金が正しく初期化されるべき", 0.0, retrievedPlayer.getBankBalance(), DELTA);
        assertNotNull("作成日時が設定されるべき", retrievedPlayer.getCreatedAt());
        assertNotNull("更新日時が設定されるべき", retrievedPlayer.getUpdatedAt());
    }

    @Test
    public void testGetPlayer() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 500.0);
        playerDAO.createPlayer(player);
        
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        assertNotNull("存在するプレイヤーが取得できるべき", retrievedPlayer);
        assertEquals("UUIDが一致するべき", testUuid, retrievedPlayer.getUuid());
        assertEquals("残高が一致するべき", 500.0, retrievedPlayer.getBalance(), DELTA);
    }

    @Test
    public void testGetPlayerNotExists() throws SQLException {
        UUID nonExistentUuid = UUID.randomUUID();
        
        Player retrievedPlayer = playerDAO.getPlayer(nonExistentUuid);
        assertNull("存在しないプレイヤーはnullを返すべき", retrievedPlayer);
    }

    @Test
    public void testGetOrCreatePlayer() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        // 初回取得で自動作成されるかテスト
        Player player = playerDAO.getOrCreatePlayer(testUuid);
        assertNotNull("存在しない場合は新規作成されるべき", player);
        assertEquals("UUIDが正しく設定されるべき", testUuid, player.getUuid());
        assertEquals("初期残高は0であるべき", 0.0, player.getBalance(), DELTA);
        
        // 既存プレイヤーの取得テスト
        Player existingPlayer = playerDAO.getOrCreatePlayer(testUuid);
        assertNotNull("既存プレイヤーが取得できるべき", existingPlayer);
        assertEquals("同じUUIDであるべき", testUuid, existingPlayer.getUuid());
    }

    @Test
    public void testUpdatePlayer() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 100.0);
        playerDAO.createPlayer(player);
        
        // 残高を更新
        player.setBalance(2000.0);
        Timestamp beforeUpdate = player.getUpdatedAt();
        
        // わずかな時間差を作るため
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerDAO.updatePlayer(player);
        
        Player updatedPlayer = playerDAO.getPlayer(testUuid);
        assertEquals("残高が更新されるべき", 2000.0, updatedPlayer.getBalance(), DELTA);
        assertTrue("更新日時が新しくなるべき", 
                   updatedPlayer.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testUpdateBalance() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 300.0);
        playerDAO.createPlayer(player);
        
        double newBalance = 1500.0;
        playerDAO.updateBalance(testUuid, newBalance);
        
        Player updatedPlayer = playerDAO.getPlayer(testUuid);
        assertEquals("残高が正しく更新されるべき", newBalance, updatedPlayer.getBalance(), DELTA);
    }

    @Test
    public void testDeletePlayer() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 400.0);
        playerDAO.createPlayer(player);
        
        // 削除前の確認
        assertNotNull("削除前はプレイヤーが存在するべき", playerDAO.getPlayer(testUuid));
        
        playerDAO.deletePlayer(testUuid);
        
        // 削除後の確認
        assertNull("削除後はプレイヤーが存在しないべき", playerDAO.getPlayer(testUuid));
    }

    @Test
    public void testGetTopPlayers() throws SQLException {
        // 複数のプレイヤーを異なる残高で作成
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        
        playerDAO.createPlayer(new Player(uuid1, 1000.0));
        playerDAO.createPlayer(new Player(uuid2, 2000.0));
        playerDAO.createPlayer(new Player(uuid3, 500.0));
        
        List<Player> topPlayers = playerDAO.getTopPlayers(2);
        
        assertEquals("指定した件数が取得されるべき", 2, topPlayers.size());
        assertEquals("1位の残高が正しい", 2000.0, topPlayers.get(0).getBalance(), DELTA);
        assertEquals("2位の残高が正しい", 1000.0, topPlayers.get(1).getBalance(), DELTA);
        assertEquals("1位のUUIDが正しい", uuid2, topPlayers.get(0).getUuid());
        assertEquals("2位のUUIDが正しい", uuid1, topPlayers.get(1).getUuid());
    }

    @Test
    public void testTransferBalanceSuccess() throws SQLException {
        UUID fromUuid = UUID.randomUUID();
        UUID toUuid = UUID.randomUUID();
        
        // 送金者に十分な銀行預金を設定
        playerDAO.createPlayer(new Player(fromUuid, 0.0, 1000.0));
        playerDAO.createPlayer(new Player(toUuid, 0.0, 200.0));
        
        double transferAmount = 300.0;
        boolean result = playerDAO.transferBalance(fromUuid, toUuid, transferAmount);
        
        assertTrue("送金が成功するべき", result);
        
        Player fromPlayer = playerDAO.getPlayer(fromUuid);
        Player toPlayer = playerDAO.getPlayer(toUuid);
        
        assertEquals("送金者の銀行預金が正しく減額されるべき", 700.0, fromPlayer.getBankBalance(), DELTA);
        assertEquals("受取者の銀行預金が正しく増額されるべき", 500.0, toPlayer.getBankBalance(), DELTA);
    }

    @Test
    public void testTransferBalanceInsufficientFunds() throws SQLException {
        UUID fromUuid = UUID.randomUUID();
        UUID toUuid = UUID.randomUUID();
        
        // 送金者に不十分な銀行預金を設定
        playerDAO.createPlayer(new Player(fromUuid, 0.0, 100.0));
        playerDAO.createPlayer(new Player(toUuid, 0.0, 200.0));
        
        double transferAmount = 300.0;
        boolean result = playerDAO.transferBalance(fromUuid, toUuid, transferAmount);
        
        assertFalse("残高不足の場合は送金が失敗するべき", result);
        
        Player fromPlayer = playerDAO.getPlayer(fromUuid);
        Player toPlayer = playerDAO.getPlayer(toUuid);
        
        assertEquals("送金者の銀行預金は変更されないべき", 100.0, fromPlayer.getBankBalance(), DELTA);
        assertEquals("受取者の銀行預金は変更されないべき", 200.0, toPlayer.getBankBalance(), DELTA);
    }

    @Test
    public void testTransferBalanceToNonExistentPlayer() throws SQLException {
        UUID fromUuid = UUID.randomUUID();
        UUID toUuid = UUID.randomUUID();
        
        // 送金者のみ作成
        playerDAO.createPlayer(new Player(fromUuid, 0.0, 1000.0));
        
        double transferAmount = 300.0;
        boolean result = playerDAO.transferBalance(fromUuid, toUuid, transferAmount);
        
        assertTrue("存在しない受取者には自動作成で送金成功するべき", result);
        
        Player fromPlayer = playerDAO.getPlayer(fromUuid);
        Player toPlayer = playerDAO.getPlayer(toUuid);
        
        assertEquals("送金者の銀行預金が正しく減額されるべき", 700.0, fromPlayer.getBankBalance(), DELTA);
        assertEquals("受取者の銀行預金が正しく増額されるべき", 300.0, toPlayer.getBankBalance(), DELTA);
    }

    @Test
    public void testGetTotalPlayerCount() throws SQLException {
        assertEquals("初期状態では0人であるべき", 0, playerDAO.getTotalPlayerCount());
        
        // 3人のプレイヤーを作成
        playerDAO.createPlayer(new Player(UUID.randomUUID(), 100.0));
        playerDAO.createPlayer(new Player(UUID.randomUUID(), 200.0));
        playerDAO.createPlayer(new Player(UUID.randomUUID(), 300.0));
        
        assertEquals("3人のプレイヤーが作成された後は3人であるべき", 3, playerDAO.getTotalPlayerCount());
    }

    @Test
    public void testGetPlayerByUUID() {
        UUID testUuid = UUID.randomUUID();
        try {
            playerDAO.createPlayer(new Player(testUuid, 600.0));
            
            Player player = playerDAO.getPlayerByUUID(testUuid.toString());
            assertNotNull("文字列UUIDでプレイヤーが取得できるべき", player);
            assertEquals("UUIDが一致するべき", testUuid, player.getUuid());
            assertEquals("残高が一致するべき", 600.0, player.getBalance(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testGetPlayerByUUIDInvalidFormat() {
        String invalidUuid = "invalid-uuid-format";
        
        Player player = playerDAO.getPlayerByUUID(invalidUuid);
        assertNull("不正なUUID形式の場合はnullを返すべき", player);
    }

    @Test
    public void testInsertPlayer() {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 700.0);
        
        boolean result = playerDAO.insertPlayer(player);
        assertTrue("プレイヤー挿入が成功するべき", result);
        
        try {
            Player retrievedPlayer = playerDAO.getPlayer(testUuid);
            assertNotNull("挿入したプレイヤーが取得できるべき", retrievedPlayer);
            assertEquals("残高が正しく保存されるべき", 700.0, retrievedPlayer.getBalance(), DELTA);
        } catch (SQLException e) {
            fail("プレイヤー取得で例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testUpdatePlayerData() {
        UUID testUuid = UUID.randomUUID();
        try {
            Player player = new Player(testUuid, 800.0);
            playerDAO.createPlayer(player);
            
            player.setBalance(1200.0);
            boolean result = playerDAO.updatePlayerData(player);
            assertTrue("プレイヤーデータ更新が成功するべき", result);
            
            Player updatedPlayer = playerDAO.getPlayer(testUuid);
            assertEquals("残高が正しく更新されるべき", 1200.0, updatedPlayer.getBalance(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testTransferBalanceStringVersion() {
        UUID fromUuid = UUID.randomUUID();
        UUID toUuid = UUID.randomUUID();
        
        try {
            playerDAO.createPlayer(new Player(fromUuid, 0.0, 900.0));
            playerDAO.createPlayer(new Player(toUuid, 0.0, 100.0));
            
            boolean result = playerDAO.transferBalance(fromUuid.toString(), toUuid.toString(), 400.0);
            assertTrue("文字列UUID版の送金が成功するべき", result);
            
            Player fromPlayer = playerDAO.getPlayer(fromUuid);
            Player toPlayer = playerDAO.getPlayer(toUuid);
            
            assertEquals("送金者の銀行預金が正しく減額されるべき", 500.0, fromPlayer.getBankBalance(), DELTA);
            assertEquals("受取者の銀行預金が正しく増額されるべき", 500.0, toPlayer.getBankBalance(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testTransferBalanceInvalidStringUUID() {
        String invalidFromUuid = "invalid-from-uuid";
        String invalidToUuid = "invalid-to-uuid";
        
        boolean result = playerDAO.transferBalance(invalidFromUuid, invalidToUuid, 100.0);
        assertFalse("不正なUUID形式の場合は送金が失敗するべき", result);
    }

    @Test
    public void testGetTopPlayersByBalance() {
        try {
            // テストデータ作成
            playerDAO.createPlayer(new Player(UUID.randomUUID(), 1500.0));
            playerDAO.createPlayer(new Player(UUID.randomUUID(), 800.0));
            playerDAO.createPlayer(new Player(UUID.randomUUID(), 2200.0));
            
            List<Player> topPlayers = playerDAO.getTopPlayersByBalance(2);
            
            assertEquals("指定した件数が取得されるべき", 2, topPlayers.size());
            assertEquals("1位の残高が正しい", 2200.0, topPlayers.get(0).getBalance(), DELTA);
            assertEquals("2位の残高が正しい", 1500.0, topPlayers.get(1).getBalance(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testDuplicatePlayerCreation() {
        UUID testUuid = UUID.randomUUID();
        Player player1 = new Player(testUuid, 100.0);
        Player player2 = new Player(testUuid, 200.0);
        
        // 1回目は成功するはず
        boolean result1 = playerDAO.insertPlayer(player1);
        assertTrue("1回目のプレイヤー作成は成功するべき", result1);
        
        // 同じUUIDでの2回目は失敗するはず（主キー制約）
        boolean result2 = playerDAO.insertPlayer(player2);
        assertFalse("同じUUIDでの重複作成は失敗するべき", result2);
    }

    @Test
    public void testLargeBalanceValues() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        double largeBalance = 999999999.99;
        Player player = new Player(testUuid, largeBalance);
        
        playerDAO.createPlayer(player);
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        
        assertEquals("大きな残高値が正しく保存されるべき", largeBalance, retrievedPlayer.getBalance(), DELTA);
    }

    @Test
    public void testZeroBalance() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 0.0);
        
        playerDAO.createPlayer(player);
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        
        assertEquals("ゼロ残高が正しく保存されるべき", 0.0, retrievedPlayer.getBalance(), DELTA);
        assertEquals("ゼロ銀行預金が正しく初期化されるべき", 0.0, retrievedPlayer.getBankBalance(), DELTA);
    }

    @Test
    public void testBankBalanceOperations() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        double initialBalance = 1000.0;
        double initialBankBalance = 2000.0;
        Player player = new Player(testUuid, initialBalance, initialBankBalance);
        
        playerDAO.createPlayer(player);
        
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        assertNotNull("作成したプレイヤーが取得できるべき", retrievedPlayer);
        assertEquals("残高が正しく保存されるべき", initialBalance, retrievedPlayer.getBalance(), DELTA);
        assertEquals("銀行預金が正しく保存されるべき", initialBankBalance, retrievedPlayer.getBankBalance(), DELTA);
        
        // 銀行預金を変更
        double newBankBalance = 3000.0;
        retrievedPlayer.setBankBalance(newBankBalance);
        playerDAO.updatePlayer(retrievedPlayer);
        
        Player updatedPlayer = playerDAO.getPlayer(testUuid);
        assertEquals("更新された銀行預金が正しく保存されるべき", newBankBalance, updatedPlayer.getBankBalance(), DELTA);
        assertEquals("残高は変更されないべき", initialBalance, updatedPlayer.getBalance(), DELTA);
    }

    @Test
    public void testUpdateBankBalance() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        Player player = new Player(testUuid, 500.0, 1000.0);
        playerDAO.createPlayer(player);
        
        double newBankBalance = 1500.0;
        playerDAO.updateBankBalance(testUuid, newBankBalance);
        
        Player retrievedPlayer = playerDAO.getPlayer(testUuid);
        assertEquals("銀行預金が更新されるべき", newBankBalance, retrievedPlayer.getBankBalance(), DELTA);
        assertEquals("残高は変更されないべき", 500.0, retrievedPlayer.getBalance(), DELTA);
    }
}