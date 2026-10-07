package org.tofu.tofunomics.players;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.inventory.InventoryRestoreTracker;
import org.tofu.tofunomics.inventory.PlayerInventoryManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.rules.RulesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * プレイヤーのtofuNomicsワールド入退場処理を管理するクラス
 * ワールドに入った時のウェルカムメッセージ・インベントリ復元・プレイヤーデータ初期化、
 * 退出時のインベントリ保存などを処理する。
 * ※ 参加時はロビーワールドから始まるため、PlayerJoinEventではなく
 *   PlayerChangedWorldEvent（tofuNomics入場）を起点とする。
 */
public class PlayerJoinHandler implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final PlayerInventoryManager inventoryManager;
    private final RulesManager rulesManager;
    private final Logger logger;

    public PlayerJoinHandler(JavaPlugin plugin, ConfigManager configManager, PlayerDAO playerDAO, PlayerInventoryManager inventoryManager, RulesManager rulesManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.inventoryManager = inventoryManager;
        this.rulesManager = rulesManager;
        this.logger = plugin.getLogger();

        handlePlayersAlreadyInWorld();
    }

    /**
     * プラグイン起動時（リロード含む）に、すでに tofuNomics にいるプレイヤーを保存できる状態にする。
     * 入場イベントは起きないので、ここで扱わないと以後ずっと保存されなくなる。
     * 手持ちが空の人は、復元待ちの途中でリロードされた可能性があるので保存データから復元する。
     */
    private void handlePlayersAlreadyInWorld() {
        if (inventoryManager == null || Bukkit.getServer() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals("tofuNomics")) {
                makeSavableWithoutEntryEvent(player);
            }
        }
    }

    /**
     * 入場イベントを経ずに tofuNomics にいるプレイヤーを、保存できる状態にする。
     * すでに復元済みなら何もしない。
     */
    private void makeSavableWithoutEntryEvent(Player player) {
        if (inventoryManager.getRestoreTracker().canSave(player.getUniqueId())) {
            return;
        }
        InventoryRestoreTracker.StartupAction action =
                InventoryRestoreTracker.decideStartupAction(isInventoryEmptyExceptNavigation(player));
        if (action == InventoryRestoreTracker.StartupAction.LOAD_FROM_SAVED) {
            restoreInventory(player);
        } else {
            inventoryManager.getRestoreTracker().markRestored(player.getUniqueId());
        }
    }

    /**
     * サーバーに接続した時の処理
     * 通常はロビーから始まり、tofuNomics へはワールド移動で入る（onPlayerChangedWorld が扱う）。
     * 接続した時点で tofuNomics にいて、そのまま残った場合はワールド移動が起きないので、
     * 2秒待ってから保存できる状態にする（待つあいだにロビーへ移された場合は何もしない）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (inventoryManager == null) {
            return;
        }
        // 前回の接続の印が残っていても使わない
        inventoryManager.getRestoreTracker().clear(player.getUniqueId());

        if (!player.getWorld().getName().equals("tofuNomics")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld().getName().equals("tofuNomics")) {
                makeSavableWithoutEntryEvent(player);
            }
        }, 40L);
    }

    /**
     * ナビゲーションアイテムの置き場（スロット9〜11）を除いて、手持ち・防具・オフハンドが空か
     */
    private boolean isInventoryEmptyExceptNavigation(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i >= 9 && i <= 11) {
                continue;
            }
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 保存済みの手持ちを復元する。
     * TofuHomePluginのナビゲーションアイテム（スロット9, 10, 11）は復元後に置き直す。
     * 読み込みに失敗したときは復元済みの印が付かないので、以後この回は保存されない
     * （保存データを空の手持ちで上書きしないため）。
     */
    private void restoreInventory(Player player) {
        // 短時間に出入りを繰り返したときの二重復元を防ぐ
        if (inventoryManager.getRestoreTracker().canSave(player.getUniqueId())) {
            return;
        }

        // TofuHomePluginのナビゲーションアイテム（スロット9, 10, 11）を一時保存
        ItemStack slot9 = player.getInventory().getItem(9);
        ItemStack slot10 = player.getInventory().getItem(10);
        ItemStack slot11 = player.getInventory().getItem(11);

        // インベントリを復元
        InventoryRestoreTracker.LoadResult result = inventoryManager.loadInventory(player);
        if (result == InventoryRestoreTracker.LoadResult.FAILED) {
            logger.severe("インベントリの復元に失敗しました。このプレイヤーの手持ちは今回保存されません: " + player.getName());
            player.sendMessage(ChatColor.RED + "手持ちの読み込みに失敗しました。保存済みの手持ちは残っています。");
            player.sendMessage(ChatColor.RED + "一度ロビーに戻って入り直すか、管理者に連絡してください。");
            return;
        }

        // ナビゲーションアイテムを復元（TofuHomePluginのアイテムを優先）
        if (slot9 != null) {
            player.getInventory().setItem(9, slot9);
        }
        if (slot10 != null) {
            player.getInventory().setItem(10, slot10);
        }
        if (slot11 != null) {
            player.getInventory().setItem(11, slot11);
        }

        logger.info("インベントリ復元完了（ナビゲーションアイテム保護済み）: " + player.getName());
    }
    
    /**
     * プレイヤーがワールドを変更した時の処理
     * TofuNomicsワールドに入った場合、プレイヤーデータ初期化・ウェルカムメッセージ・職業案内を表示
     * EventPriority.LOWESTを使用してTofuHomePluginのMONITOR処理の前に実行
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String currentWorldName = player.getWorld().getName();
        String previousWorldName = event.getFrom().getName();

        // TofuNomicsワールドに入った場合
        if (currentWorldName.equals("tofuNomics")) {
            // TofuNomicsワールドからTofuNomicsワールドへの移動は処理しない
            if (!previousWorldName.equals("tofuNomics")) {
                // 遅延してインベントリを復元（TofuHomePluginのナビゲーションアイテム付与の後）
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // 復元時点でtofuNomicsワールドにいるか再チェック
                    if (inventoryManager != null && player.isOnline() && player.getWorld().getName().equals("tofuNomics")) {
                        restoreInventory(player);
                    }
                }, 40L); // 2秒後に復元（TofuHomePluginの処理完了を待つ）
            }

            // 非同期でプレイヤーデータを初期化・更新（ワールド非依存のDB登録・更新）
            // ※ 参加時はロビーから始まるため、JoinEventではなくtofuNomics入場時に実行する
            PlayerDataInitializationTask dataTask = new PlayerDataInitializationTask(player);
            dataTask.runTaskAsynchronously(plugin);

            // TofuNomicsワールドでのウェルカムメッセージとタイトルを表示
            WelcomeDisplayTask welcomeTask = new WelcomeDisplayTask(player);
            welcomeTask.runTask(plugin);

            // 対象ワールドに入ったタイミングでルール同意チェックを実施
            checkRulesAgreementOnEnter(player, currentWorldName);
        }
        // TofuNomicsワールドから出た場合、インベントリを保存
        else if (previousWorldName.equals("tofuNomics")) {
            logger.info("プレイヤー " + player.getName() + " がTofuNomicsワールドから退出 - インベントリを保存します");
            if (inventoryManager != null) {
                // 復元前（入場から2秒以内）に出た場合は saveInventory が保存を見送る
                inventoryManager.saveInventory(player);
                // 次に入ったときは、復元が済むまで保存しない
                inventoryManager.getRestoreTracker().clear(player.getUniqueId());
            }
        }
    }

    /**
     * 対象ワールドに入った際のルール同意チェック
     * 未同意プレイヤーを制限対象に登録し、ルールGUIを表示する
     * （onEnableスキャンと同じロジックを参加経路でも適用する）
     */
    private void checkRulesAgreementOnEnter(Player player, String worldName) {
        if (rulesManager == null) {
            return;
        }

        // ルール同意の強制が無効な場合は制限もGUI表示も行わない
        if (!rulesManager.isAgreementEnforced()) {
            return;
        }

        // 対象ワールド外（tofunomics系以外）はルール同意システムの対象外
        if (!configManager.isEconomyEnabledInWorld(worldName)) {
            return;
        }

        // 既に同意済みなら何もしない
        if (rulesManager.hasAgreedToRules(player.getUniqueId())) {
            return;
        }

        logger.info("プレイヤー " + player.getName() + " はルール未同意です - ルールGUIを表示します");
        rulesManager.markAsUnagreed(player.getUniqueId());
        player.sendMessage(configManager.getMessage("rules.messages.must_agree"));

        // 2秒後にルールGUIを表示（ワールド遷移・他プラグイン処理の完了を待つ）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && configManager.isEconomyEnabledInWorld(player.getWorld().getName())) {
                rulesManager.getRulesGUI().openRulesGUI(player, 1);
            }
        }, 40L);
    }
    
    /**
     * プレイヤーがサーバーから退出した時の処理
     * TofuNomicsワールドにいる場合、インベントリを保存
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // TofuNomicsワールドにいる場合、インベントリと現金データを保存
        if (player.getWorld().getName().equals("tofuNomics")) {
            logger.info("プレイヤー " + player.getName() + " がサーバーから退出 - データを保存します");
            
            try {
                // インベントリを保存（復元前に切断した場合は見送る。保存データを空で上書きしないため）
                if (inventoryManager != null) {
                    if (!inventoryManager.getRestoreTracker().canSave(player.getUniqueId())) {
                        logger.info("手持ちの復元前に退出したため、インベントリは保存しません: " + player.getName());
                    } else if (inventoryManager.saveInventory(player)) {
                        logger.info("インベントリ保存成功: " + player.getName());
                    } else {
                        logger.severe("インベントリ保存失敗: " + player.getName());
                    }
                }
                
                // プレイヤーデータを保存（念のため）
                org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayer(player.getUniqueId());
                if (tofuPlayer != null) {
                    boolean dataSaved = playerDAO.updatePlayerData(tofuPlayer);
                    if (dataSaved) {
                        logger.info("プレイヤーデータ保存成功: " + player.getName());
                    } else {
                        logger.severe("プレイヤーデータ保存失敗: " + player.getName());
                    }
                }
                
            } catch (Exception e) {
                logger.severe("プレイヤー退出時のデータ保存中にエラー: " + player.getName());
                logger.severe("エラー詳細: " + e.getMessage());
            }
        }

        // どのワールドで切断しても印を消す（次の入場では復元が済むまで保存しない）
        if (inventoryManager != null) {
            inventoryManager.getRestoreTracker().clear(player.getUniqueId());
        }
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
                
                // ルール確認システムは外部サイトURL方式に変更（GUI廃止）
                // 新規プレイヤーにはウェルカムメッセージで /rules コマンドを案内
                
                // 新規プレイヤーメッセージを表示するフラグを設定
                scheduleNewPlayerMessages(player);
            } else {
                // 既存プレイヤーの場合
                // ルール確認システムは外部サイトURL方式に変更（GUI廃止）
                // /rules コマンドでいつでもルールを確認可能
                
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
        }
    }
    
    
    
    
    
    /**
     * スポーン座標にテレポート
     */
    private void teleportToSpawn(Player player) {
        try {
            // スポーン座標機能が有効でない場合はスキップ
            if (!configManager.isSpawnLocationEnabled()) {
                return;
            }

            // 現在のワールドがtofuNomicsでない場合はスキップ
            if (!player.getWorld().getName().equals("tofuNomics")) {
                return;
            }

            // 設定からスポーン座標を取得
            String worldName = configManager.getSpawnWorldName();
            int x = configManager.getSpawnX();
            int y = configManager.getSpawnY();
            int z = configManager.getSpawnZ();
            int delay = configManager.getSpawnTeleportDelay();

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
                    // tofuNomicsワールドにいない場合はスキップ
                    if (!player.getWorld().getName().equals("tofuNomics")) {
                        return;
                    }
                    displayWelcomeMessages(player);
                    displayWelcomeTitle(player);
                    // スポーン座標へのテレポート処理を追加
                    teleportToSpawn(player);
                }
            } catch (Exception e) {
                logger.warning("TofuNomicsワールド処理中にエラーが発生しました: " + e.getMessage());
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
                // 資金を配らない設定のときは「特典」ではなく案内として見せる
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    bonusAmount > 0 ? "&6▬▬▬▬▬▬ 新規プレイヤー特典 ▬▬▬▬▬▬" : "&6▬▬▬▬▬▬ はじめての方へ ▬▬▬▬▬▬"));
                
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
                // tofuNomicsワールドにいない場合はスキップ
                if (!player.getWorld().getName().equals("tofuNomics")) {
                    return;
                }
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
            
            // tofuNomicsワールドにいない場合はスキップ
            if (!player.getWorld().getName().equals("tofuNomics")) {
                return;
            }
            
            for (String line : buildJobGuideLines(findCurrentJobDisplayName(player), findStarterJobDisplayNames())) {
                player.sendMessage(line);
            }
        }
    }

    /**
     * 入場時の職業案内の文面を組み立てる。
     * 職業に就いている人には 1 行だけ、まだの人には選び方と注意を出す。
     * 職業名を打たせる案内はしない（/jobs の画面から選べる）。
     *
     * @param currentJobDisplayName 現在の職業の表示名。未就職なら null
     * @param starterJobDisplayNames 最初から選べる職業の表示名（上級職は含めない）
     */
    static List<String> buildJobGuideLines(String currentJobDisplayName, List<String> starterJobDisplayNames) {
        List<String> lines = new ArrayList<>();
        if (currentJobDisplayName != null && !currentJobDisplayName.isEmpty()) {
            lines.add(ChatColor.GRAY + "現在の職業: " + ChatColor.AQUA + currentJobDisplayName
                    + ChatColor.GRAY + " ｜ " + ChatColor.WHITE + "/jobs" + ChatColor.GRAY + " で職業メニューを開けます");
            return lines;
        }

        lines.add("");
        lines.add(ChatColor.GOLD + "▬▬▬▬▬▬ 職業を選ぼう ▬▬▬▬▬▬");
        lines.add(ChatColor.YELLOW + "• " + ChatColor.WHITE + "/jobs" + ChatColor.GRAY + " と入力すると、職業を選ぶ画面が開きます");
        if (starterJobDisplayNames != null && !starterJobDisplayNames.isEmpty()) {
            lines.add(ChatColor.YELLOW + "• " + ChatColor.GRAY + "最初に選べる職業: "
                    + ChatColor.AQUA + String.join(" | ", starterJobDisplayNames));
        }
        lines.add(ChatColor.RED + "• 職業は Lv50 になるまで選び直せません。" + ChatColor.GRAY + "説明を読んでから決めましょう");
        lines.add(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        return lines;
    }

    private JobManager getJobManager() {
        if (plugin instanceof org.tofu.tofunomics.TofuNomics) {
            return ((org.tofu.tofunomics.TofuNomics) plugin).getJobManager();
        }
        return null;
    }

    /** 現在の職業の表示名。未就職・取得できないときは null */
    private String findCurrentJobDisplayName(Player player) {
        try {
            JobManager jobManager = getJobManager();
            if (jobManager == null) {
                return null;
            }
            String jobName = jobManager.getPlayerJob(player.getUniqueId());
            if (jobName == null || jobName.isEmpty()) {
                return null;
            }
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', jobManager.getJobDisplayName(jobName)));
        } catch (Exception e) {
            logger.warning("職業案内の表示中に現在の職業を取得できませんでした: " + e.getMessage());
            return null;
        }
    }

    /** 最初から選べる職業（上級職を除く）の表示名 */
    private List<String> findStarterJobDisplayNames() {
        List<String> names = new ArrayList<>();
        try {
            JobManager jobManager = getJobManager();
            if (jobManager == null) {
                return names;
            }
            for (Job job : jobManager.getAllJobs()) {
                if (!configManager.isAdvancedJob(job.getName())) {
                    names.add(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', job.getDisplayName())));
                }
            }
        } catch (Exception e) {
            logger.warning("職業案内の表示中に職業一覧を取得できませんでした: " + e.getMessage());
        }
        return names;
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