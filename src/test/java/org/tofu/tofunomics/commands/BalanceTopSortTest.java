package org.tofu.tofunomics.commands;

import org.junit.Test;
import org.tofu.tofunomics.models.Player;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * /baltop が、プレイヤーが実際に使えるお金（銀行残高）の多い順に並ぶことのテスト。
 * 以前は、使われていない balance 列を足した順で並び、表示額も balance 列だった。
 */
public class BalanceTopSortTest {

    private Player player(double unusedBalance, double bankBalance) {
        Player player = new Player();
        player.setBalance(unusedBalance);
        player.setBankBalance(bankBalance);
        return player;
    }

    @Test
    public void 銀行残高の多い順に並ぶ() {
        Player poorWithOldBalance = player(1000, 5);   // 使えない balance 列だけが多い人
        Player rich = player(0, 300);
        Player middle = player(0, 50);

        List<Player> sorted = BalanceTopCommand.sortByBankBalance(Arrays.asList(poorWithOldBalance, middle, rich));

        assertEquals(300, sorted.get(0).getBankBalance(), 0.0);
        assertEquals(50, sorted.get(1).getBankBalance(), 0.0);
        assertEquals(5, sorted.get(2).getBankBalance(), 0.0);
    }
}
