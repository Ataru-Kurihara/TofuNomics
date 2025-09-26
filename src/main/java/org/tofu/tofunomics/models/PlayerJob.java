package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

public class PlayerJob {
    private UUID uuid;
    private int jobId;
    private int level;
    private double experience;
    private Timestamp joinedAt;
    private Timestamp updatedAt;

    public PlayerJob() {}

    public PlayerJob(UUID uuid, int jobId) {
        this.uuid = uuid;
        this.jobId = jobId;
        this.level = 1;
        this.experience = 0.0;
        this.joinedAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
    
    public void setUuid(String uuidString) {
        this.uuid = UUID.fromString(uuidString);
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public double getExperience() {
        return experience;
    }

    public void setExperience(double experience) {
        this.experience = experience;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void addExperience(double amount) {
        this.experience += amount;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public double getExperienceForNextLevel() {
        return calculateExperienceRequired(level + 1) - experience;
    }

    public boolean canLevelUp(int maxLevel) {
        return level < maxLevel && experience >= calculateExperienceRequired(level + 1);
    }

    public void levelUp() {
        if (experience >= calculateExperienceRequired(level + 1)) {
            level++;
            this.updatedAt = new Timestamp(System.currentTimeMillis());
        }
    }

    public static double calculateExperienceRequired(int level) {
        if (level <= 1) return 0;
        return Math.pow(level - 1, 2.2) * 100;
    }

    public Timestamp getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * この職業がアクティブかどうかを返す
     * レベル1以上で有効とする
     */
    public boolean isActive() {
        return this.level >= 1;
    }
}