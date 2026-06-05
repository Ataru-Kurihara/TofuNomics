package org.tofu.tofunomics.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーのスコアボード表示・更新を管理するクラス
 */
public class ScoreboardManager implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final CurrencyConverter currencyConverter;
    private final JobManager jobManager;
    
    // プレイヤーのスコアボード表示設定を保存
    private final Map<UUID, Boolean> scoreboardEnabled = new HashMap<>();
    
    // 定期更新タスク
    private BukkitTask updateTask;
    
    public ScoreboardManager(TofuNomics plugin, ConfigManager configManager, 
                           PlayerDAO playerDAO, CurrencyConverter currencyConverter, 
                           JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.currencyConverter = currencyConverter;
        this.jobManager = jobManager;
        
        startUpdateTask();
    }
    
    /**
     * プレイヤーのスコアボード表示を有効にする
     */
    public void enableScoreboard(Player player) {
        // ワールド制限チェックを追加
        if (!isScoreboardEnabledInCurrentWorld(player)) {
            return;
        }
        scoreboardEnabled.put(player.getUniqueId(), true);
        updatePlayerScoreboard(player);
    }
    
    /**
     * プレイヤーのスコアボード表示を無効にする
     */
    public void disableScoreboard(Player player) {
        scoreboardEnabled.put(player.getUniqueId(), false);
        // 対象ワールド外ではTofuNomicsのスコアボードを一切表示しない（ヒントも含めてクリア）
        if (!isScoreboardEnabledInCurrentWorld(player)) {
            clearScoreboard(player);
            return;
        }
        // 対象ワールド内でユーザーが手動で非表示にした場合のみヒントスコアボードを表示
        showHintScoreboard(player);
    }

    /**
     * プレイヤーのスコアボードをメインスコアボードに戻す（サイドバー表示を消す）
     */
    private void clearScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * プレイヤーにヒントスコアボードを表示
     * スコアボード非表示時に、再表示方法を示す簡易スコアボードを提供
     */
    private void showHintScoreboard(Player player) {
        try {
            // 新しいスコアボードを作成
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

            // ヒントタイトルを取得・適用
            String hintTitle = ChatColor.translateAlternateColorCodes('&',
                                    configManager.getScoreboardHintTitle());
            Objective objective = scoreboard.registerNewObjective(
                                    "tofunomics_hint", "dummy", hintTitle);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            // ヒント表示行を取得
            List<String> hintLines = configManager.getScoreboardHintLines();

            // スコアを設定（下から上の順番で表示されるため逆順でセット）
            int score = hintLines.size();
            for (String line : hintLines) {
                // カラーコード変換
                String coloredLine = ChatColor.translateAlternateColorCodes('&', line);
                // 空行の場合はスペースで対応（Bukkit仕様）
                if (coloredLine.isEmpty()) {
                    coloredLine = " ";
                }
                objective.getScore(coloredLine).setScore(score--);
            }

            // プレイヤーにスコアボードを適用
            player.setScoreboard(scoreboard);

        } catch (Exception e) {
            // エラー時はメインスコアボードにフォールバック
            plugin.getLogger().warning("Failed to show hint scoreboard for player "
                                       + player.getName() + ": " + e.getMessage());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * プレイヤーのスコアボード表示設定を切り替える
     */
    public boolean toggleScoreboard(Player player) {
        boolean currentState = isScoreboardEnabled(player);
        if (currentState) {
            disableScoreboard(player);
        } else {
            enableScoreboard(player);
        }
        return !currentState;
    }
    
    /**
     * プレイヤーのスコアボード表示設定を確認
     */
    public boolean isScoreboardEnabled(Player player) {
        return scoreboardEnabled.getOrDefault(player.getUniqueId(), 
                configManager.isScoreboardDefaultEnabled());
    }
    
    /**
     * プレイヤーのスコアボードを更新
     */
    public void updatePlayerScoreboard(Player player) {
        if (!isScoreboardEnabled(player)) {
            return;
        }
        
        // ワールド制限チェックを追加
        if (!isScoreboardEnabledInCurrentWorld(player)) {
            // 対象ワールド外の場合はスコアボードを無効にする
            disableScoreboard(player);
            return;
        }
        
        try {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("tofunomics", "dummy", 
                    ChatColor.translateAlternateColorCodes('&', configManager.getScoreboardTitle()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
            // プレイヤーデータを取得
        org.tofu.tofunomics.models.Player playerData;
        try {
            playerData = playerDAO.getPlayer(player.getUniqueId());
            if (playerData == null) {
                // プレイヤーデータが存在しない場合はスキップ（警告レベルを下げる）
                plugin.getLogger().fine("Player data not found for scoreboard: " + player.getName());
                return;
            }
        } catch (java.sql.SQLException e) {
            // エラーログを出力してメソッドを終了（頻繁すぎるログを防ぐ）
            plugin.getLogger().warning("Failed to get player data for scoreboard (" + player.getName() + "): " + e.getMessage());
            return;
        }
        
            // 職業情報を取得
            PlayerJob currentJob = jobManager.getCurrentJob(player.getUniqueId());
            String jobInfo = "なし";
            String levelInfo = "";
            String experienceInfo = "";
        
            if (currentJob != null) {
                Job jobData = jobManager.getJobById(currentJob.getJobId());
                if (jobData != null) {
                    String jobTitle = jobManager.getJobTitle(currentJob.getJobId(), currentJob.getLevel());
                    jobInfo = jobData.getName();
                    levelInfo = "Lv." + currentJob.getLevel() + " " + jobTitle;
                    
                    // 次レベルまでの経験値計算
                    double requiredExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel() + 1);
                    double currentExp = currentJob.getExperience();
                    double prevLevelExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel());
                    
                    if (currentJob.getLevel() >= configManager.getMaxJobLevel()) {
                        experienceInfo = "MAX";
                    } else {
                        double progress = ((currentExp - prevLevelExp) / (requiredExp - prevLevelExp)) * 100;
                        experienceInfo = String.format("%.1f%%", progress);
                    }
                }
            }
        
            // 現金・預金情報を分けて取得
            double cashBalance = currencyConverter.getCashBalance(player);
            double bankBalance = currencyConverter.getBankBalance(player);
            String currencySymbol = configManager.getCurrencySymbol();
            
            String cashText = currencyConverter.formatCurrency(cashBalance) + currencySymbol;
            String bankText = currencyConverter.formatCurrency(bankBalance) + currencySymbol;
            
            // オンライン時間（分単位で計算）
            long onlineTime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60; // tick -> minutes
            String onlineTimeText = formatTime(onlineTime);
            
            // Minecraft時間を取得して表示用にフォーマット
            String currentTimeText = "";
            String tradingStatusText = "";
            boolean showCurrentTime = configManager.isScoreboardShowCurrentTime();
            boolean showTradingHours = configManager.isScoreboardShowTradingHours();
            
            if (showCurrentTime || showTradingHours) {
                long worldTime = player.getWorld().getTime();
                int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
                int currentMinute = (int) (((worldTime + 6000) % 1000) / 1000.0 * 60);
                currentTimeText = String.format("%02d:%02d", currentHour, currentMinute);
                
                // 取引時間の判定
                if (showTradingHours && configManager.isTradingHoursEnabled()) {
                    int startHour = configManager.getTradingStartHour();
                    int endHour = configManager.getTradingEndHour();
                    boolean isWithinTradingHours;
                    
                    if (startHour <= endHour) {
                        isWithinTradingHours = currentHour >= startHour && currentHour < endHour;
                    } else {
                        isWithinTradingHours = currentHour >= startHour || currentHour < endHour;
                    }
                    
                    if (isWithinTradingHours) {
                        tradingStatusText = ChatColor.GREEN + "営業中";
                    } else {
                        tradingStatusText = ChatColor.RED + "閉店中";
                    }
                }
            }
            
            // スコアを設定（下から上の順番で表示される）
            int score = 10;
        
            // 空行を追加してレイアウトを整える
            objective.getScore(ChatColor.WHITE + " ").setScore(score--);
            
            // 時刻表示
            if (showCurrentTime) {
                objective.getScore(ChatColor.AQUA + "⏰ 時刻: " + ChatColor.WHITE + currentTimeText).setScore(score--);
            }
            
            // 取引時間表示
            if (showTradingHours && !tradingStatusText.isEmpty()) {
                objective.getScore(ChatColor.GOLD + "💼 取引: " + tradingStatusText).setScore(score--);
            }
            
            // 職業経験値情報
            if (configManager.isScoreboardShowExperience() && !experienceInfo.isEmpty()) {
                objective.getScore(ChatColor.YELLOW + "次レベル: " + ChatColor.WHITE + experienceInfo).setScore(score--);
            }
            
            // 職業レベル
            if (configManager.isScoreboardShowJobLevel() && !levelInfo.isEmpty()) {
                objective.getScore(ChatColor.GREEN + levelInfo).setScore(score--);
            }
            
            // 職業名
            if (configManager.isScoreboardShowJob()) {
                objective.getScore(ChatColor.AQUA + "職業: " + ChatColor.WHITE + jobInfo).setScore(score--);
            }
            
            // 預金残高
            if (configManager.isScoreboardShowBalance()) {
                objective.getScore(ChatColor.GOLD + "預金: " + ChatColor.WHITE + bankText).setScore(score--);
            }
            
            // 現金残高（金塊）
            if (configManager.isScoreboardShowBalance()) {
                objective.getScore(ChatColor.GREEN + "現金: " + ChatColor.WHITE + cashText).setScore(score--);
            }
        
            // プレイヤー名
            if (configManager.isScoreboardShowPlayerName()) {
                objective.getScore(ChatColor.YELLOW + player.getName()).setScore(score--);
            }
            
            // ルールコマンド表示
            if (configManager.isScoreboardShowRulesCommand()) {
                // 空行を追加して視認性向上
                objective.getScore(ChatColor.WHITE + "  ").setScore(score--);
                // ルールコマンドテキスト
                String rulesCommandText = ChatColor.translateAlternateColorCodes('&', configManager.getScoreboardRulesCommandText());
                objective.getScore(rulesCommandText).setScore(score--);
            }

            // トグルヒント表示
            if (configManager.isScoreboardShowToggleHint()) {
                // トグルヒントテキスト
                String toggleHintText = ChatColor.translateAlternateColorCodes('&',
                        configManager.getScoreboardToggleHintText());
                objective.getScore(toggleHintText).setScore(score--);
            }

            player.setScoreboard(scoreboard);
            
        } catch (Exception e) {
            // スコアボード作成・更新中のエラーをキャッチ
            plugin.getLogger().warning("Failed to update scoreboard for player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * 職業レベルをプレイヤーのバニラ経験値バーに反映する
     * - 対象ワールド外、または職業なしの場合はバーを0にリセット（職業レベルの残留防止）
     * - 職業ありの場合はレベル数字＝職業レベル、バー進捗＝次レベルまでの達成率
     */
    public void updateExperienceBar(Player player) {
        if (!configManager.isVanillaExpBarEnabled()) {
            return;
        }

        try {
            // 対象ワールド外では職業レベルを表示しない
            if (!isScoreboardEnabledInCurrentWorld(player)) {
                resetExperienceBar(player);
                return;
            }

            PlayerJob currentJob = jobManager.getCurrentJob(player.getUniqueId());
            if (currentJob == null) {
                resetExperienceBar(player);
                return;
            }

            // 進捗率（0.0〜1.0）を計算
            double currentExp = currentJob.getExperience();
            double prevLevelExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel());
            double requiredExp = PlayerJob.calculateExperienceRequired(currentJob.getLevel() + 1);

            float progress;
            if (currentJob.getLevel() >= configManager.getMaxJobLevel() || requiredExp <= prevLevelExp) {
                // 最大レベル到達時はバーを満タンにする
                progress = 1.0f;
            } else {
                progress = (float) ((currentExp - prevLevelExp) / (requiredExp - prevLevelExp));
            }

            // バニラ仕様の範囲（0.0〜1.0）に収める。1.0は次レベル扱いになるため僅かに下げる
            progress = Math.max(0.0f, Math.min(0.9999f, progress));

            player.setLevel(currentJob.getLevel());
            player.setExp(progress);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update experience bar for player "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * バニラ経験値バーを0にリセットする
     */
    private void resetExperienceBar(Player player) {
        player.setLevel(0);
        player.setExp(0.0f);
    }

    /**
     * 時間をフォーマット（分 -> 時間:分）
     */
    private String formatTime(long minutes) {
        if (minutes < 60) {
            return minutes + "分";
        }
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        if (hours < 24) {
            return hours + "時間" + remainingMinutes + "分";
        }
        
        long days = hours / 24;
        long remainingHours = hours % 24;
        return days + "日" + remainingHours + "時間";
    }
    
    /**
     * 定期更新タスクを開始
     */
    private void startUpdateTask() {
        int updateInterval = configManager.getScoreboardUpdateInterval();
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // バニラ経験値バーはスコアボード表示のON/OFFと無関係に常時更新する
                    updateExperienceBar(player);
                    if (isScoreboardEnabled(player)) {
                        updatePlayerScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, updateInterval * 20L); // 秒をtickに変換（同期処理）
    }
    
    /**
     * プレイヤー参加時の処理
     */
    public void onPlayerJoin(Player player) {
        // デフォルト設定に基づいてスコアボードを表示（ワールド制限を考慮）
        if (configManager.isScoreboardDefaultEnabled() && isScoreboardEnabledInCurrentWorld(player)) {
            enableScoreboard(player);
        }
    }
    
    /**
     * プレイヤーがワールドを変更した時の処理
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // ワールド移動時に経験値バーを即時反映/リセットする
        updateExperienceBar(player);

        if (isScoreboardEnabledInCurrentWorld(player)) {
            // 対象ワールドに入った場合、スコアボードが有効なら表示する
            if (configManager.isScoreboardDefaultEnabled() || scoreboardEnabled.getOrDefault(player.getUniqueId(), false)) {
                enableScoreboard(player);
            }
        } else {
            // 対象ワールド外に出た場合、スコアボードを無効にする
            if (isScoreboardEnabled(player)) {
                disableScoreboard(player);
            }
        }
    }
    
    /**
     * プレイヤー退出時の処理
     */
    public void onPlayerQuit(Player player) {
        scoreboardEnabled.remove(player.getUniqueId());
        // 退出時にバニラ経験値バーを0に戻して残留を防ぐ
        if (configManager.isVanillaExpBarEnabled()) {
            resetExperienceBar(player);
        }
    }
    
    /**
     * 全てのプレイヤーのスコアボードを更新
     */
    public void updateAllScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isScoreboardEnabled(player)) {
                updatePlayerScoreboard(player);
            }
        }
    }
    
    /**
     * スコアボードマネージャーの終了処理
     */
    public void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        
        // 全プレイヤーのスコアボードをデフォルトに戻す
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // バニラ経験値バーも0に戻す（職業レベルの残留防止）
            if (configManager.isVanillaExpBarEnabled()) {
                resetExperienceBar(player);
            }
        }
        
        scoreboardEnabled.clear();
    }
    
    /**
     * プレイヤーの現在のワールドでスコアボードが有効かどうかを確認
     */
    private boolean isScoreboardEnabledInCurrentWorld(Player player) {
        String worldName = player.getWorld().getName();
        return configManager.isScoreboardEnabledInWorld(worldName);
    }
}