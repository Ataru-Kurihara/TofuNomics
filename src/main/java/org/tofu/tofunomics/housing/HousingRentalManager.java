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
    
    private HousingPropertyDAO propertyDAO;
    private HousingRentalDAO rentalDAO;
    private PlayerDAO playerDAO;

    public HousingRentalManager(TofuNomics plugin, ConfigManager configManager, DatabaseManager databaseManager, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
        this.worldGuardIntegration = worldGuardIntegration;
        
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
     * WorldGuard領域を作成
     */
    public boolean createWorldGuardRegion(String regionId, World world, Location pos1, Location pos2) {
        if (worldGuardIntegration == null || !worldGuardIntegration.isEnabled()) {
            logger.warning("WorldGuardが無効です");
            return false;
        }
        
        return worldGuardIntegration.createRegion(regionId, world, pos1, pos2);
    }

    /**
     * 賃貸契約を締結
     */
    public RentalResult rentProperty(UUID tenantUuid, int propertyId, String period, int units) {
        try {
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
            
            // 残高チェックと支払い
            org.tofu.tofunomics.models.Player player = playerDAO.getOrCreatePlayer(tenantUuid);
            if (player.getBankBalance() < totalCost) {
                return new RentalResult(false, "残高が不足しています (必要: " + totalCost + ")");
            }
            
            // 契約作成
            HousingRental rental = new HousingRental(propertyId, tenantUuid, period, rentalDays, totalCost);
            int rentalId = rentalDAO.createRental(rental);
            
            if (rentalId > 0) {
                // 支払い処理
                player.removeBankBalance(totalCost);
                playerDAO.updatePlayer(player);
                
                // 物件を利用不可に
                propertyDAO.updateAvailability(propertyId, false);
                
                // 履歴追加
                HousingRentalHistory history = new HousingRentalHistory(
                    rentalId, propertyId, tenantUuid, "rent", totalCost
                );
                rentalDAO.addRentalHistory(history);
                
                // WorldGuard領域が設定されている場合、プレイヤーをメンバーに追加
                if (property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                    World world = Bukkit.getWorld(property.getWorldName());
                    if (world != null) {
                        boolean memberAdded = worldGuardIntegration.addMember(
                            property.getWorldguardRegionId(), 
                            world, 
                            tenantUuid
                        );
                        if (memberAdded) {
                            logger.info("プレイヤー " + tenantUuid + " をWorldGuard領域 " + property.getWorldguardRegionId() + " に追加しました");
                        }
                    }
                }
                
                logger.info("賃貸契約を締結しました: " + player.getUuid() + " -> 物件ID: " + propertyId);
                
                // プレイヤーに通知
                org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(tenantUuid);
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage("§a" + property.getPropertyName() + "の賃貸契約を締結しました");
                    onlinePlayer.sendMessage("§e期間: " + rentalDays + "日間 | 費用: " + totalCost);
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
    public RentalResult extendRental(UUID tenantUuid, int propertyId, int additionalDays) {
        try {
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
            
            // 残高チェック
            org.tofu.tofunomics.models.Player player = playerDAO.getOrCreatePlayer(tenantUuid);
            if (player.getBankBalance() < additionalCost) {
                return new RentalResult(false, "残高が不足しています (必要: " + additionalCost + ")");
            }
            
            // 契約延長
            rental.extend(additionalDays, additionalCost);
            rentalDAO.updateRental(rental);
            
            // 支払い処理
            player.removeBankBalance(additionalCost);
            playerDAO.updatePlayer(player);
            
            // 履歴追加
            HousingRentalHistory history = new HousingRentalHistory(
                rental.getId(), propertyId, tenantUuid, "extend", additionalCost
            );
            rentalDAO.addRentalHistory(history);
            
            logger.info("契約を延長しました: 契約ID " + rental.getId() + " -> +" + additionalDays + "日");
            
            return new RentalResult(true, "契約を" + additionalDays + "日延長しました (費用: " + additionalCost + ")");
            
        } catch (SQLException e) {
            logger.severe("契約延長に失敗しました: " + e.getMessage());
            return new RentalResult(false, "データベースエラーが発生しました");
        }
    }

    /**
     * 契約をキャンセル
     */
    public RentalResult cancelRental(UUID tenantUuid, int propertyId) {
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
            List<HousingRental> expiredRentals = rentalDAO.getExpiredRentals();
            
            for (HousingRental rental : expiredRentals) {
                rental.expire();
                rentalDAO.updateRental(rental);
                
                // 物件を利用可能に
                propertyDAO.updateAvailability(rental.getPropertyId(), true);
                
                // WorldGuard領域からプレイヤーを削除
                HousingProperty property = propertyDAO.getProperty(rental.getPropertyId());
                if (property != null && property.hasWorldGuardRegion() && worldGuardIntegration != null && worldGuardIntegration.isEnabled()) {
                    World world = Bukkit.getWorld(property.getWorldName());
                    if (world != null) {
                        boolean memberRemoved = worldGuardIntegration.removeMember(
                            property.getWorldguardRegionId(), 
                            world, 
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
