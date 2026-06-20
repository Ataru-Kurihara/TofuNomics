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

    public HousingRentalManager(TofuNomics plugin, ConfigManager configManager, DatabaseManager databaseManager, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
        this.worldGuardIntegration = worldGuardIntegration;
        this.currencyConverter = plugin.getCurrencyConverter();
        
        initializeDAOs();
    }

    private void initializeDAOs() {
        if (databaseManager.isConnected()) {
            this.propertyDAO = new HousingPropertyDAO(databaseManager.getConnection());
            this.rentalDAO = new HousingRentalDAO(databaseManager.getConnection());
            this.playerDAO = new PlayerDAO(databaseManager.getConnection());
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
            java.util.Map<String, String> defaultFlags = configManager.getRentalRegionDefaultFlags();
            
            for (java.util.Map.Entry<String, String> entry : defaultFlags.entrySet()) {
                String flagName = entry.getKey();
                String flagState = entry.getValue();
                
                boolean flagSet = worldGuardIntegration.setRegionFlag(regionId, world, flagName, flagState);
                
                if (flagSet) {
                    logger.info("リージョン " + regionId + " にフラグ " + flagName + "=" + flagState + " を設定しました");
                } else {
                    logger.warning("リージョン " + regionId + " のフラグ " + flagName + " の設定に失敗しました");
                }
            }
        }
        
        return true;
    }

    /**
     * 賃貸契約を締結
     */
    public synchronized RentalResult rentProperty(UUID tenantUuid, int propertyId, String period, int units) {
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
                
                logger.info("賃貸契約を締結しました: " + player.getUuid() + " -> 物件ID: " + propertyId);
                
                // プレイヤーに通知（ゲーム内時間表示）
                if (onlinePlayer != null) {
                    long realMinutes = rentalDays * 20; // 1ゲーム内日 = 20分
                    onlinePlayer.sendMessage("§a" + property.getPropertyName() + "の賃貸契約を締結しました");
                    onlinePlayer.sendMessage("§e期間: " + rentalDays + "日(" + realMinutes + "分) | 費用: " + totalCost);
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
                
                logger.info("契約が期限切れになりました: 契約ID " + rental.getId());
            }
            
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
