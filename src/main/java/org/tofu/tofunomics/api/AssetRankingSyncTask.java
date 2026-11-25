package org.tofu.tofunomics.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 資産ランキングの定期送信を Bukkit のスケジューラに載せる係。
 *
 * - DB の読み取りと名前の取得はメインスレッドで行う（DB の接続は 1 本を共有しているため）。
 * - 通信は非同期スレッドで行う。前の回の送信が終わっていなければ、その回は飛ばす。
 * - 設定は start() の時点で読み取った値を使う。/tofunomics reload で start() をもう一度呼ぶと追従する。
 */
public class AssetRankingSyncTask {

    /** 起動直後の 1 回目までの待ち時間（サーバーが落ち着いてから送る） */
    private static final long FIRST_DELAY_TICKS = 20L * 60;
    private static final long TICKS_PER_MINUTE = 20L * 60;

    private final Plugin plugin;
    private final PlayerDAO playerDAO;
    private final AssetRankingSync sync;
    private final AtomicBoolean sending = new AtomicBoolean(false);

    private BukkitTask task;
    private AssetRankingSettings settings;

    public AssetRankingSyncTask(Plugin plugin, PlayerDAO playerDAO, StatsUpsertClient client) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.sync = new AssetRankingSync(client, plugin.getLogger());
    }

    /** 設定を読み直して始める（動いていれば止めてから始め直す） */
    public synchronized void start() {
        stop();
        AssetRankingSettings next = AssetRankingSettings.fromConfig(
                plugin.getConfig().getConfigurationSection("api.tohu_app"));
        if (!next.sameTarget(settings)) {
            sync.reset();
        }
        settings = next;

        if (!next.isActive()) {
            plugin.getLogger().info("[資産ランキング] 送信は無効です（api.tohu_app.enabled と api.tohu_app.asset_ranking.enabled の両方が true のときに送ります）");
            return;
        }
        long period = next.getIntervalMinutes() * TICKS_PER_MINUTE;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collectAndSend, FIRST_DELAY_TICKS, period);
        plugin.getLogger().info("[資産ランキング] " + next.getIntervalMinutes() + " 分ごとに銀行残高を送ります（送り先: "
                + next.getUpsertUrl() + " / world_id: " + next.getWorldId()
                + (next.getApiKey().isEmpty() ? " / キー未設定" : "") + "）");
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** メインスレッドで残高と名前を集め、通信は非同期に回す */
    private void collectAndSend() {
        if (!sending.compareAndSet(false, true)) {
            plugin.getLogger().info("[資産ランキング] 前回の送信が終わっていないため、今回は飛ばします");
            return;
        }
        final AssetRankingSettings current = settings;
        final List<AssetRankingSync.BalanceEntry> entries;
        try {
            Map<UUID, Double> balances = playerDAO.getAllBankBalances();
            entries = AssetRankingSync.toEntries(balances, uuid -> Bukkit.getOfflinePlayer(uuid).getName());
        } catch (Exception e) {
            sending.set(false);
            plugin.getLogger().warning("[資産ランキング] 銀行残高の読み取りに失敗しました: " + e.getMessage());
            return;
        }

        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    AssetRankingSync.Result result = sync.sync(entries, current);
                    if (result.sent() > 0) {
                        plugin.getLogger().info("[資産ランキング] 送信 " + result.sent() + " 人 / 成功 "
                                + result.succeeded() + " 人 / 失敗 " + result.failed() + " 人");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[資産ランキング] 送信中にエラーが発生しました: " + e);
                } finally {
                    sending.set(false);
                }
            });
        } catch (Exception e) {
            // プラグインの停止中などで非同期に回せなかったとき
            sending.set(false);
        }
    }
}
