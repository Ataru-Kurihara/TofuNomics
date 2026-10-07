package org.tofu.tofunomics.npc.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 銀行 GUI の出金で使う枚数計算のテスト。
 *
 * 検証観点:
 * - 残高 1 未満では出金しない（0 枚のコインを作ろうとして例外→残高だけ減る、を防ぐ）
 * - 端数は切り捨てて残高に残す（10.5 から 10.5 引いて 10 枚しか渡さない、を防ぐ）
 * - 入らなかった枚数は出金に数えない（残高へ戻す分）
 */
public class BankGUIWithdrawTest {

    @Test
    public void 残高1未満は0枚() {
        assertEquals(0, BankGUI.wholeNuggets(0.5));
        assertEquals(0, BankGUI.wholeNuggets(0.0));
        assertEquals(0, BankGUI.wholeNuggets(-3.0));
        assertEquals(0, BankGUI.wholeNuggets(Double.NaN));
    }

    @Test
    public void 端数は切り捨てる() {
        assertEquals(10, BankGUI.wholeNuggets(10.5));
        assertEquals(10, BankGUI.wholeNuggets(10.99));
        assertEquals(500, BankGUI.wholeNuggets(500.0));
    }

    @Test
    public void 入らなかった分は出金に数えない() {
        assertEquals(100, BankGUI.withdrawnNuggets(100, 0));
        assertEquals(64, BankGUI.withdrawnNuggets(100, 36));
        assertEquals(0, BankGUI.withdrawnNuggets(100, 100));
    }
}
