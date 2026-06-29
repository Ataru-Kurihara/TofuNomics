package org.tofu.tofunomics.experience;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * /tntest exp 等で使う giveExperienceManual の検証。
 *
 * 不具合: 経験値を付与しても /jobs stats の表示が変わらない。
 * 原因は giveJobExperience が updatePlayerJobData の失敗を無視し、giveExperienceManual が
 * 無条件 true を返すため、DB保存に失敗しても「付与しました」と誤報告されていたこと。
 * 本テストは「保存成功時は in-memory に加算され true を返す」「保存失敗時は false を返す」を固定する。
 */
public class JobExperienceManagerManualExpTest {

    private ConfigManager configManager;
    private PlayerJobDAO playerJobDAO;
    private JobManager jobManager;
    private ExperienceManager experienceManager;
    private JobExperienceManager manager;
    private Player player;
    private PlayerJob playerJob;

    private final UUID playerUuid = UUID.randomUUID();
    private static final String JOB = "miner";
    private static final int LEVEL = 50;

    @Before
    public void setUp() {
        configManager = mock(ConfigManager.class);
        when(configManager.getJobDisplayName(JOB)).thenReturn("鉱夫");
        when(configManager.getJobMaxLevel(JOB)).thenReturn(100);

        playerJobDAO = mock(PlayerJobDAO.class);
        // calculateRequiredExperience は純粋関数。レベル判定で実値を使うため本物を注入する
        experienceManager = new ExperienceManager(configManager, playerJobDAO);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Tester");

        // Lv50、経験値はちょうど Lv50 の閾値（= レベル内進捗0）
        playerJob = new PlayerJob();
        playerJob.setUuid(playerUuid.toString());
        playerJob.setJobId(1);
        playerJob.setLevel(LEVEL);
        playerJob.setExperience(experienceManager.calculateRequiredExperience(LEVEL));

        jobManager = mock(JobManager.class);
        when(jobManager.hasJob(player, JOB)).thenReturn(true);
        when(jobManager.getPlayerJob(player, JOB)).thenReturn(playerJob);

        // jobDAO/jobToolManager は今回の経路では未使用のため null
        manager = new JobExperienceManager(configManager, playerJobDAO, null, jobManager, null, experienceManager);
    }

    @Test
    public void testManualExp_persistsAndReturnsTrue() {
        when(playerJobDAO.updatePlayerJobData(playerJob)).thenReturn(true);
        double base = experienceManager.calculateRequiredExperience(LEVEL);

        boolean ok = manager.giveExperienceManual(player, JOB, 100.0);

        assertTrue("DB保存成功時は true を返す", ok);
        assertEquals("in-memory に +100 加算される", base + 100.0, playerJob.getExperience(), 0.0001);
        assertEquals("Lv50では100付与でレベルアップしない", LEVEL, playerJob.getLevel());
        verify(playerJobDAO, times(1)).updatePlayerJobData(playerJob);
    }

    @Test
    public void testManualExp_dbFailureReturnsFalse() {
        // DB保存が失敗する状況を再現（SQLITE_BUSY 等で updatePlayerJobData が false を返す）
        when(playerJobDAO.updatePlayerJobData(playerJob)).thenReturn(false);

        boolean ok = manager.giveExperienceManual(player, JOB, 100.0);

        assertFalse("DB保存失敗時は false を返し、成功と誤報告しない", ok);
    }

    @Test
    public void testManualExp_noJobReturnsFalse() {
        when(jobManager.hasJob(player, JOB)).thenReturn(false);

        boolean ok = manager.giveExperienceManual(player, JOB, 100.0);

        assertFalse("未就職なら false", ok);
        verify(playerJobDAO, never()).updatePlayerJobData(any());
    }
}
