package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.JobHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 職業履歴データアクセスクラス
 * プレイヤーが退職した職業の情報を管理
 */
public class JobHistoryDAO {
    
    private final Connection connection;
    
    public JobHistoryDAO(Connection connection) {
        this.connection = connection;
    }
    
    /**
     * 職業履歴を新規登録
     * @param jobHistory 登録する職業履歴
     * @return 成功時true
     */
    public boolean insertJobHistory(JobHistory jobHistory) {
        String query = "INSERT INTO job_history (uuid, job_id, max_level, left_at) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, jobHistory.getUuid());
            statement.setInt(2, jobHistory.getJobId());
            statement.setInt(3, jobHistory.getMaxLevel());
            statement.setTimestamp(4, jobHistory.getLeftAt());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * プレイヤーの職業履歴を全て取得
     * @param uuid プレイヤーUUID
     * @return 職業履歴のリスト
     */
    public List<JobHistory> getJobHistoriesByUUID(String uuid) {
        String query = "SELECT id, uuid, job_id, max_level, left_at FROM job_history WHERE uuid = ? ORDER BY left_at DESC";
        List<JobHistory> histories = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    JobHistory history = new JobHistory();
                    history.setId(resultSet.getInt("id"));
                    history.setUuid(resultSet.getString("uuid"));
                    history.setJobId(resultSet.getInt("job_id"));
                    history.setMaxLevel(resultSet.getInt("max_level"));
                    history.setLeftAt(resultSet.getTimestamp("left_at"));
                    histories.add(history);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return histories;
    }
    
    /**
     * プレイヤーが過去に達成した最高レベルを取得
     * @param uuid プレイヤーUUID
     * @return 最高レベル（履歴がない場合は0）
     */
    public int getMaxLevelAchieved(String uuid) {
        String query = "SELECT MAX(max_level) as highest_level FROM job_history WHERE uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("highest_level");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return 0;
    }
    
    /**
     * プレイヤーが特定の職業で達成した最高レベルを取得
     * @param uuid プレイヤーUUID
     * @param jobId 職業ID
     * @return 最高レベル（履歴がない場合は0）
     */
    public int getMaxLevelForJob(String uuid, int jobId) {
        String query = "SELECT MAX(max_level) as highest_level FROM job_history WHERE uuid = ? AND job_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            statement.setInt(2, jobId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("highest_level");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return 0;
    }
    
    /**
     * プレイヤーの特定の職業の最新の履歴を取得（最高レベルの記録）
     * @param uuid プレイヤーUUID
     * @param jobId 職業ID
     * @return 最新の職業履歴（履歴がない場合はnull）
     */
    public JobHistory getLatestJobHistory(String uuid, int jobId) {
        String query = "SELECT id, uuid, job_id, max_level, left_at FROM job_history " +
                      "WHERE uuid = ? AND job_id = ? ORDER BY max_level DESC LIMIT 1";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            statement.setInt(2, jobId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    JobHistory history = new JobHistory();
                    history.setId(resultSet.getInt("id"));
                    history.setUuid(resultSet.getString("uuid"));
                    history.setJobId(resultSet.getInt("job_id"));
                    history.setMaxLevel(resultSet.getInt("max_level"));
                    history.setLeftAt(resultSet.getTimestamp("left_at"));
                    return history;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * プレイヤーが過去にレベル50以上に達したことがあるかをチェック
     * @param uuid プレイヤーUUID
     * @return レベル50以上の記録がある場合true
     */
    public boolean hasReachedLevel50(String uuid) {
        return getMaxLevelAchieved(uuid) >= 50;
    }
    
    /**
     * プレイヤーの職業履歴を全て削除（テスト用）
     * @param uuid プレイヤーUUID
     * @return 成功時true
     */
    public boolean deleteAllHistoriesByUUID(String uuid) {
        String query = "DELETE FROM job_history WHERE uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            return statement.executeUpdate() >= 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
