package org.tofu.tofunomics.tutorial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * チュートリアル進捗のデータベース操作を行うDAO
 */
public class TutorialProgressDAO {
    private final Connection connection;

    public TutorialProgressDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * プレイヤーのチュートリアル進捗を取得
     * @param uuid プレイヤーUUID
     * @return 現在のステップ、レコードがない場合はNOT_STARTED
     */
    public TutorialStep getProgress(UUID uuid) throws SQLException {
        String query = "SELECT current_step FROM tutorial_progress WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return TutorialStep.fromOrder(rs.getInt("current_step"));
            }
        }
        return TutorialStep.NOT_STARTED;
    }

    /**
     * チュートリアル進捗レコードを作成
     * @param uuid プレイヤーUUID
     * @param step 初期ステップ
     */
    public void createProgress(UUID uuid, TutorialStep step) throws SQLException {
        String query = "INSERT INTO tutorial_progress (uuid, current_step, started_at) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, step.getOrder());
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        }
    }

    /**
     * チュートリアル進捗を更新
     * @param uuid プレイヤーUUID
     * @param step 新しいステップ
     */
    public void updateProgress(UUID uuid, TutorialStep step) throws SQLException {
        String columnName = getStepCompletedColumn(step.getOrder() - 1);
        String query;

        if (columnName != null) {
            // 前のステップの完了時刻を記録しながら進捗を更新
            query = "UPDATE tutorial_progress SET current_step = ?, " + columnName + " = ? WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, step.getOrder());
                stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                stmt.setString(3, uuid.toString());
                stmt.executeUpdate();
            }
        } else {
            query = "UPDATE tutorial_progress SET current_step = ? WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, step.getOrder());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            }
        }
    }

    /**
     * チュートリアルを完了としてマーク
     * @param uuid プレイヤーUUID
     */
    public void markCompleted(UUID uuid) throws SQLException {
        String query = "UPDATE tutorial_progress SET current_step = ?, tutorial_completed = TRUE, completed_at = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, TutorialStep.COMPLETED.getOrder());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * チュートリアルをスキップとしてマーク
     * @param uuid プレイヤーUUID
     */
    public void markSkipped(UUID uuid) throws SQLException {
        String query = "UPDATE tutorial_progress SET current_step = ?, tutorial_completed = TRUE, tutorial_skipped = TRUE, completed_at = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, TutorialStep.COMPLETED.getOrder());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * チュートリアル進捗をリセット
     * @param uuid プレイヤーUUID
     */
    public void resetProgress(UUID uuid) throws SQLException {
        String query = "UPDATE tutorial_progress SET current_step = ?, " +
                "step1_completed_at = NULL, step2_completed_at = NULL, " +
                "step3_completed_at = NULL, step4_completed_at = NULL, " +
                "tutorial_completed = FALSE, tutorial_skipped = FALSE, " +
                "started_at = ?, completed_at = NULL WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, TutorialStep.JOB_SELECT.getOrder());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * プレイヤーのチュートリアル進捗レコードが存在するか確認
     * @param uuid プレイヤーUUID
     * @return レコードが存在する場合true
     */
    public boolean hasProgress(UUID uuid) throws SQLException {
        String query = "SELECT 1 FROM tutorial_progress WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * チュートリアルが完了しているか確認
     * @param uuid プレイヤーUUID
     * @return 完了している場合true
     */
    public boolean isCompleted(UUID uuid) throws SQLException {
        String query = "SELECT tutorial_completed FROM tutorial_progress WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("tutorial_completed");
            }
        }
        return false;
    }

    /**
     * チュートリアルがスキップされたか確認
     * @param uuid プレイヤーUUID
     * @return スキップされた場合true
     */
    public boolean isSkipped(UUID uuid) throws SQLException {
        String query = "SELECT tutorial_skipped FROM tutorial_progress WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("tutorial_skipped");
            }
        }
        return false;
    }

    /**
     * ステップ番号に対応する完了時刻カラム名を取得
     */
    private String getStepCompletedColumn(int stepOrder) {
        return switch (stepOrder) {
            case 1 -> "step1_completed_at";
            case 2 -> "step2_completed_at";
            case 3 -> "step3_completed_at";
            case 4 -> "step4_completed_at";
            default -> null;
        };
    }
}
