package org.tofu.tofunomics;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.database.DatabaseManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobChangeDAO;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.ItemManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.economy.BankLocationManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.commands.*;
import org.tofu.tofunomics.tools.JobToolManager;
import org.tofu.tofunomics.experience.JobExperienceManager;
import org.tofu.tofunomics.income.JobIncomeManager;
import org.tofu.tofunomics.quests.JobQuestManager;
import org.tofu.tofunomics.rewards.JobLevelRewardManager;
import org.tofu.tofunomics.stats.JobStatsManager;

import java.io.File;

public final class TofuNomics extends JavaPlugin {
    
    private static TofuNomics instance;
    private DatabaseManager databaseManager;
    private PlayerDAO playerDAO;
    private JobDAO jobDAO;
    private PlayerJobDAO playerJobDAO;
    private JobChangeDAO jobChangeDAO;
    private ConfigManager configManager;
    private ItemManager itemManager;
    private CurrencyConverter currencyConverter;
    private BankLocationManager bankLocationManager;
    private JobManager jobManager;
    private ExperienceManager experienceManager;
    
    // Phase 3 新機能マネージャー
    private JobToolManager jobToolManager;
    private JobExperienceManager jobExperienceManager;
    // 収入システムは無効化: JobIncomeManager は削除されました
    // private JobIncomeManager jobIncomeManager;
    private JobQuestManager jobQuestManager;
    private JobLevelRewardManager jobLevelRewardManager;
    private JobStatsManager jobStatsManager;
    private org.tofu.tofunomics.jobs.JobBlockPermissionManager jobBlockPermissionManager;
    
    // Phase 6 クラフト制限システム
    private org.tofu.tofunomics.jobs.JobCraftPermissionManager jobCraftPermissionManager;
    
    // 住居賃貸システム
    private org.tofu.tofunomics.housing.HousingRentalManager housingRentalManager;
    private org.tofu.tofunomics.housing.SelectionManager selectionManager;
    private org.tofu.tofunomics.housing.HousingListener housingListener;
    private org.tofu.tofunomics.integration.WorldGuardIntegration worldGuardIntegration;

    // テストモードマネージャー
    private org.tofu.tofunomics.testing.TestModeManager testModeManager;
    private org.tofu.tofunomics.testing.WorldGuardTestModeListener worldGuardTestModeListener;
    
    // Phase 4 取引システムマネージャー
    private org.tofu.tofunomics.trade.TradeChestManager tradeChestManager;
    private org.tofu.tofunomics.trade.TradePriceManager tradePriceManager;
    private org.tofu.tofunomics.trade.TradeChestListener tradeChestListener;
    
    // Phase 5 統合イベントシステム
    private org.tofu.tofunomics.events.UnifiedEventHandler unifiedEventHandler;
    
    // Phase 6 クラフト制限専用イベントハンドラー（緊急対応）
    private org.tofu.tofunomics.events.CraftRestrictionEventHandler craftRestrictionEventHandler;
    
    // プレイヤー参加時処理
    private org.tofu.tofunomics.players.PlayerJoinHandler playerJoinHandler;

    // インベントリ管理システム
    private org.tofu.tofunomics.inventory.PlayerInventoryManager inventoryManager;

    // スコアボードシステム
    private org.tofu.tofunomics.scoreboard.ScoreboardManager scoreboardManager;
    
    // NPCシステム（新機能）
    private org.tofu.tofunomics.npc.NPCManager npcManager;
    private org.tofu.tofunomics.npc.BankNPCManager bankNPCManager;
    private org.tofu.tofunomics.npc.TradingNPCManager tradingNPCManager;
    private org.tofu.tofunomics.npc.FoodNPCManager foodNPCManager;
    private org.tofu.tofunomics.npc.ProcessingNPCManager processingNPCManager;
    private org.tofu.tofunomics.npc.NPCListener npcListener;
    private org.tofu.tofunomics.npc.gui.BankGUI bankGUI;
    private org.tofu.tofunomics.npc.gui.TradingGUI tradingGUI;
    private org.tofu.tofunomics.npc.gui.FoodGUI foodGUI;
    private org.tofu.tofunomics.npc.gui.ProcessingGUI processingGUI;
    private org.tofu.tofunomics.npc.gui.QuantitySelectorGUI quantitySelectorGUI;

    // エリアシステム
    private org.tofu.tofunomics.area.AreaManager areaManager;
    private org.tofu.tofunomics.area.AreaListener areaListener;
    
    // 時刻放送システム
    private org.tofu.tofunomics.announcement.TimeAnnouncementSystem timeAnnouncementSystem;
    
    // 時計アイテムシステム
    private org.tofu.tofunomics.items.ClockItemManager clockItemManager;
    
    // ルール確認システム
    private org.tofu.tofunomics.rules.RulesManager rulesManager;

    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("TofuNomicsプラグインを有効化しています...");
        
        // 設定ファイルの初期化
        saveDefaultConfig();
        
        // ConfigManagerの初期化
        this.configManager = new ConfigManager(this);
        
        // 設定ファイルの自動マイグレーション
        configManager.migrateConfig();
        
        // 設定の自動初期化を実行
        initializeAutoConfig();
        
        // データベースの初期化
        if (!initializeDatabase()) {
            getLogger().severe("データベースの初期化に失敗しました。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // DAOの初期化
        initializeDAOs();
        
        // マネージャーの初期化
        initializeManagers();
        
        // Phase 3 新機能マネージャーの初期化
        initializePhase3Managers();
        
        // Phase 4 取引システムの初期化
        initializePhase4TradeSystem();
        
        // Phase 5 統合イベントシステムの初期化
        initializePhase5EventSystem();
        
        // スコアボードシステムの初期化
        initializeScoreboardSystem();
        
        // ルール確認システムの初期化（PlayerJoinHandlerの前に初期化）
        initializeRulesSystem();
        
        // プレイヤー参加時処理の初期化
        initializePlayerJoinHandler();
        
        // NPCシステムの初期化（新機能）
        initializeNPCSystem();

        // エリアシステムの初期化
        initializeAreaSystem();

        // 住居賃貸システムの初期化
        initializeHousingSystem();
        
        // 時刻放送システムの初期化
        initializeTimeAnnouncementSystem();
        
        // 時計アイテムシステムの初期化
        initializeClockItemSystem();

        // イベントリスナーの登録
        registerEventListeners();
        
        // コマンドハンドラーの登録
        registerCommands();
        
        getLogger().info("TofuNomicsプラグインが正常に有効化されました！");
    }

    @Override
    public void onDisable() {
        getLogger().info("TofuNomicsプラグインを無効化しています...");

        // インベントリ管理システムのクリーンアップ
        if (inventoryManager != null) {
            // 全オンラインプレイヤーのインベントリを保存
            inventoryManager.saveAllOnlineInventories();
            // 自動保存タスクを停止
            inventoryManager.stopAutoSaveTask();
            getLogger().info("インベントリ管理システムをクリーンアップしました");
        }

        // Phase 5 統合イベントシステムのクリーンアップ
        if (unifiedEventHandler != null) {
            unifiedEventHandler.cleanup();
        }

        // スコアボードシステムのクリーンアップ
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }

        // NPCシステムのクリーンアップ
        cleanupNPCSystem();

        // エリアシステムのクリーンアップ
        cleanupAreaSystem();

        // 住居賃貸システムのクリーンアップ
        cleanupHousingSystem();

        // 時刻放送システムのクリーンアップ
        if (timeAnnouncementSystem != null) {
            timeAnnouncementSystem.stop();
        }

        // 時計アイテムシステムのクリーンアップ
        if (clockItemManager != null) {
            clockItemManager.stopActionBarTask();
        }

        // データベース接続を閉じる
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("TofuNomicsプラグインが無効化されました。");
    }
    
    private boolean initializeDatabase() {
        try {
            // データベースファイルのパスを設定
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            String databasePath = new File(dataFolder, "tofunomics.db").getAbsolutePath();
            
            // DatabaseManagerの初期化
            databaseManager = new DatabaseManager(databasePath, getLogger());
            
            // データベースに接続
            if (!databaseManager.connect()) {
                return false;
            }
            
            // テーブルを作成
            databaseManager.createTables();
            
            return true;
        } catch (Exception e) {
            getLogger().severe("データベース初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void initializeDAOs() {
        if (databaseManager != null && databaseManager.isConnected()) {
            playerDAO = new PlayerDAO(databaseManager.getConnection());
            jobDAO = new JobDAO(databaseManager.getConnection());
            playerJobDAO = new PlayerJobDAO(databaseManager.getConnection());
            jobChangeDAO = new JobChangeDAO(databaseManager.getConnection());
            
            getLogger().info("データアクセス層（DAO）を初期化しました");
        }
    }
    
    private void initializeManagers() {
        try {
            // ConfigManagerは既にonEnable()で初期化済み
            
            // ItemManagerの初期化
            itemManager = new ItemManager(configManager);
            
            // CurrencyConverterの初期化
            currencyConverter = new CurrencyConverter(
                playerDAO, 
                itemManager, 
                configManager,
                configManager.getCurrencyDecimalPlaces()
            );
            
            // BankLocationManagerの初期化
            bankLocationManager = new BankLocationManager(configManager);
            
            // JobManagerの初期化
            jobManager = new JobManager(
                configManager,
                jobDAO,
                playerDAO,
                playerJobDAO,
                jobChangeDAO
            );
            
            // ExperienceManagerの初期化
            experienceManager = new ExperienceManager(configManager, playerJobDAO);
            
            getLogger().info("マネージャーを初期化しました");
        } catch (Exception e) {
            getLogger().severe("マネージャー初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializePhase3Managers() {
        try {
            // JobToolManagerの初期化
            jobToolManager = new JobToolManager(configManager);
            
            // JobLevelRewardManagerの初期化
            jobLevelRewardManager = new JobLevelRewardManager(configManager, playerDAO);
            
            // JobExperienceManagerの初期化
            jobExperienceManager = new JobExperienceManager(
                configManager, 
                playerJobDAO, 
                jobDAO, 
                jobManager, 
                jobToolManager,
                experienceManager
            );
            
            // 収入システムは無効化: JobIncomeManagerの初期化はコメントアウト
            /*
            jobIncomeManager = new JobIncomeManager(
                configManager,
                playerDAO,
                jobManager
            );
            */
            
            // JobQuestManagerの初期化
            jobQuestManager = new JobQuestManager(
                configManager, 
                playerDAO, 
                jobManager
            );
            
            // JobStatsManagerの初期化
            jobStatsManager = new JobStatsManager(
                configManager, 
                playerJobDAO, 
                jobManager, 
                jobLevelRewardManager
            );
            
            // JobBlockPermissionManagerの初期化
            jobBlockPermissionManager = new org.tofu.tofunomics.jobs.JobBlockPermissionManager(
                configManager,
                jobManager
            );
            
            getLogger().info("Phase 3 職業特化機能を初期化しました");
        } catch (Exception e) {
            getLogger().severe("Phase 3 マネージャー初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializePhase4TradeSystem() {
        try {
            if (!configManager.isTradeSystemEnabled()) {
                getLogger().info("取引システムは無効化されています。");
                return;
            }
            
            // TradeChestManagerの初期化
            tradeChestManager = new org.tofu.tofunomics.trade.TradeChestManager(
                databaseManager.getConnection(),
                configManager,
                playerDAO
            );
            
            // TradePriceManagerの初期化
            tradePriceManager = new org.tofu.tofunomics.trade.TradePriceManager(
                configManager,
                jobManager
            );
            
            // TradeChestListenerの初期化
            tradeChestListener = new org.tofu.tofunomics.trade.TradeChestListener(
                tradeChestManager,
                tradePriceManager,
                configManager,
                playerDAO,
                jobManager
            );
            
            getLogger().info("Phase 4 取引システムを初期化しました");
        } catch (Exception e) {
            getLogger().severe("Phase 4 取引システム初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Phase 6 クラフト制限システムの初期化
        try {
            getLogger().info("Phase 6 クラフト制限システム初期化開始");
            
            // JobCraftPermissionManagerの初期化
            jobCraftPermissionManager = new org.tofu.tofunomics.jobs.JobCraftPermissionManager(
                this,
                jobManager,
                configManager
            );
            
            // クラフト制限メッセージの強制初期化（緊急対応）
            getLogger().info("クラフト制限メッセージの強制初期化を実行");
            configManager.ensureCraftRestrictionMessagesExist();
            saveConfig(); // 設定を確実に保存
            configManager.reloadConfig(); // リロードで反映
            
            // CraftRestrictionEventHandlerの初期化（緊急対応）
            craftRestrictionEventHandler = new org.tofu.tofunomics.events.CraftRestrictionEventHandler(this);
            
            getLogger().info("Phase 6 クラフト制限システムを初期化しました");
        } catch (Exception e) {
            getLogger().severe("Phase 6 クラフト制限システム初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 設定の自動初期化処理
     */
    private void initializeAutoConfig() {
        try {
            getLogger().info("=== 設定自動初期化開始 ===");
            
            // 新しい自動更新機能（バージョン2.0対応）
            getLogger().info("設定ファイルの自動更新を実行中...");
            configManager.updateConfigWithDefaults();
            
            // NPC関連設定の自動初期化
            configManager.ensureNPCMessagesExist();
            
            // player_join設定の自動初期化
            configManager.ensurePlayerJoinSettingsExist();
            
            // クラフト制限設定の自動初期化
            configManager.ensureCraftRestrictionMessagesExist();
            
            // 設定を保存
            saveConfig();
            
            getLogger().info("=== 設定自動初期化完了 ===");
            
        } catch (Exception e) {
            getLogger().severe("設定自動初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializePhase5EventSystem() {
        try {
            if (!configManager.isEventSystemEnabled()) {
                getLogger().info("統合イベントシステムは無効化されています。");
                return;
            }
            
            // UnifiedEventHandlerの初期化
            getLogger().info("UnifiedEventHandler初期化開始");
            try {
                unifiedEventHandler = new org.tofu.tofunomics.events.UnifiedEventHandler(
                    this,
                    configManager,
                    playerDAO,
                    playerJobDAO,
                    jobManager,
                    jobExperienceManager,
                    jobQuestManager,
                    jobBlockPermissionManager
                );
                getLogger().info("UnifiedEventHandler初期化完了");
            } catch (Exception e) {
                getLogger().severe("UnifiedEventHandler初期化エラー: " + e.getMessage());
                e.printStackTrace();
            }
            
            getLogger().info("Phase 5 統合イベントシステムを初期化しました");
        } catch (Exception e) {
            getLogger().severe("Phase 5 統合イベントシステム初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializePlayerJoinHandler() {
        try {
            // PlayerInventoryManagerの初期化
            inventoryManager = new org.tofu.tofunomics.inventory.PlayerInventoryManager(
                this,
                databaseManager.getConnection()
            );
            getLogger().info("インベントリ管理システムを初期化しました");

            // PlayerJoinHandlerの初期化
            playerJoinHandler = new org.tofu.tofunomics.players.PlayerJoinHandler(
                this,
                configManager,
                playerDAO,
                scoreboardManager,
                inventoryManager,
                rulesManager
            );

            getLogger().info("プレイヤー参加時処理を初期化しました");
        } catch (Exception e) {
            getLogger().severe("プレイヤー参加時処理の初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeScoreboardSystem() {
        try {
            if (!configManager.isScoreboardEnabled()) {
                getLogger().info("スコアボードシステムは無効化されています。");
                return;
            }
            
            // ScoreboardManagerの初期化
            scoreboardManager = new org.tofu.tofunomics.scoreboard.ScoreboardManager(
                this,
                configManager,
                playerDAO,
                currencyConverter,
                jobManager
            );
            
            getLogger().info("スコアボードシステムを初期化しました");
        } catch (Exception e) {
            getLogger().severe("スコアボードシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void registerEventListeners() {
        try {
            // Phase 5 統合イベントハンドラーの登録
            if (unifiedEventHandler != null) {
                getServer().getPluginManager().registerEvents(unifiedEventHandler, this);
                getLogger().info("Phase 5 統合イベントハンドラーを登録しました");
            } else {
                // フォールバック：既存の個別リスナーを登録（収入システムは無効化）
                getServer().getPluginManager().registerEvents(jobExperienceManager, this);
                // getServer().getPluginManager().registerEvents(jobIncomeManager, this);  // 収入システムは無効化
                getServer().getPluginManager().registerEvents(jobQuestManager, this);
                getLogger().info("既存の個別イベントリスナーを登録しました");
            }
            
            // Phase 4 取引システムイベントリスナーの登録
            if (tradeChestListener != null) {
                getServer().getPluginManager().registerEvents(tradeChestListener, this);
                getLogger().info("取引システムリスナーを登録しました");
            }
            
            // Phase 6 クラフト制限イベントハンドラーの登録（緊急対応）
            if (craftRestrictionEventHandler != null) {
                getServer().getPluginManager().registerEvents(craftRestrictionEventHandler, this);
                getLogger().info("Phase 6 クラフト制限イベントハンドラーを登録しました");
            }
            
            // プレイヤー参加時処理リスナーの登録
            if (playerJoinHandler != null) {
                getServer().getPluginManager().registerEvents(playerJoinHandler, this);
                getLogger().info("プレイヤー参加時処理リスナーを登録しました");
            }
            
            // スコアボードマネージャーリスナーの登録
            if (scoreboardManager != null) {
                getServer().getPluginManager().registerEvents(scoreboardManager, this);
                getLogger().info("スコアボードシステムリスナーを登録しました");
            }
            
            // NPCシステムリスナーの登録
            registerNPCEventListeners();

            // エリアシステムリスナーの登録
            if (areaListener != null) {
                getServer().getPluginManager().registerEvents(areaListener, this);
                getLogger().info("エリアシステムリスナーを登録しました");
            }

            // 住居賃貸システムリスナーの登録
            if (housingListener != null) {
                getServer().getPluginManager().registerEvents(housingListener, this);
                getLogger().info("住居賃貸システムリスナーを登録しました");
            }

            // WorldGuardテストモードリスナーの登録
            if (worldGuardTestModeListener != null) {
                getServer().getPluginManager().registerEvents(worldGuardTestModeListener, this);
                getLogger().info("WorldGuardテストモードリスナーを登録しました");
            }
            
            // ルール確認システムリスナーの登録
            if (rulesManager != null) {
                getServer().getPluginManager().registerEvents(rulesManager, this);
                getServer().getPluginManager().registerEvents(rulesManager.getRulesGUI(), this);
                getLogger().info("ルール確認システムリスナーを登録しました");
            }

            getLogger().info("全てのイベントリスナーを登録しました");
        } catch (Exception e) {
            getLogger().severe("イベントリスナー登録中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void registerCommands() {
        try {
            // 経済系コマンド
            getCommand("balance").setExecutor(new BalanceCommand(configManager, currencyConverter));
            getCommand("pay").setExecutor(new PayCommand(configManager, currencyConverter));
            getCommand("withdraw").setExecutor(new WithdrawCommand(configManager, currencyConverter, bankLocationManager));
            getCommand("deposit").setExecutor(new DepositCommand(configManager, currencyConverter, itemManager, bankLocationManager));
            getCommand("balancetop").setExecutor(new BalanceTopCommand(configManager, currencyConverter, playerDAO));
            getCommand("eco").setExecutor(new EcoCommand(configManager, currencyConverter, playerDAO));
            
            // 職業系コマンド
            getCommand("jobs").setExecutor(new JobsCommand(configManager, jobManager, experienceManager));
            getCommand("jobstats").setExecutor(new JobStatsCommand(jobStatsManager));
            getCommand("quest").setExecutor(new JobQuestCommand(configManager, jobQuestManager));
            
            // Phase 4 取引系コマンド
            if (tradingNPCManager != null) {
                org.tofu.tofunomics.commands.TradeCommand tradeCommand = 
                    new org.tofu.tofunomics.commands.TradeCommand(tradingNPCManager, configManager, tradeChestManager);
                getCommand("trade").setExecutor(tradeCommand);
                getCommand("trade").setTabCompleter(tradeCommand);
            }
            
            // スコアボード系コマンド
            if (scoreboardManager != null) {
                getCommand("scoreboard").setExecutor(
                    new org.tofu.tofunomics.commands.ScoreboardCommand(scoreboardManager, configManager));
            }
            
            // 住居賃貸系コマンド
            if (housingRentalManager != null && testModeManager != null) {
                org.tofu.tofunomics.commands.HousingCommand housingCommand = 
                    new org.tofu.tofunomics.commands.HousingCommand(
                        this,
                        housingRentalManager,
                        selectionManager,
                        testModeManager
                    );
                getCommand("housing").setExecutor(housingCommand);
                getCommand("housing").setTabCompleter(housingCommand);
            }
            
            // テストモードコマンド
            if (testModeManager != null) {
                org.tofu.tofunomics.commands.TestModeCommand testModeCommand = 
                    new org.tofu.tofunomics.commands.TestModeCommand(this, testModeManager);
                getCommand("testmode").setExecutor(testModeCommand);
                getCommand("testmode").setTabCompleter(testModeCommand);
            }
            
            // 時計アイテムコマンド
            if (clockItemManager != null) {
                org.tofu.tofunomics.commands.ClockCommand clockCommand = 
                    new org.tofu.tofunomics.commands.ClockCommand(this);
                getCommand("clock").setExecutor(clockCommand);
                getCommand("clock").setTabCompleter(clockCommand);
            }
            
            // メインコマンド（NPC管理機能含む）
            if (npcManager != null) {
                org.tofu.tofunomics.commands.TofuNomicsCommand mainCommand = 
                    new org.tofu.tofunomics.commands.TofuNomicsCommand(this, configManager, npcManager, bankNPCManager, tradingNPCManager, foodNPCManager, processingNPCManager);
                getCommand("tofunomics").setExecutor(mainCommand);
                getCommand("tofunomics").setTabCompleter(mainCommand);
            }
            
            // ルール確認コマンド
            if (rulesManager != null) {
                org.tofu.tofunomics.commands.RulesCommand rulesCommand = 
                    new org.tofu.tofunomics.commands.RulesCommand(rulesManager);
                getCommand("rules").setExecutor(rulesCommand);
            }
            
            getLogger().info("コマンドハンドラーを登録しました");
        } catch (Exception e) {
            getLogger().severe("コマンド登録中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Getter methods for other classes to access
    public static TofuNomics getInstance() {
        return instance;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public PlayerDAO getPlayerDAO() {
        return playerDAO;
    }
    
    public JobDAO getJobDAO() {
        return jobDAO;
    }
    
    public PlayerJobDAO getPlayerJobDAO() {
        return playerJobDAO;
    }
    
    public JobChangeDAO getJobChangeDAO() {
        return jobChangeDAO;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public ItemManager getItemManager() {
        return itemManager;
    }
    
    public CurrencyConverter getCurrencyConverter() {
        return currencyConverter;
    }
    
    public BankLocationManager getBankLocationManager() {
        return bankLocationManager;
    }
    
    public JobManager getJobManager() {
        return jobManager;
    }
    
    public ExperienceManager getExperienceManager() {
        return experienceManager;
    }
    
    public org.tofu.tofunomics.scoreboard.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    // NPCシステムの初期化
    private void initializeNPCSystem() {
        try {
            if (!configManager.isNPCSystemEnabled()) {
                getLogger().info("NPCシステムは無効化されています");
                return;
            }
            
            getLogger().info("=== NPCシステム初期化開始 ===");
            
            // NPCマネージャーの初期化
            getLogger().info("NPCマネージャー初期化中...");
            npcManager = new org.tofu.tofunomics.npc.NPCManager(this, configManager);
            getLogger().info("NPCマネージャー初期化完了: " + (npcManager != null ? "成功" : "失敗"));
            
            // GUIの初期化（NPCマネージャーより先に）
            getLogger().info("BankGUIインスタンス作成開始...");
            bankGUI = new org.tofu.tofunomics.npc.gui.BankGUI(
                this, 
                configManager, 
                currencyConverter, 
                itemManager
            );
            getLogger().info("BankGUIインスタンス作成完了: " + (bankGUI != null ? "成功" : "失敗"));
            
            // 銀行NPCマネージャーの初期化
            getLogger().info("BankNPCマネージャー初期化中...");
            bankNPCManager = new org.tofu.tofunomics.npc.BankNPCManager(
                this, 
                configManager, 
                npcManager, 
                bankLocationManager, 
                currencyConverter,
                bankGUI
            );
            getLogger().info("BankNPCマネージャー初期化完了: " + (bankNPCManager != null ? "成功" : "失敗"));
            
            // 取引NPCマネージャーの初期化
            getLogger().info("TradingNPCマネージャー初期化中...");
            tradingNPCManager = new org.tofu.tofunomics.npc.TradingNPCManager(
                this, 
                configManager, 
                npcManager, 
                currencyConverter, 
                jobManager, 
                tradePriceManager, 
                playerDAO
            );
            getLogger().info("TradingNPCマネージャー初期化完了: " + (tradingNPCManager != null ? "成功" : "失敗"));
            
            // 食料NPCマネージャーの初期化
            getLogger().info("FoodNPCマネージャー初期化中...");
            foodNPCManager = new org.tofu.tofunomics.npc.FoodNPCManager(
                this,
                configManager,
                npcManager,
                currencyConverter,
                playerDAO
            );
            getLogger().info("FoodNPCマネージャー初期化完了: " + (foodNPCManager != null ? "成功" : "失敗"));
            
            // 加工NPCマネージャーの初期化
            getLogger().info("ProcessingNPCマネージャー初期化中...");
            processingNPCManager = new org.tofu.tofunomics.npc.ProcessingNPCManager(
                this,
                configManager,
                npcManager,
                currencyConverter,
                jobManager
            );
            getLogger().info("ProcessingNPCマネージャー初期化完了: " + (processingNPCManager != null ? "成功" : "失敗"));
            
            // NPCリスナーの初期化
            getLogger().info("NPCリスナー初期化中...");
            npcListener = new org.tofu.tofunomics.npc.NPCListener(
                this, 
                configManager, 
                npcManager, 
                bankNPCManager, 
                tradingNPCManager,
                foodNPCManager,
                processingNPCManager
            );
            getLogger().info("NPCリスナー初期化完了: " + (npcListener != null ? "成功" : "失敗"));
            
            // ===== 重要: TradingGUIの初期化 =====
            getLogger().info("=== TradingGUI初期化開始 ===");
            getLogger().info("依存コンポーネント確認:");
            getLogger().info("  - ConfigManager: " + (configManager != null ? "OK" : "NULL"));
            getLogger().info("  - CurrencyConverter: " + (currencyConverter != null ? "OK" : "NULL"));
            getLogger().info("  - JobManager: " + (jobManager != null ? "OK" : "NULL"));
            getLogger().info("  - TradingNPCManager: " + (tradingNPCManager != null ? "OK" : "NULL"));
            getLogger().info("  - TradePriceManager: " + (tradePriceManager != null ? "OK" : "NULL"));
            
            try {
                tradingGUI = new org.tofu.tofunomics.npc.gui.TradingGUI(
                    this, 
                    configManager, 
                    currencyConverter, 
                    jobManager, 
                    tradingNPCManager, 
                    tradePriceManager
                );
                
                if (tradingGUI != null) {
                    getLogger().info("✓ TradingGUIインスタンス作成成功！");
                } else {
                    getLogger().severe("✗ TradingGUIインスタンスがnullです！");
                }
            } catch (Exception e) {
                getLogger().severe("✗ TradingGUI初期化中に例外が発生しました:");
                getLogger().severe("  エラーメッセージ: " + e.getMessage());
                getLogger().severe("  エラークラス: " + e.getClass().getName());
                e.printStackTrace();
                tradingGUI = null;
            }
            getLogger().info("=== TradingGUI初期化完了 ===");
            
            // FoodGUIの初期化
            getLogger().info("FoodGUIインスタンス作成開始...");
            try {
                foodGUI = new org.tofu.tofunomics.npc.gui.FoodGUI(
                    this, 
                    configManager, 
                    currencyConverter, 
                    foodNPCManager
                );
                getLogger().info("FoodGUIインスタンス作成完了: " + (foodGUI != null ? "成功" : "失敗"));
            } catch (Exception e) {
                getLogger().severe("FoodGUI初期化エラー: " + e.getMessage());
                e.printStackTrace();
                foodGUI = null;
            }
            
            // QuantitySelectorGUIの初期化
            getLogger().info("QuantitySelectorGUIインスタンス作成開始...");
            try {
                quantitySelectorGUI = new org.tofu.tofunomics.npc.gui.QuantitySelectorGUI(this);
                getLogger().info("QuantitySelectorGUIインスタンス作成完了: " + (quantitySelectorGUI != null ? "成功" : "失敗"));
            } catch (Exception e) {
                getLogger().severe("QuantitySelectorGUI初期化エラー: " + e.getMessage());
                e.printStackTrace();
                quantitySelectorGUI = null;
            }
            
            // ProcessingGUIの初期化
            getLogger().info("ProcessingGUIインスタンス作成開始...");
            try {
                processingGUI = new org.tofu.tofunomics.npc.gui.ProcessingGUI(
                    this, 
                    configManager, 
                    currencyConverter, 
                    processingNPCManager,
                    jobManager,
                    quantitySelectorGUI
                );
                getLogger().info("ProcessingGUIインスタンス作成完了: " + (processingGUI != null ? "成功" : "失敗"));
            } catch (Exception e) {
                getLogger().severe("ProcessingGUI初期化エラー: " + e.getMessage());
                e.printStackTrace();
                processingGUI = null;
            }
            
            // 既存のシステムNPCを削除（重複防止）
            getLogger().info("既存システムNPCの削除中...");
            npcManager.removeExistingSystemNPCs();
            
            // エンティティ削除が完全に処理されるまで待機（2秒 = 40 ticks）
            getLogger().info("エンティティ削除処理の完了を待機中（2秒）...");
            getServer().getScheduler().runTaskLater(this, () -> {
                // NPCの生成（各マネージャーが個別に生成）
                getLogger().info("各マネージャーによるNPC生成開始...");
                bankNPCManager.initializeBankNPCs();
                tradingNPCManager.initializeTradingNPCs();
                foodNPCManager.initializeFoodNPCs();
                processingNPCManager.initializeProcessingNPCs();
                
                getLogger().info("=== NPCシステム初期化完了（遅延生成） ===");
            }, 40L);  // 40 ticks = 2秒待機
            
            getLogger().info("=== GUI初期化完了 ===");
            getLogger().info("GUI初期化結果サマリー:");
            getLogger().info("  - NPCManager: " + (npcManager != null ? "✓" : "✗"));
            getLogger().info("  - BankGUI: " + (bankGUI != null ? "✓" : "✗"));
            getLogger().info("  - TradingGUI: " + (tradingGUI != null ? "✓" : "✗"));
            getLogger().info("  - FoodGUI: " + (foodGUI != null ? "✓" : "✗"));
            getLogger().info("  - ProcessingGUI: " + (processingGUI != null ? "✓" : "✗"));
            
        } catch (Exception e) {
            getLogger().severe("NPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // NPCシステムのクリーンアップ
    private void cleanupNPCSystem() {
        try {
            if (bankGUI != null) {
                bankGUI.closeAllGUIs();
            }
            
            if (tradingGUI != null) {
                tradingGUI.closeAllGUIs();
            }
            
            if (foodGUI != null) {
                foodGUI.closeAllGUIs();
            }
            
            if (processingGUI != null) {
                processingGUI.closeAllGUIs();
            }
            
            if (quantitySelectorGUI != null) {
                quantitySelectorGUI.closeAllGUIs();
            }
            
            if (npcManager != null) {
                npcManager.removeAllNPCs();
            }
            
            getLogger().info("NPCシステムをクリーンアップしました");
        } catch (Exception e) {
            getLogger().warning("NPCシステムのクリーンアップ中にエラーが発生しました: " + e.getMessage());
        }
    }
    
    // NPCシステムのイベントリスナーを登録
    private void registerNPCEventListeners() {
        if (npcListener != null) {
            getServer().getPluginManager().registerEvents(npcListener, this);
            getLogger().info("NPCイベントリスナーを登録しました");
        }
        
        if (bankGUI != null) {
            getServer().getPluginManager().registerEvents(bankGUI, this);
            getLogger().info("銀行GUIリスナーを登録しました");
        }
        
        if (tradingGUI != null) {
            getServer().getPluginManager().registerEvents(tradingGUI, this);
            getLogger().info("取引GUIリスナーを登録しました");
        }
        
        if (foodGUI != null) {
            getServer().getPluginManager().registerEvents(foodGUI, this);
            getLogger().info("食料GUIリスナーを登録しました");
        }
        
        if (processingGUI != null) {
            getServer().getPluginManager().registerEvents(processingGUI, this);
            getLogger().info("加工GUIリスナーを登録しました");
        }
        
        if (quantitySelectorGUI != null) {
            getServer().getPluginManager().registerEvents(quantitySelectorGUI, this);
            getLogger().info("数量選択GUIリスナーを登録しました");
        }
    }
    
    // NPCシステムのGetter メソッド
    public org.tofu.tofunomics.npc.NPCManager getNPCManager() {
        return npcManager;
    }
    
    public org.tofu.tofunomics.npc.BankNPCManager getBankNPCManager() {
        return bankNPCManager;
    }
    
    public org.tofu.tofunomics.npc.TradingNPCManager getTradingNPCManager() {
        return tradingNPCManager;
    }
    
    public org.tofu.tofunomics.npc.FoodNPCManager getFoodNPCManager() {
        return foodNPCManager;
    }
    
    public org.tofu.tofunomics.npc.ProcessingNPCManager getProcessingNPCManager() {
        return processingNPCManager;
    }
    
    public org.tofu.tofunomics.npc.gui.BankGUI getBankGUI() {
        return bankGUI;
    }
    
    public org.tofu.tofunomics.npc.gui.TradingGUI getTradingGUI() {
        getLogger().info("TradingGUIインスタンス取得要求: " + (tradingGUI != null ? "存在" : "null"));
        if (tradingGUI == null) {
            getLogger().warning("TradingGUIがnullです。初期化に問題がある可能性があります。");
        }
        return tradingGUI;
    }
    
    public org.tofu.tofunomics.npc.gui.FoodGUI getFoodGUI() {
        return foodGUI;
    }
    
    public org.tofu.tofunomics.npc.gui.ProcessingGUI getProcessingGUI() {
        return processingGUI;
    }
    
    // Phase 6 クラフト制限システムGetter
    public org.tofu.tofunomics.jobs.JobCraftPermissionManager getJobCraftPermissionManager() {
        return jobCraftPermissionManager;
    }

    /**
     * エリアシステムの初期化
     */
    private void initializeAreaSystem() {
        try {
            if (!getConfig().getBoolean("area_system.enabled", true)) {
                getLogger().info("エリアシステムは無効化されています");
                return;
            }

            getLogger().info("エリアシステムを初期化しています...");

            // AreaManagerの初期化
            this.areaManager = new org.tofu.tofunomics.area.AreaManager(
                this,
                configManager,
                getLogger()
            );

            // AreaListenerの初期化
            this.areaListener = new org.tofu.tofunomics.area.AreaListener(
                this,
                areaManager
            );

            getLogger().info("エリアシステムを正常に初期化しました（エリア数: " + areaManager.getAreaCount() + "）");
        } catch (Exception e) {
            getLogger().severe("エリアシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * エリアシステムのクリーンアップ
     */
    private void cleanupAreaSystem() {
        if (areaListener != null) {
            areaListener.cleanup();
        }
        if (areaManager != null) {
            areaManager.cleanup();
        }
        getLogger().info("エリアシステムをクリーンアップしました");
    }

    private void initializeHousingSystem() {
        try {
            if (!getConfig().getBoolean("housing_rental.enabled", true)) {
                getLogger().info("住居賃貸システムは無効化されています");
                return;
            }
            
            getLogger().info("住居賃貸システムを初期化しています...");
            
            // WorldGuard統合の初期化（最初に初期化）
            this.worldGuardIntegration = new org.tofu.tofunomics.integration.WorldGuardIntegration(this);
            
            // テストモードマネージャーの初期化
            this.testModeManager = new org.tofu.tofunomics.testing.TestModeManager(this);
            
            // WorldGuardテストモードリスナーの初期化（worldGuardIntegrationとtestModeManager両方が準備できてから）
            this.worldGuardTestModeListener = new org.tofu.tofunomics.testing.WorldGuardTestModeListener(this, testModeManager, worldGuardIntegration);
            
            // HousingRentalManager の初期化
            this.housingRentalManager = new org.tofu.tofunomics.housing.HousingRentalManager(
                this,
                configManager,
                databaseManager,
                worldGuardIntegration
            );
            
            // SelectionManager の初期化
            this.selectionManager = new org.tofu.tofunomics.housing.SelectionManager();
            
            // 選択ツールの設定
            String toolName = getConfig().getString("housing_rental.selection_tool", "WOODEN_AXE");
            try {
                Material tool = Material.valueOf(toolName);
                selectionManager.setSelectionTool(tool);
            } catch (IllegalArgumentException e) {
                getLogger().warning("無効な選択ツール: " + toolName + " - WOODEN_AXEを使用します");
            }
            
            // HousingListener の初期化
            this.housingListener = new org.tofu.tofunomics.housing.HousingListener(this, selectionManager, testModeManager);
            
            // 期限切れチェックタスクの開始
            startHousingExpiryTask();
            
            getLogger().info("住居賃貸システムを正常に初期化しました");
        } catch (Exception e) {
            getLogger().severe("住居賃貸システムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void cleanupHousingSystem() {
        if (selectionManager != null) {
            selectionManager.clearAllSelections();
        }
        if (testModeManager != null) {
            testModeManager.clearAll();
        }
        getLogger().info("住居賃貸システムをクリーンアップしました");
    }
    
    private void startHousingExpiryTask() {
        if (housingRentalManager == null) {
            return;
        }
        
        int intervalSeconds = getConfig().getInt("housing_rental.expire_check_interval", 3600);
        long intervalTicks = intervalSeconds * 20L; // 秒をTickに変換
        
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                housingRentalManager.processExpiredRentals();
            } catch (Exception e) {
                getLogger().warning("賃貸契約期限切れチェック中にエラーが発生しました: " + e.getMessage());
            }
        }, intervalTicks, intervalTicks);
        
        getLogger().info("賃貸契約期限切れチェックタスクを開始しました（" + intervalSeconds + "秒ごと）");
    }
    
    /**
     * HousingRentalManagerの取得
     */
    public org.tofu.tofunomics.housing.HousingRentalManager getHousingRentalManager() {
        return housingRentalManager;
    }

    /**
     * SelectionManagerの取得
     */
    public org.tofu.tofunomics.housing.SelectionManager getSelectionManager() {
        return selectionManager;
    }

    /**
     * AreaManagerの取得
     */
    public org.tofu.tofunomics.area.AreaManager getAreaManager() {
        return areaManager;
    }
    
    /**
     * 時刻放送システムの初期化
     */
    private void initializeTimeAnnouncementSystem() {
        try {
            getLogger().info("時刻放送システムを初期化しています...");
            
            timeAnnouncementSystem = new org.tofu.tofunomics.announcement.TimeAnnouncementSystem(this, configManager);
            timeAnnouncementSystem.start();
            
            getLogger().info("時刻放送システムが初期化されました");
        } catch (Exception e) {
            getLogger().severe("時刻放送システムの初期化に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * TimeAnnouncementSystemの取得
     */
    public org.tofu.tofunomics.announcement.TimeAnnouncementSystem getTimeAnnouncementSystem() {
        return timeAnnouncementSystem;
    }
    
    /**
     * 時計アイテムシステムの初期化
     */
    private void initializeClockItemSystem() {
        try {
            getLogger().info("時計アイテムシステムを初期化しています...");
            
            clockItemManager = new org.tofu.tofunomics.items.ClockItemManager(this, configManager);
            clockItemManager.startActionBarTask();
            
            // イベントリスナーを登録
            getServer().getPluginManager().registerEvents(clockItemManager, this);
            
            getLogger().info("時計アイテムシステムが初期化されました");
        } catch (Exception e) {
            getLogger().severe("時計アイテムシステムの初期化に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ClockItemManagerの取得
     */
    public org.tofu.tofunomics.items.ClockItemManager getClockItemManager() {
        return clockItemManager;
    }
    
    /**
     * ルール確認システムの初期化
     */
    private void initializeRulesSystem() {
        try {
            getLogger().info("ルール確認システムを初期化しています...");
            
            // RulesManagerの初期化
            rulesManager = new org.tofu.tofunomics.rules.RulesManager(
                this,
                configManager,
                playerDAO
            );
            
            getLogger().info("ルール確認システムの初期化が完了しました");
            
            // 既存のオンラインプレイヤーに対してルール同意チェック
            getServer().getScheduler().runTaskLater(this, () -> {
                for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                    boolean hasAgreed = rulesManager.hasAgreedToRules(player.getUniqueId());
                    if (!hasAgreed) {
                        getLogger().info("オンラインプレイヤー " + player.getName() + " はルール未同意です");
                        rulesManager.markAsUnagreed(player.getUniqueId());
                        player.sendMessage(configManager.getMessage("rules.messages.must_agree"));
                        
                        // 2秒後にルールGUIを表示
                        getServer().getScheduler().runTaskLater(this, () -> {
                            rulesManager.getRulesGUI().openRulesGUI(player, 1);
                        }, 40L); // 2秒後
                    }
                }
            }, 60L); // 3秒後に実行（プラグイン初期化完了を待つ）
            
        } catch (Exception e) {
            getLogger().severe("ルール確認システムの初期化に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * RulesManagerの取得
     */
    public org.tofu.tofunomics.rules.RulesManager getRulesManager() {
        return rulesManager;
    }

    /**
     * TestModeManagerの取得
     */
    public org.tofu.tofunomics.testing.TestModeManager getTestModeManager() {
        return testModeManager;
    }


    /**
     * WorldGuard統合を取得
     */
    public org.tofu.tofunomics.integration.WorldGuardIntegration getWorldGuardIntegration() {
        return worldGuardIntegration;
    }
}
