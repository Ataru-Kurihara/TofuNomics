package org.tofu.tofunomics.quests;

import org.bukkit.Material;

import java.sql.Timestamp;
import java.util.List;

/**
 * 職業別クエストデータモデル
 */
public class JobQuest {
    
    private int questId;
    private String jobName;
    private String questName;
    private String description;
    private QuestType questType;
    private Material targetMaterial;
    private int targetAmount;
    private double experienceReward;
    private double incomeReward;
    private List<String> itemRewards;
    private int requiredLevel;
    private int cooldownHours;
    private boolean isDaily;
    private boolean isActive;
    private Timestamp createdAt;
    
    public enum QuestType {
        COLLECT("収集", "指定されたアイテムを集める"),
        CRAFT("製作", "指定されたアイテムを製作する"),
        MINE("採掘", "指定されたブロックを採掘する"),
        FISH("釣り", "指定された魚を釣る"),
        BUILD("建築", "指定されたブロックを設置する"),
        KILL("討伐", "指定されたモンスターを倒す"),
        DELIVER("配達", "指定された場所にアイテムを配達する"),
        EXPLORE("探索", "指定された場所を探索する");
        
        private final String displayName;
        private final String description;
        
        QuestType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public JobQuest() {
        this.isActive = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    // Getters and Setters
    public int getQuestId() {
        return questId;
    }
    
    public void setQuestId(int questId) {
        this.questId = questId;
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
    
    public String getQuestName() {
        return questName;
    }
    
    public void setQuestName(String questName) {
        this.questName = questName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public QuestType getQuestType() {
        return questType;
    }
    
    public void setQuestType(QuestType questType) {
        this.questType = questType;
    }
    
    public Material getTargetMaterial() {
        return targetMaterial;
    }
    
    public void setTargetMaterial(Material targetMaterial) {
        this.targetMaterial = targetMaterial;
    }
    
    public int getTargetAmount() {
        return targetAmount;
    }
    
    public void setTargetAmount(int targetAmount) {
        this.targetAmount = targetAmount;
    }
    
    public double getExperienceReward() {
        return experienceReward;
    }
    
    public void setExperienceReward(double experienceReward) {
        this.experienceReward = experienceReward;
    }
    
    public double getIncomeReward() {
        return incomeReward;
    }
    
    public void setIncomeReward(double incomeReward) {
        this.incomeReward = incomeReward;
    }
    
    public List<String> getItemRewards() {
        return itemRewards;
    }
    
    public void setItemRewards(List<String> itemRewards) {
        this.itemRewards = itemRewards;
    }
    
    public int getRequiredLevel() {
        return requiredLevel;
    }
    
    public void setRequiredLevel(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }
    
    public int getCooldownHours() {
        return cooldownHours;
    }
    
    public void setCooldownHours(int cooldownHours) {
        this.cooldownHours = cooldownHours;
    }
    
    public boolean isDaily() {
        return isDaily;
    }
    
    public void setDaily(boolean daily) {
        isDaily = daily;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * プレイヤーレベルがクエスト要求レベルに達しているかチェック
     */
    public boolean canPlayerAccept(int playerLevel) {
        return playerLevel >= requiredLevel;
    }
    
    /**
     * クエストの難易度を取得
     */
    public String getDifficultyString() {
        if (requiredLevel <= 5) {
            return "初級";
        } else if (requiredLevel <= 15) {
            return "中級";
        } else if (requiredLevel <= 30) {
            return "上級";
        } else {
            return "最上級";
        }
    }
}