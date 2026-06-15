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
    private BukkitTask updateTask;

    public JobLevelBossBarManager(TofuNomics plugin, ConfigManager configManager, JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        startUpdateTask();
    }

    private void startUpdateTask() {
        int updateInterval = Math.max(1, configManager.getScoreboardUpdateInterval());
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    refresh(player);
                }
            }
        }.runTaskTimer(plugin, 0L, updateInterval * 20L);
    }

    /**
     * 表示条件を満たせば BossBar を更新し、満たさなければ除去する（即時反映にも利用）。
     */
    public void refresh(Player player) {
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
        return jobManager.getCurrentJob(player.getUniqueId()) != null;
    }

    /**
     * 現在の職業レベル・進捗に合わせて BossBar を更新する。
     */
    private void updateBar(Player player) {
        try {
            PlayerJob currentJob = jobManager.getCurrentJob(player.getUniqueId());
            if (currentJob == null) {
                removeBar(player.getUniqueId());
                return;
            }

            Job jobData = jobManager.getJobById(currentJob.getJobId());
            String jobName = (jobData != null)
                    ? configManager.getJobDisplayName(jobData.getName())
                    : "職業";

            // 進捗率（0.0〜1.0）を計算（旧 ScoreboardManager.updateExperienceBar と同一ロジック）
            double currentExp = currentJob.getExperience();
            double prevLevelExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel());
            double requiredExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel() + 1);

            float progress;
            boolean maxLevel = currentJob.getLevel() >= configManager.getMaxJobLevel() || requiredExp <= prevLevelExp;
            if (maxLevel) {
                progress = 1.0f;
            } else {
                progress = (float) ((currentExp - prevLevelExp) / (requiredExp - prevLevelExp));
            }
            progress = Math.max(0.0f, Math.min(1.0f, progress));

            int percent = (int) Math.floor(progress * 100);
            String title = configManager.getJobBossBarTitleFormat()
                    .replace("%job%", jobName)
                    .replace("%level%", String.valueOf(currentJob.getLevel()))
                    .replace("%percent%", maxLevel ? "MAX" : String.valueOf(percent));

            BossBar bar = getOrCreate(player);
            bar.setColor(parseColor(configManager.getJobBossBarColor()));
            bar.setTitle(ChatColor.translateAlternateColorCodes('&', title));
            bar.setProgress(progress);
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
