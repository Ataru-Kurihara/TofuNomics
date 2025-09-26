package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * EcoCommandのテストクラス
 * balance/bankBalance処理統一の修正を重点的にテスト
 */
@RunWith(MockitoJUnitRunner.class)
public class EcoCommandTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private PlayerDAO playerDAO;

    @Mock
    private CommandSender sender;

    @Mock
    private Player targetPlayer;

    @Mock
    private Command command;

    @Mock
    private Server server;

    private EcoCommand ecoCommand;

    @Before
    public void setUp() {
        ecoCommand = new EcoCommand(configManager, currencyConverter, playerDAO);

        // デフォルトの設定値をモック
        when(configManager.getCurrencySymbol()).thenReturn("G");
        when(configManager.getMessagePrefix()).thenReturn("§7[TofuNomics] ");
        // when(configManager.getMessage("player_not_found")).thenReturn("プレイヤーが見つかりません。"); // 未使用のため削除
        when(configManager.getMessage("invalid_amount")).thenReturn("無効な金額です。");

        // プレイヤーの設定
        UUID targetUuid = UUID.randomUUID();
        when(targetPlayer.getUniqueId()).thenReturn(targetUuid);
        when(targetPlayer.getName()).thenReturn("TestPlayer");
    }

    @Test
    public void testEcoGiveNewPlayer() {
        // 新規プレイヤーに金額を付与するテストケース
        double amount = 1000.0;

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // プレイヤーがデータベースに存在しない
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(null);

            // 新規プレイヤー作成が成功
            when(playerDAO.insertPlayer(any(org.tofu.tofunomics.models.Player.class))).thenReturn(true);

            when(currencyConverter.formatCurrency(amount)).thenReturn("1000");

            String[] args = {"give", "TestPlayer", "1000"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき", result);

            // 新規プレイヤーが正しい設定で作成されたことを検証
            verify(playerDAO).insertPlayer(argThat(player -> {
                return player.getBalance() == 0.0 && // 持ち歩き現金は0
                       player.getBankBalance() == amount; // 銀行預金に設定
            }));

            // 成功メッセージが表示される
            verify(sender).sendMessage(contains("1000 G を付与しました"));
            verify(targetPlayer).sendMessage(contains("1000 G が付与されました"));
        }
    }

    @Test
    public void testEcoGiveExistingPlayer() {
        // 既存プレイヤーに金額を追加するテストケース
        double amount = 500.0;
        double currentBankBalance = 1000.0;

        org.tofu.tofunomics.models.Player existingPlayer = mock(org.tofu.tofunomics.models.Player.class);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // 既存プレイヤーが存在
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(existingPlayer);

            // プレイヤー更新が成功
            when(playerDAO.updatePlayerData(existingPlayer)).thenReturn(true);

            when(currencyConverter.formatCurrency(amount)).thenReturn("500");

            String[] args = {"give", "TestPlayer", "500"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき", result);

            // 銀行預金に正しく追加されたことを検証
            verify(existingPlayer).addBankBalance(amount);
            verify(playerDAO).updatePlayerData(existingPlayer);

            // 成功メッセージが表示される
            verify(sender).sendMessage(contains("500 G を付与しました"));
            verify(targetPlayer).sendMessage(contains("500 G が付与されました"));
        }
    }

    @Test
    public void testEcoTakeFromPlayer() {
        // プレイヤーから金額を取り上げるテストケース
        double amount = 300.0;
        double currentBankBalance = 1000.0;

        org.tofu.tofunomics.models.Player existingPlayer = mock(org.tofu.tofunomics.models.Player.class);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // 既存プレイヤーが存在
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(existingPlayer);

            // プレイヤー更新が成功
            when(playerDAO.updatePlayerData(existingPlayer)).thenReturn(true);

            when(currencyConverter.formatCurrency(amount)).thenReturn("300");

            String[] args = {"take", "TestPlayer", "300"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき", result);

            // 銀行預金から正しく差し引かれたことを検証
            verify(existingPlayer).removeBankBalance(amount);
            verify(playerDAO).updatePlayerData(existingPlayer);

            // 成功メッセージが表示される
            verify(sender).sendMessage(contains("300 G を取り上げました"));
            verify(targetPlayer).sendMessage(contains("300 G が取り上げられました"));
        }
    }

    @Test
    public void testEcoSetNewPlayer() {
        // 新規プレイヤーの残高を設定するテストケース
        double amount = 2000.0;

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // プレイヤーがデータベースに存在しない
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(null);

            // 新規プレイヤー作成が成功
            when(playerDAO.insertPlayer(any(org.tofu.tofunomics.models.Player.class))).thenReturn(true);

            when(currencyConverter.formatCurrency(amount)).thenReturn("2000");

            String[] args = {"set", "TestPlayer", "2000"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき", result);

            // 新規プレイヤーが正しい設定で作成されたことを検証
            verify(playerDAO).insertPlayer(argThat(player -> {
                return player.getBalance() == 0.0 && // 持ち歩き現金は0
                       player.getBankBalance() == amount; // 銀行預金に設定
            }));

            // 成功メッセージが表示される
            verify(sender).sendMessage(contains("2000 G に設定しました"));
        }
    }

    @Test
    public void testEcoSetExistingPlayer() {
        // 既存プレイヤーの残高を設定するテストケース
        double amount = 1500.0;

        org.tofu.tofunomics.models.Player existingPlayer = mock(org.tofu.tofunomics.models.Player.class);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // 既存プレイヤーが存在
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(existingPlayer);

            // プレイヤー更新が成功
            when(playerDAO.updatePlayerData(existingPlayer)).thenReturn(true);

            when(currencyConverter.formatCurrency(amount)).thenReturn("1500");

            String[] args = {"set", "TestPlayer", "1500"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき", result);

            // 銀行預金が正しく設定されたことを検証
            verify(existingPlayer).setBankBalance(amount);
            verify(playerDAO).updatePlayerData(existingPlayer);

            // 成功メッセージが表示される
            verify(sender).sendMessage(contains("1500 G に設定しました"));
        }
    }

    @Test
    public void testEcoTakePlayerNotFound() {
        // プレイヤーが存在しないテストケース
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            // プレイヤーがデータベースに存在しない
            when(playerDAO.getPlayerByUUID(targetPlayer.getUniqueId().toString())).thenReturn(null);

            String[] args = {"take", "TestPlayer", "100"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // データ更新は実行されない
            verify(playerDAO, never()).updatePlayerData(any());

            // エラーメッセージが表示される
            verify(sender).sendMessage(contains("対象プレイヤーのデータが見つかりません"));
        }
    }

    @Test
    public void testEcoInvalidAmount() {
        // 無効な金額のテストケース
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            String[] args = {"give", "TestPlayer", "abc"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // データ更新は実行されない
            verify(playerDAO, never()).insertPlayer(any());
            verify(playerDAO, never()).updatePlayerData(any());

            // エラーメッセージが表示される
            verify(sender).sendMessage(contains("無効な金額"));
        }
    }

    @Test
    public void testEcoNegativeAmount() {
        // 負の金額設定のテストケース（setコマンド用）
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer("TestPlayer")).thenReturn(targetPlayer);

            String[] args = {"set", "TestPlayer", "-100"};
            boolean result = ecoCommand.onCommand(sender, command, "eco", args);

            assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

            // データ更新は実行されない
            verify(playerDAO, never()).insertPlayer(any());
            verify(playerDAO, never()).updatePlayerData(any());

            // エラーメッセージが表示される
            verify(sender).sendMessage(contains("残高は負の値にできません"));
        }
    }

    @Test
    public void testEcoInvalidSubcommand() {
        // 無効なサブコマンドのテストケース
        String[] args = {"invalid", "TestPlayer", "100"};
        boolean result = ecoCommand.onCommand(sender, command, "eco", args);

        assertTrue("コマンドが成功するべき（ヘルプ表示）", result);

        // ヘルプメッセージが表示される
        verify(sender).sendMessage(contains("経済コマンド"));
    }

    @Test
    public void testEcoNoArguments() {
        // 引数なしのテストケース
        String[] args = {};
        boolean result = ecoCommand.onCommand(sender, command, "eco", args);

        assertTrue("コマンドが成功するべき（ヘルプ表示）", result);

        // ヘルプメッセージが表示される
        verify(sender).sendMessage(contains("経済コマンド"));
    }

    @Test
    public void testEcoInvalidArgumentCount() {
        // 引数が不足しているテストケース
        String[] args = {"give", "TestPlayer"}; // 金額が不足
        boolean result = ecoCommand.onCommand(sender, command, "eco", args);

        assertTrue("コマンドが成功するべき（エラーハンドリング）", result);

        // データ更新は実行されない
        verify(playerDAO, never()).insertPlayer(any());
        verify(playerDAO, never()).updatePlayerData(any());

        // エラーメッセージが表示される
        verify(sender).sendMessage(contains("使用法"));
    }
}