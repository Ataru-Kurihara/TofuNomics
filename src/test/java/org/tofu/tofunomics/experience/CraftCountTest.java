package org.tofu.tofunomics.experience;

import org.junit.Test;
import org.tofu.tofunomics.experience.CraftCount.Click;

import static org.junit.Assert.assertEquals;

/**
 * クラフト 1 クリックで実際に作られる回数の数え方を確かめる。
 */
public class CraftCountTest {

    // 剣・防具（1 回で 1 個、最大スタック 1）の前提
    private static int sword(Click click, boolean cursorEmpty, int minIngredient, int room, int hotbarRoom) {
        return CraftCount.crafts(click, 1, 1, cursorEmpty, false, cursorEmpty ? 0 : 1, minIngredient, room, hotbarRoom);
    }

    @Test
    public void 普通のクリックでカーソルが空なら1回() {
        assertEquals(1, sword(Click.CURSOR, true, 5, 0, 0));
    }

    @Test
    public void 普通のクリックでカーソルに物があると作られない() {
        // 剣はスタックしないので、カーソルに剣を持ったまま押しても何も出来ない
        assertEquals(0, sword(Click.CURSOR, false, 5, 0, 0));
    }

    @Test
    public void 重なる物ならカーソルに載る分だけ作られる() {
        // 1 回 4 個・最大 64。カーソル 60 個 → 64 で載る
        assertEquals(1, CraftCount.crafts(Click.CURSOR, 4, 64, false, true, 60, 3, 0, 0));
        // カーソル 61 個 → 65 で載らない
        assertEquals(0, CraftCount.crafts(Click.CURSOR, 4, 64, false, true, 61, 3, 0, 0));
    }

    @Test
    public void シフトクリックは材料とインベントリの空きの少ないほう() {
        // 材料 5 回分・空き 3 マス → 3 回
        assertEquals(3, sword(Click.SHIFT, true, 5, 3, 0));
        // 材料 2 回分・空き 10 マス → 2 回
        assertEquals(2, sword(Click.SHIFT, true, 2, 10, 0));
    }

    @Test
    public void シフトクリックでインベントリが満杯なら作られない() {
        assertEquals(0, sword(Click.SHIFT, true, 5, 0, 0));
    }

    @Test
    public void 数字キーは行き先のマスが空いていれば1回() {
        assertEquals(1, sword(Click.NUMBER_KEY, true, 5, 0, 1));
        assertEquals(0, sword(Click.NUMBER_KEY, true, 5, 0, 0));
    }

    @Test
    public void Qは1回_CtrlQは材料が尽きるまで() {
        assertEquals(1, sword(Click.DROP, false, 5, 0, 0));
        assertEquals(5, sword(Click.CONTROL_DROP, false, 5, 0, 0));
    }

    @Test
    public void 材料が無い_その他のクリックは0() {
        assertEquals(0, sword(Click.CURSOR, true, 0, 0, 0));
        assertEquals(0, sword(Click.OTHER, true, 5, 10, 1));
    }
}
