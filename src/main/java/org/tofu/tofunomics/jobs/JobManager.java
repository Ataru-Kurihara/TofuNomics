package org.tofu.tofunomics.jobs;

import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobChangeDAO;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.TofuNomics;

import java.util.List;
import java.util.logging.Logger;

public class JobManager {
    
    private final ConfigManager configManager;
    private final JobDAO jobDAO;
    private final PlayerDAO playerDAO;
    private final PlayerJobDAO playerJobDAO;
    private final JobChangeDAO jobChangeDAO;
    
    public JobManager(ConfigManager configManager, JobDAO jobDAO, PlayerDAO playerDAO, 
                     PlayerJobDAO playerJobDAO, JobChangeDAO jobChangeDAO) {
        this.configManager = configManager;
        this.jobDAO = jobDAO;
        this.playerDAO = playerDAO;
        this.playerJobDAO = playerJobDAO;
        this.jobChangeDAO = jobChangeDAO;
    }
    
    public enum JobJoinResult {
        SUCCESS,
        ALREADY_HAS_JOB,
        JOB_NOT_FOUND,
        DAILY_LIMIT_EXCEEDED,
        MAX_JOBS_REACHED,
        DATABASE_ERROR
    }
    
    public JobJoinResult joinJob(Player player, String jobName) {
        String uuid = player.getUniqueId().toString();
        
        Job job = jobDAO.getJobByNameSafe(jobName);
        if (job == null) {
            return JobJoinResult.JOB_NOT_FOUND;
        }
        
        if (configManager.isDailyJobChangeLimitEnabled() && 
            !jobChangeDAO.canPlayerChangeJobToday(uuid)) {
            return JobJoinResult.DAILY_LIMIT_EXCEEDED;
        }
        
        List<PlayerJob> currentJobs = playerJobDAO.getPlayerJobsByUUID(uuid);
        int maxJobs = configManager.getMaxJobsPerPlayer();
        
        if (currentJobs.size() >= maxJobs) {
            return JobJoinResult.MAX_JOBS_REACHED;
        }
        
        for (PlayerJob existingJob : currentJobs) {
            if (existingJob.getJobId() == job.getId()) {
                return JobJoinResult.ALREADY_HAS_JOB;
            }
        }
        
        ensurePlayerExists(player);
        
        PlayerJob playerJob = new PlayerJob();
        playerJob.setUuid(uuid);
        playerJob.setJobId(job.getId());
        playerJob.setLevel(1);
        playerJob.setExperience(0.0);
        
        if (!playerJobDAO.insertPlayerJob(playerJob)) {
            return JobJoinResult.DATABASE_ERROR;
        }
        
        if (configManager.isDailyJobChangeLimitEnabled()) {
            jobChangeDAO.recordJobChangeToday(uuid);
        }
        
        return JobJoinResult.SUCCESS;
    }
    
    public enum JobLeaveResult {
        SUCCESS,
        NO_SUCH_JOB,
        DATABASE_ERROR,
        DAILY_LIMIT_EXCEEDED
    }
    
    public JobLeaveResult leaveJob(Player player, String jobName) {
        String uuid = player.getUniqueId().toString();
        
        if (configManager.isDailyJobChangeLimitEnabled() && 
            !jobChangeDAO.canPlayerChangeJobToday(uuid)) {
            return JobLeaveResult.DAILY_LIMIT_EXCEEDED;
        }
        
        Job job = jobDAO.getJobByNameSafe(jobName);
        if (job == null) {
            return JobLeaveResult.NO_SUCH_JOB;
        }
        
        PlayerJob playerJob = playerJobDAO.getPlayerJob(uuid, job.getId());
        if (playerJob == null) {
            return JobLeaveResult.NO_SUCH_JOB;
        }
        
        if (!playerJobDAO.deletePlayerJob(uuid, job.getId())) {
            return JobLeaveResult.DATABASE_ERROR;
        }
        
        if (configManager.isDailyJobChangeLimitEnabled()) {
            jobChangeDAO.recordJobChangeToday(uuid);
        }
        
        return JobLeaveResult.SUCCESS;
    }
    
    public List<PlayerJob> getPlayerJobs(Player player) {
        return playerJobDAO.getPlayerJobsByUUID(player.getUniqueId().toString());
    }
    
    public PlayerJob getPlayerJob(Player player, String jobName) {
        Job job = jobDAO.getJobByNameSafe(jobName);
        if (job == null) {
            return null;
        }
        
        return playerJobDAO.getPlayerJob(player.getUniqueId().toString(), job.getId());
    }
    
    public boolean hasJob(Player player, String jobName) {
        PlayerJob playerJob = getPlayerJob(player, jobName);
        return playerJob != null;
    }
    
    public List<Job> getAllJobs() {
        return jobDAO.getAllJobsSafe();
    }
    
    public Job getJobByName(String jobName) {
        return jobDAO.getJobByNameSafe(jobName);
    }
    
    public Job getJobById(int jobId) {
        try {
            return jobDAO.getJobById(jobId);
        } catch (java.sql.SQLException e) {
            // エラーログを出力してnullを返す
            System.err.println("Failed to get job by id: " + jobId + " - " + e.getMessage());
            return null;
        }
    }
    
    public PlayerJob getCurrentJob(java.util.UUID uuid) {
        List<PlayerJob> jobs = playerJobDAO.getPlayerJobsByUUID(uuid.toString());
        if (jobs.isEmpty()) {
            return null;
        }
        // 最初の職業を返す（仕様上は1つの職業のみ）
        return jobs.get(0);
    }
    
    /**
     * プレイヤーの現在の職業名を取得
     */
    public String getPlayerJob(java.util.UUID uuid) {
        PlayerJob currentJob = getCurrentJob(uuid);
        if (currentJob == null) {
            return null;
        }
        
        Job job = jobDAO.getJobByIdSafe(currentJob.getJobId());
        return job != null ? job.getName() : null;
    }
    
    public boolean canChangeJobToday(Player player) {
        if (!configManager.isDailyJobChangeLimitEnabled()) {
            return true;
        }
        
        return jobChangeDAO.canPlayerChangeJobToday(player.getUniqueId().toString());
    }
    
    public String getJobChangeStatusMessage(Player player) {
        if (!configManager.isDailyJobChangeLimitEnabled()) {
            return "";
        }
        
        if (canChangeJobToday(player)) {
            return "今日はまだ職業を変更できます。";
        } else {
            return "職業変更は1日1回までです。明日になったら再度お試しください。";
        }
    }
    
    private void ensurePlayerExists(Player player) {
        String uuid = player.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(configManager.getStartingBalance());
            playerDAO.insertPlayer(tofuPlayer);
        }
    }
    
    public boolean isValidJobName(String jobName) {
        return jobDAO.getJobByNameSafe(jobName) != null;
    }
    
    public String[] getJobNames() {
        List<Job> jobs = getAllJobs();
        return jobs.stream()
                   .map(Job::getName)
                   .toArray(String[]::new);
    }
    
    public String getJobDisplayName(String jobName) {
        Job job = getJobByName(jobName);
        if (job != null) {
            return job.getDisplayName();
        }
        return configManager.getJobDisplayName(jobName);
    }
    
    /**
     * 職業IDとレベルに基づいて称号を取得する
     */
    public String getJobTitle(int jobId, int level) {
        Job job = getJobById(jobId);
        if (job == null) {
            return "不明";
        }
        
        String jobName = job.getName().toLowerCase();
        
        // レベルに基づく称号を返す
        if (level >= 75) {
            return getTitleForLevel75(jobName);
        } else if (level >= 50) {
            return getTitleForLevel50(jobName);
        } else if (level >= 25) {
            return getTitleForLevel25(jobName);
        } else if (level >= 10) {
            return getTitleForLevel10(jobName);
        } else {
            return getTitleForLevel1(jobName);
        }
    }
    
    private String getTitleForLevel75(String jobName) {
        switch (jobName) {
            case "miner": return "伝説の岩窟王";
            case "woodcutter": return "生命の樹の守り手";
            case "farmer": return "収穫の神";
            case "fisherman": return "深淵の支配者";
            case "blacksmith": return "神器の創造主";
            case "alchemist": return "真理の探求者";
            case "enchanter": return "大賢者";
            case "architect": return "世界の創造主";
            default: return "マスター";
        }
    }
    
    private String getTitleForLevel50(String jobName) {
        switch (jobName) {
            case "miner": return "アースワーデン";
            case "woodcutter": return "フォレストキーパー";
            case "farmer": return "大地の恵み";
            case "fisherman": return "海の友";
            case "blacksmith": return "魂を宿す者";
            case "alchemist": return "賢者の石を探す者";
            case "enchanter": return "古代の呪文詠唱者";
            case "architect": return "街の設計者";
            default: return "エキスパート";
        }
    }
    
    private String getTitleForLevel25(String jobName) {
        switch (jobName) {
            case "miner": return "マスターマイナー";
            case "woodcutter": return "マスターランバージャック";
            case "farmer": return "マスターファーマー";
            case "fisherman": return "マスターアングラー";
            case "blacksmith": return "マスターブラックスミス";
            case "alchemist": return "マスターアルケミスト";
            case "enchanter": return "マスターエンチャンター";
            case "architect": return "マスターアーキテクト";
            default: return "マスター";
        }
    }
    
    private String getTitleForLevel10(String jobName) {
        switch (jobName) {
            case "miner": return "熟練鉱夫";
            case "woodcutter": return "熟練木こり";
            case "farmer": return "熟練農家";
            case "fisherman": return "熟練釣り人";
            case "blacksmith": return "熟練鍛冶屋";
            case "alchemist": return "熟練錬金術師";
            case "enchanter": return "熟練付与術師";
            case "architect": return "熟練建築家";
            default: return "熟練者";
        }
    }
    
    private String getTitleForLevel1(String jobName) {
        switch (jobName) {
            case "miner": return "見習い鉱夫";
            case "woodcutter": return "見習い木こり";
            case "farmer": return "見習い農家";
            case "fisherman": return "見習い釣り人";
            case "blacksmith": return "見習い鍛冶屋";
            case "alchemist": return "見習い錬金術師";
            case "enchanter": return "見習い付与術師";
            case "architect": return "見習い建築家";
            default: return "見習い";
        }
    }
}