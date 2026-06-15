package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.MarketServiceRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * マーケットサービス依頼（修理・エンチャント募集）のDAOクラス
 */
public class MarketServiceRequestDAO {
    private final Connection connection;

    public MarketServiceRequestDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * サービス依頼を挿入し、生成されたIDを返す
     */
    public int insertServiceRequest(MarketServiceRequest req) throws SQLException {
        String sql = "INSERT INTO market_service_requests " +
            "(requester_uuid, requester_name, service_type, material, item_data, " +
            "enchant_type, enchant_level, price, status, expires_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.getRequesterUuid().toString());
            ps.setString(2, req.getRequesterName());
            ps.setString(3, req.getServiceType());
            ps.setString(4, req.getMaterial());
            ps.setString(5, req.getItemData());
            if (req.getEnchantType() != null) {
                ps.setString(6, req.getEnchantType());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            if (req.getEnchantLevel() != null) {
                ps.setInt(7, req.getEnchantLevel());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setDouble(8, req.getPrice());
            ps.setString(9, req.getStatus());
            if (req.getExpiresAt() != null) {
                ps.setLong(10, req.getExpiresAt());
            } else {
                ps.setNull(10, Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    req.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * IDでサービス依頼を取得
     */
    public MarketServiceRequest getById(int id) throws SQLException {
        String sql = "SELECT * FROM market_service_requests WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * オープン中のサービス依頼一覧を取得（新しい順）
     */
    public List<MarketServiceRequest> getOpenRequests() throws SQLException {
        String sql = "SELECT * FROM market_service_requests WHERE status = 'open' ORDER BY listed_at DESC";
        List<MarketServiceRequest> requests = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(mapResultSet(rs));
            }
        }
        return requests;
    }

    /**
     * 指定プレイヤーのオープン中サービス依頼数を取得
     */
    public int countOpenByRequester(UUID requesterUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM market_service_requests WHERE requester_uuid = ? AND status = 'open'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requesterUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 指定プレイヤーの全サービス依頼を取得（新しい順）
     */
    public List<MarketServiceRequest> getByRequester(UUID requesterUuid) throws SQLException {
        String sql = "SELECT * FROM market_service_requests WHERE requester_uuid = ? ORDER BY listed_at DESC";
        List<MarketServiceRequest> requests = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requesterUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapResultSet(rs));
                }
            }
        }
        return requests;
    }

    /**
     * 加工完了（楽観ロック: open のもののみ fulfilled に更新）
     */
    public boolean markAsFulfilled(int id, UUID workerUuid, String resultItemData, long fulfilledAtMillis) throws SQLException {
        String sql = "UPDATE market_service_requests SET status = 'fulfilled', worker_uuid = ?, " +
            "result_item_data = ?, fulfilled_at = ? WHERE id = ? AND status = 'open'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workerUuid.toString());
            ps.setString(2, resultItemData);
            ps.setTimestamp(3, new Timestamp(fulfilledAtMillis));
            ps.setInt(4, id);
            int affected = ps.executeUpdate();
            return affected == 1;
        }
    }

    /**
     * ステータスを条件付きで更新（楽観ロック）
     */
    public boolean updateStatusConditional(int id, String expectedStatus, String newStatus) throws SQLException {
        String sql = "UPDATE market_service_requests SET status = ? WHERE id = ? AND status = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            ps.setString(3, expectedStatus);
            int affected = ps.executeUpdate();
            return affected == 1;
        }
    }

    /**
     * 期限切れのオープン依頼を取得
     */
    public List<MarketServiceRequest> getExpiredOpenRequests(long nowMillis) throws SQLException {
        String sql = "SELECT * FROM market_service_requests WHERE status = 'open' AND expires_at IS NOT NULL AND expires_at <= ?";
        List<MarketServiceRequest> requests = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, nowMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapResultSet(rs));
                }
            }
        }
        return requests;
    }

    /**
     * 共通のResultSetマッピング処理
     */
    private MarketServiceRequest mapResultSet(ResultSet rs) throws SQLException {
        MarketServiceRequest req = new MarketServiceRequest();
        req.setId(rs.getInt("id"));
        req.setRequesterUuid(UUID.fromString(rs.getString("requester_uuid")));
        req.setRequesterName(rs.getString("requester_name"));
        req.setServiceType(rs.getString("service_type"));
        req.setMaterial(rs.getString("material"));
        req.setItemData(rs.getString("item_data"));
        req.setEnchantType(rs.getString("enchant_type"));
        int enchantLevel = rs.getInt("enchant_level");
        if (!rs.wasNull()) {
            req.setEnchantLevel(enchantLevel);
        }
        req.setPrice(rs.getDouble("price"));
        req.setStatus(rs.getString("status"));
        String workerUuid = rs.getString("worker_uuid");
        if (workerUuid != null) {
            req.setWorkerUuid(UUID.fromString(workerUuid));
        }
        req.setResultItemData(rs.getString("result_item_data"));
        Timestamp listedAt = rs.getTimestamp("listed_at");
        if (listedAt != null) {
            req.setListedAt(listedAt);
        }
        long expiresAt = rs.getLong("expires_at");
        if (!rs.wasNull()) {
            req.setExpiresAt(expiresAt);
        }
        Timestamp fulfilledAt = rs.getTimestamp("fulfilled_at");
        if (fulfilledAt != null) {
            req.setFulfilledAt(fulfilledAt);
        }
        return req;
    }
}
