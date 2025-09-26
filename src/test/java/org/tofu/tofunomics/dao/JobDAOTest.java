package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.models.Job;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * フェーズ7 - JobDAO単体テスト
 * H2インメモリデータベースを使用したJobDAO操作の完全テスト
 */
public class JobDAOTest {

    private Connection connection;
    private JobDAO jobDAO;
    private final double DELTA = 0.001;
    
    @Before
    public void setUp() throws SQLException {
        // H2インメモリデータベースの設定
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb_job;DB_CLOSE_DELAY=-1", "sa", "");
        
        // テーブル作成（AUTO_INCREMENT主キー付き）
        String createTableQuery = "CREATE TABLE IF NOT EXISTS jobs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL UNIQUE, " +
                "display_name VARCHAR(100) NOT NULL, " +
                "max_level INT NOT NULL DEFAULT 100, " +
                "base_income DOUBLE NOT NULL DEFAULT 0.0, " +
                "created_at TIMESTAMP NOT NULL" +
                ")";
        
        try (PreparedStatement statement = connection.prepareStatement(createTableQuery)) {
            statement.executeUpdate();
        }
        
        jobDAO = new JobDAO(connection);
    }
    
    @After
    public void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // テーブル削除
            try (PreparedStatement statement = connection.prepareStatement("DROP TABLE IF EXISTS jobs")) {
                statement.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    public void testCreateJob() throws SQLException {
        String jobName = "farmer";
        String displayName = "農家";
        int maxLevel = 100;
        double baseIncome = 15.0;
        
        Job job = new Job(jobName, displayName, maxLevel, baseIncome);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName(jobName);
        assertNotNull("作成した職業が取得できるべき", retrievedJob);
        assertEquals("職業名が正しく保存されるべき", jobName, retrievedJob.getName());
        assertEquals("表示名が正しく保存されるべき", displayName, retrievedJob.getDisplayName());
        assertEquals("最大レベルが正しく保存されるべき", maxLevel, retrievedJob.getMaxLevel());
        assertEquals("基本収入が正しく保存されるべき", baseIncome, retrievedJob.getBaseIncome(), DELTA);
        assertNotNull("作成日時が設定されるべき", retrievedJob.getCreatedAt());
        assertTrue("IDが自動生成されるべき", retrievedJob.getId() > 0);
    }

    @Test
    public void testGetJobById() throws SQLException {
        Job job = new Job("miner", "鉱夫", 80, 20.0);
        jobDAO.createJob(job);
        
        // IDで検索するために、まず名前で取得してIDを確認
        Job createdJob = jobDAO.getJobByName("miner");
        int jobId = createdJob.getId();
        
        Job retrievedJob = jobDAO.getJobById(jobId);
        assertNotNull("IDで職業が取得できるべき", retrievedJob);
        assertEquals("IDが一致するべき", jobId, retrievedJob.getId());
        assertEquals("職業名が一致するべき", "miner", retrievedJob.getName());
        assertEquals("表示名が一致するべき", "鉱夫", retrievedJob.getDisplayName());
    }

    @Test
    public void testGetJobByIdNotExists() throws SQLException {
        Job retrievedJob = jobDAO.getJobById(999);
        assertNull("存在しない職業IDはnullを返すべき", retrievedJob);
    }

    @Test
    public void testGetJobByName() throws SQLException {
        Job job = new Job("builder", "建築家", 90, 18.0);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName("builder");
        assertNotNull("名前で職業が取得できるべき", retrievedJob);
        assertEquals("職業名が一致するべき", "builder", retrievedJob.getName());
        assertEquals("表示名が一致するべき", "建築家", retrievedJob.getDisplayName());
        assertEquals("最大レベルが一致するべき", 90, retrievedJob.getMaxLevel());
        assertEquals("基本収入が一致するべき", 18.0, retrievedJob.getBaseIncome(), DELTA);
    }

    @Test
    public void testGetJobByNameNotExists() throws SQLException {
        Job retrievedJob = jobDAO.getJobByName("nonexistent");
        assertNull("存在しない職業名はnullを返すべき", retrievedJob);
    }

    @Test
    public void testGetAllJobs() throws SQLException {
        // 複数の職業を作成
        jobDAO.createJob(new Job("farmer", "農家", 100, 15.0));
        jobDAO.createJob(new Job("miner", "鉱夫", 80, 20.0));
        jobDAO.createJob(new Job("builder", "建築家", 90, 18.0));
        
        List<Job> allJobs = jobDAO.getAllJobs();
        
        assertEquals("作成した職業数と一致するべき", 3, allJobs.size());
        
        // IDの昇順でソートされていることを確認
        assertTrue("IDが昇順になっているべき", allJobs.get(0).getId() <= allJobs.get(1).getId());
        assertTrue("IDが昇順になっているべき", allJobs.get(1).getId() <= allJobs.get(2).getId());
        
        // 職業名が含まれていることを確認
        List<String> jobNames = new ArrayList<>();
        for (Job job : allJobs) {
            jobNames.add(job.getName());
        }
        assertTrue("farmer職業が含まれるべき", jobNames.contains("farmer"));
        assertTrue("miner職業が含まれるべき", jobNames.contains("miner"));
        assertTrue("builder職業が含まれるべき", jobNames.contains("builder"));
    }

    @Test
    public void testGetAllJobsEmpty() throws SQLException {
        List<Job> allJobs = jobDAO.getAllJobs();
        assertEquals("職業が存在しない場合は空リストを返すべき", 0, allJobs.size());
    }

    @Test
    public void testUpdateJob() throws SQLException {
        // 職業作成
        Job job = new Job("farmer", "農家", 100, 15.0);
        jobDAO.createJob(job);
        
        // 作成された職業を取得してIDを確認
        Job createdJob = jobDAO.getJobByName("farmer");
        int jobId = createdJob.getId();
        
        // 職業を更新
        Job updateJob = new Job("farmer", "上級農家", 120, 25.0);
        updateJob.setId(jobId);
        jobDAO.updateJob(updateJob);
        
        // 更新結果を確認
        Job updatedJob = jobDAO.getJobById(jobId);
        assertEquals("表示名が更新されるべき", "上級農家", updatedJob.getDisplayName());
        assertEquals("最大レベルが更新されるべき", 120, updatedJob.getMaxLevel());
        assertEquals("基本収入が更新されるべき", 25.0, updatedJob.getBaseIncome(), DELTA);
        assertEquals("職業名は変更されないべき", "farmer", updatedJob.getName());
    }

    @Test
    public void testDeleteJob() throws SQLException {
        // 職業作成
        Job job = new Job("temporary", "一時職業", 50, 10.0);
        jobDAO.createJob(job);
        
        Job createdJob = jobDAO.getJobByName("temporary");
        int jobId = createdJob.getId();
        
        // 削除前の確認
        assertNotNull("削除前は職業が存在するべき", jobDAO.getJobById(jobId));
        
        // 削除実行
        jobDAO.deleteJob(jobId);
        
        // 削除後の確認
        assertNull("削除後は職業が存在しないべき", jobDAO.getJobById(jobId));
        assertNull("名前での検索でも存在しないべき", jobDAO.getJobByName("temporary"));
    }

    @Test
    public void testJobExists() throws SQLException {
        String jobName = "blacksmith";
        assertFalse("作成前は職業が存在しないべき", jobDAO.jobExists(jobName));
        
        jobDAO.createJob(new Job(jobName, "鍛冶屋", 85, 22.0));
        
        assertTrue("作成後は職業が存在するべき", jobDAO.jobExists(jobName));
        assertFalse("存在しない職業名はfalseを返すべき", jobDAO.jobExists("nonexistent"));
    }

    @Test
    public void testGetJobNames() throws SQLException {
        // 複数の職業を作成
        jobDAO.createJob(new Job("farmer", "農家", 100, 15.0));
        jobDAO.createJob(new Job("miner", "鉱夫", 80, 20.0));
        jobDAO.createJob(new Job("builder", "建築家", 90, 18.0));
        
        List<String> jobNames = jobDAO.getJobNames();
        
        assertEquals("職業名の数が正しいべき", 3, jobNames.size());
        assertTrue("farmer職業名が含まれるべき", jobNames.contains("farmer"));
        assertTrue("miner職業名が含まれるべき", jobNames.contains("miner"));
        assertTrue("builder職業名が含まれるべき", jobNames.contains("builder"));
    }

    @Test
    public void testGetJobDisplayNames() throws SQLException {
        // 複数の職業を作成
        jobDAO.createJob(new Job("farmer", "農家", 100, 15.0));
        jobDAO.createJob(new Job("miner", "鉱夫", 80, 20.0));
        jobDAO.createJob(new Job("builder", "建築家", 90, 18.0));
        
        List<String> displayNames = jobDAO.getJobDisplayNames();
        
        assertEquals("表示名の数が正しいべき", 3, displayNames.size());
        assertTrue("農家表示名が含まれるべき", displayNames.contains("農家"));
        assertTrue("鉱夫表示名が含まれるべき", displayNames.contains("鉱夫"));
        assertTrue("建築家表示名が含まれるべき", displayNames.contains("建築家"));
    }

    @Test
    public void testGetJobByNameSafe() {
        // 新しい無効なconnectionでテスト用DAOを作成
        try {
            Connection invalidConnection = DriverManager.getConnection("jdbc:h2:mem:invalid_test4_" + System.currentTimeMillis(), "sa", "");
            invalidConnection.close(); // 意図的に閉じる
            
            JobDAO invalidJobDAO = new JobDAO(invalidConnection);
            Job job = invalidJobDAO.getJobByNameSafe("farmer");
            assertNull("データベースエラー時はnullを返すべき", job);
        } catch (SQLException e) {
            fail("テスト実行中にSQLExceptionが発生しました: " + e.getMessage());
        }
    }

    @Test
    public void testGetAllJobsSafe() {
        // 新しい無効なconnectionでテスト用DAOを作成
        try {
            Connection invalidConnection = DriverManager.getConnection("jdbc:h2:mem:invalid_test_" + System.currentTimeMillis(), "sa", "");
            invalidConnection.close(); // 意図的に閉じる
            
            JobDAO invalidJobDAO = new JobDAO(invalidConnection);
            List<Job> jobs = invalidJobDAO.getAllJobsSafe();
            assertNotNull("データベースエラー時は空リストを返すべき", jobs);
            assertEquals("空リストであるべき", 0, jobs.size());
        } catch (SQLException e) {
            fail("テスト実行中にSQLExceptionが発生しました: " + e.getMessage());
        }
    }

    @Test
    public void testJobExistsSafe() {
        // 新しい無効なconnectionでテスト用DAOを作成
        try {
            Connection invalidConnection = DriverManager.getConnection("jdbc:h2:mem:invalid_test2_" + System.currentTimeMillis(), "sa", "");
            invalidConnection.close(); // 意図的に閉じる
            
            JobDAO invalidJobDAO = new JobDAO(invalidConnection);
            boolean exists = invalidJobDAO.jobExistsSafe("farmer");
            assertFalse("データベースエラー時はfalseを返すべき", exists);
        } catch (SQLException e) {
            fail("テスト実行中にSQLExceptionが発生しました: " + e.getMessage());
        }
    }

    @Test
    public void testGetJobNamesSafe() {
        // 新しい無効なconnectionでテスト用DAOを作成
        try {
            Connection invalidConnection = DriverManager.getConnection("jdbc:h2:mem:invalid_test3_" + System.currentTimeMillis(), "sa", "");
            invalidConnection.close(); // 意図的に閉じる
            
            JobDAO invalidJobDAO = new JobDAO(invalidConnection);
            List<String> jobNames = invalidJobDAO.getJobNamesSafe();
            assertNotNull("データベースエラー時は空リストを返すべき", jobNames);
            assertEquals("空リストであるべき", 0, jobNames.size());
        } catch (SQLException e) {
            fail("テスト実行中にSQLExceptionが発生しました: " + e.getMessage());
        }
    }

    @Test
    public void testDuplicateJobCreation() throws SQLException {
        String jobName = "duplicate";
        Job job1 = new Job(jobName, "重複職業1", 100, 10.0);
        Job job2 = new Job(jobName, "重複職業2", 100, 20.0);
        
        // 1回目は成功するはず
        jobDAO.createJob(job1);
        Job createdJob = jobDAO.getJobByName(jobName);
        assertNotNull("1回目の職業作成は成功するべき", createdJob);
        
        // 同じ名前での2回目は例外が発生するはず（UNIQUE制約）
        assertThrows("同じ名前での重複作成は例外を発生するべき", 
                    SQLException.class, 
                    () -> jobDAO.createJob(job2));
    }

    @Test
    public void testJobWithNullValues() throws SQLException {
        Job job = new Job(null, null, 50, 10.0);
        
        // NULL値での作成は例外が発生するはず（NOT NULL制約）
        assertThrows("NULL値での職業作成は例外を発生するべき", 
                    SQLException.class, 
                    () -> jobDAO.createJob(job));
    }

    @Test
    public void testJobWithZeroMaxLevel() throws SQLException {
        Job job = new Job("zero_level", "ゼロレベル職", 0, 5.0);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName("zero_level");
        assertEquals("ゼロ最大レベルが正しく保存されるべき", 0, retrievedJob.getMaxLevel());
    }

    @Test
    public void testJobWithNegativeBaseIncome() throws SQLException {
        Job job = new Job("negative_income", "マイナス収入職", 100, -10.0);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName("negative_income");
        assertEquals("マイナス基本収入が正しく保存されるべき", -10.0, retrievedJob.getBaseIncome(), DELTA);
    }

    @Test
    public void testJobWithExtremeValues() throws SQLException {
        int maxLevel = Integer.MAX_VALUE;
        double baseIncome = Double.MAX_VALUE;
        Job job = new Job("extreme", "極値職業", maxLevel, baseIncome);
        
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName("extreme");
        assertEquals("極大最大レベルが正しく保存されるべき", maxLevel, retrievedJob.getMaxLevel());
        assertEquals("極大基本収入が正しく保存されるべき", baseIncome, retrievedJob.getBaseIncome(), DELTA);
    }

    @Test
    public void testJobWithLongNames() throws SQLException {
        String longName = new String(new char[49]).replace('\0', 'a'); // 50文字制限の直下
        String longDisplayName = new String(new char[99]).replace('\0', 'あ'); // 100文字制限の直下
        
        Job job = new Job(longName, longDisplayName, 100, 15.0);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName(longName);
        assertNotNull("長い名前の職業が作成できるべき", retrievedJob);
        assertEquals("長い職業名が正しく保存されるべき", longName, retrievedJob.getName());
        assertEquals("長い表示名が正しく保存されるべき", longDisplayName, retrievedJob.getDisplayName());
    }

    @Test
    public void testMapResultSetToJobPrivateMethod() throws SQLException {
        // mapResultSetToJobはprivateメソッドなので、間接的にテスト
        Job job = new Job("mapper_test", "マッパーテスト", 75, 12.5);
        jobDAO.createJob(job);
        
        Job retrievedJob = jobDAO.getJobByName("mapper_test");
        
        // mapResultSetToJobが正しく動作していることを確認
        assertNotNull("マッピングされた職業が取得できるべき", retrievedJob);
        assertTrue("IDがマッピングされるべき", retrievedJob.getId() > 0);
        assertEquals("名前がマッピングされるべき", "mapper_test", retrievedJob.getName());
        assertEquals("表示名がマッピングされるべき", "マッパーテスト", retrievedJob.getDisplayName());
        assertEquals("最大レベルがマッピングされるべき", 75, retrievedJob.getMaxLevel());
        assertEquals("基本収入がマッピングされるべき", 12.5, retrievedJob.getBaseIncome(), DELTA);
        assertNotNull("作成日時がマッピングされるべき", retrievedJob.getCreatedAt());
    }

    @Test
    public void testUpdateJobNonExistent() throws SQLException {
        Job nonExistentJob = new Job("nonexistent", "存在しない職業", 100, 10.0);
        nonExistentJob.setId(999); // 存在しないID
        
        // 存在しない職業の更新は例外を発生させないが、何も更新されない
        try {
            jobDAO.updateJob(nonExistentJob);
            // 例外が発生しなければテスト成功
        } catch (SQLException e) {
            fail("存在しない職業の更新は例外を発生させないべき: " + e.getMessage());
        }
    }

    @Test
    public void testDeleteJobNonExistent() throws SQLException {
        // 存在しない職業の削除は例外を発生させないが、何も削除されない
        try {
            jobDAO.deleteJob(999);
            // 例外が発生しなければテスト成功
        } catch (SQLException e) {
            fail("存在しない職業の削除は例外を発生させないべき: " + e.getMessage());
        }
    }
}