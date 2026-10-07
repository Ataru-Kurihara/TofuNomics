package org.tofu.tofunomics.economy;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 現金・預金が動いたことを残高の表示側へ知らせる係のテスト。
 */
public class BalanceDisplayRefresherTest {

    @After
    public void tearDown() {
        BalanceDisplayRefresher.install(null);
    }

    @Test
    public void 預金が動くと表示側へ知らせる_動かなければ知らせない() {
        List<UUID> notified = new ArrayList<>();
        BalanceDisplayRefresher.install(notified::add);
        UUID receiver = UUID.randomUUID();

        // 送金の受け取り側など、預金だけが動く場合もここを通る
        TransactionRecorder.recordBankChange(receiver, 200.0, 1200.0);
        TransactionRecorder.recordBankChange(receiver, 0.0, 1200.0);

        assertEquals(1, notified.size());
        assertEquals(receiver, notified.get(0));
    }

    @Test
    public void 登録が無ければ何もしない_表示の更新に失敗してもお金の操作は止めない() {
        BalanceDisplayRefresher.notifyChanged(UUID.randomUUID());

        BalanceDisplayRefresher.install(uuid -> { throw new IllegalStateException("表示の更新に失敗"); });
        BalanceDisplayRefresher.notifyChanged(UUID.randomUUID());
        TransactionRecorder.recordBankChange(UUID.randomUUID(), 10.0, 10.0);
        BalanceDisplayRefresher.notifyChanged(null);
    }
}
