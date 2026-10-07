package org.tofu.tofunomics.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.entity.EntityTypes;
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
            
            // 変更を保存
            try {
                regionManager.save();
                logger.info("WorldGuard領域 " + regionId + " をディスクに保存しました");
            } catch (Exception saveException) {
                logger.severe("WorldGuard領域の保存に失敗しました: " + saveException.getMessage());
                // 保存失敗時はリージョンを削除してロールバック
                regionManager.removeRegion(regionId);
                return false;
            }

            logger.info("WorldGuard領域 " + regionId + " を作成しました");
            return true;

        } catch (Exception e) {
            logger.severe("WorldGuard領域の作成に失敗しました: " + e.getMessage());
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
            
            // 変更を保存
            try {
                regionManager.save();
                logger.info("プレイヤー " + playerUuid + " を領域 " + regionId + " のメンバーに追加し、保存しました");
            } catch (Exception saveException) {
                logger.severe("メンバー追加の保存に失敗しました: " + saveException.getMessage());
                return false;
            }
            
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
            
            // 変更を保存
            try {
                regionManager.save();
                logger.info("プレイヤー " + playerUuid + " を領域 " + regionId + " のメンバーから削除し、保存しました");
            } catch (Exception saveException) {
                logger.severe("メンバー削除の保存に失敗しました: " + saveException.getMessage());
                return false;
            }
            
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
                // 変更を保存
                try {
                    regionManager.save();
                    logger.info("領域 " + regionId + " を削除し、保存しました");
                } catch (Exception saveException) {
                    logger.severe("領域削除の保存に失敗しました: " + saveException.getMessage());
                    return false;
                }
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
     * 既存領域を新しい座標範囲で再定義する。
     * WorldGuardのCuboid領域はbounds（min/max）の直接変更APIを持たないため、
     * 同IDで新しい領域を作成し、旧領域のメンバー/オーナー/フラグ/親/優先度を引き継ぐ。
     * （区画リサイズ用。所有者・WGメンバー登録を保持したまま範囲のみ変更する）
     *
     * @param regionId 領域ID
     * @param world ワールド
     * @param pos1 新しい範囲の角1
     * @param pos2 新しい範囲の角2
     * @return 成功した場合true
     */
    public boolean redefineRegion(String regionId, World world, Location pos1, Location pos2) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なため領域を再定義できません");
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return false;
            }

            ProtectedRegion oldRegion = regionManager.getRegion(regionId);
            if (oldRegion == null) {
                logger.warning("再定義対象の領域 " + regionId + " が見つかりません");
                return false;
            }

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

            // 同IDで新しいCuboid領域を作成し、旧領域の属性（メンバー/オーナー/フラグ/親/優先度）を引き継ぐ
            // copyFromはbounds以外をコピーするため、まさにリサイズ用途に適する
            ProtectedCuboidRegion newRegion = new ProtectedCuboidRegion(regionId, min, max);
            newRegion.copyFrom(oldRegion);

            // 同IDでaddRegionすると既存領域を置き換える
            regionManager.addRegion(newRegion);

            // 変更を保存
            try {
                regionManager.save();
                logger.info("WorldGuard領域 " + regionId + " を新しい範囲で再定義しました");
            } catch (Exception saveException) {
                logger.severe("領域再定義の保存に失敗しました: " + saveException.getMessage());
                // 保存失敗時は旧領域を再登録してロールバック
                regionManager.addRegion(oldRegion);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.severe("WorldGuard領域の再定義に失敗しました: " + e.getMessage());
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
                            return true;
                        }
                    }
                    // どの領域のメンバーでもオーナーでもない場合は拒否
                    return false;
                }
            }
            
            // 保護領域がない場合は操作可能
            return true;
            
        } catch (Exception e) {
            logger.warning("WorldGuard保護チェック中にエラーが発生しました: " + e.getMessage());
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
        if (!enabled) {
            // WorldGuardが無効な場合は保護されていないとみなす
            return false;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) {
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

            // 領域が1つでも存在すれば保護領域内
            return !regions.isEmpty();

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
     * 選択範囲(pos1〜pos2)に重なる既存リージョンのID一覧を取得
     * 一時的なCuboid領域を作り、それに適用されるリージョンを列挙する（リージョンは登録しない）
     *
     * @param world ワールド
     * @param pos1 第1座標
     * @param pos2 第2座標
     * @return 重なる既存リージョンIDのリスト（重なりがなければ空リスト）
     */
    public java.util.List<String> getOverlappingRegionNames(World world, Location pos1, Location pos2) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (!enabled) {
            return result;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return result;
            }

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

            // 一時的な領域を作って重なりを判定（登録はしない）
            ProtectedCuboidRegion tempRegion = new ProtectedCuboidRegion("__tofunomics_temp__", min, max);
            for (ProtectedRegion region : regionManager.getApplicableRegions(tempRegion)) {
                result.add(region.getId());
            }
        } catch (Exception e) {
            logger.warning("重なるリージョンの取得に失敗しました: " + e.getMessage());
        }

        return result;
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
                
                // 変更を保存
                try {
                    regionManager.save();
                    logger.info("リージョン " + childRegionId + " の親リージョンを " + parentRegionId + " に設定し、保存しました");
                } catch (Exception saveException) {
                    logger.severe("親リージョン設定の保存に失敗しました: " + saveException.getMessage());
                    // 保存失敗時は親設定をロールバック
                    childRegion.setParent(null);
                    return false;
                }
                
                return true;
            } catch (ProtectedRegion.CircularInheritanceException e) {
                logger.severe("循環参照エラー: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            logger.severe("親リージョンの設定に失敗しました: " + e.getMessage());
            return false;
        }
    }


    /**
     * リージョンの親リージョンIDを取得する。
     *
     * @param regionId 対象リージョンID
     * @param world ワールド
     * @return 親リージョンID。親が未設定／リージョンが存在しない／WG無効の場合は null
     */
    public String getParentRegionName(String regionId, World world) {
        if (!enabled) {
            return null;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                return null;
            }

            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) {
                return null;
            }

            ProtectedRegion parent = region.getParent();
            return parent != null ? parent.getId() : null;

        } catch (Exception e) {
            logger.warning("親リージョンの取得に失敗しました: " + e.getMessage());
            return null;
        }
    }

    /**
     * リージョンにフラグを設定（対象グループの指定なし＝全員に適用）
     *
     * @param regionId リージョンID
     * @param world ワールド
     * @param flagName フラグ名（"build", "use", "chest-access" など WorldGuard に登録された状態フラグ）
     * @param state フラグの状態（"allow", "deny"）
     * @return 成功した場合true
     */
    public boolean setRegionFlag(String regionId, World world, String flagName, String state) {
        java.util.Map<String, String> flags = new java.util.LinkedHashMap<>();
        flags.put(flagName, state);
        return applyRegionFlags(regionId, world, flags, java.util.Collections.<String, String>emptyMap()) == 1;
    }

    /**
     * リージョンに複数のフラグをまとめて設定し、最後に1回だけ保存する
     *
     * WorldGuard のフラグは対象グループを指定しないと全員に適用される。
     * 「メンバー以外だけ拒否」のようにしたい場合は groups で対象を指定する。
     * groups に無いフラグは対象グループを既定（全員）に戻す。
     *
     * @param regionId リージョンID
     * @param world ワールド
     * @param flags フラグ名 → 状態（"allow", "deny"）
     * @param groups フラグ名 → 対象グループ（"all", "members", "owners", "nonmembers", "nonowners"）
     * @return 設定できたフラグの数。リージョンが見つからない等で何も設定できなければ0
     */
    public int applyRegionFlags(String regionId, World world,
                                java.util.Map<String, String> flags,
                                java.util.Map<String, String> groups) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なためフラグを設定できません");
            return 0;
        }

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                logger.warning("WorldGuardのRegionManagerを取得できませんでした");
                return 0;
            }

            ProtectedRegion region = regionManager.getRegion(regionId);
            if (region == null) {
                logger.warning("リージョン " + regionId + " が見つかりません");
                return 0;
            }

            int applied = applyFlagsToRegion(region, flags, groups, worldGuard.getFlagRegistry(), logger);

            if (applied > 0) {
                try {
                    regionManager.save();
                } catch (Exception saveException) {
                    logger.severe("フラグ設定の保存に失敗しました: " + saveException.getMessage());
                    return 0;
                }
            }

            return applied;

        } catch (Exception e) {
            logger.severe("フラグの設定に失敗しました: " + e.getMessage());
            return 0;
        }
    }

    /**
     * フラグ名を WorldGuard の登録簿から引いてリージョンに設定する（保存はしない）
     *
     * @return 設定できたフラグの数
     */
    static int applyFlagsToRegion(ProtectedRegion region,
                                  java.util.Map<String, String> flags,
                                  java.util.Map<String, String> groups,
                                  com.sk89q.worldguard.protection.flags.registry.FlagRegistry registry,
                                  Logger logger) {
        int applied = 0;

        for (java.util.Map.Entry<String, String> entry : flags.entrySet()) {
            String flagName = entry.getKey();

            Flag<?> flag = registry.get(flagName.toLowerCase());
            if (!(flag instanceof StateFlag)) {
                logger.warning("未対応のフラグ: " + flagName);
                continue;
            }

            StateFlag.State flagState = parseState(entry.getValue());
            if (flagState == null) {
                logger.warning("未対応の状態: " + entry.getValue() + " (フラグ " + flagName + ")");
                continue;
            }

            String groupName = groups == null ? null : groups.get(flagName);
            RegionGroup group = null;
            if (groupName != null) {
                group = parseRegionGroup(groupName);
                if (group == null) {
                    logger.warning("未対応の対象グループ: " + groupName + " (フラグ " + flagName + ")");
                    continue;
                }
            }

            region.setFlag((StateFlag) flag, flagState);
            // 対象グループが未指定なら既定（全員）に戻す
            region.setFlag(flag.getRegionGroupFlag(), group);
            applied++;
        }

        return applied;
    }

    static StateFlag.State parseState(String state) {
        if (state == null) {
            return null;
        }
        switch (state.trim().toLowerCase()) {
            case "allow":
                return StateFlag.State.ALLOW;
            case "deny":
                return StateFlag.State.DENY;
            default:
                return null;
        }
    }

    /**
     * 対象グループ名を解釈する。"nonmembers" / "non_members" / "non-members" のどれでも受け付ける
     */
    static RegionGroup parseRegionGroup(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase().replace("_", "").replace("-", "")) {
            case "all":
                return RegionGroup.ALL;
            case "members":
                return RegionGroup.MEMBERS;
            case "owners":
                return RegionGroup.OWNERS;
            case "nonmembers":
                return RegionGroup.NON_MEMBERS;
            case "nonowners":
                return RegionGroup.NON_OWNERS;
            default:
                return null;
        }
    }


    /**
     * 敵対的なモブのスポーンを制御
     * 
     * @param regionId リージョンID
     * @param world ワールド
     * @param allow trueの場合スポーンを許可、falseの場合スポーンを禁止
     * @return 成功した場合true
     */
    public boolean setHostileMobSpawning(String regionId, World world, boolean allow) {
        if (!enabled) {
            logger.warning("WorldGuardが無効なため敵対的モブのスポーン制御を設定できません");
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

            if (allow) {
                // スポーン制限を解除（nullを設定）
                region.setFlag(Flags.DENY_SPAWN, null);

                // ダメージ・爆発防止フラグも解除
                region.setFlag(Flags.MOB_DAMAGE, null);
                region.setFlag(Flags.CREEPER_EXPLOSION, null);
                region.setFlag(Flags.GHAST_FIREBALL, null);

                logger.info("リージョン " + regionId + " の敵対的モブスポーン制限・ダメージ・爆発防止を解除しました");
            } else {
                // 敵対的なモブのリストを作成
                java.util.Set<com.sk89q.worldedit.world.entity.EntityType> hostileMobs = new java.util.HashSet<>();
                
                // 敵対的なモブを追加
                addIfExists(hostileMobs, "zombie");
                addIfExists(hostileMobs, "skeleton");
                addIfExists(hostileMobs, "creeper");
                addIfExists(hostileMobs, "spider");
                addIfExists(hostileMobs, "cave_spider");
                addIfExists(hostileMobs, "enderman");
                addIfExists(hostileMobs, "witch");
                addIfExists(hostileMobs, "slime");
                addIfExists(hostileMobs, "phantom");
                addIfExists(hostileMobs, "drowned");
                addIfExists(hostileMobs, "husk");
                addIfExists(hostileMobs, "stray");
                addIfExists(hostileMobs, "pillager");
                addIfExists(hostileMobs, "vindicator");
                addIfExists(hostileMobs, "evoker");
                addIfExists(hostileMobs, "ravager");
                addIfExists(hostileMobs, "vex");
                addIfExists(hostileMobs, "zombie_villager");
                addIfExists(hostileMobs, "wither_skeleton");
                addIfExists(hostileMobs, "blaze");
                addIfExists(hostileMobs, "ghast");
                addIfExists(hostileMobs, "magma_cube");
                addIfExists(hostileMobs, "silverfish");
                addIfExists(hostileMobs, "endermite");
                addIfExists(hostileMobs, "guardian");
                addIfExists(hostileMobs, "elder_guardian");
                addIfExists(hostileMobs, "shulker");
                addIfExists(hostileMobs, "hoglin");
                addIfExists(hostileMobs, "piglin_brute");
                addIfExists(hostileMobs, "zoglin");

                // deny-spawnフラグに設定
                region.setFlag(Flags.DENY_SPAWN, hostileMobs);

                // ダメージ・爆発防止フラグも設定（二重防御）
                region.setFlag(Flags.MOB_DAMAGE, StateFlag.State.DENY);
                region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
                region.setFlag(Flags.GHAST_FIREBALL, StateFlag.State.DENY);

                logger.info("リージョン " + regionId + " で敵対的モブのスポーンを禁止しました（" + hostileMobs.size() + "種類）");
                logger.info("リージョン " + regionId + " でダメージ・爆発防止も設定しました（二重防御）");
            }

            // 変更を保存
            try {
                regionManager.save();
                logger.info("リージョン " + regionId + " の設定を保存しました");
            } catch (Exception saveException) {
                logger.severe("設定の保存に失敗しました: " + saveException.getMessage());
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.severe("敵対的モブのスポーン制御設定に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * エンティティタイプが存在する場合にセットに追加するヘルパーメソッド
     */
    private void addIfExists(java.util.Set<com.sk89q.worldedit.world.entity.EntityType> set, String entityId) {
        try {
            com.sk89q.worldedit.world.entity.EntityType entityType = 
                com.sk89q.worldedit.world.entity.EntityTypes.get(entityId);
            if (entityType != null) {
                set.add(entityType);
            }
        } catch (Exception e) {
            // エンティティタイプが見つからない場合は無視
            logger.fine("エンティティタイプ " + entityId + " が見つかりませんでした");
        }
    }
}
