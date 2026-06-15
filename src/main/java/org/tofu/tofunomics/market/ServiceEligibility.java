package org.tofu.tofunomics.market;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.List;

/**
 * 修理・エンチャント依頼を引き受ける worker の資格判定。
 *
 * 金床を使えるのは鍛冶屋(blacksmith)・エンチャンター(enchanter)に限られるため、
 * これらの職業に就いており、かつ金床を所持している場合のみ引き受けを許可する。
 * （アイテムは引き受け時にシステム保持のままカスタムGUIで加工されるため、
 *   ここでの判定は「手動加工の作業者として適格か」のゲートにあたる）
 */
public class ServiceEligibility {

    public enum Result {
        OK,
        WRONG_JOB,   // 鍛冶屋・エンチャンター以外
        NO_ANVIL     // 金床を所持していない
    }

    /**
     * 判定の純粋ロジック（Bukkit 非依存・単体テスト可能）。
     *
     * @param currentJobName 現在の職業名（無職なら null）
     * @param hasAnvil       金床を所持しているか
     * @param requiredJobs   許可職業一覧（空・null なら職業制限なし）
     * @param requireAnvil   金床所持を必須とするか
     */
    public static Result check(String currentJobName, boolean hasAnvil,
                               List<String> requiredJobs, boolean requireAnvil) {
        if (requiredJobs != null && !requiredJobs.isEmpty()) {
            boolean jobOk = false;
            if (currentJobName != null) {
                for (String job : requiredJobs) {
                    if (job != null && job.equalsIgnoreCase(currentJobName)) {
                        jobOk = true;
                        break;
                    }
                }
            }
            if (!jobOk) {
                return Result.WRONG_JOB;
            }
        }
        if (requireAnvil && !hasAnvil) {
            return Result.NO_ANVIL;
        }
        return Result.OK;
    }

    /**
     * Player から職業・金床所持を解決して判定する（Bukkit 依存）。
     */
    public static Result evaluate(Player worker, JobManager jobManager, ConfigManager configManager) {
        String jobName = resolveJobName(worker, jobManager);
        boolean hasAnvil = hasAnvil(worker);
        return check(jobName, hasAnvil,
                configManager.getMarketServiceRequiredJobs(),
                configManager.isMarketServiceRequireAnvil());
    }

    private static String resolveJobName(Player worker, JobManager jobManager) {
        PlayerJob currentJob = jobManager.getCurrentJob(worker.getUniqueId());
        if (currentJob == null) {
            return null;
        }
        Job job = jobManager.getJobById(currentJob.getJobId());
        return job != null ? job.getName() : null;
    }

    /**
     * 金床（通常・欠けた・破損）をいずれか所持しているか。
     */
    public static boolean hasAnvil(Player worker) {
        return worker.getInventory().contains(Material.ANVIL)
                || worker.getInventory().contains(Material.CHIPPED_ANVIL)
                || worker.getInventory().contains(Material.DAMAGED_ANVIL);
    }
}
