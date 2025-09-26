package org.tofu.tofunomics.models;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * フェーズ7 - Playerモデルクラステスト
 * プレイヤーデータの基本機能とバリデーションをテスト
 */
public class PlayerTest {

    private Player player;
    private UUID testUuid;
    private final double INITIAL_BALANCE = 1000.0;
    private final double DELTA = 0.001; // 浮動小数点比較用許容差

    @Before
    public void setUp() {
        testUuid = UUID.randomUUID();
        player = new Player(testUuid, INITIAL_BALANCE);
    }

    @Test
    public void testDefaultConstructor() {
        Player emptyPlayer = new Player();
        assertNull("デフォルトコンストラクタでuuidはnullであるべき", emptyPlayer.getUuid());
        assertEquals("デフォルトコンストラクタで残高は0であるべき", 0.0, emptyPlayer.getBalance(), DELTA);
    }

    @Test
    public void testParameterizedConstructor() {
        assertNotNull("コンストラクタでuuidが設定されるべき", player.getUuid());
        assertEquals("コンストラクタでuuidが正しく設定されるべき", testUuid, player.getUuid());
        assertEquals("コンストラクタで残高が正しく設定されるべき", INITIAL_BALANCE, player.getBalance(), DELTA);
        assertNotNull("コンストラクタでcreatedAtが設定されるべき", player.getCreatedAt());
        assertNotNull("コンストラクタでupdatedAtが設定されるべき", player.getUpdatedAt());
    }

    @Test
    public void testUuidSettersAndGetters() {
        UUID newUuid = UUID.randomUUID();
        
        // UUID オブジェクトでの設定テスト
        player.setUuid(newUuid);
        assertEquals("setUuid(UUID)で正しく設定されるべき", newUuid, player.getUuid());

        // 文字列でのUUID設定テスト
        String uuidString = UUID.randomUUID().toString();
        player.setUuid(uuidString);
        assertEquals("setUuid(String)で正しく設定されるべき", uuidString, player.getUuid().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetUuidWithInvalidString() {
        player.setUuid("invalid-uuid-string");
    }

    @Test
    public void testBalanceSettersAndGetters() {
        double newBalance = 2500.0;
        Timestamp beforeUpdate = player.getUpdatedAt();
        
        // 少し待機してタイムスタンプの差を確実にする
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        player.setBalance(newBalance);
        assertEquals("setBalanceで正しく設定されるべき", newBalance, player.getBalance(), DELTA);
        assertTrue("setBalanceでupdatedAtが更新されるべき", 
                   player.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testAddBalance() {
        double addAmount = 500.0;
        double expectedBalance = INITIAL_BALANCE + addAmount;
        Timestamp beforeUpdate = player.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        player.addBalance(addAmount);
        assertEquals("addBalanceで正しく加算されるべき", expectedBalance, player.getBalance(), DELTA);
        assertTrue("addBalanceでupdatedAtが更新されるべき", 
                   player.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testAddNegativeBalance() {
        double negativeAmount = -200.0;
        double expectedBalance = INITIAL_BALANCE + negativeAmount;
        
        player.addBalance(negativeAmount);
        assertEquals("負の値でもaddBalanceが動作するべき", expectedBalance, player.getBalance(), DELTA);
    }

    @Test
    public void testRemoveBalanceSuccess() {
        double removeAmount = 300.0;
        double expectedBalance = INITIAL_BALANCE - removeAmount;
        Timestamp beforeUpdate = player.getUpdatedAt();
        
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        boolean result = player.removeBalance(removeAmount);
        assertTrue("残高が十分な場合removeBalanceはtrueを返すべき", result);
        assertEquals("removeBalanceで正しく減算されるべき", expectedBalance, player.getBalance(), DELTA);
        assertTrue("removeBalanceでupdatedAtが更新されるべき", 
                   player.getUpdatedAt().after(beforeUpdate));
    }

    @Test
    public void testRemoveBalanceInsufficientFunds() {
        double removeAmount = INITIAL_BALANCE + 1.0; // 残高より多い金額
        double originalBalance = player.getBalance();
        Timestamp beforeUpdate = player.getUpdatedAt();
        
        boolean result = player.removeBalance(removeAmount);
        assertFalse("残高が不足している場合removeBalanceはfalseを返すべき", result);
        assertEquals("残高が不足している場合は残高は変更されないべき", 
                     originalBalance, player.getBalance(), DELTA);
        assertEquals("残高が不足している場合はupdatedAtは更新されないべき", 
                     beforeUpdate, player.getUpdatedAt());
    }

    @Test
    public void testRemoveExactBalance() {
        boolean result = player.removeBalance(INITIAL_BALANCE);
        assertTrue("残高と同額のremoveBalanceはtrueを返すべき", result);
        assertEquals("残高と同額のremoveBalance後は残高が0になるべき", 0.0, player.getBalance(), DELTA);
    }

    @Test
    public void testTimestampSettersAndGetters() {
        Timestamp newCreatedAt = new Timestamp(System.currentTimeMillis() - 1000);
        Timestamp newUpdatedAt = new Timestamp(System.currentTimeMillis());
        
        player.setCreatedAt(newCreatedAt);
        player.setUpdatedAt(newUpdatedAt);
        
        assertEquals("setCreatedAtで正しく設定されるべき", newCreatedAt, player.getCreatedAt());
        assertEquals("setUpdatedAtで正しく設定されるべき", newUpdatedAt, player.getUpdatedAt());
    }

    @Test
    public void testBalancePrecision() {
        // 小数点精度のテスト
        double preciseAmount = 123.456789;
        player.setBalance(preciseAmount);
        assertEquals("小数点精度が保持されるべき", preciseAmount, player.getBalance(), DELTA);
    }

    @Test
    public void testMultipleOperations() {
        // 複数操作の組み合わせテスト
        player.addBalance(500.0);
        player.removeBalance(200.0);
        player.addBalance(100.0);
        
        double expectedBalance = INITIAL_BALANCE + 500.0 - 200.0 + 100.0;
        assertEquals("複数操作の結果が正しいべき", expectedBalance, player.getBalance(), DELTA);
    }

    @Test
    public void testZeroBalance() {
        player.setBalance(0.0);
        assertEquals("0円残高が正しく設定されるべき", 0.0, player.getBalance(), DELTA);
        
        boolean result = player.removeBalance(1.0);
        assertFalse("0円残高からの引き出しはfalseを返すべき", result);
        assertEquals("0円残高からの引き出し後も0円のまま", 0.0, player.getBalance(), DELTA);
    }
}