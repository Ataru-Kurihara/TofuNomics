package org.tofu.tofunomics.food;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食事による職業経験値ブーストバフの管理クラス。
 *
 * 「職業マッチ制」: 食材カテゴリ（パン/野菜/魚/肉）にはそれぞれ対応職業が設定されており、
 * 自分の職業に対応した食材を食べたときだけ経験値ブーストが効く。対応しない食材を
 * 食べてもブーストは付かない（倍率1.0）。ボーナス量・持続時間は全職業共通。
 *
 * 重複挙動: マッチした食材のときだけバフを付与/更新する（最後に食べたマッチ食材で上書き）。
 * 非マッチ食材は既存の有効バフを上書きしないため、効果中に別系統の食材を食べても
 * バフは消えない。マッチ判定の正本は {@link #getMultiplier(Player, String)} 側に置く
 * （食後の転職や経験値経路ごとの職業差に正しく追従するため）。
 */
public class FoodBuffManager {

    private final ConfigManager configManager;
    // applyBuff 時のマッチ/非マッチ メッセージ出し分け用（倍率算出には不要）
    private final JobManager jobManager;

    // プレイヤーUUID -> 有効中のバフ
    private final Map<UUID, ActiveBuff> activeBuffs = new ConcurrentHashMap<>();

    public FoodBuffManager(ConfigManager configManager, JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    /**
     * 有効中のバフ情報
     */
    private static class ActiveBuff {
        final double factor;        // 経験値倍率（例: +25% -> 1.25）
        final long expiresAtMillis; // 失効時刻（エポックミリ秒）
        final String categoryKey;   // 対応職業を引くためのカテゴリキー（bread/vegetable/fish/meat）

        ActiveBuff(double factor, long expiresAtMillis, String categoryKey) {
            this.factor = factor;
            this.expiresAtMillis = expiresAtMillis;
            this.categoryKey = categoryKey;
        }
    }

    /**
     * 食材カテゴリに応じたバフを付与する。
     * バフ自体は常に最後に食べたカテゴリで上書きするが、実際にブーストが効くかは
     * 経験値獲得時のマッチ判定（getMultiplier）に委ねる。
     * ここでは現在の職業に対応するかでメッセージのみ出し分ける。
     */
    public void applyBuff(Player player, FoodCategory category) {
        if (player == null || category == null) {
            return;
        }
        if (!configManager.isFoodBuffEnabled()) {
            return;
        }

        int bonusPercent = configManager.getFoodBuffBonusPercent();
        int durationSeconds = configManager.getFoodBuffDurationSeconds();

        // 効果が無い設定（0%や0秒）の場合はバフを付与しない
        if (bonusPercent <= 0 || durationSeconds <= 0) {
            return;
        }

        String key = category.getConfigKey();

        // 現在の職業がこのカテゴリにマッチするか判定し、メッセージを出し分ける
        String jobName = (jobManager != null) ? jobManager.getPlayerJob(player.getUniqueId()) : null;
        boolean matches = jobName != null
            && configManager.getFoodBuffCategoryJobs(key).contains(jobName);

        if (matches) {
            // マッチした食材のときだけバフを付与/更新する。
            // 非マッチ食材で既存の有効バフを上書きしないことで、効果中に別系統の
            // 食材を食べてもバフ（と職業レベルバーの表示）が消えないようにする。
            double newFactor = 1.0 + (bonusPercent / 100.0);
            long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
            activeBuffs.put(player.getUniqueId(), new ActiveBuff(newFactor, expiresAt, key));
            sendBuffMessage(player, category, bonusPercent, durationSeconds);
        } else {
            sendNoMatchMessage(player, category, jobName);
        }
    }

    /**
     * プレイヤーの指定職業に対する経験値倍率を取得する（マッチ判定の正本）。
     * 有効なバフが無い・失効済み・現在のバフカテゴリが jobName に対応しない場合は 1.0 を返す。
     */
    public double getMultiplier(Player player, String jobName) {
        if (player == null || jobName == null) {
            return 1.0;
        }
        UUID uuid = player.getUniqueId();
        ActiveBuff buff = activeBuffs.get(uuid);
        if (buff == null) {
            return 1.0;
        }
        if (buff.expiresAtMillis <= System.currentTimeMillis()) {
            // 失効済みは破棄
            activeBuffs.remove(uuid);
            return 1.0;
        }
        // 保持カテゴリの対応職業に jobName が含まれるときだけブースト
        if (!configManager.getFoodBuffCategoryJobs(buff.categoryKey).contains(jobName)) {
            return 1.0;
        }
        return buff.factor;
    }

    /**
     * 現在の職業に対して有効な食事バフのスナップショット（表示用）。
     */
    public static final class ActiveBuffInfo {
        private final int bonusPercent;      // 経験値上昇割合（例: +25% -> 25）
        private final long remainingSeconds; // 失効までの残り秒（切り上げ）

        public ActiveBuffInfo(int bonusPercent, long remainingSeconds) {
            this.bonusPercent = bonusPercent;
            this.remainingSeconds = remainingSeconds;
        }

        public int getBonusPercent() {
            return bonusPercent;
        }

        public long getRemainingSeconds() {
            return remainingSeconds;
        }
    }

    /**
     * 指定職業に対して現在有効な食事バフの表示用情報を返す。
     * 判定条件は {@link #getMultiplier(Player, String)} と完全一致させる
     * （バフ存在・未失効・保持カテゴリの対応職業に jobName が含まれる）。
     * 一つでも満たさなければ null を返す。jobName は内部職業名を渡すこと。
     */
    public ActiveBuffInfo getActiveBuffInfo(Player player, String jobName) {
        if (player == null || jobName == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        ActiveBuff buff = activeBuffs.get(uuid);
        if (buff == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (buff.expiresAtMillis <= now) {
            // 失効済みは破棄（getMultiplier と同じ副作用に揃える）
            activeBuffs.remove(uuid);
            return null;
        }
        // 保持カテゴリの対応職業に jobName が含まれるときだけ有効
        if (!configManager.getFoodBuffCategoryJobs(buff.categoryKey).contains(jobName)) {
            return null;
        }
        int bonusPercent = (int) Math.round((buff.factor - 1.0) * 100);
        long remainingSeconds = (buff.expiresAtMillis - now + 999) / 1000; // 切り上げ
        return new ActiveBuffInfo(bonusPercent, remainingSeconds);
    }

    /**
     * プレイヤーの状態をクリアする（ログアウト時などに呼ぶ想定。任意）
     */
    public void clear(UUID uuid) {
        if (uuid != null) {
            activeBuffs.remove(uuid);
        }
    }

    private void sendBuffMessage(Player player, FoodCategory category,
                                 int bonusPercent, int durationSeconds) {
        String template = configManager.getFoodBuffAppliedMessage();
        if (template == null || template.isEmpty()) {
            return;
        }
        String message = template
            .replace("%category%", category.getDisplayName())
            .replace("%percent%", String.valueOf(bonusPercent))
            .replace("%duration%", String.valueOf(durationSeconds));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * 食べた食材が現在の職業に対応しないため、ブーストが効かないことを通知する。
     */
    private void sendNoMatchMessage(Player player, FoodCategory category, String jobName) {
        String template = configManager.getFoodBuffNoMatchMessage();
        if (template == null || template.isEmpty()) {
            return;
        }
        // 無職時は「無職」と表示。就職済みは職業の日本語表示名を表示。
        String jobLabel = (jobName != null) ? configManager.getJobDisplayName(jobName) : "無職";
        String message = template
            .replace("%category%", category.getDisplayName())
            .replace("%job%", jobLabel);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
