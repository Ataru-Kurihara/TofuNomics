package org.tofu.tofunomics.models;

import org.tofu.tofunomics.skills.SkillType;

import java.sql.Timestamp;
import java.util.UUID;

public class PlayerSkill {
    private UUID uuid;
    private int skillId;
    private SkillType skillType;
    private int level;
    private double experience;
    private boolean unlocked;
    private Timestamp unlockedAt;
    private Timestamp lastUsed;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PlayerSkill() {
        this.level = 0;
        this.experience = 0.0;
        this.unlocked = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public PlayerSkill(UUID uuid, int skillId) {
        this();
        this.uuid = uuid;
        this.skillId = skillId;
        this.unlockedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public PlayerSkill(String uuidString, SkillType skillType) {
        this();
        this.uuid = UUID.fromString(uuidString);
        this.skillType = skillType;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int skillId) {
        this.skillId = skillId;
    }
    
    public SkillType getSkillType() {
        return skillType;
    }
    
    public void setSkillType(SkillType skillType) {
        this.skillType = skillType;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public double getExperience() {
        return experience;
    }
    
    public void setExperience(double experience) {
        this.experience = experience;
    }
    
    public boolean isUnlocked() {
        return unlocked;
    }
    
    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public Timestamp getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Timestamp unlockedAt) {
        this.unlockedAt = unlockedAt;
    }

    public Timestamp getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Timestamp lastUsed) {
        this.lastUsed = lastUsed;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ユーティリティメソッド
    public void addExperience(double exp) {
        this.experience += exp;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void levelUp() {
        this.level++;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void unlock() {
        this.unlocked = true;
        this.unlockedAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void markAsUsed() {
        this.lastUsed = new Timestamp(System.currentTimeMillis());
    }

    public boolean isOnCooldown(int cooldownSeconds) {
        if (lastUsed == null) return false;
        long currentTime = System.currentTimeMillis();
        long cooldownTime = lastUsed.getTime() + (cooldownSeconds * 1000L);
        return currentTime < cooldownTime;
    }

    public long getRemainingCooldown(int cooldownSeconds) {
        if (lastUsed == null) return 0;
        long currentTime = System.currentTimeMillis();
        long cooldownTime = lastUsed.getTime() + (cooldownSeconds * 1000L);
        return Math.max(0, (cooldownTime - currentTime) / 1000);
    }
    
    /**
     * レベルアップに必要な経験値を計算
     */
    public double getRequiredExperienceForNextLevel() {
        return Math.pow(level + 1, 2.0) * 50.0;
    }
    
    /**
     * レベルアップ可能かチェック
     */
    public boolean canLevelUp() {
        return experience >= getRequiredExperienceForNextLevel();
    }
    
    /**
     * スキル効果の強度を計算（レベルベース）
     * @return 0.0〜1.0の効果強度
     */
    public double getEffectivenessPower() {
        if (!unlocked || level == 0) {
            return 0.0;
        }
        
        // レベル50で最大効果（100%）になる計算
        double maxLevel = 50.0;
        return Math.min(level / maxLevel, 1.0);
    }
    
    /**
     * スキルのパーセンテージ効果を取得
     * @param basePercentage 基本効果パーセンテージ
     * @return 実際の効果パーセンテージ
     */
    public double getSkillEffectPercentage(double basePercentage) {
        return basePercentage * getEffectivenessPower();
    }
}