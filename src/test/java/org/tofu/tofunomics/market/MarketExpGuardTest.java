package org.tofu.tofunomics.market;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * マーケットの取引で入る職業経験値の稼ぎすぎ防止、エンチャント依頼の範囲、件数の上限のテスト。
 */
public class MarketExpGuardTest {

    private static final long HOUR = 60 * 60_000L;

    // ===== 価格の下限 =====

    @Test
    public void 価格1の取引は基準価格のある品では経験値の対象外() {
        // 基準価格 10 の品 64 個を合計 1 で取引
        assertFalse(MarketExpGuard.meetsPriceFloor(1, 64, 10.0, 0.5));
    }

    @Test
    public void 基準価格の半額以上なら対象() {
        assertTrue(MarketExpGuard.meetsPriceFloor(320, 64, 10.0, 0.5));   // ちょうど半額
        assertTrue(MarketExpGuard.meetsPriceFloor(640, 64, 10.0, 0.5));   // 基準どおり
        assertFalse(MarketExpGuard.meetsPriceFloor(319, 64, 10.0, 0.5));  // わずかに届かない
    }

    @Test
    public void 基準価格が無い品や下限なしの設定では制限しない() {
        assertTrue(MarketExpGuard.meetsPriceFloor(1, 64, 0.0, 0.5));
        assertTrue(MarketExpGuard.meetsPriceFloor(1, 64, 10.0, 0.0));
        assertFalse(MarketExpGuard.meetsPriceFloor(100, 0, 10.0, 0.5));
    }

    // ===== 相手ごとの間隔 =====

    @Test
    public void 同じ2人の間では向きを変えても間隔のあいだは1回だけ() {
        MarketExpGuard guard = new MarketExpGuard();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(guard.tryAcquire(a, b, 0L, HOUR));
        // A が B に売った直後に、B が A に売り返す
        assertFalse(guard.tryAcquire(b, a, 1000L, HOUR));
        assertFalse(guard.tryAcquire(a, b, HOUR - 1, HOUR));
        // 間隔が明ければまた入る
        assertTrue(guard.tryAcquire(a, b, HOUR, HOUR));
    }

    @Test
    public void 別の相手との取引は妨げない() {
        MarketExpGuard guard = new MarketExpGuard();
        UUID seller = UUID.randomUUID();

        assertTrue(guard.tryAcquire(seller, UUID.randomUUID(), 0L, HOUR));
        assertTrue(guard.tryAcquire(seller, UUID.randomUUID(), 1L, HOUR));
        assertTrue(guard.tryAcquire(seller, UUID.randomUUID(), 2L, HOUR));
    }

    @Test
    public void 間隔0は制限なし_相手が不明なら出さない() {
        MarketExpGuard guard = new MarketExpGuard();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(guard.tryAcquire(a, b, 0L, 0L));
        assertTrue(guard.tryAcquire(a, b, 1L, 0L));
        assertFalse(guard.tryAcquire(a, null, 0L, HOUR));
        assertEquals(MarketExpGuard.pairKey(a, b), MarketExpGuard.pairKey(b, a));
    }

    // ===== エンチャント依頼の範囲 =====

    @Test
    public void エンチャント依頼はバニラで付けられる範囲だけ() {
        // 幸運 III（最大 3）をツルハシに: 可
        assertTrue(ServiceProcessor.isWithinVanillaLimits(true, 3, 3, false));
        // 幸運 V: 最大レベル超え
        assertFalse(ServiceProcessor.isWithinVanillaLimits(true, 5, 3, false));
        // シルクタッチ付きのツルハシに幸運: 両立しない
        assertFalse(ServiceProcessor.isWithinVanillaLimits(true, 1, 3, true));
        // 剣に幸運: 付けられない種類
        assertFalse(ServiceProcessor.isWithinVanillaLimits(false, 1, 3, false));
        assertFalse(ServiceProcessor.isWithinVanillaLimits(true, 0, 3, false));
    }

    // ===== 件数の上限 =====

    @Test
    public void 件数の上限_達していれば断る_0以下は制限なし() {
        assertFalse(MarketManager.isAtLimit(9, 10));
        assertTrue(MarketManager.isAtLimit(10, 10));
        assertTrue(MarketManager.isAtLimit(11, 10));
        assertFalse(MarketManager.isAtLimit(100, 0));
        assertFalse(MarketManager.isAtLimit(100, -1));
    }
}
