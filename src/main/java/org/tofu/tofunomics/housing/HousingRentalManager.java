package org.tofu.tofunomics.housing;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.HousingPropertyDAO;
import org.tofu.tofunomics.dao.HousingRentalDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.database.DatabaseManager;
import org.tofu.tofunomics.models.HousingProperty;
import org.tofu.tofunomics.models.HousingRental;
import org.tofu.tofunomics.models.HousingRentalHistory;
import org.tofu.tofunomics.integration.WorldGuardIntegration;
import org.bukkit.World;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 住居賃貸契約を管理するクラス
 */
public class HousingRentalManager {
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final WorldGuardIntegration worldGuardIntegration;
    private final org.tofu.tofunomics.economy.CurrencyConverter currencyConverter;
    
    private HousingPropertyDAO propertyDAO;
    private HousingRentalDAO rentalDAO;
    private PlayerDAO playerDAO;
    private org.tofu.tofunomics.dao.HousingPlacedBlockDAO placedBlockDAO;
    private org.tofu.tofunomics.dao.HousingParcelDAO parcelDAO;

    /** 契約中の物件の範囲（設置・破壊のたびに DB を引かないための写し）。契約・解約・期限切れで作り直す */
    private volatile List<RentedArea> rentedAreas = java.util.Collections.emptyList();

    public HousingRentalManager(TofuNomics plugin, ConfigManager configManager, DatabaseManager databaseManager, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
        this.worldGuardIntegration = worldGuardIntegration;
        this.currencyConverter = plugin.getCurrencyConverter();
        
        initializeDAOs();
        refreshRentedAreas();
    }

    private void initializeDAOs() {
        if (databaseManager.isConnected()) {
            this.propertyDAO = new HousingPropertyDAO(databaseManager.getConnection());
            this.rentalDAO = new HousingRentalDAO(databaseManager.getConnection());
            this.playerDAO = new PlayerDAO(databaseManager.getConnection());
            this.placedBlockDAO = new org.tofu.tofunomics.dao.HousingPlacedBlockDAO(databaseManager.getConnection());
            this.parcelDAO = new org.tofu.tofunomics.dao.HousingParcelDAO(databaseManager.getConnection());
        }
    }

    // ===== 借主による物件内の設置・破壊 =====

    /**
     * 契約中の物件 1 件の範囲と借主。
     */
    public static final class RentedArea {
        private final int propertyId;
        private final int rentalId;
        private final UUID tenantUuid;
        private final String worldName;
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        public RentedArea(int propertyId, int rentalId, UUID tenantUuid, String worldName,
                          int x1, int y1, int z1, int x2, int y2, int z2) {
            this.propertyId = propertyId;
            this.rentalId = rentalId;
            this.tenantUuid = tenantUuid;
            this.worldName = worldName;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean contains(String world, int x, int y, int z) {
            return worldName.equals(world)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        /** 横に margin マス広げた範囲に入っているか（物件の壁の外側に付いたドア用） */
        public boolean containsWithMargin(String world, int x, int y, int z, int margin) {
            return worldName.equals(world)
                    && x >= minX - margin && x <= maxX + margin
                    && y >= minY && y <= maxY
                    && z >= minZ - margin && z <= maxZ + margin;
        }

        public int getPropertyId() { return propertyId; }
        public int getRentalId() { return rentalId; }
        public UUID getTenantUuid() { return tenantUuid; }
        public String getWorldName() { return worldName; }
    }

    /**
     * 契約中の物件の範囲を DB から読み直す。
     */
    public void refreshRentedAreas() {
        if (rentalDAO == null || propertyDAO == null) {
            return;
        }
        try {
            List<RentedArea> areas = new java.util.ArrayList<>();
            for (HousingRental rental : rentalDAO.getAllActiveRentals()) {
                HousingProperty property = propertyDAO.getProperty(rental.getPropertyId());
                if (property == null) {
                    continue;
                }
                if (!property.hasCoordinates()) {
                    // 座標の無い物件は範囲が分からず、元からあるブロックを守れない
                    logger.warning("物件 " + property.getId() + " に座標が無いため、借主の設置・破壊の制限が効きません");
                    continue;
                }
                areas.add(new RentedArea(property.getId(), rental.getId(), rental.getTenantUuid(),
                        property.getWorldName(),
                        property.getX1(), property.getY1(), property.getZ1(),
                        property.getX2(), property.getY2(), property.getZ2()));
            }
            this.rentedAreas = java.util.Collections.unmodifiableList(areas);
        } catch (SQLException e) {
            logger.severe("契約中の物件の読み込みに失敗しました: " + e.getMessage());
        }
    }

    /**
     * その場所を含む契約中の物件（無ければ null）。
     */
    public RentedArea findRentedAreaAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (RentedArea area : rentedAreas) {
            if (area.contains(world, x, y, z)) {
                return area;
            }
        }
        return null;
    }

    /**
     * ドアの位置から、そのドアが入口になっている契約中の物件を探す。
     * 範囲の中に無ければ、横に 1 マス広げて探す（範囲を壁の内側で選んだ物件の入口のドア用）。
     */
    public RentedArea findRentedAreaForDoor(Location location) {
        RentedArea inside = findRentedAreaAt(location);
        if (inside != null || location == null || location.getWorld() == null) {
            return inside;
        }
        String world = location.getWorld().getName();
        for (RentedArea area : rentedAreas) {
            if (area.containsWithMargin(world, location.getBlockX(), location.getBlockY(), location.getBlockZ(), 1)) {
                return area;
            }
        }
        return null;
    }

    /**
     * 借主の設置を記録する。
     *
     * @param itemData 置いたアイテム（Base64）。2 個目以降のブロック（ドアの上半分など）は null
     */
    public void recordTenantPlacement(RentedArea area, org.bukkit.block.Block block, String itemData) {
        if (placedBlockDAO == null) {
            return;
        }
        try {
            placedBlockDAO.record(area.getRentalId(), area.getPropertyId(), area.getTenantUuid(),
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                    block.getType().name(), itemData);
        } catch (SQLException e) {
            logger.severe("借主の設置の記録に失敗しました: " + e.getMessage());
        }
    }

    /**
     * そのブロックを、その契約の借主が置いたか。
     */
    public boolean isPlacedByTenant(RentedArea area, org.bukkit.block.Block block) {
        if (placedBlockDAO == null) {
            return false;
        }
        try {
            org.tofu.tofunomics.dao.HousingPlacedBlockDAO.PlacedBlock placed = placedBlockDAO.find(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            return placed != null && placed.getRentalId() == area.getRentalId();
        } catch (SQLException e) {
            logger.severe("借主の設置の確認に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * 借主が壊したブロックの記録を消す。
     */
    public void forgetTenantPlacement(org.bukkit.block.Block block) {
        if (placedBlockDAO == null) {
            return;
        }
        try {
            placedBlockDAO.delete(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        } catch (SQLException e) {
            logger.severe("借主の設置の記録の削除に失敗しました: " + e.getMessage());
        }
    }

    /**
     * 退去時の原状回復。借主が置いたブロックを消し、ブロック自体と中身を預かり荷物にする。
     *
     * @return 預かった荷物の数（スタック数）
     */
    public int restoreTenantBlocks(HousingRental rental) {
        if (placedBlockDAO == null || parcelDAO == null) {
            return 0;
        }
        int parcels = 0;
        try {
            List<org.tofu.tofunomics.dao.HousingPlacedBlockDAO.PlacedBlock> placedBlocks =
                    placedBlockDAO.findByRental(rental.getId());
            for (org.tofu.tofunomics.dao.HousingPlacedBlockDAO.PlacedBlock placed : placedBlocks) {
                World world = Bukkit.getWorld(placed.getWorldName());
                if (world == null) {
                    continue;
                }
                org.bukkit.block.Block block = world.getBlockAt(placed.getX(), placed.getY(), placed.getZ());
                List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();

                // 借主が壊した後（空気）なら、ブロックはもう手元にあるので何もしない。
                // 空気でなければ、借主しか置けない場所なので、種類が変わっていても（銅の錆び・大釜の水など）借主の物として扱う
                if (!block.getType().isAir()) {
                    items.addAll(takeContents(block));
                    block.setType(org.bukkit.Material.AIR, false);
                    org.bukkit.inventory.ItemStack placedItem =
                            org.tofu.tofunomics.market.MarketItemSerializer.deserialize(placed.getItemData());
                    if (placedItem != null) {
                        items.add(placedItem);
                    }
                }

                for (org.bukkit.inventory.ItemStack item : items) {
                    String data = org.tofu.tofunomics.market.MarketItemSerializer.serialize(item);
                    if (data == null) {
                        logger.warning("預かり荷物の保存に失敗しました: " + item.getType() + " x" + item.getAmount()
                                + " (借主 " + rental.getTenantUuid() + ")");
                        continue;
                    }
                    parcelDAO.add(rental.getTenantUuid(), rental.getPropertyId(), data);
                    parcels++;
                }
            }
            placedBlockDAO.deleteByRental(rental.getId());
            if (!placedBlocks.isEmpty()) {
                logger.info("退去の原状回復: 契約ID " + rental.getId() + " ブロック " + placedBlocks.size()
                        + " 件を片付け、荷物 " + parcels + " 件を預かりました");
            }
        } catch (SQLException e) {
            logger.severe("退去の原状回復に失敗しました (契約ID " + rental.getId() + "): " + e.getMessage());
        }
        return parcels;
    }

    /**
     * コンテナ等の中身を取り出して空にする（ラージチェストは自分の半分だけ）。
     */
    private List<org.bukkit.inventory.ItemStack> takeContents(org.bukkit.block.Block block) {
        List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();
        org.bukkit.block.BlockState state = block.getState();
        org.bukkit.inventory.Inventory inventory = null;
        if (state instanceof org.bukkit.block.Chest) {
            inventory = ((org.bukkit.block.Chest) state).getBlockInventory();
        } else if (state instanceof org.bukkit.inventory.BlockInventoryHolder) {
            inventory = ((org.bukkit.inventory.BlockInventoryHolder) state).getInventory();
        }
        if (inventory == null) {
            return items;
        }
        for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        inventory.clear();
        return items;
    }

    /**
     * 借主の預かり荷物。
     */
    public List<org.tofu.tofunomics.dao.HousingParcelDAO.Parcel> getParcels(UUID tenantUuid) {
        if (parcelDAO == null) {
            return java.util.Collections.emptyList();
        }
        try {
            return parcelDAO.findByTenant(tenantUuid);
        } catch (SQLException e) {
            logger.severe("預かり荷物の取得に失敗しました: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 預かり荷物を 1 件受け取る。手持ちが一杯で入りきらない分は荷物に残す。
     *
     * @return 受け取れた個数（0 なら受け取れなかった）
     */
    public int takeParcel(Player player, org.tofu.tofunomics.dao.HousingParcelDAO.Parcel parcel) {
        if (parcelDAO == null) {
            return 0;
        }
        org.bukkit.inventory.ItemStack item =
                org.tofu.tofunomics.market.MarketItemSerializer.deserialize(parcel.getItemData());
        if (item == null) {
            logger.warning("預かり荷物を読めませんでした: ID " + parcel.getId());
            return 0;
        }
        try {
            int total = item.getAmount();
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> rest =
                    player.getInventory().addItem(item.clone());
            int left = 0;
            for (org.bukkit.inventory.ItemStack r : rest.values()) {
                left += r.getAmount();
            }
            if (left == 0) {
                parcelDAO.delete(parcel.getId(), player.getUniqueId());
            } else if (left < total) {
                org.bukkit.inventory.ItemStack remain = item.clone();
                remain.setAmount(left);
                parcelDAO.updateItem(parcel.getId(),
                        org.tofu.tofunomics.market.MarketItemSerializer.serialize(remain));
            }
            return total - left;
        } catch (SQLException e) {
            logger.severe("預かり荷物の受け取りに失敗しました: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 物件の中の開いているドアを閉める（内見のときに開けたままのドアから、契約後に他の人が入れないように）。
     */
    private void closeDoors(HousingProperty property) {
        if (!property.hasCoordinates()) {
            return;
        }
        World world = Bukkit.getWorld(property.getWorldName());
        if (world == null) {
            return;
        }
        // 壁の外側に付いた入口のドアも閉めるよう、横に 1 マス広げる（findRentedAreaForDoor と同じ）
        int minX = Math.min(property.getX1(), property.getX2()) - 1, maxX = Math.max(property.getX1(), property.getX2()) + 1;
        int minY = Math.min(property.getY1(), property.getY2()), maxY = Math.max(property.getY1(), property.getY2());
        int minZ = Math.min(property.getZ1(), property.getZ2()) - 1, maxZ = Math.max(property.getZ1(), property.getZ2()) + 1;
        int closed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (!HousingDoorListener.isDoor(block.getType())) {
                        continue;
                    }
                    org.bukkit.block.data.BlockData data = block.getBlockData();
                    if (data instanceof org.bukkit.block.data.type.Door
                            && ((org.bukkit.block.data.type.Door) data).isOpen()) {
                        ((org.bukkit.block.data.type.Door) data).setOpen(false);
                        block.setBlockData(data, false);
                        closed++;
                    }
                }
            }
        }
        if (closed > 0) {
            logger.info("物件 " + property.getId() + " の開いていたドアを " + closed + " か所閉めました");
        }
    }

    private void notifyParcels(UUID tenantUuid, int parcels) {
        if (parcels <= 0) {
            return;
        }
        Player online = Bukkit.getPlayer(tenantUuid);
        if (online != null) {
            online.sendMessage("§e物件に置いていた物を「預かり荷物」として預かりました（" + parcels + "件）");
            online.sendMessage("§e/housing の「預かり荷物」から受け取れます");
        }
    }

    /**
     * 物件を登録（運営用）
     */
    public RentalResult registerProperty(HousingProperty property) {
        try {
            // 座標またはWorldGuard領域が設定されているかチェック
            if (!property.hasCoordinates() && !property.hasWorldGuardRegion()) {
                return new RentalResult(false, "座標またはWorldGuard領域を設定してください");
            }
            
            int propertyId = propertyDAO.createProperty(property);
            if (propertyId > 0) {
                logger.info("物件を登録しました: " + property.getPropertyName() + " (ID: " + propertyId + ")");
                return new RentalResult(true, "物件を登録しました (ID: " + propertyId + ")");
            }
        } catch (SQLException e) {
            logger.severe("物件登録に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
        
        return new RentalResult(false, "物件の登録に失敗しました");
    }

    /**
     * 連番自動命名で次の物件名を生成する。
     * config の接頭辞 + 最小の未使用番号（1始まり）を返す。
     * 既存の物件名とWorldGuardリージョン名の両方で衝突を回避する
     * （リージョン名が重複するとWG領域作成が失敗するため）。
     *
     * @return 衝突しない新しい物件名（例: house-1）
     */
    public String generateNextPropertyName() {
        String prefix = configManager.getHousingAutoNamingPrefix();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (HousingProperty property : getAllProperties()) {
            if (property.getPropertyName() != null) {
                used.add(property.getPropertyName());
            }
            if (property.getWorldguardRegionId() != null) {
                used.add(property.getWorldguardRegionId());
            }
        }

        int n = 1;
        while (used.contains(prefix + n)) {
            n++;
        }
        return prefix + n;
    }

    /**
     * 選択範囲から物件を登録する（quickregister / 登録GUI共通の中核処理）。
     * createWg が true の場合、物件名と同名のWorldGuard領域を自動作成し、親リージョン継承・フラグ設定も行う。
     *
     * @param propertyName 物件名
     * @param world ワールド
     * @param pos1 第1座標
     * @param pos2 第2座標
     * @param dailyRent 日額賃料
     * @param createWg WorldGuard領域も自動作成するか
     * @return 登録結果
     */
    public RentalResult registerPropertyFromSelection(String propertyName, World world,
                                                      Location pos1, Location pos2,
                                                      double dailyRent, boolean createWg) {
        HousingProperty property = new HousingProperty(
            propertyName,
            world.getName(),
            pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
            pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ(),
            null,
            dailyRent
        );

        // WorldGuard領域を物件名と同じIDで自動作成
        if (createWg && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
            boolean regionCreated = createWorldGuardRegion(propertyName, world, pos1, pos2);
            if (regionCreated) {
                property.setWorldguardRegionId(propertyName);
            } else {
                // 領域作成に失敗しても座標範囲で登録は続行する（既存registerと同方針）
                logger.warning("WorldGuard領域 '" + propertyName + "' の作成に失敗したため座標範囲のみで登録します");
            }
        }

        return registerProperty(property);
    }

    /**
     * WorldGuard領域を作成
     * config.ymlで親リージョンが設定されている場合、自動的に子リージョンとして設定
     */
    public boolean createWorldGuardRegion(String regionId, World world, Location pos1, Location pos2) {
        if (worldGuardIntegration == null || !worldGuardIntegration.isEnabled()) {
            logger.warning("WorldGuardが無効です");
            return false;
        }
        
        boolean regionCreated = worldGuardIntegration.createRegion(regionId, world, pos1, pos2);
        
        if (!regionCreated) {
            return false;
        }
        
        // config.ymlから親リージョン設定を確認
        boolean cityProtectionEnabled = plugin.getConfig().getBoolean("housing_rental.city_protection.enabled", false);
        String parentRegionName = plugin.getConfig().getString("housing_rental.city_protection.region_name", "");
        
        if (cityProtectionEnabled && !parentRegionName.isEmpty()) {
            // 親リージョンを設定
            boolean parentSet = worldGuardIntegration.setParentRegion(regionId, parentRegionName, world);
            
            if (parentSet) {
                logger.info("リージョン " + regionId + " を親リージョン " + parentRegionName + " の子として設定しました");
            } else {
                logger.warning("リージョン " + regionId + " は作成されましたが、親リージョンの設定に失敗しました");
                logger.warning("親リージョン '" + parentRegionName + "' が存在することを確認してください");
            }
        }
        
        // フラグの自動設定
        if (configManager.isRentalRegionAutoConfigure()) {
            applyDefaultRegionFlags(regionId, world);
        }
        
        return true;
    }

    /**
     * 賃貸リージョンに config の既定フラグ（default_flags / default_flag_groups）を設定する
     * 新規作成時と、既存リージョンへの付け直し（/housing admin applyflags）の両方で使う
     *
     * @return 全てのフラグを設定できた場合true
     */
    public boolean applyDefaultRegionFlags(String regionId, World world) {
        if (worldGuardIntegration == null || !worldGuardIntegration.isEnabled()) {
            return false;
        }

        java.util.Map<String, String> defaultFlags = configManager.getRentalRegionDefaultFlags();
        java.util.Map<String, String> flagGroups = configManager.getRentalRegionDefaultFlagGroups();

        int applied = worldGuardIntegration.applyRegionFlags(regionId, world, defaultFlags, flagGroups);

        if (applied == defaultFlags.size()) {
            logger.info("リージョン " + regionId + " に既定フラグ " + applied + " 個を設定しました");
            return true;
        }
        logger.warning("リージョン " + regionId + " の既定フラグは " + defaultFlags.size() + " 個中 "
            + applied + " 個しか設定できませんでした");
        return false;
    }

    /**
     * 賃貸契約を締結
     */
    public synchronized RentalResult rentProperty(UUID tenantUuid, int propertyId, String period, int units) {
        // お金の記録に理由（住居の家賃）を残すための宣言。処理の中身は下の rentPropertyInternal のまま
        try (org.tofu.tofunomics.economy.TransactionContext.Scope scope = org.tofu.tofunomics.economy.TransactionContext.open(
                org.tofu.tofunomics.economy.TransactionType.RENT, null, "契約 物件ID " + propertyId)) {
            return rentPropertyInternal(tenantUuid, propertyId, period, units);
        }
    }

    private RentalResult rentPropertyInternal(UUID tenantUuid, int propertyId, String period, int units) {
        try {
            // 契約期間（単位数）の妥当性チェック（0以下だと即時期限切れや永久契約を招くため弾く）
            if (units <= 0) {
                return new RentalResult(false, "契約期間は1以上を指定してください");
            }

            // 物件の確認
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }
            
            if (!property.isAvailable()) {
                return new RentalResult(false, "この物件は現在利用できません");
            }
            
            // 既存の契約確認
            HousingRental existingRental = rentalDAO.getActiveRentalByProperty(propertyId);
            if (existingRental != null) {
                return new RentalResult(false, "この物件は既に賃貸中です");
            }
            
            // 最大契約数チェック
            int maxRentals = plugin.getConfig().getInt("housing_rental.max_rentals_per_player", 3);
            List<HousingRental> activeRentals = rentalDAO.getActiveRentalsByTenant(tenantUuid);
            if (activeRentals.size() >= maxRentals) {
                return new RentalResult(false, "賃貸契約の上限数(" + maxRentals + ")に達しています");
            }
            
            // 賃料計算
            int rentalDays = calculateDays(period, units);
            double totalCost = calculateTotalCost(property, period, units);
            
            // オンラインプレイヤーを取得（インベントリアクセスのため）
            org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(tenantUuid);
            if (onlinePlayer == null) {
                return new RentalResult(false, "オンライン状態で契約してください");
            }
            
            // 総資産（銀行残高+現金）をチェック
            double totalBalance = currencyConverter.getTotalBalance(onlinePlayer);
            if (totalBalance < totalCost) {
                return new RentalResult(false, "残高が不足しています (必要: " + 
                    currencyConverter.formatCurrency(totalCost) + ", 所持: " + 
                    currencyConverter.formatCurrency(totalBalance) + ")");
            }
            
            // TofuNomicsワールドの時間を取得
            String worldName = plugin.getConfig().getString("housing_rental.world_name", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return new RentalResult(false, "TofuNomicsワールドが見つかりません");
            }
            long startTick = world.getFullTime();
            
            // 契約作成
            HousingRental rental = new HousingRental(propertyId, tenantUuid, period, rentalDays, totalCost, startTick);
            int rentalId = rentalDAO.createRental(rental);
            
            if (rentalId > 0) {
                // 支払い処理（銀行残高優先、足りない分を現金から）
                org.tofu.tofunomics.models.Player player = playerDAO.getOrCreatePlayer(tenantUuid);
                double bankBalance = player.getBankBalance();
                
                if (bankBalance >= totalCost) {
                    // 銀行残高のみで支払い可能
                    player.removeBankBalance(totalCost);
                    playerDAO.updatePlayer(player);
                } else {
                    // 銀行残高+現金で支払い
                    double remainingCost = totalCost;
                    
                    if (bankBalance > 0) {
                        // 銀行残高を全額使用
                        player.removeBankBalance(bankBalance);
                        playerDAO.updatePlayer(player);
                        remainingCost -= bankBalance;
                    }
                    
                    // 残りを現金から支払い
                    if (!currencyConverter.payWithCash(onlinePlayer, remainingCost)) {
                        // 支払い失敗時はロールバック（銀行残高を戻す）
                        if (bankBalance > 0) {
                            player.addBankBalance(bankBalance);
                            playerDAO.updatePlayer(player);
                        }
                        // 作成済みの契約レコードを削除（物件が永久ロックされるのを防ぐ）
                        rentalDAO.deleteRental(rentalId);
                        return new RentalResult(false, "支払い処理に失敗しました");
                    }
                }
                
                // 物件を利用不可に
                propertyDAO.updateAvailability(propertyId, false);
                
                // 履歴追加
                HousingRentalHistory history = new HousingRentalHistory(
                    rentalId, propertyId, tenantUuid, "rent", totalCost
                );
                rentalDAO.addRentalHistory(history);
                
                // WorldGuard領域が設定されている場合、プレイヤーをメンバーに追加
                if (property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                    World propertyWorld = Bukkit.getWorld(property.getWorldName());
                    if (propertyWorld != null) {
                        boolean memberAdded = worldGuardIntegration.addMember(
                            property.getWorldguardRegionId(), 
                            propertyWorld, 
                            tenantUuid
                        );
                        if (memberAdded) {
                            logger.info("プレイヤー " + tenantUuid + " をWorldGuard領域 " + property.getWorldguardRegionId() + " に追加しました");
                        }
                    }
                }
                
                refreshRentedAreas();
                closeDoors(property);
                logger.info("賃貸契約を締結しました: " + player.getUuid() + " -> 物件ID: " + propertyId);
                
                // プレイヤーに通知（ゲーム内時間表示）
                if (onlinePlayer != null) {
                    long realMinutes = rentalDays * 20; // 1ゲーム内日 = 20分
                    onlinePlayer.sendMessage("§a" + property.getPropertyName() + "の賃貸契約を締結しました");
                    onlinePlayer.sendMessage("§e期間: " + rentalDays + "日(" + realMinutes + "分) | 費用: " + totalCost);
                    onlinePlayer.sendMessage("§7物件のドアはクリックすると通り抜けられます（契約者だけが入れます）");
                    onlinePlayer.sendMessage("§7物件の中の空いている所に、チェストや作業台などを置けます（元からあるブロックは壊せません）");
                    onlinePlayer.sendMessage("§7退去のときは置いた物が片付けられ、/housing の「預かり荷物」から受け取れます");
                }
                
                return new RentalResult(true, "賃貸契約を締結しました (契約ID: " + rentalId + ")");
            }
        } catch (SQLException e) {
            logger.severe("賃貸契約の締結に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
        
        return new RentalResult(false, "賃貸契約の締結に失敗しました");
    }

    /**
     * 契約を延長
     */
    public synchronized RentalResult extendRental(UUID tenantUuid, int propertyId, int additionalDays) {
        // お金の記録に理由（住居の家賃）を残すための宣言。処理の中身は下の extendRentalInternal のまま
        try (org.tofu.tofunomics.economy.TransactionContext.Scope scope = org.tofu.tofunomics.economy.TransactionContext.open(
                org.tofu.tofunomics.economy.TransactionType.RENT, null, "延長 物件ID " + propertyId)) {
            return extendRentalInternal(tenantUuid, propertyId, additionalDays);
        }
    }

    private RentalResult extendRentalInternal(UUID tenantUuid, int propertyId, int additionalDays) {
        try {
            // 追加日数の妥当性チェック（0以下を弾く）
            if (additionalDays <= 0) {
                return new RentalResult(false, "追加日数は1以上を指定してください");
            }

            HousingRental rental = rentalDAO.getActiveRentalByProperty(propertyId);
            
            if (rental == null) {
                return new RentalResult(false, "有効な契約が見つかりません");
            }
            
            if (!rental.getTenantUuid().equals(tenantUuid)) {
                return new RentalResult(false, "この物件の契約者ではありません");
            }
            
            // 物件情報取得
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }
            
            // 追加料金計算
            double additionalCost = property.getDailyRent() * additionalDays;
            
            // オンラインプレイヤーを取得（インベントリアクセスのため）
            org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(tenantUuid);
            if (onlinePlayer == null) {
                return new RentalResult(false, "オンライン状態で契約延長してください");
            }
            
            // 総資産（銀行残高+現金）をチェック
            double totalBalance = currencyConverter.getTotalBalance(onlinePlayer);
            if (totalBalance < additionalCost) {
                return new RentalResult(false, "残高が不足しています (必要: " + 
                    currencyConverter.formatCurrency(additionalCost) + ", 所持: " + 
                    currencyConverter.formatCurrency(totalBalance) + ")");
            }
            
            // 先に支払い処理を行い、成功した場合のみ契約を延長する（DB不整合・無料延長を防ぐ）
            org.tofu.tofunomics.models.Player player = playerDAO.getOrCreatePlayer(tenantUuid);
            double bankBalance = player.getBankBalance();

            if (bankBalance >= additionalCost) {
                // 銀行残高のみで支払い可能
                player.removeBankBalance(additionalCost);
                playerDAO.updatePlayer(player);
            } else {
                // 銀行残高+現金で支払い
                double remainingCost = additionalCost;

                if (bankBalance > 0) {
                    // 銀行残高を全額使用
                    player.removeBankBalance(bankBalance);
                    playerDAO.updatePlayer(player);
                    remainingCost -= bankBalance;
                }

                // 残りを現金から支払い
                if (!currencyConverter.payWithCash(onlinePlayer, remainingCost)) {
                    // 支払い失敗時は銀行残高を戻す（契約は未変更のため巻き戻し不要）
                    if (bankBalance > 0) {
                        player.addBankBalance(bankBalance);
                        playerDAO.updatePlayer(player);
                    }
                    return new RentalResult(false, "支払い処理に失敗しました");
                }
            }

            // 支払い成功後に契約を延長
            try {
                rental.extend(additionalDays, additionalCost);
                rentalDAO.updateRental(rental);
            } catch (SQLException e) {
                // 契約更新に失敗した場合は支払い済み金額を銀行へ返金（資産消失を防ぐ）
                player.addBankBalance(additionalCost);
                playerDAO.updatePlayer(player);
                logger.severe("契約延長のDB更新に失敗したため返金しました: " + e.getMessage());
                return new RentalResult(false, "データベースエラーが発生しました（料金は返金されました）");
            }
            
            // 履歴追加
            HousingRentalHistory history = new HousingRentalHistory(
                rental.getId(), propertyId, tenantUuid, "extend", additionalCost
            );
            rentalDAO.addRentalHistory(history);
            
            logger.info("契約を延長しました: 契約ID " + rental.getId() + " -> +" + additionalDays + "日");
            
            long realMinutes = additionalDays * 20; // 1ゲーム内日 = 20分
            return new RentalResult(true, "契約を" + additionalDays + "日(" + realMinutes + "分)延長しました (費用: " + additionalCost + ")");
            
        } catch (SQLException e) {
            logger.severe("契約延長に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 契約をキャンセル
     */
    public synchronized RentalResult cancelRental(UUID tenantUuid, int propertyId) {
        // お金の記録に理由（住居の家賃）を残すための宣言。処理の中身は下の cancelRentalInternal のまま
        try (org.tofu.tofunomics.economy.TransactionContext.Scope scope = org.tofu.tofunomics.economy.TransactionContext.open(
                org.tofu.tofunomics.economy.TransactionType.RENT, null, "解約 物件ID " + propertyId)) {
            return cancelRentalInternal(tenantUuid, propertyId);
        }
    }

    private RentalResult cancelRentalInternal(UUID tenantUuid, int propertyId) {
        try {
            HousingRental rental = rentalDAO.getActiveRentalByProperty(propertyId);
            
            if (rental == null) {
                return new RentalResult(false, "有効な契約が見つかりません");
            }
            
            if (!rental.getTenantUuid().equals(tenantUuid)) {
                return new RentalResult(false, "この物件の契約者ではありません");
            }
            
            // 契約をキャンセル
            rental.cancel();
            rentalDAO.updateRental(rental);
            
            // 物件を利用可能に
            propertyDAO.updateAvailability(propertyId, true);
            
            // WorldGuard領域からプレイヤーを削除
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property != null && property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                World world = Bukkit.getWorld(property.getWorldName());
                if (world != null) {
                    boolean memberRemoved = worldGuardIntegration.removeMember(
                        property.getWorldguardRegionId(), 
                        world, 
                        tenantUuid
                    );
                    if (memberRemoved) {
                        logger.info("プレイヤー " + tenantUuid + " をWorldGuard領域 " + property.getWorldguardRegionId() + " から削除しました");
                    }
                }
            }
            
            // 履歴追加
            HousingRentalHistory history = new HousingRentalHistory(
                rental.getId(), propertyId, tenantUuid, "cancel", null
            );
            rentalDAO.addRentalHistory(history);
            
            refreshRentedAreas();
            notifyParcels(tenantUuid, restoreTenantBlocks(rental));

            logger.info("契約をキャンセルしました: 契約ID " + rental.getId());
            
            return new RentalResult(true, "契約をキャンセルしました");
            
        } catch (SQLException e) {
            logger.severe("契約キャンセルに失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 期限切れ契約の自動終了
     */
    public void processExpiredRentals() {
        try {
            // TofuNomicsワールドを取得
            String worldName = plugin.getConfig().getString("housing_rental.world_name", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("TofuNomicsワールドが見つかりません: " + worldName);
                return;
            }
            
            long currentTick = world.getFullTime();
            List<HousingRental> expiredRentals = rentalDAO.getExpiredRentals(currentTick);
            
            for (HousingRental rental : expiredRentals) {
                rental.expire();
                rentalDAO.updateRental(rental);
                
                // 物件を利用可能に
                propertyDAO.updateAvailability(rental.getPropertyId(), true);
                
                // WorldGuard領域からプレイヤーを削除
                HousingProperty property = propertyDAO.getProperty(rental.getPropertyId());
                if (property != null && property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                    World propertyWorld = Bukkit.getWorld(property.getWorldName());
                    if (propertyWorld != null) {
                        boolean memberRemoved = worldGuardIntegration.removeMember(
                            property.getWorldguardRegionId(), 
                            propertyWorld, 
                            rental.getTenantUuid()
                        );
                        if (memberRemoved) {
                            logger.info("プレイヤー " + rental.getTenantUuid() + " をWorldGuard領域 " + property.getWorldguardRegionId() + " から削除しました");
                        }
                    }
                }
                
                // 履歴追加
                HousingRentalHistory history = new HousingRentalHistory(
                    rental.getId(), rental.getPropertyId(), rental.getTenantUuid(), "expire", null
                );
                rentalDAO.addRentalHistory(history);
                
                // プレイヤーに通知
                org.bukkit.entity.Player player = Bukkit.getPlayer(rental.getTenantUuid());
                if (player != null) {
                    player.sendMessage("§c賃貸契約が期限切れになりました (物件ID: " + rental.getPropertyId() + ")");
                }
                
                notifyParcels(rental.getTenantUuid(), restoreTenantBlocks(rental));

                logger.info("契約が期限切れになりました: 契約ID " + rental.getId());
            }
            // 期限切れの反映と、管理者による物件の座標の変更を拾うため、毎回読み直す
            refreshRentedAreas();
            
        } catch (SQLException e) {
            logger.severe("期限切れ契約の処理に失敗しました: " + e.getMessage());
        }
    }

    /**
     * プレイヤーの有効な契約一覧を取得
     */
    public List<HousingRental> getPlayerRentals(UUID uuid) {
        try {
            return rentalDAO.getActiveRentalsByTenant(uuid);
        } catch (SQLException e) {
            logger.severe("契約一覧の取得に失敗しました: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 賃貸可能な物件一覧を取得
     */
    public List<HousingProperty> getAvailableProperties() {
        try {
            return propertyDAO.getAvailableProperties();
        } catch (SQLException e) {
            logger.severe("物件一覧の取得に失敗しました: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 全物件一覧を取得（運営用：賃貸中の物件も含む）
     */
    public List<HousingProperty> getAllProperties() {
        try {
            return propertyDAO.getAllProperties();
        } catch (SQLException e) {
            logger.severe("全物件一覧の取得に失敗しました: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 物件情報を取得
     */
    public HousingProperty getProperty(int propertyId) {
        try {
            return propertyDAO.getProperty(propertyId);
        } catch (SQLException e) {
            logger.severe("物件情報の取得に失敗しました: " + e.getMessage());
            return null;
        }
    }

    /**
     * 日数を計算
     */
    private int calculateDays(String period, int units) {
        switch (period.toLowerCase()) {
            case "daily":
                return units;
            case "weekly":
                return units * 7;
            case "monthly":
                return units * 30;
            default:
                return units; // デフォルトは日数
        }
    }

    /**
     * 合計費用を計算
     */
    private double calculateTotalCost(HousingProperty property, String period, int units) {
        switch (period.toLowerCase()) {
            case "daily":
                return property.getDailyRent() * units;
            case "weekly":
                return property.getWeeklyRent() * units;
            case "monthly":
                return property.getMonthlyRent() * units;
            default:
                return property.getDailyRent() * units;
        }
    }

    /**
     * 物件を削除
     */
    public RentalResult removeProperty(int propertyId) {
        try {
            // 物件の確認
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }
            
            // アクティブな賃貸契約を確認
            HousingRental activeRental = rentalDAO.getActiveRentalByProperty(propertyId);
            if (activeRental != null) {
                return new RentalResult(false, "この物件は現在賃貸中のため削除できません。先に契約をキャンセルしてください。");
            }
            
            // WorldGuard領域が設定されている場合は削除
            if (property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                World world = Bukkit.getWorld(property.getWorldName());
                if (world != null) {
                    boolean regionRemoved = worldGuardIntegration.removeRegion(
                        property.getWorldguardRegionId(),
                        world
                    );
                    if (!regionRemoved) {
                        logger.warning("WorldGuard領域 " + property.getWorldguardRegionId() + " の削除に失敗しました");
                    }
                }
            }
            
            // データベースから物件を削除
            propertyDAO.deleteProperty(propertyId);
            
            logger.info("物件を削除しました: ID " + propertyId + " (" + property.getPropertyName() + ")");
            
            return new RentalResult(true, "物件 '" + property.getPropertyName() + "' を削除しました");
            
        } catch (SQLException e) {
            logger.severe("物件の削除に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 物件の日額賃料を更新（運営用）
     * 週額・月額は日額から自動計算されるため、ここでは日額のみ更新する
     */
    public RentalResult updatePropertyRent(int propertyId, double newDailyRent) {
        try {
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }

            property.setDailyRent(newDailyRent);
            // 週額・月額を日額からの自動計算に委ねるためリセット（古い固定値が残るのを防ぐ）
            property.setWeeklyRent(null);
            property.setMonthlyRent(null);
            propertyDAO.updateProperty(property);

            logger.info("物件の賃料を変更しました: ID " + propertyId + " -> 日額 " + newDailyRent);
            return new RentalResult(true, "賃料を変更しました");
        } catch (SQLException e) {
            logger.severe("賃料変更に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 物件からWorldGuard領域の紐付けを解除する（運営用）。
     *
     * @param propertyId 物件ID
     * @param alsoDeleteRegion true の場合、実際のWorldGuard領域も削除する
     * @return 処理結果
     */
    public RentalResult unlinkWorldGuardRegion(int propertyId, boolean alsoDeleteRegion) {
        try {
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }
            if (!property.hasWorldGuardRegion()) {
                return new RentalResult(false, "この物件にはWG領域が設定されていません");
            }
            if (!property.hasCoordinates()) {
                return new RentalResult(false, "座標範囲がないためWG領域を解除できません（位置特定不可になります）");
            }

            String regionId = property.getWorldguardRegionId();

            // 実領域も削除する場合
            if (alsoDeleteRegion && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                World world = Bukkit.getWorld(property.getWorldName());
                if (world != null) {
                    boolean removed = worldGuardIntegration.removeRegion(regionId, world);
                    if (!removed) {
                        logger.warning("WG領域 " + regionId + " の削除に失敗しました（紐付けは解除します）");
                    }
                }
            }

            // 物件側の紐付けを解除
            property.setWorldguardRegionId(null);
            propertyDAO.updateProperty(property);

            logger.info("物件 " + propertyId + " のWG領域紐付けを解除しました (領域削除: " + alsoDeleteRegion + ")");
            return new RentalResult(true, alsoDeleteRegion
                ? "WG領域 '" + regionId + "' を削除し、紐付けを解除しました"
                : "WG領域 '" + regionId + "' の紐付けを解除しました（領域は残存）");
        } catch (SQLException e) {
            logger.severe("WG領域の紐付け解除に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 物件のWorldGuard領域IDを別の既存リージョンに再紐付けする（運営用）。
     *
     * @param propertyId 物件ID
     * @param newRegionId 新しいWGリージョンID（実在する必要がある）
     * @return 処理結果
     */
    public RentalResult relinkWorldGuardRegion(int propertyId, String newRegionId) {
        try {
            HousingProperty property = propertyDAO.getProperty(propertyId);
            if (property == null) {
                return new RentalResult(false, "物件が見つかりません");
            }
            if (worldGuardIntegration == null || !worldGuardIntegration.isEnabled()) {
                return new RentalResult(false, "WorldGuard統合が無効です");
            }

            World world = Bukkit.getWorld(property.getWorldName());
            if (world == null) {
                return new RentalResult(false, "ワールド '" + property.getWorldName() + "' が見つかりません");
            }

            // 指定リージョンの存在チェック
            if (!worldGuardIntegration.hasRegion(newRegionId, world)) {
                return new RentalResult(false, "リージョン '" + newRegionId + "' が存在しません");
            }

            property.setWorldguardRegionId(newRegionId);
            propertyDAO.updateProperty(property);

            logger.info("物件 " + propertyId + " のWG領域を " + newRegionId + " に再紐付けしました");
            return new RentalResult(true, "WG領域を '" + newRegionId + "' に再紐付けしました");
        } catch (SQLException e) {
            logger.severe("WG領域の再紐付けに失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 物件のWorldGuard領域に親リージョンを設定する（運営用）。
     *
     * @param propertyId 物件ID
     * @param parentRegionId 親リージョンID
     * @return 処理結果
     */
    public RentalResult setPropertyParentRegion(int propertyId, String parentRegionId) {
        HousingProperty property = propertyDAO != null ? getProperty(propertyId) : null;
        if (property == null) {
            return new RentalResult(false, "物件が見つかりません");
        }
        if (!property.hasWorldGuardRegion()) {
            return new RentalResult(false, "この物件にはWG領域が設定されていません");
        }
        if (worldGuardIntegration == null || !worldGuardIntegration.isEnabled()) {
            return new RentalResult(false, "WorldGuard統合が無効です");
        }

        World world = Bukkit.getWorld(property.getWorldName());
        if (world == null) {
            return new RentalResult(false, "ワールド '" + property.getWorldName() + "' が見つかりません");
        }

        if (!worldGuardIntegration.hasRegion(parentRegionId, world)) {
            return new RentalResult(false, "親リージョン '" + parentRegionId + "' が存在しません");
        }

        boolean ok = worldGuardIntegration.setParentRegion(
            property.getWorldguardRegionId(), parentRegionId, world);
        if (ok) {
            logger.info("物件 " + propertyId + " のWG領域 " + property.getWorldguardRegionId()
                + " を親リージョン " + parentRegionId + " の子に設定しました");
            return new RentalResult(true, "親リージョンを '" + parentRegionId + "' に設定しました");
        }
        return new RentalResult(false, "親リージョンの設定に失敗しました（循環参照や保存失敗の可能性）");
    }

    /**
     * 賃貸処理結果
     */
    public static class RentalResult {
        private final boolean success;
        private final String message;

        public RentalResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
