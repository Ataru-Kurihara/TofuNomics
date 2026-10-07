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
    public void 起動時に手持ちが空なら保存データから復元する() {
        assertEquals(StartupAction.LOAD_FROM_SAVED, InventoryRestoreTracker.decideStartupAction(true));
    }

    @Test
    public void 起動時に手持ちがあれば今の手持ちを正とする() {
        assertEquals(StartupAction.TRUST_CURRENT, InventoryRestoreTracker.decideStartupAction(false));
    }
}
