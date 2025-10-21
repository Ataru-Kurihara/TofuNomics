package org.tofu.tofunomics.announcement;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * 時刻放送システム
 * Minecraft時間に基づいて定期的に時刻を放送する
 */
public class TimeAnnouncementSystem {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private BukkitTask announcementTask;
    
    // 前回放送した時刻（Minecraft時間の分単位）
    private int lastAnnouncedMinute = -1;
    
    // 特別メッセージを送信したかどうかのフラグ
    private boolean sentOpeningMessage = false;
    private boolean sentClosingWarningMessage = false;
    private boolean sentClosingMessage = false;
    
    public TimeAnnouncementSystem(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * 時刻放送システムを開始
     */
    public void start() {
        if (!configManager.isTimeAnnouncementEnabled()) {
            plugin.getLogger().info("時刻放送システムは無効化されています");
            return;
        }
        
        // 既存のタスクがあればキャンセル
        if (announcementTask != null) {
            announcementTask.cancel();
        }
        
        // 1秒ごとにチェック（20 ticks = 1秒）
        announcementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndAnnounce, 20L, 20L);
        
        plugin.getLogger().info("時刻放送システムを開始しました（間隔: " + configManager.getTimeAnnouncementInterval() + "分）");
    }
    
    /**
     * 時刻放送システムを停止
     */
    public void stop() {
        if (announcementTask != null) {
            announcementTask.cancel();
            announcementTask = null;
        }
        plugin.getLogger().info("時刻放送システムを停止しました");
    }
    
    /**
     * 時刻をチェックして必要に応じて放送
     */
    private void checkAndAnnounce() {
        // メインワールドを取得
        World world = Bukkit.getWorlds().get(0);
        if (world == null) {
            return;
        }
        
        // Minecraft時間を取得
        long worldTime = world.getTime();
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
        int currentMinute = (int) (((worldTime + 6000) % 1000) / 1000.0 * 60);
        
        // Minecraft時間の分単位（0-1439）
        int totalMinutes = currentHour * 60 + currentMinute;
        
        // 取引時間の特別メッセージ
        if (configManager.isAnnounceTradingHours() && configManager.isTradingHoursEnabled()) {
            int startHour = configManager.getTradingStartHour();
            int endHour = configManager.getTradingEndHour();
            
            // 開店メッセージ（6:00）
            if (currentHour == startHour && currentMinute < 1 && !sentOpeningMessage) {
                broadcastMessage(configManager.getTimeAnnouncementTradingOpenMessage());
                sentOpeningMessage = true;
                sentClosingWarningMessage = false;
                sentClosingMessage = false;
            }
            
            // 閉店警告メッセージ（21:00、閉店1時間前）
            if (currentHour == (endHour - 1) && currentMinute < 1 && !sentClosingWarningMessage) {
                broadcastMessage(configManager.getTimeAnnouncementTradingCloseWarningMessage());
                sentClosingWarningMessage = true;
            }
            
            // 閉店メッセージ（22:00）
            if (currentHour == endHour && currentMinute < 1 && !sentClosingMessage) {
                broadcastMessage(configManager.getTimeAnnouncementTradingCloseMessage());
                sentClosingMessage = true;
                sentOpeningMessage = false;
            }
        }
        
        // 定期放送
        int interval = configManager.getTimeAnnouncementInterval();
        if (interval > 0) {
            // 放送すべき分かどうかチェック（例：60分間隔なら0分、60分、120分...）
            if (totalMinutes % interval == 0 && totalMinutes != lastAnnouncedMinute) {
                announceTime(currentHour, currentMinute);
                lastAnnouncedMinute = totalMinutes;
            }
            
            // 次の分に移行したらリセット
            if (totalMinutes != lastAnnouncedMinute && totalMinutes % interval != 0) {
                // 何もしない（次の放送タイミングまで待機）
            }
        }
    }
    
    /**
     * 現在時刻を放送
     */
    private void announceTime(int hour, int minute) {
        String timeText = String.format("%02d:%02d", hour, minute);
        
        // 取引ステータスを取得
        String tradingStatus;
        if (configManager.isTradingHoursEnabled()) {
            int startHour = configManager.getTradingStartHour();
            int endHour = configManager.getTradingEndHour();
            boolean isWithinTradingHours;
            
            if (startHour <= endHour) {
                isWithinTradingHours = hour >= startHour && hour < endHour;
            } else {
                isWithinTradingHours = hour >= startHour || hour < endHour;
            }
            
            if (isWithinTradingHours) {
                tradingStatus = configManager.getTimeAnnouncementStatusOpen();
            } else {
                tradingStatus = configManager.getTimeAnnouncementStatusClosed();
            }
        } else {
            tradingStatus = configManager.getTimeAnnouncementStatusOpen();
        }
        
        // メッセージを作成
        String message = configManager.getTimeAnnouncementRegularMessage()
            .replace("%time%", timeText)
            .replace("%trading_status%", tradingStatus);
        
        broadcastMessage(message);
    }
    
    /**
     * 全プレイヤーにメッセージを放送
     */
    private void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
    
    /**
     * システムを再読み込み
     */
    public void reload() {
        stop();
        start();
    }
}
