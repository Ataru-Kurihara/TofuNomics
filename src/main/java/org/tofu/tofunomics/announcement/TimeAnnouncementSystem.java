package org.tofu.tofunomics.announcement;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

/**
 * 時刻放送システム
 * Minecraft時間に基づいて営業時間関連のメッセージを放送する
 */
public class TimeAnnouncementSystem {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private BukkitTask announcementTask;
    
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
        
        plugin.getLogger().info("時刻放送システムを開始しました");
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
        // 営業時間の判定基準となるワールドを取得
        // ボスバー/スコアボードはプレイヤーがいる経済対象ワールド(tofuNomics)の時刻で
        // 営業状態を表示するため、告知も同じ経済対象ワールドの時刻を基準にする。
        // メインワールドを参照すると時刻がずれて営業状態表示が食い違う。
        World world = getAnnouncementWorld();
        if (world == null) {
            return;
        }
        
        // Minecraft時間を取得
        long worldTime = world.getTime();
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
        int currentMinute = (int) (((worldTime + 6000) % 1000) / 1000.0 * 60);
        
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
    }
    
    /**
     * 営業時間の判定基準となるワールドを取得する。
     * 経済対象ワールド(enabled_worlds)のうち実在する最初のワールドを返す。
     * 見つからない場合はサーバーのメインワールドにフォールバックする。
     */
    private World getAnnouncementWorld() {
        for (String worldName : configManager.getEconomyEnabledWorlds()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /**
     * 対象ワールドのプレイヤーにメッセージを放送
     * 対象外ワールドのプレイヤーには送信しない
     */
    private void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!configManager.isEconomyEnabledInWorld(player.getWorld().getName())) {
                continue;
            }
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
