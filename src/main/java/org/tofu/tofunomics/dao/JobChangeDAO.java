package org.tofu.tofunomics.dao;

import org.tofu.tofunomics.models.JobChange;

import java.sql.*;

public class JobChangeDAO {
    
    private final Connection connection;
    
    public JobChangeDAO(Connection connection) {
        this.connection = connection;
    }
    
    public JobChange getJobChangeByUUID(String uuid) {
        String query = "SELECT uuid, last_change_date, created_at, updated_at FROM job_changes WHERE uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    JobChange jobChange = new JobChange();
                    jobChange.setUuid(resultSet.getString("uuid"));
                    jobChange.setLastChangeDate(resultSet.getString("last_change_date"));
                    jobChange.setCreatedAt(resultSet.getTimestamp("created_at"));
                    jobChange.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                    return jobChange;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    public boolean insertJobChange(JobChange jobChange) {
        String query = "INSERT INTO job_changes (uuid, last_change_date, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, jobChange.getUuid());
            statement.setString(2, jobChange.getLastChangeDate());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateJobChange(JobChange jobChange) {
        String query = "UPDATE job_changes SET last_change_date = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, jobChange.getLastChangeDate());
            statement.setString(2, jobChange.getUuid());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean upsertJobChange(JobChange jobChange) {
        JobChange existing = getJobChangeByUUID(jobChange.getUuid());
        if (existing != null) {
            return updateJobChange(jobChange);
        } else {
            return insertJobChange(jobChange);
        }
    }
    
    public boolean canPlayerChangeJobToday(String uuid) {
        JobChange jobChange = getJobChangeByUUID(uuid);
        return jobChange == null || jobChange.canChangeJobToday();
    }
    
    public boolean recordJobChangeToday(String uuid) {
        JobChange jobChange = new JobChange(uuid, JobChange.getTodayDateString());
        return upsertJobChange(jobChange);
    }
    
    public boolean deleteJobChange(String uuid) {
        String query = "DELETE FROM job_changes WHERE uuid = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}