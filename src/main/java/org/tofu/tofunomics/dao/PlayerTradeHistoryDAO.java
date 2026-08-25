package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.PlayerTradeHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 売却履歴のデータアクセスオブジェクト
 *
 * 取引所NPCへの売却はプレイヤーの主収入源だが、これまで一切記録されておらず
 * （書き込みは取引チェスト経路のみ）、公開後に職業ごとの稼ぎを実データで
 * 比較できない状態だった。バランス調整の根拠を残すためのテーブル書き込みを担う。
 */
public class PlayerTradeHistoryDAO {

    /**
     * 取引所NPCでの売却を表す trade_chest_id。
     *
     * player_trade_history は元々「取引チェスト」向けのテーブルで
     * trade_chest_id が NOT NULL のため、チェストを介さないNPC売却は
     * この番兵値で表す（trade_chests に id=0 の行は存在しない）。
     */
    public static final int TRADING_NPC_CHEST_ID = 0;

    private final Connection connection;

    public PlayerTradeHistoryDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 売却履歴を1件記録する。
     *
     * @return 記録できた場合true
     */
    public boolean insert(PlayerTradeHistory history) throws SQLException {
        String sql = "INSERT INTO player_trade_history "
            + "(uuid, trade_chest_id, item_type, item_amount, sale_price, job_bonus, player_job, player_job_level) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, history.getUuid());
            statement.setInt(2, history.getTradeChestId());
            statement.setString(3, history.getItemType());
            statement.setInt(4, history.getItemAmount());
            statement.setDouble(5, history.getSalePrice());
            statement.setDouble(6, history.getJobBonus());
            statement.setString(7, history.getPlayerJob());
            statement.setInt(8, history.getPlayerJobLevel());

            return statement.executeUpdate() > 0;
        }
    }
}
