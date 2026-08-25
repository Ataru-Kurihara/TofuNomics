package org.tofu.tofunomics.jobs;

import org.junit.After;
import org.junit.Test;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 必要経験値カーブのテスト
 *
 * 修正前は PlayerJob と ExperienceManager の2箇所に式がハードコードされており、
 * config/jobs.yml の leveling.experience 設定がまったく効いていなかった。
 */
public class ExperienceCurveTest {

    private static final double DELTA = 0.001;

    @After
    public void tearDown() {
        // 静的状態を持つため、テスト間で既定へ戻す
        ExperienceCurve.reset();
    }

    @Test
    public void 既定カーブがガイドブックの記載値と一致する() {
        ExperienceCurve.reset();

        // config/jobs.yml のガイドブックに載っている累計経験値
        assertEquals(1_393, Math.round(ExperienceCurve.requiredExperience(5)));
        assertEquals(6_502, Math.round(ExperienceCurve.requiredExperience(10)));
        assertEquals(41_918, Math.round(ExperienceCurve.requiredExperience(25)));
        assertEquals(162_694, Math.round(ExperienceCurve.requiredExperience(50)));
    }

    @Test
    public void レベル1以下は0経験値() {
        assertEquals(0.0, ExperienceCurve.requiredExperience(1), DELTA);
        assertEquals(0.0, ExperienceCurve.requiredExperience(0), DELTA);
        assertEquals(0.0, ExperienceCurve.requiredExperience(-5), DELTA);
    }

    @Test
    public void 設定値がカーブに反映される() {
        ExperienceCurve.configure(80, 1.7);

        assertEquals(80.0, ExperienceCurve.getBaseMultiplier(), DELTA);
        assertEquals(1.7, ExperienceCurve.getExponent(), DELTA);
        assertEquals(Math.pow(49, 1.7) * 80, ExperienceCurve.requiredExperience(50), DELTA);
    }

    @Test
    public void 不正な設定値は既定にフォールバックする() {
        ExperienceCurve.configure(0, -1);

        assertEquals(ExperienceCurve.DEFAULT_BASE_MULTIPLIER, ExperienceCurve.getBaseMultiplier(), DELTA);
        assertEquals(ExperienceCurve.DEFAULT_EXPONENT, ExperienceCurve.getExponent(), DELTA);
    }

    /**
     * レベルアップ判定は ExperienceManager 側の値で行い、実際の加算は PlayerJob#levelUp が行う。
     * 両者の式が食い違うと「上げられると判定されたのに上がらない」無限ループになるため、
     * 必ず一致していることを固定する。
     */
    @Test
    public void 判定側と加算側の必要経験値が一致する() {
        ExperienceCurve.configure(80, 1.7);
        ExperienceManager experienceManager = new ExperienceManager(null, null);

        for (int level = 1; level <= 100; level++) {
            assertEquals("レベル" + level + "の必要経験値が一致すること",
                PlayerJob.calculateExperienceRequired(level),
                experienceManager.calculateRequiredExperience(level),
                DELTA);
        }
    }

    @Test
    public void 設定変更後もレベルアップが成立する() {
        ExperienceCurve.configure(80, 1.7);

        PlayerJob playerJob = new PlayerJob(UUID.randomUUID(), 1);
        playerJob.setLevel(1);
        playerJob.setExperience(ExperienceCurve.requiredExperience(2));

        playerJob.levelUp();

        assertEquals("必要経験値ちょうどでレベルアップすること", 2, playerJob.getLevel());
    }
}
