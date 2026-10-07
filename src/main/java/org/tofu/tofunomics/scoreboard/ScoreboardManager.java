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

    /** DB から読む値（職業・レベル・銀行残高）を使い回す時間 */
    static final long STATUS_CACHE_MILLIS = 5000L;

    // プレイヤーごとのサイドバー。一度作ったものを使い回し、変わった行だけ書き換える
    // （以前は毎秒、全員ぶんのスコアボードを作り直していた）
    private final Map<UUID, PlayerSidebar> sidebars = new HashMap<>();
    // DB から読む値の写し。毎秒の更新では DB を読まず、数秒に 1 回だけ読み直す
    private final TimedCache<UUID, PlayerStatusSnapshot> statusCache = new TimedCache<>(STATUS_CACHE_MILLIS);

    /**
     * 職業・レベル・銀行残高が変わったと分かったときに呼ぶ。次の更新で DB から読み直す。
     */
    public void invalidateStatus(UUID uuid) {
        statusCache.invalidate(uuid);
    }

    /**
     * DB から読む値の写しを返す。プレイヤーデータが無いときは null。
     */
    private PlayerStatusSnapshot loadStatus(Player player) {
        UUID uuid = player.getUniqueId();
        return statusCache.get(uuid, () -> {
            try {
                if (playerDAO.getPlayer(uuid) == null) {
                    // プレイヤーデータが存在しない場合はスキップ（警告レベルを下げる）
                    plugin.getLogger().fine("Player data not found for scoreboard: " + player.getName());
                    return null;
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("Failed to get player data for scoreboard: " + e.getMessage());
                return null;
            }
            return PlayerStatusSnapshot.load(uuid, jobManager, configManager,
                    currencyConverter.getBankBalance(player));
        });
    }
    
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
        sidebars.remove(player.getUniqueId());
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

            // プレイヤーにスコアボードを適用（通常のサイドバーは捨て、再表示のときに作り直す）
            sidebars.remove(player.getUniqueId());
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
     * プレイヤーのスコアボードを更新（値が変わった直後に呼ぶ用。DB から読み直して反映する）
     */
    public void updatePlayerScoreboard(Player player) {
        invalidateStatus(player.getUniqueId());
        renderPlayerScoreboard(player);
    }

    /**
     * プレイヤーのスコアボードを描く（定期更新用。DB は数秒に 1 回だけ読む）
     */
    private void renderPlayerScoreboard(Player player) {
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
            // DB から読む値（数秒のあいだ使い回す）
            PlayerStatusSnapshot status = loadStatus(player);
            if (status == null) {
                return;
            }

            // 表示する行（上から順）
            List<String> lines = new java.util.ArrayList<>();

            // 職業情報
            String jobDisplayName = status.jobDisplayName;
            String levelInfo = "";
            String experienceInfo = "";
            if (status.hasJob) {
                levelInfo = "Lv." + status.level + " " + status.jobTitle;
                // 次レベルまでの経験値計算
                // レベル上限は職業別設定(既定75)を使用する。getMaxJobLevel()(=100固定)
                // ではないことに注意(JobLevelBossBarManager と共通)。
                if (status.isMaxLevel()) {
                    experienceInfo = "MAX";
                } else {
                    // [0,100]クランプ＋0除算ガード付きの共通ヘルパーで計算(負値・100超え・NaN防止)
                    double progress = PlayerJob.calculateLevelProgressPercent(status.level, status.experience);
                    experienceInfo = String.format("%.1f%%", progress);
                }
            }
        
            // 現金・預金情報を分けて取得
            double cashBalance = currencyConverter.getCashBalance(player);
            double bankBalance = status.bankBalance;
            String currencySymbol = configManager.getCurrencySymbol();
            
            String cashText = currencyConverter.formatCurrency(cashBalance) + " " + currencySymbol;
            String bankText = currencyConverter.formatCurrency(bankBalance) + " " + currencySymbol;
            
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
            
            // 行を上から順に並べる
        
            // 空行を追加してレイアウトを整える
            lines.add(ChatColor.WHITE + " ");
            
            // 時刻表示
            if (showCurrentTime) {
                lines.add(ChatColor.AQUA + "⏰ 時刻: " + ChatColor.WHITE + currentTimeText);
            }
            
            // 取引時間表示
            if (showTradingHours && !tradingStatusText.isEmpty()) {
                lines.add(ChatColor.GOLD + "💼 取引: " + tradingStatusText);
            }

            // 中心都市の距離・方角表示
            if (configManager.isScoreboardShowCenterCity()) {
                String centerCityText = buildCenterCityLine(player);
                if (centerCityText != null) {
                    lines.add(centerCityText);
                }
            }
            
            // 職業経験値情報
            if (configManager.isScoreboardShowExperience() && !experienceInfo.isEmpty()) {
                lines.add(ChatColor.YELLOW + "次レベル: " + ChatColor.WHITE + experienceInfo);
            }
            
            // 職業レベル
            if (configManager.isScoreboardShowJobLevel() && !levelInfo.isEmpty()) {
                lines.add(ChatColor.GREEN + levelInfo);
            }
            
            // 職業名
            if (configManager.isScoreboardShowJob()) {
                lines.add(ChatColor.AQUA + "職業: " + ChatColor.WHITE + jobDisplayName);
            }
            
            // 預金残高
            if (configManager.isScoreboardShowBalance()) {
                lines.add(ChatColor.GOLD + "預金: " + ChatColor.WHITE + bankText);
            }
            
            // 現金残高（金塊）
            if (configManager.isScoreboardShowBalance()) {
                lines.add(ChatColor.GREEN + "現金: " + ChatColor.WHITE + cashText);
            }
        
            // プレイヤー名
            if (configManager.isScoreboardShowPlayerName()) {
                lines.add(ChatColor.YELLOW + player.getName());
            }
            
            // ルールコマンド表示
            if (configManager.isScoreboardShowRulesCommand()) {
                // 空行を追加して視認性向上
                lines.add(ChatColor.WHITE + "  ");
                // ルールコマンドテキスト
                String rulesCommandText = ChatColor.translateAlternateColorCodes('&', configManager.getScoreboardRulesCommandText());
                lines.add(rulesCommandText);
            }

            // トグルヒント表示
            if (configManager.isScoreboardShowToggleHint()) {
                // トグルヒントテキスト
                String toggleHintText = ChatColor.translateAlternateColorCodes('&',
                        configManager.getScoreboardToggleHintText());
                lines.add(toggleHintText);
            }

            // 一度作ったサイドバーを使い回し、変わった行だけ書き換える
            PlayerSidebar sidebar = sidebars.computeIfAbsent(player.getUniqueId(), uuid ->
                    new PlayerSidebar(ChatColor.translateAlternateColorCodes('&', configManager.getScoreboardTitle())));
            sidebar.show(lines);
            if (player.getScoreboard() != sidebar.getScoreboard()) {
                player.setScoreboard(sidebar.getScoreboard());
            }
            
        } catch (Exception e) {
            // スコアボード作成・更新中のエラーをキャッチ
            plugin.getLogger().warning("Failed to update scoreboard for player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    // 職業レベルの表示は JobLevelBossBarManager（BossBar）へ移設した。
    // XP バー（setLevel/setExp）には一切触れないことで、バニラ経験値を本来通り利用可能にしている。

    /**
     * 中心都市までの距離・方角の表示行を生成
     * 中心都市の基準座標は spawn_location を流用する
     * @return 表示行。プレイヤーが中心都市と別ワールドにいる場合はnull（行を出さない）
     */
    private String buildCenterCityLine(Player player) {
        String cityWorld = configManager.getSpawnWorldName();
        // 別ワールドでは距離・方角が計算できないため行を出さない
        if (!player.getWorld().getName().equals(cityWorld)) {
            return null;
        }
        double dx = configManager.getSpawnX() - player.getLocation().getX();
        double dz = configManager.getSpawnZ() - player.getLocation().getZ();
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        String direction = getCompassDirection(dx, dz);
        return ChatColor.LIGHT_PURPLE + "🏛 中心都市: "
             + ChatColor.WHITE + distance + "m " + direction;
    }

    /**
     * dx, dz から8方位（矢印＋名称）を算出
     * Minecraft座標系: +X=東, -X=西, +Z=南, -Z=北
     */
    private String getCompassDirection(double dx, double dz) {
        // 北を0とした時計回りの角度（度）
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(angle / 45.0) & 7;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String[] names  = {"北", "北東", "東", "南東", "南", "南西", "西", "北西"};
        return arrows[index] + names[index];
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
                    if (isScoreboardEnabled(player)) {
                        renderPlayerScoreboard(player);
                    }
                }
                // ログアウトした人のサイドバーと写しを片付ける
                sidebars.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
                statusCache.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
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
        sidebars.remove(player.getUniqueId());
        statusCache.invalidate(player.getUniqueId());
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
        }
        
        scoreboardEnabled.clear();
        sidebars.clear();
        statusCache.clear();
    }
    
    /**
     * プレイヤーの現在のワールドでスコアボードが有効かどうかを確認
     */
    private boolean isScoreboardEnabledInCurrentWorld(Player player) {
        String worldName = player.getWorld().getName();
        return configManager.isScoreboardEnabledInWorld(worldName);
    }
}