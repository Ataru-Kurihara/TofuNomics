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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 取引営業時間をBossBarで常時表示するマネージャー。
 *
 * 営業中は緑バーで閉店までの残り時間、閉店中は赤バーで開店までの残り時間を表示する。
 * 営業時間の判定は {@code ScoreboardManager} と同じ {@code ConfigManager} の
 * 取引時間設定を再利用する。表示対象ワールドはスコアボードと同じ設定に合わせる。
 */
public class BossBarManager implements Listener {

    // 1ゲーム時間 = 1000tick = 50実秒
    private static final double REAL_SECONDS_PER_GAME_HOUR = 50.0;

    private final TofuNomics plugin;
    private final ConfigManager configManager;

    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private BukkitTask updateTask;

    public BossBarManager(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        startUpdateTask();
    }

    private void startUpdateTask() {
        // 1秒ごとに更新（カウントダウンを滑らかに表示）
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (shouldShow(player)) {
                        updateBar(player);
                    } else {
                        removeBar(player.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * このプレイヤーにBossBarを表示すべきか判定する。
     */
    private boolean shouldShow(Player player) {
        if (!configManager.isTradingBossBarEnabled()) {
            return false;
        }
        if (!configManager.isTradingHoursEnabled()) {
            return false;
        }
        return configManager.isScoreboardEnabledInWorld(player.getWorld().getName());
    }

    /**
     * プレイヤーのBossBarを現在の営業状況に合わせて更新する。
     */
    private void updateBar(Player player) {
        long worldTime = player.getWorld().getTime();
        double timeOfDay = (((worldTime + 6000) / 1000.0)) % 24.0;
        int startHour = configManager.getTradingStartHour();
        int endHour = configManager.getTradingEndHour();

        boolean open;
        double remainingGameHours;
        double windowHours;

        if (startHour <= endHour) {
            open = timeOfDay >= startHour && timeOfDay < endHour;
            if (open) {
                remainingGameHours = endHour - timeOfDay;
                windowHours = endHour - startHour;
            } else {
                remainingGameHours = (timeOfDay < startHour)
                    ? startHour - timeOfDay
                    : (24 - timeOfDay) + startHour;
                windowHours = 24 - (endHour - startHour);
            }
        } else {
            // 日をまたぐ営業時間（例: 22時〜翌6時）
            open = timeOfDay >= startHour || timeOfDay < endHour;
            if (open) {
                remainingGameHours = (timeOfDay >= startHour)
                    ? (24 - timeOfDay) + endHour
                    : endHour - timeOfDay;
                windowHours = (24 - startHour) + endHour;
            } else {
                remainingGameHours = startHour - timeOfDay;
                windowHours = startHour - endHour;
            }
        }

        long remainingRealSec = (long) (remainingGameHours * REAL_SECONDS_PER_GAME_HOUR);
        double progress = (windowHours > 0) ? clamp01(remainingGameHours / windowHours) : 1.0;

        BossBar bar = getOrCreate(player);
        if (open) {
            bar.setColor(BarColor.GREEN);
            bar.setTitle(color("&a&l💼 営業中 &f— 閉店まであと " + formatRemain(remainingRealSec)));
        } else {
            bar.setColor(BarColor.RED);
            bar.setTitle(color("&c&l🔒 閉店中 &f— 開店まであと " + formatRemain(remainingRealSec)));
        }
        bar.setProgress(progress);

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private BossBar getOrCreate(Player player) {
        return playerBars.computeIfAbsent(player.getUniqueId(),
            uuid -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
    }

    private void removeBar(UUID uuid) {
        BossBar bar = playerBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    private String formatRemain(long seconds) {
        if (seconds < 0) seconds = 0;
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes > 0) {
            return minutes + "分" + remSec + "秒";
        }
        return remSec + "秒";
    }

    private String color(String legacy) {
        return ChatColor.translateAlternateColorCodes('&', legacy);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBar(event.getPlayer().getUniqueId());
    }

    /**
     * 終了処理：タスク停止と全BossBarの除去。
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
