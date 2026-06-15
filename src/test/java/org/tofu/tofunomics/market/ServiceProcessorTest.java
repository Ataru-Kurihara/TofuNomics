package org.tofu.tofunomics.market;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * ServiceProcessor.scaledRepairExpCost（損耗率ベースの修理経験値コスト・純粋ロジック）の単体テスト。
 */
public class ServiceProcessorTest {

    @Test
    public void testFullDamageCostsBase() {
        // 完全損耗（damage == maxDurability）→ baseCost
        assertEquals(5, ServiceProcessor.scaledRepairExpCost(1561, 1561, 5, 1));
    }

    @Test
    public void testHalfDamageCostsAboutHalf() {
        // 50%損耗 → ceil(5 * 0.5) = 3
        assertEquals(3, ServiceProcessor.scaledRepairExpCost(780, 1561, 5, 1));
    }

    @Test
    public void testSmallDamageHitsMinimum() {
        // わずかな損耗でも最低コストを下回らない
        assertEquals(1, ServiceProcessor.scaledRepairExpCost(1, 1561, 5, 1));
    }

    @Test
    public void testNoDamageReturnsMinimum() {
        // 無損耗（damage 0）→ 最低コスト
        assertEquals(1, ServiceProcessor.scaledRepairExpCost(0, 1561, 5, 1));
    }

    @Test
    public void testCostNeverExceedsBase() {
        // damage が maxDurability を超えても baseCost で頭打ち
        assertEquals(5, ServiceProcessor.scaledRepairExpCost(9999, 1561, 5, 1));
    }

    @Test
    public void testZeroMaxDurabilityReturnsMinimum() {
        // 耐久を持たないアイテム → 最低コスト
        assertEquals(1, ServiceProcessor.scaledRepairExpCost(0, 0, 5, 1));
    }

    @Test
    public void testQuarterDamageRoundsUp() {
        // 25%損耗 → ceil(5 * 0.25) = ceil(1.25) = 2
        assertEquals(2, ServiceProcessor.scaledRepairExpCost(390, 1561, 5, 1));
    }
}
