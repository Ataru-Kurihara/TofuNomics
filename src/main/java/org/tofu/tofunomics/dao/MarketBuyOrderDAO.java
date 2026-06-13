package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.MarketBuyOrder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * プレイヤー間マーケット買い注文（募集）のデータアクセスオブジェクト
 *
 * 売り出品の {@link MarketListingDAO} の鏡像。
 * expires_at は epoch millis（INTEGER）で保持し、期限判定は数値比較で行う。
 */
public class MarketBuyOrderDAO {
    private final Connection connection;

    public MarketBuyOrderDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 募集を新規作成し、生成された id を返す。
     * listed_at は DB 側の DEFAULT CURRENT_TIMESTAMP に委ねる。
     */
    public int insertBuyOrder(MarketBuyOrder order) throws SQLException {
        String query = "INSERT INTO market_buy_orders " +
                "(requester_uuid, requester_name, material, amount, price, status, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, order.getRequesterUuid().toString());
            statement.setString(2, order.getRequesterName());
            statement.setString(3, order.getMaterial());
            statement.setInt(4, order.getAmount());
            statement.setDouble(5, order.getPrice());
            statement.setString(6, order.getStatus() != null ? order.getStatus() : MarketBuyOrder.STATUS_OPEN);
            if (order.getExpiresAtMillis() != null) {
                statement.setLong(7, order.getExpiresAtMillis());
            } else {
                statement.setNull(7, Types.BIGINT);
            }

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    order.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * id で募集を取得
     */
    public MarketBuyOrder getById(int id) throws SQLException {
        String query = "SELECT * FROM market_buy_orders WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * 募集中（open）の全募集を新しい順で取得（GUI 一覧用）
     */
    public List<MarketBuyOrder> getOpenOrders() throws SQLException {
        String query = "SELECT * FROM market_buy_orders WHERE status = ? ORDER BY listed_at DESC, id DESC";
        List<MarketBuyOrder> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketBuyOrder.STATUS_OPEN);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSet(rs));
                }
            }
        }
        return orders;
    }

    /**
     * 指定募集者の全募集（status 問わず）を新しい順で取得（myrequests 用）
     */
    public List<MarketBuyOrder> getOrdersByRequester(UUID requesterUuid) throws SQLException {
        String query = "SELECT * FROM market_buy_orders WHERE requester_uuid = ? ORDER BY listed_at DESC, id DESC";
        List<MarketBuyOrder> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, requesterUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSet(rs));
                }
            }
        }
        return orders;
    }

    /**
     * 指定募集者の open 募集数をカウント（募集上限チェック用）
     */
    public int countOpenByRequester(UUID requesterUuid) throws SQLException {
        String query = "SELECT COUNT(*) FROM market_buy_orders WHERE requester_uuid = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, requesterUuid.toString());
            statement.setString(2, MarketBuyOrder.STATUS_OPEN);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 期限切れの open 募集を取得（期限切れ返金処理用）。
     * 買い注文の期限切れは返金を伴うため、一括 UPDATE ではなく取得 → ループ処理にする。
     * @param nowMillis 現在時刻（epoch millis）
     */
    public List<MarketBuyOrder> getExpiredOpenOrders(long nowMillis) throws SQLException {
        String query = "SELECT * FROM market_buy_orders " +
                "WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?";
        List<MarketBuyOrder> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketBuyOrder.STATUS_OPEN);
            statement.setLong(2, nowMillis);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSet(rs));
                }
            }
        }
        return orders;
    }

    /**
     * 募集を供給成立（fulfilled）にする（楽観ロック）。
     * status='open' の行のみを対象とし、影響行数が 1 のときだけ成功とする。
     * これにより、同一募集への同時供給（二重供給）を防止する。
     *
     * @param id 募集 id
     * @param supplierUuid 供給者 UUID
     * @param itemData 供給されたアイテム（Base64）
     * @param fulfilledAtMillis 成立時刻（epoch millis）
     * @return 成立処理に成功した場合 true（影響行数 == 1）
     */
    public boolean markAsFulfilled(int id, UUID supplierUuid, String itemData, long fulfilledAtMillis) throws SQLException {
        String query = "UPDATE market_buy_orders SET status = ?, supplier_uuid = ?, item_data = ?, fulfilled_at = ? " +
                "WHERE id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketBuyOrder.STATUS_FULFILLED);
            statement.setString(2, supplierUuid.toString());
            statement.setString(3, itemData);
            statement.setTimestamp(4, new Timestamp(fulfilledAtMillis));
            statement.setInt(5, id);
            statement.setString(6, MarketBuyOrder.STATUS_OPEN);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * 募集のステータスを条件付きで更新する（楽観ロック）。
     * 現在のステータスが expectedStatus の行のみを newStatus に更新し、
     * 影響行数が 1 のときだけ成功とする。
     *
     * cancel（open → cancelled）/ reclaim（fulfilled → reclaimed）/
     * expire（open → expired）に用いる。
     *
     * @return 更新に成功した場合 true（影響行数 == 1）
     */
    public boolean updateStatusConditional(int id, String expectedStatus, String newStatus) throws SQLException {
        String query = "UPDATE market_buy_orders SET status = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, newStatus);
            statement.setInt(2, id);
            statement.setString(3, expectedStatus);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * ResultSet から MarketBuyOrder にマッピング
     */
    private MarketBuyOrder mapResultSet(ResultSet rs) throws SQLException {
        MarketBuyOrder order = new MarketBuyOrder();
        order.setId(rs.getInt("id"));
        order.setRequesterUuid(UUID.fromString(rs.getString("requester_uuid")));
        order.setRequesterName(rs.getString("requester_name"));
        order.setMaterial(rs.getString("material"));
        order.setAmount(rs.getInt("amount"));
        order.setPrice(rs.getDouble("price"));
        order.setStatus(rs.getString("status"));

        String supplierUuid = rs.getString("supplier_uuid");
        if (supplierUuid != null) {
            order.setSupplierUuid(UUID.fromString(supplierUuid));
        }

        order.setItemData(rs.getString("item_data"));
        order.setListedAt(rs.getTimestamp("listed_at"));

        long expiresAt = rs.getLong("expires_at");
        if (!rs.wasNull()) {
            order.setExpiresAtMillis(expiresAt);
        }

        order.setFulfilledAt(rs.getTimestamp("fulfilled_at"));

        return order;
    }
}
