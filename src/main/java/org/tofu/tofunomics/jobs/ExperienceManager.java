package org.tofu.tofunomics.jobs;

import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.models.PlayerJob;

public class ExperienceManager {
    
    private final ConfigManager configManager;
    private final PlayerJobDAO playerJobDAO;
    
    public ExperienceManager(ConfigManager configManager, PlayerJobDAO playerJobDAO) {
        this.configManager = configManager;
        this.playerJobDAO = playerJobDAO;
    }
    
    public double calculateRequiredExperience(int level) {
        if (level <= 1) {
            return 0.0;
        }
        return Math.pow(level, 2.2) * 100;
    }
    
    public double calculateRequiredExperienceForNextLevel(int currentLevel) {
        return calculateRequiredExperience(currentLevel + 1);
    }
    
    public double getExperienceToNextLevel(PlayerJob playerJob) {
        if (playerJob == null) {
            return 0.0;
        }
        
        double requiredForNext = calculateRequiredExperienceForNextLevel(playerJob.getLevel());
        return Math.max(0, requiredForNext - playerJob.getExperience());
    }
    
    public int getMaxLevelForJob(String jobName) {
        return configManager.getJobMaxLevel(jobName);
    }
    
    public boolean isMaxLevel(PlayerJob playerJob, String jobName) {
        if (playerJob == null) {
            return false;
        }
        return playerJob.getLevel() >= getMaxLevelForJob(jobName);
    }
    
    public enum ExperienceAddResult {
        SUCCESS,
        LEVEL_UP,
        MAX_LEVEL_REACHED,
        INVALID_AMOUNT,
        DATABASE_ERROR
    }
    
    public ExperienceAddResult addExperience(PlayerJob playerJob, String jobName, double amount) {
        if (amount <= 0) {
            return ExperienceAddResult.INVALID_AMOUNT;
        }
        
        if (playerJob == null) {
            return ExperienceAddResult.DATABASE_ERROR;
        }
        
        if (isMaxLevel(playerJob, jobName)) {
            return ExperienceAddResult.MAX_LEVEL_REACHED;
        }
        
        double multiplier = configManager.getJobExpMultiplier(jobName);
        double adjustedAmount = amount * multiplier;
        
        double oldExperience = playerJob.getExperience();
        int oldLevel = playerJob.getLevel();
        
        playerJob.addExperience(adjustedAmount);
        
        int newLevel = calculateLevelFromExperience(playerJob.getExperience());
        int maxLevel = getMaxLevelForJob(jobName);
        
        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            playerJob.setExperience(calculateRequiredExperience(maxLevel));
        }
        
        playerJob.setLevel(newLevel);
        
        if (!playerJobDAO.updatePlayerJobData(playerJob)) {
            return ExperienceAddResult.DATABASE_ERROR;
        }
        
        if (newLevel > oldLevel) {
            return ExperienceAddResult.LEVEL_UP;
        } else {
            return ExperienceAddResult.SUCCESS;
        }
    }
    
    public int calculateLevelFromExperience(double experience) {
        if (experience <= 0) {
            return 1;
        }
        
        int level = 1;
        while (calculateRequiredExperience(level + 1) <= experience) {
            level++;
        }
        
        return level;
    }
    
    public double getExperienceProgress(PlayerJob playerJob) {
        if (playerJob == null) {
            return 0.0;
        }
        
        int currentLevel = playerJob.getLevel();
        double currentExp = playerJob.getExperience();
        double expForCurrentLevel = calculateRequiredExperience(currentLevel);
        double expForNextLevel = calculateRequiredExperience(currentLevel + 1);
        
        if (expForNextLevel <= expForCurrentLevel) {
            return 1.0;
        }
        
        double progress = (currentExp - expForCurrentLevel) / (expForNextLevel - expForCurrentLevel);
        return Math.max(0.0, Math.min(1.0, progress));
    }
    
    public String getExperienceProgressBar(PlayerJob playerJob, int barLength) {
        if (playerJob == null) {
            return "[]";
        }
        
        double progress = getExperienceProgress(playerJob);
        int filledLength = (int) (progress * barLength);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("=");
            } else {
                bar.append("-");
            }
        }
        bar.append("]");
        
        return bar.toString();
    }
    
    public String getFormattedExperienceInfo(PlayerJob playerJob, String jobName) {
        if (playerJob == null) {
            return "職業情報が見つかりません";
        }
        
        if (isMaxLevel(playerJob, jobName)) {
            return String.format("レベル %d (最大レベル) - 経験値: %.0f",
                    playerJob.getLevel(), playerJob.getExperience());
        }
        
        double expToNext = getExperienceToNextLevel(playerJob);
        String progressBar = getExperienceProgressBar(playerJob, 20);
        
        return String.format("レベル %d - 経験値: %.0f (次のレベルまで: %.0f) %s",
                playerJob.getLevel(), playerJob.getExperience(), expToNext, progressBar);
    }
    
    public boolean setExperience(PlayerJob playerJob, String jobName, double experience) {
        if (experience < 0) {
            return false;
        }
        
        if (playerJob == null) {
            return false;
        }
        
        int newLevel = calculateLevelFromExperience(experience);
        int maxLevel = getMaxLevelForJob(jobName);
        
        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            experience = calculateRequiredExperience(maxLevel);
        }
        
        playerJob.setLevel(newLevel);
        playerJob.setExperience(experience);
        
        return playerJobDAO.updatePlayerJobData(playerJob);
    }
    
    public boolean setLevel(PlayerJob playerJob, String jobName, int level) {
        if (level < 1) {
            return false;
        }
        
        int maxLevel = getMaxLevelForJob(jobName);
        if (level > maxLevel) {
            level = maxLevel;
        }
        
        double experience = calculateRequiredExperience(level);
        return setExperience(playerJob, jobName, experience);
    }
}