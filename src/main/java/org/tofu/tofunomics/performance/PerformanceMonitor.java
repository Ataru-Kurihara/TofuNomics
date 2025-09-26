package org.tofu.tofunomics.performance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tofu.tofunomics.batch.OptimizedBatchProcessor;
import org.tofu.tofunomics.cache.CacheManager;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.database.HikariDatabaseManager;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * パフォーマンス監視システム
 * CPU、メモリ、TPS、データベース、キャッシュのリアルタイム監視
 */
public class PerformanceMonitor {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    
    // 監視対象システム
    private final HikariDatabaseManager databaseManager;
    private final CacheManager cacheManager;
    private final OptimizedBatchProcessor batchProcessor;
    
    // システム情報取得用
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    // TPS監視用
    private final TpsCalculator tpsCalculator;
    
    // 監視スケジューラ
    private final ScheduledExecutorService monitorScheduler;
    
    // 統計データ
    private final ConcurrentHashMap<String, PerformanceMetric> metrics;
    
    // アラート機能
    private final AlertManager alertManager;
    
    // 監視設定
    private volatile boolean statisticsEnabled;
    private volatile boolean realtimeMonitoringEnabled;
    private volatile int collectionInterval;
    private volatile double cpuThreshold;
    private volatile double memoryThreshold;
    private volatile double tpsThreshold;
    
    public PerformanceMonitor(JavaPlugin plugin, ConfigManager configManager,
                            HikariDatabaseManager databaseManager, CacheManager cacheManager,
                            OptimizedBatchProcessor batchProcessor) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
        this.databaseManager = databaseManager;
        this.cacheManager = cacheManager;
        this.batchProcessor = batchProcessor;
        
        // システム情報取得
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        
        // TPS計算機
        this.tpsCalculator = new TpsCalculator();
        
        // メトリクス保存
        this.metrics = new ConcurrentHashMap<>();
        
        // アラートマネージャ
        this.alertManager = new AlertManager(plugin, configManager);
        
        // スケジューラ
        this.monitorScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "TofuNomics-PerformanceMonitor");
            thread.setDaemon(true);
            return thread;
        });
        
        // 設定読み込み
        loadConfiguration();
        
        // 監視開始
        startMonitoring();
    }
    
    /**
     * 設定読み込み
     */
    private void loadConfiguration() {
        this.statisticsEnabled = configManager.isStatisticsEnabled();
        this.realtimeMonitoringEnabled = true; // 基本的には常時有効
        this.collectionInterval = configManager.getStatisticsCollectionInterval();
        this.cpuThreshold = 80.0; // 設定から取得するように後で修正
        this.memoryThreshold = configManager.getMemoryWarningThreshold() * 100;
        this.tpsThreshold = configManager.getTpsWarningThreshold();
    }
    
    /**
     * 監視開始
     */
    private void startMonitoring() {
        if (!statisticsEnabled) {
            logger.info("パフォーマンス統計が無効化されています。");
            return;
        }
        
        // TPS監視を開始（1秒間隔）
        tpsCalculator.startMonitoring();
        
        // メトリクス収集タスク（設定間隔で実行）
        monitorScheduler.scheduleAtFixedRate(this::collectMetrics, 
                10, collectionInterval, TimeUnit.SECONDS);
        
        // リアルタイム監視タスク（30秒間隔）
        if (realtimeMonitoringEnabled) {
            monitorScheduler.scheduleAtFixedRate(this::performRealtimeChecks, 
                    30, 30, TimeUnit.SECONDS);
        }
        
        logger.info("パフォーマンス監視システムを開始しました。");
    }
    
    /**
     * メトリクス収集
     */
    private void collectMetrics() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // システムメトリクス
            collectSystemMetrics(timestamp);
            
            // Minecraftメトリクス
            collectMinecraftMetrics(timestamp);
            
            // データベースメトリクス
            collectDatabaseMetrics(timestamp);
            
            // キャッシュメトリクス
            collectCacheMetrics(timestamp);
            
            // バッチ処理メトリクス
            collectBatchMetrics(timestamp);
            
            // 古いメトリクスを削除（メモリ使用量を制限）
            cleanupOldMetrics(timestamp);
            
        } catch (Exception e) {
            logger.warning("メトリクス収集中にエラーが発生しました: " + e.getMessage());
        }
    }
    
    /**
     * システムメトリクス収集
     */
    private void collectSystemMetrics(long timestamp) {
        // CPU使用率（Java8では別の方法で取得）
        try {
            // 利用可能なプロセッサ数
            int availableProcessors = osBean.getAvailableProcessors();
            addMetric("system.available_processors", availableProcessors, timestamp);
            
            // システム負荷平均（Unix系のみ）
            double systemLoadAverage = osBean.getSystemLoadAverage();
            if (systemLoadAverage >= 0) {
                addMetric("system.load_average", systemLoadAverage, timestamp);
                // 負荷を百分率で表現
                double loadPercentage = availableProcessors > 0 ? (systemLoadAverage / availableProcessors) * 100 : 0;
                addMetric("system.cpu_usage", Math.min(loadPercentage, 100), timestamp);
            }
        } catch (Exception e) {
            // CPU情報取得失敗時はスキップ
        }
        
        // メモリ使用率
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsage = maxMemory > 0 ? ((double) usedMemory / maxMemory) * 100 : 0;
        addMetric("system.memory_usage", memoryUsage, timestamp);
        addMetric("system.memory_used", usedMemory / (1024 * 1024), timestamp); // MB
        addMetric("system.memory_max", maxMemory / (1024 * 1024), timestamp); // MB
        
        // GC統計
        addMetric("system.gc_collections", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gc -> gc.getCollectionCount()).sum(), timestamp);
        addMetric("system.gc_time", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(gc -> gc.getCollectionTime()).sum(), timestamp);
    }
    
    /**
     * Minecraftメトリクス収集
     */
    private void collectMinecraftMetrics(long timestamp) {
        // TPS
        double tps = tpsCalculator.getTPS();
        addMetric("minecraft.tps", tps, timestamp);
        
        // プレイヤー数
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        addMetric("minecraft.players_online", onlinePlayers, timestamp);
        
        // チャンク数
        int totalChunks = Bukkit.getWorlds().stream()
                .mapToInt(world -> world.getLoadedChunks().length)
                .sum();
        addMetric("minecraft.loaded_chunks", totalChunks, timestamp);
        
        // エンティティ数
        int totalEntities = Bukkit.getWorlds().stream()
                .mapToInt(world -> world.getEntities().size())
                .sum();
        addMetric("minecraft.total_entities", totalEntities, timestamp);
    }
    
    /**
     * データベースメトリクス収集
     */
    private void collectDatabaseMetrics(long timestamp) {
        if (databaseManager != null && databaseManager.isInitialized()) {
            HikariDatabaseManager.DatabaseStatistics dbStats = databaseManager.getStatistics();
            
            addMetric("database.active_connections", dbStats.getActiveConnections(), timestamp);
            addMetric("database.total_queries", dbStats.getTotalQueries(), timestamp);
            addMetric("database.failed_queries", dbStats.getFailedQueries(), timestamp);
            addMetric("database.average_query_time", dbStats.getAverageQueryTime(), timestamp);
            addMetric("database.success_rate", dbStats.getSuccessRate() * 100, timestamp);
        }
    }
    
    /**
     * キャッシュメトリクス収集
     */
    private void collectCacheMetrics(long timestamp) {
        if (cacheManager != null) {
            CacheManager.CacheStatistics cacheStats = cacheManager.getStatistics();
            
            addMetric("cache.player_cache_size", cacheStats.getPlayerCacheSize(), timestamp);
            addMetric("cache.player_hit_rate", cacheStats.getPlayerCacheHitRate() * 100, timestamp);
            addMetric("cache.job_cache_size", cacheStats.getJobCacheSize(), timestamp);
            addMetric("cache.job_hit_rate", cacheStats.getJobCacheHitRate() * 100, timestamp);
            addMetric("cache.config_cache_size", cacheStats.getConfigCacheSize(), timestamp);
            addMetric("cache.config_hit_rate", cacheStats.getConfigCacheHitRate() * 100, timestamp);
        }
    }
    
    /**
     * バッチ処理メトリクス収集
     */
    private void collectBatchMetrics(long timestamp) {
        if (batchProcessor != null) {
            OptimizedBatchProcessor.BatchStatistics batchStats = batchProcessor.getStatistics();
            
            addMetric("batch.processed_batches", batchStats.getTotalBatchesProcessed(), timestamp);
            addMetric("batch.processed_operations", batchStats.getTotalOperationsProcessed(), timestamp);
            addMetric("batch.queued_operations", batchStats.getCurrentQueuedOperations(), timestamp);
            addMetric("batch.failed_batches", batchStats.getFailedBatches(), timestamp);
            addMetric("batch.success_rate", batchStats.getBatchSuccessRate() * 100, timestamp);
            addMetric("batch.average_batch_time", batchStats.getAverageBatchTime(), timestamp);
        }
    }
    
    /**
     * リアルタイムチェック
     */
    private void performRealtimeChecks() {
        try {
            // CPU使用率チェック
            PerformanceMetric cpuMetric = metrics.get("system.cpu_usage");
            if (cpuMetric != null && cpuMetric.getValue() > cpuThreshold) {
                alertManager.sendAlert(AlertLevel.WARNING, 
                        "CPU使用率が高いです", 
                        String.format("現在のCPU使用率: %.1f%% (閾値: %.1f%%)", cpuMetric.getValue(), cpuThreshold));
            }
            
            // メモリ使用率チェック
            PerformanceMetric memoryMetric = metrics.get("system.memory_usage");
            if (memoryMetric != null && memoryMetric.getValue() > memoryThreshold) {
                alertManager.sendAlert(AlertLevel.WARNING, 
                        "メモリ使用率が高いです", 
                        String.format("現在のメモリ使用率: %.1f%% (閾値: %.1f%%)", memoryMetric.getValue(), memoryThreshold));
            }
            
            // TPS低下チェック
            PerformanceMetric tpsMetric = metrics.get("minecraft.tps");
            if (tpsMetric != null && tpsMetric.getValue() < tpsThreshold) {
                alertManager.sendAlert(AlertLevel.CRITICAL, 
                        "TPS低下が発生しています", 
                        String.format("現在のTPS: %.1f (閾値: %.1f)", tpsMetric.getValue(), tpsThreshold));
            }
            
            // データベース失敗率チェック
            PerformanceMetric dbSuccessRate = metrics.get("database.success_rate");
            if (dbSuccessRate != null && dbSuccessRate.getValue() < 95.0) {
                alertManager.sendAlert(AlertLevel.WARNING, 
                        "データベースエラー率が高いです", 
                        String.format("成功率: %.1f%%", dbSuccessRate.getValue()));
            }
            
        } catch (Exception e) {
            logger.warning("リアルタイムチェック中にエラーが発生しました: " + e.getMessage());
        }
    }
    
    /**
     * メトリクス追加
     */
    private void addMetric(String name, double value, long timestamp) {
        metrics.put(name, new PerformanceMetric(name, value, timestamp));
    }
    
    /**
     * 古いメトリクスをクリーンアップ
     */
    private void cleanupOldMetrics(long currentTimestamp) {
        long retentionTime = 7 * 24 * 60 * 60 * 1000; // 7日間
        long cutoffTime = currentTimestamp - retentionTime;
        
        metrics.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < cutoffTime);
    }
    
    /**
     * パフォーマンスレポートの生成
     */
    public PerformanceReport generateReport() {
        return new PerformanceReport(
            metrics.get("system.cpu_usage"),
            metrics.get("system.memory_usage"),
            metrics.get("minecraft.tps"),
            metrics.get("minecraft.players_online"),
            databaseManager != null ? databaseManager.getStatistics() : null,
            cacheManager != null ? cacheManager.getStatistics() : null,
            batchProcessor != null ? batchProcessor.getStatistics() : null
        );
    }
    
    /**
     * 統計データをデータベースに永続化
     */
    public void persistStatistics() {
        // バッチ処理を使用して統計データを保存
        for (PerformanceMetric metric : metrics.values()) {
            batchProcessor.queueOperation(
                OptimizedBatchProcessor.BatchOperationType.PERFORMANCE_STATS,
                "system", // プレイヤーUUIDの代わりにsystemを使用
                metric.getName(),
                metric.getValue()
            );
        }
    }
    
    /**
     * 設定再読み込み
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("パフォーマンス監視設定を再読み込みしました。");
    }
    
    /**
     * シャットダウン処理
     */
    public void shutdown() {
        // 統計データを永続化
        persistStatistics();
        
        // TPS計算を停止
        tpsCalculator.stopMonitoring();
        
        // スケジューラを停止
        monitorScheduler.shutdown();
        try {
            if (!monitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorScheduler.shutdownNow();
        }
        
        logger.info("パフォーマンス監視システムをシャットダウンしました。");
    }
    
    // ========== 内部クラス ==========
    
    /**
     * TPS計算機
     */
    private class TpsCalculator {
        private final AtomicLong tickCount = new AtomicLong(0);
        private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        private volatile double currentTPS = 20.0;
        private BukkitRunnable tpsTask;
        
        public void startMonitoring() {
            tpsTask = new BukkitRunnable() {
                private long lastCheck = System.currentTimeMillis();
                private long lastTickCount = 0;
                
                @Override
                public void run() {
                    long currentTime = System.currentTimeMillis();
                    long currentTicks = tickCount.incrementAndGet();
                    
                    if (currentTime - lastCheck >= 1000) { // 1秒間隔で計算
                        long timeDiff = currentTime - lastCheck;
                        long tickDiff = currentTicks - lastTickCount;
                        
                        currentTPS = (tickDiff * 1000.0) / timeDiff;
                        currentTPS = Math.min(currentTPS, 20.0); // 最大20TPS
                        
                        lastCheck = currentTime;
                        lastTickCount = currentTicks;
                    }
                }
            };
            
            tpsTask.runTaskTimer(plugin, 0L, 1L);
        }
        
        public void stopMonitoring() {
            if (tpsTask != null) {
                tpsTask.cancel();
            }
        }
        
        public double getTPS() {
            return currentTPS;
        }
    }
    
    /**
     * アラートマネージャー
     */
    private static class AlertManager {
        private final JavaPlugin plugin;
        private final ConfigManager configManager;
        
        public AlertManager(JavaPlugin plugin, ConfigManager configManager) {
            this.plugin = plugin;
            this.configManager = configManager;
        }
        
        public void sendAlert(AlertLevel level, String title, String message) {
            // ログに記録
            switch (level) {
                case WARNING:
                    plugin.getLogger().warning("[ALERT] " + title + ": " + message);
                    break;
                case CRITICAL:
                    plugin.getLogger().severe("[CRITICAL ALERT] " + title + ": " + message);
                    break;
                case INFO:
                default:
                    plugin.getLogger().info("[ALERT] " + title + ": " + message);
                    break;
            }
            
            // 管理者プレイヤーに通知
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("tofunomics.admin.alerts")) {
                    String alertMessage = String.format("§c[%s] §f%s: %s", level, title, message);
                    player.sendMessage(alertMessage);
                }
            }
        }
    }
    
    /**
     * アラートレベル
     */
    public enum AlertLevel {
        INFO, WARNING, CRITICAL
    }
    
    /**
     * パフォーマンスメトリクス
     */
    public static class PerformanceMetric {
        private final String name;
        private final double value;
        private final long timestamp;
        
        public PerformanceMetric(String name, double value, long timestamp) {
            this.name = name;
            this.value = value;
            this.timestamp = timestamp;
        }
        
        public String getName() { return name; }
        public double getValue() { return value; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * パフォーマンスレポート
     */
    public static class PerformanceReport {
        private final PerformanceMetric cpuUsage;
        private final PerformanceMetric memoryUsage;
        private final PerformanceMetric tps;
        private final PerformanceMetric onlinePlayers;
        private final HikariDatabaseManager.DatabaseStatistics databaseStats;
        private final CacheManager.CacheStatistics cacheStats;
        private final OptimizedBatchProcessor.BatchStatistics batchStats;
        
        public PerformanceReport(PerformanceMetric cpuUsage, PerformanceMetric memoryUsage,
                               PerformanceMetric tps, PerformanceMetric onlinePlayers,
                               HikariDatabaseManager.DatabaseStatistics databaseStats,
                               CacheManager.CacheStatistics cacheStats,
                               OptimizedBatchProcessor.BatchStatistics batchStats) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.tps = tps;
            this.onlinePlayers = onlinePlayers;
            this.databaseStats = databaseStats;
            this.cacheStats = cacheStats;
            this.batchStats = batchStats;
        }
        
        // Getters
        public PerformanceMetric getCpuUsage() { return cpuUsage; }
        public PerformanceMetric getMemoryUsage() { return memoryUsage; }
        public PerformanceMetric getTps() { return tps; }
        public PerformanceMetric getOnlinePlayers() { return onlinePlayers; }
        public HikariDatabaseManager.DatabaseStatistics getDatabaseStats() { return databaseStats; }
        public CacheManager.CacheStatistics getCacheStats() { return cacheStats; }
        public OptimizedBatchProcessor.BatchStatistics getBatchStats() { return batchStats; }
    }
}