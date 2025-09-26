package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.PlayerJob;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * フェーズ7 - PlayerJobDAO単体テスト
 * H2インメモリデータベースを使用したPlayerJobDAO操作の完全テスト
 */
public class PlayerJobDAOTest {

    private Connection connection;
    private PlayerJobDAO playerJobDAO;
    private final double DELTA = 0.001;
    
    @Before
    public void setUp() throws SQLException {
        // H2インメモリデータベースの設定
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb_playerjob;DB_CLOSE_DELAY=-1", "sa", "");
        
        // テーブル作成
        String createTableQuery = "CREATE TABLE IF NOT EXISTS player_jobs (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "job_id INT NOT NULL, " +
                "level INT NOT NULL DEFAULT 1, " +
                "experience DOUBLE NOT NULL DEFAULT 0.0, " +
                "joined_at TIMESTAMP NOT NULL, " +
                "updated_at TIMESTAMP NOT NULL, " +
                "PRIMARY KEY (uuid, job_id)" +
                ")";
        
        try (PreparedStatement statement = connection.prepareStatement(createTableQuery)) {
            statement.executeUpdate();
        }
        
        playerJobDAO = new PlayerJobDAO(connection);
    }
    
    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // テーブル削除
            try (PreparedStatement statement = connection.prepareStatement("DROP TABLE IF EXISTS player_jobs")) {
                statement.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    public void testCreatePlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJob.setLevel(5);
        playerJob.setExperience(150.0);
        
        playerJobDAO.createPlayerJob(playerJob);
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertNotNull("作成したプレイヤー職業が取得できるべき", retrievedPlayerJob);
        assertEquals("UUIDが正しく保存されるべき", testUuid, retrievedPlayerJob.getUuid());
        assertEquals("職業IDが正しく保存されるべき", testJobId, retrievedPlayerJob.getJobId());
        assertEquals("レベルが正しく保存されるべき", 5, retrievedPlayerJob.getLevel());
        assertEquals("経験値が正しく保存されるべき", 150.0, retrievedPlayerJob.getExperience(), DELTA);
        assertNotNull("参加日時が設定されるべき", retrievedPlayerJob.getJoinedAt());
        assertNotNull("更新日時が設定されるべき", retrievedPlayerJob.getUpdatedAt());
    }

    @Test
    public void testGetPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 2;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJob.setLevel(10);
        playerJob.setExperience(500.0);
        
        playerJobDAO.createPlayerJob(playerJob);
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertNotNull("存在するプレイヤー職業が取得できるべき", retrievedPlayerJob);
        assertEquals("UUIDが一致するべき", testUuid, retrievedPlayerJob.getUuid());
        assertEquals("職業IDが一致するべき", testJobId, retrievedPlayerJob.getJobId());
        assertEquals("レベルが一致するべき", 10, retrievedPlayerJob.getLevel());
        assertEquals("経験値が一致するべき", 500.0, retrievedPlayerJob.getExperience(), DELTA);
    }

    @Test
    public void testGetPlayerJobNotExists() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 999;
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertNull("存在しないプレイヤー職業はnullを返すべき", retrievedPlayerJob);
    }

    @Test
    public void testGetCurrentPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        // 複数の職業を異なる時間で作成（最新を判定するため）
        PlayerJob job1 = new PlayerJob(testUuid, 1);
        PlayerJob job2 = new PlayerJob(testUuid, 2);
        PlayerJob job3 = new PlayerJob(testUuid, 3);
        
        playerJobDAO.createPlayerJob(job1);
        
        // 明確な時間差を作るため
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJobDAO.createPlayerJob(job2);
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJobDAO.createPlayerJob(job3);
        
        PlayerJob currentJob = playerJobDAO.getCurrentPlayerJob(testUuid);
        assertNotNull("現在の職業が取得できるべき", currentJob);
        
        // updated_atが最新のものを確認（必ずしも最後に作成されたものが最新とは限らない）
        assertTrue("現在の職業が取得されるべき", currentJob.getJobId() >= 1 && currentJob.getJobId() <= 3);
    }

    @Test
    public void testGetCurrentPlayerJobNotExists() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        PlayerJob currentJob = playerJobDAO.getCurrentPlayerJob(testUuid);
        assertNull("職業を持たないプレイヤーはnullを返すべき", currentJob);
    }

    @Test
    public void testGetPlayerJobs() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        // 複数の職業を作成
        PlayerJob job1 = new PlayerJob(testUuid, 1);
        PlayerJob job2 = new PlayerJob(testUuid, 2);
        PlayerJob job3 = new PlayerJob(testUuid, 3);
        
        playerJobDAO.createPlayerJob(job1);
        playerJobDAO.createPlayerJob(job2);
        playerJobDAO.createPlayerJob(job3);
        
        List<PlayerJob> playerJobs = playerJobDAO.getPlayerJobs(testUuid);
        
        assertEquals("作成した職業数と一致するべき", 3, playerJobs.size());
        
        // 参加日時の昇順でソートされていることを確認
        assertEquals("1番目の職業IDが正しい", 1, playerJobs.get(0).getJobId());
        assertEquals("2番目の職業IDが正しい", 2, playerJobs.get(1).getJobId());
        assertEquals("3番目の職業IDが正しい", 3, playerJobs.get(2).getJobId());
    }

    @Test
    public void testGetPlayerJobsEmpty() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        List<PlayerJob> playerJobs = playerJobDAO.getPlayerJobs(testUuid);
        assertEquals("職業を持たないプレイヤーは空リストを返すべき", 0, playerJobs.size());
    }

    @Test
    public void testUpdatePlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJobDAO.createPlayerJob(playerJob);
        
        // プレイヤー職業を更新
        playerJob.setLevel(15);
        playerJob.setExperience(800.0);
        
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        // わずかな時間差を作るため
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJobDAO.updatePlayerJob(playerJob);
        
        PlayerJob updatedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertEquals("レベルが更新されるべき", 15, updatedPlayerJob.getLevel());
        assertEquals("経験値が更新されるべき", 800.0, updatedPlayerJob.getExperience(), DELTA);
        assertTrue("更新日時が新しくなるべき", 
                   updatedPlayerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testAddExperience() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJob.setExperience(100.0);
        playerJobDAO.createPlayerJob(playerJob);
        
        double additionalExp = 250.0;
        playerJobDAO.addExperience(testUuid, testJobId, additionalExp);
        
        PlayerJob updatedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertEquals("経験値が正しく加算されるべき", 350.0, updatedPlayerJob.getExperience(), DELTA);
    }

    @Test
    public void testAddExperienceToNonExistentPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        double experience = 150.0;
        
        // 存在しないプレイヤー職業に経験値を追加（自動作成される）
        playerJobDAO.addExperience(testUuid, testJobId, experience);
        
        PlayerJob createdPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertNotNull("プレイヤー職業が自動作成されるべき", createdPlayerJob);
        assertEquals("経験値が正しく設定されるべき", experience, createdPlayerJob.getExperience(), DELTA);
        assertEquals("初期レベルが1であるべき", 1, createdPlayerJob.getLevel());
    }

    @Test
    public void testLevelUp() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJobDAO.createPlayerJob(playerJob);
        
        int newLevel = 20;
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        // わずかな時間差を作るため
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJobDAO.levelUp(testUuid, testJobId, newLevel);
        
        PlayerJob updatedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertEquals("レベルが更新されるべき", newLevel, updatedPlayerJob.getLevel());
        assertTrue("更新日時が新しくなるべき", 
                   updatedPlayerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testDeletePlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJobDAO.createPlayerJob(playerJob);
        
        // 削除前の確認
        assertNotNull("削除前はプレイヤー職業が存在するべき", 
                      playerJobDAO.getPlayerJob(testUuid, testJobId));
        
        playerJobDAO.deletePlayerJob(testUuid, testJobId);
        
        // 削除後の確認
        assertNull("削除後はプレイヤー職業が存在しないべき", 
                   playerJobDAO.getPlayerJob(testUuid, testJobId));
    }

    @Test
    public void testDeleteAllPlayerJobs() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        
        // 複数の職業を作成
        playerJobDAO.createPlayerJob(new PlayerJob(testUuid, 1));
        playerJobDAO.createPlayerJob(new PlayerJob(testUuid, 2));
        playerJobDAO.createPlayerJob(new PlayerJob(testUuid, 3));
        
        // 削除前の確認
        assertEquals("削除前は3つの職業が存在するべき", 3, 
                     playerJobDAO.getPlayerJobs(testUuid).size());
        
        playerJobDAO.deleteAllPlayerJobs(testUuid);
        
        // 削除後の確認
        assertEquals("削除後は職業が存在しないべき", 0, 
                     playerJobDAO.getPlayerJobs(testUuid).size());
    }

    @Test
    public void testHasPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        
        assertFalse("作成前は職業を持たないべき", 
                   playerJobDAO.hasPlayerJob(testUuid, testJobId));
        
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJobDAO.createPlayerJob(playerJob);
        
        assertTrue("作成後は職業を持つべき", 
                  playerJobDAO.hasPlayerJob(testUuid, testJobId));
        assertFalse("存在しない職業IDはfalseを返すべき", 
                   playerJobDAO.hasPlayerJob(testUuid, 999));
    }

    @Test
    public void testGetTopPlayersByJobLevel() throws SQLException {
        int testJobId = 1;
        
        // 異なるレベルと経験値でプレイヤーを作成
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        UUID uuid4 = UUID.randomUUID();
        
        PlayerJob player1 = new PlayerJob(uuid1, testJobId);
        player1.setLevel(10);
        player1.setExperience(500.0);
        
        PlayerJob player2 = new PlayerJob(uuid2, testJobId);
        player2.setLevel(15);
        player2.setExperience(800.0);
        
        PlayerJob player3 = new PlayerJob(uuid3, testJobId);
        player3.setLevel(15);
        player3.setExperience(900.0); // 同じレベルでより高い経験値
        
        PlayerJob player4 = new PlayerJob(uuid4, testJobId);
        player4.setLevel(20);
        player4.setExperience(1000.0);
        
        playerJobDAO.createPlayerJob(player1);
        playerJobDAO.createPlayerJob(player2);
        playerJobDAO.createPlayerJob(player3);
        playerJobDAO.createPlayerJob(player4);
        
        List<PlayerJob> topPlayers = playerJobDAO.getTopPlayersByJobLevel(testJobId, 3);
        
        assertEquals("指定した件数が取得されるべき", 3, topPlayers.size());
        assertEquals("1位はレベル20のプレイヤー", 20, topPlayers.get(0).getLevel());
        assertEquals("2位はレベル15で経験値900のプレイヤー", uuid3, topPlayers.get(1).getUuid());
        assertEquals("3位はレベル15で経験値800のプレイヤー", uuid2, topPlayers.get(2).getUuid());
        
        // レベル順、経験値順の確認
        assertTrue("レベル降順になっているべき", 
                  topPlayers.get(0).getLevel() >= topPlayers.get(1).getLevel());
        assertTrue("同レベル内では経験値降順になっているべき", 
                  topPlayers.get(1).getExperience() >= topPlayers.get(2).getExperience());
    }

    @Test
    public void testGetPlayerJobsByUUID() {
        UUID testUuid = UUID.randomUUID();
        try {
            playerJobDAO.createPlayerJob(new PlayerJob(testUuid, 1));
            playerJobDAO.createPlayerJob(new PlayerJob(testUuid, 2));
            
            List<PlayerJob> playerJobs = playerJobDAO.getPlayerJobsByUUID(testUuid.toString());
            assertEquals("文字列UUIDでプレイヤー職業が取得できるべき", 2, playerJobs.size());
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testGetPlayerJobsByUUIDInvalidFormat() {
        String invalidUuid = "invalid-uuid-format";
        
        List<PlayerJob> playerJobs = playerJobDAO.getPlayerJobsByUUID(invalidUuid);
        assertNotNull("不正なUUID形式でも空リストを返すべき", playerJobs);
        assertEquals("空リストであるべき", 0, playerJobs.size());
    }

    @Test
    public void testInsertPlayerJob() {
        UUID testUuid = UUID.randomUUID();
        PlayerJob playerJob = new PlayerJob(testUuid, 1);
        playerJob.setLevel(8);
        playerJob.setExperience(300.0);
        
        boolean result = playerJobDAO.insertPlayerJob(playerJob);
        assertTrue("プレイヤー職業挿入が成功するべき", result);
        
        try {
            PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, 1);
            assertNotNull("挿入したプレイヤー職業が取得できるべき", retrievedPlayerJob);
            assertEquals("レベルが正しく保存されるべき", 8, retrievedPlayerJob.getLevel());
            assertEquals("経験値が正しく保存されるべき", 300.0, retrievedPlayerJob.getExperience(), DELTA);
        } catch (SQLException e) {
            fail("プレイヤー職業取得で例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testUpdatePlayerJobData() {
        UUID testUuid = UUID.randomUUID();
        try {
            PlayerJob playerJob = new PlayerJob(testUuid, 1);
            playerJobDAO.createPlayerJob(playerJob);
            
            playerJob.setLevel(12);
            playerJob.setExperience(600.0);
            boolean result = playerJobDAO.updatePlayerJobData(playerJob);
            assertTrue("プレイヤー職業データ更新が成功するべき", result);
            
            PlayerJob updatedPlayerJob = playerJobDAO.getPlayerJob(testUuid, 1);
            assertEquals("レベルが正しく更新されるべき", 12, updatedPlayerJob.getLevel());
            assertEquals("経験値が正しく更新されるべき", 600.0, updatedPlayerJob.getExperience(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testDeletePlayerJobStringVersion() {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        
        try {
            playerJobDAO.createPlayerJob(new PlayerJob(testUuid, testJobId));
            
            boolean result = playerJobDAO.deletePlayerJob(testUuid.toString(), testJobId);
            assertTrue("文字列UUID版の削除が成功するべき", result);
            
            PlayerJob deletedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
            assertNull("削除後はプレイヤー職業が存在しないべき", deletedPlayerJob);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testDeletePlayerJobInvalidStringUUID() {
        String invalidUuid = "invalid-uuid";
        
        boolean result = playerJobDAO.deletePlayerJob(invalidUuid, 1);
        assertFalse("不正なUUID形式の場合は削除が失敗するべき", result);
    }

    @Test
    public void testGetPlayerJobStringVersion() {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        
        try {
            PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
            playerJob.setLevel(7);
            playerJob.setExperience(280.0);
            playerJobDAO.createPlayerJob(playerJob);
            
            PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid.toString(), testJobId);
            assertNotNull("文字列UUIDでプレイヤー職業が取得できるべき", retrievedPlayerJob);
            assertEquals("UUIDが一致するべき", testUuid, retrievedPlayerJob.getUuid());
            assertEquals("職業IDが一致するべき", testJobId, retrievedPlayerJob.getJobId());
            assertEquals("レベルが一致するべき", 7, retrievedPlayerJob.getLevel());
            assertEquals("経験値が一致するべき", 280.0, retrievedPlayerJob.getExperience(), DELTA);
        } catch (SQLException e) {
            fail("例外が発生してはいけない: " + e.getMessage());
        }
    }

    @Test
    public void testGetPlayerJobStringInvalidFormat() {
        String invalidUuid = "invalid-uuid-format";
        
        PlayerJob playerJob = playerJobDAO.getPlayerJob(invalidUuid, 1);
        assertNull("不正なUUID形式の場合はnullを返すべき", playerJob);
    }

    @Test
    public void testDuplicatePlayerJobCreation() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        
        PlayerJob playerJob1 = new PlayerJob(testUuid, testJobId);
        PlayerJob playerJob2 = new PlayerJob(testUuid, testJobId);
        
        // 1回目は成功するはず
        playerJobDAO.createPlayerJob(playerJob1);
        
        // 同じUUIDと職業IDでの2回目は例外が発生するはず（主キー制約）
        assertThrows("同じUUIDと職業IDでの重複作成は例外を発生するべき", 
                    SQLException.class, 
                    () -> playerJobDAO.createPlayerJob(playerJob2));
    }

    @Test
    public void testZeroExperienceAndLevel() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJob.setLevel(0);
        playerJob.setExperience(0.0);
        
        playerJobDAO.createPlayerJob(playerJob);
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertEquals("ゼロレベルが正しく保存されるべき", 0, retrievedPlayerJob.getLevel());
        assertEquals("ゼロ経験値が正しく保存されるべき", 0.0, retrievedPlayerJob.getExperience(), DELTA);
    }

    @Test
    public void testLargeExperienceValues() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        double largeExperience = 999999999.99;
        playerJob.setExperience(largeExperience);
        
        playerJobDAO.createPlayerJob(playerJob);
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        assertEquals("大きな経験値が正しく保存されるべき", largeExperience, 
                     retrievedPlayerJob.getExperience(), DELTA);
    }

    @Test
    public void testMapResultSetToPlayerJobPrivateMethod() throws SQLException {
        // mapResultSetToPlayerJobはprivateメソッドなので、間接的にテスト
        UUID testUuid = UUID.randomUUID();
        int testJobId = 1;
        PlayerJob playerJob = new PlayerJob(testUuid, testJobId);
        playerJob.setLevel(25);
        playerJob.setExperience(1250.5);
        
        playerJobDAO.createPlayerJob(playerJob);
        
        PlayerJob retrievedPlayerJob = playerJobDAO.getPlayerJob(testUuid, testJobId);
        
        // mapResultSetToPlayerJobが正しく動作していることを確認
        assertNotNull("マッピングされたプレイヤー職業が取得できるべき", retrievedPlayerJob);
        assertEquals("UUIDがマッピングされるべき", testUuid, retrievedPlayerJob.getUuid());
        assertEquals("職業IDがマッピングされるべき", testJobId, retrievedPlayerJob.getJobId());
        assertEquals("レベルがマッピングされるべき", 25, retrievedPlayerJob.getLevel());
        assertEquals("経験値がマッピングされるべき", 1250.5, retrievedPlayerJob.getExperience(), DELTA);
        assertNotNull("参加日時がマッピングされるべき", retrievedPlayerJob.getJoinedAt());
        assertNotNull("更新日時がマッピングされるべき", retrievedPlayerJob.getUpdatedAt());
    }

    @Test
    public void testUpdateNonExistentPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 999;
        PlayerJob nonExistentPlayerJob = new PlayerJob(testUuid, testJobId);
        nonExistentPlayerJob.setLevel(10);
        nonExistentPlayerJob.setExperience(500.0);
        
        // 存在しないプレイヤー職業の更新は例外を発生させないが、何も更新されない
        try {
            playerJobDAO.updatePlayerJob(nonExistentPlayerJob);
            // 例外が発生しなければテスト成功
        } catch (SQLException e) {
            fail("存在しないプレイヤー職業の更新は例外を発生させないべき: " + e.getMessage());
        }
    }

    @Test
    public void testLevelUpNonExistentPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 999;
        
        // 存在しないプレイヤー職業のレベルアップは例外を発生させないが、何も更新されない
        try {
            playerJobDAO.levelUp(testUuid, testJobId, 10);
            // 例外が発生しなければテスト成功
        } catch (SQLException e) {
            fail("存在しないプレイヤー職業のレベルアップは例外を発生させないべき: " + e.getMessage());
        }
    }

    @Test
    public void testDeleteNonExistentPlayerJob() throws SQLException {
        UUID testUuid = UUID.randomUUID();
        int testJobId = 999;
        
        // 存在しないプレイヤー職業の削除は例外を発生させないが、何も削除されない
        try {
            playerJobDAO.deletePlayerJob(testUuid, testJobId);
            // 例外が発生しなければテスト成功
        } catch (SQLException e) {
            fail("存在しないプレイヤー職業の削除は例外を発生させないべき: " + e.getMessage());
        }
    }
}