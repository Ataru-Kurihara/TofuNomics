package org.tofu.tofunomics.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.dao.TransactionLogDAO;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * お金の記録（監査ログ）を残す係。
 *
 * 記録に失敗しても、お金の操作そのものは止めない（例外は外へ出さず、警告だけ残す）。
 * 残高を書き換える PlayerDAO はプラグイン内に複数作られるので、どれからでも同じ係に
 * 届くよう、起動時に 1 つだけ登録して共有する。登録が無い（テストなど）ときは何もしない。
 */
public class TransactionRecorder {

    /** これ未満の増減は「変化なし」とみなして記録しない（浮動小数の誤差よけ） */
    private static final double MIN_RECORDED_AMOUNT = 0.0001;

    private static volatile TransactionRecorder global;

    private final TransactionLogDAO logDAO;
    private final Logger logger;

    public TransactionRecorder(TransactionLogDAO logDAO, Logger logger) {
        this.logDAO = logDAO;
        this.logger = logger;
    }

    /** 起動時に 1 回呼ぶ。null を渡すと登録を外す */
    public static void install(TransactionRecorder recorder) {
        global = recorder;
    }

    /** 登録されている係。無ければ null */
    public static TransactionRecorder global() {
        return global;
    }

    /**
     * 預金（銀行残高）の増減を、いま宣言されている理由で記録する。
     */
    public static void recordBankChange(UUID playerUuid, double amount, double balanceAfter) {
        if (isRecordable(amount)) {
            // 預金が動いたので、残高の表示（スコアボード）にも知らせる。送金の受け取り側もここを通る
            BalanceDisplayRefresher.notifyChanged(playerUuid);
        }
        TransactionRecorder recorder = global;
        if (recorder != null) {
            recorder.record(playerUuid, TransactionLogDAO.ACCOUNT_BANK, amount, balanceAfter);
        }
    }

    /**
     * 手持ちの現金の増減を、いま宣言されている理由で記録する。
     */
    public static void recordCashChange(UUID playerUuid, double amount) {
        TransactionRecorder recorder = global;
        if (recorder != null) {
            recorder.record(playerUuid, TransactionLogDAO.ACCOUNT_CASH, amount, null);
        }
    }

    /** 記録する価値のある増減かどうか */
    public static boolean isRecordable(double amount) {
        return !Double.isNaN(amount) && Math.abs(amount) >= MIN_RECORDED_AMOUNT;
    }

    void record(UUID playerUuid, String account, double amount, Double balanceAfter) {
        if (playerUuid == null || !isRecordable(amount)) {
            return;
        }
        try {
            TransactionContext context = TransactionContext.current();
            logDAO.insert(new TransactionLogDAO.Entry(
                System.currentTimeMillis(),
                playerUuid,
                resolveOnlineName(playerUuid),
                context.getType().name(),
                account,
                amount,
                balanceAfter,
                context.getCounterparty(),
                context.getDetail()));
        } catch (Exception e) {
            // 記録の失敗でお金の操作を止めない
            logger.log(Level.WARNING, "お金の記録に失敗しました（操作は続行します）: " + playerUuid, e);
        }
    }

    /** オンラインなら名前を添える（オフラインの相手を名前で探せるようにするため） */
    private String resolveOnlineName(UUID playerUuid) {
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            Player online = Bukkit.getPlayer(playerUuid);
            return online != null ? online.getName() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 指定したプレイヤーの記録を新しい順に返す。読めなければ空 */
    public List<TransactionLogDAO.Entry> findRecent(UUID playerUuid, int limit) {
        try {
            return logDAO.findRecentByPlayer(playerUuid, limit);
        } catch (Exception e) {
            logger.log(Level.WARNING, "お金の記録の読み取りに失敗しました: " + playerUuid, e);
            return Collections.emptyList();
        }
    }

    /** 記録に残っている名前から UUID を引く。見つからなければ null */
    public UUID findUuidByName(String playerName) {
        try {
            return logDAO.findUuidByName(playerName);
        } catch (Exception e) {
            return null;
        }
    }
}
