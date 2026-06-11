package org.tofu.tofunomics.market;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * 期限切れ出品を定期的に expired 化するタスク。
 *
 * {@code MarketExpirationTask#runTaskTimer(plugin, 0L, intervalTicks)} で起動する。
 * 実処理は {@link MarketManager#expireListings()} に委譲し、内部で
 * {@code synchronized(connection)} により共有 Connection の競合を防いでいる。
 */
public class MarketExpirationTask extends BukkitRunnable {

    private final MarketManager marketManager;
    private final Logger logger;

    public MarketExpirationTask(MarketManager marketManager, Logger logger) {
        this.marketManager = marketManager;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            int expired = marketManager.expireListings();
            if (expired > 0) {
                logger.info("マーケット: 期限切れ出品を " + expired + " 件 expired 化しました。");
            }
        } catch (Exception e) {
            // タスクが例外で停止しないように握りつぶしてログのみ
            logger.warning("マーケット期限切れタスクの実行中にエラーが発生しました: " + e.getMessage());
        }
    }
}
