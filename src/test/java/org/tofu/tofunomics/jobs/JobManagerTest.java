package org.tofu.tofunomics.jobs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobChangeDAO;
import org.tofu.tofunomics.dao.JobHistoryDAO;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.JobHistory;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.jobs.JobManager.JobJoinResult;
import org.tofu.tofunomics.jobs.JobManager.JobLeaveResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * フェーズ7 - JobManager統合テスト
 * ビジネスロジックと各DAOクラスの連携をテスト
 */
public class JobManagerTest {

    @Mock
    private ConfigManager configManager;
    
    @Mock
    private JobDAO jobDAO;
    
    @Mock
    private PlayerDAO playerDAO;
    
    @Mock
    private PlayerJobDAO playerJobDAO;
    
    @Mock
    private JobChangeDAO jobChangeDAO;
    
    @Mock
    private JobHistoryDAO jobHistoryDAO;
    
    @Mock
    private Player player;
    
    private JobManager jobManager;
    private UUID playerUuid;
    private String playerUuidString;

    // JobManagerの一部メソッド（joinJob成功時の農家区画割当、resetAllJobs等）は
    // TofuNomics.getInstance() を参照するため、クラス全体でstaticをモックする
    private MockedStatic<org.tofu.tofunomics.TofuNomics> tofuNomicsStatic;
    private org.tofu.tofunomics.TofuNomics pluginMock;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        jobManager = new JobManager(configManager, jobDAO, playerDAO, playerJobDAO, jobChangeDAO, jobHistoryDAO);
        playerUuid = UUID.randomUUID();
        playerUuidString = playerUuid.toString();

        // プレイヤーのモック設定
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("TestPlayer");

        // TofuNomics.getInstance() をモック（ロガー取得・農家区画割当の参照に使用）
        tofuNomicsStatic = mockStatic(org.tofu.tofunomics.TofuNomics.class);
        pluginMock = mock(org.tofu.tofunomics.TofuNomics.class);
        when(pluginMock.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        when(pluginMock.getFarmPlotManager()).thenReturn(null); // 農家区画割当はスキップ
        tofuNomicsStatic.when(org.tofu.tofunomics.TofuNomics::getInstance).thenReturn(pluginMock);
    }

    @After
    public void tearDown() {
        if (tofuNomicsStatic != null) {
            tofuNomicsStatic.close();
        }
    }

    @Test
    public void testJoinJobSuccess() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        // モック設定
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);
        
        JobJoinResult result = jobManager.joinJob(player, jobName);
        
        assertEquals("職業参加が成功するべき", JobJoinResult.SUCCESS, result);
        verify(playerJobDAO).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinJobNotFound() {
        String jobName = "nonexistent";
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(null);
        
        JobJoinResult result = jobManager.joinJob(player, jobName);
        
        assertEquals("存在しない職業は失敗するべき", JobJoinResult.JOB_NOT_FOUND, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinJobLevelTooLow() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        // 既存の職業があるが、レベルが50未満の場合
        PlayerJob existingJob = new PlayerJob();
        existingJob.setJobId(2);
        existingJob.setLevel(30); // レベル30（50未満）
        List<PlayerJob> currentJobs = new ArrayList<>();
        currentJobs.add(existingJob);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(currentJobs);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(2);
        
        JobJoinResult result = jobManager.joinJob(player, jobName);
        
        assertEquals("レベル50未満で転職失敗するべき", JobJoinResult.LEVEL_TOO_LOW, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinJobMaxJobsReached() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        // 既に最大数の職業を持っている
        List<PlayerJob> existingJobs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PlayerJob playerJob = new PlayerJob();
            playerJob.setJobId(i + 10); // 異なるジョブID
            existingJobs.add(playerJob);
        }
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(existingJobs);
        // Lv50到達済み（転職可能）として、最大職業数チェックまで到達させる
        when(jobHistoryDAO.hasReachedLevel50(playerUuidString)).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("最大職業数到達で失敗するべき", JobJoinResult.MAX_JOBS_REACHED, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinJobAlreadyHasJob() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        // 既に同じ職業を持っている
        PlayerJob existingJob = new PlayerJob();
        existingJob.setJobId(1);
        List<PlayerJob> existingJobs = new ArrayList<>();
        existingJobs.add(existingJob);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(existingJobs);
        // Lv50到達済み（転職可能）として、既存職業チェックまで到達させる
        when(jobHistoryDAO.hasReachedLevel50(playerUuidString)).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("既に持っている職業で失敗するべき", JobJoinResult.ALREADY_HAS_JOB, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinAdvancedJobLockedOnFirstEmployment() {
        // 上級職業を初回就職しようとするとロックされる
        String jobName = "architect";
        Job job = new Job(jobName, "建築家", 100, 15.0);
        job.setId(8);

        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(1);
        when(configManager.isAdvancedJob(jobName)).thenReturn(true);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(jobHistoryDAO.hasReachedLevel50(playerUuidString)).thenReturn(false);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("初回就職で上級職業はロックされるべき", JobJoinResult.ADVANCED_JOB_LOCKED, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinAdvancedJobSuccessWithLevel50History() {
        // Lv50到達履歴があれば上級職業に就職できる
        String jobName = "architect";
        Job job = new Job(jobName, "建築家", 100, 15.0);
        job.setId(8);

        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(1);
        when(configManager.isAdvancedJob(jobName)).thenReturn(true);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(jobHistoryDAO.hasReachedLevel50(playerUuidString)).thenReturn(true);
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("Lv50到達履歴ありで上級職業に就職できるべき", JobJoinResult.SUCCESS, result);
        verify(playerJobDAO).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinNonAdvancedJobOnFirstEmployment() {
        // 通常職業は初回就職できる（既存挙動が壊れていないことの確認）
        String jobName = "miner";
        Job job = new Job(jobName, "鉱夫", 100, 15.0);
        job.setId(1);

        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(1);
        when(configManager.isAdvancedJob(jobName)).thenReturn(false);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("通常職業は初回就職できるべき", JobJoinResult.SUCCESS, result);
        verify(playerJobDAO).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testJoinJobDatabaseError() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(false);
        
        JobJoinResult result = jobManager.joinJob(player, jobName);
        
        assertEquals("データベースエラーで失敗するべき", JobJoinResult.DATABASE_ERROR, result);
    }

    @Test
    public void testJoinJobWithDailyLimitRecording() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(true);
        when(jobChangeDAO.canPlayerChangeJobToday(playerUuidString)).thenReturn(true);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);
        when(jobChangeDAO.recordJobChangeToday(playerUuidString)).thenReturn(true);
        
        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("1日制限が有効でも初回就職は成功するべき", JobJoinResult.SUCCESS, result);
        // 日次変更の記録は退職(leaveJob)時のみ。就職では消費しない
        verify(jobChangeDAO, never()).recordJobChangeToday(playerUuidString);
    }

    @Test
    public void testLeaveJobSuccess() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        PlayerJob playerJob = new PlayerJob();
        playerJob.setJobId(1);
        playerJob.setLevel(50); // 退職にはLv50到達が必要

        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(playerJob);
        when(jobHistoryDAO.insertJobHistory(any())).thenReturn(true);
        when(playerJobDAO.deletePlayerJob(playerUuidString, 1)).thenReturn(true);

        JobLeaveResult result = jobManager.leaveJob(player, jobName);

        assertEquals("職業離脱が成功するべき", JobLeaveResult.SUCCESS, result);
        verify(playerJobDAO).deletePlayerJob(playerUuidString, 1);
    }

    @Test
    public void testLeaveJobSavesExperienceToHistory() {
        // 辞職時に現在の経験値が job_history に保存されることを検証
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);

        PlayerJob playerJob = new PlayerJob();
        playerJob.setJobId(1);
        playerJob.setLevel(50);
        playerJob.setExperience(123456.0); // 付与済みの経験値

        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(playerJob);
        when(jobHistoryDAO.insertJobHistory(any())).thenReturn(true);
        when(playerJobDAO.deletePlayerJob(playerUuidString, 1)).thenReturn(true);

        JobLeaveResult result = jobManager.leaveJob(player, jobName);

        assertEquals("職業離脱が成功するべき", JobLeaveResult.SUCCESS, result);

        ArgumentCaptor<JobHistory> captor = ArgumentCaptor.forClass(JobHistory.class);
        verify(jobHistoryDAO).insertJobHistory(captor.capture());
        assertEquals("辞職時のレベルが履歴に保存されるべき", 50, captor.getValue().getMaxLevel());
        assertEquals("辞職時の経験値が履歴に保存されるべき", 123456.0, captor.getValue().getExperience(), 0.001);
    }

    @Test
    public void testJoinJobRestoresLevelAndExperienceFromHistory() {
        // 再就職時に job_history からレベルと経験値の両方が復元されることを検証
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);

        JobHistory history = new JobHistory(playerUuidString, 1, 50, 123456.0);

        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        when(jobHistoryDAO.getLatestJobHistory(playerUuidString, 1)).thenReturn(history);
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, jobName);

        assertEquals("再就職が成功するべき", JobJoinResult.SUCCESS, result);

        ArgumentCaptor<PlayerJob> captor = ArgumentCaptor.forClass(PlayerJob.class);
        verify(playerJobDAO).insertPlayerJob(captor.capture());
        assertEquals("過去の最高レベルが復元されるべき", 50, captor.getValue().getLevel());
        assertEquals("過去の経験値が復元されるべき", 123456.0, captor.getValue().getExperience(), 0.001);
    }

    @Test
    public void testLeaveJobDailyLimitExceeded() {
        String jobName = "farmer";
        
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(true);
        when(jobChangeDAO.canPlayerChangeJobToday(playerUuidString)).thenReturn(false);
        
        JobLeaveResult result = jobManager.leaveJob(player, jobName);
        
        assertEquals("1日制限超過で失敗するべき", JobLeaveResult.DAILY_LIMIT_EXCEEDED, result);
        verify(playerJobDAO, never()).deletePlayerJob(anyString(), anyInt());
    }

    @Test
    public void testLeaveJobNotFound() {
        String jobName = "nonexistent";
        
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(null);
        
        JobLeaveResult result = jobManager.leaveJob(player, jobName);
        
        assertEquals("存在しない職業で失敗するべき", JobLeaveResult.NO_SUCH_JOB, result);
        verify(playerJobDAO, never()).deletePlayerJob(anyString(), anyInt());
    }

    @Test
    public void testLeaveJobDatabaseError() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        PlayerJob playerJob = new PlayerJob();
        playerJob.setJobId(1);
        playerJob.setLevel(50); // 退職にはLv50到達が必要

        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(playerJob);
        when(jobHistoryDAO.insertJobHistory(any())).thenReturn(true);
        when(playerJobDAO.deletePlayerJob(playerUuidString, 1)).thenReturn(false);

        JobLeaveResult result = jobManager.leaveJob(player, jobName);

        assertEquals("データベースエラーで失敗するべき", JobLeaveResult.DATABASE_ERROR, result);
    }

    @Test
    public void testGetPlayerJobs() {
        List<PlayerJob> expectedJobs = new ArrayList<>();
        PlayerJob job1 = new PlayerJob();
        job1.setJobId(1);
        job1.setLevel(5);
        expectedJobs.add(job1);
        
        PlayerJob job2 = new PlayerJob();
        job2.setJobId(2);
        job2.setLevel(3);
        expectedJobs.add(job2);
        
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(expectedJobs);
        
        List<PlayerJob> actualJobs = jobManager.getPlayerJobs(player);
        
        assertEquals("プレイヤーの職業リストが正しく取得されるべき", expectedJobs.size(), actualJobs.size());
        assertEquals("1番目の職業IDが一致するべき", job1.getJobId(), actualJobs.get(0).getJobId());
        assertEquals("2番目の職業IDが一致するべき", job2.getJobId(), actualJobs.get(1).getJobId());
        verify(playerJobDAO).getPlayerJobsByUUID(playerUuidString);
    }

    @Test
    public void testGetPlayerJobsEmpty() {
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(new ArrayList<>());
        
        List<PlayerJob> actualJobs = jobManager.getPlayerJobs(player);
        
        assertEquals("職業を持たないプレイヤーは空リストを返すべき", 0, actualJobs.size());
        verify(playerJobDAO).getPlayerJobsByUUID(playerUuidString);
    }

    @Test
    public void testGetAllJobs() {
        List<Job> expectedJobs = new ArrayList<>();
        Job job1 = new Job("farmer", "農家", 100, 15.0);
        job1.setId(1);
        Job job2 = new Job("miner", "鉱夫", 80, 20.0);
        job2.setId(2);
        expectedJobs.add(job1);
        expectedJobs.add(job2);
        
        when(jobDAO.getAllJobsSafe()).thenReturn(expectedJobs);
        
        List<Job> actualJobs = jobManager.getAllJobs();
        
        assertEquals("全職業リストが正しく取得されるべき", expectedJobs.size(), actualJobs.size());
        assertEquals("1番目の職業名が一致するべき", "farmer", actualJobs.get(0).getName());
        assertEquals("2番目の職業名が一致するべき", "miner", actualJobs.get(1).getName());
        verify(jobDAO).getAllJobsSafe();
    }

    @Test
    public void testCanChangeJobToday() {
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(true);
        when(jobChangeDAO.canPlayerChangeJobToday(playerUuidString)).thenReturn(true);
        
        boolean canChange = jobManager.canChangeJobToday(player);
        
        assertTrue("プレイヤーが職業変更可能であるべき", canChange);
        verify(jobChangeDAO).canPlayerChangeJobToday(playerUuidString);
    }

    @Test
    public void testCanChangeJobTodayDisabled() {
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(false);
        
        boolean canChange = jobManager.canChangeJobToday(player);
        
        assertTrue("制限が無効の場合は常に変更可能であるべき", canChange);
        verify(jobChangeDAO, never()).canPlayerChangeJobToday(anyString());
    }

    @Test
    public void testCanChangeJobTodayLimitExceeded() {
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(true);
        when(jobChangeDAO.canPlayerChangeJobToday(playerUuidString)).thenReturn(false);
        
        boolean canChange = jobManager.canChangeJobToday(player);
        
        assertFalse("制限超過の場合は変更不可であるべき", canChange);
        verify(jobChangeDAO).canPlayerChangeJobToday(playerUuidString);
    }

    @Test
    public void testGetPlayerJob() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        PlayerJob playerJob = new PlayerJob();
        playerJob.setJobId(1);
        playerJob.setLevel(15);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(playerJob);
        
        PlayerJob result = jobManager.getPlayerJob(player, jobName);
        
        assertNotNull("プレイヤーの職業が取得されるべき", result);
        assertEquals("プレイヤーの職業レベルが正しいべき", 15, result.getLevel());
        verify(jobDAO).getJobByNameSafe(jobName);
        verify(playerJobDAO).getPlayerJob(playerUuidString, 1);
    }

    @Test
    public void testGetPlayerJobNotFound() {
        String jobName = "nonexistent";
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(null);
        
        PlayerJob result = jobManager.getPlayerJob(player, jobName);
        
        assertNull("存在しない職業はnullを返すべき", result);
        verify(jobDAO).getJobByNameSafe(jobName);
    }

    @Test
    public void testHasJob() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        PlayerJob playerJob = new PlayerJob();
        playerJob.setJobId(1);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(playerJob);
        
        boolean hasJob = jobManager.hasJob(player, jobName);
        
        assertTrue("プレイヤーが職業を持っているべき", hasJob);
        verify(jobDAO).getJobByNameSafe(jobName);
        verify(playerJobDAO).getPlayerJob(playerUuidString, 1);
    }

    @Test
    public void testHasJobFalse() {
        String jobName = "farmer";
        Job job = new Job(jobName, "農家", 100, 15.0);
        job.setId(1);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        when(playerJobDAO.getPlayerJob(playerUuidString, 1)).thenReturn(null);
        
        boolean hasJob = jobManager.hasJob(player, jobName);
        
        assertFalse("プレイヤーが職業を持たないべき", hasJob);
        verify(jobDAO).getJobByNameSafe(jobName);
        verify(playerJobDAO).getPlayerJob(playerUuidString, 1);
    }

    @Test
    public void testGetJobByName() {
        String jobName = "farmer";
        Job expectedJob = new Job(jobName, "農家", 100, 15.0);
        expectedJob.setId(1);
        
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(expectedJob);
        
        Job actualJob = jobManager.getJobByName(jobName);
        
        assertNotNull("職業が取得されるべき", actualJob);
        assertEquals("職業名が一致するべき", jobName, actualJob.getName());
        verify(jobDAO).getJobByNameSafe(jobName);
    }

    @Test
    public void testComplexScenarioMaxJobsWithDailyLimit() {
        // 複雑なシナリオ：最大職業数に達している状態で、制限付きで新しい職業に参加
        String newJobName = "builder";
        Job newJob = new Job(newJobName, "建築家", 90, 18.0);
        newJob.setId(4);
        
        // 既に3つの職業を持っている（最大数）
        List<PlayerJob> existingJobs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            PlayerJob job = new PlayerJob();
            job.setJobId(i);
            job.setLevel(5);
            existingJobs.add(job);
        }
        
        when(jobDAO.getJobByNameSafe(newJobName)).thenReturn(newJob);
        when(configManager.isDailyJobChangeLimitEnabled()).thenReturn(true);
        when(jobChangeDAO.canPlayerChangeJobToday(playerUuidString)).thenReturn(true);
        when(configManager.getMaxJobsPerPlayer()).thenReturn(3);
        when(playerJobDAO.getPlayerJobsByUUID(playerUuidString)).thenReturn(existingJobs);
        // Lv50到達済み（転職可能）として、最大職業数チェックまで到達させる
        when(jobHistoryDAO.hasReachedLevel50(playerUuidString)).thenReturn(true);

        JobJoinResult result = jobManager.joinJob(player, newJobName);

        assertEquals("最大職業数到達により失敗するべき", JobJoinResult.MAX_JOBS_REACHED, result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
        verify(jobChangeDAO, never()).recordJobChangeToday(anyString());
    }

    // ===== 管理者テスト用 adminForceJoinJob =====

    @Test
    public void testAdminForceJoinJobSuccess() throws Exception {
        // 上級職を含む各種就職制限を無視して、強制的に就職させられること
        String jobName = "miner"; // farmer以外を使い畑区画割当(TofuNomics経由)の副作用を避ける
        Job job = new Job(jobName, "鉱夫", 100, 12.0);
        job.setId(1);

        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(job);
        // resetAllJobs内部の削除呼び出し
        when(jobHistoryDAO.deleteAllHistoriesByUUID(playerUuidString)).thenReturn(true);
        // ensurePlayerExists: 既存プレイヤーありとして新規作成を回避
        when(playerDAO.getPlayerByUUID(playerUuidString))
            .thenReturn(new org.tofu.tofunomics.models.Player(playerUuid, 100.0));
        when(playerJobDAO.insertPlayerJob(any(PlayerJob.class))).thenReturn(true);

        // TofuNomics.getInstance() はクラス共通の@Beforeでモック済み
        PlayerJob result = jobManager.adminForceJoinJob(player, jobName);

        assertNotNull("強制就職でPlayerJobが返るべき", result);
        assertEquals("職業IDが一致するべき", 1, result.getJobId());
        assertEquals("初期レベルは1であるべき", 1, result.getLevel());
        // 既存データのリセットと新規登録が行われること
        verify(playerJobDAO).deleteAllPlayerJobs(playerUuid);
        verify(playerJobDAO).insertPlayerJob(any(PlayerJob.class));
    }

    @Test
    public void testAdminForceJoinJobInvalidJob() throws Exception {
        // 存在しない職業名ではnullを返し、就職処理を行わないこと
        String jobName = "nonexistent";
        when(jobDAO.getJobByNameSafe(jobName)).thenReturn(null);

        PlayerJob result = jobManager.adminForceJoinJob(player, jobName);

        assertNull("不正な職業名ではnullを返すべき", result);
        verify(playerJobDAO, never()).insertPlayerJob(any(PlayerJob.class));
        verify(playerJobDAO, never()).deleteAllPlayerJobs(any(UUID.class));
    }
}