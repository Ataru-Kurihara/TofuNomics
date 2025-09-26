package org.tofu.tofunomics.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HikariCP を使用した高性能データベース管理システム
 * コネクションプールによる最適化とパフォーマンス監視機能
 */
public class HikariDatabaseManager {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    
    private HikariDataSource dataSource;
    private boolean isInitialized = false;
    
    // パフォーマンス統計
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong failedQueries = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    
    public HikariDatabaseManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }
    
    /**
     * データベース接続とテーブルの初期化
     */
    public boolean initialize() {
        try {
            // データフォルダの作成
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // データベースファイルパスの決定
            File databaseFile = new File(plugin.getDataFolder(), configManager.getDatabaseFilename());
            String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            
            // HikariCP設定
            setupHikariConfig(jdbcUrl);
            
            // テーブル作成
            createTables();
            
            // デフォルトデータの挿入
            insertDefaultData();
            
            isInitialized = true;
            logger.info("HikariCP データベース接続を初期化しました。");
            logger.info("データベースファイル: " + databaseFile.getAbsolutePath());
            logger.info("コネクションプール設定: 最大" + dataSource.getMaximumPoolSize() + 
                       "接続, 最小アイドル" + dataSource.getMinimumIdle() + "接続");
            
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "データベース初期化に失敗しました", e);
            return false;
        }
    }
    
    /**
     * HikariCP設定のセットアップ
     */
    private void setupHikariConfig(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        
        // 基本設定
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        
        // コネクションプール設定
        if (configManager.isConnectionPoolEnabled()) {
            config.setMaximumPoolSize(configManager.getMaximumPoolSize());
            config.setMinimumIdle(configManager.getMinimumIdle());
            config.setConnectionTimeout(configManager.getConnectionTimeout());
            config.setIdleTimeout(600000); // 10分
            config.setMaxLifetime(1800000); // 30分
            config.setLeakDetectionThreshold(60000); // 1分
        } else {
            // 基本設定（非プール使用時）
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
        }
        
        // SQLite固有の設定
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        // 接続プール名
        config.setPoolName("TofuNomics-HikariCP");
        
        // データソースの作成
        dataSource = new HikariDataSource(config);
        
        logger.info("HikariCP設定を完了しました。");
    }
    
    /**
     * データベーステーブルの作成
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            // プレイヤーテーブル
            String createPlayersTable = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid TEXT PRIMARY KEY," +
                "balance REAL DEFAULT 100.0," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            // 職業テーブル
            String createJobsTable = "CREATE TABLE IF NOT EXISTS jobs (" +
                "job_id TEXT PRIMARY KEY," +
                "job_name TEXT NOT NULL," +
                "max_level INTEGER DEFAULT 75," +
                "base_income REAL DEFAULT 1.0," +
                "description TEXT" +
                ")";
            
            // プレイヤー職業テーブル
            String createPlayerJobsTable = "CREATE TABLE IF NOT EXISTS player_jobs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "job_id TEXT NOT NULL," +
                "level INTEGER DEFAULT 1," +
                "experience REAL DEFAULT 0.0," +
                "is_active BOOLEAN DEFAULT 1," +
                "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "last_used DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)," +
                "FOREIGN KEY (job_id) REFERENCES jobs(job_id)" +
                ")";
            
            // 職業スキルテーブル
            String createJobSkillsTable = "CREATE TABLE IF NOT EXISTS job_skills (" +
                "skill_id TEXT PRIMARY KEY," +
                "job_id TEXT NOT NULL," +
                "skill_name TEXT NOT NULL," +
                "unlock_level INTEGER DEFAULT 1," +
                "skill_type TEXT NOT NULL," +
                "effect_description TEXT," +
                "FOREIGN KEY (job_id) REFERENCES jobs(job_id)" +
                ")";
            
            // プレイヤースキルテーブル
            String createPlayerSkillsTable = "CREATE TABLE IF NOT EXISTS player_skills (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "skill_id TEXT NOT NULL," +
                "acquired_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "skill_level INTEGER DEFAULT 1," +
                "usage_count INTEGER DEFAULT 0," +
                "last_used DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)," +
                "FOREIGN KEY (skill_id) REFERENCES job_skills(skill_id)" +
                ")";
            
            // 土地所有テーブル
            String createLandOwnershipTable = "CREATE TABLE IF NOT EXISTS land_ownership (" +
                "land_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid TEXT NOT NULL," +
                "world_name TEXT NOT NULL," +
                "x INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "size INTEGER DEFAULT 16," +
                "purchase_price REAL NOT NULL," +
                "purchased_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (owner_uuid) REFERENCES players(uuid)" +
                ")";
            
            // 取引チェストテーブル（フェーズ4）
            String createTradeChestsTable = "CREATE TABLE IF NOT EXISTS trade_chests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid TEXT NOT NULL," +
                "job_type TEXT NOT NULL," +
                "world_name TEXT NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "last_used DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (owner_uuid) REFERENCES players(uuid)" +
                ")";
            
            // プレイヤー取引履歴テーブル（フェーズ4）
            String createPlayerTradeHistoryTable = "CREATE TABLE IF NOT EXISTS player_trade_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "item_type TEXT NOT NULL," +
                "quantity INTEGER NOT NULL," +
                "unit_price REAL NOT NULL," +
                "total_amount REAL NOT NULL," +
                "job_type TEXT NOT NULL," +
                "job_level INTEGER NOT NULL," +
                "price_bonus REAL DEFAULT 0.0," +
                "trade_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (player_uuid) REFERENCES players(uuid)" +
                ")";
            
            // パフォーマンス統計テーブル（フェーズ6）
            String createPerformanceStatsTable = "CREATE TABLE IF NOT EXISTS performance_stats (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "stat_type TEXT NOT NULL," +
                "stat_value REAL NOT NULL," +
                "recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            // インデックスの作成
            String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_player_jobs_uuid ON player_jobs(uuid)",
                "CREATE INDEX IF NOT EXISTS idx_player_jobs_job_id ON player_jobs(job_id)",
                "CREATE INDEX IF NOT EXISTS idx_player_skills_uuid ON player_skills(uuid)",
                "CREATE INDEX IF NOT EXISTS idx_trade_chests_location ON trade_chests(world_name, x, y, z)",
                "CREATE INDEX IF NOT EXISTS idx_trade_history_player ON player_trade_history(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_trade_history_date ON player_trade_history(trade_date)",
                "CREATE INDEX IF NOT EXISTS idx_performance_stats_type ON performance_stats(stat_type, recorded_at)"
            };
            
            // テーブル作成実行
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createPlayersTable);
                stmt.execute(createJobsTable);
                stmt.execute(createPlayerJobsTable);
                stmt.execute(createJobSkillsTable);
                stmt.execute(createPlayerSkillsTable);
                stmt.execute(createLandOwnershipTable);
                stmt.execute(createTradeChestsTable);
                stmt.execute(createPlayerTradeHistoryTable);
                stmt.execute(createPerformanceStatsTable);
                
                // インデックス作成
                for (String indexSql : indexes) {
                    stmt.execute(indexSql);
                }
                
                logger.info("データベーステーブルとインデックスを作成しました。");
            }
        }
    }
    
    /**
     * デフォルトデータの挿入
     */
    private void insertDefaultData() throws SQLException {
        insertDefaultJobs();
        insertDefaultJobSkills();
    }
    
    /**
     * デフォルト職業データの挿入
     */
    private void insertDefaultJobs() throws SQLException {
        String insertJobQuery = "INSERT OR IGNORE INTO jobs (job_id, job_name, max_level, base_income, description) " +
            "VALUES (?, ?, ?, ?, ?)";
        
        Object[][] jobData = {
            {"miner", "鉱夫", 75, 1.0, "鉱物資源と石材の供給を担当"},
            {"woodcutter", "木こり", 75, 1.0, "木材の供給を担当"},
            {"farmer", "農家", 75, 1.0, "農作物・畜産物の供給を担当"},
            {"fisherman", "釣り人", 75, 1.0, "魚と宝の供給を担当"},
            {"blacksmith", "鍛冶屋", 75, 1.0, "ツール、武器、防具の生産と修理を担当"},
            {"alchemist", "ポーション屋", 75, 1.0, "ポーションの生産を担当"},
            {"enchanter", "エンチャンター", 75, 1.0, "エンチャントサービスを担当"},
            {"architect", "建築家", 75, 1.0, "建築と装飾を担当"}
        };
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertJobQuery)) {
            
            for (Object[] job : jobData) {
                pstmt.setString(1, (String) job[0]);
                pstmt.setString(2, (String) job[1]);
                pstmt.setInt(3, (Integer) job[2]);
                pstmt.setDouble(4, (Double) job[3]);
                pstmt.setString(5, (String) job[4]);
                pstmt.executeUpdate();
            }
            
            logger.info("デフォルト職業データを挿入しました。");
        }
    }
    
    /**
     * デフォルト職業スキルデータの挿入
     */
    private void insertDefaultJobSkills() throws SQLException {
        String insertSkillQuery = "INSERT OR IGNORE INTO job_skills (skill_id, job_id, skill_name, unlock_level, skill_type, effect_description) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        
        Object[][] skillData = {
            // 鉱夫スキル
            {"miner_fortune_strike", "miner", "幸運の一撃", 10, "passive", "追加ドロップ確率向上"},
            {"miner_vein_discovery", "miner", "鉱脈発見", 25, "active", "連続採掘ボーナス"},
            {"miner_mining_mastery", "miner", "採掘の極意", 50, "passive", "ツール耐久度節約"},
            
            // 木こりスキル
            {"woodcutter_tree_feller", "woodcutter", "一斉伐採", 15, "active", "木全体を一度に伐採"},
            {"woodcutter_sapling_blessing", "woodcutter", "苗木の恵み", 30, "passive", "苗木ドロップボーナス"},
            {"woodcutter_forest_guardian", "woodcutter", "森の番人", 45, "passive", "木材品質向上"},
            
            // 農家スキル
            {"farmer_harvest_blessing", "farmer", "豊穣の恵み", 10, "passive", "作物追加ドロップ"},
            {"farmer_twin_miracle", "farmer", "双子の奇跡", 15, "passive", "動物繁殖時双子確率"},
            {"farmer_growth_acceleration", "farmer", "成長促進", 30, "passive", "作物・動物成長加速"},
            {"farmer_selective_breeding", "farmer", "品種改良", 50, "passive", "高品質作物・動物"},
            
            // 漁師スキル
            {"fisherman_big_catch", "fisherman", "大物釣り", 20, "passive", "大型魚釣り確率向上"},
            {"fisherman_treasure_hunter", "fisherman", "宝探し", 35, "passive", "宝物釣り確率向上"},
            {"fisherman_sea_blessing", "fisherman", "海の加護", 50, "passive", "天候無視釣り"},
            
            // 鍛冶屋スキル
            {"blacksmith_perfect_repair", "blacksmith", "完璧な修理", 10, "active", "修理時耐久度ボーナス"},
            {"blacksmith_master_craftsmanship", "blacksmith", "名工の技", 25, "passive", "製作時品質向上"},
            {"blacksmith_artifact_creation", "blacksmith", "神器創造", 60, "active", "特別エンチャント付与"},
            
            // 錬金術師スキル
            {"alchemist_ingredient_conservation", "alchemist", "材料節約", 15, "passive", "醸造材料消費削減"},
            {"alchemist_double_brewing", "alchemist", "二重醸造", 30, "passive", "ポーション2個作成"},
            {"alchemist_alchemy_mastery", "alchemist", "錬金の極意", 50, "passive", "効果時間延長"},
            
            // 魔術師スキル
            {"enchanter_experience_conservation", "enchanter", "経験値節約", 10, "passive", "エンチャント経験値コスト削減"},
            {"enchanter_bonus_enchantment", "enchanter", "ボーナスエンチャント", 25, "passive", "追加エンチャント付与"},
            {"enchanter_mystical_arts", "enchanter", "魔術の神秘", 45, "passive", "希少エンチャント確率向上"},
            
            // 建築家スキル
            {"architect_material_efficiency", "architect", "材料節約", 10, "passive", "建築材料消費削減"},
            {"architect_architectural_aesthetics", "architect", "建築の美学", 30, "passive", "装飾ブロックボーナス"},
            {"architect_master_architect", "architect", "巨匠の設計", 50, "passive", "大規模建築ボーナス"}
        };
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSkillQuery)) {
            
            for (Object[] skill : skillData) {
                pstmt.setString(1, (String) skill[0]);
                pstmt.setString(2, (String) skill[1]);
                pstmt.setString(3, (String) skill[2]);
                pstmt.setInt(4, (Integer) skill[3]);
                pstmt.setString(5, (String) skill[4]);
                pstmt.setString(6, (String) skill[5]);
                pstmt.executeUpdate();
            }
            
            logger.info("デフォルト職業スキルデータを挿入しました。");
        }
    }
    
    /**
     * データベース接続の取得（統計情報付き）
     */
    public Connection getConnection() throws SQLException {
        if (!isInitialized || dataSource.isClosed()) {
            throw new SQLException("データベースが初期化されていないか、既に閉じられています。");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Connection connection = dataSource.getConnection();
            activeConnections.incrementAndGet();
            totalConnections.incrementAndGet();
            
            long queryTime = System.currentTimeMillis() - startTime;
            totalQueryTime.addAndGet(queryTime);
            totalQueries.incrementAndGet();
            
            return new ConnectionWrapper(connection, this);
            
        } catch (SQLException e) {
            failedQueries.incrementAndGet();
            throw e;
        }
    }
    
    /**
     * 接続がクローズされた際に呼び出される内部メソッド
     */
    void onConnectionClosed() {
        activeConnections.decrementAndGet();
    }
    
    /**
     * データベース統計情報の取得
     */
    public DatabaseStatistics getStatistics() {
        return new DatabaseStatistics(
            activeConnections.get(),
            totalConnections.get(),
            totalQueries.get(),
            failedQueries.get(),
            totalQueries.get() == 0 ? 0.0 : (double) totalQueryTime.get() / totalQueries.get(),
            dataSource != null ? dataSource.getMaximumPoolSize() : 0,
            dataSource != null ? dataSource.getMinimumIdle() : 0
        );
    }
    
    /**
     * パフォーマンス統計をデータベースに記録
     */
    public void recordPerformanceStats() {
        try (Connection conn = getConnection()) {
            String insertStatQuery = "INSERT INTO performance_stats (stat_type, stat_value) " +
                                     "VALUES (?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertStatQuery)) {
                DatabaseStatistics stats = getStatistics();
                
                // 各統計をレコードとして挿入
                pstmt.setString(1, "active_connections");
                pstmt.setDouble(2, stats.getActiveConnections());
                pstmt.executeUpdate();
                
                pstmt.setString(1, "total_queries");
                pstmt.setDouble(2, stats.getTotalQueries());
                pstmt.executeUpdate();
                
                pstmt.setString(1, "average_query_time");
                pstmt.setDouble(2, stats.getAverageQueryTime());
                pstmt.executeUpdate();
                
                pstmt.setString(1, "failed_queries");
                pstmt.setDouble(2, stats.getFailedQueries());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("パフォーマンス統計の記録に失敗しました: " + e.getMessage());
        }
    }
    
    /**
     * データベース接続のクローズとリソースの解放
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP データソースをクローズしました。");
        }
        
        DatabaseStatistics finalStats = getStatistics();
        logger.info(String.format("データベース最終統計: アクティブ接続%d, 総クエリ%d, 失敗%d, 平均時間%.2fms",
                finalStats.getActiveConnections(),
                finalStats.getTotalQueries(),
                finalStats.getFailedQueries(),
                finalStats.getAverageQueryTime()));
        
        isInitialized = false;
    }
    
    /**
     * 初期化状態のチェック
     */
    public boolean isInitialized() {
        return isInitialized && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * データベース統計情報クラス
     */
    public static class DatabaseStatistics {
        private final int activeConnections;
        private final long totalConnections;
        private final long totalQueries;
        private final long failedQueries;
        private final double averageQueryTime;
        private final int maxPoolSize;
        private final int minPoolSize;
        
        public DatabaseStatistics(int activeConnections, long totalConnections, long totalQueries,
                                long failedQueries, double averageQueryTime, int maxPoolSize, int minPoolSize) {
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.totalQueries = totalQueries;
            this.failedQueries = failedQueries;
            this.averageQueryTime = averageQueryTime;
            this.maxPoolSize = maxPoolSize;
            this.minPoolSize = minPoolSize;
        }
        
        public double getSuccessRate() {
            return totalQueries == 0 ? 1.0 : 1.0 - ((double) failedQueries / totalQueries);
        }
        
        // Getters
        public int getActiveConnections() { return activeConnections; }
        public long getTotalConnections() { return totalConnections; }
        public long getTotalQueries() { return totalQueries; }
        public long getFailedQueries() { return failedQueries; }
        public double getAverageQueryTime() { return averageQueryTime; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getMinPoolSize() { return minPoolSize; }
    }
    
    /**
     * 接続ラッパークラス（統計追跡用）
     */
    private static class ConnectionWrapper implements Connection {
        private final Connection delegate;
        private final HikariDatabaseManager manager;
        private boolean closed = false;
        
        public ConnectionWrapper(Connection delegate, HikariDatabaseManager manager) {
            this.delegate = delegate;
            this.manager = manager;
        }
        
        @Override
        public void close() throws SQLException {
            if (!closed) {
                delegate.close();
                manager.onConnectionClosed();
                closed = true;
            }
        }
        
        // 以下、Connection インターフェースの全メソッドをdelegateに委譲
        @Override
        public Statement createStatement() throws SQLException { return delegate.createStatement(); }
        
        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException { return delegate.prepareStatement(sql); }
        
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException { delegate.setAutoCommit(autoCommit); }
        
        @Override
        public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        
        @Override
        public void commit() throws SQLException { delegate.commit(); }
        
        @Override
        public void rollback() throws SQLException { delegate.rollback(); }
        
        @Override
        public boolean isClosed() throws SQLException { return closed || delegate.isClosed(); }
        
        // その他のメソッドは必要に応じて実装
        // 簡潔性のため一部のみ記載
        
        // 以下のメソッドは基本的にサポートされていないか、使用されないため簡略実装
        @Override
        public java.sql.CallableStatement prepareCall(String sql) throws SQLException { 
            return delegate.prepareCall(sql); 
        }
        
        @Override
        public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
        
        @Override
        public java.sql.DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException { delegate.setReadOnly(readOnly); }
        
        @Override
        public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        
        @Override
        public void setCatalog(String catalog) throws SQLException { delegate.setCatalog(catalog); }
        
        @Override
        public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        
        @Override
        public void setTransactionIsolation(int level) throws SQLException { delegate.setTransactionIsolation(level); }
        
        @Override
        public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        
        @Override
        public java.sql.SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        
        @Override
        public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        
        // Java 8で必要な他のメソッドも同様に委譲実装
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public java.util.Map<String,Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
        
        @Override
        public void setTypeMap(java.util.Map<String,Class<?>> map) throws SQLException { delegate.setTypeMap(map); }
        
        @Override
        public void setHoldability(int holdability) throws SQLException { delegate.setHoldability(holdability); }
        
        @Override
        public int getHoldability() throws SQLException { return delegate.getHoldability(); }
        
        @Override
        public java.sql.Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
        
        @Override
        public java.sql.Savepoint setSavepoint(String name) throws SQLException { return delegate.setSavepoint(name); }
        
        @Override
        public void rollback(java.sql.Savepoint savepoint) throws SQLException { delegate.rollback(savepoint); }
        
        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException { delegate.releaseSavepoint(savepoint); }
        
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.prepareStatement(sql, autoGeneratedKeys);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return delegate.prepareStatement(sql, columnIndexes);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return delegate.prepareStatement(sql, columnNames);
        }
        
        @Override
        public java.sql.Clob createClob() throws SQLException { return delegate.createClob(); }
        
        @Override
        public java.sql.Blob createBlob() throws SQLException { return delegate.createBlob(); }
        
        @Override
        public java.sql.NClob createNClob() throws SQLException { return delegate.createNClob(); }
        
        @Override
        public java.sql.SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
        
        @Override
        public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
        
        @Override
        public void setClientInfo(String name, String value) throws java.sql.SQLClientInfoException { 
            delegate.setClientInfo(name, value); 
        }
        
        @Override
        public void setClientInfo(java.util.Properties properties) throws java.sql.SQLClientInfoException { 
            delegate.setClientInfo(properties); 
        }
        
        @Override
        public String getClientInfo(String name) throws SQLException { return delegate.getClientInfo(name); }
        
        @Override
        public java.util.Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
        
        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { 
            return delegate.createArrayOf(typeName, elements); 
        }
        
        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { 
            return delegate.createStruct(typeName, attributes); 
        }
        
        @Override
        public void setSchema(String schema) throws SQLException { delegate.setSchema(schema); }
        
        @Override
        public String getSchema() throws SQLException { return delegate.getSchema(); }
        
        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException { delegate.abort(executor); }
        
        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException { 
            delegate.setNetworkTimeout(executor, milliseconds); 
        }
        
        @Override
        public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }
}