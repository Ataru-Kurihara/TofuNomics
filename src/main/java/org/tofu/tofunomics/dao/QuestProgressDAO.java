package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.QuestProgress;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * クエスト受注NPCの受注状態のデータアクセスオブジェクト
 *
 * 受注状態（どのクエストを受注中か）のみを永続化する。進捗は持たず、
 * 納品時にインベントリの実数で判定する。
 */
public class QuestProgressDAO {
    private final Connection connection;

    public QuestProgressDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * クエストを受注（accepted で新規 insert）し、生成された id を返す。
     * accepted_at は DB 側の DEFAULT CURRENT_TIMESTAMP に委ねる。
     * UNIQUE(player_uuid, quest_id) 制約に違反する場合は SQLException を送出する。
     */
    public int insertProgress(QuestProgress progress) throws SQLException {
        String query = "INSERT INTO quest_progress (player_uuid, quest_id, status) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, progress.getPlayerUuid().toString());
            statement.setString(2, progress.getQuestId());
            statement.setString(3, progress.getStatus() != null ? progress.getStatus() : QuestProgress.STATUS_ACCEPTED);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    progress.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * 指定プレイヤーの指定クエストの受注状態を取得（存在しなければ null）
     */
    public QuestProgress getByPlayerAndQuest(UUID playerUuid, String questId) throws SQLException {
        String query = "SELECT * FROM quest_progress WHERE player_uuid = ? AND quest_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, questId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * 指定プレイヤーの受注中（accepted）クエストID集合を取得（GUI 表示用）
     */
    public List<QuestProgress> getAcceptedByPlayer(UUID playerUuid) throws SQLException {
        String query = "SELECT * FROM quest_progress WHERE player_uuid = ? AND status = ?";
        List<QuestProgress> list = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, QuestProgress.STATUS_ACCEPTED);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * 指定プレイヤーが指定クエストを受注中（accepted）かどうか
     */
    public boolean isAccepted(UUID playerUuid, String questId) throws SQLException {
        String query = "SELECT COUNT(*) FROM quest_progress WHERE player_uuid = ? AND quest_id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, questId);
            statement.setString(3, QuestProgress.STATUS_ACCEPTED);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * 指定プレイヤーの受注中（accepted）クエスト数をカウント（同時受注上限チェック用）
     */
    public int countAcceptedByPlayer(UUID playerUuid) throws SQLException {
        String query = "SELECT COUNT(*) FROM quest_progress WHERE player_uuid = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, QuestProgress.STATUS_ACCEPTED);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * 受注を完了に更新（楽観ロック）。
     * status='accepted' の行のみを対象とし、影響行数が 1 のときだけ成功とする。
     * これにより、同一クエストへの同時納品（二重報酬）を防止する。
     *
     * @return 完了処理に成功した場合 true（影響行数 == 1）
     */
    public boolean markAsCompleted(UUID playerUuid, String questId, long completedAtMillis) throws SQLException {
        String query = "UPDATE quest_progress SET status = ?, completed_at = ? " +
                "WHERE player_uuid = ? AND quest_id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, QuestProgress.STATUS_COMPLETED);
            statement.setTimestamp(2, new Timestamp(completedAtMillis));
            statement.setString(3, playerUuid.toString());
            statement.setString(4, questId);
            statement.setString(5, QuestProgress.STATUS_ACCEPTED);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * 指定プレイヤーの指定クエストの受注行を削除（再受注を許可するために使用）
     */
    public boolean deleteByPlayerAndQuest(UUID playerUuid, String questId) throws SQLException {
        String query = "DELETE FROM quest_progress WHERE player_uuid = ? AND quest_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, questId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * ResultSet から QuestProgress にマッピング
     */
    private QuestProgress mapResultSet(ResultSet rs) throws SQLException {
        QuestProgress progress = new QuestProgress();
        progress.setId(rs.getInt("id"));
        progress.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        progress.setQuestId(rs.getString("quest_id"));
        progress.setStatus(rs.getString("status"));
        progress.setAcceptedAt(rs.getTimestamp("accepted_at"));
        progress.setCompletedAt(rs.getTimestamp("completed_at"));
        return progress;
    }
}
