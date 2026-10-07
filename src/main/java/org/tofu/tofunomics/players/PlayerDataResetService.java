package org.tofu.tofunomics.players;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 特定プレイヤーの TofuNomics データを DB から完全に消し、新規参加者の状態に戻す。
 *
 * 1 トランザクションで行い、途中で失敗したら何も消さない。
 * WorldGuard のメンバー解除やメモリ上のキャッシュ更新は呼び出し側（/tntest fullreset）が行う。
 *
 * 手持ちの保存データ（player_inventories）もここで消す。いま手に持っている物とエンダーチェストは呼び出し側が空にする。
 *
 * 消さないもの:
 * - transaction_log（お金の動きの記録。リセット後も経緯を追えるように残す）
 * - trade_chests.created_by / housing_properties.owner_uuid（管理者が設置した設備の記録）
 * - 他プレイヤーの出品・依頼に残る buyer_uuid / supplier_uuid / worker_uuid（相手側の記録）
 */
public class PlayerDataResetService {

    /** 削除対象（テーブル名, UUID 列名）。外部キーは有効化していないため子テーブルも明示的に消す。 */
    private static final String[][] DELETE_TARGETS = {
        {"player_jobs", "uuid"},
        {"job_history", "uuid"},
        {"job_changes", "uuid"},
        {"player_skills", "uuid"},
        {"player_first_acquisitions", "uuid"},
        {"player_trade_history", "uuid"},
        {"quest_progress", "player_uuid"},
        {"market_listings", "seller_uuid"},
        {"market_buy_orders", "requester_uuid"},
        {"market_service_requests", "requester_uuid"},
        {"land_ownership", "owner_uuid"},
        {"housing_rental_history", "tenant_uuid"},
        {"housing_rentals", "tenant_uuid"},
        {"player_inventories", "player_uuid"},
    };

    private final Connection connection;

    public PlayerDataResetService(Connection connection) {
        this.connection = connection;
    }

    /**
     * @param startingBankBalance リセット後の預金（初期残高）
     * @return テーブル名 → 影響した行数（表示・ログ用。処理順）
     */
    public Map<String, Integer> resetPlayerData(UUID uuid, double startingBankBalance) throws SQLException {
        String uuidString = uuid.toString();
        Map<String, Integer> affected = new LinkedHashMap<>();

        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                // 契約行を消す前に、契約中だった物件を空きに戻す（消した後では物件を特定できない）
                affected.put("housing_properties", update(
                    "UPDATE housing_properties SET is_available = 1 WHERE id IN (" +
                    "SELECT property_id FROM housing_rentals WHERE tenant_uuid = ? AND status = 'active')",
                    uuidString));

                for (String[] target : DELETE_TARGETS) {
                    affected.put(target[0], update(
                        "DELETE FROM " + target[0] + " WHERE " + target[1] + " = ?", uuidString));
                }

                // 畑区画は区画自体を残し、持ち主だけ外す
                affected.put("farm_plots", update(
                    "UPDATE farm_plots SET owner_uuid = NULL, updated_at = CURRENT_TIMESTAMP WHERE owner_uuid = ?",
                    uuidString));

                // players 行は残す（name / last_login を保ち、ログイン時の新規登録処理と競合させない）
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET balance = 0, bank_balance = ?, rules_agreed = 0, " +
                        "rules_agreed_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?")) {
                    statement.setDouble(1, startingBankBalance);
                    statement.setString(2, uuidString);
                    affected.put("players", statement.executeUpdate());
                }

                connection.commit();
                return affected;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int update(String sql, String uuidString) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuidString);
            return statement.executeUpdate();
        }
    }
}
