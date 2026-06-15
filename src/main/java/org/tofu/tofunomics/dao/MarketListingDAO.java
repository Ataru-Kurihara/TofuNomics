package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.MarketListing;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * プレイヤー間マーケット出品のデータアクセスオブジェクト
 *
 * expires_at は epoch millis（INTEGER）で保持し、期限判定は数値比較で行う
 * （CURRENT_TIMESTAMP との文字列比較によるフォーマット依存を避けるため）。
 */
public class MarketListingDAO {
    private final Connection connection;

    public MarketListingDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 出品を新規作成し、生成された id を返す。
     * listed_at は DB 側の DEFAULT CURRENT_TIMESTAMP に委ねる。
     */
    public int insertListing(MarketListing listing) throws SQLException {
        String query = "INSERT INTO market_listings " +
                "(seller_uuid, seller_name, item_data, display_name, material, amount, price, status, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, listing.getSellerUuid().toString());
            statement.setString(2, listing.getSellerName());
            statement.setString(3, listing.getItemData());
            statement.setString(4, listing.getDisplayName());
            statement.setString(5, listing.getMaterial());
            statement.setInt(6, listing.getAmount());
            statement.setDouble(7, listing.getPrice());
            statement.setString(8, listing.getStatus() != null ? listing.getStatus() : MarketListing.STATUS_ACTIVE);
            if (listing.getExpiresAtMillis() != null) {
                statement.setLong(9, listing.getExpiresAtMillis());
            } else {
                statement.setNull(9, Types.BIGINT);
            }

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    listing.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * id で出品を取得
     */
    public MarketListing getById(int id) throws SQLException {
        String query = "SELECT * FROM market_listings WHERE id = ?";
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
     * 出品中（active）の全出品を新しい順で取得（GUI 一覧用）
     */
    public List<MarketListing> getActiveListings() throws SQLException {
        String query = "SELECT * FROM market_listings WHERE status = ? ORDER BY listed_at DESC, id DESC";
        List<MarketListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketListing.STATUS_ACTIVE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    listings.add(mapResultSet(rs));
                }
            }
        }
        return listings;
    }

    /**
     * 指定出品者の全出品（status 問わず）を新しい順で取得（mylistings 用）
     */
    public List<MarketListing> getListingsBySeller(UUID sellerUuid) throws SQLException {
        String query = "SELECT * FROM market_listings WHERE seller_uuid = ? ORDER BY listed_at DESC, id DESC";
        List<MarketListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    listings.add(mapResultSet(rs));
                }
            }
        }
        return listings;
    }

    /**
     * 指定出品者の active 出品数をカウント（出品上限チェック用）
     */
    public int countActiveBySeller(UUID sellerUuid) throws SQLException {
        String query = "SELECT COUNT(*) FROM market_listings WHERE seller_uuid = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sellerUuid.toString());
            statement.setString(2, MarketListing.STATUS_ACTIVE);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 期限切れの active 出品を取得（参照・テスト用）。
     * @param nowMillis 現在時刻（epoch millis）
     */
    public List<MarketListing> getExpiredActiveListings(long nowMillis) throws SQLException {
        String query = "SELECT * FROM market_listings " +
                "WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?";
        List<MarketListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketListing.STATUS_ACTIVE);
            statement.setLong(2, nowMillis);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    listings.add(mapResultSet(rs));
                }
            }
        }
        return listings;
    }

    /**
     * 期限切れの active 出品を一括で expired 化する。
     * @param nowMillis 現在時刻（epoch millis）
     * @return 期限切れ化した件数
     */
    public int expireActiveListings(long nowMillis) throws SQLException {
        String query = "UPDATE market_listings SET status = ? " +
                "WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketListing.STATUS_EXPIRED);
            statement.setString(2, MarketListing.STATUS_ACTIVE);
            statement.setLong(3, nowMillis);
            return statement.executeUpdate();
        }
    }

    /**
     * 出品を売却済みにする（楽観ロック）。
     * status='active' の行のみを対象とし、影響行数が 1 のときだけ成功とする。
     * これにより、同一出品への同時購入（二重購入）を防止する。
     *
     * @return 売却処理に成功した場合 true（影響行数 == 1）
     */
    public boolean markAsSold(int id, UUID buyerUuid, long soldAtMillis) throws SQLException {
        String query = "UPDATE market_listings SET status = ?, buyer_uuid = ?, sold_at = ? " +
                "WHERE id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, MarketListing.STATUS_SOLD);
            statement.setString(2, buyerUuid.toString());
            statement.setTimestamp(3, new Timestamp(soldAtMillis));
            statement.setInt(4, id);
            statement.setString(5, MarketListing.STATUS_ACTIVE);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * 出品のステータスを条件付きで更新する（楽観ロック）。
     * 現在のステータスが expectedStatus の行のみを newStatus に更新し、
     * 影響行数が 1 のときだけ成功とする。
     *
     * cancel（active → reclaimed）/ reclaim（expired → reclaimed）に用いる。
     *
     * @return 更新に成功した場合 true（影響行数 == 1）
     */
    public boolean updateStatusConditional(int id, String expectedStatus, String newStatus) throws SQLException {
        String query = "UPDATE market_listings SET status = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, newStatus);
            statement.setInt(2, id);
            statement.setString(3, expectedStatus);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * ResultSet から MarketListing にマッピング
     */
    private MarketListing mapResultSet(ResultSet rs) throws SQLException {
        MarketListing listing = new MarketListing();
        listing.setId(rs.getInt("id"));
        listing.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
        listing.setSellerName(rs.getString("seller_name"));
        listing.setItemData(rs.getString("item_data"));
        listing.setDisplayName(rs.getString("display_name"));
        listing.setMaterial(rs.getString("material"));
        listing.setAmount(rs.getInt("amount"));
        listing.setPrice(rs.getDouble("price"));
        listing.setStatus(rs.getString("status"));

        String buyerUuid = rs.getString("buyer_uuid");
        if (buyerUuid != null) {
            listing.setBuyerUuid(UUID.fromString(buyerUuid));
        }

        listing.setListedAt(rs.getTimestamp("listed_at"));

        long expiresAt = rs.getLong("expires_at");
        if (!rs.wasNull()) {
            listing.setExpiresAtMillis(expiresAt);
        }

        listing.setSoldAt(rs.getTimestamp("sold_at"));

        return listing;
    }
}
