package org.tofu.tofunomics.jobs;

import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobChangeDAO;
import org.tofu.tofunomics.dao.JobHistoryDAO;
import org.tofu.tofunomics.models.JobHistory;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.TofuNomics;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class JobManager {
    
    private final ConfigManager configManager;
    private final JobDAO jobDAO;
    private final PlayerDAO playerDAO;
    private final PlayerJobDAO playerJobDAO;
    private final JobChangeDAO jobChangeDAO;
    private final JobHistoryDAO jobHistoryDAO;
    
    public JobManager(ConfigManager configManager, JobDAO jobDAO, PlayerDAO playerDAO, 
                     PlayerJobDAO playerJobDAO, JobChangeDAO jobChangeDAO, JobHistoryDAO jobHistoryDAO) {
        this.configManager = configManager;
        this.jobDAO = jobDAO;
        this.playerDAO = playerDAO;
        this.playerJobDAO = playerJobDAO;
        this.jobChangeDAO = jobChangeDAO;
        this.jobHistoryDAO = jobHistoryDAO;
    }
    
    public enum JobJoinResult {
        SUCCESS,
        ALREADY_HAS_JOB,
        JOB_NOT_FOUND,
        LEVEL_TOO_LOW,
        MAX_JOBS_REACHED,
        ADVANCED_JOB_LOCKED,
        DATABASE_ERROR
    }

    /**
     * プレイヤーがいずれかの職業でレベル50に到達した経験があるかどうかを判定する。
     * 現在の職業または過去の職業履歴のいずれかでレベル50到達があれば true。
     * 転職条件・上級職業の解禁条件の判定に共通利用する。
     */
    private boolean hasReachedLevel50(String uuid, List<PlayerJob> currentJobs) {
        for (PlayerJob existingJob : currentJobs) {
            if (existingJob.getLevel() >= 50) {
                return true;
            }
        }
        return jobHistoryDAO.hasReachedLevel50(uuid);
    }

    /**
     * プレイヤーがいずれかの職業でレベル50に到達した経験があるかどうかを判定する（GUI 用公開版）。
     */
    public boolean hasReachedLevel50(Player player) {
        String uuid = player.getUniqueId().toString();
        return hasReachedLevel50(uuid, playerJobDAO.getPlayerJobsByUUID(uuid));
    }
    
    public JobJoinResult joinJob(Player player, String jobName) {
        String uuid = player.getUniqueId().toString();
        
        Job job = jobDAO.getJobByNameSafe(jobName);
        if (job == null) {
            return JobJoinResult.JOB_NOT_FOUND;
        }
        
        List<PlayerJob> currentJobs = playerJobDAO.getPlayerJobsByUUID(uuid);
        int maxJobs = configManager.getMaxJobsPerPlayer();
        boolean reachedLevel50 = hasReachedLevel50(uuid, currentJobs);

        // 上級職業は初回就職では選べず、いずれかの職業でレベル50到達後に解禁される
        if (configManager.isAdvancedJob(jobName) && !reachedLevel50) {
            return JobJoinResult.ADVANCED_JOB_LOCKED;
        }

        // レベル50チェック（転職時のみ）
        // 現在職業を持っている場合のみチェック。初回就職・再就職（現在職業なし）は制限なし
        if (!currentJobs.isEmpty() && !reachedLevel50) {
            return JobJoinResult.LEVEL_TOO_LOW;
        }

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
        
        // job_historyから過去の記録を取得してレベルを復元
        JobHistory history = jobHistoryDAO.getLatestJobHistory(uuid, job.getId());
        if (history != null) {
            // 過去の記録がある場合はレベルを復元
            playerJob.setLevel(history.getMaxLevel());
        } else {
            // 初回就職の場合はレベル1
            playerJob.setLevel(1);
        }
        playerJob.setExperience(0.0); // 経験値は常に0
        
        if (!playerJobDAO.insertPlayerJob(playerJob)) {
            return JobJoinResult.DATABASE_ERROR;
        }

        // 農家就職時に畑区画を自動割り当て
        onFarmerJobJoined(player, jobName);

        return JobJoinResult.SUCCESS;
    }

    /**
     * 農家就職時に畑区画を自動割り当てする（farmer以外は何もしない）。
     * FarmPlotManagerは遅延取得し、未初期化でも安全に無視する。
     */
    private void onFarmerJobJoined(Player player, String jobName) {
        if (!"farmer".equalsIgnoreCase(jobName)) {
            return;
        }
        try {
            org.tofu.tofunomics.farming.FarmPlotManager fpm = TofuNomics.getInstance().getFarmPlotManager();
            if (fpm != null) {
                fpm.assignPlot(player);
            }
        } catch (Exception e) {
            TofuNomics.getInstance().getLogger().warning("農家区画の自動割り当てに失敗しました: " + e.getMessage());
        }
    }

    /**
     * 農家離職/転職時に畑区画を解放する（farmer以外は何もしない）。
     */
    private void onFarmerJobLeft(java.util.UUID playerUuid, String jobName) {
        if (!"farmer".equalsIgnoreCase(jobName)) {
            return;
        }
        try {
            org.tofu.tofunomics.farming.FarmPlotManager fpm = TofuNomics.getInstance().getFarmPlotManager();
            if (fpm != null) {
                fpm.releasePlot(playerUuid);
            }
        } catch (Exception e) {
            TofuNomics.getInstance().getLogger().warning("農家区画の解放に失敗しました: " + e.getMessage());
        }
    }
    
    public enum JobLeaveResult {
        SUCCESS,
        NO_SUCH_JOB,
        DATABASE_ERROR,
        DAILY_LIMIT_EXCEEDED,
        LEVEL_TOO_LOW
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
        
        // レベル50未満は辞職不可
        if (playerJob.getLevel() < 50) {
            return JobLeaveResult.LEVEL_TOO_LOW;
        }
        
        // 退職前に職業履歴を保存
        JobHistory jobHistory = new JobHistory(uuid, job.getId(), playerJob.getLevel());
        if (!jobHistoryDAO.insertJobHistory(jobHistory)) {
            TofuNomics.getInstance().getLogger().warning("職業履歴の保存に失敗しました: " + uuid + ", job=" + jobName);
        }
        
        if (!playerJobDAO.deletePlayerJob(uuid, job.getId())) {
            return JobLeaveResult.DATABASE_ERROR;
        }

        if (configManager.isDailyJobChangeLimitEnabled()) {
            jobChangeDAO.recordJobChangeToday(uuid);
        }

        // 農家離職時に畑区画を解放
        onFarmerJobLeft(player.getUniqueId(), jobName);

        return JobLeaveResult.SUCCESS;
    }


    /**
     * 管理者による強制辞職（レベル制限を無視）
     * @param player プレイヤー
     * @param jobName 職業名
     * @return 辞職結果
     */
    public JobLeaveResult forceLeaveJob(Player player, String jobName) {
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
        
        // レベルチェックをスキップして辞職処理を実行
        
        // 退職前に職業履歴を保存
        JobHistory jobHistory = new JobHistory(uuid, job.getId(), playerJob.getLevel());
        if (!jobHistoryDAO.insertJobHistory(jobHistory)) {
            TofuNomics.getInstance().getLogger().warning("職業履歴の保存に失敗しました: " + uuid + ", job=" + jobName);
        }
        
        if (!playerJobDAO.deletePlayerJob(uuid, job.getId())) {
            return JobLeaveResult.DATABASE_ERROR;
        }

        if (configManager.isDailyJobChangeLimitEnabled()) {
            jobChangeDAO.recordJobChangeToday(uuid);
        }

        // 農家強制離職時に畑区画を解放
        onFarmerJobLeft(player.getUniqueId(), jobName);

        return JobLeaveResult.SUCCESS;
    }
    
    public List<PlayerJob> getPlayerJobs(Player player) {
        return playerJobDAO.getPlayerJobsByUUID(player.getUniqueId().toString());
    }

    /**
     * プレイヤーの全職業データを完全にリセットする（管理者テストプレイ用）。
     * player_jobs（現在の職業・レベル・経験値）、job_history（過去の最高レベル履歴）、
     * job_changes（日次変更制限記録）をすべて削除し、初期状態（無職・履歴なし）に戻す。
     * forceLeaveJob()は履歴を保存し日次制限にも引っかかるため、完全初期化には使用しない。
     *
     * @return すべての削除に成功した場合true
     */
    public boolean resetAllJobs(Player player) {
        UUID uuid = player.getUniqueId();
        String uuidString = uuid.toString();
        boolean success = true;

        // player_jobsの削除が失敗してもjob_historyの削除は試みる。
        // 万一player_jobsだけ削除できた場合は履歴が残る不整合が生じうるが、
        // テスト用途であり、再実行すれば解消できるため許容する。
        try {
            playerJobDAO.deleteAllPlayerJobs(uuid);
        } catch (SQLException e) {
            TofuNomics.getInstance().getLogger().warning("職業データの削除に失敗しました: " + uuidString + " - " + e.getMessage());
            success = false;
        }

        if (!jobHistoryDAO.deleteAllHistoriesByUUID(uuidString)) {
            TofuNomics.getInstance().getLogger().warning("職業履歴の削除に失敗しました: " + uuidString);
            success = false;
        }

        // 日次変更制限の記録を削除（本日変更していなければ記録自体が存在せずfalseが返るが、
        // それは正常な状態なので成否判定には含めない）
        jobChangeDAO.deleteJobChange(uuidString);

        if (success) {
            TofuNomics.getInstance().getLogger().info(
                "管理者コマンドにより職業データをリセットしました: " + player.getName() + " (" + uuidString + ")");
        }

        return success;
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
            TofuNomics.getInstance().getLogger().severe("職業IDからの取得に失敗しました (jobId=" + jobId + "): " + e.getMessage());
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