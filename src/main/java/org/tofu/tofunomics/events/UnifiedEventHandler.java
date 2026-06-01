package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.inventory.FurnaceExtractEvent;
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
                              org.tofu.tofunomics.jobs.JobBlockPermissionManager blockPermissionManager,
                              AsyncEventUpdater asyncUpdater) {
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
        this.asyncUpdater = asyncUpdater;
        
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
        
        // プレイヤーが設置したブロックかチェック（メモリ内追跡）
        String blockKey = getBlockLocationKey(event.getBlock().getLocation());
        boolean isPlayerPlaced = playerPlacedBlocks.contains(blockKey);
        
        System.out.println("プレイヤー設置ブロック: " + isPlayerPlaced);
        
        // 設置ブロックの場合はセットから削除
        if (isPlayerPlaced) {
            playerPlacedBlocks.remove(blockKey);
        }
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "block_break", 50)) {
            System.out.println("重複イベントのためスキップ");
            return; // 50ms以内の重複イベントは無視
        }
        
        // 既存のマネージャーに処理を委譲（収入システムは無効化）
        // プレイヤーが設置したブロックの場合は経験値を付与しない
        if (!isPlayerPlaced) {
            experienceManager.onBlockBreak(event);
            
            // エンチャンターのラピス採掘ボーナス
            if (blockType == Material.LAPIS_ORE || blockType.name().equals("DEEPSLATE_LAPIS_ORE")) {
                if (jobManager.hasJob(player, "enchanter")) {
                    PlayerJob enchanterJob = jobManager.getPlayerJob(player, "enchanter");
                    if (enchanterJob != null && enchanterJob.isActive()) {
                        double lapisExp = 8.0;  // ラピス採掘ボーナス
                        double lapisIncome = 5.0;  // ラピス採掘収入
                        String playerUUID = player.getUniqueId().toString();
                        asyncUpdater.updateJobExperience(playerUUID, "enchanter", lapisExp);
                        asyncUpdater.updatePlayerBalance(playerUUID, lapisIncome, "エンチャンターラピス採掘報酬");
                        player.sendMessage("§d§l[エンチャント] §bラピス採掘ボーナス! §a+" + lapisExp + "経験値 §6+" + lapisIncome + "金塊");
                    }
                }
            }
            
            // 調合師のネザーウォート収穫ボーナス
            if (blockType == Material.NETHER_WART) {
                if (jobManager.hasJob(player, "alchemist")) {
                    PlayerJob alchemistJob = jobManager.getPlayerJob(player, "alchemist");
                    if (alchemistJob != null && alchemistJob.isActive()) {
                        double wartExp = 5.0;  // ネザーウォート収穫ボーナス
                        double wartIncome = 3.0;  // ネザーウォート収穫収入
                        String playerUUID = player.getUniqueId().toString();
                        asyncUpdater.updateJobExperience(playerUUID, "alchemist", wartExp);
                        asyncUpdater.updatePlayerBalance(playerUUID, wartIncome, "調合師ネザーウォート収穫報酬");
                        player.sendMessage("§5§l[調合] §dネザーウォート収穫ボーナス! §a+" + wartExp + "経験値 §6+" + wartIncome + "金塊");
                    }
                }
            }
        } else {
            System.out.println("プレイヤー設置ブロックのため経験値なし");
        }
        questManager.onBlockBreak(event);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "block_break");
        
        System.out.println("=== ブロック破壊イベント処理完了 ===");
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
        
        if (!shouldProcessEvent(event)) {
            return;
        }
        
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
        if (!shouldProcessEvent(event)) {
            return;
        }
        
        // 農家の作物成長処理
        growthHandler.handleBlockGrow(event);
    }
    
    // ========== クラフト・醸造・エンチャント関連イベント ==========
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        plugin.getLogger().info("=== onCraftItem メソッド開始 ===");
        if (!shouldProcessEvent(event)) {
            return;
        }
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
        
        // 既存のマネージャーに処理を委譲（収入システムは無効化）
        experienceManager.onCraftItem(event);
        questManager.onCraftItem(event);
        
        // エンチャンターの本棚クラフトボーナス
        if (craftedItem == Material.BOOKSHELF || craftedItem == Material.ENCHANTING_TABLE) {
            if (jobManager.hasJob(player, "enchanter")) {
                PlayerJob enchanterJob = jobManager.getPlayerJob(player, "enchanter");
                if (enchanterJob != null && enchanterJob.isActive()) {
                    double craftExp = (craftedItem == Material.ENCHANTING_TABLE) ? 15.0 : 5.0;
                    double craftIncome = (craftedItem == Material.ENCHANTING_TABLE) ? 5.0 : 3.0;
                    String playerUUID = player.getUniqueId().toString();
                    asyncUpdater.updateJobExperience(playerUUID, "enchanter", craftExp);
                    asyncUpdater.updatePlayerBalance(playerUUID, craftIncome, "エンチャンタークラフト報酬");
                    String itemName = (craftedItem == Material.ENCHANTING_TABLE) ? "エンチャントテーブル" : "本棚";
                    player.sendMessage("§d§l[エンチャント] §b" + itemName + "クラフトボーナス! §a+" + craftExp + "経験値 §6+" + craftIncome + "金塊");
                }
            }
        }
        
        // 鍛冶屋のクラフト収入
        if (jobManager.hasJob(player, "blacksmith")) {
            PlayerJob blacksmithJob = jobManager.getPlayerJob(player, "blacksmith");
            if (blacksmithJob != null && blacksmithJob.isActive()) {
                double craftIncome = getBlacksmithCraftIncome(craftedItem);
                if (craftIncome > 0) {
                    asyncUpdater.updatePlayerBalance(player.getUniqueId().toString(), craftIncome, "鍛冶屋クラフト報酬");
                }
            }
        }
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "craft_item");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!shouldProcessEvent(event)) {
            return;
        }
        
        // 調合師専用処理
        brewingHandler.handleBrew(event);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!shouldProcessEvent(event)) {
            return;
        }
        
        Player player = event.getEnchanter();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "enchant_item", 500)) {
            return;
        }
        
        // 既存のマネージャーに処理を委譲
        experienceManager.onEnchantItem(event);
        
        // 魔術師専用処理
        enchantmentHandler.handleEnchantment(event);
        
        // エンチャンターの収入付与
        if (jobManager.hasJob(player, "enchanter")) {
            PlayerJob enchanterJob = jobManager.getPlayerJob(player, "enchanter");
            if (enchanterJob != null && enchanterJob.isActive()) {
                int expLevelCost = event.getExpLevelCost();
                double enchantIncome = getEnchantmentIncome(expLevelCost);
                if (enchantIncome > 0) {
                    asyncUpdater.updatePlayerBalance(player.getUniqueId().toString(), enchantIncome, "エンチャント報酬");
                }
            }
        }
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "enchant_item");
    }
    
    // ========== 精錬関連イベント ==========
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Material extractedItem = event.getItemType();
        int amount = event.getItemAmount();
        
        // 鍛冶屋の精錬完了ボーナス（鉄・金インゴット精錬時）
        if (jobManager.hasJob(player, "blacksmith")) {
            PlayerJob blacksmithJob = jobManager.getPlayerJob(player, "blacksmith");
            if (blacksmithJob != null && blacksmithJob.isActive()) {
                double smeltExp = 0.0;
                String itemName = null;
                
                if (extractedItem == Material.IRON_INGOT) {
                    smeltExp = 3.0;
                    itemName = "鉄インゴット";
                } else if (extractedItem == Material.GOLD_INGOT) {
                    smeltExp = 4.0;
                    itemName = "金インゴット";
                } else if (extractedItem == Material.NETHERITE_SCRAP) {
                    smeltExp = 10.0;
                    itemName = "ネザライトスクラップ";
                }
                
                if (smeltExp > 0) {
                    double totalExp = smeltExp * amount;
                    double smeltIncome = 2.0 * amount;  // 精錬収入: 1個あたり2.0金塊
                    String playerUUID = player.getUniqueId().toString();
                    asyncUpdater.updateJobExperience(playerUUID, "blacksmith", totalExp);
                    asyncUpdater.updatePlayerBalance(playerUUID, smeltIncome, "鍛冶屋精錬報酬");
                    player.sendMessage("§6§l[鍛冶] §e" + itemName + "精錬ボーナス! §a+" + totalExp + "経験値 §6+" + smeltIncome + "金塊 §7(x" + amount + ")");
                }
            }
        }
    }
    
    // ========== エンティティ関連イベント ==========
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!shouldProcessEvent(event)) {
            return;
        }
        if (event.getEntity().getKiller() == null) return;
        
        Player player = event.getEntity().getKiller();
        
        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "entity_death", 100)) {
            return;
        }
        
        // 調合師のモンスター素材採取ボーナス
        Entity entity = event.getEntity();
        if (jobManager.hasJob(player, "alchemist")) {
            PlayerJob alchemistJob = jobManager.getPlayerJob(player, "alchemist");
            if (alchemistJob != null && alchemistJob.isActive()) {
                double materialExp = 0.0;
                String materialName = null;
                
                // ブレイズ討伐 → ブレイズロッド
                if (entity.getType() == org.bukkit.entity.EntityType.BLAZE) {
                    materialExp = 8.0;
                    materialName = "ブレイズロッド";
                }
                // ガスト討伐 → ガストの涙
                else if (entity.getType() == org.bukkit.entity.EntityType.GHAST) {
                    materialExp = 10.0;
                    materialName = "ガストの涙";
                }
                // ファントム討伐 → ファントムの皮膜
                else if (entity.getType() == org.bukkit.entity.EntityType.PHANTOM) {
                    materialExp = 6.0;
                    materialName = "ファントムの皮膜";
                }
                // マグマキューブ討伐 → マグマクリーム
                else if (entity.getType() == org.bukkit.entity.EntityType.MAGMA_CUBE) {
                    materialExp = 5.0;
                    materialName = "マグマクリーム";
                }
                // ウサギ討伐 → ウサギの足（希少）
                else if (entity.getType() == org.bukkit.entity.EntityType.RABBIT) {
                    materialExp = 3.0;  // ドロップ率が低いので基本経験値は低め
                    materialName = "ウサギ素材";
                }
                
                if (materialExp > 0) {
                    double materialIncome = getMaterialIncomeForEntity(entity);
                    String playerUUID = player.getUniqueId().toString();
                    asyncUpdater.updateJobExperience(playerUUID, "alchemist", materialExp);
                    if (materialIncome > 0) {
                        asyncUpdater.updatePlayerBalance(playerUUID, materialIncome, "調合師素材採取報酬");
                    }
                    player.sendMessage("§5§l[調合] §d" + materialName + "素材ボーナス! §a+" + materialExp + "経験値 §6+" + materialIncome + "金塊");
                }
            }
        }
        
        // エンティティ討伐による基本的な報酬処理（将来実装）
        // handleEntityDeathRewards(event, player);
        
        // キャッシュに記録
        eventCache.markAsProcessed(player, "entity_death");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!shouldProcessEvent(event)) {
            return;
        }
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
        if (!shouldProcessEvent(event)) {
            return;
        }
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
        return blockType == Material.COAL_ORE || 
               blockType == Material.IRON_ORE || 
               blockType == Material.GOLD_ORE || 
               blockType == Material.DIAMOND_ORE || 
               blockType == Material.EMERALD_ORE || 
               blockType == Material.LAPIS_ORE || 
               blockType == Material.REDSTONE_ORE || 
               blockType == Material.NETHER_QUARTZ_ORE ||
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
        if (blockType == Material.STONE || blockType == Material.COBBLESTONE) {
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
     * 鍛冶屋のクラフト収入を取得
     */
    private double getBlacksmithCraftIncome(Material material) {
        switch (material) {
            // 鉄ツール
            case IRON_SWORD:
                return 8.0;
            case IRON_PICKAXE:
                return 10.0;
            case IRON_AXE:
                return 10.0;
            case IRON_SHOVEL:
                return 6.0;
            case IRON_HOE:
                return 6.0;
            // ダイヤツール
            case DIAMOND_SWORD:
                return 25.0;
            case DIAMOND_PICKAXE:
                return 30.0;
            case DIAMOND_AXE:
                return 30.0;
            case DIAMOND_SHOVEL:
                return 20.0;
            case DIAMOND_HOE:
                return 20.0;
            // ネザライトツール
            case NETHERITE_SWORD:
                return 50.0;
            case NETHERITE_PICKAXE:
                return 60.0;
            case NETHERITE_AXE:
                return 60.0;
            case NETHERITE_SHOVEL:
                return 40.0;
            case NETHERITE_HOE:
                return 40.0;
            // 鉄防具
            case IRON_HELMET:
                return 8.0;
            case IRON_CHESTPLATE:
                return 12.0;
            case IRON_LEGGINGS:
                return 10.0;
            case IRON_BOOTS:
                return 8.0;
            // ダイヤ防具
            case DIAMOND_HELMET:
                return 20.0;
            case DIAMOND_CHESTPLATE:
                return 30.0;
            case DIAMOND_LEGGINGS:
                return 25.0;
            case DIAMOND_BOOTS:
                return 20.0;
            // ネザライト防具
            case NETHERITE_HELMET:
                return 40.0;
            case NETHERITE_CHESTPLATE:
                return 60.0;
            case NETHERITE_LEGGINGS:
                return 50.0;
            case NETHERITE_BOOTS:
                return 40.0;
            // その他
            case SHIELD:
                return 10.0;
            case CHAINMAIL_HELMET:
            case CHAINMAIL_CHESTPLATE:
            case CHAINMAIL_LEGGINGS:
            case CHAINMAIL_BOOTS:
                return 15.0;
            case ANVIL:
                return 20.0;
            case IRON_BLOCK:
                return 5.0;
            case GOLD_BLOCK:
                return 8.0;
            case DIAMOND_BLOCK:
                return 25.0;
            case NETHERITE_BLOCK:
                return 100.0;
            default:
                return 0.0;
        }
    }


    /**
     * エンチャントレベルに応じた収入を取得
     */
    private double getEnchantmentIncome(int expLevelCost) {
        if (expLevelCost >= 30) {
            return 65.0;  // Lv5相当
        } else if (expLevelCost >= 24) {
            return 45.0;  // Lv4相当
        } else if (expLevelCost >= 18) {
            return 30.0;  // Lv3相当
        } else if (expLevelCost >= 12) {
            return 18.0;  // Lv2相当
        } else if (expLevelCost >= 1) {
            return 10.0;  // Lv1相当
        }
        return 0.0;
    }


    /**
     * 調合師の素材採取収入を取得
     */
    private double getMaterialIncomeForEntity(org.bukkit.entity.Entity entity) {
        switch (entity.getType()) {
            case BLAZE:
                return 5.0;  // ブレイズロッド
            case GHAST:
                return 8.0;  // ガストの涙
            case PHANTOM:
                return 5.0;  // ファントムの皮膜
            case MAGMA_CUBE:
                return 4.0;  // マグマクリーム
            case RABBIT:
                return 3.0;  // ウサギの足
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