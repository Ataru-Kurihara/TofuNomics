package org.tofu.tofunomics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 退去時に預かった荷物（housing_parcels）のデータアクセス。1 行 = アイテム 1 スタック。
 */
public class HousingParcelDAO {

    /** 荷物 1 件 */
    public static class Parcel {
        private final int id;
        private final UUID tenantUuid;
        private final int propertyId;
        private final String itemData;

        public Parcel(int id, UUID tenantUuid, int propertyId, String itemData) {
            this.id = id;
            this.tenantUuid = tenantUuid;
            this.propertyId = propertyId;
            this.itemData = itemData;
        }

        public int getId() { return id; }
        public UUID getTenantUuid() { return tenantUuid; }
        public int getPropertyId() { return propertyId; }
        public String getItemData() { return itemData; }
    }

    private final Connection connection;

    public HousingParcelDAO(Connection connection) {
        this.connection = connection;
    }

    public void add(UUID tenantUuid, int propertyId, String itemData) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO housing_parcels (tenant_uuid, property_id, item_data) VALUES (?, ?, ?)")) {
            ps.setString(1, tenantUuid.toString());
            ps.setInt(2, propertyId);
            ps.setString(3, itemData);
            ps.executeUpdate();
        }
    }

    public List<Parcel> findByTenant(UUID tenantUuid) throws SQLException {
        List<Parcel> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM housing_parcels WHERE tenant_uuid = ? ORDER BY id")) {
            ps.setString(1, tenantUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Parcel(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("tenant_uuid")),
                            rs.getInt("property_id"),
                            rs.getString("item_data")));
                }
            }
        }
        return result;
    }

    public int countByTenant(UUID tenantUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM housing_parcels WHERE tenant_uuid = ?")) {
            ps.setString(1, tenantUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 荷物を受け取り済みにする（行を消す）。
     *
     * @return 消せたら true（二重受け取りの防止に使う）
     */
    public boolean delete(int id, UUID tenantUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM housing_parcels WHERE id = ? AND tenant_uuid = ?")) {
            ps.setInt(1, id);
            ps.setString(2, tenantUuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 受け取りで一部だけ手持ちに入ったとき、残りの量で置き換える。
     */
    public void updateItem(int id, String itemData) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE housing_parcels SET item_data = ? WHERE id = ?")) {
            ps.setString(1, itemData);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }
}
