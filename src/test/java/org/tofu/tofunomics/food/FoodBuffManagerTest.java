package org.tofu.tofunomics.food;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * FoodBuffManager のバフ付与・倍率算出・上書きルールのテスト
 */
public class FoodBuffManagerTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private Player player;

    private FoodBuffManager foodBuffManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(configManager.isFoodBuffEnabled()).thenReturn(true);
        when(configManager.getFoodBuffAppliedMessage()).thenReturn("applied");
        when(configManager.getFoodBuffReplacedMessage()).thenReturn("replaced");
        when(configManager.getFoodBuffWeakerIgnoredMessage()).thenReturn("ignored:%current%");

        // パン +10% / 180秒, 肉 +30% / 480秒
        when(configManager.getFoodBuffBonusPercent("bread")).thenReturn(10);
        when(configManager.getFoodBuffDurationSeconds("bread")).thenReturn(180);
        when(configManager.getFoodBuffBonusPercent("meat")).thenReturn(30);
        when(configManager.getFoodBuffDurationSeconds("meat")).thenReturn(480);

        foodBuffManager = new FoodBuffManager(configManager);
    }

    @Test
    public void noBuffReturnsBaseMultiplier() {
        assertEquals(1.0, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void eatingBreadGivesTenPercentBuff() {
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.10, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void strongerBuffOverwritesWeaker() {
        foodBuffManager.applyBuff(player, FoodCategory.BREAD); // +10%
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);  // +30%（上書き）
        assertEquals(1.30, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void weakerBuffDoesNotReplaceStronger() {
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);  // +30%
        foodBuffManager.applyBuff(player, FoodCategory.BREAD); // +10%（無視されるべき）
        assertEquals(1.30, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void weakerBuffSendsIgnoredMessage() {
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);  // +30%（現在有効: 肉）
        foodBuffManager.applyBuff(player, FoodCategory.BREAD); // +10%（無視され通知される）
        // 「食べたのに無反応」防止のため、現在有効なバフ名を含むメッセージが送られる
        verify(player).sendMessage(contains("肉"));
    }

    @Test
    public void disabledConfigDoesNotApplyBuff() {
        when(configManager.isFoodBuffEnabled()).thenReturn(false);
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);
        assertEquals(1.0, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void zeroBonusDoesNotApplyBuff() {
        when(configManager.getFoodBuffBonusPercent("bread")).thenReturn(0);
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.0, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void clearRemovesBuff() {
        foodBuffManager.applyBuff(player, FoodCategory.MEAT);
        foodBuffManager.clear(player.getUniqueId());
        assertEquals(1.0, foodBuffManager.getMultiplier(player), 0.0001);
    }

    @Test
    public void expiredBuffReturnsBaseMultiplier() {
        // 持続0秒（=即失効扱い）の設定では applyBuff が付与しないが、
        // ここでは1秒設定で付与→経過で失効することを確認する
        when(configManager.getFoodBuffDurationSeconds("bread")).thenReturn(1);
        foodBuffManager.applyBuff(player, FoodCategory.BREAD);
        assertEquals(1.10, foodBuffManager.getMultiplier(player), 0.0001);

        // 1秒強待機して失効を確認（遅延失効）
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(1.0, foodBuffManager.getMultiplier(player), 0.0001);
    }
}
