package org.tofu.tofunomics.housing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 住居の場所（座標）の表示のテスト。
 */
public class HousingLocationFormatterTest {

    @Test
    public void 範囲の中心と床の高さを出す() {
        assertEquals("X: 105 / Y: 64 / Z: -25 付近",
                HousingLocationFormatter.format(100, 64, -30, 110, 70, -20));
    }

    @Test
    public void 角の順番が逆でも同じ() {
        assertEquals("X: 105 / Y: 64 / Z: -25 付近",
                HousingLocationFormatter.format(110, 70, -20, 100, 64, -30));
    }

    @Test
    public void 負の座標でも中心がずれない() {
        assertEquals("X: -8 / Y: 60 / Z: -8 付近",
                HousingLocationFormatter.format(-10, 60, -10, -5, 60, -5));
    }

    @Test
    public void 範囲が未登録なら未登録と出す() {
        assertEquals("未登録", HousingLocationFormatter.format(null, 64, 0, 10, 70, 10));
        assertEquals("未登録", HousingLocationFormatter.format(null));
    }
}
