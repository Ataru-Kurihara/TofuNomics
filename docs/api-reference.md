# TofuNomics API リファレンス

## 🔧 開発者向けAPI仕様書

TofuNomicsプラグインの拡張・連携開発のためのAPI仕様書です。

## 📋 目次

- [基本概要](#基本概要)
- [主要クラス構成](#主要クラス構成)
- [経済システムAPI](#経済システムapi)
- [職業システムAPI](#職業システムapi)
- [データベースAPI](#データベースapi)
- [イベントAPI](#イベントapi)
- [設定管理API](#設定管理api)
- [拡張開発ガイド](#拡張開発ガイド)

## 🏗️ 基本概要

### プラグインアーキテクチャ
```
TofuNomics/
├── core/           # コアシステム
├── economy/        # 経済システム
├── jobs/          # 職業システム
├── database/      # データ永続化
├── events/        # イベントハンドリング
├── commands/      # コマンドシステム
├── config/        # 設定管理
├── cache/         # キャッシュシステム
└── utils/         # ユーティリティ
```

### 依存関係
- **Java**: 8+
- **Bukkit/Spigot**: 1.16.5
- **SQLite**: JDBC 3.36.0
- **JUnit**: 4.13.2（テスト用）

## 🎯 主要クラス構成

### コアクラス

#### TofuNomics.java
```java
public class TofuNomics extends JavaPlugin {
    // プラグインのメインクラス
    public static TofuNomics getInstance()
    public EconomyManager getEconomyManager()
    public JobManager getJobManager()
    public DatabaseManager getDatabaseManager()
    public ConfigManager getConfigManager()
    public CacheManager getCacheManager()
}
```

### マネージャークラス階層
```java
// 基底インターフェース
public interface Manager {
    void initialize();
    void shutdown();
    boolean isInitialized();
}

// 実装クラス
- EconomyManager implements Manager
- JobManager implements Manager  
- DatabaseManager implements Manager
- ConfigManager implements Manager
- CacheManager implements Manager
```

## 💰 経済システムAPI

### EconomyManager

#### 基本メソッド
```java
public class EconomyManager implements Manager {
    
    /**
     * プレイヤーの残高を取得
     * @param uuid プレイヤーUUID
     * @return 残高（金塊）
     */
    public double getBalance(UUID uuid);
    
    /**
     * プレイヤーの残高を設定
     * @param uuid プレイヤーUUID
     * @param amount 設定する金額
     * @return 成功時true
     */
    public boolean setBalance(UUID uuid, double amount);
    
    /**
     * プレイヤーに金額を追加
     * @param uuid プレイヤーUUID
     * @param amount 追加する金額
     * @return 成功時true
     */
    public boolean addBalance(UUID uuid, double amount);
    
    /**
     * プレイヤーから金額を減算
     * @param uuid プレイヤーUUID
     * @param amount 減算する金額
     * @return 成功時true（残高不足時false）
     */
    public boolean subtractBalance(UUID uuid, double amount);
    
    /**
     * 残高の存在チェック
     * @param uuid プレイヤーUUID
     * @param amount チェックする金額
     * @return 残高が足りる場合true
     */
    public boolean hasBalance(UUID uuid, double amount);
}
```

#### 取引メソッド
```java
public class EconomyManager {
    
    /**
     * プレイヤー間送金
     * @param from 送金者UUID
     * @param to 受取者UUID
     * @param amount 送金額
     * @return 取引結果
     */
    public TransactionResult transfer(UUID from, UUID to, double amount);
    
    /**
     * 取引手数料計算
     * @param amount 取引額
     * @return 手数料
     */
    public double calculateFee(double amount);
    
    /**
     * 取引履歴追加
     * @param transaction 取引データ
     */
    public void addTransaction(Transaction transaction);
}
```

#### 通貨変換
```java
public class CurrencyConverter {
    
    /**
     * 金塊アイテムを銀行残高に変換
     * @param player プレイヤー
     * @param amount 変換する金塊数
     * @return 成功時true
     */
    public boolean depositGoldIngots(Player player, int amount);
    
    /**
     * 銀行残高を金塊アイテムに変換
     * @param player プレイヤー
     * @param amount 変換する金額
     * @return 成功時true
     */
    public boolean withdrawGoldIngots(Player player, double amount);
}
```

### データモデル

#### Transaction
```java
public class Transaction {
    private UUID id;
    private UUID fromPlayer;
    private UUID toPlayer;
    private double amount;
    private double fee;
    private TransactionType type;
    private Timestamp timestamp;
    private String description;
    
    // コンストラクタ、ゲッター、セッター
}

public enum TransactionType {
    TRANSFER,    // プレイヤー間送金
    DEPOSIT,     // 預金
    WITHDRAWAL,  // 引き出し
    JOB_INCOME, // 職業収入
    TRADE_SALE, // 取引売却
    ADMIN       // 管理者操作
}
```

## 👨‍💼 職業システムAPI

### JobManager

#### 職業操作
```java
public class JobManager implements Manager {
    
    /**
     * プレイヤーの現在職業を取得
     * @param uuid プレイヤーUUID
     * @return 職業情報（未参加時はnull）
     */
    public Job getCurrentJob(UUID uuid);
    
    /**
     * 職業に参加
     * @param uuid プレイヤーUUID
     * @param jobType 職業タイプ
     * @return 成功時true
     */
    public boolean joinJob(UUID uuid, JobType jobType);
    
    /**
     * 職業から離脱
     * @param uuid プレイヤーUUID
     * @return 成功時true
     */
    public boolean leaveJob(UUID uuid);
    
    /**
     * 職業レベルを取得
     * @param uuid プレイヤーUUID
     * @param jobType 職業タイプ
     * @return レベル
     */
    public int getJobLevel(UUID uuid, JobType jobType);
    
    /**
     * 職業経験値を取得
     * @param uuid プレイヤーUUID
     * @param jobType 職業タイプ
     * @return 経験値
     */
    public int getJobExperience(UUID uuid, JobType jobType);
}
```

#### 経験値・レベルシステム
```java
public class ExperienceManager {
    
    /**
     * 経験値を追加
     * @param uuid プレイヤーUUID
     * @param jobType 職業タイプ
     * @param amount 経験値量
     * @return レベルアップした場合true
     */
    public boolean addExperience(UUID uuid, JobType jobType, int amount);
    
    /**
     * レベルアップチェック
     * @param currentExp 現在の経験値
     * @param currentLevel 現在のレベル
     * @return 新しいレベル
     */
    public int calculateLevel(int currentExp, int currentLevel);
    
    /**
     * 次のレベルに必要な経験値を計算
     * @param level 現在のレベル
     * @return 必要経験値
     */
    public int getRequiredExperience(int level);
}
```

#### スキルシステム
```java
public class SkillManager {
    
    /**
     * スキルを実行
     * @param player プレイヤー
     * @param skill スキル
     * @return 実行結果
     */
    public SkillResult executeSkill(Player player, Skill skill);
    
    /**
     * スキルクールダウンチェック
     * @param uuid プレイヤーUUID
     * @param skillType スキルタイプ
     * @return クールダウン中の場合true
     */
    public boolean isSkillOnCooldown(UUID uuid, SkillType skillType);
    
    /**
     * プレイヤーの解放済みスキル一覧
     * @param uuid プレイヤーUUID
     * @param jobType 職業タイプ
     * @return スキル一覧
     */
    public List<Skill> getUnlockedSkills(UUID uuid, JobType jobType);
}
```

### データモデル

#### JobType列挙型
```java
public enum JobType {
    FARMER("農家", "farming"),
    MINER("鉱夫", "mining"),
    WOODCUTTER("木こり", "woodcutting"),
    FISHERMAN("釣り人", "fishing"),
    ENCHANTER("エンチャンター", "enchanting"),
    BREWER("醸造師", "brewing"),
    BUILDER("建築家", "building"),
    MERCHANT("商人", "trading");
    
    private final String displayName;
    private final String identifier;
    
    // コンストラクタ、ゲッター
}
```

#### Job クラス
```java
public class Job {
    private JobType type;
    private int level;
    private int experience;
    private Date joinDate;
    private Date lastActivity;
    private List<Skill> unlockedSkills;
    
    // コンストラクタ、ゲッター、セッター
}
```

#### Skill クラス
```java
public class Skill {
    private SkillType type;
    private int unlockLevel;
    private double effectChance;
    private int cooldownSeconds;
    private String description;
    private Map<String, Object> parameters;
    
    // コンストラクタ、ゲッター、セッター
}
```

## 🗄️ データベースAPI

### DatabaseManager

#### 基本操作
```java
public class DatabaseManager implements Manager {
    
    /**
     * データベース接続を取得
     * @return 接続オブジェクト
     */
    public Connection getConnection();
    
    /**
     * プレイヤーデータを保存
     * @param uuid プレイヤーUUID
     * @param data プレイヤーデータ
     * @return 成功時true
     */
    public boolean savePlayerData(UUID uuid, PlayerData data);
    
    /**
     * プレイヤーデータを読み込み
     * @param uuid プレイヤーUUID
     * @return プレイヤーデータ
     */
    public PlayerData loadPlayerData(UUID uuid);
    
    /**
     * バッチ処理でデータを更新
     * @param queries SQLクエリリスト
     * @return 成功時true
     */
    public boolean executeBatch(List<String> queries);
}
```

#### DAO（Data Access Object）パターン
```java
// プレイヤーDAO
public class PlayerDAO {
    public boolean save(Player player);
    public Player findByUUID(UUID uuid);
    public List<Player> findAll();
    public boolean delete(UUID uuid);
}

// 職業DAO
public class JobDAO {
    public boolean savePlayerJob(UUID uuid, JobType type, int level, int exp);
    public PlayerJob findPlayerJob(UUID uuid);
    public List<PlayerJob> findJobsByType(JobType type);
}

// 取引DAO
public class TransactionDAO {
    public boolean save(Transaction transaction);
    public List<Transaction> findByPlayer(UUID uuid);
    public List<Transaction> findByDateRange(Date from, Date to);
}
```

## 🎭 イベントAPI

### カスタムイベント

#### JobEvent
```java
public class JobLevelUpEvent extends Event {
    private final UUID playerUUID;
    private final JobType jobType;
    private final int newLevel;
    private final int oldLevel;
    
    public JobLevelUpEvent(UUID playerUUID, JobType jobType, 
                          int oldLevel, int newLevel) {
        this.playerUUID = playerUUID;
        this.jobType = jobType;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }
    
    // ゲッターメソッド
}

// イベントリスナーの例
@EventHandler
public void onJobLevelUp(JobLevelUpEvent event) {
    // レベルアップ処理
    Player player = Bukkit.getPlayer(event.getPlayerUUID());
    if (player != null) {
        player.sendMessage("レベルアップ！" + event.getJobType().getDisplayName() 
                         + " Lv." + event.getNewLevel());
    }
}
```

#### EconomyEvent
```java
public class EconomyTransactionEvent extends Event {
    private final UUID fromPlayer;
    private final UUID toPlayer;
    private final double amount;
    private final TransactionType type;
    private boolean cancelled = false;
    
    // コンストラクタ、ゲッター、セッター
}
```

### イベントハンドラー登録
```java
public class MyPlugin extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
        // イベントリスナー登録
        getServer().getPluginManager().registerEvents(this, this);
    }
    
    @EventHandler
    public void onJobLevelUp(JobLevelUpEvent event) {
        // 処理内容
    }
    
    @EventHandler  
    public void onEconomyTransaction(EconomyTransactionEvent event) {
        // 取引時の処理
        if (event.getAmount() > 10000) {
            // 高額取引の場合はログ出力
            getLogger().info("High value transaction: " + event.getAmount());
        }
    }
}
```

## ⚙️ 設定管理API

### ConfigManager

#### 設定読み込み
```java
public class ConfigManager implements Manager {
    
    /**
     * 設定値を取得
     * @param path 設定パス（例: "jobs.farmer.base-income"）
     * @return 設定値
     */
    public Object getValue(String path);
    
    /**
     * 設定値を取得（デフォルト値付き）
     * @param path 設定パス
     * @param defaultValue デフォルト値
     * @return 設定値
     */
    public <T> T getValue(String path, T defaultValue);
    
    /**
     * 設定をリロード
     * @return 成功時true
     */
    public boolean reload();
    
    /**
     * 設定変更通知リスナー追加
     * @param listener リスナー
     */
    public void addConfigChangeListener(ConfigChangeListener listener);
}
```

#### 設定バリデーション
```java
public class ConfigValidator {
    
    /**
     * 設定値の妥当性チェック
     * @param config 設定データ
     * @return バリデーション結果
     */
    public ValidationResult validate(FileConfiguration config);
    
    /**
     * 数値範囲チェック
     * @param value 値
     * @param min 最小値
     * @param max 最大値
     * @return 範囲内の場合true
     */
    public boolean validateRange(double value, double min, double max);
}
```

## 🔧 拡張開発ガイド

### プラグイン連携

#### 他プラグインからの利用例
```java
public class MyExtensionPlugin extends JavaPlugin {
    
    private TofuNomics tofuNomics;
    
    @Override
    public void onEnable() {
        // TofuNomicsプラグインの取得
        tofuNomics = (TofuNomics) getServer().getPluginManager()
                                           .getPlugin("TofuNomics");
        
        if (tofuNomics == null || !tofuNomics.isEnabled()) {
            getLogger().severe("TofuNomics not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // API使用例
        EconomyManager economy = tofuNomics.getEconomyManager();
        JobManager jobs = tofuNomics.getJobManager();
    }
    
    // カスタム機能の実装
    public void giveJobBonus(Player player) {
        UUID uuid = player.getUniqueId();
        Job currentJob = jobs.getCurrentJob(uuid);
        
        if (currentJob != null && currentJob.getLevel() >= 50) {
            economy.addBalance(uuid, 1000.0);
            player.sendMessage("高レベル職業ボーナス: 1000金塊獲得！");
        }
    }
}
```

### カスタムスキル開発

#### スキルインターフェース
```java
public interface CustomSkill {
    
    /**
     * スキル実行
     * @param player 実行プレイヤー
     * @param context スキル実行コンテキスト
     * @return 実行結果
     */
    SkillResult execute(Player player, SkillContext context);
    
    /**
     * スキル実行条件チェック
     * @param player プレイヤー
     * @return 実行可能な場合true
     */
    boolean canExecute(Player player);
    
    /**
     * スキル設定取得
     * @return スキル設定
     */
    SkillConfiguration getConfiguration();
}
```

#### カスタムスキル実装例
```java
public class SuperHarvestSkill implements CustomSkill {
    
    @Override
    public SkillResult execute(Player player, SkillContext context) {
        // 特別な収穫スキルの実装
        if (context.getEventType() == EventType.BLOCK_BREAK) {
            Block block = context.getBlock();
            if (isCrop(block)) {
                // 収穫量を3倍にする
                return SkillResult.success().withMultiplier(3.0);
            }
        }
        return SkillResult.failure("実行条件を満たしていません");
    }
    
    @Override
    public boolean canExecute(Player player) {
        JobManager jobs = TofuNomics.getInstance().getJobManager();
        Job job = jobs.getCurrentJob(player.getUniqueId());
        return job != null && job.getType() == JobType.FARMER 
               && job.getLevel() >= 75;
    }
    
    private boolean isCrop(Block block) {
        Material type = block.getType();
        return type == Material.WHEAT || type == Material.CARROTS 
               || type == Material.POTATOES;
    }
}
```

### データベース拡張

#### カスタムテーブル追加
```java
public class CustomTableDAO {
    
    private final DatabaseManager dbManager;
    
    public CustomTableDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        createTable();
    }
    
    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS custom_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                data_type TEXT NOT NULL,
                data_value TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (player_uuid) REFERENCES players(uuid)
            )
            """;
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            // エラーハンドリング
        }
    }
}
```

### パフォーマンス最適化

#### 非同期処理の実装
```java
public class AsyncTaskManager {
    
    private final TofuNomics plugin;
    private final ExecutorService executor;
    
    public AsyncTaskManager(TofuNomics plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 非同期でデータベース操作実行
     * @param task データベースタスク
     * @param callback 完了コールバック
     */
    public void executeAsync(Callable<Void> task, 
                           Consumer<Boolean> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                task.call();
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Async task failed: " + e.getMessage());
                return false;
            }
        }, executor).thenAccept(callback);
    }
}
```

## 📚 コードサンプル集

### 基本的な使用例

#### プレイヤーの職業情報表示
```java
public void displayJobInfo(Player player) {
    TofuNomics plugin = TofuNomics.getInstance();
    JobManager jobManager = plugin.getJobManager();
    EconomyManager economyManager = plugin.getEconomyManager();
    
    UUID uuid = player.getUniqueId();
    Job currentJob = jobManager.getCurrentJob(uuid);
    double balance = economyManager.getBalance(uuid);
    
    if (currentJob != null) {
        player.sendMessage("§a=== 職業情報 ===");
        player.sendMessage("§b職業: §f" + currentJob.getType().getDisplayName());
        player.sendMessage("§bレベル: §f" + currentJob.getLevel());
        player.sendMessage("§b経験値: §f" + currentJob.getExperience());
        player.sendMessage("§b残高: §f" + balance + "金塊");
    } else {
        player.sendMessage("§c職業に参加していません");
    }
}
```

#### カスタム取引処理
```java
public class CustomTradeHandler {
    
    public void processCustomTrade(Player seller, ItemStack item, double price) {
        TofuNomics plugin = TofuNomics.getInstance();
        EconomyManager economy = plugin.getEconomyManager();
        JobManager jobs = plugin.getJobManager();
        
        UUID sellerUUID = seller.getUniqueId();
        Job sellerJob = jobs.getCurrentJob(sellerUUID);
        
        // 職業ボーナス計算
        double bonus = 1.0;
        if (sellerJob != null && sellerJob.getType() == JobType.MERCHANT) {
            bonus = 1.0 + (sellerJob.getLevel() * 0.02); // レベルごとに2%ボーナス
        }
        
        double finalPrice = price * bonus;
        
        // 取引実行
        if (economy.addBalance(sellerUUID, finalPrice)) {
            seller.sendMessage("§a取引成功！ " + finalPrice + "金塊を獲得しました");
            
            // 経験値付与
            if (sellerJob != null && sellerJob.getType() == JobType.MERCHANT) {
                plugin.getJobManager().addExperience(sellerUUID, JobType.MERCHANT, 50);
            }
        }
    }
}
```

## 🧪 テスト・デバッグ

### ユニットテストの作成
```java
public class EconomyManagerTest {
    
    private EconomyManager economyManager;
    private UUID testUUID;
    
    @Before
    public void setUp() {
        economyManager = new EconomyManager();
        testUUID = UUID.randomUUID();
    }
    
    @Test
    public void testBalanceOperations() {
        // 初期残高設定
        assertTrue(economyManager.setBalance(testUUID, 1000.0));
        assertEquals(1000.0, economyManager.getBalance(testUUID), 0.01);
        
        // 残高追加
        assertTrue(economyManager.addBalance(testUUID, 500.0));
        assertEquals(1500.0, economyManager.getBalance(testUUID), 0.01);
        
        // 残高減算
        assertTrue(economyManager.subtractBalance(testUUID, 200.0));
        assertEquals(1300.0, economyManager.getBalance(testUUID), 0.01);
        
        // 残高不足チェック
        assertFalse(economyManager.subtractBalance(testUUID, 2000.0));
    }
}
```

### ログ出力とデバッグ
```java
public class DebugUtils {
    
    private static final Logger logger = TofuNomics.getInstance().getLogger();
    
    public static void debugPlayerData(UUID uuid) {
        logger.info("=== Player Debug Info ===");
        logger.info("UUID: " + uuid.toString());
        
        EconomyManager economy = TofuNomics.getInstance().getEconomyManager();
        logger.info("Balance: " + economy.getBalance(uuid));
        
        JobManager jobs = TofuNomics.getInstance().getJobManager();
        Job currentJob = jobs.getCurrentJob(uuid);
        if (currentJob != null) {
            logger.info("Job: " + currentJob.getType().name());
            logger.info("Level: " + currentJob.getLevel());
            logger.info("Experience: " + currentJob.getExperience());
        } else {
            logger.info("No active job");
        }
    }
    
    public static void logPerformance(String operation, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        if (duration > 100) { // 100ms以上の場合は警告
            logger.warning("Slow operation detected: " + operation 
                         + " took " + duration + "ms");
        }
    }
}
```

## 📖 リファレンス

### 設定ファイル構造
```yaml
# config.yml の主要セクション
database:
  type: "sqlite"
  file: "data/database.db"

economy:
  starting-balance: 1000.0
  max-balance: 1000000.0

jobs:
  farmer:
    max-level: 100
    base-income: 10.0
    skills:
      harvest-boost:
        unlock-level: 10
        effect-chance: 0.15

performance:
  cache:
    player-data-cache-size: 100
  async:
    worker-threads: 4
```

### よくある問題と解決策

#### メモリリーク対策
```java
// 適切なリソース管理
try (Connection conn = dbManager.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    // データベース操作
} catch (SQLException e) {
    logger.severe("Database error: " + e.getMessage());
}

// 弱参照を使用したキャッシュ
private final Map<UUID, WeakReference<PlayerData>> cache = 
    new ConcurrentHashMap<>();
```

#### 非同期処理のベストプラクティス
```java
// メインスレッドでのUI更新
Bukkit.getScheduler().runTask(plugin, () -> {
    player.sendMessage("処理完了！");
});

// 非同期での重い処理
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // データベース操作など
    // メインスレッドに戻る場合は上記のrunTaskを使用
});
```

---

## 📞 サポート情報

### 開発者サポート
- **GitHub**: [リポジトリURL]
- **API仕様更新**: バージョンアップ時に随時更新
- **サンプルコード**: `examples/` ディレクトリ参照

### 貢献ガイドライン
1. **コーディング規約**: Java標準規約準拠
2. **テスト**: 新機能には必ずテストを作成
3. **ドキュメント**: APIの変更時は本ドキュメント更新
4. **プルリクエスト**: 詳細な説明とテスト結果を添付

---

**TofuNomics API で素晴らしい拡張機能を開発してください！** 🚀