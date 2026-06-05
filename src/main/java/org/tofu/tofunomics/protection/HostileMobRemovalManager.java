package org.tofu.tofunomics.protection;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.integration.WorldGuardIntegration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WorldGuardリージョン内の敵対的モブを自動的に除去するマネージャー
 */
public class HostileMobRemovalManager {
    private final Plugin plugin;
    private final WorldGuardIntegration worldGuardIntegration;
    private final Logger logger;

    // ワールドごとの除去対象リージョンを管理
    // Key: ワールド名, Value: リージョンIDのセット
    private final Map<String, Set<String>> protectedRegions;

    // 定期タスク
    private BukkitTask monitoringTask;

    // スキャン間隔（ティック）
    private static final long SCAN_INTERVAL_TICKS = 100L; // 5秒 (100 ticks = 5秒)

    // 除去統計
    private long totalRemovedMobs = 0;

    public HostileMobRemovalManager(Plugin plugin, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.worldGuardIntegration = worldGuardIntegration;
        this.logger = plugin.getLogger();
        this.protectedRegions = new ConcurrentHashMap<>();
    }

    /**
     * モニタリングを開始
     */
    public void startMonitoring() {
        if (monitoringTask != null) {
            logger.warning("HostileMobRemovalManagerは既に実行中です");
            return;
        }

        if (!worldGuardIntegration.isEnabled()) {
            logger.warning("WorldGuardが無効なため、モブ自動除去を開始できません");
            return;
        }

        monitoringTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAndRemoveHostileMobs,
            SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);

        logger.info("敵対的モブ自動除去システムを開始しました（スキャン間隔: " + (SCAN_INTERVAL_TICKS / 20) + "秒）");
    }

    /**
     * モニタリングを停止
     */
    public void stopMonitoring() {
        if (monitoringTask != null) {
            monitoringTask.cancel();
            monitoringTask = null;
            logger.info("敵対的モブ自動除去システムを停止しました（累計除去数: " + totalRemovedMobs + "）");
        }
    }

    /**
     * リージョンを除去対象に追加
     *
     * @param regionId リージョンID
     * @param world ワールド
     */
    public void addRegion(String regionId, World world) {
        String worldName = world.getName();
        protectedRegions.computeIfAbsent(worldName, k -> new HashSet<>()).add(regionId);
        logger.info("リージョン '" + regionId + "' (ワールド: " + worldName + ") をモブ自動除去対象に追加しました");
    }

    /**
     * リージョンを除去対象から削除
     *
     * @param regionId リージョンID
     * @param world ワールド
     */
    public void removeRegion(String regionId, World world) {
        String worldName = world.getName();
        Set<String> regions = protectedRegions.get(worldName);
        if (regions != null) {
            boolean removed = regions.remove(regionId);
            if (removed) {
                logger.info("リージョン '" + regionId + "' (ワールド: " + worldName + ") をモブ自動除去対象から削除しました");

                // ワールドのリージョンリストが空になったら削除
                if (regions.isEmpty()) {
                    protectedRegions.remove(worldName);
                }
            }
        }
    }

    /**
     * 敵対的モブをスキャンして除去
     */
    private void scanAndRemoveHostileMobs() {
        if (protectedRegions.isEmpty()) {
            return; // 除去対象リージョンがなければスキップ
        }

        int removedCount = 0;

        try {
            // 各ワールドごとに処理
            for (Map.Entry<String, Set<String>> entry : protectedRegions.entrySet()) {
                String worldName = entry.getKey();
                Set<String> regionIds = entry.getValue();

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    logger.warning("ワールド '" + worldName + "' が見つかりません");
                    continue;
                }

                // ワールド内の敵対的モブをスキャン
                for (Entity entity : world.getEntities()) {
                    // 敵対的モブのみチェック
                    if (!isHostileMob(entity)) {
                        continue;
                    }

                    // エンティティが除去対象リージョン内にいるかチェック
                    boolean inProtectedRegion = worldGuardIntegration.isInProtectedRegion(entity.getLocation());

                    if (inProtectedRegion) {
                        entity.remove();
                        removedCount++;
                        totalRemovedMobs++;
                    }
                }
            }

            if (removedCount > 0) {
                logger.fine("敵対的モブを " + removedCount + " 体除去しました");
            }

        } catch (Exception e) {
            logger.severe("モブスキャン中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * エンティティが敵対的モブかチェック
     *
     * @param entity チェックするエンティティ
     * @return 敵対的モブの場合true
     */
    private boolean isHostileMob(Entity entity) {
        // Monsterインターフェースを実装しているエンティティは敵対的
        if (entity instanceof Monster) {
            return true;
        }

        // 追加で特定のエンティティタイプをチェック
        EntityType type = entity.getType();
        switch (type) {
            case SLIME:
            case PHANTOM:
            case MAGMA_CUBE:
            case SHULKER:
            case HOGLIN:
            case ZOGLIN:
                return true;
            default:
                return false;
        }
    }

    /**
     * 累計除去数を取得
     *
     * @return 累計除去数
     */
    public long getTotalRemovedMobs() {
        return totalRemovedMobs;
    }

    /**
     * 現在の除去対象リージョン数を取得
     *
     * @return 除去対象リージョン数
     */
    public int getProtectedRegionCount() {
        return protectedRegions.values().stream()
            .mapToInt(Set::size)
            .sum();
    }

    /**
     * 特定のリージョンが除去対象に含まれているかチェック
     *
     * @param regionId リージョンID
     * @param world ワールド
     * @return 除去対象の場合true
     */
    public boolean isRegionProtected(String regionId, World world) {
        Set<String> regions = protectedRegions.get(world.getName());
        return regions != null && regions.contains(regionId);
    }
}
