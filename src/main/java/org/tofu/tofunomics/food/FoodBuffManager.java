package org.tofu.tofunomics.food;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食事による職業経験値ブーストバフの管理クラス。
 *
 * 食材を食べると、そのカテゴリ（パン/野菜/魚/肉）に応じた一定時間の
 * 職業経験値倍率バフを付与する。倍率は経験値獲得時（JobExperienceManager）に乗算される。
 *
 * 重複挙動: 上書き（一番強いものが有効）。
 *   - より強い（または同等の）バフを食べた場合は上書き（持続時間もリセット）。
 *   - 既存より弱いバフを食べても無視（下げない・延長しない）。
 */
public class FoodBuffManager {

    private final ConfigManager configManager;

    // プレイヤーUUID -> 有効中のバフ
    private final Map<UUID, ActiveBuff> activeBuffs = new ConcurrentHashMap<>();

    public FoodBuffManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 有効中のバフ情報
     */
    private static class ActiveBuff {
        final double factor;       // 経験値倍率（例: +30% -> 1.30）
        final long expiresAtMillis; // 失効時刻（エポックミリ秒）
        final String displayName;  // カテゴリ表示名

        ActiveBuff(double factor, long expiresAtMillis, String displayName) {
            this.factor = factor;
            this.expiresAtMillis = expiresAtMillis;
            this.displayName = displayName;
        }
    }

    /**
     * 食材カテゴリに応じたバフを付与する。
     * 既に有効なバフより弱い場合は何もしない（一番強いものが有効）。
     */
    public void applyBuff(Player player, FoodCategory category) {
        if (player == null || category == null) {
            return;
        }
        if (!configManager.isFoodBuffEnabled()) {
            return;
        }

        String key = category.getConfigKey();
        int bonusPercent = configManager.getFoodBuffBonusPercent(key);
        int durationSeconds = configManager.getFoodBuffDurationSeconds(key);

        // 効果が無い設定（0%や0秒）の場合はバフを付与しない
        if (bonusPercent <= 0 || durationSeconds <= 0) {
            return;
        }

        double newFactor = 1.0 + (bonusPercent / 100.0);
        long now = System.currentTimeMillis();
        long expiresAt = now + (durationSeconds * 1000L);

        UUID uuid = player.getUniqueId();
        ActiveBuff current = activeBuffs.get(uuid);
        boolean currentValid = current != null && current.expiresAtMillis > now;

        // 既存が有効かつ今回が弱い場合は何もしない（下げない・延長しない）
        // 「食べたのに無反応」に見えるのを防ぐため、理由をプレイヤーに通知する
        if (currentValid && newFactor < current.factor) {
            sendWeakerIgnoredMessage(player, current);
            return;
        }

        boolean replaced = currentValid;
        activeBuffs.put(uuid, new ActiveBuff(newFactor, expiresAt, category.getDisplayName()));

        sendBuffMessage(player, replaced, category, bonusPercent, durationSeconds);
    }

    /**
     * プレイヤーの現在の経験値倍率を取得する。
     * 有効なバフが無い、または失効済みの場合は 1.0 を返す（遅延失効）。
     */
    public double getMultiplier(Player player) {
        if (player == null) {
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
        return buff.factor;
    }

    /**
     * プレイヤーの状態をクリアする（ログアウト時などに呼ぶ想定。任意）
     */
    public void clear(UUID uuid) {
        if (uuid != null) {
            activeBuffs.remove(uuid);
        }
    }

    private void sendBuffMessage(Player player, boolean replaced, FoodCategory category,
                                 int bonusPercent, int durationSeconds) {
        String template = replaced
            ? configManager.getFoodBuffReplacedMessage()
            : configManager.getFoodBuffAppliedMessage();
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
     * 既により強いバフが有効なため、弱い食事効果が無視されたことを通知する。
     */
    private void sendWeakerIgnoredMessage(Player player, ActiveBuff current) {
        String template = configManager.getFoodBuffWeakerIgnoredMessage();
        if (template == null || template.isEmpty()) {
            return;
        }
        String message = template.replace("%current%", current.displayName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
