package org.tofu.tofunomics.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * チュートリアルシステムの中心管理クラス
 * プレイヤーのチュートリアル進捗を管理し、メッセージ表示を行う
 */
public class TutorialManager {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final TutorialProgressDAO tutorialProgressDAO;
    private final Logger logger;

    // インメモリキャッシュ（パフォーマンス最適化）
    private final Map<UUID, TutorialStep> playerProgressCache;

    public TutorialManager(TofuNomics plugin, ConfigManager configManager, TutorialProgressDAO tutorialProgressDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tutorialProgressDAO = tutorialProgressDAO;
        this.logger = plugin.getLogger();
        this.playerProgressCache = new HashMap<>();
    }

    /**
     * チュートリアルが有効かどうか
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("tutorial.enabled", true);
    }

    /**
     * 自動開始が有効かどうか
     */
    public boolean isAutoStartEnabled() {
        return plugin.getConfig().getBoolean("tutorial.auto_start", true);
    }

    /**
     * 開始遅延時間（秒）
     */
    public int getStartDelaySeconds() {
        return plugin.getConfig().getInt("tutorial.start_delay_seconds", 10);
    }

    /**
     * チュートリアルを開始
     * @param player プレイヤー
     */
    public void startTutorial(Player player) {
        if (!isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        try {
            // 既に開始済みの場合は何もしない
            if (tutorialProgressDAO.hasProgress(uuid)) {
                TutorialStep currentStep = tutorialProgressDAO.getProgress(uuid);
                if (currentStep.isCompleted()) {
                    return;
                }
                // 進行中の場合は現在のステップを表示
                playerProgressCache.put(uuid, currentStep);
                sendStepMessage(player, currentStep);
                return;
            }

            // 新規開始
            TutorialStep firstStep = TutorialStep.JOB_SELECT;
            tutorialProgressDAO.createProgress(uuid, firstStep);
            playerProgressCache.put(uuid, firstStep);

            // 開始メッセージを送信
            sendStartMessage(player);

            // 少し遅延してから最初のステップメッセージを送信
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    sendStepMessage(player, firstStep);
                }
            }, 60L); // 3秒後

        } catch (SQLException e) {
            logger.warning("チュートリアル開始時にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * 次のステップに進む
     * @param player プレイヤー
     */
    public void advanceStep(Player player) {
        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = getCurrentStep(uuid);

        if (currentStep == null || currentStep.isCompleted()) {
            return;
        }

        TutorialStep nextStep = currentStep.getNextStep();

        try {
            // 現在のステップの完了メッセージを送信
            sendStepCompleteMessage(player, currentStep);

            if (nextStep.isCompleted()) {
                // チュートリアル完了
                tutorialProgressDAO.markCompleted(uuid);
                playerProgressCache.put(uuid, TutorialStep.COMPLETED);
                sendCompletedMessage(player);
                giveCompletionReward(player);
            } else {
                // 次のステップへ
                tutorialProgressDAO.updateProgress(uuid, nextStep);
                playerProgressCache.put(uuid, nextStep);

                // 少し遅延してから次のステップメッセージを送信
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        sendStepMessage(player, nextStep);
                    }
                }, 40L); // 2秒後
            }
        } catch (SQLException e) {
            logger.warning("チュートリアル進行時にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * チュートリアルをスキップ
     * @param player プレイヤー
     * @param confirmed 確認済みかどうか
     * @return スキップが成功した場合true
     */
    public boolean skipTutorial(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();

        if (!confirmed) {
            String message = plugin.getConfig().getString("tutorial.messages.skip_confirm",
                    "&eチュートリアルをスキップしますか？ &f/tutorial skip confirm &eで確定");
            player.sendMessage(colorize(message));
            return false;
        }

        try {
            if (!tutorialProgressDAO.hasProgress(uuid)) {
                tutorialProgressDAO.createProgress(uuid, TutorialStep.COMPLETED);
            }
            tutorialProgressDAO.markSkipped(uuid);
            playerProgressCache.put(uuid, TutorialStep.COMPLETED);

            String successMessage = plugin.getConfig().getString("tutorial.messages.skip_success",
                    "&aチュートリアルをスキップしました。");
            String noteMessage = plugin.getConfig().getString("tutorial.messages.skip_note",
                    "&7いつでも &f/tutorial restart &7でやり直せます。");
            player.sendMessage(colorize(successMessage));
            player.sendMessage(colorize(noteMessage));
            return true;
        } catch (SQLException e) {
            logger.warning("チュートリアルスキップ時にエラーが発生しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * チュートリアルをリスタート
     * @param player プレイヤー
     */
    public void restartTutorial(Player player) {
        UUID uuid = player.getUniqueId();

        try {
            // プレイヤーの現在の状態に応じて開始ステップを決定
            TutorialStep startStep = determineStartingStep(player);

            if (tutorialProgressDAO.hasProgress(uuid)) {
                tutorialProgressDAO.resetProgress(uuid);
                tutorialProgressDAO.updateProgress(uuid, startStep);
            } else {
                tutorialProgressDAO.createProgress(uuid, startStep);
            }
            playerProgressCache.put(uuid, startStep);

            String message = plugin.getConfig().getString("tutorial.messages.restart_success",
                    "&aチュートリアルを最初からやり直します。");
            player.sendMessage(colorize(message));

            // 開始メッセージを送信
            sendStartMessage(player);

            // 少し遅延してから開始ステップメッセージを送信
            final TutorialStep finalStartStep = startStep;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    sendStepMessage(player, finalStartStep);
                }
            }, 60L);
        } catch (SQLException e) {
            logger.warning("チュートリアルリスタート時にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * プレイヤーの現在の状態に応じて開始すべきステップを決定
     * 既に条件を満たしているステップはスキップ
     */
    private TutorialStep determineStartingStep(Player player) {
        // ステップ1: 職業に就いているかチェック
        var jobManager = plugin.getJobManager();
        if (jobManager != null) {
            var playerJobs = jobManager.getPlayerJobs(player);
            if (playerJobs != null && !playerJobs.isEmpty()) {
                // 既に職業に就いている → ステップ2から開始
                return TutorialStep.FIRST_INCOME;
            }
        }

        // デフォルト: ステップ1から開始
        return TutorialStep.JOB_SELECT;
    }

    /**
     * 管理者による他プレイヤーのチュートリアルリセット
     * @param admin 管理者
     * @param targetUuid 対象プレイヤーUUID
     * @param targetName 対象プレイヤー名
     */
    public void adminResetTutorial(Player admin, UUID targetUuid, String targetName) {
        try {
            if (tutorialProgressDAO.hasProgress(targetUuid)) {
                tutorialProgressDAO.resetProgress(targetUuid);
            } else {
                tutorialProgressDAO.createProgress(targetUuid, TutorialStep.JOB_SELECT);
            }
            playerProgressCache.put(targetUuid, TutorialStep.JOB_SELECT);

            String message = plugin.getConfig().getString("tutorial.messages.admin_reset_success",
                    "&a%player%さんのチュートリアルをリセットしました。");
            admin.sendMessage(colorize(message.replace("%player%", targetName)));
        } catch (SQLException e) {
            logger.warning("チュートリアルリセット時にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * 現在のステップを取得
     * @param uuid プレイヤーUUID
     * @return 現在のステップ
     */
    public TutorialStep getCurrentStep(UUID uuid) {
        // キャッシュから取得
        if (playerProgressCache.containsKey(uuid)) {
            return playerProgressCache.get(uuid);
        }

        // DBから取得
        try {
            TutorialStep step = tutorialProgressDAO.getProgress(uuid);
            playerProgressCache.put(uuid, step);
            return step;
        } catch (SQLException e) {
            logger.warning("チュートリアル進捗取得時にエラーが発生しました: " + e.getMessage());
            return TutorialStep.NOT_STARTED;
        }
    }

    /**
     * チュートリアルが完了しているか確認
     * @param uuid プレイヤーUUID
     * @return 完了している場合true
     */
    public boolean isTutorialCompleted(UUID uuid) {
        TutorialStep step = getCurrentStep(uuid);
        return step != null && step.isCompleted();
    }

    /**
     * 進捗表示を送信
     * @param player プレイヤー
     */
    public void sendProgressDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = getCurrentStep(uuid);

        if (currentStep == TutorialStep.NOT_STARTED) {
            String message = plugin.getConfig().getString("tutorial.messages.not_started",
                    "&cチュートリアルがまだ開始されていません。");
            player.sendMessage(colorize(message));
            return;
        }

        if (currentStep.isCompleted()) {
            String message = plugin.getConfig().getString("tutorial.messages.already_completed",
                    "&eチュートリアルは既に完了しています。");
            player.sendMessage(colorize(message));
            return;
        }

        // ヘッダー
        String header = plugin.getConfig().getString("tutorial.messages.progress.header",
                "&6▬▬▬▬▬▬▬▬ チュートリアル進捗 ▬▬▬▬▬▬▬▬");
        player.sendMessage(colorize(header));

        // 現在のステップ
        String currentStepMsg = plugin.getConfig().getString("tutorial.messages.progress.current_step",
                "&e現在のステップ: &f%step%");
        player.sendMessage(colorize(currentStepMsg.replace("%step%", currentStep.getDisplayName())));

        // 各ステップの状態
        String completeFormat = plugin.getConfig().getString("tutorial.messages.progress.step_complete",
                "&a✓ %step_name% - 完了");
        String currentFormat = plugin.getConfig().getString("tutorial.messages.progress.step_current",
                "&e→ %step_name% - 進行中");
        String pendingFormat = plugin.getConfig().getString("tutorial.messages.progress.step_pending",
                "&7○ %step_name% - 未完了");

        TutorialStep[] steps = {TutorialStep.JOB_SELECT, TutorialStep.FIRST_INCOME,
                TutorialStep.BANK_NPC, TutorialStep.TRADING_NPC};

        for (TutorialStep step : steps) {
            String format;
            if (step.getOrder() < currentStep.getOrder()) {
                format = completeFormat;
            } else if (step == currentStep) {
                format = currentFormat;
            } else {
                format = pendingFormat;
            }
            player.sendMessage(colorize(format.replace("%step_name%", step.getDisplayName())));
        }
    }

    /**
     * 開始メッセージを送信
     */
    private void sendStartMessage(Player player) {
        List<String> messages = plugin.getConfig().getStringList("tutorial.messages.start");
        for (String message : messages) {
            player.sendMessage(colorize(message.replace("%player%", player.getName())));
        }
    }

    /**
     * ステップメッセージを送信
     */
    private void sendStepMessage(Player player, TutorialStep step) {
        String configKey = "tutorial.messages." + step.getConfigKey().replace("tutorial.", "");

        String title = plugin.getConfig().getString(configKey + ".title", "");
        if (!title.isEmpty()) {
            player.sendMessage(colorize(title));
        }

        List<String> description = plugin.getConfig().getStringList(configKey + ".description");
        for (String line : description) {
            player.sendMessage(colorize(line));
        }
    }

    /**
     * ステップ完了メッセージを送信
     */
    private void sendStepCompleteMessage(Player player, TutorialStep step) {
        String configKey = "tutorial.messages." + step.getConfigKey().replace("tutorial.", "") + ".complete";
        String message = plugin.getConfig().getString(configKey, "");
        if (!message.isEmpty()) {
            player.sendMessage(colorize(message));
        }
    }

    /**
     * 完了メッセージを送信
     */
    private void sendCompletedMessage(Player player) {
        List<String> messages = plugin.getConfig().getStringList("tutorial.messages.completed");
        for (String message : messages) {
            player.sendMessage(colorize(message));
        }
    }

    /**
     * 完了報酬を付与
     */
    private void giveCompletionReward(Player player) {
        boolean rewardEnabled = plugin.getConfig().getBoolean("tutorial.completion_rewards.enabled", true);
        if (!rewardEnabled) {
            return;
        }

        double money = plugin.getConfig().getDouble("tutorial.completion_rewards.money", 50.0);
        if (money > 0) {
            try {
                // プレイヤーに報酬を付与
                var playerDAO = plugin.getPlayerDAO();
                var playerData = playerDAO.getPlayer(player.getUniqueId());
                if (playerData != null) {
                    double newBalance = playerData.getBalance() + money;
                    playerDAO.updateBalance(player.getUniqueId(), newBalance);

                    String rewardMessage = plugin.getConfig().getString("tutorial.messages.completed_reward",
                            "&6報酬: &f%money%%currency% &6を獲得しました！");
                    rewardMessage = rewardMessage
                            .replace("%money%", String.format("%.0f", money))
                            .replace("%currency%", configManager.getCurrencyName());
                    player.sendMessage(colorize(rewardMessage));
                }
            } catch (SQLException e) {
                logger.warning("チュートリアル報酬付与時にエラーが発生しました: " + e.getMessage());
            }
        }
    }

    /**
     * プレイヤーのキャッシュをクリア（ログアウト時に呼び出す）
     */
    public void clearPlayerCache(UUID uuid) {
        playerProgressCache.remove(uuid);
    }

    /**
     * カラーコードを適用
     */
    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
