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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
    private final org.tofu.tofunomics.events.handlers.FarmingActivityEventHandler farmingActivityHandler;
    
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
        // 経験値の付与は JobExperienceManager に一本化する
        // （AsyncEventUpdater 独自のレベル計算は職業IDの誤りと減算モデルで壊れていた）
        this.asyncUpdater = new AsyncEventUpdater(plugin, playerDAO, playerJobDAO, experienceManager);
        
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
        this.farmingActivityHandler = new org.tofu.tofunomics.events.handlers.FarmingActivityEventHandler(
            configManager, playerDAO, jobManager, asyncUpdater
        );
    }
    
    // ========== ブロック関連イベント ==========
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        // 職業ブロック制限チェック（採掘禁止はハードルールのため、報酬システムの
        // ゲームモードフィルタ shouldProcessEvent より前に必ず実行する。
        // ただしワールド判定だけは先に行い、TofuNomics 対象外ワールド
        // （ロビー・ミニゲーム等）には干渉しない。
        // canPlayerBreakBlock 内で「制限無効なら許可」「管理者権限バイパス」を自己ガード済み。
        // onBlockPlace の植え付け制限と対称の順序）
        if (isJobRestrictionWorld(player) && !blockPermissionManager.canPlayerBreakBlock(player, blockType)) {
            event.setCancelled(true);
            String message = blockPermissionManager.getDeniedMessage(player, blockType);
            player.sendMessage(message);
            return;
        }

        // 以降の報酬/経験値/クエスト処理は従来どおりフィルタの対象
        if (!shouldProcessEvent(event)) {
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
        
        // TofuNomics 対象ワールドでのみ設置系の制限を適用する
        // （ロビー・ミニゲーム等の他ワールドには干渉しない）
        if (isJobRestrictionWorld(player)) {
            // 鉱石ブロックの設置を禁止（管理者権限がない場合）
            if (isOreBlock(blockType) && !player.hasPermission("tofunomics.place.ore")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "鉱石ブロックは設置できません。");
                return;
            }

            // 職業別 植え付け制限チェック（農家以外の作物植え付けを拒否）
            if (!blockPermissionManager.canPlayerPlantBlock(player, blockType)) {
                event.setCancelled(true);
                player.sendMessage(blockPermissionManager.getPlantingDeniedMessage(player, blockType));
                return;
            }
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
    
    /**
     * 種アイテムによる植え付け制限（農家以外の種まきを拒否）
     * 畑・ソウルサンドへの種まきは BlockPlaceEvent を発火しないため、
     * PlayerInteractEvent（右クリック）で捕捉する。
     * ブロックアイテム（サトウキビ・サボテン・竹・ココア等）は onBlockPlace 側で処理する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractPlanting(PlayerInteractEvent event) {
        // 右クリック（ブロックに対して）のみ対象
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 両手分の二重発火を防ぐためメインハンドのみ処理
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        // 手に持っているアイテムが種アイテムかチェック
        Material cropBlock = blockPermissionManager.getCropBlockForSeed(item.getType());
        if (cropBlock == null) return;

        // クリックしたブロックが植え付け可能な面かチェック（食用作物の誤キャンセル防止）
        if (event.getClickedBlock() == null) return;
        Material clicked = event.getClickedBlock().getType();
        boolean validSurface = (cropBlock == Material.NETHER_WART)
            ? (clicked == Material.SOUL_SAND)
            : (clicked == Material.FARMLAND);
        if (!validSurface) return;

        Player player = event.getPlayer();
        // TofuNomics 対象ワールド以外では制限しない
        if (!isJobRestrictionWorld(player)) return;

        if (!blockPermissionManager.canPlayerPlantBlock(player, cropBlock)) {
            event.setCancelled(true);
            player.sendMessage(blockPermissionManager.getPlantingDeniedMessage(player, cropBlock));
        }
    }

    /**
     * 職業制限（採掘・植え付け・鉱石設置）を適用すべきワールドかどうかを判定する。
     * CraftRestrictionEventHandler と同じ基準（events.excluded_worlds 除外 +
     * economy.enabled_worlds ホワイトリスト）。
     */
    private boolean isJobRestrictionWorld(Player player) {
        if (configManager == null) {
            // 設定が取得できない場合は安全側（制限なし）に倒す
            return false;
        }
        return configManager.isJobRestrictionEnabledInWorld(player.getWorld().getName());
    }

    /**
     * 骨粉による作物成長。JobExperienceManager 側に農家の経験値処理があるため委譲する。
     * （従来はどこからも呼ばれておらず、骨粉の経験値が入っていなかった）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(org.bukkit.event.block.BlockFertilizeEvent event) {
        if (!shouldProcessEvent(event)) return;

        experienceManager.onBlockFertilize(event);
    }

    /**
     * 右クリック収穫。JobExperienceManager 側に農家の経験値処理があるため委譲する。
     * （従来はどこからも呼ばれておらず、右クリック収穫の経験値が入っていなかった）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractHarvest(PlayerInteractEvent event) {
        if (!shouldProcessEvent(event)) return;

        experienceManager.onPlayerInteractHarvest(event);
    }

    /**
     * 鍛冶台での加工。JobExperienceManager 側に鍛冶屋の経験値処理があるため委譲する。
     * （従来はどこからも呼ばれておらず、鍛冶台の経験値が入っていなかった）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithItem(org.bukkit.event.inventory.SmithItemEvent event) {
        if (!shouldProcessEvent(event)) return;

        experienceManager.onSmithItem(event);
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

        // 職業別クラフト制限は CraftRestrictionEventHandler(LOWEST) が担当する。
        // そこでキャンセルされたイベントは ignoreCancelled = true により
        // このハンドラーには届かないため、ここでは経験値・クエスト処理のみ行う。

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
        
        // 釣り人: 海洋系モブ討伐による経験値付与
        experienceManager.onFishermanEntityKill(player, event.getEntity());
        
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerShearRestriction(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        org.bukkit.entity.Entity target = event.getEntity();

        // 管理者はバイパス（既存の採掘制限と同じ流儀）
        boolean admin = player.hasPermission("tofunomics.admin.shear");

        // 職業判定は毎イベント走らせたくないため、拒否判定側で遅延評価する
        if (!shouldDenyShear(isJobRestrictionWorld(player), isShearableLivestock(target), admin,
                () -> jobManager.hasJob(player, "farmer"))) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("§c毛刈りができるのは農家のみです。");
    }

    /**
     * 毛刈り（シアー）を制限対象とするエンティティかどうか。
     * 雪ゴーレム等は羊毛経済と無関係のため対象外。
     */
    static boolean isShearableLivestock(org.bukkit.entity.Entity target) {
        return target instanceof org.bukkit.entity.Sheep
                || target instanceof org.bukkit.entity.MushroomCow;
    }

    /**
     * 毛刈りを拒否すべきかどうかの純粋判定（テスト容易性のため副作用を分離）。
     *
     * TofuNomics 対象ワールド以外では拒否しない。ロビーやミニゲームワールドでも
     * 「毛刈りができるのは農家のみです。」が出て刈れなくなる不具合の再発防止であり、
     * onBlockBreak / onPlayerInteractPlanting のワールド判定と同じ基準を用いる。
     */
    static boolean shouldDenyShear(boolean inJobRestrictionWorld, boolean shearableLivestock,
                                   boolean admin, java.util.function.BooleanSupplier isFarmer) {
        if (!inJobRestrictionWorld) return false;
        if (!shearableLivestock) return false;
        if (admin) return false;
        return !isFarmer.getAsBoolean();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShear(PlayerShearEntityEvent event) {
        if (!shouldProcessEvent(event)) return;

        Player player = event.getPlayer();

        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "player_shear", 500)) {
            return;
        }

        // 農家専用処理（羊毛・キノコ牛の刈り取り）
        farmingActivityHandler.handleShear(event);

        // キャッシュに記録
        eventCache.markAsProcessed(player, "player_shear");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        if (!shouldProcessEvent(event)) return;

        Player player = event.getPlayer();

        // キャッシュチェック
        if (eventCache.isRecentlyProcessed(player, "egg_throw", 500)) {
            return;
        }

        // 農家専用処理（卵の孵化）
        farmingActivityHandler.handleEggHatch(event);

        // キャッシュに記録
        eventCache.markAsProcessed(player, "egg_throw");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractComposter(PlayerInteractEvent event) {
        if (!shouldProcessEvent(event)) return;

        // 農家専用処理（コンポスターの骨粉生成）
        // 満杯(LEVEL 8)の取り出し時のみ成立し、取り出すとLEVEL 0になるため
        // 1操作で1回しか経験値が入らない。材料消費も伴うためキャッシュ不要。
        // 両手分の二重発火はハンドラ側でメインハンド限定にして防ぐ。
        farmingActivityHandler.handleComposterHarvest(event);
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
    static boolean shouldTrackPlacedBlock(Material blockType) {
        // 鉱石類は設置禁止のため追跡不要（isOreBlockで判定）
        
        // STONE, COBBLESTONE（無限経験値防止）
        // 深層岩石材（DEEPSLATE/COBBLED_DEEPSLATE/TUFF）および岩系（ANDESITE/DIORITE/GRANITE）は
        // STONEへ正規化され採掘経験値の対象になるため、設置→採掘による無限経験値を防ぐべく追跡する
        if (blockType == Material.STONE || blockType == Material.COBBLESTONE ||
            blockType == Material.DEEPSLATE || blockType == Material.COBBLED_DEEPSLATE ||
            blockType == Material.TUFF ||
            blockType == Material.ANDESITE || blockType == Material.DIORITE ||
            blockType == Material.GRANITE) {
            return true;
        }

        // 黒曜石・グロウストーン・アメジスト（採掘経験値対象。建材として設置可能なため、
        // 設置→採掘による無限経験値を防ぐべく追跡する）
        if (blockType == Material.OBSIDIAN || blockType == Material.CRYING_OBSIDIAN ||
            blockType == Material.GLOWSTONE || blockType == Material.AMETHYST_CLUSTER) {
            return true;
        }
        
        // 原木類
        if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_STEM")) {
            return true;
        }
        
        // 農作物類（茎から自然成長したブロックを収穫するため、手動設置→破壊の悪用のみ防ぐ）
        // カボチャ・スイカ・サトウキビは、収穫対象が「茎から自然成長したブロック」であり
        // プレイヤー設置ブロックではないため、追跡しても正規の収穫には影響しない。
        if (blockType == Material.PUMPKIN || blockType == Material.MELON ||
            blockType == Material.SUGAR_CANE) {
            return true;
        }

        // 成熟度ゲートのある作物（小麦・ニンジン・ジャガイモ・ビートルート・ネザーウォート）は
        // あえて追跡しない。これらはプレイヤーが種を植えた作物ブロックそのものを収穫するため、
        // 追跡すると「自分で植えて収穫する」農業の基本ループで経験値が入らなくなる。
        // 無限ファーミングは JobExperienceManager.isCropFullyGrown()（完全成長時のみ付与）で
        // 既に防止済みのため、追跡対象から除外しても悪用は不可能。

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