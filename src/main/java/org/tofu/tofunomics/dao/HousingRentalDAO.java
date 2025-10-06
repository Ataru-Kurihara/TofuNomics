package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.HousingRental;
import org.tofu.tofunomics.models.HousingRentalHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 住居賃貸契約のデータアクセスオブジェクト
 */
public class HousingRentalDAO {
    private final Connection connection;

    public HousingRentalDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 賃貸契約を新規作成
     */
    public int createRental(HousingRental rental) throws SQLException {
        String query = "INSERT INTO housing_rentals " +
                      "(property_id, tenant_uuid, rental_period, rental_days, total_cost, " +
                      "start_date, end_date, status, auto_renew, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, rental.getPropertyId());
            statement.setString(2, rental.getTenantUuid().toString());
            statement.setString(3, rental.getRentalPeriod());
            statement.setInt(4, rental.getRentalDays());
            statement.setDouble(5, rental.getTotalCost());
            statement.setTimestamp(6, rental.getStartDate());
            statement.setTimestamp(7, rental.getEndDate());
            statement.setString(8, rental.getStatus());
            statement.setBoolean(9, rental.isAutoRenew());
            statement.setTimestamp(10, rental.getCreatedAt());
            
            statement.executeUpdate();
            
            // 生成されたIDを取得
            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                int id = generatedKeys.getInt(1);
                rental.setId(id);
                return id;
            }
        }
        return -1;
    }

    /**
     * IDで賃貸契約を取得
     */
    public HousingRental getRental(int id) throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            ResultSet rs = statement.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToRental(rs);
            }
        }
        return null;
    }

    /**
     * プレイヤーの有効な賃貸契約一覧を取得
     */
    public List<HousingRental> getActiveRentalsByTenant(UUID tenantUuid) throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE tenant_uuid = ? AND status = 'active' ORDER BY end_date";
        List<HousingRental> rentals = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tenantUuid.toString());
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                rentals.add(mapResultSetToRental(rs));
            }
        }
        return rentals;
    }

    /**
     * プレイヤーの全賃貸契約一覧を取得
     */
    public List<HousingRental> getAllRentalsByTenant(UUID tenantUuid) throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE tenant_uuid = ? ORDER BY created_at DESC";
        List<HousingRental> rentals = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tenantUuid.toString());
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                rentals.add(mapResultSetToRental(rs));
            }
        }
        return rentals;
    }

    /**
     * 物件の有効な賃貸契約を取得
     */
    public HousingRental getActiveRentalByProperty(int propertyId) throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE property_id = ? AND status = 'active' LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, propertyId);
            ResultSet rs = statement.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToRental(rs);
            }
        }
        return null;
    }

    /**
     * 全ての有効な賃貸契約を取得（期限チェック用）
     */
    public List<HousingRental> getAllActiveRentals() throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE status = 'active' ORDER BY end_date";
        List<HousingRental> rentals = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                rentals.add(mapResultSetToRental(rs));
            }
        }
        return rentals;
    }

    /**
     * 期限切れの賃貸契約を取得
     */
    public List<HousingRental> getExpiredRentals() throws SQLException {
        String query = "SELECT * FROM housing_rentals WHERE status = 'active' AND end_date <= ?";
        List<HousingRental> rentals = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                rentals.add(mapResultSetToRental(rs));
            }
        }
        return rentals;
    }

    /**
     * 賃貸契約を更新
     */
    public void updateRental(HousingRental rental) throws SQLException {
        String query = "UPDATE housing_rentals SET " +
                      "rental_period = ?, rental_days = ?, total_cost = ?, " +
                      "start_date = ?, end_date = ?, status = ?, auto_renew = ? " +
                      "WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, rental.getRentalPeriod());
            statement.setInt(2, rental.getRentalDays());
            statement.setDouble(3, rental.getTotalCost());
            statement.setTimestamp(4, rental.getStartDate());
            statement.setTimestamp(5, rental.getEndDate());
            statement.setString(6, rental.getStatus());
            statement.setBoolean(7, rental.isAutoRenew());
            statement.setInt(8, rental.getId());
            
            statement.executeUpdate();
        }
    }

    /**
     * 賃貸契約の状態を更新
     */
    public void updateRentalStatus(int rentalId, String status) throws SQLException {
        String query = "UPDATE housing_rentals SET status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, status);
            statement.setInt(2, rentalId);
            statement.executeUpdate();
        }
    }

    /**
     * 賃貸契約を削除
     */
    public void deleteRental(int id) throws SQLException {
        String query = "DELETE FROM housing_rentals WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /**
     * 賃貸履歴を追加
     */
    public void addRentalHistory(HousingRentalHistory history) throws SQLException {
        String query = "INSERT INTO housing_rental_history " +
                      "(rental_id, property_id, tenant_uuid, action_type, amount, action_date) " +
                      "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, history.getRentalId());
            statement.setInt(2, history.getPropertyId());
            statement.setString(3, history.getTenantUuid().toString());
            statement.setString(4, history.getActionType());
            
            if (history.getAmount() != null) {
                statement.setDouble(5, history.getAmount());
            } else {
                statement.setNull(5, Types.DOUBLE);
            }
            
            statement.setTimestamp(6, history.getActionDate());
            statement.executeUpdate();
        }
    }

    /**
     * プレイヤーの賃貸履歴を取得
     */
    public List<HousingRentalHistory> getRentalHistoryByTenant(UUID tenantUuid, int limit) throws SQLException {
        String query = "SELECT * FROM housing_rental_history WHERE tenant_uuid = ? " +
                      "ORDER BY action_date DESC LIMIT ?";
        List<HousingRentalHistory> histories = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tenantUuid.toString());
            statement.setInt(2, limit);
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                histories.add(mapResultSetToHistory(rs));
            }
        }
        return histories;
    }

    /**
     * ResultSetからHousingRentalオブジェクトにマッピング
     */
    private HousingRental mapResultSetToRental(ResultSet rs) throws SQLException {
        HousingRental rental = new HousingRental();
        rental.setId(rs.getInt("id"));
        rental.setPropertyId(rs.getInt("property_id"));
        rental.setTenantUuid(UUID.fromString(rs.getString("tenant_uuid")));
        rental.setRentalPeriod(rs.getString("rental_period"));
        rental.setRentalDays(rs.getInt("rental_days"));
        rental.setTotalCost(rs.getDouble("total_cost"));
        rental.setStartDate(rs.getTimestamp("start_date"));
        rental.setEndDate(rs.getTimestamp("end_date"));
        rental.setStatus(rs.getString("status"));
        rental.setAutoRenew(rs.getBoolean("auto_renew"));
        rental.setCreatedAt(rs.getTimestamp("created_at"));
        
        return rental;
    }

    /**
     * ResultSetからHousingRentalHistoryオブジェクトにマッピング
     */
    private HousingRentalHistory mapResultSetToHistory(ResultSet rs) throws SQLException {
        HousingRentalHistory history = new HousingRentalHistory();
        history.setId(rs.getInt("id"));
        history.setRentalId(rs.getInt("rental_id"));
        history.setPropertyId(rs.getInt("property_id"));
        history.setTenantUuid(UUID.fromString(rs.getString("tenant_uuid")));
        history.setActionType(rs.getString("action_type"));
        
        double amount = rs.getDouble("amount");
        if (!rs.wasNull()) {
            history.setAmount(amount);
        }
        
        history.setActionDate(rs.getTimestamp("action_date"));
        
        return history;
    }
}
