package org.tofu.tofunomics.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.food.FoodBuffManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 現在の職業レベルと次レベルまでの進捗を BossBar で常時表示するマネージャー。
 *
 * 従来は {@code ScoreboardManager} がバニラ経験値バー（setLevel/setExp）に職業レベルを
 * 上書きしていたが、それだとプレイヤーのバニラ経験値が使えなくなる。本マネージャーへ
 * 移設することで XP バーを解放し、バニラ経験値を本来通り利用できるようにする。
 *
 * 取引営業時間の {@link BossBarManager} と同じ構造（プレイヤー毎の BossBar を保持し、
 * 定期タスクで更新／対象外では除去）で実装している。
 */
public class JobLevelBossBarManager implements Listener {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;

    private final Map<UUID, BossBar> playerBars = new HashMap<>();

    // 職業・レベル・経験値は DB から読むので、数秒のあいだ使い回す（以前は毎秒 2 回読んでいた）。
    // 経験値を得たときは refresh() が呼ばれ、そこで捨てて読み直す。
    private final TimedCache<UUID, PlayerStatusSnapshot> statusCache =
            new TimedCache<>(ScoreboardManager.STATUS_CACHE_MILLIS);
    private BukkitTask updateTask;

    // 食事バフ表示用（初期化順序の都合で後から setter 注入。未注入でも動作する）
    private FoodBuffManager foodBuffManager;

    public JobLevelBossBarManager(TofuNomics plugin, ConfigManager configManager, JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        startUpdateTask();
    }

    /**
     * 食事バフ表示のため FoodBuffManager を注入する（任意）。
     */
    public void setFoodBuffManager(FoodBuffManager foodBuffManager) {
        this.foodBuffManager = foodBuffManager;
    }

    private void startUpdateTask() {
        int updateInterval = Math.max(1, configManager.getScoreboardUpdateInterval());
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    refreshFromCache(player);
                }
            }
        }.runTaskTimer(plugin, 0L, updateInterval * 20L);
    }

    /**
     * 値が変わったとき（経験値の獲得など）に呼ぶ。DB から読み直して、すぐに表示へ反映する。
     * スコアボード側の写しも捨てるので、次の更新でスコアボードにも反映される。
     */
    public void refresh(Player player) {
        statusCache.invalidate(player.getUniqueId());
        ScoreboardManager scoreboardManager = plugin.getScoreboardManager();
        if (scoreboardManager != null) {
            scoreboardManager.invalidateStatus(player.getUniqueId());
        }
        refreshFromCache(player);
    }

    /**
     * 表示条件を満たせば BossBar を更新し、満たさなければ除去する（定期更新用。DB は数秒に 1 回だけ読む）。
     */
    private void refreshFromCache(Player player) {
        if (shouldShow(player)) {
            updateBar(player);
        } else {
            removeBar(player.getUniqueId());
        }
    }

    /**
     * このプレイヤーに職業レベル BossBar を表示すべきか判定する。
     */
    private boolean shouldShow(Player player) {
        if (!configManager.isJobBossBarEnabled()) {
            return false;
        }
        if (!configManager.isScoreboardEnabledInWorld(player.getWorld().getName())) {
            return false;
        }
        PlayerStatusSnapshot status = status(player);
        return status != null && status.hasJob;
    }

    private PlayerStatusSnapshot status(Player player) {
        UUID uuid = player.getUniqueId();
        return statusCache.get(uuid, () -> PlayerStatusSnapshot.load(uuid, jobManager, configManager, 0));
    }

    /**
     * 現在の職業レベル・進捗に合わせて BossBar を更新する。
     */
    private void updateBar(Player player) {
        try {
            PlayerStatusSnapshot status = status(player);
            if (status == null || !status.hasJob) {
                removeBar(player.getUniqueId());
                return;
            }

            // config キーには内部職業名(status.jobName)を渡すこと(表示名 jobName 変数は不可)。
            String jobName = configManager.getJobDisplayName(status.jobName);

            // 進捗率（0.0〜1.0）を計算（ScoreboardManager と共通ヘルパーを使用）
            // レベル上限は職業別設定(既定75)を使用。
            float progress;
            boolean maxLevel = status.isMaxLevel();
            if (maxLevel) {
                progress = 1.0f;
            } else {
                progress = (float) (PlayerJob.calculateLevelProgressPercent(
                        status.level, status.experience) / 100.0);
            }

            int percent = (int) Math.floor(progress * 100);
            String title = configManager.getJobBossBarTitleFormat()
                    .replace("%job%", jobName)
                    .replace("%level%", String.valueOf(status.level))
                    .replace("%percent%", maxLevel ? "MAX" : String.valueOf(percent));

            // 食事バフが現在の職業にマッチして有効ならタイトル末尾に表示する。
            // getActiveBuffInfo には内部職業名（status.jobName）を渡す。
            // 表示名の jobName 変数を渡すと常に非マッチになり suffix が出ないため注意。
            if (foodBuffManager != null) {
                FoodBuffManager.ActiveBuffInfo buffInfo =
                        foodBuffManager.getActiveBuffInfo(player, status.jobName);
                if (buffInfo != null) {
                    String suffix = configManager.getJobBossBarFoodBuffSuffix()
                            .replace("%percent%", String.valueOf(buffInfo.getBonusPercent()))
                            .replace("%seconds%", String.valueOf(buffInfo.getRemainingSeconds()));
                    title = title + suffix;
                }
            }

            BossBar bar = getOrCreate(player);
            // 変わったときだけ書き換える（毎秒の無駄な送信を避ける）
            BarColor color = parseColor(configManager.getJobBossBarColor());
            if (bar.getColor() != color) {
                bar.setColor(color);
            }
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            if (!coloredTitle.equals(bar.getTitle())) {
                bar.setTitle(coloredTitle);
            }
            if (bar.getProgress() != progress) {
                bar.setProgress(progress);
            }
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("職業レベルBossBarの更新に失敗しました ("
                    + player.getName() + "): " + e.getMessage());
        }
    }

    private BarColor parseColor(String name) {
        if (name == null) {
            return BarColor.GREEN;
        }
        try {
            return BarColor.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.GREEN;
        }
    }

    private BossBar getOrCreate(Player player) {
        return playerBars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
    }

    private void removeBar(UUID uuid) {
        BossBar bar = playerBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBar(event.getPlayer().getUniqueId());
        statusCache.invalidate(event.getPlayer().getUniqueId());
    }

    /**
     * 終了処理：タスク停止と全 BossBar の除去。
     */
    public void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        for (BossBar bar : playerBars.values()) {
            bar.removeAll();
        }
        playerBars.clear();
    }
}
