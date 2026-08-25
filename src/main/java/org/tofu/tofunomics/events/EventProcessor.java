package org.tofu.tofunomics.events;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * イベント処理振り分けシステム
 * イベントを適切なハンドラに振り分け、処理すべきかどうかを判定
 */
public class EventProcessor {
    
    private final ConfigManager configManager;
    private final JobManager jobManager;
    
    // 除外ワールドのキャッシュ
    private final Set<String> excludedWorlds;

    // 経済機能を有効にするワールド（ホワイトリスト）のキャッシュ
    private final Set<String> enabledWorlds;
    
    // 除外ゲームモード
    private final Set<GameMode> excludedGameModes;
    
    public EventProcessor(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.excludedWorlds = new HashSet<>();
        this.enabledWorlds = new HashSet<>();
        this.excludedGameModes = new HashSet<>();

        initializeExclusions();
    }
    
    /**
     * 除外設定の初期化
     */
    private void initializeExclusions() {
        // 除外ワールドの設定
        List<String> worlds = configManager.getExcludedWorlds();
        if (worlds != null) {
            excludedWorlds.addAll(worlds);
        }

        // 経済機能を有効にするワールド（ホワイトリスト）の設定
        List<String> economyWorlds = configManager.getEconomyEnabledWorlds();
        if (economyWorlds != null) {
            enabledWorlds.addAll(economyWorlds);
        }

        // 除外ゲームモードの設定
        excludedGameModes.add(GameMode.CREATIVE);
        excludedGameModes.add(GameMode.SPECTATOR);
        
        // configから追加の除外ゲームモード読み込み（将来の拡張用）
        List<String> modes = configManager.getExcludedGameModes();
        if (modes != null) {
            for (String mode : modes) {
                try {
                    excludedGameModes.add(GameMode.valueOf(mode.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // 無効なゲームモード名は無視
                }
            }
        }
    }

    /**
     * 設定の再読み込み（リロードコマンド時に呼び出し）
     * enabledWorlds / excludedWorlds / excludedGameModes を config から再構築する。
     * これを呼ばないと /tofunomics reload では economy.enabled_worlds 等が反映されず、
     * 起動時の値（空＝全ワールド対象）のまま動作し続けてしまう。
     */
    public void reloadConfiguration() {
        excludedWorlds.clear();
        enabledWorlds.clear();
        excludedGameModes.clear();
        initializeExclusions();
    }

    /**
     * イベント処理を行うべきか判定
     * @param event イベント
     * @return 処理すべき場合true
     */
    public boolean shouldProcessEvent(Event event) {
        Player player = extractPlayer(event);
        if (player == null) {
            return false;
        }
        
        // プレイヤーの基本チェック
        if (!isValidPlayer(player)) {
            return false;
        }
        
        // ワールドチェック
        if (!isValidWorld(player.getWorld())) {
            return false;
        }
        
        // ゲームモードチェック
        if (!isValidGameMode(player.getGameMode())) {
            return false;
        }
        
        // 権限チェック
        if (!hasRequiredPermission(player, event)) {
            return false;
        }
        
        // 職業チェック
        if (!hasValidJob(player, event)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * イベントからプレイヤーを抽出
     */
    private Player extractPlayer(Event event) {
        if (event instanceof PlayerEvent) {
            return ((PlayerEvent) event).getPlayer();
        }
        
        if (event instanceof BlockBreakEvent) {
            return ((BlockBreakEvent) event).getPlayer();
        }
        
        if (event instanceof BlockPlaceEvent) {
            return ((BlockPlaceEvent) event).getPlayer();
        }

        // 骨粉による成長。ディスペンサー由来の場合は getPlayer() が null になり、
        // その場合は処理対象外（プレイヤーの行動ではない）となる。
        if (event instanceof org.bukkit.event.block.BlockFertilizeEvent) {
            return ((org.bukkit.event.block.BlockFertilizeEvent) event).getPlayer();
        }
        
        if (event instanceof InventoryEvent) {
            if (((InventoryEvent) event).getView().getPlayer() instanceof Player) {
                return (Player) ((InventoryEvent) event).getView().getPlayer();
            }
        }
        
        if (event instanceof EntityEvent) {
            if (((EntityEvent) event).getEntity() instanceof Player) {
                return (Player) ((EntityEvent) event).getEntity();
            }
        }
        
        return null;
    }
    
    /**
     * プレイヤーの妥当性チェック
     */
    private boolean isValidPlayer(Player player) {
        if (player == null) {
            return false;
        }
        
        if (!player.isOnline()) {
            return false;
        }
        
        // NPCチェック（他プラグインのNPCを除外）
        if (player.hasMetadata("NPC")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * ワールドの妥当性チェック
     */
    private boolean isValidWorld(World world) {
        if (world == null) {
            return false;
        }
        
        String worldName = world.getName();
        // 除外ワールドはスキップ
        if (excludedWorlds.contains(worldName)) {
            return false;
        }

        // 経済機能を有効にするワールドのホワイトリストチェック
        // （リストが空の場合は全ワールドで有効：後方互換）
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(worldName)) {
            return false;
        }

        return true;
    }
    
    /**
     * ゲームモードの妥当性チェック
     */
    private boolean isValidGameMode(GameMode gameMode) {
        if (gameMode == null) {
            return false;
        }
        
        if (excludedGameModes.contains(gameMode)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 必要な権限をチェック
     */
    private boolean hasRequiredPermission(Player player, Event event) {
        // 基本的な権限チェック
        if (!player.hasPermission("tofunomics.use")) {
            // デフォルトでは全員に権限があるものとする
            // （権限が明示的に設定されていない場合はtrue）
            if (player.isPermissionSet("tofunomics.use")) {
                return false;
            }
        }
        
        // イベント固有の権限チェック
        String eventPermission = getEventPermission(event);
        if (eventPermission != null) {
            if (!player.hasPermission(eventPermission)) {
                if (player.isPermissionSet(eventPermission)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * イベントに対応する権限を取得
     */
    private String getEventPermission(Event event) {
        String className = event.getClass().getSimpleName();
        
        switch (className) {
            case "BlockBreakEvent":
                return "tofunomics.event.blockbreak";
            case "BlockPlaceEvent":
                return "tofunomics.event.blockplace";
            case "CraftItemEvent":
                return "tofunomics.event.craft";
            case "EnchantItemEvent":
                return "tofunomics.event.enchant";
            case "BrewEvent":
                return "tofunomics.event.brew";
            case "PlayerFishEvent":
                return "tofunomics.event.fish";
            case "EntityDeathEvent":
                return "tofunomics.event.kill";
            case "EntityBreedEvent":
                return "tofunomics.event.breed";
            default:
                return null;
        }
    }
    
    /**
     * 有効な職業を持っているかチェック
     */
    private boolean hasValidJob(Player player, Event event) {
        // 特定のイベントは職業なしでも処理可能
        if (isJobOptionalEvent(event)) {
            return true;
        }

        // プレイヤーが少なくとも1つの職業を持っているかチェック
        List<PlayerJob> jobs = jobManager.getPlayerJobs(player);
        if (jobs == null || jobs.isEmpty()) {
            return false;
        }

        // アクティブな職業があるかチェック
        for (PlayerJob job : jobs) {
            if (job.isActive()) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * 職業が必須でないイベントかチェック
     */
    private boolean isJobOptionalEvent(Event event) {
        // 基本的な移動やチャットなどのイベントは職業不要
        String className = event.getClass().getSimpleName();
        
        switch (className) {
            case "PlayerMoveEvent":
            case "PlayerChatEvent":
            case "PlayerJoinEvent":
            case "PlayerQuitEvent":
            case "BlockBreakEvent":  // ブロック破壊は職業チェックをスキップし、具体的な制限は後で行う
            case "BlockPlaceEvent":  // ブロック設置も同様
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 除外ワールドリストを更新
     */
    public void updateExcludedWorlds(List<String> worlds) {
        excludedWorlds.clear();
        if (worlds != null) {
            excludedWorlds.addAll(worlds);
        }
    }
    
    /**
     * 除外ゲームモードリストを更新
     */
    public void updateExcludedGameModes(List<GameMode> modes) {
        excludedGameModes.clear();
        excludedGameModes.add(GameMode.CREATIVE); // 常に除外
        excludedGameModes.add(GameMode.SPECTATOR); // 常に除外
        if (modes != null) {
            excludedGameModes.addAll(modes);
        }
    }
    
    /**
     * 統計情報を取得
     */
    public ProcessorStatistics getStatistics() {
        return new ProcessorStatistics(
            excludedWorlds.size(),
            excludedGameModes.size()
        );
    }
    
    /**
     * プロセッサー統計クラス
     */
    public static class ProcessorStatistics {
        private final int excludedWorldCount;
        private final int excludedGameModeCount;
        
        public ProcessorStatistics(int excludedWorldCount, int excludedGameModeCount) {
            this.excludedWorldCount = excludedWorldCount;
            this.excludedGameModeCount = excludedGameModeCount;
        }
        
        public int getExcludedWorldCount() { return excludedWorldCount; }
        public int getExcludedGameModeCount() { return excludedGameModeCount; }
    }
}