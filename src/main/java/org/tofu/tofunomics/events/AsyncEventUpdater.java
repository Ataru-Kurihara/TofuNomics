package org.tofu.tofunomics.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.experience.JobExperienceManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.sql.SQLException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 非同期更新処理システム
 * データベース更新を非同期で実行し、メインスレッドのパフォーマンスを維持
 */
public class AsyncEventUpdater {
    
    private final JavaPlugin plugin;
    private final PlayerDAO playerDAO;
    private final PlayerJobDAO playerJobDAO;
    private final JobExperienceManager jobExperienceManager;
    private final Logger logger;
    
    // 非同期処理用のExecutorService
    private final ExecutorService executorService;
    
    // 更新キュー
    private final Queue<UpdateTask> updateQueue;
    
    // バッチ処理用のタスク
    private BukkitRunnable batchProcessor;
    
    // 統計情報
    private final AtomicInteger pendingUpdates;
    private final AtomicInteger completedUpdates;
    private final AtomicInteger failedUpdates;
    
    // 設定値
    private static final int THREAD_POOL_SIZE = 2;
    private static final long BATCH_INTERVAL = 100L; // 5秒（100 ticks）
    private static final int MAX_BATCH_SIZE = 50;
    
    public AsyncEventUpdater(JavaPlugin plugin, PlayerDAO playerDAO, PlayerJobDAO playerJobDAO) {
        this(plugin, playerDAO, playerJobDAO, null);
    }

    public AsyncEventUpdater(JavaPlugin plugin, PlayerDAO playerDAO, PlayerJobDAO playerJobDAO,
                             JobExperienceManager jobExperienceManager) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.playerJobDAO = playerJobDAO;
        this.jobExperienceManager = jobExperienceManager;
        this.logger = plugin.getLogger();
        
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.updateQueue = new ConcurrentLinkedQueue<>();
        
        this.pendingUpdates = new AtomicInteger(0);
        this.completedUpdates = new AtomicInteger(0);
        this.failedUpdates = new AtomicInteger(0);
        
        startBatchProcessor();
    }
    
    /**
     * バッチ処理タスクを開始
     */
    private void startBatchProcessor() {
        batchProcessor = new BukkitRunnable() {
            @Override
            public void run() {
                processBatch();
            }
        };
        
        // 定期的にバッチ処理を実行
        batchProcessor.runTaskTimerAsynchronously(plugin, BATCH_INTERVAL, BATCH_INTERVAL);
    }
    
    /**
     * バッチ処理を実行
     */
    private void processBatch() {
        int processedCount = 0;
        
        while (!updateQueue.isEmpty() && processedCount < MAX_BATCH_SIZE) {
            UpdateTask task = updateQueue.poll();
            if (task == null) break;
            
            pendingUpdates.decrementAndGet();
            
            // 非同期でタスクを実行
            executorService.submit(() -> executeTask(task));
            
            processedCount++;
        }
        
        if (processedCount > 0) {
            logger.fine("Processed " + processedCount + " update tasks in batch");
        }
    }
    
    /**
     * 更新タスクを実行
     */
    private void executeTask(UpdateTask task) {
        try {
            task.execute();
            completedUpdates.incrementAndGet();
            
            // 成功時のコールバック実行（メインスレッドで）
            if (task.getOnSuccess() != null) {
                Bukkit.getScheduler().runTask(plugin, task.getOnSuccess());
            }
        } catch (Exception e) {
            failedUpdates.incrementAndGet();
            logger.log(Level.WARNING, "Failed to execute update task: " + task.getDescription(), e);
            
            // 失敗時のコールバック実行（メインスレッドで）
            if (task.getOnFailure() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> task.getOnFailure().accept(e));
            }
        }
    }
    
    // ========== 更新メソッド ==========
    
    /**
     * プレイヤーの残高を非同期更新
     */
    public void updatePlayerBalance(String playerUUID, double amount, String reason) {
        UpdateTask task = new UpdateTask("Update balance for " + playerUUID) {
            @Override
            public void execute() throws SQLException {
                org.tofu.tofunomics.models.Player player = playerDAO.getPlayerByUUID(playerUUID);
                if (player != null) {
                    player.addBankBalance(amount); // 銀行預金に加算
                    playerDAO.updatePlayerData(player);
                }
            }
        };
        
        queueUpdate(task);
    }
    
    /**
     * プレイヤーの職業経験値を付与する。
     *
     * 以前はこのクラス独自にDBを更新していたが、
     *   - 職業名→job_id のマップが実DBと一致していなかった（"builder"/"wizard" 等）
     *   - 昇格時に経験値を減算していた（メインの PlayerJob は累積型）
     *   - 必要経験値の式が独自（level^2 * 100）だった
     * ため、経験値がサイレントに失われるか、多重就業を許可した時点でデータが壊れる
     * 状態だった。現在は経験値の管理を JobExperienceManager に一本化し、
     * ここはメインスレッドへの受け渡しだけを行う。
     *
     * @param jobType 内部職業名（miner / woodcutter / farmer / fisherman /
     *                blacksmith / alchemist / enchanter / architect）
     */
    public void updateJobExperience(String playerUUID, String jobType, double experience) {
        if (jobExperienceManager == null) {
            logger.warning("JobExperienceManager が未設定のため経験値を付与できません: job=" + jobType);
            return;
        }

        // 経験値の付与とレベルアップ処理はメインスレッドで行う
        // （通知・演出・Bukkit API 呼び出しを含むため）
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(java.util.UUID.fromString(playerUUID));
            if (player == null || !player.isOnline()) {
                return;
            }
            // 付与に失敗した場合は黙って捨てずに記録する。
            // 職業名の誤りや未就業が原因で経験値が消えていても気づけるようにするため。
            if (!jobExperienceManager.giveExperienceManual(player, jobType, experience)) {
                logger.warning("職業経験値を付与できませんでした (player=" + player.getName()
                    + ", job=" + jobType + ", exp=" + experience
                    + ")。職業名が正しいか、プレイヤーがその職業に就いているか確認してください。");
            }
        });
    }
    
    /**
     * バッチ残高更新
     */
    public void batchUpdateBalances(java.util.Map<String, Double> balanceUpdates) {
        UpdateTask task = new UpdateTask("Batch update balances") {
            @Override
            public void execute() throws SQLException {
                for (java.util.Map.Entry<String, Double> entry : balanceUpdates.entrySet()) {
                    org.tofu.tofunomics.models.Player player = playerDAO.getPlayerByUUID(entry.getKey());
                    if (player != null) {
                        player.addBankBalance(entry.getValue()); // 銀行預金に加算
                        playerDAO.updatePlayerData(player);
                    }
                }
            }
        };
        
        queueUpdate(task);
    }
    
    // ========== ユーティリティメソッド ==========
    
    /**
     * 更新タスクをキューに追加
     */
    private void queueUpdate(UpdateTask task) {
        updateQueue.offer(task);
        pendingUpdates.incrementAndGet();
        
        // キューが大きくなりすぎた場合は即座に処理
        if (pendingUpdates.get() > MAX_BATCH_SIZE * 2) {
            executorService.submit(this::processBatch);
        }
    }
    
    /**
     * シャットダウン処理
     */
    public void shutdown() {
        // バッチプロセッサを停止
        if (batchProcessor != null) {
            batchProcessor.cancel();
        }
        
        // 残りのタスクを処理
        while (!updateQueue.isEmpty()) {
            processBatch();
        }
        
        // ExecutorServiceをシャットダウン
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        
        logger.info("AsyncEventUpdater shutdown completed. " +
                   "Completed: " + completedUpdates.get() + ", " +
                   "Failed: " + failedUpdates.get());
    }
    
    // ========== 統計情報メソッド ==========
    
    public int getPendingUpdates() {
        return pendingUpdates.get();
    }
    
    public int getCompletedUpdates() {
        return completedUpdates.get();
    }
    
    public int getFailedUpdates() {
        return failedUpdates.get();
    }
    
    public String getStatistics() {
        return String.format(
            "AsyncEventUpdater Statistics: Pending=%d, Completed=%d, Failed=%d",
            getPendingUpdates(),
            getCompletedUpdates(),
            getFailedUpdates()
        );
    }
    
    // ========== 内部クラス ==========
    
    /**
     * 更新タスクの抽象クラス
     */
    private static abstract class UpdateTask {
        private final String description;
        private Runnable onSuccess;
        private java.util.function.Consumer<Exception> onFailure;
        
        public UpdateTask(String description) {
            this.description = description;
        }
        
        public abstract void execute() throws Exception;
        
        public String getDescription() { return description; }
        public Runnable getOnSuccess() { return onSuccess; }
        public java.util.function.Consumer<Exception> getOnFailure() { return onFailure; }
        
        public UpdateTask onSuccess(Runnable callback) {
            this.onSuccess = callback;
            return this;
        }
        
        public UpdateTask onFailure(java.util.function.Consumer<Exception> callback) {
            this.onFailure = callback;
            return this;
        }
    }
}