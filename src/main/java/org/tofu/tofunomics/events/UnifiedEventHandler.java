package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import java.util.List;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.logging.Logger;

/**
 * 統合イベントハンドラシステム
 * 全てのゲームプレイイベントを一元管理し、パフォーマンスを最適化
 */
public class UnifiedEventHandler implements Listener {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final PlayerJobDAO playerJobDAO;
    private final JobManager jobManager;
    private final Logger logger;
    
    // サブシステム
    private final EventCache eventCache;
    private final EventProcessor eventProcessor;
    private final AsyncEventUpdater asyncUpdater;
    
    // 個別イベントハンドラ
    private final org.tofu.tofunomics.events.handlers.BrewingEventHandler brewingHandler;
    private final org.tofu.tofunomics.events.handlers.EnchantmentEventHandler enchantmentHandler;
    private final org.tofu.tofunomics.events.handlers.BreedingEventHandler breedingHandler;
    private final org.tofu.tofunomics.events.handlers.GrowthEventHandler growthHandler;
    private final org.tofu.tofunomics.events.handlers.BuildingEventHandler buildingHandler;
    
    // 既存のハンドラ参照
    private final org.tofu.tofunomics.experience.JobExperienceManager experienceManager;
    private final org.tofu.tofunomics.income.JobIncomeManager incomeManager;
    private final org.tofu.tofunomics.quests.JobQuestManager questManager;
    
    // 職業ブロック制限システム
    private final org.tofu.tofunomics.jobs.JobBlockPermissionManager blockPermissionManager;
    
    public UnifiedEventHandler(JavaPlugin plugin, ConfigManager configManager,
                              PlayerDAO playerDAO, PlayerJobDAO playerJobDAO,
                              JobManager jobManager,
                              org.tofu.tofunomics.experience.JobExperienceManager experienceManager,
                              org.tofu.tofunomics.income.JobIncomeManager incomeManager,
                              org.tofu.tofunomics.quests.JobQuestManager questManager,
                              org.tofu.tofunomics.jobs.JobBlockPermissionManager blockPermissionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.playerJobDAO = playerJobDAO;
        this.jobManager = jobManager;
        this.logger = plugin.getLogger();
        this.experienceManager = experienceManager;
        this.incomeManager = incomeManager;
        this.questManager = questManager;
        this.blockPermissionManager = blockPermissionManager;
        
        // サブシステムの初期化
        this.eventCache = new EventCache(plugin);
        this.eventProcessor = new EventProcessor(configManager, jobManager);
        this.asyncUpdater = new AsyncEventUpdater(plugin, playerDAO, playerJobDAO);
        
        // 個別ハンドラの初期化
        this.brewingHandler = new org.tofu.tofunomics.events.handlers.BrewingEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
        this.enchantmentHandler = new org.tofu.tofunomics.events.handlers.EnchantmentEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
        this.breedingHandler = new org.tofu.tofunomics.events.handlers.BreedingEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
        this.growthHandler = new org.tofu.tofunomics.events.handlers.GrowthEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
        this.buildingHandler = new org.tofu.tofunomics.events.handlers.BuildingEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
    }
    
    // ========== ブロック関連イベント ==========
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        System.out.println("=== UnifiedEventHandler.onBlockBreak デバッグ開始 ===");
        System.out.println("イベント: " + event.getClass().getSimpleName());
        
        // 基本的なイベント処理チェック
        if (!shouldProcessEvent(event)) {
            System.out.println("shouldProcessEventでfalse、処理をスキップ");
            return;
        }
        
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        System.out.println("プレイヤー: " + player.getName());
        System.out.println("ブロック: " + blockType.name());
        
        
        
        // 職業ブロック制限チェック（優先度HIGHで早期チェック）
        if (!blockPermissionManager.canPlayerBreakBlock(player, blockType)) {
            System.out.println("blockPermissionManagerで拒否されました");
            event.setCancelled(true);
            String message = blockPermissionManager.getDeniedMessage(player, blockType);
            player.sendMessage(message);
            return;
        }
        
        System.out.println("ブロック破壊許可 - 通常処理を継続");
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "block_break", 50)) {
            System.out.println("重複イベントのためスキップ");
            return; // 50ms以内の重複イベントは無視
        }
        
        // 既存のマネージャーに処理を委譲
        experienceManager.onBlockBreak(event);
        incomeManager.onBlockBreak(event);
        questManager.onBlockBreak(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "block_break");
        
        System.out.println("=== ブロック破壊イベント処理完了 ===");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!shouldProcessEvent(event)) return;
        
        Player player = event.getPlayer();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "block_place", 50)) {
            return;
        }
        
        // 建築家専用処理
        buildingHandler.handleBlockPlace(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "block_place");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!shouldProcessEvent(event)) return;
        
        // 農家の作物成長処理
        growthHandler.handleBlockGrow(event);
    }
    
    // ========== クラフト・醸造・エンチャント関連イベント ==========
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        plugin.getLogger().info("=== onCraftItem メソッド開始 ===");
        if (!shouldProcessEvent(event)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();
        
        // クラフト制限チェック（優先度HIGH で先にチェック）
        TofuNomics tofuPlugin = (TofuNomics) plugin;
        plugin.getLogger().info("=== CraftItemEvent処理開始 ===");
        plugin.getLogger().info("プレイヤー: " + player.getName() + ", アイテム: " + craftedItem.name());
        
        if (tofuPlugin.getJobCraftPermissionManager() != null) {
            plugin.getLogger().info("JobCraftPermissionManager: 初期化済み");
            
            if (!tofuPlugin.getJobCraftPermissionManager().canPlayerCraftItem(player, craftedItem)) {
                // クラフトを禁止
                event.setCancelled(true);
                
                // 制限メッセージを送信
                String message = tofuPlugin.getJobCraftPermissionManager().getCraftDeniedMessage(player, craftedItem);
                player.sendMessage(message);
                
                plugin.getLogger().info("クラフト制限: " + player.getName() + " が " + craftedItem.name() + " のクラフトを禁止されました");
                return;
            } else {
                plugin.getLogger().info("クラフト許可: " + player.getName() + " が " + craftedItem.name() + " のクラフトを許可");
            }
        } else {
            plugin.getLogger().warning("JobCraftPermissionManager: 未初期化のため制限チェックをスキップ");
        }
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "craft_item", 100)) {
            return;
        }
        
        // 既存のマネージャーに処理を委譲
        experienceManager.onCraftItem(event);
        incomeManager.onCraftItem(event);
        questManager.onCraftItem(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "craft_item");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!shouldProcessEvent(event)) return;
        
        // 調合師専用処理
        brewingHandler.handleBrew(event);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!shouldProcessEvent(event)) return;
        
        Player player = event.getEnchanter();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "enchant_item", 500)) {
            return;
        }
        
        // 既存のマネージャーに処理を委譲
        experienceManager.onEnchantItem(event);
        
        // 魔術師専用処理
        enchantmentHandler.handleEnchantment(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "enchant_item");
    }
    
    // ========== エンティティ関連イベント ==========
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!shouldProcessEvent(event)) return;
        if (event.getEntity().getKiller() == null) return;
        
        Player player = event.getEntity().getKiller();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "entity_death", 100)) {
            return;
        }
        
        // エンティティ討伐による基本的な報酬処理（将来実装）
        // handleEntityDeathRewards(event, player);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "entity_death");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!shouldProcessEvent(event)) return;
        if (!(event.getBreeder() instanceof Player)) return;
        
        Player player = (Player) event.getBreeder();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "entity_breed", 1000)) {
            return;
        }
        
        // 農家専用処理
        breedingHandler.handleBreeding(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "entity_breed");
    }
    
    // ========== プレイヤーアクション関連イベント ==========
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!shouldProcessEvent(event)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        
        Player player = event.getPlayer();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "player_fish", 500)) {
            return;
        }
        
        // 既存のマネージャーに処理を委譲
        experienceManager.onPlayerFish(event);
        incomeManager.onPlayerFish(event);
        questManager.onPlayerFish(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "player_fish");
    }
    
    // ========== ユーティリティメソッド ==========
    
    /**
     * イベント処理を行うべきかチェック
     */
    private boolean shouldProcessEvent(Event event) {
        plugin.getLogger().info("=== shouldProcessEvent 診断開始 ===");
        plugin.getLogger().info("イベントタイプ: " + event.getClass().getSimpleName());
        
        // 設定でイベントシステムが無効化されている場合
        boolean isEventSystemEnabled = configManager.isEventSystemEnabled();
        plugin.getLogger().info("イベントシステム有効: " + isEventSystemEnabled);
        if (!isEventSystemEnabled) {
            plugin.getLogger().info("判定結果: イベントシステムが無効のため処理スキップ");
            return false;
        }
        
        // イベント処理プロセッサでの判定
        boolean shouldProcessByProcessor = eventProcessor.shouldProcessEvent(event);
        plugin.getLogger().info("イベントプロセッサ判定: " + shouldProcessByProcessor);
        if (!shouldProcessByProcessor) {
            plugin.getLogger().info("判定結果: イベントプロセッサが処理を拒否");
            return false;
        }
        
        plugin.getLogger().info("判定結果: イベント処理を実行");
        return true;
    }
    
    /**
     * システムのクリーンアップ
     */
    public void cleanup() {
        eventCache.cleanup();
        asyncUpdater.shutdown();
        logger.info("UnifiedEventHandler cleaned up successfully");
    }
    
    /**
     * 統計情報の取得
     */
    public EventStatistics getStatistics() {
        return new EventStatistics(
            eventCache.getTotalProcessedEvents(),
            eventCache.getCacheHitRate(),
            asyncUpdater.getPendingUpdates()
        );
    }
    
    /**
     * エンティティ討伐による報酬処理
     */
    private void handleEntityDeathRewards(EntityDeathEvent event, Player player) {
        if (event.getEntity() == null || player == null) {
            return;
        }
        
        // プレイヤーの職業を取得
        List<org.tofu.tofunomics.models.PlayerJob> playerJobs = jobManager.getPlayerJobs(player);
        if (playerJobs == null || playerJobs.isEmpty()) {
            return; // 無職の場合は報酬なし
        }
        
        org.tofu.tofunomics.models.PlayerJob primaryJob = playerJobs.get(0);
        // JobIDから職業名を取得
        org.tofu.tofunomics.models.Job job = jobManager.getJobById(primaryJob.getJobId());
        if (job == null) {
            return; // 有効な職業が見つからない場合は処理しない
        }
        String jobName = job.getName();
        int jobLevel = primaryJob.getLevel();
        
        // エンティティタイプに応じた基本経験値と報酬を設定
        double baseExperience = getBaseExperienceForEntity(event.getEntity());
        double baseIncome = getBaseIncomeForEntity(event.getEntity());
        
        if (baseExperience > 0 || baseIncome > 0) {
            // レベル補正を適用
            double levelMultiplier = 1.0 + (jobLevel * 0.01); // レベル1毎に1%増加
            
            double finalExperience = baseExperience * levelMultiplier;
            double finalIncome = baseIncome * levelMultiplier;
            
            // 経験値付与（既存システムを利用）
            if (finalExperience > 0) {
                experienceManager.giveExperienceManual(player, jobName, finalExperience);
            }
            
            // 収入付与（既存システムを利用）
            if (finalIncome > 0) {
                incomeManager.giveIncomeManual(player, jobName, finalIncome);
                
                // プレイヤーに通知
                player.sendMessage(String.format("§7[%s] §e+%.1f経験値 §a+%.2f金塊", 
                    configManager.getJobDisplayName(jobName), finalExperience, finalIncome));
            }
        }
    }
    
    /**
     * エンティティタイプに応じた基本経験値を取得
     */
    private double getBaseExperienceForEntity(org.bukkit.entity.Entity entity) {
        switch (entity.getType()) {
            // 敵対的モブ
            case ZOMBIE:
            case SKELETON:
            case SPIDER:
            case CREEPER:
                return 2.0;
            case ENDERMAN:
            case WITCH:
                return 5.0;
            case BLAZE:
            case GHAST:
                return 8.0;
            // ボス系
            case ENDER_DRAGON:
                return 100.0;
            case WITHER:
                return 80.0;
            // 動物（農家の畜産業）
            case COW:
            case PIG:
            case CHICKEN:
            case SHEEP:
                return 1.0;
            default:
                return 0.0;
        }
    }
    
    /**
     * エンティティタイプに応じた基本収入を取得
     */
    private double getBaseIncomeForEntity(org.bukkit.entity.Entity entity) {
        switch (entity.getType()) {
            // 敵対的モブ
            case ZOMBIE:
            case SKELETON:
            case SPIDER:
            case CREEPER:
                return 1.5;
            case ENDERMAN:
            case WITCH:
                return 3.0;
            case BLAZE:
            case GHAST:
                return 5.0;
            // ボス系
            case ENDER_DRAGON:
                return 500.0;
            case WITHER:
                return 300.0;
            // 動物（農家の畜産業）
            case COW:
            case PIG:
            case CHICKEN:
            case SHEEP:
                return 0.8;
            default:
                return 0.0;
        }
    }
    
    /**
     * イベント統計クラス
     */
    public static class EventStatistics {
        private final long totalProcessedEvents;
        private final double cacheHitRate;
        private final int pendingUpdates;
        
        public EventStatistics(long totalProcessedEvents, double cacheHitRate, int pendingUpdates) {
            this.totalProcessedEvents = totalProcessedEvents;
            this.cacheHitRate = cacheHitRate;
            this.pendingUpdates = pendingUpdates;
        }
        
        public long getTotalProcessedEvents() { return totalProcessedEvents; }
        public double getCacheHitRate() { return cacheHitRate; }
        public int getPendingUpdates() { return pendingUpdates; }
    }
}