package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * PayCommandのテストクラス
 * 手数料処理バグの修正を重点的にテスト
 */
@RunWith(MockitoJUnitRunner.class)
public class PayCommandTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private Player fromPlayer;

    @Mock
    private Player targetPlayer;

    @Mock
    private Command command;

    @Mock
    private Server server;

    private PayCommand payCommand;

    @Before
    public void setUp() {
        payCommand = new PayCommand(configManager, currencyConverter);

        // デフォルトの設定値をモック
        when(configManager.getMinimumPayAmount()).thenReturn(1.0);
        when(configManager.getMaximumPayAmount()).thenReturn(10000.0);
        when(configManager.getPayFeePercentage()).thenReturn(5.0); // 5%の手数料
        when(configManager.getCurrencySymbol()).thenReturn("G");
        when(configManager.getMessagePrefix()).thenReturn("§7[TofuNomics] ");
        when(configManager.getMessage("invalid_amount")).thenReturn("無効な金額です。");
        when(configManager.getMessage("insufficient_balance")).thenReturn("残高が不足しています。");
        when(configManager.getMessage(eq("economy.pay_sent"), any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                String player = (String) args[2];
                String amount = (String) args[4];
                String currency = (String) args[6];
                return player + "に" + amount + currency + "を送金しました。";
            });
        when(configManager.getMessage(eq("economy.pay_received"), any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                String player = (String) args[2];
                String amount = (String) args[4];
                String currency = (String) args[6];
                return player + "から" + amount + currency + "を受け取りました。";
            });

        // プレイヤーの設定
        UUID fromUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        when(fromPlayer.getUniqueId()).thenReturn(fromUuid);
        when(targetPlayer.getUniqueId()).thenReturn(targetUuid);
        when(fromPlayer.getName()).thenReturn("TestSender");
        when(targetPlayer.getName()).thenReturn("TestReceiver");
    }

    @Test
    public void testPayCommandWithFee_CorrectDeduction() {
        // 送金額100G、手数料5%（5G）のテストケース
        double payAmount = 100.0;
        double feePercentage = 5.0;
        double expectedFee = payAmount * (feePercentage / 100.0); // 5G
        double totalRequired = payAmount + expectedFee; // 105G

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            // 送金者は十分な残高を持っている
            when(currencyConverter.canAfford(fromPlayer, totalRequired)).thenReturn(true);
            
            // 送金処理が成功する（送金額のみ）
            when(currencyConverter.transfer(fromPlayer, targetPlayer, payAmount)).thenReturn(true);
            
            // 手数料差し引きが成功する
            when(currencyConverter.subtractBalance(fromPlayer.getUniqueId(), expectedFee)).thenReturn(true);
            
            // 金額フォーマット
            when(currencyConverter.formatCurrency(payAmount)).thenReturn("100");
            when(currencyConverter.formatCurrency(expectedFee)).thenReturn("5");

            String[] args = {"TestReceiver", "100"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき", result);

            // 送金が正しい金額で実行されたことを検証
            verify(currencyConverter).transfer(fromPlayer, targetPlayer, payAmount);
            
            // 手数料が正しく差し引かれたことを検証
            verify(currencyConverter).subtractBalance(fromPlayer.getUniqueId(), expectedFee);

            // メッセージが送信されたことを検証
            verify(fromPlayer).sendMessage(contains("100G"));
            verify(targetPlayer).sendMessage(contains("100G"));
            verify(fromPlayer).sendMessage(contains("送金手数料: 5 G"));
        }
    }

    @Test
    public void testPayCommandWithoutFee() {
        // 手数料0%のテストケース
        when(configManager.getPayFeePercentage()).thenReturn(0.0);

        double payAmount = 100.0;

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            when(currencyConverter.canAfford(fromPlayer, payAmount)).thenReturn(true);
            when(currencyConverter.transfer(fromPlayer, targetPlayer, payAmount)).thenReturn(true);
            when(currencyConverter.formatCurrency(payAmount)).thenReturn("100");

            String[] args = {"TestReceiver", "100"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき", result);

            // 送金のみ実行され、手数料差し引きは実行されない
            verify(currencyConverter).transfer(fromPlayer, targetPlayer, payAmount);
            verify(currencyConverter, never()).subtractBalance(eq(fromPlayer.getUniqueId()), anyDouble());

            // 手数料メッセージは表示されない
            verify(fromPlayer, never()).sendMessage(contains("送金手数料"));
        }
    }

    @Test
    public void testPayCommandInsufficientBalance() {
        // 残高不足のテストケース
        double payAmount = 100.0;
        double expectedFee = 5.0;
        double totalRequired = 105.0;

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            // 残高が不足している
            when(currencyConverter.canAfford(fromPlayer, totalRequired)).thenReturn(false);

            String[] args = {"TestReceiver", "100"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // 送金処理は実行されない
            verify(currencyConverter, never()).transfer(any(), any(), anyDouble());
            verify(currencyConverter, never()).subtractBalance(any(), anyDouble());

            // エラーメッセージが表示される
            verify(fromPlayer).sendMessage(contains("残高が不足しています"));
        }
    }

    @Test
    public void testPayCommandTransferFailure() {
        // 送金処理が失敗するテストケース
        double payAmount = 100.0;
        double totalRequired = 105.0;

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            when(currencyConverter.canAfford(fromPlayer, totalRequired)).thenReturn(true);
            
            // 送金処理が失敗する
            when(currencyConverter.transfer(fromPlayer, targetPlayer, payAmount)).thenReturn(false);

            String[] args = {"TestReceiver", "100"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // 手数料差し引きは実行されない（送金失敗のため）
            verify(currencyConverter, never()).subtractBalance(any(), anyDouble());

            // エラーメッセージが表示される
            verify(fromPlayer).sendMessage(contains("送金に失敗しました"));
        }
    }

    @Test
    public void testPayCommandMinimumAmount() {
        // 最低送金額のテストケース
        when(configManager.getMinimumPayAmount()).thenReturn(10.0);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            String[] args = {"TestReceiver", "5"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // 送金処理は実行されない
            verify(currencyConverter, never()).transfer(any(), any(), anyDouble());

            // エラーメッセージが表示される
            verify(fromPlayer).sendMessage(contains("最低送金額"));
        }
    }

    @Test
    public void testPayCommandToSelf() {
        // 自分自身への送金テストケース
        UUID selfUuid = UUID.randomUUID();
        when(fromPlayer.getUniqueId()).thenReturn(selfUuid);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestSender")).thenReturn(fromPlayer);

            String[] args = {"TestSender", "100"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // 送金処理は実行されない
            verify(currencyConverter, never()).transfer(any(), any(), anyDouble());

            // エラーメッセージが表示される
            verify(fromPlayer).sendMessage(contains("自分自身には送金できません"));
        }
    }

    @Test
    public void testPayCommandInvalidArguments() {
        // 引数が不正なテストケース
        String[] args = {"TestReceiver"}; // 金額が不足

        boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

        assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

        // 送金処理は実行されない
        verify(currencyConverter, never()).transfer(any(), any(), anyDouble());

        // エラーメッセージが表示される
        verify(fromPlayer).sendMessage(contains("使用法"));
    }

    @Test
    public void testPayCommandNonNumericAmount() {
        // 数値でない金額のテストケース
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestReceiver")).thenReturn(targetPlayer);

            String[] args = {"TestReceiver", "abc"};
            boolean result = payCommand.onCommand(fromPlayer, command, "pay", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // 送金処理は実行されない
            verify(currencyConverter, never()).transfer(any(), any(), anyDouble());

            // エラーメッセージが表示される
            verify(fromPlayer).sendMessage(contains("無効な金額"));
        }
    }
}