package org.tofu.tofunomics.models;

import java.sql.Timestamp;

public class JobSkill {
    private int id;
    private int jobId;
    private String skillName;
    private int unlockLevel;
    private String description;
    private String effectType;
    private double effectValue;
    private int cooldown;
    private Timestamp createdAt;

    public JobSkill() {}

    public JobSkill(int jobId, String skillName, int unlockLevel, String description, 
                   String effectType, double effectValue, int cooldown) {
        this.jobId = jobId;
        this.skillName = skillName;
        this.unlockLevel = unlockLevel;
        this.description = description;
        this.effectType = effectType;
        this.effectValue = effectValue;
        this.cooldown = cooldown;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    public void setUnlockLevel(int unlockLevel) {
        this.unlockLevel = unlockLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEffectType() {
        return effectType;
    }

    public void setEffectType(String effectType) {
        this.effectType = effectType;
    }

    public double getEffectValue() {
        return effectValue;
    }

    public void setEffectValue(double effectValue) {
        this.effectValue = effectValue;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}