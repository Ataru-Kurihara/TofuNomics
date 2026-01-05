package org.tofu.tofunomics.models;

import java.sql.Timestamp;

/**
 * 職業履歴モデルクラス
 * プレイヤーが退職した職業の情報を記録
 */
public class JobHistory {
    
    private int id;
    private String uuid;
    private int jobId;
    private int maxLevel;
    private Timestamp leftAt;
    
    public JobHistory() {}
    
    public JobHistory(String uuid, int jobId, int maxLevel) {
        this.uuid = uuid;
        this.jobId = jobId;
        this.maxLevel = maxLevel;
        this.leftAt = new Timestamp(System.currentTimeMillis());
    }
    
    // Getters
    public int getId() {
        return id;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public int getJobId() {
        return jobId;
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public Timestamp getLeftAt() {
        return leftAt;
    }
    
    // Setters
    public void setId(int id) {
        this.id = id;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public void setJobId(int jobId) {
        this.jobId = jobId;
    }
    
    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }
    
    public void setLeftAt(Timestamp leftAt) {
        this.leftAt = leftAt;
    }
    
    @Override
    public String toString() {
        return "JobHistory{" +
                "id=" + id +
                ", uuid='" + uuid + '\'' +
                ", jobId=" + jobId +
                ", maxLevel=" + maxLevel +
                ", leftAt=" + leftAt +
                '}';
    }
}
