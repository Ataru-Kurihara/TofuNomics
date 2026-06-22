package org.tofu.tofunomics.rewards;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業レベルアップ報酬システム
 *
 * 報酬は「お金（通貨）」のみ。5レベルおき（Lv5, 10, …, 100）に付与する。
 * 金額は職業別の基礎額にレベル段階（level / 5）を掛けた値。
 * 人気職は優遇せず、手間のかかる専門職を厚めに設定している。
 */
public class JobLevelRewardManager {

    /** 報酬を付与するレベル間隔 */
    private static final int REWARD_INTERVAL = 5;

    /** 報酬対象の最大レベル */
    private static final int MAX_REWARD_LEVEL = 100;

    /** 未知の職業に対するデフォルト基礎額 */
    private static final double DEFAULT_BASE_REWARD = 40.0;

    /** 職業別の基礎報酬額（Lv5時点の金額。以降は段階に比例して増加） */
    private static final Map<String, Double> JOB_BASE_REWARD = new HashMap<>();
    static {
        JOB_BASE_REWARD.put("enchanter", 50.0);   // 地味・手間 → 優遇
        JOB_BASE_REWARD.put("alchemist", 50.0);   // 地味・手間 → 優遇
        JOB_BASE_REWARD.put("architect", 45.0);   // スキル要・地味
        JOB_BASE_REWARD.put("blacksmith", 45.0);  // 手間
        JOB_BASE_REWARD.put("miner", 40.0);       // 人気・中位
        JOB_BASE_REWARD.put("farmer", 40.0);      // 人気・中位
        JOB_BASE_REWARD.put("woodcutter", 40.0);  // 単純・中位
        JOB_BASE_REWARD.put("fisherman", 35.0);   // 放置気味・人気
    }

    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;

    public JobLevelRewardManager(ConfigManager configManager, CurrencyConverter currencyConverter) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
    }

    /**
     * 指定職業・レベルのお金報酬額を返す。
     * 報酬対象でないレベル（5の倍数でない、0以下、上限超過）は0を返す。
     */
    public double getRewardAmount(String jobName, int level) {
        if (level <= 0 || level > MAX_REWARD_LEVEL || level % REWARD_INTERVAL != 0) {
            return 0.0;
        }
        double base = JOB_BASE_REWARD.getOrDefault(
            jobName == null ? "" : jobName.toLowerCase(), DEFAULT_BASE_REWARD);
        return base * (level / REWARD_INTERVAL);
    }

    /**
     * レベルアップ報酬（お金）をプレイヤーに付与する。
     * 報酬対象レベルでなければ何もしない。
     *
     * @return 報酬を付与した場合true
     */
    public boolean giveJobLevelReward(Player player, String jobName, int level) {
        double amount = getRewardAmount(jobName, level);
        if (amount <= 0) {
            return false;
        }

        // 残高付与が成功した場合のみメッセージを送る（DBとキャッシュの整合を保つ）
        if (!giveMoney(player, amount)) {
            return false;
        }
        sendLevelUpRewardMessage(player, jobName, level, amount);
        return true;
    }

    /**
     * お金報酬を付与する。CurrencyConverter経由で付与し、DBとメモリキャッシュの
     * 両方を更新する（直接DBを書き換えるとオンライン中の残高キャッシュと食い違う）。
     *
     * @return 付与に成功した場合true
     */
    private boolean giveMoney(Player player, double amount) {
        boolean ok = currencyConverter.addBalance(player.getUniqueId(), amount);
        if (!ok) {
            // 残高上限到達やプレイヤー未登録などで付与できなかった場合
            org.bukkit.Bukkit.getLogger().warning(
                "[TofuNomics] レベルアップ報酬の付与に失敗しました: " +
                player.getName() + " amount=" + amount);
        }
        return ok;
    }

    private void sendLevelUpRewardMessage(Player player, String jobName, int level, double amount) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GOLD + "★ " + configManager.getJobDisplayName(jobName) +
                          " レベル " + level + " 達成報酬 ★");
        player.sendMessage(ChatColor.GREEN + "報酬: " + formatAmount(amount) + " " +
                          configManager.getCurrencySymbol());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private String formatAmount(double amount) {
        // 通貨は整数（金塊）単位。小数を切り捨てて表示する。
        return String.valueOf((long) amount);
    }

    /**
     * 指定された職業の、現在レベルより先にある次の報酬レベルを返す。
     * なければ-1。
     */
    public int getNextRewardLevel(String jobName, int currentLevel) {
        int next = ((currentLevel / REWARD_INTERVAL) + 1) * REWARD_INTERVAL;
        return next <= MAX_REWARD_LEVEL ? next : -1;
    }

    /**
     * 全報酬レベルの一覧（Lv5, 10, …, 100）を返す。
     */
    public List<Integer> getAllRewardLevels(String jobName) {
        List<Integer> levels = new ArrayList<>();
        for (int level = REWARD_INTERVAL; level <= MAX_REWARD_LEVEL; level += REWARD_INTERVAL) {
            levels.add(level);
        }
        return levels;
    }

    /**
     * 指定レベルに報酬があるか（5の倍数かつ上限以内か）。
     */
    public boolean hasRewardAtLevel(String jobName, int level) {
        return getRewardAmount(jobName, level) > 0;
    }
}
