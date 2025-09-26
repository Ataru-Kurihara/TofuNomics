package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.PlayerJob;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerJobDAO {
    private final Connection connection;

    public PlayerJobDAO(Connection connection) {
        this.connection = connection;
    }

    public void createPlayerJob(PlayerJob playerJob) throws SQLException {
        String query = "INSERT INTO player_jobs (uuid, job_id, level, experience, joined_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerJob.getUuid().toString());
            statement.setInt(2, playerJob.getJobId());
            statement.setInt(3, playerJob.getLevel());
            statement.setDouble(4, playerJob.getExperience());
            statement.setTimestamp(5, playerJob.getJoinedAt());
            statement.setTimestamp(6, playerJob.getUpdatedAt());
            statement.executeUpdate();
        }
    }

    public PlayerJob getPlayerJob(UUID uuid, int jobId) throws SQLException {
        String query = "SELECT * FROM player_jobs WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, jobId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return mapResultSetToPlayerJob(resultSet);
            }
        }
        return null;
    }

    public PlayerJob getCurrentPlayerJob(UUID uuid) throws SQLException {
        String query = "SELECT * FROM player_jobs WHERE uuid = ? ORDER BY updated_at DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return mapResultSetToPlayerJob(resultSet);
            }
        }
        return null;
    }

    public List<PlayerJob> getPlayerJobs(UUID uuid) throws SQLException {
        String query = "SELECT * FROM player_jobs WHERE uuid = ? ORDER BY joined_at";
        List<PlayerJob> playerJobs = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                playerJobs.add(mapResultSetToPlayerJob(resultSet));
            }
        }
        return playerJobs;
    }

    public void updatePlayerJob(PlayerJob playerJob) throws SQLException {
        String query = "UPDATE player_jobs SET level = ?, experience = ?, updated_at = ? WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, playerJob.getLevel());
            statement.setDouble(2, playerJob.getExperience());
            statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            statement.setString(4, playerJob.getUuid().toString());
            statement.setInt(5, playerJob.getJobId());
            statement.executeUpdate();
        }
    }

    public void addExperience(UUID uuid, int jobId, double experience) throws SQLException {
        String query = "UPDATE player_jobs SET experience = experience + ?, updated_at = ? WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setDouble(1, experience);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setString(3, uuid.toString());
            statement.setInt(4, jobId);
            int rowsAffected = statement.executeUpdate();
            
            if (rowsAffected == 0) {
                PlayerJob newPlayerJob = new PlayerJob(uuid, jobId);
                newPlayerJob.addExperience(experience);
                createPlayerJob(newPlayerJob);
            }
        }
    }

    public void levelUp(UUID uuid, int jobId, int newLevel) throws SQLException {
        String query = "UPDATE player_jobs SET level = ?, updated_at = ? WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, newLevel);
            statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement.setString(3, uuid.toString());
            statement.setInt(4, jobId);
            statement.executeUpdate();
        }
    }

    public void deletePlayerJob(UUID uuid, int jobId) throws SQLException {
        String query = "DELETE FROM player_jobs WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, jobId);
            statement.executeUpdate();
        }
    }

    public void deleteAllPlayerJobs(UUID uuid) throws SQLException {
        String query = "DELETE FROM player_jobs WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public boolean hasPlayerJob(UUID uuid, int jobId) throws SQLException {
        String query = "SELECT COUNT(*) FROM player_jobs WHERE uuid = ? AND job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, jobId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        }
        return false;
    }

    public List<PlayerJob> getTopPlayersByJobLevel(int jobId, int limit) throws SQLException {
        String query = "SELECT * FROM player_jobs WHERE job_id = ? ORDER BY level DESC, experience DESC LIMIT ?";
        List<PlayerJob> topPlayers = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, jobId);
            statement.setInt(2, limit);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                topPlayers.add(mapResultSetToPlayerJob(resultSet));
            }
        }
        return topPlayers;
    }

    private PlayerJob mapResultSetToPlayerJob(ResultSet resultSet) throws SQLException {
        PlayerJob playerJob = new PlayerJob();
        playerJob.setUuid(UUID.fromString(resultSet.getString("uuid")));
        playerJob.setJobId(resultSet.getInt("job_id"));
        playerJob.setLevel(resultSet.getInt("level"));
        playerJob.setExperience(resultSet.getDouble("experience"));
        playerJob.setJoinedAt(resultSet.getTimestamp("joined_at"));
        playerJob.setUpdatedAt(resultSet.getTimestamp("updated_at"));
        return playerJob;
    }
    
    // StringのUUIDを受け取るメソッド（互換性のため）
    public List<PlayerJob> getPlayerJobsByUUID(String uuidString) {
        try {
            return getPlayerJobs(UUID.fromString(uuidString));
        } catch (SQLException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }
    
    // 戻り値がbooleanのinsertPlayerJobメソッド
    public boolean insertPlayerJob(PlayerJob playerJob) {
        try {
            createPlayerJob(playerJob);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    
    // 戻り値がbooleanのupdatePlayerJobメソッド
    public boolean updatePlayerJobData(PlayerJob playerJob) {
        try {
            String query = "UPDATE player_jobs SET level = ?, experience = ?, updated_at = ? WHERE uuid = ? AND job_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, playerJob.getLevel());
                statement.setDouble(2, playerJob.getExperience());
                statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                statement.setString(4, playerJob.getUuid().toString());
                statement.setInt(5, playerJob.getJobId());
                statement.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }
    
    // StringのUUIDとjobIdを受け取るdeletePlayerJobメソッド
    public boolean deletePlayerJob(String uuidString, int jobId) {
        try {
            deletePlayerJob(UUID.fromString(uuidString), jobId);
            return true;
        } catch (SQLException | IllegalArgumentException e) {
            return false;
        }
    }
    
    // StringのUUIDを受け取るgetPlayerJobメソッド
    public PlayerJob getPlayerJob(String uuidString, int jobId) {
        try {
            return getPlayerJob(UUID.fromString(uuidString), jobId);
        } catch (SQLException | IllegalArgumentException e) {
            return null;
        }
    }
}