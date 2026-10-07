package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.economy.TransactionRecorder;
import org.tofu.tofunomics.models.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerDAO {
    private static final Logger logger = Logger.getLogger(PlayerDAO.class.getName());

    private final Connection connection;

    public PlayerDAO(Connection connection) {
        this.connection = connection;
    }

    public void createPlayer(Player player) throws SQLException {
        String query = "INSERT INTO players (uuid, balance, bank_balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, player.getUuid().toString());
            statement.setDouble(2, player.getBalance());
            statement.setDouble(3, player.getBankBalance());
            statement.setTimestamp(4, player.getCreatedAt());
            statement.setTimestamp(5, player.getUpdatedAt());
            statement.executeUpdate();
        }
        // 新規作成時に預金が入っていれば、0 からの増加として記録する
        recordBankChange(player, 0.0);
    }

    /**
     * 保存前の預金残高を読む。お金の記録（増減の算出）に使う。
     * 記録係が登録されていないとき（テストなど）と、行が無いときは null。
     */
    private Double readBankBalanceForLog(UUID uuid) {
        if (TransactionRecorder.global() == null || uuid == null) {
            return null;
        }
        String query = "SELECT bank_balance FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble(1);
                }
            }
        } catch (SQLException e) {
            // 記録のための読み取りに失敗しても、保存そのものは続ける
        }
        return null;
    }

    /**
     * 保存の前後で預金残高が変わっていれば、その差をお金の記録に残す。
     * 残高を書き換える所はこの DAO の保存処理を必ず通るので、ここで拾えば漏れない。
     * 理由は呼び出し側の宣言（TransactionContext）から読む。宣言が無ければ「その他」。
     */
    private void recordBankChange(Player player, Double balanceBefore) {
        if (balanceBefore == null || player == null || player.getUuid() == null) {
            return;
        }
        try {
            TransactionRecorder.recordBankChange(
                player.getUuid(), bankDelta(balanceBefore, player.getBankBalance()), player.getBankBalance());
        } catch (RuntimeException e) {
            // 記録の失敗でお金の操作を止めない
        }
    }

    /** 預金残高の増減（増えたら正、減ったら負） */
    static double bankDelta(double before, double after) {
        return after - before;
    }

    public Player getPlayer(UUID uuid) throws SQLException {
        String query = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                Player player = new Player();
                player.setUuid(UUID.fromString(resultSet.getString("uuid")));
                player.setBalance(resultSet.getDouble("balance"));
                // bank_balanceカラムの存在チェック（マイグレーション対応）
                try {
                    player.setBankBalance(resultSet.getDouble("bank_balance"));
                } catch (SQLException e) {
                    player.setBankBalance(0.0);
                }
                try {
                    player.setCreatedAt(resultSet.getTimestamp("created_at"));
                } catch (SQLException e) {
                    player.setCreatedAt(null);
                }
                try {
                    player.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                } catch (SQLException e) {
                    player.setUpdatedAt(null);
                }
                return player;
            }
        }
        return null;
    }

    public Player getOrCreatePlayer(UUID uuid) throws SQLException {
        Player player = getPlayer(uuid);
        if (player == null) {
            player = new Player(uuid, 0.0);
            createPlayer(player);
        }
        return player;
    }

    public void updatePlayer(Player player) throws SQLException {
        Double balanceBefore = readBankBalanceForLog(player.getUuid());
        String query = "UPDATE players SET balance = ?, bank_balance = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setDouble(1, player.getBalance());
            statement.setDouble(2, player.getBankBalance());
            statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            statement.setString(4, player.getUuid().toString());
            statement.executeUpdate();
        }
        recordBankChange(player, balanceBefore);
    }

    public void updateBalance(UUID uuid, double newBalance) throws SQLException {
        String query = "UPDATE players SET balance = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setDouble(1, newBalance);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateBankBalance(UUID uuid, double newBankBalance) throws SQLException {
        Double balanceBefore = readBankBalanceForLog(uuid);
        String query = "UPDATE players SET bank_balance = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setDouble(1, newBankBalance);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
        if (balanceBefore != null) {
            try {
                TransactionRecorder.recordBankChange(uuid, bankDelta(balanceBefore, newBankBalance), newBankBalance);
            } catch (RuntimeException e) {
                // 記録の失敗でお金の操作を止めない
            }
        }
    }

    public void deletePlayer(UUID uuid) throws SQLException {
        String query = "DELETE FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public List<Player> getTopPlayers(int limit) throws SQLException {
        // 銀行残高（bank_balance）の多い順に並べる。
        // balance 列は今は使われておらず、昔の値が残っている人がいる。足して並べると、
        // その人が残高ランキングの上位の枠を取ってしまう（表示も銀行残高なので順位と食い違う）。
        String query = "SELECT * FROM players ORDER BY bank_balance DESC LIMIT ?";
        List<Player> players = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, limit);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                Player player = new Player();
                player.setUuid(UUID.fromString(resultSet.getString("uuid")));
                player.setBalance(resultSet.getDouble("balance"));
                // bank_balanceカラムの存在チェック（マイグレーション対応）
                try {
                    player.setBankBalance(resultSet.getDouble("bank_balance"));
                } catch (SQLException e) {
                    player.setBankBalance(0.0);
                }
                try {
                    player.setCreatedAt(resultSet.getTimestamp("created_at"));
                } catch (SQLException e) {
                    player.setCreatedAt(null);
                }
                try {
                    player.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                } catch (SQLException e) {
                    player.setUpdatedAt(null);
                }
                players.add(player);
            }
        }
        return players;
    }

    public boolean transferBalance(UUID fromUuid, UUID toUuid, double amount) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Player fromPlayer = getPlayer(fromUuid);
            Player toPlayer = getOrCreatePlayer(toUuid);

            if (fromPlayer == null || fromPlayer.getBankBalance() < amount) {
                connection.rollback();
                return false;
            }

            fromPlayer.removeBankBalance(amount);
            toPlayer.addBankBalance(amount);

            // 送る側・受け取る側それぞれに、相手を添えて記録する
            try (org.tofu.tofunomics.economy.TransactionContext.Scope scope =
                     org.tofu.tofunomics.economy.TransactionContext.open(
                         org.tofu.tofunomics.economy.TransactionType.PAY_SEND, toUuid.toString(), null)) {
                updatePlayer(fromPlayer);
            }
            try (org.tofu.tofunomics.economy.TransactionContext.Scope scope =
                     org.tofu.tofunomics.economy.TransactionContext.open(
                         org.tofu.tofunomics.economy.TransactionType.PAY_RECEIVE, fromUuid.toString(), null)) {
                updatePlayer(toPlayer);
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public int getTotalPlayerCount() throws SQLException {
        String query = "SELECT COUNT(*) FROM players";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }
    
    // StringのUUIDを受け取るメソッド（互換性のため）
    public Player getPlayerByUUID(String uuidString) {
        try {
            return getPlayer(UUID.fromString(uuidString));
        } catch (SQLException | IllegalArgumentException e) {
            return null;
        }
    }
    
    // 戻り値がbooleanのinsertPlayerメソッド
    public boolean insertPlayer(Player player) {
        try {
            createPlayer(player);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    
    // 戻り値がbooleanのupdatePlayerメソッド
    public boolean updatePlayerData(Player player) {
        try {
            updatePlayer(player);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    
    
    // transferBalanceメソッドのStringバージョン
    public boolean transferBalance(String fromUuidString, String toUuidString, double amount) {
        try {
            UUID fromUuid = UUID.fromString(fromUuidString);
            UUID toUuid = UUID.fromString(toUuidString);
            return transferBalance(fromUuid, toUuid, amount);
        } catch (SQLException | IllegalArgumentException e) {
            return false;
        }
    }
    
    // getTopPlayersByBalanceメソッド（BalanceTopCommand用）
    public List<Player> getTopPlayersByBalance(int limit) {
        try {
            return getTopPlayers(limit);
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * プレイヤーの最終ログイン時間を更新
     */
    public void updateLastLogin(UUID uuid) throws SQLException {
        String query = "UPDATE players SET updated_at = ? WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }
    
    /**
     * プレイヤーの最終ログイン時間をチェック（復帰プレイヤー判定用）
     */
    public boolean isReturningPlayer(UUID uuid, int daysThreshold) throws SQLException {
        String query = "SELECT updated_at FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                Timestamp lastLogin = resultSet.getTimestamp("updated_at");
                if (lastLogin != null) {
                    long daysBetween = (System.currentTimeMillis() - lastLogin.getTime()) / (24 * 60 * 60 * 1000);
                    return daysBetween >= daysThreshold;
                }
            }
        }
        return false;
    }
    
    /**
     * プレイヤー名の取得と更新
     * プレイヤーのテーブルにnameカラムがあることを前提とする
     */
    public void updatePlayerName(UUID uuid, String name) throws SQLException {
        String query = "UPDATE players SET name = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }
    
    /**
     * プレイヤーがルールに同意しているか確認
     */
    public boolean hasAgreedToRules(UUID uuid) {
        String query = "SELECT rules_agreed FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                return result.getBoolean("rules_agreed");
            }
        } catch (SQLException e) {
            logger.severe("ルール同意状態の取得に失敗しました: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * プレイヤーのルール同意状態を設定
     */
    public void setRulesAgreed(UUID uuid, boolean agreed) {
        String query = "UPDATE players SET rules_agreed = ?, rules_agreed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBoolean(1, agreed);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.severe("ルール同意状態の更新に失敗しました: " + e.getMessage());
        }
    }
}