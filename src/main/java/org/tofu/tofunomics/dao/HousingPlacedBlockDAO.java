package org.tofu.tofunomics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 借主が物件内に置いたブロックの記録（housing_placed_blocks）のデータアクセス。
 *
 * 記録は「借主が壊してよいブロック」の判定と、退去時の原状回復に使う。
 * 同じ座標には 1 件だけ（UNIQUE(world_name, x, y, z)）。
 */
public class HousingPlacedBlockDAO {

    /** 記録 1 件 */
    public static class PlacedBlock {
        private final int id;
        private final int rentalId;
        private final int propertyId;
        private final UUID tenantUuid;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final String material;
        private final String itemData;

        public PlacedBlock(int id, int rentalId, int propertyId, UUID tenantUuid, String worldName,
                           int x, int y, int z, String material, String itemData) {
            this.id = id;
            this.rentalId = rentalId;
            this.propertyId = propertyId;
            this.tenantUuid = tenantUuid;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.itemData = itemData;
        }

        public int getId() { return id; }
        public int getRentalId() { return rentalId; }
        public int getPropertyId() { return propertyId; }
        public UUID getTenantUuid() { return tenantUuid; }
        public String getWorldName() { return worldName; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public String getMaterial() { return material; }
        /** 置いたアイテム（Base64）。ドアの上半分など、2 個目以降のブロックは null */
        public String getItemData() { return itemData; }
    }

    private final Connection connection;

    public HousingPlacedBlockDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 設置を記録する。同じ座標に古い記録があれば置き換える。
     */
    public void record(int rentalId, int propertyId, UUID tenantUuid, String worldName,
                       int x, int y, int z, String material, String itemData) throws SQLException {
        String sql = "INSERT OR REPLACE INTO housing_placed_blocks " +
                "(rental_id, property_id, tenant_uuid, world_name, x, y, z, material, item_data) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setInt(2, propertyId);
            ps.setString(3, tenantUuid.toString());
            ps.setString(4, worldName);
            ps.setInt(5, x);
            ps.setInt(6, y);
            ps.setInt(7, z);
            ps.setString(8, material);
            ps.setString(9, itemData);
            ps.executeUpdate();
        }
    }

    /**
     * 座標の記録を取得する（無ければ null）。
     */
    public PlacedBlock find(String worldName, int x, int y, int z) throws SQLException {
        String sql = "SELECT * FROM housing_placed_blocks WHERE world_name = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, worldName);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * 契約の記録をすべて取得する。
     */
    public List<PlacedBlock> findByRental(int rentalId) throws SQLException {
        String sql = "SELECT * FROM housing_placed_blocks WHERE rental_id = ? ORDER BY id";
        List<PlacedBlock> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /**
     * 座標の記録を消す。
     */
    public void delete(String worldName, int x, int y, int z) throws SQLException {
        String sql = "DELETE FROM housing_placed_blocks WHERE world_name = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, worldName);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }

    /**
     * 契約の記録をすべて消す。
     */
    public void deleteByRental(int rentalId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM housing_placed_blocks WHERE rental_id = ?")) {
            ps.setInt(1, rentalId);
            ps.executeUpdate();
        }
    }

    private PlacedBlock map(ResultSet rs) throws SQLException {
        return new PlacedBlock(
                rs.getInt("id"),
                rs.getInt("rental_id"),
                rs.getInt("property_id"),
                UUID.fromString(rs.getString("tenant_uuid")),
                rs.getString("world_name"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getString("material"),
                rs.getString("item_data"));
    }
}
