package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;
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
    // 収入システムは無効化: incomeManager は削除されました
    private final org.tofu.tofunomics.quests.JobQuestManager questManager;
    
    // 職業ブロック制限システム
    private final org.tofu.tofunomics.jobs.JobBlockPermissionManager blockPermissionManager;
    
    // プレイヤーが設置したブロックの位置を記録（メモリ内追跡）
    private final Set<String> playerPlacedBlocks;
    
    public UnifiedEventHandler(JavaPlugin plugin, ConfigManager configManager,
                              PlayerDAO playerDAO, PlayerJobDAO playerJobDAO,
                              JobManager jobManager,
                              org.tofu.tofunomics.experience.JobExperienceManager experienceManager,
                              org.tofu.tofunomics.quests.JobQuestManager questManager,
                              org.tofu.tofunomics.jobs.JobBlockPermissionManager blockPermissionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.playerJobDAO = playerJobDAO;
        this.jobManager = jobManager;
        this.logger = plugin.getLogger();
        this.experienceManager = experienceManager;
        // 収入システムは無効化: incomeManager は削除されました
        this.questManager = questManager;
        this.blockPermissionManager = blockPermissionManager;
        
        // プレイヤー設置ブロック追跡システムの初期化
        this.playerPlacedBlocks = new HashSet<>();
        
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
        // 基本的なイベント処理チェック
        if (!shouldProcessEvent(event)) {
            return;
        }

        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        // 職業ブロック制限チェック（優先度HIGHで早期チェック）
        if (!blockPermissionManager.canPlayerBreakBlock(player, blockType)) {
            event.setCancelled(true);
            String message = blockPermissionManager.getDeniedMessage(player, blockType);
            player.sendMessage(message);
            return;
        }

        // プレイヤーが設置したブロックかチェック（メモリ内追跡）
        String blockKey = getBlockLocationKey(event.getBlock().getLocation());
        boolean isPlayerPlaced = playerPlacedBlocks.contains(blockKey);

        // 設置ブロックの場合はセットから削除
        if (isPlayerPlaced) {
            playerPlacedBlocks.remove(blockKey);
        }

        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "block_break", 50)) {
            return; // 50ms以内の重複イベントは無視
        }

        // 既存のマネージャーに処理を委譲（収入システムは無効化）
        // プレイヤーが設置したブロックの場合は経験値を付与しない
        if (!isPlayerPlaced) {
            experienceManager.onBlockBreak(event);
        }
        questManager.onBlockBreak(event);

        // キャッシュに記録
        eventCache.markAsProcessed(player, "block_break");
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        // 鉱石ブロックの設置を禁止（管理者権限がない場合）
        if (isOreBlock(blockType) && !player.hasPermission("tofunomics.place.ore")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "鉱石ブロックは設置できません。");
            return;
        }
        
        if (!shouldProcessEvent(event)) return;
        
        // プレイヤーが設置したブロックを記録（メモリ内追跡）
        // STONE, COBBLESTONEなど経験値対象ブロックのみ追跡（鉱石は除外）
        if (shouldTrackPlacedBlock(blockType)) {
            String blockKey = getBlockLocationKey(event.getBlock().getLocation());
            playerPlacedBlocks.add(blockKey);
        }
        
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
        if (!shouldProcessEvent(event)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();

        // クラフト制限チェック（優先度HIGH で先にチェック）
        TofuNomics tofuPlugin = (TofuNomics) plugin;
        if (tofuPlugin.getJobCraftPermissionManager() != null) {
            if (!tofuPlugin.getJobCraftPermissionManager().canPlayerCraftItem(player, craftedItem)) {
                // クラフトを禁止
                event.setCancelled(true);

                // 制限メッセージを送信
                String message = tofuPlugin.getJobCraftPermissionManager().getCraftDeniedMessage(player, craftedItem);
                player.sendMessage(message);
                return;
            }
        } else {
            plugin.getLogger().warning("JobCraftPermissionManager: 未初期化のため制限チェックをスキップ");
        }

        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "craft_item", 100)) {
            return;
        }
        
        // 既存のマネージャーに処理を委譲（収入システムは無効化）
        experienceManager.onCraftItem(event);
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
        
        // 既存のマネージャーに処理を委譲（収入システムは無効化）
        experienceManager.onPlayerFish(event);
        questManager.onPlayerFish(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "player_fish");
    }
    
    // ========== ユーティリティメソッド ==========
    
    /**
     * 鉱石ブロックかどうかを判定
     */
    private boolean isOreBlock(Material blockType) {
        // 深層岩鉱石（DEEPSLATE_*_ORE）も通常鉱石へ正規化して判定対象に含める
        blockType = org.tofu.tofunomics.util.BlockNormalizer.normalizeForJob(blockType);
        return blockType == Material.COAL_ORE ||
               blockType == Material.IRON_ORE || 
               blockType == Material.GOLD_ORE || 
               blockType == Material.DIAMOND_ORE || 
               blockType == Material.EMERALD_ORE || 
               blockType == Material.LAPIS_ORE || 
               blockType == Material.REDSTONE_ORE ||
               blockType == Material.NETHER_QUARTZ_ORE ||
               blockType == Material.COPPER_ORE ||
               blockType == Material.ANCIENT_DEBRIS;
    }
    
    /**
     * ブロック位置をキー文字列に変換
     */
    private String getBlockLocationKey(Location location) {
        return location.getWorld().getName() + ":" + 
               location.getBlockX() + "," + 
               location.getBlockY() + "," + 
               location.getBlockZ();
    }
    
    /**
     * ブロック設置を追跡すべきかチェック
     * 経験値が得られる可能性のあるブロックのみ追跡
     * 注意: 鉱石ブロックは設置禁止のため追跡不要
     */
    private boolean shouldTrackPlacedBlock(Material blockType) {
        // 鉱石類は設置禁止のため追跡不要（isOreBlockで判定）
        
        // STONE, COBBLESTONE（無限経験値防止）
        // 深層岩石材（DEEPSLATE/COBBLED_DEEPSLATE/TUFF）はSTONEへ正規化され採掘経験値の対象になるため、
        // 設置→採掘による無限経験値を防ぐべく追跡する
        if (blockType == Material.STONE || blockType == Material.COBBLESTONE ||
            blockType == Material.DEEPSLATE || blockType == Material.COBBLED_DEEPSLATE ||
            blockType == Material.TUFF) {
            return true;
        }
        
        // 原木類
        if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_STEM")) {
            return true;
        }
        
        // 農作物類
        if (blockType == Material.WHEAT || blockType == Material.POTATOES || 
            blockType == Material.CARROTS || blockType == Material.BEETROOTS ||
            blockType == Material.PUMPKIN || blockType == Material.MELON ||
            blockType == Material.SUGAR_CANE || blockType == Material.NETHER_WART) {
            return true;
        }
        
        return false;
    }
    
    /**
     * イベント処理を行うべきかチェック
     */
    private boolean shouldProcessEvent(Event event) {
        // 設定でイベントシステムが無効化されている場合
        if (!configManager.isEventSystemEnabled()) {
            return false;
        }

        // イベント処理プロセッサでの判定
        return eventProcessor.shouldProcessEvent(event);
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
     * 設定の再読み込み（リロードコマンド時に呼び出し）
     * 内部の EventProcessor が保持するワールド/ゲームモード設定を再構築する。
     */
    public void reloadConfiguration() {
        eventProcessor.reloadConfiguration();
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
            
            // 収入システムは無効化：収入付与は削除されました
            // プレイヤーに経験値獲得通知のみ（金塊は表示しない）
            if (finalExperience > 0) {
                player.sendMessage(String.format("§7[%s] §e+%.1f経験値",
                    configManager.getJobDisplayName(jobName), finalExperience));
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