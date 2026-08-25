package org.tofu.tofunomics.experience;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.tools.JobToolManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 職業レベル上限の回帰テスト
 *
 * 修正前は checkLevelUp のループ条件が max_level を見ておらず、
 * 上限到達後も経験値を得るたびにレベルが上がり続け、
 * 「最大レベルに到達しました」演出が毎回再生されていた。
 */
public class JobExperienceManagerLevelCapTest {

    private static final String JOB_NAME = "miner";
    private static final int MAX_LEVEL = 75;

    @Mock private ConfigManager configManager;
    @Mock private PlayerJobDAO playerJobDAO;
    @Mock private JobDAO jobDAO;
    @Mock private JobManager jobManager;
    @Mock private JobToolManager jobToolManager;
    @Mock private Player player;

    private JobExperienceManager experienceManagerUnderTest;
    private PlayerJob playerJob;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // 演出はテスト対象外なので無効化する
        when(configManager.isLevelUpTitleEnabled()).thenReturn(false);
        when(configManager.isLevelUpParticleEnabled()).thenReturn(false);
        when(configManager.getJobDisplayName(JOB_NAME)).thenReturn("鉱夫");

        when(player.getName()).thenReturn("tester");

        Job job = new Job(JOB_NAME, "鉱夫", MAX_LEVEL, 1.0);
        when(jobDAO.getJobByNameSafe(JOB_NAME)).thenReturn(job);

        playerJob = new PlayerJob(UUID.randomUUID(), 1);
        when(jobManager.hasJob(player, JOB_NAME)).thenReturn(true);
        when(jobManager.getPlayerJob(player, JOB_NAME)).thenReturn(playerJob);

        when(playerJobDAO.updatePlayerJobData(any(PlayerJob.class))).thenReturn(true);

        ExperienceManager experienceManager = new ExperienceManager(configManager, playerJobDAO);
        experienceManagerUnderTest = new JobExperienceManager(
            configManager, playerJobDAO, jobDAO, jobManager, jobToolManager, experienceManager);
    }

    @Test
    public void 最大レベル到達後は経験値を得てもレベルが上がらない() {
        playerJob.setLevel(MAX_LEVEL);
        playerJob.setExperience(0);

        experienceManagerUnderTest.giveExperienceManual(player, JOB_NAME, 10_000_000);

        assertEquals("最大レベルを超えてレベルが上がってはいけない", MAX_LEVEL, playerJob.getLevel());
    }

    @Test
    public void 最大レベル到達後は最大レベル演出が再生されない() {
        playerJob.setLevel(MAX_LEVEL);
        playerJob.setExperience(0);

        experienceManagerUnderTest.giveExperienceManual(player, JOB_NAME, 10_000_000);

        verify(player, never()).sendMessage(contains("最大レベルに到達"));
    }

    @Test
    public void 最大レベルに到達した瞬間は演出が一度だけ再生される() {
        playerJob.setLevel(MAX_LEVEL - 1);
        playerJob.setExperience(0);

        // 上限直前から一気に上限へ到達させる
        experienceManagerUnderTest.giveExperienceManual(player, JOB_NAME, 10_000_000);

        assertEquals("上限ちょうどで止まること", MAX_LEVEL, playerJob.getLevel());
        verify(player, times(1)).sendMessage(contains("最大レベルに到達"));
    }
}
