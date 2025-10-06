package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.HousingProperty;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 住居物件のデータアクセスオブジェクト
 */
public class HousingPropertyDAO {
    private final Connection connection;

    public HousingPropertyDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 物件を新規作成
     */
    public int createProperty(HousingProperty property) throws SQLException {
        String query = "INSERT INTO housing_properties " +
                      "(property_name, world_name, x1, y1, z1, x2, y2, z2, " +
                      "worldguard_region_id, description, daily_rent, weekly_rent, monthly_rent, " +
                      "is_available, owner_uuid, created_at, updated_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, property.getPropertyName());
            statement.setString(2, property.getWorldName());
            
            // 座標（NULLの可能性あり）
            setIntegerOrNull(statement, 3, property.getX1());
            setIntegerOrNull(statement, 4, property.getY1());
            setIntegerOrNull(statement, 5, property.getZ1());
            setIntegerOrNull(statement, 6, property.getX2());
            setIntegerOrNull(statement, 7, property.getY2());
            setIntegerOrNull(statement, 8, property.getZ2());
            
            statement.setString(9, property.getWorldguardRegionId());
            statement.setString(10, property.getDescription());
            statement.setDouble(11, property.getDailyRent());
            
            setDoubleOrNull(statement, 12, property.getWeeklyRent());
            setDoubleOrNull(statement, 13, property.getMonthlyRent());
            
            statement.setBoolean(14, property.isAvailable());
            
            if (property.getOwnerUuid() != null) {
                statement.setString(15, property.getOwnerUuid().toString());
            } else {
                statement.setNull(15, Types.VARCHAR);
            }
            
            statement.setTimestamp(16, property.getCreatedAt());
            statement.setTimestamp(17, property.getUpdatedAt());
            
            statement.executeUpdate();
            
            // 生成されたIDを取得
            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                int id = generatedKeys.getInt(1);
                property.setId(id);
                return id;
            }
        }
        return -1;
    }

    /**
     * IDで物件を取得
     */
    public HousingProperty getProperty(int id) throws SQLException {
        String query = "SELECT * FROM housing_properties WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            ResultSet rs = statement.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToProperty(rs);
            }
        }
        return null;
    }

    /**
     * 全物件を取得
     */
    public List<HousingProperty> getAllProperties() throws SQLException {
        String query = "SELECT * FROM housing_properties ORDER BY id";
        List<HousingProperty> properties = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                properties.add(mapResultSetToProperty(rs));
            }
        }
        return properties;
    }

    /**
     * 賃貸可能な物件一覧を取得
     */
    public List<HousingProperty> getAvailableProperties() throws SQLException {
        String query = "SELECT * FROM housing_properties WHERE is_available = TRUE ORDER BY id";
        List<HousingProperty> properties = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                properties.add(mapResultSetToProperty(rs));
            }
        }
        return properties;
    }

    /**
     * 運営所有物件を取得
     */
    public List<HousingProperty> getSystemOwnedProperties() throws SQLException {
        String query = "SELECT * FROM housing_properties WHERE owner_uuid IS NULL ORDER BY id";
        List<HousingProperty> properties = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                properties.add(mapResultSetToProperty(rs));
            }
        }
        return properties;
    }

    /**
     * プレイヤー所有物件を取得（将来用）
     */
    public List<HousingProperty> getPlayerOwnedProperties(UUID ownerUuid) throws SQLException {
        String query = "SELECT * FROM housing_properties WHERE owner_uuid = ? ORDER BY id";
        List<HousingProperty> properties = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ownerUuid.toString());
            ResultSet rs = statement.executeQuery();
            
            while (rs.next()) {
                properties.add(mapResultSetToProperty(rs));
            }
        }
        return properties;
    }

    /**
     * 物件を更新
     */
    public void updateProperty(HousingProperty property) throws SQLException {
        String query = "UPDATE housing_properties SET " +
                      "property_name = ?, world_name = ?, " +
                      "x1 = ?, y1 = ?, z1 = ?, x2 = ?, y2 = ?, z2 = ?, " +
                      "worldguard_region_id = ?, description = ?, " +
                      "daily_rent = ?, weekly_rent = ?, monthly_rent = ?, " +
                      "is_available = ?, owner_uuid = ?, updated_at = ? " +
                      "WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, property.getPropertyName());
            statement.setString(2, property.getWorldName());
            
            setIntegerOrNull(statement, 3, property.getX1());
            setIntegerOrNull(statement, 4, property.getY1());
            setIntegerOrNull(statement, 5, property.getZ1());
            setIntegerOrNull(statement, 6, property.getX2());
            setIntegerOrNull(statement, 7, property.getY2());
            setIntegerOrNull(statement, 8, property.getZ2());
            
            statement.setString(9, property.getWorldguardRegionId());
            statement.setString(10, property.getDescription());
            statement.setDouble(11, property.getDailyRent());
            
            setDoubleOrNull(statement, 12, property.getWeeklyRent());
            setDoubleOrNull(statement, 13, property.getMonthlyRent());
            
            statement.setBoolean(14, property.isAvailable());
            
            if (property.getOwnerUuid() != null) {
                statement.setString(15, property.getOwnerUuid().toString());
            } else {
                statement.setNull(15, Types.VARCHAR);
            }
            
            statement.setTimestamp(16, new Timestamp(System.currentTimeMillis()));
            statement.setInt(17, property.getId());
            
            statement.executeUpdate();
        }
    }

    /**
     * 物件の利用可能状態を更新
     */
    public void updateAvailability(int propertyId, boolean isAvailable) throws SQLException {
        String query = "UPDATE housing_properties SET is_available = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBoolean(1, isAvailable);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setInt(3, propertyId);
            statement.executeUpdate();
        }
    }

    /**
     * 物件を削除
     */
    public void deleteProperty(int id) throws SQLException {
        String query = "DELETE FROM housing_properties WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /**
     * ResultSetからHousingPropertyオブジェクトにマッピング
     */
    private HousingProperty mapResultSetToProperty(ResultSet rs) throws SQLException {
        HousingProperty property = new HousingProperty();
        property.setId(rs.getInt("id"));
        property.setPropertyName(rs.getString("property_name"));
        property.setWorldName(rs.getString("world_name"));
        
        // NULLチェック付きで座標を設定
        property.setX1(getIntegerOrNull(rs, "x1"));
        property.setY1(getIntegerOrNull(rs, "y1"));
        property.setZ1(getIntegerOrNull(rs, "z1"));
        property.setX2(getIntegerOrNull(rs, "x2"));
        property.setY2(getIntegerOrNull(rs, "y2"));
        property.setZ2(getIntegerOrNull(rs, "z2"));
        
        property.setWorldguardRegionId(rs.getString("worldguard_region_id"));
        property.setDescription(rs.getString("description"));
        property.setDailyRent(rs.getDouble("daily_rent"));
        
        property.setWeeklyRent(getDoubleOrNull(rs, "weekly_rent"));
        property.setMonthlyRent(getDoubleOrNull(rs, "monthly_rent"));
        
        property.setAvailable(rs.getBoolean("is_available"));
        
        String ownerUuidStr = rs.getString("owner_uuid");
        if (ownerUuidStr != null) {
            property.setOwnerUuid(UUID.fromString(ownerUuidStr));
        }
        
        property.setCreatedAt(rs.getTimestamp("created_at"));
        property.setUpdatedAt(rs.getTimestamp("updated_at"));
        
        return property;
    }

    /**
     * PreparedStatementにIntegerまたはNULLを設定
     */
    private void setIntegerOrNull(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value != null) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    /**
     * PreparedStatementにDoubleまたはNULLを設定
     */
    private void setDoubleOrNull(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value != null) {
            statement.setDouble(index, value);
        } else {
            statement.setNull(index, Types.DOUBLE);
        }
    }

    /**
     * ResultSetからIntegerまたはNULLを取得
     */
    private Integer getIntegerOrNull(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * ResultSetからDoubleまたはNULLを取得
     */
    private Double getDoubleOrNull(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }
}
