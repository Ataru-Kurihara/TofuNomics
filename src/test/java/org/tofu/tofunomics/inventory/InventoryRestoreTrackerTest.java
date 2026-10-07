package org.tofu.tofunomics.inventory;

import org.junit.Test;
import org.tofu.tofunomics.inventory.InventoryRestoreTracker.LoadResult;
import org.tofu.tofunomics.inventory.InventoryRestoreTracker.StartupAction;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 「復元が済むまで手持ちを保存しない」判定の回帰テスト。
 * 入場直後（復元前）の保存で、保存データが空の手持ちに上書きされる不具合を防ぐ。
 */
public class InventoryRestoreTrackerTest {

    private final UUID player = UUID.randomUUID();

    @Test
    public void 復元前は保存できない() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        assertFalse(tracker.canSave(player));
    }

    @Test
    public void 復元に成功したら保存できる() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        assertTrue(tracker.onLoadFinished(player, LoadResult.RESTORED));
        assertTrue(tracker.canSave(player));
    }

    @Test
    public void 保存データが無い初回入場は保存できる() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        assertTrue(tracker.onLoadFinished(player, LoadResult.NO_SAVED_DATA));
        assertTrue(tracker.canSave(player));
    }

    @Test
    public void 読み込みに失敗したら保存できないまま() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        assertFalse(tracker.onLoadFinished(player, LoadResult.FAILED));
        assertFalse(tracker.canSave(player));
    }

    @Test
    public void 退出や切断で印を消すと次の復元まで保存できない() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        tracker.onLoadFinished(player, LoadResult.RESTORED);
        tracker.clear(player);
        assertFalse(tracker.canSave(player));

        // 入り直して復元が済めば、また保存できる
        tracker.onLoadFinished(player, LoadResult.RESTORED);
        assertTrue(tracker.canSave(player));
    }

    @Test
    public void 印はプレイヤーごとに別() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        tracker.onLoadFinished(player, LoadResult.RESTORED);
        assertFalse(tracker.canSave(UUID.randomUUID()));
    }

    @Test
    public void 予約した復元は自分の入場が最新のときだけ動く() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        long first = tracker.beginEntry(player);
        assertTrue(tracker.isLatestEntry(player, first));
    }

    @Test
    public void 入場して退出して再入場したら1回目の予約は動かない() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        long first = tracker.beginEntry(player);   // 1 回目の入場（40tick 後の復元を予約）
        tracker.clear(player);                      // 0.5 秒で退出
        long second = tracker.beginEntry(player);  // さらに 0.5 秒で再入場

        assertFalse("1 回目の予約は無効", tracker.isLatestEntry(player, first));
        assertTrue("2 回目の予約だけが動く", tracker.isLatestEntry(player, second));
    }

    @Test
    public void 退出や切断のあとは予約した復元が動かない() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        long entry = tracker.beginEntry(player);
        tracker.clear(player);
        assertFalse(tracker.isLatestEntry(player, entry));
    }

    @Test
    public void 切断して入り直しても前の接続の番号とは重ならない() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        long beforeQuit = tracker.beginEntry(player);
        tracker.clear(player);
        long afterRejoin = tracker.beginEntry(player);
        assertNotEquals(beforeQuit, afterRejoin);
        assertFalse(tracker.isLatestEntry(player, beforeQuit));
    }

    @Test
    public void 入場の番号はプレイヤーごとに別() {
        InventoryRestoreTracker tracker = new InventoryRestoreTracker();
        long mine = tracker.beginEntry(player);
        UUID other = UUID.randomUUID();
        tracker.beginEntry(other);
        assertTrue("ほかの人の入場で自分の予約は無効にならない", tracker.isLatestEntry(player, mine));
    }

    @Test
    public void 起動時に手持ちが空なら保存データから復元する() {
        assertEquals(StartupAction.LOAD_FROM_SAVED, InventoryRestoreTracker.decideStartupAction(true));
    }

    @Test
    public void 起動時に手持ちがあれば今の手持ちを正とする() {
        assertEquals(StartupAction.TRUST_CURRENT, InventoryRestoreTracker.decideStartupAction(false));
    }
}
