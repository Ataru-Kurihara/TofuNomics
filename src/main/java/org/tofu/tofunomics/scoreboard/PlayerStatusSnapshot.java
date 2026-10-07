package org.tofu.tofunomics.scoreboard;

import org.bukkit.ChatColor;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.UUID;

/**
 * スコアボードとボスバーに出す値のうち、DB から読むものをまとめた写し。
 * {@link TimedCache} に入れて数秒使い回す。
 */
public final class PlayerStatusSnapshot {

    /** 職業に就いているか */
    public final boolean hasJob;
    /** 職業の内部名（miner など）。未就職なら null */
    public final String jobName;
    /** 職業の表示名（鉱夫 など。色コードなし）。未就職なら「なし」 */
    public final String jobDisplayName;
    public final int level;
    public final double experience;
    /** 称号。未就職なら空文字 */
    public final String jobTitle;
    /** その職業の最大レベル */
    public final int jobMaxLevel;
    /** 銀行残高。読まなかった場合は 0 */
    public final double bankBalance;

    private PlayerStatusSnapshot(boolean hasJob, String jobName, String jobDisplayName, int level,
                                 double experience, String jobTitle, int jobMaxLevel, double bankBalance) {
        this.hasJob = hasJob;
        this.jobName = jobName;
        this.jobDisplayName = jobDisplayName;
        this.level = level;
        this.experience = experience;
        this.jobTitle = jobTitle;
        this.jobMaxLevel = jobMaxLevel;
        this.bankBalance = bankBalance;
    }

    /**
     * DB から読んで写しを作る。
     *
     * @param bankBalance 銀行残高（呼び出し側で読んだ値。使わない場合は 0）
     */
    public static PlayerStatusSnapshot load(UUID uuid, JobManager jobManager, ConfigManager configManager,
                                            double bankBalance) {
        PlayerJob currentJob = jobManager.getCurrentJob(uuid);
        Job jobData = currentJob != null ? jobManager.getJobById(currentJob.getJobId()) : null;
        if (currentJob == null || jobData == null) {
            return new PlayerStatusSnapshot(false, null, "なし", 0, 0, "", configManager.getMaxJobLevel(), bankBalance);
        }
        String jobName = jobData.getName();
        String displayName = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', jobManager.getJobDisplayName(jobName)));
        return new PlayerStatusSnapshot(true, jobName, displayName, currentJob.getLevel(),
                currentJob.getExperience(), jobManager.getJobTitle(currentJob.getJobId(), currentJob.getLevel()),
                configManager.getJobMaxLevel(jobName), bankBalance);
    }

    public boolean isMaxLevel() {
        return hasJob && level >= jobMaxLevel;
    }
}
