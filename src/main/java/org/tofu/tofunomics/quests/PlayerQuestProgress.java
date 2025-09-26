package org.tofu.tofunomics.quests;

import java.sql.Timestamp;

/**
 * プレイヤークエスト進行状況データモデル
 */
public class PlayerQuestProgress {
    
    private String uuid;
    private int questId;
    private QuestStatus status;
    private int currentProgress;
    private Timestamp acceptedAt;
    private Timestamp completedAt;
    private Timestamp lastUpdated;
    
    public enum QuestStatus {
        ACCEPTED("受諾中"),
        IN_PROGRESS("進行中"),
        COMPLETED("完了"),
        ABANDONED("放棄"),
        FAILED("失敗");
        
        private final String displayName;
        
        QuestStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public PlayerQuestProgress() {
        this.status = QuestStatus.ACCEPTED;
        this.currentProgress = 0;
        this.acceptedAt = new Timestamp(System.currentTimeMillis());
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public PlayerQuestProgress(String uuid, int questId) {
        this();
        this.uuid = uuid;
        this.questId = questId;
    }
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public int getQuestId() {
        return questId;
    }
    
    public void setQuestId(int questId) {
        this.questId = questId;
    }
    
    public QuestStatus getStatus() {
        return status;
    }
    
    public void setStatus(QuestStatus status) {
        this.status = status;
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public int getCurrentProgress() {
        return currentProgress;
    }
    
    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public Timestamp getAcceptedAt() {
        return acceptedAt;
    }
    
    public void setAcceptedAt(Timestamp acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
    
    public Timestamp getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }
    
    public Timestamp getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    // ユーティリティメソッド
    public void addProgress(int amount) {
        this.currentProgress += amount;
        this.status = QuestStatus.IN_PROGRESS;
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public void complete() {
        this.status = QuestStatus.COMPLETED;
        this.completedAt = new Timestamp(System.currentTimeMillis());
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public void abandon() {
        this.status = QuestStatus.ABANDONED;
        this.lastUpdated = new Timestamp(System.currentTimeMillis());
    }
    
    public boolean isCompleted() {
        return status == QuestStatus.COMPLETED;
    }
    
    public boolean isActive() {
        return status == QuestStatus.ACCEPTED || status == QuestStatus.IN_PROGRESS;
    }
    
    /**
     * クエスト進行度をパーセンテージで取得
     */
    public double getProgressPercentage(int targetAmount) {
        if (targetAmount <= 0) return 100.0;
        return Math.min(100.0, (double) currentProgress / targetAmount * 100.0);
    }
}