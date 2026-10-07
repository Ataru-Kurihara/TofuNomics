package org.tofu.tofunomics.inventory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** ワールド退出・切断のときに印を消す */
    public void clear(UUID playerUuid) {
        restored.remove(playerUuid);
    }

    /** 保存してよいか（復元が済んでいるか） */
    public boolean canSave(UUID playerUuid) {
        return restored.contains(playerUuid);
    }
}
