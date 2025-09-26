package org.tofu.tofunomics.models;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * フェーズ7 - PlayerJobモデルクラステスト
 * プレイヤー職業データの基本機能とレベルアップシステムをテスト
 */
public class PlayerJobTest {

    private PlayerJob playerJob;
    private UUID testUuid;
    private final int TEST_JOB_ID = 1;
    private final double DELTA = 0.001;

    @Before
    public void setUp() {
        testUuid = UUID.randomUUID();
        playerJob = new PlayerJob(testUuid, TEST_JOB_ID);
    }

    @Test
    public void testDefaultConstructor() {
        PlayerJob emptyPlayerJob = new PlayerJob();
        assertNull("デフォルトコンストラクタでuuidはnullであるべき", emptyPlayerJob.getUuid());
        assertEquals("デフォルトコンストラクタでjobIdは0であるべき", 0, emptyPlayerJob.getJobId());
        assertEquals("デフォルトコンストラクタでlevelは0であるべき", 0, emptyPlayerJob.getLevel());
        assertEquals("デフォルトコンストラクタでexperienceは0であるべき", 0.0, emptyPlayerJob.getExperience(), DELTA);
    }

    @Test
    public void testParameterizedConstructor() {
        assertEquals("コンストラクタでuuidが正しく設定されるべき", testUuid, playerJob.getUuid());
        assertEquals("コンストラクタでjobIdが正しく設定されるべき", TEST_JOB_ID, playerJob.getJobId());
        assertEquals("コンストラクタで初期レベルが1であるべき", 1, playerJob.getLevel());
        assertEquals("コンストラクタで初期経験値が0であるべき", 0.0, playerJob.getExperience(), DELTA);
        assertNotNull("コンストラクタでjoinedAtが設定されるべき", playerJob.getJoinedAt());
        assertNotNull("コンストラクタでupdatedAtが設定されるべき", playerJob.getUpdatedAt());
        assertTrue("コンストラクタで初期状態はアクティブであるべき", playerJob.isActive());
    }

    @Test
    public void testUuidSetters() {
        UUID newUuid = UUID.randomUUID();
        
        playerJob.setUuid(newUuid);
        assertEquals("setUuid(UUID)で正しく設定されるべき", newUuid, playerJob.getUuid());

        String uuidString = UUID.randomUUID().toString();
        playerJob.setUuid(uuidString);
        assertEquals("setUuid(String)で正しく設定されるべき", uuidString, playerJob.getUuid().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetUuidWithInvalidString() {
        playerJob.setUuid("invalid-uuid-string");
    }

    @Test
    public void testJobIdSetter() {
        int newJobId = 5;
        playerJob.setJobId(newJobId);
        assertEquals("setJobIdで正しく設定されるべき", newJobId, playerJob.getJobId());
    }

    @Test
    public void testLevelSetter() {
        int newLevel = 10;
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJob.setLevel(newLevel);
        assertEquals("setLevelで正しく設定されるべき", newLevel, playerJob.getLevel());
        assertTrue("setLevelでupdatedAtが更新されるべき", 
                   playerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testExperienceSetter() {
        double newExperience = 500.0;
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJob.setExperience(newExperience);
        assertEquals("setExperienceで正しく設定されるべき", newExperience, playerJob.getExperience(), DELTA);
        assertTrue("setExperienceでupdatedAtが更新されるべき", 
                   playerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testAddExperience() {
        double addAmount = 250.0;
        double expectedExperience = 0.0 + addAmount;
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJob.addExperience(addAmount);
        assertEquals("addExperienceで正しく加算されるべき", expectedExperience, playerJob.getExperience(), DELTA);
        assertTrue("addExperienceでupdatedAtが更新されるべき", 
                   playerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testCalculateExperienceRequired() {
        // レベル1は0経験値であるべき
        assertEquals("レベル1の必要経験値は0であるべき", 0.0, 
                     PlayerJob.calculateExperienceRequired(1), DELTA);
        
        // レベル2の必要経験値をテスト
        double level2Required = Math.pow(1, 2.2) * 100;
        assertEquals("レベル2の必要経験値が正しい計算式であるべき", level2Required, 
                     PlayerJob.calculateExperienceRequired(2), DELTA);
        
        // レベル3の必要経験値をテスト
        double level3Required = Math.pow(2, 2.2) * 100;
        assertEquals("レベル3の必要経験値が正しい計算式であるべき", level3Required, 
                     PlayerJob.calculateExperienceRequired(3), DELTA);
        
        // レベル0以下は0を返すべき
        assertEquals("レベル0の必要経験値は0であるべき", 0.0, 
                     PlayerJob.calculateExperienceRequired(0), DELTA);
        assertEquals("負のレベルの必要経験値は0であるべき", 0.0, 
                     PlayerJob.calculateExperienceRequired(-1), DELTA);
    }

    @Test
    public void testGetExperienceForNextLevel() {
        // レベル1で経験値50の場合
        playerJob.setExperience(50.0);
        double level2Required = PlayerJob.calculateExperienceRequired(2);
        double expectedRemaining = level2Required - 50.0;
        
        assertEquals("次レベルまでの必要経験値が正しく計算されるべき", expectedRemaining, 
                     playerJob.getExperienceForNextLevel(), DELTA);
    }

    @Test
    public void testCanLevelUp() {
        int maxLevel = 100;
        
        // 十分な経験値がない場合
        playerJob.setExperience(50.0);
        assertFalse("経験値不足の場合はcanLevelUpがfalseを返すべき", 
                    playerJob.canLevelUp(maxLevel));
        
        // 十分な経験値がある場合
        double level2Required = PlayerJob.calculateExperienceRequired(2);
        playerJob.setExperience(level2Required);
        assertTrue("十分な経験値がある場合はcanLevelUpがtrueを返すべき", 
                   playerJob.canLevelUp(maxLevel));
        
        // 最大レベルに達している場合
        playerJob.setLevel(maxLevel);
        assertFalse("最大レベルに達している場合はcanLevelUpがfalseを返すべき", 
                    playerJob.canLevelUp(maxLevel));
    }

    @Test
    public void testLevelUp() {
        // レベルアップできない状態でのテスト
        playerJob.setExperience(50.0);
        int originalLevel = playerJob.getLevel();
        playerJob.levelUp();
        assertEquals("経験値不足の場合はレベルアップしないべき", originalLevel, playerJob.getLevel());
        
        // レベルアップできる状態でのテスト
        double level2Required = PlayerJob.calculateExperienceRequired(2);
        playerJob.setExperience(level2Required);
        Timestamp beforeUpdate = playerJob.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        playerJob.levelUp();
        assertEquals("十分な経験値がある場合はレベルアップするべき", 2, playerJob.getLevel());
        assertTrue("levelUpでupdatedAtが更新されるべき", 
                   playerJob.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testMultipleLevelUps() {
        // レベル3まで上がれる経験値を設定
        double level3Required = PlayerJob.calculateExperienceRequired(3);
        playerJob.setExperience(level3Required);
        
        // 1回目のレベルアップ（レベル1→2）
        playerJob.levelUp();
        assertEquals("1回目のレベルアップ後はレベル2であるべき", 2, playerJob.getLevel());
        
        // 2回目のレベルアップ（レベル2→3）
        playerJob.levelUp();
        assertEquals("2回目のレベルアップ後はレベル3であるべき", 3, playerJob.getLevel());
        
        // 3回目のレベルアップは起こらないべき（レベル4の経験値が不足）
        playerJob.levelUp();
        assertEquals("経験値不足でレベル4にはならないべき", 3, playerJob.getLevel());
    }

    @Test
    public void testIsActive() {
        // レベル1でアクティブ
        assertTrue("レベル1でアクティブであるべき", playerJob.isActive());
        
        // レベル0で非アクティブ
        playerJob.setLevel(0);
        assertFalse("レベル0で非アクティブであるべき", playerJob.isActive());
        
        // レベル10でアクティブ
        playerJob.setLevel(10);
        assertTrue("レベル10でアクティブであるべき", playerJob.isActive());
    }

    @Test
    public void testTimestampSetters() {
        Timestamp newJoinedAt = new Timestamp(System.currentTimeMillis() - 10000);
        Timestamp newUpdatedAt = new Timestamp(System.currentTimeMillis());
        
        playerJob.setJoinedAt(newJoinedAt);
        playerJob.setUpdatedAt(newUpdatedAt);
        
        assertEquals("setJoinedAtで正しく設定されるべき", newJoinedAt, playerJob.getJoinedAt());
        assertEquals("setUpdatedAtで正しく設定されるべき", newUpdatedAt, playerJob.getUpdatedAt());
    }

    @Test
    public void testExperienceFormula() {
        // 各レベルでの必要経験値の増加を確認
        double level1Exp = PlayerJob.calculateExperienceRequired(1);
        double level2Exp = PlayerJob.calculateExperienceRequired(2);
        double level3Exp = PlayerJob.calculateExperienceRequired(3);
        double level10Exp = PlayerJob.calculateExperienceRequired(10);
        
        assertEquals("レベル1の必要経験値", 0.0, level1Exp, DELTA);
        assertTrue("レベル2の必要経験値がレベル1より多い", level2Exp > level1Exp);
        assertTrue("レベル3の必要経験値がレベル2より多い", level3Exp > level2Exp);
        assertTrue("レベル10の必要経験値がレベル3より多い", level10Exp > level3Exp);
    }
}