package org.tofu.tofunomics.economy;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 現金操作のあとの手持ち保存の予約のテスト。
 */
public class CashSaveSchedulerTest {

    @Test
    public void 同じ人の予約は何度呼んでも1回にまとまる() {
        Set<UUID> pending = new LinkedHashSet<>();
        UUID player = UUID.randomUUID();

        assertTrue("最初の予約は受け付ける", CashSaveScheduler.enqueue(pending, player));
        // 支払い→お釣り→品の受け取り、のように 1 回の操作で何度も現金が動く
        assertFalse(CashSaveScheduler.enqueue(pending, player));
        assertFalse(CashSaveScheduler.enqueue(pending, player));

        assertEquals(1, pending.size());
    }

    @Test
    public void 別の人の予約は別々に持つ() {
        Set<UUID> pending = new LinkedHashSet<>();

        assertTrue(CashSaveScheduler.enqueue(pending, UUID.randomUUID()));
        assertTrue(CashSaveScheduler.enqueue(pending, UUID.randomUUID()));
        assertFalse(CashSaveScheduler.enqueue(pending, null));

        assertEquals(2, pending.size());
    }

    @Test
    public void 手持ちを管理しているワールドにいる人だけ保存する() {
        assertTrue(CashSaveScheduler.isManagedWorld("tofuNomics"));
        // ほかのワールドの手持ちを保存すると、経済ワールド用の保存データを上書きしてしまう
        assertFalse(CashSaveScheduler.isManagedWorld("lobby"));
        assertFalse(CashSaveScheduler.isManagedWorld(null));
    }
}
