package org.tofu.tofunomics.npc.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GUI の保護に使う判定のテスト。
 *
 * 検証観点:
 * - close の一致判定: 別の GUI が閉じられてもセッションを消さない
 * - 上段判定: 手持ち側のクリック・ドラッグを GUI のボタン操作と見なさない
 * - 売却マスの選択: 通貨などの売れない品を飛ばし、上限個数を守る
 * - 空き計算: 印の違う同種の品には重ならないものとして数える
 */
public class GuiSafetyTest {

    private ItemStack stack(Material type, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    // ===== close の一致判定 =====

    @Test
    public void close_セッションのGUIが閉じられたときだけ一致する() {
        Inventory first = mock(Inventory.class);
        Inventory second = mock(Inventory.class);

        assertTrue(GuiSafety.isSessionInventory(second, second));
        // 2 枚目を開く瞬間に届く「1 枚目の close」では、2 枚目のセッションを消さない
        assertFalse(GuiSafety.isSessionInventory(second, first));
        assertFalse(GuiSafety.isSessionInventory(null, first));
    }

    // ===== 上段判定 =====

    @Test
    public void 上段判定_GUI側のマスだけを上段とみなす() {
        assertTrue(GuiSafety.isTopSlot(0, 27));
        assertTrue(GuiSafety.isTopSlot(26, 27));
        // 27 以降は手持ち側。getSlot() だと 9〜35 に見えてボタンと重なる
        assertFalse(GuiSafety.isTopSlot(27, 27));
        assertFalse(GuiSafety.isTopSlot(40, 27));
        // 画面の外（-999）
        assertFalse(GuiSafety.isTopSlot(-999, 27));
    }

    @Test
    public void ドラッグ_上段に1マスでもかかれば検出する() {
        assertTrue(GuiSafety.dragTouchesTop(new HashSet<>(Arrays.asList(30, 31, 5)), 27));
        assertFalse(GuiSafety.dragTouchesTop(new HashSet<>(Arrays.asList(30, 31, 32)), 27));
        assertFalse(GuiSafety.dragTouchesTop(Collections.emptySet(), 27));
        assertFalse(GuiSafety.dragTouchesTop(null, 27));
    }

    // ===== 売却マスの選択 =====

    @Test
    public void 売却_通貨のマスを飛ばして同じ種類の普通の品だけを選ぶ() {
        ItemStack tofuGold = stack(Material.GOLD_INGOT, 5);   // 通貨（種類は金インゴット）
        ItemStack plainGold = stack(Material.GOLD_INGOT, 7);
        ItemStack[] storage = new ItemStack[36];
        storage[0] = tofuGold;
        storage[3] = plainGold;

        Map<Integer, Integer> selected = GuiSafety.selectSellSlots(
            storage, item -> item != tofuGold, Integer.MAX_VALUE);

        assertEquals(1, selected.size());
        assertEquals(Integer.valueOf(7), selected.get(3));
        assertFalse("通貨のマスは売却対象にしない", selected.containsKey(0));
    }

    @Test
    public void 売却_上限個数までしか選ばない() {
        ItemStack[] storage = new ItemStack[36];
        storage[1] = stack(Material.COBBLESTONE, 64);
        storage[2] = stack(Material.COBBLESTONE, 64);

        Map<Integer, Integer> selected = GuiSafety.selectSellSlots(storage, item -> true, 70);

        assertEquals(Integer.valueOf(64), selected.get(1));
        assertEquals(Integer.valueOf(6), selected.get(2));
    }

    @Test
    public void 売却_売れる品が無ければ空() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = stack(Material.DIRT, 10);

        assertTrue(GuiSafety.selectSellSlots(storage, item -> false, 10).isEmpty());
        assertTrue(GuiSafety.selectSellSlots(null, item -> true, 10).isEmpty());
    }

    // ===== 空き計算 =====

    @Test
    public void 空き_印の違う同種の品には重ならないので数えない() {
        ItemStack marked = stack(Material.BREAD, 1);          // これから渡す品（印付き）
        ItemStack unmarkedBread = stack(Material.BREAD, 10);  // 手持ちの印無しパン
        when(unmarkedBread.isSimilar(marked)).thenReturn(false);

        ItemStack[] storage = new ItemStack[36];
        Arrays.fill(storage, unmarkedBread);

        // 収納がすべて「印無しのパン 10 個」で埋まっている → 印付きのパンは 1 個も入らない
        assertEquals(0, GuiSafety.freeCapacityFor(storage, marked, 64));
    }

    @Test
    public void 空き_空きマスと同じ品の途中スタックを合計する() {
        ItemStack marked = stack(Material.BREAD, 1);
        ItemStack sameMarked = stack(Material.BREAD, 60);
        when(sameMarked.isSimilar(marked)).thenReturn(true);
        ItemStack other = stack(Material.DIRT, 1);

        ItemStack[] storage = new ItemStack[36];
        Arrays.fill(storage, other);
        storage[0] = null;        // 空き 1 マス = 64
        storage[1] = sameMarked;  // 残り 4

        assertEquals(68, GuiSafety.freeCapacityFor(storage, marked, 64));
    }
}
