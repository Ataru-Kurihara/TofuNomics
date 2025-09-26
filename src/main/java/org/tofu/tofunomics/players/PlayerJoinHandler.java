package org.tofu.tofunomics.players;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.scoreboard.ScoreboardManager;

import java.util.List;
import java.util.logging.Logger;

/**
 * プレイヤー参加時の処理を管理するクラス
 * ワールドに入った時のウェルカムメッセージ、タイトル表示、初回特典などを処理
 */
public class PlayerJoinHandler implements Listener {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final ScoreboardManager scoreboardManager;
    private final Logger logger;
    
    public PlayerJoinHandler(JavaPlugin plugin, ConfigManager configManager, PlayerDAO playerDAO, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.scoreboardManager = scoreboardManager;
        this.logger = plugin.getLogger();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        logger.info("プレイヤー参加イベント開始: " + player.getName() + " ワールド: " + player.getWorld().getName());
        
        // スコアボード処理（同期処理）
        if (scoreboardManager != null) {
            scoreboardManager.onPlayerJoin(player);
        }
        
        // tofuNomicsワールドに最初から参加した場合のテレポート処理
        if (player.getWorld().getName().equals("tofuNomics")) {
            logger.info("プレイヤー " + player.getName() + " はtofuNomicsワールドに直接参加しました - ウェルカム処理を開始します");
            // 少し遅延してウェルカムメッセージとテレポートを実行
            WelcomeDisplayTask welcomeTask = new WelcomeDisplayTask(player);
            welcomeTask.runTaskLater(plugin, 40L); // 2秒後に実行
        }
        
        // 非同期でプレイヤーデータの初期化のみ実行
        PlayerDataInitializationTask task = new PlayerDataInitializationTask(player);
        task.runTaskAsynchronously(plugin);
    }
    
    /**
     * プレイヤーがワールドを変更した時の処理
     * TofuNomicsワールドに入った場合、ウェルカムメッセージと職業案内を表示
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String currentWorldName = player.getWorld().getName();
        
        // デバッグログ: ワールド変更を記録
        logger.info("プレイヤー " + player.getName() + " がワールドを変更しました: " + currentWorldName);
        
        // ロビーワールドなど、TofuNomics以外のワールドでは処理しない
        if (!currentWorldName.equals("tofuNomics")) {
            logger.info("プレイヤー " + player.getName() + " は TofuNomics 以外のワールド(" + currentWorldName + ")にいるため、処理をスキップします");
            return;
        }
        
        // 明示的にロビーワールドなどを除外
        if (currentWorldName.toLowerCase().contains("lobby") || 
            currentWorldName.toLowerCase().contains("spawn") ||
            currentWorldName.equals("world") ||
            currentWorldName.equals("world_nether") ||
            currentWorldName.equals("world_the_end")) {
            logger.info("プレイヤー " + player.getName() + " は除外対象のワールド(" + currentWorldName + ")にいるため、処理をスキップします");
            return;
        }
        
        logger.info("プレイヤー " + player.getName() + " がTofuNomicsワールドに入りました - ウェルカムメッセージを表示します");
        
        // TofuNomicsワールドでのウェルカムメッセージとタイトルを表示
        WelcomeDisplayTask welcomeTask = new WelcomeDisplayTask(player);
        welcomeTask.runTask(plugin);
    }
    
    /**
     * プレイヤーデータの初期化と更新処理
     */
    private void handlePlayerData(Player player) {
        try {
            // 既存プレイヤーかチェック
            org.tofu.tofunomics.models.Player existingPlayer = playerDAO.getPlayer(player.getUniqueId());
            
            if (existingPlayer == null) {
                // 新規プレイヤーの場合
                createNewPlayer(player);
                logger.info("新規プレイヤーを登録しました: " + player.getName());
                
                // 新規プレイヤーメッセージを表示するフラグを設定
                scheduleNewPlayerMessages(player);
            } else {
                // 復帰プレイヤーかチェック
                boolean isReturning = checkReturningPlayer(player);
                if (isReturning) {
                    scheduleWelcomeBackMessage(player);
                }
                
                // 既存プレイヤーの最終ログイン時間とプレイヤー名を更新
                updateLastLogin(player);
                updatePlayerName(player);
                logger.info("プレイヤーのログイン時間を更新しました: " + player.getName());
            }
        } catch (Exception e) {
            logger.severe("プレイヤーデータ処理中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 新規プレイヤーの作成
     */
    private void createNewPlayer(Player player) {
        try {
            // 設定から初期残高を取得
            double startingBalance = configManager.getStartingBalance();
            
            // 新しいプレイヤーオブジェクトを作成
            org.tofu.tofunomics.models.Player newPlayer = new org.tofu.tofunomics.models.Player(
                player.getUniqueId(),
                startingBalance
            );
            
            // データベースに保存
            playerDAO.insertPlayer(newPlayer);
            
        } catch (Exception e) {
            logger.severe("新規プレイヤー作成中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 最終ログイン時間の更新
     */
    private void updateLastLogin(Player player) {
        try {
            playerDAO.updateLastLogin(player.getUniqueId());
        } catch (Exception e) {
            logger.severe("ログイン時間更新中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * プレイヤー名の更新
     */
    private void updatePlayerName(Player player) {
        try {
            playerDAO.updatePlayerName(player.getUniqueId(), player.getName());
        } catch (Exception e) {
            logger.severe("プレイヤー名更新中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 復帰プレイヤーかチェック
     */
    private boolean checkReturningPlayer(Player player) {
        try {
            if (!configManager.isWelcomeBackMessageEnabled()) {
                return false;
            }
            int threshold = configManager.getWelcomeBackDays();
            return playerDAO.isReturningPlayer(player.getUniqueId(), threshold);
        } catch (Exception e) {
            logger.warning("復帰プレイヤーチェック中にエラー: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 新規プレイヤーメッセージのスケジュール
     */
    private void scheduleNewPlayerMessages(Player player) {
        if (!configManager.isNewPlayerBonusEnabled()) {
            return;
        }
        
        NewPlayerBonusTask bonusTask = new NewPlayerBonusTask(player);
        bonusTask.runTaskLater(plugin, 120L); // 6秒後に表示（職業案内の後）
    }
    
    /**
     * 復帰プレイヤーメッセージのスケジュール
     */
    private void scheduleWelcomeBackMessage(Player player) {
        WelcomeBackMessageTask welcomeBackTask = new WelcomeBackMessageTask(player);
        welcomeBackTask.runTaskLater(plugin, 100L); // 5秒後に表示
    }
    
    /**
     * ウェルカムメッセージの表示
     */
    private void displayWelcomeMessages(Player player) {
        try {
            // 設定からメッセージを取得
            List<String> welcomeMessages = configManager.getWelcomeMessages();
            
            if (welcomeMessages != null && !welcomeMessages.isEmpty()) {
                // 少し間隔をあけてメッセージを表示
                for (int i = 0; i < welcomeMessages.size(); i++) {
                    final String message = welcomeMessages.get(i);
                    
                    WelcomeMessageTask messageTask = new WelcomeMessageTask(player, message);
                    messageTask.runTaskLater(plugin, i * 20L); // 1秒間隔で表示
                }
            }
            
            // 職業案内メッセージ
            displayJobGuideMessage(player);
            
        } catch (Exception e) {
            logger.warning("ウェルカムメッセージ表示中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 職業案内メッセージの表示
     */
    private void displayJobGuideMessage(Player player) {
        JobGuideMessageTask jobGuideTask = new JobGuideMessageTask(player);
        jobGuideTask.runTaskLater(plugin, 80L); // 4秒後に表示
    }
    
    /**
     * ウェルカムタイトルの表示
     */
    private void displayWelcomeTitle(Player player) {
        try {
            String title = configManager.getWelcomeTitle();
            String subtitle = configManager.getWelcomeSubtitle();
            
            if (title != null && !title.isEmpty()) {
                // プレースホルダーを置換
                title = formatMessage(title, player);
                subtitle = formatMessage(subtitle, player);
                
                // タイトルを表示（フェードイン1秒、表示3秒、フェードアウト1秒）
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    20, 60, 20
                );
            }
        } catch (Exception e) {
            logger.warning("タイトル表示中にエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
    
    
    /**
     * スポーン座標にテレポート
     */
    private void teleportToSpawn(Player player) {
        try {
            logger.info("=== テレポート処理開始 ===");
            logger.info("プレイヤー: " + player.getName());
            logger.info("現在のワールド: " + player.getWorld().getName());
            logger.info("現在の座標: " + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
            
            // スポーン座標機能が有効でない場合はスキップ
            boolean isEnabled = configManager.isSpawnLocationEnabled();
            logger.info("スポーン座標機能の有効状態: " + isEnabled);
            if (!isEnabled) {
                logger.info("スポーン座標機能が無効のため、テレポートをスキップします");
                return;
            }
            
            // 設定からスポーン座標を取得
            String worldName = configManager.getSpawnWorldName();
            int x = configManager.getSpawnX();
            int y = configManager.getSpawnY();
            int z = configManager.getSpawnZ();
            int delay = configManager.getSpawnTeleportDelay();
            
            logger.info("スポーン座標設定 - ワールド: " + worldName + ", 座標: (" + x + ", " + y + ", " + z + "), 遅延: " + delay + " tick");
            
            // ワールドを取得
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("スポーン座標のワールドが見つかりません: " + worldName);
                return;
            }
            
            // スポーン座標を作成
            Location spawnLocation = new Location(world, x + 0.5, y, z + 0.5);
            
            // 遅延付きでテレポート実行
            SpawnTeleportTask teleportTask = new SpawnTeleportTask(player, spawnLocation, x, y, z, worldName);
            teleportTask.runTaskLater(plugin, delay);
            
        } catch (Exception e) {
            logger.warning("スポーン座標へのテレポート中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * メッセージのプレースホルダーを置換
     */
    private String formatMessage(String message, Player player) {
        if (message == null) return "";
        
        return message
            .replace("%player%", player.getName())
            .replace("%displayname%", player.getDisplayName())
            .replace("%world%", player.getWorld().getName())
            .replace("%currency%", configManager.getCurrencyName())
            .replace("%starting_balance%", String.valueOf(configManager.getStartingBalance()));
    }
    
    // 名前付き内部クラス
    private class PlayerDataInitializationTask extends BukkitRunnable {
        private final Player player;
        
        public PlayerDataInitializationTask(Player player) {
            this.player = player;
        }
        
        @Override
        public void run() {
            try {
                handlePlayerData(player);
            } catch (Exception e) {
                logger.warning("プレイヤー参加時データ処理中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private class WelcomeDisplayTask extends BukkitRunnable {
        private final Player player;
        
        public WelcomeDisplayTask(Player player) {
            this.player = player;
        }
        
        @Override
        public void run() {
            try {
                if (player.isOnline()) {
                    displayWelcomeMessages(player);
                    displayWelcomeTitle(player);
                    // スポーン座標へのテレポート処理を追加
                    teleportToSpawn(player);
                }
            } catch (Exception e) {
                logger.warning("TofuNomicsワールド処理中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private class NewPlayerBonusTask extends BukkitRunnable {
        private final Player player;
        
        public NewPlayerBonusTask(Player player) {
            this.player = player;
        }
        
        @Override
        public void run() {
            if (!player.isOnline()) return;
            
            // 新規プレイヤーボーナス付与
            double bonusAmount = configManager.getNewPlayerBonusAmount();
            if (bonusAmount > 0) {
                try {
                    org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayer(player.getUniqueId());
                    if (tofuPlayer != null) {
                        tofuPlayer.addBalance(bonusAmount);
                        playerDAO.updatePlayerData(tofuPlayer);
                    }
                } catch (Exception e) {
                    logger.warning("新規プレイヤーボーナス付与中にエラー: " + e.getMessage());
                }
            }
            
            // 新規プレイヤーメッセージ表示
            List<String> newPlayerMessages = configManager.getNewPlayerMessages();
            if (newPlayerMessages != null && !newPlayerMessages.isEmpty()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&6▬▬▬▬▬▬ 新規プレイヤー特典 ▬▬▬▬▬▬"));
                
                for (String message : newPlayerMessages) {
                    String formattedMessage = formatMessage(message, player);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedMessage));
                }
                
                if (bonusAmount > 0) {
                    player.sendMessage(ChatColor.GREEN + "✦ 新規プレイヤーボーナス: " + 
                                     bonusAmount + configManager.getCurrencyName() + " を獲得しました！");
                }
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            }
        }
    }
    
    private class WelcomeBackMessageTask extends BukkitRunnable {
        private final Player player;
        
        public WelcomeBackMessageTask(Player player) {
            this.player = player;
        }
        
        @Override
        public void run() {
            if (!player.isOnline()) return;
            
            String welcomeBackMessage = configManager.getWelcomeBackMessage();
            if (welcomeBackMessage != null && !welcomeBackMessage.isEmpty()) {
                String formattedMessage = formatMessage(welcomeBackMessage, player);
                player.sendMessage("");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedMessage));
                player.sendMessage(ChatColor.YELLOW + "久しぶりですね！何か変化があったかチェックしてみましょう。");
            }
        }
    }
    
    private class WelcomeMessageTask extends BukkitRunnable {
        private final Player player;
        private final String message;
        
        public WelcomeMessageTask(Player player, String message) {
            this.player = player;
            this.message = message;
        }
        
        @Override
        public void run() {
            if (player.isOnline()) {
                String formattedMessage = formatMessage(message, player);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedMessage));
            }
        }
    }
    
    private class JobGuideMessageTask extends BukkitRunnable {
        private final Player player;
        
        public JobGuideMessageTask(Player player) {
            this.player = player;
        }
        
        @Override
        public void run() {
            if (!player.isOnline()) return;
            
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬ 職業システム案内 ▬▬▬▬▬▬");
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + "/jobs join <職業名> - 職業に就職");
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + "/jobs stats - 現在の職業状況を確認");
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + "/jobstats - 詳細な職業統計を表示");
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + "/quest - 職業クエストを確認");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "利用可能な職業:");
            player.sendMessage(ChatColor.AQUA + "鉱夫 | 木こり | 農家 | 釣り人 | 鍛冶屋 | ポーション屋 | エンチャンター | 建築家");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }
    }
    
    private class SpawnTeleportTask extends BukkitRunnable {
        private final Player player;
        private final Location spawnLocation;
        private final int x, y, z;
        private final String worldName;
        
        public SpawnTeleportTask(Player player, Location spawnLocation, int x, int y, int z, String worldName) {
            this.player = player;
            this.spawnLocation = spawnLocation;
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldName = worldName;
        }
        
        @Override
        public void run() {
            if (player.isOnline()) {
                player.teleport(spawnLocation);
                logger.info("プレイヤー " + player.getName() + " をスポーン座標にテレポートしました: " + 
                          x + ", " + y + ", " + z + " (" + worldName + ")");
            }
        }
    }
}