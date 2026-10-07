package org.tofu.tofunomics.inventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 「このプレイヤーの手持ちは、保存してよい状態か」を覚えておくクラス。
 *
 * tofuNomics に入ると、ロビー側のプラグインが手持ちを消し、少し遅れて
 * TofuNomics が保存済みの手持ちを復元する。復元が済む前に保存すると、
 * 空の手持ちで保存データを上書きしてしまう。
 * そのため、復元が済んだプレイヤーにだけ印を付け、印が無いあいだは
 * どの経路（ワールド退出・切断・自動保存・プラグイン停止）でも保存しない。
 */
public class InventoryRestoreTracker {

    /** 手持ちの読み込み結果 */
    public enum LoadResult {
        /** 保存データを読み、手持ちに反映した */
        RESTORED,
        /** 保存データが無い（初めての入場） */
        NO_SAVED_DATA,
        /** 読み込みに失敗した（手持ちは変えていない） */
        FAILED
    }

    /** プラグイン起動時に、すでに tofuNomics にいたプレイヤーの扱い */
    public enum StartupAction {
        /** 保存データから復元する */
        LOAD_FROM_SAVED,
        /** 今の手持ちを正として、保存できる状態にする */
        TRUST_CURRENT
    }

    private final Set<UUID> restored = ConcurrentHashMap.newKeySet();

    /** 入場の番号を振るための通し番号（プレイヤーをまたいで増え続け、同じ番号は二度と使わない） */
    private final AtomicLong entryCounter = new AtomicLong();
    /** プレイヤーごとの「いちばん新しい入場の番号」。退出・切断で消す */
    private final Map<UUID, Long> latestEntry = new ConcurrentHashMap<>();

    /**
     * 入場（または接続）を記録し、その入場の番号を返す。
     * 復元は入場の少しあとに予約して行うので、予約した処理はこの番号を持っておき、
     * 動く時点で {@link #isLatestEntry} を確かめる。
     * 短時間に 入場 → 退出 → 再入場 したとき、1 回目の入場で予約した復元が、
     * 2 回目の入場の途中（ロビー側のアイテム付与より前）に走ってしまうのを防ぐ。
     */
    public long beginEntry(UUID playerUuid) {
        long entryId = entryCounter.incrementAndGet();
        latestEntry.put(playerUuid, entryId);
        return entryId;
    }

    /** 退出・切断のときに呼ぶ。予約済みの復元をすべて無効にする */
    public void cancelPendingEntry(UUID playerUuid) {
        latestEntry.remove(playerUuid);
    }

    /** 予約した処理の番号が、今もそのプレイヤーのいちばん新しい入場の番号か */
    public boolean isLatestEntry(UUID playerUuid, long entryId) {
        Long latest = latestEntry.get(playerUuid);
        return latest != null && latest == entryId;
    }

    /**
     * 読み込み結果を受け取り、保存してよい状態になったら印を付ける。
     * 失敗のときは印を付けない（保存データを空の手持ちで上書きしないため）。
     *
     * @return 印を付けたら true
     */
    public boolean onLoadFinished(UUID playerUuid, LoadResult result) {
        if (shouldMarkRestored(result)) {
            restored.add(playerUuid);
            return true;
        }
        return false;
    }

    /**
     * 読み込み結果から、保存してよい状態になったかを判定する。
     * 保存データが無い場合は、上書きして困るデータが無いので保存してよい。
     */
    public static boolean shouldMarkRestored(LoadResult result) {
        return result == LoadResult.RESTORED || result == LoadResult.NO_SAVED_DATA;
    }

    /**
     * プラグイン起動時（リロード含む）に、すでに tofuNomics にいたプレイヤーの扱いを決める。
     * 手持ちが空なら、復元待ちの途中でリロードされた可能性があるので保存データから復元する。
     * 空でなければ、今の手持ちが最新なのでそのまま保存できる状態にする。
     */
    public static StartupAction decideStartupAction(boolean inventoryIsEmpty) {
        return inventoryIsEmpty ? StartupAction.LOAD_FROM_SAVED : StartupAction.TRUST_CURRENT;
    }

    /** 今の手持ちを正として、保存できる状態にする */
    public void markRestored(UUID playerUuid) {
        restored.add(playerUuid);
    }

    /** ワールド退出・切断のときに印を消し、予約済みの復元も無効にする */
    public void clear(UUID playerUuid) {
        restored.remove(playerUuid);
        cancelPendingEntry(playerUuid);
    }

    /** 保存してよいか（復元が済んでいるか） */
    public boolean canSave(UUID playerUuid) {
        return restored.contains(playerUuid);
    }
}
