package org.tofu.tofunomics.batch;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.database.HikariDatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 最適化されたバッチ処理システム
 * より効率的なバッチ処理とメモリ使用量削減を実現
 */
public class OptimizedBatchProcessor {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final HikariDatabaseManager databaseManager;
    private final Logger logger;
    
    // バッチ処理キュー（操作タイプ別）
    private final Map<BatchOperationType, BlockingQueue<BatchOperation>> batchQueues;
    
    // バッチ処理スケジューラ
    private final ScheduledExecutorService batchScheduler;
    private final ExecutorService batchExecutor;
    
    // オブジェクトプール
    private final ObjectPool<StringBuilder> stringBuilderPool;
    private final ObjectPool<HashMap<String, Object>> mapPool;
    
    // 統計情報
    private final AtomicInteger totalBatchesProcessed = new AtomicInteger(0);
    private final AtomicInteger totalOperationsProcessed = new AtomicInteger(0);
    private final AtomicLong totalBatchTime = new AtomicLong(0);
    private final AtomicInteger failedBatches = new AtomicInteger(0);
    
    // 設定値
    private volatile int batchSize;
    private volatile int batchTimeout;
    private volatile boolean batchProcessingEnabled;
    
    public OptimizedBatchProcessor(JavaPlugin plugin, ConfigManager configManager, 
                                 HikariDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
        
        // 設定値の初期化
        loadConfiguration();
        
        // バッチキューの初期化
        this.batchQueues = new ConcurrentHashMap<>();
        for (BatchOperationType type : BatchOperationType.values()) {
            this.batchQueues.put(type, new ArrayBlockingQueue<>(1000));
        }
        
        // スレッドプールの初期化
        this.batchScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "TofuNomics-BatchScheduler");
            thread.setDaemon(true);
            return thread;
        });
        
        this.batchExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "TofuNomics-BatchExecutor");
            thread.setDaemon(true);
            return thread;
        });
        
        // オブジェクトプールの初期化
        this.stringBuilderPool = new ObjectPool<>(StringBuilder::new, 50);
        this.mapPool = new ObjectPool<>(HashMap::new, 100);
        
        // バッチ処理タスクの開始
        startBatchProcessing();
    }
    
    /**
     * 設定値の読み込み
     */
    private void loadConfiguration() {
        this.batchProcessingEnabled = configManager.isBatchProcessingEnabled();
        this.batchSize = configManager.getBatchSize();
        this.batchTimeout = configManager.getBatchTimeout();
    }
    
    /**
     * バッチ処理タスクの開始
     */
    private void startBatchProcessing() {
        if (!batchProcessingEnabled) {
            logger.info("バッチ処理が無効化されています。");
            return;
        }
        
        // 各操作タイプに対してバッチ処理タスクを開始
        for (BatchOperationType type : BatchOperationType.values()) {
            batchScheduler.scheduleAtFixedRate(() -> {
                try {
                    processBatch(type);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "バッチ処理中にエラーが発生しました: " + type, e);
                }
            }, 1000, batchTimeout, TimeUnit.MILLISECONDS);
        }
        
        logger.info("最適化バッチ処理システムを開始しました。");
    }
    
    /**
     * バッチ操作をキューに追加
     */
    public void queueOperation(BatchOperationType type, String playerUUID, Object... parameters) {
        if (!batchProcessingEnabled) {
            // バッチ処理が無効の場合は即座に実行
            executeImmediately(type, playerUUID, parameters);
            return;
        }
        
        BlockingQueue<BatchOperation> queue = batchQueues.get(type);
        if (queue != null) {
            BatchOperation operation = new BatchOperation(playerUUID, parameters);
            
            if (!queue.offer(operation)) {
                logger.warning("バッチキューが満杯です。操作を即座に実行します: " + type);
                executeImmediately(type, playerUUID, parameters);
            }
        }
    }
    
    /**
     * 特定タイプのバッチ処理を実行
     */
    private void processBatch(BatchOperationType type) {
        BlockingQueue<BatchOperation> queue = batchQueues.get(type);
        if (queue.isEmpty()) {
            return;
        }
        
        List<BatchOperation> operations = new ArrayList<>();
        
        // バッチサイズ分の操作を収集
        queue.drainTo(operations, batchSize);
        
        if (operations.isEmpty()) {
            return;
        }
        
        // 非同期でバッチを実行
        batchExecutor.submit(() -> executeBatch(type, operations));
    }
    
    /**
     * バッチの実行
     */
    private void executeBatch(BatchOperationType type, List<BatchOperation> operations) {
        long startTime = System.currentTimeMillis();
        
        try {
            switch (type) {
                case BALANCE_UPDATE:
                    executeBatchBalanceUpdate(operations);
                    break;
                case EXPERIENCE_UPDATE:
                    executeBatchExperienceUpdate(operations);
                    break;
                case TRADE_HISTORY:
                    executeBatchTradeHistory(operations);
                    break;
                case PERFORMANCE_STATS:
                    executeBatchPerformanceStats(operations);
                    break;
                default:
                    logger.warning("未知のバッチ操作タイプ: " + type);
                    break;
            }
            
            // 統計更新
            totalBatchesProcessed.incrementAndGet();
            totalOperationsProcessed.addAndGet(operations.size());
            totalBatchTime.addAndGet(System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            failedBatches.incrementAndGet();
            logger.log(Level.SEVERE, "バッチ実行に失敗しました: " + type, e);
            
            // 失敗した操作を個別に再試行
            for (BatchOperation operation : operations) {
                try {
                    executeImmediately(type, operation.getPlayerUUID(), operation.getParameters());
                } catch (Exception retryException) {
                    logger.log(Level.SEVERE, "個別実行でも失敗しました", retryException);
                }
            }
        }
    }
    
    /**
     * バッチ残高更新の実行
     */
    private void executeBatchBalanceUpdate(List<BatchOperation> operations) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            String updateQuery = "UPDATE players SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                
                for (BatchOperation operation : operations) {
                    Object[] params = operation.getParameters();
                    if (params.length >= 1) {
                        pstmt.setDouble(1, (Double) params[0]);
                        pstmt.setString(2, operation.getPlayerUUID());
                        pstmt.addBatch();
                    }
                }
                
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                logger.fine(String.format("バッチ残高更新完了: %d操作", results.length));
            }
        }
    }
    
    /**
     * バッチ経験値更新の実行
     */
    private void executeBatchExperienceUpdate(List<BatchOperation> operations) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            String updateQuery = "UPDATE player_jobs " +
                                 "SET experience = experience + ?, last_used = CURRENT_TIMESTAMP " +
                                 "WHERE uuid = ? AND job_id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                
                for (BatchOperation operation : operations) {
                    Object[] params = operation.getParameters();
                    if (params.length >= 2) {
                        pstmt.setDouble(1, (Double) params[1]); // 経験値
                        pstmt.setString(2, operation.getPlayerUUID());
                        pstmt.setString(3, (String) params[0]); // 職業タイプ
                        pstmt.addBatch();
                    }
                }
                
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                logger.fine(String.format("バッチ経験値更新完了: %d操作", results.length));
            }
        }
    }
    
    /**
     * バッチ取引履歴記録の実行
     */
    private void executeBatchTradeHistory(List<BatchOperation> operations) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            String insertQuery = "INSERT INTO player_trade_history " +
                                 "(player_uuid, item_type, quantity, unit_price, total_amount, job_type, job_level, price_bonus) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                
                for (BatchOperation operation : operations) {
                    Object[] params = operation.getParameters();
                    if (params.length >= 7) {
                        pstmt.setString(1, operation.getPlayerUUID());
                        pstmt.setString(2, (String) params[0]);   // item_type
                        pstmt.setInt(3, (Integer) params[1]);     // quantity
                        pstmt.setDouble(4, (Double) params[2]);   // unit_price
                        pstmt.setDouble(5, (Double) params[3]);   // total_amount
                        pstmt.setString(6, (String) params[4]);   // job_type
                        pstmt.setInt(7, (Integer) params[5]);     // job_level
                        pstmt.setDouble(8, (Double) params[6]);   // price_bonus
                        pstmt.addBatch();
                    }
                }
                
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                logger.fine(String.format("バッチ取引履歴記録完了: %d操作", results.length));
            }
        }
    }
    
    /**
     * バッチパフォーマンス統計記録の実行
     */
    private void executeBatchPerformanceStats(List<BatchOperation> operations) throws SQLException {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            String insertQuery = "INSERT INTO performance_stats (stat_type, stat_value) VALUES (?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                
                for (BatchOperation operation : operations) {
                    Object[] params = operation.getParameters();
                    if (params.length >= 2) {
                        pstmt.setString(1, (String) params[0]);   // stat_type
                        pstmt.setDouble(2, (Double) params[1]);   // stat_value
                        pstmt.addBatch();
                    }
                }
                
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                logger.fine(String.format("バッチ統計記録完了: %d操作", results.length));
            }
        }
    }
    
    /**
     * 即座に操作を実行（バッチ処理無効時またはキューフル時）
     */
    private void executeImmediately(BatchOperationType type, String playerUUID, Object... parameters) {
        List<BatchOperation> singleOperation = Arrays.asList(new BatchOperation(playerUUID, parameters));
        executeBatch(type, singleOperation);
    }
    
    /**
     * 統計情報の取得
     */
    public BatchStatistics getStatistics() {
        // 各キューのサイズを取得
        int totalQueuedOperations = batchQueues.values().stream()
            .mapToInt(Queue::size)
            .sum();
        
        return new BatchStatistics(
            totalBatchesProcessed.get(),
            totalOperationsProcessed.get(),
            totalQueuedOperations,
            failedBatches.get(),
            totalBatchesProcessed.get() == 0 ? 0.0 : (double) totalBatchTime.get() / totalBatchesProcessed.get()
        );
    }
    
    /**
     * 設定の再読み込み
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("バッチ処理設定を再読み込みしました。");
    }
    
    /**
     * 未処理の操作を強制処理してシャットダウン
     */
    public void shutdown() {
        logger.info("バッチ処理システムをシャットダウン中...");
        
        // 全ての未処理操作をフラッシュ
        for (BatchOperationType type : BatchOperationType.values()) {
            processBatch(type);
        }
        
        // スレッドプールのシャットダウン
        batchScheduler.shutdown();
        batchExecutor.shutdown();
        
        try {
            if (!batchScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                batchScheduler.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchScheduler.shutdownNow();
            batchExecutor.shutdownNow();
        }
        
        // 最終統計を出力
        BatchStatistics finalStats = getStatistics();
        logger.info(String.format("バッチ処理最終統計: 処理済みバッチ%d, 総操作%d, 失敗%d, 平均時間%.2fms",
                finalStats.getTotalBatchesProcessed(),
                finalStats.getTotalOperationsProcessed(),
                finalStats.getFailedBatches(),
                finalStats.getAverageBatchTime()));
    }
    
    // ========== 内部クラス ==========
    
    /**
     * バッチ操作の種類
     */
    public enum BatchOperationType {
        BALANCE_UPDATE,      // 残高更新
        EXPERIENCE_UPDATE,   // 経験値更新
        TRADE_HISTORY,      // 取引履歴
        PERFORMANCE_STATS   // パフォーマンス統計
    }
    
    /**
     * バッチ操作データ
     */
    private static class BatchOperation {
        private final String playerUUID;
        private final Object[] parameters;
        
        public BatchOperation(String playerUUID, Object[] parameters) {
            this.playerUUID = playerUUID;
            this.parameters = parameters != null ? parameters : new Object[0];
        }
        
        public String getPlayerUUID() { return playerUUID; }
        public Object[] getParameters() { return parameters; }
    }
    
    /**
     * オブジェクトプール実装
     */
    private static class ObjectPool<T> {
        private final Queue<T> pool;
        private final java.util.function.Supplier<T> factory;
        private final int maxSize;
        
        public ObjectPool(java.util.function.Supplier<T> factory, int maxSize) {
            this.factory = factory;
            this.maxSize = maxSize;
            this.pool = new ConcurrentLinkedQueue<>();
            
            // 初期オブジェクトを生成
            for (int i = 0; i < Math.min(10, maxSize); i++) {
                pool.offer(factory.get());
            }
        }
        
        public T borrow() {
            T object = pool.poll();
            return object != null ? object : factory.get();
        }
        
        public void returnObject(T object) {
            if (pool.size() < maxSize) {
                // オブジェクトのリセット
                if (object instanceof StringBuilder) {
                    ((StringBuilder) object).setLength(0);
                } else if (object instanceof Map) {
                    ((Map<?, ?>) object).clear();
                }
                pool.offer(object);
            }
        }
    }
    
    /**
     * バッチ処理統計情報
     */
    public static class BatchStatistics {
        private final int totalBatchesProcessed;
        private final int totalOperationsProcessed;
        private final int currentQueuedOperations;
        private final int failedBatches;
        private final double averageBatchTime;
        
        public BatchStatistics(int totalBatchesProcessed, int totalOperationsProcessed,
                             int currentQueuedOperations, int failedBatches, double averageBatchTime) {
            this.totalBatchesProcessed = totalBatchesProcessed;
            this.totalOperationsProcessed = totalOperationsProcessed;
            this.currentQueuedOperations = currentQueuedOperations;
            this.failedBatches = failedBatches;
            this.averageBatchTime = averageBatchTime;
        }
        
        public double getBatchSuccessRate() {
            return totalBatchesProcessed == 0 ? 1.0 : 1.0 - ((double) failedBatches / totalBatchesProcessed);
        }
        
        public double getAverageOperationsPerBatch() {
            return totalBatchesProcessed == 0 ? 0.0 : (double) totalOperationsProcessed / totalBatchesProcessed;
        }
        
        // Getters
        public int getTotalBatchesProcessed() { return totalBatchesProcessed; }
        public int getTotalOperationsProcessed() { return totalOperationsProcessed; }
        public int getCurrentQueuedOperations() { return currentQueuedOperations; }
        public int getFailedBatches() { return failedBatches; }
        public double getAverageBatchTime() { return averageBatchTime; }
    }
}