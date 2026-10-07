package org.tofu.tofunomics.economy;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 「いまから行うお金の操作の理由」の宣言のテスト。
 */
public class TransactionContextTest {

    @Test
    public void 宣言が無ければその他() {
        assertFalse(TransactionContext.isDeclared());
        assertEquals(TransactionType.OTHER, TransactionContext.current().getType());
        assertNull(TransactionContext.current().getCounterparty());
    }

    @Test
    public void 宣言した範囲を抜けると元に戻る() {
        try (TransactionContext.Scope outer = TransactionContext.open(TransactionType.NPC_BUY, "NPC:食料品店", "BREAD x1")) {
            assertEquals(TransactionType.NPC_BUY, TransactionContext.current().getType());
            assertEquals("NPC:食料品店", TransactionContext.current().getCounterparty());
            assertEquals("BREAD x1", TransactionContext.current().getDetail());

            try (TransactionContext.Scope inner = TransactionContext.open(TransactionType.MARKET_REFUND, null, null)) {
                assertEquals(TransactionType.MARKET_REFUND, TransactionContext.current().getType());
            }
            assertEquals(TransactionType.NPC_BUY, TransactionContext.current().getType());
        }
        assertFalse(TransactionContext.isDeclared());
    }

    @Test
    public void 共通の入口の既定の理由は呼び出し元の宣言を上書きしない() {
        // 宣言が無ければ既定の理由が付く
        try (TransactionContext.Scope scope = TransactionContext.openIfUndeclared(TransactionType.DEPOSIT, null, null)) {
            assertEquals(TransactionType.DEPOSIT, TransactionContext.current().getType());
        }
        assertFalse(TransactionContext.isDeclared());

        // 呼び出し元が宣言していれば、そちらが優先される
        try (TransactionContext.Scope caller = TransactionContext.open(TransactionType.RENT, null, null)) {
            try (TransactionContext.Scope scope = TransactionContext.openIfUndeclared(TransactionType.DEPOSIT, null, null)) {
                assertEquals(TransactionType.RENT, TransactionContext.current().getType());
            }
            assertEquals("内側を抜けても呼び出し元の宣言は残る", TransactionType.RENT, TransactionContext.current().getType());
        }
    }

    @Test
    public void 理由の表示名_知らない記号はそのまま返す() {
        assertEquals("預け入れ", TransactionType.labelOf("DEPOSIT"));
        assertEquals("その他", TransactionType.labelOf(null));
        assertEquals("FUTURE_TYPE", TransactionType.labelOf("FUTURE_TYPE"));
    }

    @Test
    public void ごく小さな増減は記録の対象にしない() {
        assertTrue(TransactionRecorder.isRecordable(1.0));
        assertTrue(TransactionRecorder.isRecordable(-0.5));
        assertFalse(TransactionRecorder.isRecordable(0.0));
        assertFalse(TransactionRecorder.isRecordable(0.00000001));
        assertFalse(TransactionRecorder.isRecordable(Double.NaN));
    }

    @Test
    public void 例外で抜けても前の宣言に戻る() {
        try (TransactionContext.Scope outer = TransactionContext.open(TransactionType.RENT, null, null)) {
            try {
                try (TransactionContext.Scope inner = TransactionContext.open(TransactionType.NPC_BUY, null, null)) {
                    throw new IllegalStateException("途中で失敗");
                }
            } catch (IllegalStateException expected) {
                // 内側の宣言は残らず、外側の宣言に戻っている
                assertEquals(TransactionType.RENT, TransactionContext.current().getType());
            }
        }
        assertFalse("すべて抜けたら宣言なしに戻る", TransactionContext.isDeclared());
    }

    @Test
    public void 宣言はスレッドごとに別で_ほかのスレッドの宣言は混ざらない() throws Exception {
        final TransactionType[] seenInOtherThread = new TransactionType[1];
        final boolean[] declaredInOtherThread = new boolean[1];

        try (TransactionContext.Scope scope = TransactionContext.open(TransactionType.ECO_GIVE, "admin", null)) {
            // 入場時のデータ作成のように、別スレッドで残高の保存が走る場合
            Thread other = new Thread(() -> {
                declaredInOtherThread[0] = TransactionContext.isDeclared();
                seenInOtherThread[0] = TransactionContext.current().getType();
            });
            other.start();
            other.join();

            assertEquals(TransactionType.ECO_GIVE, TransactionContext.current().getType());
        }

        assertFalse(declaredInOtherThread[0]);
        assertEquals(TransactionType.OTHER, seenInOtherThread[0]);
    }
}
