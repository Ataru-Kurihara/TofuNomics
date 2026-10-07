package org.tofu.tofunomics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * お金の記録（transaction_log 表）の読み書き。
 * 表は DatabaseManager が起動時に CREATE TABLE IF NOT EXISTS で作る。
 */
public class TransactionLogDAO {

    /** 預金（銀行残高）の増減 */
    public static final String ACCOUNT_BANK = "bank";
    /** 手持ちの現金（TofuCoin / TofuGold）の増減 */
    public static final String ACCOUNT_CASH = "cash";

    /** 表と索引を作る SQL（DatabaseManager とテストで同じ定義を使う） */
    public static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS transaction_log (" +
        "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "    created_at INTEGER NOT NULL," +
        "    player_uuid TEXT NOT NULL," +
        "    player_name TEXT," +
        "    type TEXT NOT NULL," +
        "    account TEXT NOT NULL," +
        "    amount REAL NOT NULL," +
        "    balance_after REAL," +
        "    counterparty TEXT," +
        "    detail TEXT" +
        ");";
    public static final String CREATE_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS idx_transaction_log_player ON transaction_log (player_uuid, created_at);";

    private final Connection connection;

    public TransactionLogDAO(Connection connection) {
        this.connection = connection;
    }

    /** 記録 1 件 */
    public static class Entry {
        private final long createdAt;
        private final UUID playerUuid;
        private final String playerName;
        private final String type;
        private final String account;
        private final double amount;
        private final Double balanceAfter;
        private final String counterparty;
        private final String detail;

        public Entry(long createdAt, UUID playerUuid, String playerName, String type, String account,
                     double amount, Double balanceAfter, String counterparty, String detail) {
            this.createdAt = createdAt;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.type = type;
            this.account = account;
            this.amount = amount;
            this.balanceAfter = balanceAfter;
            this.counterparty = counterparty;
            this.detail = detail;
        }

        public long getCreatedAt() { return createdAt; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public String getType() { return type; }
        public String getAccount() { return account; }
        public double getAmount() { return amount; }
        /** 操作後の預金残高。手持ちだけの操作では null */
        public Double getBalanceAfter() { return balanceAfter; }
        public String getCounterparty() { return counterparty; }
        public String getDetail() { return detail; }
    }

    public void insert(Entry entry) throws SQLException {
        String sql = "INSERT INTO transaction_log " +
            "(created_at, player_uuid, player_name, type, account, amount, balance_after, counterparty, detail) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, entry.getCreatedAt());
                statement.setString(2, entry.getPlayerUuid().toString());
                statement.setString(3, entry.getPlayerName());
                statement.setString(4, entry.getType());
                statement.setString(5, entry.getAccount());
                statement.setDouble(6, entry.getAmount());
                if (entry.getBalanceAfter() == null) {
                    statement.setNull(7, Types.REAL);
                } else {
                    statement.setDouble(7, entry.getBalanceAfter());
                }
                statement.setString(8, entry.getCounterparty());
                statement.setString(9, entry.getDetail());
                statement.executeUpdate();
            }
        }
    }

    /**
     * 指定したプレイヤーの記録を新しい順に返す。
     */
    public List<Entry> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT * FROM transaction_log WHERE player_uuid = ? ORDER BY created_at DESC, id DESC LIMIT ?";
        List<Entry> entries = new ArrayList<>();
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        double balanceAfter = rs.getDouble("balance_after");
                        Double balanceAfterOrNull = rs.wasNull() ? null : balanceAfter;
                        entries.add(new Entry(
                            rs.getLong("created_at"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getString("type"),
                            rs.getString("account"),
                            rs.getDouble("amount"),
                            balanceAfterOrNull,
                            rs.getString("counterparty"),
                            rs.getString("detail")));
                    }
                }
            }
        }
        return entries;
    }

    /**
     * 記録に残っている名前から UUID を引く（オフラインの相手を名前で指定するため）。
     * 同じ名前の記録が複数あれば最も新しいものを返す。見つからなければ null。
     */
    public UUID findUuidByName(String playerName) throws SQLException {
        String sql = "SELECT player_uuid FROM transaction_log WHERE LOWER(player_name) = LOWER(?) " +
            "ORDER BY created_at DESC, id DESC LIMIT 1";
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerName);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("player_uuid"));
                    }
                }
            }
        }
        return null;
    }
}
