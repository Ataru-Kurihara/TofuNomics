package org.tofu.tofunomics.models;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.*;

/**
 * フェーズ7 - Jobモデルクラステスト
 * 職業データの基本機能とバリデーションをテスト
 */
public class JobTest {

    private Job job;
    private final String TEST_NAME = "farmer";
    private final String TEST_DISPLAY_NAME = "農家";
    private final int TEST_MAX_LEVEL = 100;
    private final double TEST_BASE_INCOME = 10.0;
    private final double DELTA = 0.001;

    @Before
    public void setUp() {
        job = new Job(TEST_NAME, TEST_DISPLAY_NAME, TEST_MAX_LEVEL, TEST_BASE_INCOME);
    }

    @Test
    public void testDefaultConstructor() {
        Job emptyJob = new Job();
        assertEquals("デフォルトコンストラクタでidは0であるべき", 0, emptyJob.getId());
        assertNull("デフォルトコンストラクタでnameはnullであるべき", emptyJob.getName());
        assertNull("デフォルトコンストラクタでdisplayNameはnullであるべき", emptyJob.getDisplayName());
        assertEquals("デフォルトコンストラクタでmaxLevelは0であるべき", 0, emptyJob.getMaxLevel());
        assertEquals("デフォルトコンストラクタでbaseIncomeは0であるべき", 0.0, emptyJob.getBaseIncome(), DELTA);
        assertNull("デフォルトコンストラクタでcreatedAtはnullであるべき", emptyJob.getCreatedAt());
    }

    @Test
    public void testParameterizedConstructor() {
        assertEquals("コンストラクタでnameが正しく設定されるべき", TEST_NAME, job.getName());
        assertEquals("コンストラクタでdisplayNameが正しく設定されるべき", TEST_DISPLAY_NAME, job.getDisplayName());
        assertEquals("コンストラクタでmaxLevelが正しく設定されるべき", TEST_MAX_LEVEL, job.getMaxLevel());
        assertEquals("コンストラクタでbaseIncomeが正しく設定されるべき", TEST_BASE_INCOME, job.getBaseIncome(), DELTA);
        assertNotNull("コンストラクタでcreatedAtが設定されるべき", job.getCreatedAt());
        assertEquals("デフォルトのidは0であるべき", 0, job.getId());
    }

    @Test
    public void testIdSetterAndGetter() {
        int testId = 5;
        job.setId(testId);
        assertEquals("setIdで正しく設定されるべき", testId, job.getId());
    }

    @Test
    public void testNameSetterAndGetter() {
        String newName = "miner";
        job.setName(newName);
        assertEquals("setNameで正しく設定されるべき", newName, job.getName());
        
        // null設定のテスト
        job.setName(null);
        assertNull("null設定が正しく動作するべき", job.getName());
        
        // 空文字列設定のテスト
        job.setName("");
        assertEquals("空文字列設定が正しく動作するべき", "", job.getName());
    }

    @Test
    public void testDisplayNameSetterAndGetter() {
        String newDisplayName = "鉱夫";
        job.setDisplayName(newDisplayName);
        assertEquals("setDisplayNameで正しく設定されるべき", newDisplayName, job.getDisplayName());
        
        // null設定のテスト
        job.setDisplayName(null);
        assertNull("null設定が正しく動作するべき", job.getDisplayName());
        
        // 空文字列設定のテスト
        job.setDisplayName("");
        assertEquals("空文字列設定が正しく動作するべき", "", job.getDisplayName());
    }

    @Test
    public void testMaxLevelSetterAndGetter() {
        int newMaxLevel = 50;
        job.setMaxLevel(newMaxLevel);
        assertEquals("setMaxLevelで正しく設定されるべき", newMaxLevel, job.getMaxLevel());
        
        // 0設定のテスト
        job.setMaxLevel(0);
        assertEquals("0設定が正しく動作するべき", 0, job.getMaxLevel());
        
        // 負の値設定のテスト
        job.setMaxLevel(-1);
        assertEquals("負の値設定が正しく動作するべき", -1, job.getMaxLevel());
    }

    @Test
    public void testBaseIncomeSetterAndGetter() {
        double newBaseIncome = 25.5;
        job.setBaseIncome(newBaseIncome);
        assertEquals("setBaseIncomeで正しく設定されるべき", newBaseIncome, job.getBaseIncome(), DELTA);
        
        // 0設定のテスト
        job.setBaseIncome(0.0);
        assertEquals("0設定が正しく動作するべき", 0.0, job.getBaseIncome(), DELTA);
        
        // 負の値設定のテスト（現実的ではないが、システム的には許可）
        job.setBaseIncome(-5.0);
        assertEquals("負の値設定が正しく動作するべき", -5.0, job.getBaseIncome(), DELTA);
    }

    @Test
    public void testCreatedAtSetterAndGetter() {
        Timestamp newCreatedAt = new Timestamp(System.currentTimeMillis() - 10000);
        job.setCreatedAt(newCreatedAt);
        assertEquals("setCreatedAtで正しく設定されるべき", newCreatedAt, job.getCreatedAt());
        
        // null設定のテスト
        job.setCreatedAt(null);
        assertNull("null設定が正しく動作するべき", job.getCreatedAt());
    }

    @Test
    public void testJobWithDifferentParameters() {
        // 異なるパラメータでの職業作成テスト
        String builderName = "builder";
        String builderDisplayName = "建築家";
        int builderMaxLevel = 80;
        double builderBaseIncome = 15.0;
        
        Job builder = new Job(builderName, builderDisplayName, builderMaxLevel, builderBaseIncome);
        
        assertEquals("builder職業のname", builderName, builder.getName());
        assertEquals("builder職業のdisplayName", builderDisplayName, builder.getDisplayName());
        assertEquals("builder職業のmaxLevel", builderMaxLevel, builder.getMaxLevel());
        assertEquals("builder職業のbaseIncome", builderBaseIncome, builder.getBaseIncome(), DELTA);
        assertNotNull("builder職業のcreatedAt", builder.getCreatedAt());
    }

    @Test
    public void testJobWithZeroValues() {
        // 0値での職業作成テスト
        Job zeroJob = new Job("zero", "ゼロ職", 0, 0.0);
        
        assertEquals("ゼロ職業のmaxLevel", 0, zeroJob.getMaxLevel());
        assertEquals("ゼロ職業のbaseIncome", 0.0, zeroJob.getBaseIncome(), DELTA);
    }

    @Test
    public void testJobWithNullValues() {
        // null値での職業作成テスト
        Job nullJob = new Job(null, null, 50, 10.0);
        
        assertNull("null職業のname", nullJob.getName());
        assertNull("null職業のdisplayName", nullJob.getDisplayName());
        assertEquals("null職業のmaxLevel", 50, nullJob.getMaxLevel());
        assertEquals("null職業のbaseIncome", 10.0, nullJob.getBaseIncome(), DELTA);
        assertNotNull("null職業のcreatedAt", nullJob.getCreatedAt());
    }

    @Test
    public void testJobWithExtremeValues() {
        // 極端な値での職業作成テスト
        int extremeMaxLevel = Integer.MAX_VALUE;
        double extremeBaseIncome = Double.MAX_VALUE;
        
        Job extremeJob = new Job("extreme", "極端職", extremeMaxLevel, extremeBaseIncome);
        
        assertEquals("極端職業のmaxLevel", extremeMaxLevel, extremeJob.getMaxLevel());
        assertEquals("極端職業のbaseIncome", extremeBaseIncome, extremeJob.getBaseIncome(), DELTA);
    }

    @Test
    public void testTimestampCreation() {
        // 作成時刻のテスト
        long beforeCreation = System.currentTimeMillis();
        Job timestampJob = new Job("timestamp", "時刻職", 100, 10.0);
        long afterCreation = System.currentTimeMillis();
        
        assertNotNull("作成時刻が設定されるべき", timestampJob.getCreatedAt());
        long createdTime = timestampJob.getCreatedAt().getTime();
        assertTrue("作成時刻が範囲内にあるべき", 
                   createdTime >= beforeCreation && createdTime <= afterCreation);
    }

    @Test
    public void testJobDataIntegrity() {
        // データ整合性テスト：設定した値が変更されずに保持されるか
        String originalName = job.getName();
        String originalDisplayName = job.getDisplayName();
        int originalMaxLevel = job.getMaxLevel();
        double originalBaseIncome = job.getBaseIncome();
        Timestamp originalCreatedAt = job.getCreatedAt();
        
        // 時間を置いてデータが変更されていないことを確認
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertEquals("nameが保持されるべき", originalName, job.getName());
        assertEquals("displayNameが保持されるべき", originalDisplayName, job.getDisplayName());
        assertEquals("maxLevelが保持されるべき", originalMaxLevel, job.getMaxLevel());
        assertEquals("baseIncomeが保持されるべき", originalBaseIncome, job.getBaseIncome(), DELTA);
        assertEquals("createdAtが保持されるべき", originalCreatedAt, job.getCreatedAt());
    }

    @Test
    public void testPrecisionOfBaseIncome() {
        // baseIncomeの精度テスト
        double preciseIncome = 123.456789;
        job.setBaseIncome(preciseIncome);
        assertEquals("baseIncomeの精度が保持されるべき", preciseIncome, job.getBaseIncome(), DELTA);
    }
}