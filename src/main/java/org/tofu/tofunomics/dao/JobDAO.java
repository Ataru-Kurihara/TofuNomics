package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.TofuNomics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class JobDAO {
    private final Connection connection;

    public JobDAO(Connection connection) {
        this.connection = connection;
    }

    public void createJob(Job job) throws SQLException {
        String query = "INSERT INTO jobs (name, display_name, max_level, base_income, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, job.getName());
            statement.setString(2, job.getDisplayName());
            statement.setInt(3, job.getMaxLevel());
            statement.setDouble(4, job.getBaseIncome());
            statement.setTimestamp(5, job.getCreatedAt());
            statement.executeUpdate();
        }
    }

    public Job getJobById(int id) throws SQLException {
        String query = "SELECT * FROM jobs WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return mapResultSetToJob(resultSet);
            }
        }
        return null;
    }

    public Job getJobByName(String name) throws SQLException {
        String query = "SELECT * FROM jobs WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return mapResultSetToJob(resultSet);
            }
        }
        return null;
    }

    public List<Job> getAllJobs() throws SQLException {
        String query = "SELECT * FROM jobs ORDER BY id";
        List<Job> jobs = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                jobs.add(mapResultSetToJob(resultSet));
            }
        }
        return jobs;
    }

    public void updateJob(Job job) throws SQLException {
        String query = "UPDATE jobs SET name = ?, display_name = ?, max_level = ?, base_income = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, job.getName());
            statement.setString(2, job.getDisplayName());
            statement.setInt(3, job.getMaxLevel());
            statement.setDouble(4, job.getBaseIncome());
            statement.setInt(5, job.getId());
            statement.executeUpdate();
        }
    }

    public void deleteJob(int id) throws SQLException {
        String query = "DELETE FROM jobs WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public boolean jobExists(String name) throws SQLException {
        String query = "SELECT COUNT(*) FROM jobs WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        }
        return false;
    }

    public List<String> getJobNames() throws SQLException {
        String query = "SELECT name FROM jobs ORDER BY id";
        List<String> jobNames = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                jobNames.add(resultSet.getString("name"));
            }
        }
        return jobNames;
    }

    public List<String> getJobDisplayNames() throws SQLException {
        String query = "SELECT display_name FROM jobs ORDER BY id";
        List<String> displayNames = new ArrayList<>();
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                displayNames.add(resultSet.getString("display_name"));
            }
        }
        return displayNames;
    }

    private Job mapResultSetToJob(ResultSet resultSet) throws SQLException {
        Job job = new Job();
        job.setId(resultSet.getInt("id"));
        job.setName(resultSet.getString("name"));
        job.setDisplayName(resultSet.getString("display_name"));
        job.setMaxLevel(resultSet.getInt("max_level"));
        job.setBaseIncome(resultSet.getDouble("base_income"));
        
        // タイムスタンプの安全な取得
        try {
            // まず通常のgetTimestamp()を試す
            job.setCreatedAt(resultSet.getTimestamp("created_at"));
        } catch (SQLException e) {
            // エラーが発生した場合、文字列として取得してパース
            String dateStr = resultSet.getString("created_at");
            if (dateStr != null && !dateStr.isEmpty()) {
                try {
                    // 複数のフォーマットに対応
                    Timestamp timestamp = parseTimestamp(dateStr);
                    job.setCreatedAt(timestamp);
                } catch (Exception parseEx) {
                    // パースに失敗した場合は現在時刻を設定
                    TofuNomics plugin = TofuNomics.getInstance();
                    if (plugin != null) {
                        plugin.getLogger().warning("Failed to parse timestamp: " + dateStr + ", using current time");
                    }
                    job.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                }
            } else {
                // nullの場合は現在時刻を設定
                job.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            }
        }
        
        return job;
    }
    
    private Timestamp parseTimestamp(String dateStr) throws ParseException {
        // 複数の日付フォーマットを試す
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        };
        
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                Date date = sdf.parse(dateStr);
                return new Timestamp(date.getTime());
            } catch (ParseException e) {
                // 次のパターンを試す
            }
        }
        
        throw new ParseException("Unable to parse date: " + dateStr, 0);
    }
    
    // SQLExceptionをハンドリングするメソッド群
    public Job getJobByNameSafe(String name) {
        try {
            return getJobByName(name);
        } catch (SQLException e) {
            return null;
        }
    }
    
    public Job getJobByIdSafe(int id) {
        try {
            return getJobById(id);
        } catch (SQLException e) {
            return null;
        }
    }
    
    public List<Job> getAllJobsSafe() {
        try {
            // TofuNomicsインスタンスの安全な取得
            TofuNomics plugin = TofuNomics.getInstance();
            if (plugin != null) {
                Logger logger = plugin.getLogger();
                logger.info("=== JobDAO.getAllJobsSafe() Debug ===");
                logger.info("Executing getAllJobs() query...");
                
                List<Job> jobs = getAllJobs();
                logger.info("Query executed successfully. Jobs found: " + jobs.size());
                
                for (Job job : jobs) {
                    logger.info("Job: " + job.getName() + " (" + job.getDisplayName() + ") ID: " + job.getId());
                }
                
                return jobs;
            } else {
                // テスト環境などでプラグインインスタンスが存在しない場合
                // 接続が閉じられている可能性があるため、空のリストを返す
                return new ArrayList<>();
            }
        } catch (SQLException e) {
            // エラーログの安全な出力
            TofuNomics plugin = TofuNomics.getInstance();
            if (plugin != null) {
                Logger logger = plugin.getLogger();
                logger.severe("SQLException in getAllJobsSafe(): " + e.getMessage());
                e.printStackTrace();
            } else {
                System.err.println("SQLException in getAllJobsSafe(): " + e.getMessage());
                e.printStackTrace();
            }
            return new ArrayList<>();
        }
    }
    
    public boolean jobExistsSafe(String name) {
        try {
            return jobExists(name);
        } catch (SQLException e) {
            return false;
        }
    }
    
    public List<String> getJobNamesSafe() {
        try {
            return getJobNames();
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }
}