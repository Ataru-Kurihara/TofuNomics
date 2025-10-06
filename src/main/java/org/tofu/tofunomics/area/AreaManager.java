package org.tofu.tofunomics.area;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.models.AreaZone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * エリア管理マネージャー
 * エリアの定義、プレイヤーの進入履歴、クールダウン管理を行う
 */
public class AreaManager {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final Map<String, AreaZone> areas;
    private final Map<UUID, Map<String, Long>> playerAreaHistory;
    private final int cooldownSeconds;
    private boolean enabled;

    /**
     * コンストラクタ
     *
     * @param plugin プラグインインスタンス
     * @param configManager 設定マネージャー
     * @param logger ロガー
     */
    public AreaManager(org.bukkit.plugin.java.JavaPlugin plugin, ConfigManager configManager, Logger logger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = logger;
        this.areas = new HashMap<>();
        this.playerAreaHistory = new ConcurrentHashMap<>();
        this.cooldownSeconds = plugin.getConfig().getInt("area_system.cooldown_seconds", 60);
        this.enabled = plugin.getConfig().getBoolean("area_system.enabled", true);

        loadAreas();
    }

    /**
     * config.ymlからエリア情報を読み込み
     */
    private void loadAreas() {
        if (!enabled) {
            logger.info("エリアシステムは無効化されています");
            return;
        }

        ConfigurationSection areasSection = plugin.getConfig().getConfigurationSection("area_system.areas");
        if (areasSection == null) {
            logger.warning("エリア設定が見つかりません（area_system.areas）");
            return;
        }

        int loadedCount = 0;
        for (String areaId : areasSection.getKeys(false)) {
            try {
                AreaZone area = loadAreaFromConfig(areaId, areasSection.getConfigurationSection(areaId));
                if (area != null) {
                    areas.put(areaId, area);
                    loadedCount++;
                    logger.info("エリアを読み込みました: " + area.getName() + " (" + areaId + ")");
                }
            } catch (Exception e) {
                logger.warning("エリア読み込みエラー (" + areaId + "): " + e.getMessage());
            }
        }

        logger.info("合計 " + loadedCount + " 個のエリアを読み込みました");
    }

    /**
     * 設定セクションからエリアを構築
     */
    private AreaZone loadAreaFromConfig(String areaId, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String name = section.getString("name", areaId);
        String world = section.getString("world", "tofuNomics");
        int minX = section.getInt("min_x", 0);
        int minY = section.getInt("min_y", 0);
        int minZ = section.getInt("min_z", 0);
        int maxX = section.getInt("max_x", 0);
        int maxY = section.getInt("max_y", 256);
        int maxZ = section.getInt("max_z", 0);
        String title = section.getString("title", "");
        String subtitle = section.getString("subtitle", "");
        String message = section.getString("message", "");

        // 効果音の読み込み（無効な場合はnull）
        Sound sound = null;
        String soundName = section.getString("sound", "BLOCK_NOTE_BLOCK_BELL");
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            logger.warning("無効な効果音: " + soundName + " (エリア: " + areaId + ")");
        }

        return new AreaZone(areaId, name, world, minX, minY, minZ, maxX, maxY, maxZ,
                           title, subtitle, message, sound);
    }

    /**
     * プレイヤーの現在位置がどのエリアに属するかを判定
     *
     * @param location プレイヤーの位置
     * @return エリアID（エリア外の場合null）
     */
    public AreaZone getAreaAtLocation(Location location) {
        if (location == null || !enabled) {
            return null;
        }

        for (AreaZone area : areas.values()) {
            if (area.contains(location)) {
                return area;
            }
        }

        return null;
    }

    /**
     * プレイヤーがエリアに進入できるかチェック（クールダウン確認）
     *
     * @param playerId プレイヤーUUID
     * @param areaId エリアID
     * @return 進入可能な場合true
     */
    public boolean canEnterArea(UUID playerId, String areaId) {
        if (!enabled) {
            return false;
        }

        Map<String, Long> history = playerAreaHistory.get(playerId);
        if (history == null) {
            return true;
        }

        Long lastEnterTime = history.get(areaId);
        if (lastEnterTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastEnterTime) / 1000;

        return elapsedSeconds >= cooldownSeconds;
    }

    /**
     * プレイヤーのエリア進入を記録
     *
     * @param playerId プレイヤーUUID
     * @param areaId エリアID
     */
    public void recordAreaEntry(UUID playerId, String areaId) {
        if (!enabled) {
            return;
        }

        playerAreaHistory.computeIfAbsent(playerId, k -> new HashMap<>())
                        .put(areaId, System.currentTimeMillis());
    }

    /**
     * プレイヤーの履歴をクリア（ログアウト時など）
     *
     * @param playerId プレイヤーUUID
     */
    public void clearPlayerHistory(UUID playerId) {
        playerAreaHistory.remove(playerId);
    }

    /**
     * エリアシステムが有効かどうか
     *
     * @return 有効な場合true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 読み込まれたエリアの数を取得
     *
     * @return エリア数
     */
    public int getAreaCount() {
        return areas.size();
    }

    /**
     * すべてのエリアを取得
     *
     * @return エリアのコレクション
     */
    public Collection<AreaZone> getAllAreas() {
        return new ArrayList<>(areas.values());
    }

    /**
     * IDからエリアを取得
     *
     * @param areaId エリアID
     * @return エリア（存在しない場合null）
     */
    public AreaZone getAreaById(String areaId) {
        return areas.get(areaId);
    }

    /**
     * エリア設定を再読み込み
     */
    public void reload() {
        areas.clear();
        this.enabled = plugin.getConfig().getBoolean("area_system.enabled", true);
        loadAreas();
    }

    /**
     * クリーンアップ処理
     */
    public void cleanup() {
        playerAreaHistory.clear();
        areas.clear();
    }
}
