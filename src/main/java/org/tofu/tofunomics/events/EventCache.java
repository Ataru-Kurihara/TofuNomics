package org.tofu.tofunomics.events;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * イベントキャッシュシステム
 * 重複イベント処理を防ぎ、パフォーマンスを最適化
 */
public class EventCache {
    
    private final JavaPlugin plugin;
    
    // プレイヤーごとの最終イベント処理時刻を記録
    private final Map<String, Map<String, Long>> playerEventTimestamps;
    
    // 統計情報
    private final AtomicLong totalProcessedEvents;
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    
    // キャッシュクリーンアップタスク
    private BukkitRunnable cleanupTask;
    
    // キャッシュ有効期限（ミリ秒）
    private static final long CACHE_CLEANUP_INTERVAL = 60000; // 1分
    private static final long CACHE_EXPIRY_TIME = 300000; // 5分
    
    public EventCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerEventTimestamps = new ConcurrentHashMap<>();
        this.totalProcessedEvents = new AtomicLong(0);
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
        
        startCleanupTask();
    }
    
    /**
     * 最近処理されたイベントかチェック
     * @param player プレイヤー
     * @param eventType イベントタイプ
     * @param cooldownMs クールダウン時間（ミリ秒）
     * @return 最近処理された場合true
     */
    public boolean isRecentlyProcessed(Player player, String eventType, long cooldownMs) {
        String playerUUID = player.getUniqueId().toString();
        Map<String, Long> eventTimes = playerEventTimestamps.get(playerUUID);
        
        if (eventTimes == null) {
            cacheMisses.incrementAndGet();
            return false;
        }
        
        Long lastProcessedTime = eventTimes.get(eventType);
        if (lastProcessedTime == null) {
            cacheMisses.incrementAndGet();
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessedTime < cooldownMs) {
            cacheHits.incrementAndGet();
            return true;
        }
        
        cacheMisses.incrementAndGet();
        return false;
    }
    
    /**
     * イベントを処理済みとして記録
     * @param player プレイヤー
     * @param eventType イベントタイプ
     */
    public void markAsProcessed(Player player, String eventType) {
        String playerUUID = player.getUniqueId().toString();
        long currentTime = System.currentTimeMillis();
        
        playerEventTimestamps.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                            .put(eventType, currentTime);
        
        totalProcessedEvents.incrementAndGet();
    }
    
    /**
     * 特定のプレイヤーのキャッシュをクリア
     * @param player プレイヤー
     */
    public void clearPlayerCache(Player player) {
        String playerUUID = player.getUniqueId().toString();
        playerEventTimestamps.remove(playerUUID);
    }
    
    /**
     * 特定のイベントタイプのキャッシュをクリア
     * @param eventType イベントタイプ
     */
    public void clearEventTypeCache(String eventType) {
        for (Map<String, Long> eventTimes : playerEventTimestamps.values()) {
            eventTimes.remove(eventType);
        }
    }
    
    /**
     * 全てのキャッシュをクリア
     */
    public void clearAllCache() {
        playerEventTimestamps.clear();
    }
    
    /**
     * キャッシュクリーンアップタスクを開始
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCache();
            }
        };
        
        // 1分ごとにクリーンアップを実行
        cleanupTask.runTaskTimerAsynchronously(plugin, 
            CACHE_CLEANUP_INTERVAL / 50, // 初回実行までの遅延（tick）
            CACHE_CLEANUP_INTERVAL / 50  // 実行間隔（tick）
        );
    }
    
    /**
     * 期限切れのキャッシュエントリを削除
     */
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        int removedEntries = 0;
        
        // 各プレイヤーのキャッシュをチェック
        for (Map.Entry<String, Map<String, Long>> playerEntry : playerEventTimestamps.entrySet()) {
            Map<String, Long> eventTimes = playerEntry.getValue();
            
            // 期限切れのイベントエントリを削除
            eventTimes.entrySet().removeIf(entry -> {
                boolean expired = currentTime - entry.getValue() > CACHE_EXPIRY_TIME;
                if (expired) {
                    return true;
                }
                return false;
            });
            
            // イベントエントリが空になったプレイヤーエントリを削除
            if (eventTimes.isEmpty()) {
                playerEventTimestamps.remove(playerEntry.getKey());
                removedEntries++;
            }
        }
        
        if (removedEntries > 0) {
            plugin.getLogger().fine("EventCache cleanup: removed " + removedEntries + " expired entries");
        }
    }
    
    /**
     * クリーンアップ処理
     */
    public void cleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        clearAllCache();
    }
    
    // ========== 統計情報メソッド ==========
    
    /**
     * 処理されたイベントの総数を取得
     */
    public long getTotalProcessedEvents() {
        return totalProcessedEvents.get();
    }
    
    /**
     * キャッシュヒット数を取得
     */
    public long getCacheHits() {
        return cacheHits.get();
    }
    
    /**
     * キャッシュミス数を取得
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }
    
    /**
     * キャッシュヒット率を取得
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return (double) hits / total;
    }
    
    /**
     * 現在のキャッシュエントリ数を取得
     */
    public int getCacheSize() {
        int totalEntries = 0;
        for (Map<String, Long> eventTimes : playerEventTimestamps.values()) {
            totalEntries += eventTimes.size();
        }
        return totalEntries;
    }
    
    /**
     * アクティブなプレイヤー数を取得
     */
    public int getActivePlayerCount() {
        return playerEventTimestamps.size();
    }
    
    /**
     * キャッシュ統計を文字列で取得
     */
    public String getCacheStatistics() {
        return String.format(
            "EventCache Statistics: " +
            "Processed=%d, Hits=%d (%.2f%%), Misses=%d, " +
            "CacheSize=%d, ActivePlayers=%d",
            getTotalProcessedEvents(),
            getCacheHits(),
            getCacheHitRate() * 100,
            getCacheMisses(),
            getCacheSize(),
            getActivePlayerCount()
        );
    }
}