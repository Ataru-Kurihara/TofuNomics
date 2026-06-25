package org.tofu.tofunomics.food;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * FoodBuffManager のバフ付与・倍率算出・職業マッチ判定のテスト
 */
public class FoodBuffManagerTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private JobManager jobManager;

    @Mock
    private Player player;

    private UUID uuid;

    private FoodBuffManager foodBuffManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        when(configManager.isFoodBuffEnabled()).thenReturn(true);
        when(configManager.getFoodBuffAppliedMessage()).thenReturn("applied:%category%");
        when(configManager.getFoodBuffNoMatchMessage()).thenReturn("nomatch:%category%:%job%");

        // 全職業共通 +25% / 300秒
        when(configManager.getFoodBuffBonusPercent()).thenReturn(25);
        when(configManager.getFoodBuffDurationSeconds()).thenReturn(300);

        // カテゴリ -> 対応職業
        when(configManager.getFoodBuffCategoryJobs("bread"))
            .thenReturn(Arrays.asList("woodcutter", "architect"));
        when(configManager.getFoodBuffCategoryJobs("meat"))
            .thenReturn(Arrays.asList("miner", "blacksmith"));

        // 職業表示名（no_match メッセージ用）
        when(configManager.getJobDisplayName("miner")).thenReturn("鉱夫");
        when(configManager.getJobDisplayName("woodcutter")).thenReturn("木こり");

        // デフォルトの現在職業は木こり（パンにマッチ）
        when(jobManager.getPlayerJob(uuid)).thenReturn("woodcutter");

        foodBuffManager = new FoodBuffManager(configManager, jobManager);
    }

    @Test
    public void noBuffReturnsBaseMultiplier() {
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }

    @Test
    public void matchingJobGetsBoost() {
        // 木こりがパン（bread=woodcutter/architect）を食べる -> ブースト適用
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.25, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }

    @Test
    public void nonMatchingJobGetsNoBoost() {
        // パンを食べても、鉱夫の経験値にはブーストが効かない
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "miner"), 0.0001);
    }

    @Test
    public void lastEatenCategoryWins() {
        // パン -> 肉 の順で食べると、最後の肉カテゴリのみ有効
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);
        assertEquals(1.25, foodBuffManager.getMultiplier(player, "miner"), 0.0001);      // 肉=鉱夫 -> 適用
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);  // 肉≠木こり -> なし
    }

    @Test
    public void noMatchSendsHintMessage() {
        // 現在職業が鉱夫のときにパン（鉱夫非対応）を食べると no_match ヒントが出る
        when(jobManager.getPlayerJob(uuid)).thenReturn("miner");
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        verify(player).sendMessage(contains("鉱夫"));
    }

    @Test
    public void matchSendsAppliedMessage() {
        // 現在職業が木こりのときにパンを食べると applied メッセージが出る
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        verify(player).sendMessage(contains("applied"));
    }

    @Test
    public void nullJobReturnsBaseMultiplier() {
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, null), 0.0001);
    }

    @Test
    public void disabledConfigDoesNotApplyBuff() {
        when(configManager.isFoodBuffEnabled()).thenReturn(false);
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "miner"), 0.0001);
    }

    @Test
    public void zeroBonusDoesNotApplyBuff() {
        when(configManager.getFoodBuffBonusPercent()).thenReturn(0);
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }

    @Test
    public void clearRemovesBuff() {
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        foodBuffManager.clear(uuid);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }

    @Test
    public void expiredBuffReturnsBaseMultiplier() {
        // 1秒設定で付与 -> 経過で失効することを確認する
        when(configManager.getFoodBuffDurationSeconds()).thenReturn(1);
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.25, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);

        // 1秒強待機して失効を確認（遅延失効）
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }

    @Test
    public void unmappedCategoryGivesNoBoost() {
        // jobs リストが空のカテゴリはどの職業にも効かない（防御的確認）
        when(configManager.getFoodBuffCategoryJobs("bread")).thenReturn(Collections.emptyList());
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.0, foodBuffManager.getMultiplier(player, "woodcutter"), 0.0001);
    }
}
