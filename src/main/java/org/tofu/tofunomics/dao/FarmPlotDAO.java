package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.FarmPlot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 農家畑区画(farm_plots)のデータアクセスオブジェクト
 */
public class FarmPlotDAO {
    private final Connection connection;

    public FarmPlotDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 区画を新規作成し、生成IDを返す（失敗時 -1）
     */
    public int createPlot(FarmPlot plot) throws SQLException {
        String query = "INSERT INTO farm_plots " +
                "(plot_name, world_name, x1, y1, z1, x2, y2, z2, worldguard_region_id, owner_uuid) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, plot.getPlotName());
            st.setString(2, plot.getWorldName());
            st.setInt(3, plot.getX1());
            st.setInt(4, plot.getY1());
            st.setInt(5, plot.getZ1());
            st.setInt(6, plot.getX2());
            st.setInt(7, plot.getY2());
            st.setInt(8, plot.getZ2());
            st.setString(9, plot.getWorldguardRegionId());
            if (plot.getOwnerUuid() != null) {
                st.setString(10, plot.getOwnerUuid());
            } else {
                st.setNull(10, Types.VARCHAR);
            }
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    plot.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    public FarmPlot getById(int id) throws SQLException {
        String query = "SELECT * FROM farm_plots WHERE id = ?";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public FarmPlot getPlotByName(String plotName) throws SQLException {
        String query = "SELECT * FROM farm_plots WHERE plot_name = ?";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            st.setString(1, plotName);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<FarmPlot> getAllPlots() throws SQLException {
        String query = "SELECT * FROM farm_plots ORDER BY id";
        List<FarmPlot> plots = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(query);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                plots.add(map(rs));
            }
        }
        return plots;
    }

    /**
     * 指定プレイヤーが所有する区画（1人1区画のため単一想定）
     */
    public FarmPlot getPlotByOwner(String ownerUuid) throws SQLException {
        String query = "SELECT * FROM farm_plots WHERE owner_uuid = ? LIMIT 1";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            st.setString(1, ownerUuid);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    /**
     * 空き区画（owner_uuid IS NULL）を1件取得（なければnull）
     */
    public FarmPlot findAvailablePlot() throws SQLException {
        String query = "SELECT * FROM farm_plots WHERE owner_uuid IS NULL ORDER BY id LIMIT 1";
        try (PreparedStatement st = connection.prepareStatement(query);
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                return map(rs);
            }
        }
        return null;
    }

    /**
     * 区画の所有者を更新（解放する場合は ownerUuid に null を渡す）
     */
    public boolean updateOwner(int id, String ownerUuid) throws SQLException {
        String query = "UPDATE farm_plots SET owner_uuid = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            if (ownerUuid != null) {
                st.setString(1, ownerUuid);
            } else {
                st.setNull(1, Types.VARCHAR);
            }
            st.setInt(2, id);
            return st.executeUpdate() > 0;
        }
    }

    /**
     * 区画の座標範囲を更新する（owner_uuid・worldguard_region_id・world_nameは変更しない）
     */
    public boolean updateCoordinates(int id, int x1, int y1, int z1, int x2, int y2, int z2) throws SQLException {
        String query = "UPDATE farm_plots SET x1 = ?, y1 = ?, z1 = ?, x2 = ?, y2 = ?, z2 = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            st.setInt(1, x1);
            st.setInt(2, y1);
            st.setInt(3, z1);
            st.setInt(4, x2);
            st.setInt(5, y2);
            st.setInt(6, z2);
            st.setInt(7, id);
            return st.executeUpdate() > 0;
        }
    }

    public boolean deletePlot(int id) throws SQLException {
        String query = "DELETE FROM farm_plots WHERE id = ?";
        try (PreparedStatement st = connection.prepareStatement(query)) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        }
    }

    private FarmPlot map(ResultSet rs) throws SQLException {
        FarmPlot plot = new FarmPlot();
        plot.setId(rs.getInt("id"));
        plot.setPlotName(rs.getString("plot_name"));
        plot.setWorldName(rs.getString("world_name"));
        plot.setX1(rs.getInt("x1"));
        plot.setY1(rs.getInt("y1"));
        plot.setZ1(rs.getInt("z1"));
        plot.setX2(rs.getInt("x2"));
        plot.setY2(rs.getInt("y2"));
        plot.setZ2(rs.getInt("z2"));
        plot.setWorldguardRegionId(rs.getString("worldguard_region_id"));
        plot.setOwnerUuid(rs.getString("owner_uuid"));
        plot.setCreatedAt(rs.getTimestamp("created_at"));
        plot.setUpdatedAt(rs.getTimestamp("updated_at"));
        return plot;
    }
}
