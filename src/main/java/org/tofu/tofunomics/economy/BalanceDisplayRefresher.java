package org.tofu.tofunomics.economy;

import org.bukkit.Bukkit;
import org.tofu.tofunomics.TofuNomics;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 現金・預金が動いたことを、残高を表示している側（スコアボード）へ知らせる係。
 *
 * スコアボードは DB から読んだ残高を数秒のあいだ使い回す。お金が動いた直後にその写しを
 * 捨ててもらうことで、表示が古い残高のまま数秒残るのを防ぐ。
 * 残高を書き換える所はプラグイン内に散らばっているので、お金の記録（TransactionRecorder）と
 * 同じく、起動時に 1 つだけ登録して共有する。登録が無い（テストなど）ときは何もしない。
 */
public final class BalanceDisplayRefresher {

    private static volatile Consumer<UUID> listener;

    private BalanceDisplayRefresher() {
    }

    /** 起動時に 1 回呼ぶ。null を渡すと登録を外す */
    public static void install(Consumer<UUID> newListener) {
        listener = newListener;
    }

    /**
     * その人の現金・預金が動いたことを知らせる。失敗しても、お金の操作は止めない。
     */
    public static void notifyChanged(UUID playerUuid) {
        Consumer<UUID> current = listener;
        if (current == null || playerUuid == null) {
            return;
        }
        try {
            current.accept(playerUuid);
        } catch (RuntimeException e) {
            // 表示の更新に失敗しても、お金の操作は止めない
        }
    }

    /**
     * スコアボードの残高の写しを捨てる処理を作る。
     * スコアボードの操作はメインスレッドで行う。残高の保存は別スレッドから呼ばれることがある
     * （入場時の新規プレイヤー作成など）ので、その場合はメインスレッドへ回す。
     */
    public static Consumer<UUID> forScoreboard(TofuNomics plugin) {
        return playerUuid -> {
            if (Bukkit.isPrimaryThread()) {
                invalidate(plugin, playerUuid);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> invalidate(plugin, playerUuid));
            }
        };
    }

    private static void invalidate(TofuNomics plugin, UUID playerUuid) {
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().invalidateStatus(playerUuid);
        }
    }
}
