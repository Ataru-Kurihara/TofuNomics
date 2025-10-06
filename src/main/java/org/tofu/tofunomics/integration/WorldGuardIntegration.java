package org.tofu.tofunomics.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * WorldGuardとの統合を管理するクラス
 */
public class WorldGuardIntegration {
    private final Plugin plugin;
    private final Logger logger;
    private final WorldGuard worldGuard;
    private final RegionContainer regionContainer;
    private boolean enabled;

    public WorldGuardIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // WorldGuardが有効かチェック
        Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin != null && wgPlugin instanceof WorldGuardPlugin) {
            this.worldGuard = WorldGuard.getInstance();
            this.regionContainer = worldGuard.getPlatform().getRegionContainer();
            this.enabled = true;
            logger.info("WorldGuard統合が有効化されました");
        } else {
            this.worldGuard = null;
            this.regionContainer = null;
            this.enabled = false;
            logger.warning("WorldGuardが見つかりません。WorldGuard機能は無効です");
        }
    }

    /**
     * WorldGuardが有効かチェック
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 座標範囲からWorldGuard領域を作成
     * 
     * @param regionId 領域ID
     * @param world ワールド
     * @param pos1 第1座標
     * @param pos2 第2座標
     * @return 成功した場合true
     */
    public boolean createRegion(String regionId, World world, Location pos1, Location pos2) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なため領域を作成できません");
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                logger.warning("WorldGuardのRegionManagerを取得できませんでした");
                return false;
            }

            // 既存の領域をチェック
            if (regionManager.hasRegion(regionId)) {
                logger.warning("領域 " + regionId + " は既に存在します");
                return false;
            }

            // 座標からBlockVector3を作成
            BlockVector3 min = BlockVector3.at(
                Math.min(pos1.getBlockX(), pos2.getBlockX()),
                Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ())
            );
            BlockVector3 max = BlockVector3.at(
                Math.max(pos1.getBlockX(), pos2.getBlockX()),
                Math.max(pos1.getBlockY(), pos2.getBlockY()),
                Math.max(pos1.getBlockZ(), pos2.getBlockZ())
            );

            // Cuboid領域を作成
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

            // フラグ設定: 非メンバーはドア・チェスト使用不可
            region.setFlag(Flags.USE, StateFlag.State.DENY);
            region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.DENY);
            region.setFlag(Flags.INTERACT, StateFlag.State.DENY);

            // 領域を登録
            regionManager.addRegion(region);

            logger.info("WorldGuard領域 " + regionId + " を作成しました");
            return true;

        } catch (Exception e) {
            logger.severe("WorldGuard領域の作成に失敗しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 領域にプレイヤーをメンバーとして追加
     * 
     * @param regionId 領域ID
     * @param world ワールド
     * @param playerUuid プレイヤーUUID
     * @return 成功した場合true
     */
    public boolean addMember(String regionId, World world, UUID playerUuid) {
        if (!enabled) {
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return false;
            }

            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) {
                logger.warning("領域 " + regionId + " が見つかりません");
                return false;
            }

            DefaultDomain members = region.getMembers();
            members.addPlayer(playerUuid);
            region.setMembers(members);

            logger.info("プレイヤー " + playerUuid + " を領域 " + regionId + " のメンバーに追加しました");
            return true;

        } catch (Exception e) {
            logger.severe("メンバー追加に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * 領域からプレイヤーをメンバーから削除
     * 
     * @param regionId 領域ID
     * @param world ワールド
     * @param playerUuid プレイヤーUUID
     * @return 成功した場合true
     */
    public boolean removeMember(String regionId, World world, UUID playerUuid) {
        if (!enabled) {
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return false;
            }

            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) {
                logger.warning("領域 " + regionId + " が見つかりません");
                return false;
            }

            DefaultDomain members = region.getMembers();
            members.removePlayer(playerUuid);
            region.setMembers(members);

            logger.info("プレイヤー " + playerUuid + " を領域 " + regionId + " のメンバーから削除しました");
            return true;

        } catch (Exception e) {
            logger.severe("メンバー削除に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * 領域を削除
     * 
     * @param regionId 領域ID
     * @param world ワールド
     * @return 成功した場合true
     */
    public boolean removeRegion(String regionId, World world) {
        if (!enabled) {
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return false;
            }

            boolean removed = regionManager.removeRegion(regionId) != null;
            if (removed) {
                logger.info("領域 " + regionId + " を削除しました");
                return true;
            } else {
                logger.warning("領域 " + regionId + " が見つかりません");
                return false;
            }

        } catch (Exception e) {
            logger.severe("領域削除に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * 領域が存在するかチェック
     * 
     * @param regionId 領域ID
     * @param world ワールド
     * @return 存在する場合true
     */
    public boolean hasRegion(String regionId, World world) {
        if (!enabled) {
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return false;
            }

            return regionManager.hasRegion(regionId);

        } catch (Exception e) {
            logger.severe("領域チェックに失敗しました: " + e.getMessage());
            return false;
        }
    }
}
