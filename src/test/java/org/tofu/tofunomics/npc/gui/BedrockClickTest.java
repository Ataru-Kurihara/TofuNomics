package org.tofu.tofunomics.npc.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 統合版（左クリックしか送れないことがある）でも、全額・まとめ買いに届くことのテスト。
 *
 * 検証観点:
 * - 数量ボタンの段階を選ぶと、左クリックが右・シフトクリックと同じ扱いになる
 * - Java 版の右・シフトクリックは段階に関係なく今までどおり
 * - 段階は決めた数で一周する
 * - 銀行のクリックの種類ごとの金額は今までどおり
 */
public class BedrockClickTest {

    @Test
    public void 段階0の左クリックは左クリックのまま() {
        assertEquals(ClickType.LEFT, ClickTier.apply(ClickType.LEFT, 0));
    }

    @Test
    public void 段階を選ぶと左クリックが右やシフトになる() {
        assertEquals(ClickType.RIGHT, ClickTier.apply(ClickType.LEFT, 1));
        assertEquals(ClickType.SHIFT_LEFT, ClickTier.apply(ClickType.LEFT, 2));
        assertEquals(ClickType.SHIFT_RIGHT, ClickTier.apply(ClickType.LEFT, 3));
    }

    @Test
    public void 左クリック以外は段階に関係なくそのまま() {
        assertEquals(ClickType.RIGHT, ClickTier.apply(ClickType.RIGHT, 2));
        assertEquals(ClickType.SHIFT_LEFT, ClickTier.apply(ClickType.SHIFT_LEFT, 1));
        assertEquals(ClickType.MIDDLE, ClickTier.apply(ClickType.MIDDLE, 3));
    }

    @Test
    public void 範囲外の段階は左クリックのまま() {
        assertEquals(ClickType.LEFT, ClickTier.apply(ClickType.LEFT, -1));
        assertEquals(ClickType.LEFT, ClickTier.apply(ClickType.LEFT, 9));
    }

    @Test
    public void 段階は決めた数で一周する() {
        assertEquals(1, ClickTier.next(0, 3));
        assertEquals(2, ClickTier.next(1, 3));
        assertEquals(0, ClickTier.next(2, 3));
        assertEquals(3, ClickTier.next(2, 4));
        assertEquals(0, ClickTier.next(3, 4));
        assertEquals(0, ClickTier.next(0, 1));
    }

    @Test
    public void 取引所と食料店の段階の数() {
        assertEquals(3, TradingGUI.QUANTITY_TIERS);
        assertEquals(4, FoodGUI.QUANTITY_TIERS);
        assertEquals("64個", TradingGUI.quantityLabel(TradingGUI.TradingMode.BUY, 2));
        assertEquals("持っている分すべて", TradingGUI.quantityLabel(TradingGUI.TradingMode.SELL, 2));
        assertEquals("上限まで", FoodGUI.quantityLabel(3));
    }

    @Test
    public void 銀行の出金額はクリックの種類どおり() {
        assertEquals(10.0, BankGUI.withdrawRequestFor(ClickType.LEFT), 0.0);
        assertEquals(100.0, BankGUI.withdrawRequestFor(ClickType.RIGHT), 0.0);
        assertEquals(500.0, BankGUI.withdrawRequestFor(ClickType.SHIFT_LEFT), 0.0);
        assertEquals(BankGUI.WITHDRAW_ALL, BankGUI.withdrawRequestFor(ClickType.SHIFT_RIGHT), 0.0);
        assertNull(BankGUI.withdrawRequestFor(ClickType.MIDDLE));
    }

    @Test
    public void 銀行の入金枚数はクリックの種類どおり() {
        assertEquals(1, BankGUI.depositRequestFor(ClickType.LEFT));
        assertEquals(9, BankGUI.depositRequestFor(ClickType.RIGHT));
        assertEquals(Integer.MAX_VALUE, BankGUI.depositRequestFor(ClickType.SHIFT_LEFT));
        assertEquals(Integer.MAX_VALUE, BankGUI.depositRequestFor(ClickType.SHIFT_RIGHT));
        assertEquals(0, BankGUI.depositRequestFor(ClickType.DROP));
    }

    @Test
    public void 銀行の全額ボタンは既存のボタンや閉じるボタンと重ならない() {
        int[] used = {4, 11, 13, 15, 19, 21, 26};
        int[] added = {BankGUI.WITHDRAW_ALL_SLOT, BankGUI.DEPOSIT_ALL_SLOT,
            BankGUI.WITHDRAW_100_SLOT, BankGUI.WITHDRAW_500_SLOT};
        for (int a : added) {
            for (int u : used) {
                org.junit.Assert.assertNotEquals(u, a);
            }
        }
    }
}
