package org.tofu.tofunomics.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionQuery;
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

            // フラグ設定: デフォルトのメンバーシステムに任せる
            // メンバーとして追加されたプレイヤーは自由に行動可能
            // 非メンバーは親領域の設定に従う

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
     * プレイヤーが指定された場所で操作可能かをチェック
     * WorldGuardの保護設定を考慮してチェックする
     * 
     * @param player チェックするプレイヤー
     * @param location チェックする場所
     * @return 操作可能な場合true、保護されていて操作できない場合false
     */
    public boolean canInteract(org.bukkit.entity.Player player, org.bukkit.Location location) {
        if (!enabled) {
            // WorldGuardが無効な場合は常に操作可能
            return true;
        }

        try {
            // Bukkit PlayerをWorldGuard LocalPlayerに変換
            com.sk89q.worldguard.LocalPlayer localPlayer = 
                WorldGuardPlugin.inst().wrapPlayer(player);
            
            // RegionManagerを取得
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager != null) {
                // 指定位置に適用される領域を取得
                ApplicableRegionSet regions = regionManager.getApplicableRegions(
                    BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                );
                
                // 適用される領域があるかチェック
                if (!regions.getRegions().isEmpty()) {
                    // いずれかの領域のメンバーまたはオーナーかチェック
                    for (ProtectedRegion region : regions) {
                        if (region.isMember(localPlayer) || region.isOwner(localPlayer)) {
                            logger.fine("プレイヤー " + player.getName() + " は領域 " + region.getId() + " のメンバーです");
                            return true;
                        }
                    }
                    // どの領域のメンバーでもオーナーでもない場合は拒否
                    logger.fine("プレイヤー " + player.getName() + " は保護領域のメンバーではありません");
                    return false;
                }
            }
            
            // 保護領域がない場合は操作可能
            return true;
            
        } catch (Exception e) {
            logger.warning("WorldGuard保護チェック中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            // エラー時は安全側に倒して操作を拒否
            return false;
        }
    }

    /**
     * 指定された場所が保護領域内かどうかをチェック
     * プレイヤーの権限は考慮せず、単純に領域内かどうかのみを判定
     * 
     * @param location チェックする場所
     * @return 保護領域内の場合true、領域外の場合false
     */
    public boolean isInProtectedRegion(org.bukkit.Location location) {
        logger.info("WorldGuardIntegration.isInProtectedRegion: チェック開始 - 座標: " + 
            location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        
        if (!enabled) {
            logger.info("WorldGuardIntegration.isInProtectedRegion: WorldGuardが無効");
            // WorldGuardが無効な場合は保護されていないとみなす
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) {
                logger.warning("WorldGuardIntegration.isInProtectedRegion: RegionManagerがnull");
                return false;
            }

            // その場所に適用される領域があるかチェック
            com.sk89q.worldedit.math.BlockVector3 position = BlockVector3.at(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            );
            
            // 適用される領域のセットを取得
            java.util.Set<ProtectedRegion> regions = regionManager.getApplicableRegions(position).getRegions();
            
            logger.info("WorldGuardIntegration.isInProtectedRegion: 検出された領域数: " + regions.size());
            for (ProtectedRegion region : regions) {
                logger.info("WorldGuardIntegration.isInProtectedRegion: 領域名: " + region.getId());
            }
            
            // 領域が1つでも存在すれば保護領域内
            boolean result = !regions.isEmpty();
            logger.info("WorldGuardIntegration.isInProtectedRegion: 最終結果: " + (result ? "保護領域内" : "保護領域外"));
            return result;

        } catch (Exception e) {
            logger.warning("保護領域チェック中にエラーが発生しました: " + e.getMessage());
            // エラー時は安全側に倒して保護されていないとみなす
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

    /**
     * 子リージョンに親リージョンを設定
     * 
     * @param childRegionId 子リージョンID
     * @param parentRegionId 親リージョンID
     * @param world ワールド
     * @return 成功した場合true
     */
    public boolean setParentRegion(String childRegionId, String parentRegionId, World world) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なため親リージョンを設定できません");
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                logger.warning("WorldGuardのRegionManagerを取得できませんでした");
                return false;
            }

            // 子リージョンを取得
            ProtectedRegion childRegion = regionManager.getRegion(childRegionId);
            if (childRegion == null) {
                logger.warning("子リージョン " + childRegionId + " が見つかりません");
                return false;
            }

            // 親リージョンを取得
            ProtectedRegion parentRegion = regionManager.getRegion(parentRegionId);
            if (parentRegion == null) {
                logger.warning("親リージョン " + parentRegionId + " が見つかりません");
                return false;
            }

            // 親リージョンを設定
            try {
                childRegion.setParent(parentRegion);
                logger.info("リージョン " + childRegionId + " の親リージョンを " + parentRegionId + " に設定しました");
                return true;
            } catch (ProtectedRegion.CircularInheritanceException e) {
                logger.severe("循環参照エラー: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            logger.severe("親リージョンの設定に失敗しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * リージョンにフラグを設定
     * 
     * @param regionId リージョンID
     * @param world ワールド
     * @param flagName フラグ名（"build", "use", "pvp"など）
     * @param state フラグの状態（"allow", "deny"）
     * @return 成功した場合true
     */
    public boolean setRegionFlag(String regionId, World world, String flagName, String state) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なためフラグを設定できません");
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                logger.warning("WorldGuardのRegionManagerを取得できませんでした");
                return false;
            }

            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) {
                logger.warning("リージョン " + regionId + " が見つかりません");
                return false;
            }

            // フラグの取得
            Flag<?> flag = null;
            switch (flagName.toLowerCase()) {
                case "build":
                    flag = Flags.BUILD;
                    break;
                case "use":
                    flag = Flags.USE;
                    break;
                case "pvp":
                    flag = Flags.PVP;
                    break;
                case "interact":
                    flag = Flags.INTERACT;
                    break;
                case "block-break":
                    flag = Flags.BLOCK_BREAK;
                    break;
                case "block-place":
                    flag = Flags.BLOCK_PLACE;
                    break;
                default:
                    logger.warning("未対応のフラグ: " + flagName);
                    return false;
            }

            // 状態の設定
            StateFlag.State flagState = null;
            switch (state.toLowerCase()) {
                case "allow":
                    flagState = StateFlag.State.ALLOW;
                    break;
                case "deny":
                    flagState = StateFlag.State.DENY;
                    break;
                default:
                    logger.warning("未対応の状態: " + state);
                    return false;
            }

            // フラグを設定
            region.setFlag((StateFlag) flag, flagState);

            logger.info("リージョン " + regionId + " のフラグ " + flagName + " を " + state + " に設定しました");
            return true;

        } catch (Exception e) {
            logger.severe("フラグの設定に失敗しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
