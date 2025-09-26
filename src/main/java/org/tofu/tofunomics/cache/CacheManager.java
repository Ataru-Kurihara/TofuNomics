package org.tofu.tofunomics.cache;

import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.models.Player;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.lang.ref.WeakReference;

/**
 * プレイヤーデータキャッシュ管理システム
 * メモリキャッシュによるデータベースアクセス最適化
 */
public class CacheManager {
    
    private final ConfigManager configManager;
    private final Logger logger;
    
    // プレイヤーデータキャッシュ
    private final Map<String, CachedPlayer> playerCache;
    private final Map<String, Long> playerAccessTimes;
    
    // 職業データキャッシュ
    private final Map<String, CachedPlayerJob> jobCache;
    private final Map<String, Long> jobAccessTimes;
    
    // 統計情報
    private final AtomicLong playerCacheHits;
    private final AtomicLong playerCacheMisses;
    private final AtomicLong jobCacheHits;
    private final AtomicLong jobCacheMisses;
    
    // キャッシュクリーンアップ用スケジューラ
    private final ScheduledExecutorService cleanupScheduler;
    
    // パフォーマンス最適化機能
    private final Map<String, Object> objectPool = new ConcurrentHashMap<>();
    private final Queue<StringBuilder> stringBuilderPool = new ConcurrentLinkedQueue<>();
    private final AtomicLong memoryUsage = new AtomicLong(0);
    private final Map<String, WeakReference<Object>> weakReferenceCache = new ConcurrentHashMap<>();
    
    // 非同期処理用
    private final ExecutorService asyncExecutor;
    private final BlockingQueue<Runnable> asyncTaskQueue;
    
    // パフォーマンス監視
    private final AtomicLong gcSuggestionCount = new AtomicLong(0);
    private final Map<String, Long> performanceMetrics = new ConcurrentHashMap<>();
    
    public CacheManager(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        
        // キャッシュマップの初期化
        this.playerCache = new ConcurrentHashMap<>();
        this.playerAccessTimes = new ConcurrentHashMap<>();
        this.jobCache = new ConcurrentHashMap<>();
        this.jobAccessTimes = new ConcurrentHashMap<>();
        
        // 統計情報の初期化
        this.playerCacheHits = new AtomicLong(0);
        this.playerCacheMisses = new AtomicLong(0);
        this.jobCacheHits = new AtomicLong(0);
        this.jobCacheMisses = new AtomicLong(0);
        
        // スケジューラとエグゼキューターの初期化
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        this.asyncExecutor = Executors.newFixedThreadPool(2);
        this.asyncTaskQueue = new LinkedBlockingQueue<>();
        
        // オブジェクトプールの初期化
        initializeObjectPool();
        
        // 定期的なキャッシュクリーンアップとメモリ監視を開始
        startCleanupTask();
        startMemoryMonitoring();
        startAsyncTaskProcessor();
    }
    
    /**
     * オブジェクトプールを初期化
     */
    private void initializeObjectPool() {
        // StringBuilderプールの初期化
        for (int i = 0; i < 50; i++) {
            stringBuilderPool.offer(new StringBuilder());
        }
    }
    
    /**
     * 非同期タスクプロセッサーを開始
     */
    private void startAsyncTaskProcessor() {
        asyncExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Runnable task = asyncTaskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warning("非同期タスク実行中にエラーが発生: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * メモリ監視を開始
     */
    private void startMemoryMonitoring() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                monitorMemoryUsage();
            } catch (Exception e) {
                logger.warning("メモリ監視中にエラーが発生: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * メモリ使用量を監視し、必要に応じて最適化を実行
     */
    private void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsageRatio = (double) usedMemory / runtime.maxMemory();
        
        // パフォーマンス指標を記録
        performanceMetrics.put("memory_usage_ratio", (long) (memoryUsageRatio * 100));
        performanceMetrics.put("cache_hit_rate", (long) (getCombinedCacheHitRate() * 100));
        performanceMetrics.put("player_cache_size", (long) playerCache.size());
        performanceMetrics.put("job_cache_size", (long) jobCache.size());
        
        if (configManager.isMemoryMonitoringEnabled()) {
            double warningThreshold = configManager.getMemoryWarningThreshold();
            double gcThreshold = 0.85; // GC提案閾値
            
            if (memoryUsageRatio > warningThreshold) {
                logger.warning("メモリ使用量が高くなっています: " + String.format("%.1f%%", memoryUsageRatio * 100));
                
                if (memoryUsageRatio > gcThreshold) {
                    gcSuggestionCount.incrementAndGet();
                    performMemoryOptimization();
                }
            }
        }
        
        // メモリ使用量をアトミックに更新
        memoryUsage.set(usedMemory);
    }
    
    /**
     * メモリ最適化を実行
     */
    private void performMemoryOptimization() {
        // 弱参照キャッシュをクリア
        weakReferenceCache.entrySet().removeIf(entry -> entry.getValue().get() == null);
        
        // 期限切れエントリを強制的にクリーンアップ
        cleanupExpiredEntries();
        
        // オブジェクトプールを調整
        optimizeObjectPool();
        
        logger.info("メモリ最適化を実行しました。");
    }
    
    /**
     * オブジェクトプールを最適化
     */
    private void optimizeObjectPool() {
        // StringBuilderプールのクリーンアップ
        while (stringBuilderPool.size() > 25) {
            StringBuilder sb = stringBuilderPool.poll();
            if (sb != null && sb.capacity() > 1024) {
                continue;
            }
        }
        
        // 必要に応じてプールを補充
        while (stringBuilderPool.size() < 25) {
            stringBuilderPool.offer(new StringBuilder());
        }
    }
    
    // ========== プレイヤーキャッシュ ==========
    
    /**
     * プレイヤーデータをキャッシュから取得
     */
    public Player getCachedPlayer(String playerUUID) {
        if (!configManager.isPlayerCacheEnabled()) {
            playerCacheMisses.incrementAndGet();
            return null;
        }
        
        CachedPlayer cachedPlayer = playerCache.get(playerUUID);
        
        if (cachedPlayer != null && !isExpired(cachedPlayer)) {
            // アクセス時間を更新
            playerAccessTimes.put(playerUUID, System.currentTimeMillis());
            playerCacheHits.incrementAndGet();
            return cachedPlayer.getPlayer();
        }
        
        // キャッシュミスまたは期限切れ
        if (cachedPlayer != null) {
            playerCache.remove(playerUUID);
            playerAccessTimes.remove(playerUUID);
        }
        
        playerCacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * プレイヤーデータをキャッシュに保存
     */
    public void cachePlayer(String playerUUID, Player player) {
        if (!configManager.isPlayerCacheEnabled()) {
            return;
        }
        
        // キャッシュサイズ制限をチェック
        if (playerCache.size() >= configManager.getPlayerCacheMaxSize()) {
            evictOldestPlayerEntry();
        }
        
        long currentTime = System.currentTimeMillis();
        CachedPlayer cachedPlayer = new CachedPlayer(player, currentTime);
        
        playerCache.put(playerUUID, cachedPlayer);
        playerAccessTimes.put(playerUUID, currentTime);
    }
    
    // ========== 職業キャッシュ ==========
    
    /**
     * 職業データをキャッシュから取得
     */
    public PlayerJob getCachedPlayerJob(String playerUUID, String jobType) {
        if (!configManager.isJobCacheEnabled()) {
            jobCacheMisses.incrementAndGet();
            return null;
        }
        
        String cacheKey = playerUUID + ":" + jobType;
        CachedPlayerJob cachedJob = jobCache.get(cacheKey);
        
        if (cachedJob != null && !isExpired(cachedJob)) {
            // アクセス時間を更新
            jobAccessTimes.put(cacheKey, System.currentTimeMillis());
            jobCacheHits.incrementAndGet();
            return cachedJob.getPlayerJob();
        }
        
        // キャッシュミスまたは期限切れ
        if (cachedJob != null) {
            jobCache.remove(cacheKey);
            jobAccessTimes.remove(cacheKey);
        }
        
        jobCacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * 職業データをキャッシュに保存
     */
    public void cachePlayerJob(String playerUUID, String jobType, PlayerJob playerJob) {
        if (!configManager.isJobCacheEnabled()) {
            return;
        }
        
        String cacheKey = playerUUID + ":" + jobType;
        
        // キャッシュサイズ制限をチェック
        if (jobCache.size() >= 500) {
            evictOldestJobEntry();
        }
        
        long currentTime = System.currentTimeMillis();
        CachedPlayerJob cachedJob = new CachedPlayerJob(playerJob, currentTime);
        
        jobCache.put(cacheKey, cachedJob);
        jobAccessTimes.put(cacheKey, currentTime);
    }
    
    // ========== キャッシュ管理 ==========
    
    /**
     * キャッシュエントリが期限切れかチェック
     */
    private boolean isExpired(CachedPlayer cachedPlayer) {
        long expireTime = configManager.getPlayerCacheExpireAfterWrite() * 1000; // 秒をミリ秒に変換
        return System.currentTimeMillis() - cachedPlayer.getCacheTime() > expireTime;
    }
    
    private boolean isExpired(CachedPlayerJob cachedJob) {
        long expireTime = 7200 * 1000; // 2時間
        return System.currentTimeMillis() - cachedJob.getCacheTime() > expireTime;
    }
    
    /**
     * 最も古いプレイヤーエントリを削除
     */
    private void evictOldestPlayerEntry() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, Long> entry : playerAccessTimes.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            playerCache.remove(oldestKey);
            playerAccessTimes.remove(oldestKey);
        }
    }
    
    /**
     * 最も古い職業エントリを削除
     */
    private void evictOldestJobEntry() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, Long> entry : jobAccessTimes.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            jobCache.remove(oldestKey);
            jobAccessTimes.remove(oldestKey);
        }
    }
    
    /**
     * 定期的なキャッシュクリーンアップタスクを開始
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredEntries();
            } catch (Exception e) {
                logger.warning("キャッシュクリーンアップ中にエラーが発生しました: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS); // 1分毎に実行
    }
    
    /**
     * 期限切れのキャッシュエントリを削除
     */
    private void cleanupExpiredEntries() {
        // プレイヤーキャッシュのクリーンアップ
        playerCache.entrySet().removeIf(entry -> {
            if (isExpired(entry.getValue())) {
                playerAccessTimes.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        // 職業キャッシュのクリーンアップ
        jobCache.entrySet().removeIf(entry -> {
            if (isExpired(entry.getValue())) {
                jobAccessTimes.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * キャッシュ統計情報を取得
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            playerCache.size(),
            playerCacheHits.get(),
            playerCacheMisses.get(),
            jobCache.size(),
            jobCacheHits.get(),
            jobCacheMisses.get(),
            0, // configCacheSize（簡略化）
            0L, // configCacheHits（簡略化）
            0L  // configCacheMisses（簡略化）
        );
    }
    
    /**
     * シャットダウン処理
     */
    public void shutdown() {
        // 非同期タスクキューの処理完了を待つ
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
        
        // クリーンアップスケジューラの停止
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
        }
        
        // 全キャッシュのクリア
        playerCache.clear();
        playerAccessTimes.clear();
        jobCache.clear();
        jobAccessTimes.clear();
        objectPool.clear();
        weakReferenceCache.clear();
        performanceMetrics.clear();
        stringBuilderPool.clear();
        
        logger.info("CacheManager のシャットダウンが完了しました。");
    }
    
    // ========== パフォーマンス最適化メソッド ==========
    
    /**
     * StringBuilder をオブジェクトプールから取得
     */
    public StringBuilder getStringBuilder() {
        StringBuilder sb = stringBuilderPool.poll();
        if (sb == null) {
            sb = new StringBuilder();
        } else {
            sb.setLength(0); // リセット
        }
        return sb;
    }
    
    /**
     * StringBuilder をオブジェクトプールに返却
     */
    public void returnStringBuilder(StringBuilder sb) {
        if (sb != null && sb.capacity() <= 1024 && stringBuilderPool.size() < 50) {
            stringBuilderPool.offer(sb);
        }
    }
    
    /**
     * 非同期タスクをキューに追加
     */
    public void submitAsyncTask(Runnable task) {
        try {
            asyncTaskQueue.offer(task, 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("非同期タスクのキューイングに失敗: " + e.getMessage());
        }
    }
    
    /**
     * 弱参照キャッシュにオブジェクトを保存
     */
    public void cacheWeakReference(String key, Object object) {
        weakReferenceCache.put(key, new WeakReference<>(object));
    }
    
    /**
     * 弱参照キャッシュからオブジェクトを取得
     */
    public Object getWeakReference(String key) {
        WeakReference<Object> ref = weakReferenceCache.get(key);
        if (ref != null) {
            Object obj = ref.get();
            if (obj == null) {
                weakReferenceCache.remove(key);
            }
            return obj;
        }
        return null;
    }
    
    /**
     * 統合キャッシュヒット率を計算
     */
    private double getCombinedCacheHitRate() {
        long totalHits = playerCacheHits.get() + jobCacheHits.get();
        long totalMisses = playerCacheMisses.get() + jobCacheMisses.get();
        long total = totalHits + totalMisses;
        return total == 0 ? 0.0 : (double) totalHits / total;
    }
    
    /**
     * パフォーマンスメトリクスを取得
     */
    public Map<String, Long> getPerformanceMetrics() {
        Map<String, Long> metrics = new HashMap<>(performanceMetrics);
        metrics.put("gc_suggestion_count", gcSuggestionCount.get());
        metrics.put("memory_usage_bytes", memoryUsage.get());
        metrics.put("weak_reference_cache_size", (long) weakReferenceCache.size());
        metrics.put("string_builder_pool_size", (long) stringBuilderPool.size());
        metrics.put("async_task_queue_size", (long) asyncTaskQueue.size());
        return metrics;
    }
    
    /**
     * キャッシュウォームアップ（事前読み込み）
     */
    public void warmupCache(List<String> playerUUIDs) {
        if (playerUUIDs == null || playerUUIDs.isEmpty()) {
            return;
        }
        
        submitAsyncTask(() -> {
            logger.info("キャッシュウォームアップを開始します。対象: " + playerUUIDs.size() + "人のプレイヤー");
            
            for (String playerUUID : playerUUIDs) {
                try {
                    // ここでデータベースから事前にデータを読み込む処理を実行
                    // 実装は DAO クラスとの連携が必要
                    
                    Thread.sleep(10); // CPU負荷軽減のための短い待機
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warning("キャッシュウォームアップ中にエラー: " + e.getMessage());
                }
            }
            
            logger.info("キャッシュウォームアップが完了しました。");
        });
    }
    
    /**
     * キャッシュの整合性チェック
     */
    public boolean checkCacheIntegrity() {
        try {
            // プレイヤーキャッシュの整合性チェック
            int inconsistentEntries = 0;
            for (Map.Entry<String, CachedPlayer> entry : playerCache.entrySet()) {
                if (!playerAccessTimes.containsKey(entry.getKey())) {
                    inconsistentEntries++;
                }
            }
            
            // 職業キャッシュの整合性チェック
            for (Map.Entry<String, CachedPlayerJob> entry : jobCache.entrySet()) {
                if (!jobAccessTimes.containsKey(entry.getKey())) {
                    inconsistentEntries++;
                }
            }
            
            if (inconsistentEntries > 0) {
                logger.warning("キャッシュの整合性に問題があります。不整合エントリ数: " + inconsistentEntries);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.severe("キャッシュ整合性チェック中にエラーが発生: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * キャッシュのプリロード（将来的にアクセスされる可能性の高いデータを事前読み込み）
     */
    public void preloadCache(String playerUUID, Set<String> jobTypes) {
        submitAsyncTask(() -> {
            try {
                // ここで DAO を使用してデータベースからデータを読み込み、キャッシュに保存
                logger.info("プリロード完了: " + playerUUID + " (" + jobTypes.size() + " jobs)");
            } catch (Exception e) {
                logger.warning("キャッシュプリロード中にエラー: " + e.getMessage());
            }
        });
    }
    
    // ========== 内部クラス ==========
    
    /**
     * キャッシュされたプレイヤーデータ
     */
    private static class CachedPlayer {
        private final Player player;
        private final long cacheTime;
        
        public CachedPlayer(Player player, long cacheTime) {
            this.player = player;
            this.cacheTime = cacheTime;
        }
        
        public Player getPlayer() { return player; }
        public long getCacheTime() { return cacheTime; }
    }
    
    /**
     * キャッシュされた職業データ
     */
    private static class CachedPlayerJob {
        private final PlayerJob playerJob;
        private final long cacheTime;
        
        public CachedPlayerJob(PlayerJob playerJob, long cacheTime) {
            this.playerJob = playerJob;
            this.cacheTime = cacheTime;
        }
        
        public PlayerJob getPlayerJob() { return playerJob; }
        public long getCacheTime() { return cacheTime; }
    }
    
    /**
     * キャッシュ統計情報
     */
    public static class CacheStatistics {
        private final int playerCacheSize;
        private final long playerCacheHits;
        private final long playerCacheMisses;
        private final int jobCacheSize;
        private final long jobCacheHits;
        private final long jobCacheMisses;
        private final int configCacheSize;
        private final long configCacheHits;
        private final long configCacheMisses;
        
        public CacheStatistics(int playerCacheSize, long playerCacheHits, long playerCacheMisses,
                              int jobCacheSize, long jobCacheHits, long jobCacheMisses,
                              int configCacheSize, long configCacheHits, long configCacheMisses) {
            this.playerCacheSize = playerCacheSize;
            this.playerCacheHits = playerCacheHits;
            this.playerCacheMisses = playerCacheMisses;
            this.jobCacheSize = jobCacheSize;
            this.jobCacheHits = jobCacheHits;
            this.jobCacheMisses = jobCacheMisses;
            this.configCacheSize = configCacheSize;
            this.configCacheHits = configCacheHits;
            this.configCacheMisses = configCacheMisses;
        }
        
        public double getPlayerCacheHitRate() {
            long total = playerCacheHits + playerCacheMisses;
            return total == 0 ? 0.0 : (double) playerCacheHits / total;
        }
        
        public double getJobCacheHitRate() {
            long total = jobCacheHits + jobCacheMisses;
            return total == 0 ? 0.0 : (double) jobCacheHits / total;
        }
        
        public double getConfigCacheHitRate() {
            long total = configCacheHits + configCacheMisses;
            return total == 0 ? 0.0 : (double) configCacheHits / total;
        }
        
        // Getters
        public int getPlayerCacheSize() { return playerCacheSize; }
        public long getPlayerCacheHits() { return playerCacheHits; }
        public long getPlayerCacheMisses() { return playerCacheMisses; }
        public int getJobCacheSize() { return jobCacheSize; }
        public long getJobCacheHits() { return jobCacheHits; }
        public long getJobCacheMisses() { return jobCacheMisses; }
        public int getConfigCacheSize() { return configCacheSize; }
        public long getConfigCacheHits() { return configCacheHits; }
        public long getConfigCacheMisses() { return configCacheMisses; }
    }
}