package org.tofu.tofunomics.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * /withdraw all で引き出す額のテスト。
 *
 * 検証観点:
 * - 端数は残高に残す（コインは整数枚）
 * - 1 回の上限を超える分は残す
 * - 残高 1 未満は 0（引き出さない）
 */
public class WithdrawAllAmountTest {

    @Test
    public void 端数は切り捨てる() {
        assertEquals(123.0, WithdrawCommand.allWithdrawAmount(123.7, 10000), 0.0);
    }

    @Test
    public void 上限を超える分は残す() {
        assertEquals(1000.0, WithdrawCommand.allWithdrawAmount(5000, 1000), 0.0);
    }

    @Test
    public void 残高1未満は0() {
        assertEquals(0.0, WithdrawCommand.allWithdrawAmount(0.5, 1000), 0.0);
        assertEquals(0.0, WithdrawCommand.allWithdrawAmount(Double.NaN, 1000), 0.0);
    }
}
