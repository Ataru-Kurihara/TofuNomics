package org.tofu.tofunomics.economy;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 「入りきらなかった分は口座へ」の動きのテスト。
 *
 * 検証観点:
 * - /withdraw: 手持ちに入れられなかったら、引いた残高をそのまま戻す
 * - 支払い: TofuGold を崩したお釣りが入りきらなければ口座へ入金する
 * - 返金・報酬: 入りきらない分を口座へ入金する
 */
public class CurrencyConverterSafetyTest {

    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private PlayerDAO playerDAO;
    private ItemManager itemManager;
    private Player player;
    private org.tofu.tofunomics.models.Player account;
    private CurrencyConverter converter;

    @Before
    public void setUp() {
        playerDAO = mock(PlayerDAO.class);
        itemManager = mock(ItemManager.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("tester");

        account = new org.tofu.tofunomics.models.Player();
        account.setUuid(uuid.toString());
        account.setBankBalance(1000.0);
        when(playerDAO.getPlayerByUUID(uuid.toString())).thenReturn(account);
        when(playerDAO.updatePlayerData(account)).thenReturn(true);

        converter = new CurrencyConverter(playerDAO, itemManager, null, 0);
    }

    @Test
    public void 出金_手持ちに入れられなければ残高は元のまま() {
        when(itemManager.hasInventorySpace(player, 100)).thenReturn(true);
        when(itemManager.addGoldNuggetsToInventory(player, 100)).thenReturn(false);

        CurrencyConverter.WithdrawResult result = converter.withdrawToGoldNuggets(player, 100);

        assertEquals(CurrencyConverter.WithdrawResult.INSUFFICIENT_INVENTORY_SPACE, result);
        assertEquals(1000.0, account.getBankBalance(), 0.001);
    }

    @Test
    public void 支払い_入りきらなかったお釣りは口座へ入金する() {
        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(9);
        // 5 コインの支払いで TofuGold 1 枚を崩し、お釣り 4 枚のうち 3 枚が入りきらなかった
        when(itemManager.removeGoldNuggetsReturningUnplacedChange(player, 5)).thenReturn(3);

        assertTrue(converter.payWithCash(player, 5));
        assertEquals(1003.0, account.getBankBalance(), 0.001);
    }

    @Test
    public void 支払い_引き落とせなければ失敗し口座は変わらない() {
        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(9);
        when(itemManager.removeGoldNuggetsReturningUnplacedChange(player, 5)).thenReturn(-1);

        assertFalse(converter.payWithCash(player, 5));
        assertEquals(1000.0, account.getBankBalance(), 0.001);
    }

    @Test
    public void 返金_入りきらない分は口座へ入金する() {
        when(itemManager.addGoldNuggetsWithLeftover(player, 200)).thenReturn(72);

        int banked = converter.receiveCashWithBankFallback(player, 200.0);

        assertEquals(72, banked);
        assertEquals(1072.0, account.getBankBalance(), 0.001);
    }

    @Test
    public void 口座に入れられないときは足元へ落とす() {
        when(playerDAO.getPlayerByUUID(uuid.toString())).thenReturn(null);

        converter.bankUnplacedNuggets(player, 4);

        verify(itemManager).dropGoldNuggetsAtLocation(player, 4);
    }

    // ===== 現金が動いたら手持ちの保存を予約する =====

    @Test
    public void 現金で支払うと手持ちの保存が予約される() {
        java.util.List<Player> notified = new java.util.ArrayList<>();
        converter.setCashChangeListener(notified::add);
        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(9);
        when(itemManager.removeGoldNuggetsReturningUnplacedChange(player, 5)).thenReturn(0);

        assertTrue(converter.payWithCash(player, 5));

        assertEquals(1, notified.size());
        assertSame(player, notified.get(0));
    }

    @Test
    public void 支払えなかったときは保存を予約しない() {
        java.util.List<Player> notified = new java.util.ArrayList<>();
        converter.setCashChangeListener(notified::add);
        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(0);

        assertFalse(converter.payWithCash(player, 5));

        assertTrue(notified.isEmpty());
    }

    @Test
    public void 預け入れと引き出しと受け取りでも保存が予約される() {
        java.util.List<Player> notified = new java.util.ArrayList<>();
        converter.setCashChangeListener(notified::add);

        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(50);
        when(itemManager.removeGoldNuggetsReturningUnplacedChange(player, 50)).thenReturn(0);
        assertTrue(converter.depositGoldNuggets(player, 50));
        assertEquals(1050.0, account.getBankBalance(), 0.001);

        when(itemManager.hasInventorySpace(player, 20)).thenReturn(true);
        when(itemManager.addGoldNuggetsToInventory(player, 20)).thenReturn(true);
        assertEquals(CurrencyConverter.WithdrawResult.SUCCESS, converter.withdrawToGoldNuggets(player, 20));

        when(itemManager.addGoldNuggetsWithLeftover(player, 10)).thenReturn(0);
        converter.receiveCashWithBankFallback(player, 10);

        assertEquals(3, notified.size());
    }

    @Test
    public void 保存の予約に失敗してもお金の操作は成功のまま() {
        converter.setCashChangeListener(p -> { throw new IllegalStateException("保存の予約に失敗"); });
        when(itemManager.countGoldNuggetsInInventory(player)).thenReturn(9);
        when(itemManager.removeGoldNuggetsReturningUnplacedChange(player, 5)).thenReturn(0);

        assertTrue(converter.payWithCash(player, 5));
    }
}
