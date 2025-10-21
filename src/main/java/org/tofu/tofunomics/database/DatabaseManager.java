package org.tofu.tofunomics.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class DatabaseManager {
    private final String databasePath;
    private final Logger logger;
    private Connection connection;

    public DatabaseManager(String databasePath, Logger logger) {
        this.databasePath = databasePath;
        this.logger = logger;
    }

    public boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            logger.info("SQLiteデータベースに接続しました");
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            logger.severe("データベース接続に失敗しました: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("データベース接続を閉じました");
            }
        } catch (SQLException e) {
            logger.warning("データベース接続の切断に失敗しました: " + e.getMessage());
        }
    }

    public void createTables() {
        String[] tableCreationQueries = {
            // プレイヤー基本情報テーブル
            "CREATE TABLE IF NOT EXISTS players (" +
            "    uuid TEXT PRIMARY KEY," +
            "    balance REAL NOT NULL DEFAULT 0.0," +
            "    bank_balance REAL NOT NULL DEFAULT 0.0," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ");",

            // 職業情報テーブル
            "CREATE TABLE IF NOT EXISTS jobs (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    name TEXT UNIQUE NOT NULL," +
            "    display_name TEXT NOT NULL," +
            "    max_level INTEGER NOT NULL DEFAULT 75," +
            "    base_income REAL NOT NULL DEFAULT 1.0," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ");",

            // プレイヤー職業関連テーブル
            "CREATE TABLE IF NOT EXISTS player_jobs (" +
            "    uuid TEXT NOT NULL," +
            "    job_id INTEGER NOT NULL," +
            "    level INTEGER NOT NULL DEFAULT 1," +
            "    experience REAL NOT NULL DEFAULT 0.0," +
            "    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    PRIMARY KEY (uuid, job_id)," +
            "    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE," +
            "    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE" +
            ");",

            // 職業スキルテーブル
            "CREATE TABLE IF NOT EXISTS job_skills (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    job_id INTEGER NOT NULL," +
            "    skill_name TEXT NOT NULL," +
            "    unlock_level INTEGER NOT NULL," +
            "    description TEXT," +
            "    effect_type TEXT NOT NULL," +
            "    effect_value REAL," +
            "    cooldown INTEGER DEFAULT 0," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE," +
            "    UNIQUE(job_id, skill_name)" +
            ");",

            // プレイヤーが持つスキルテーブル
            "CREATE TABLE IF NOT EXISTS player_skills (" +
            "    uuid TEXT NOT NULL," +
            "    skill_id INTEGER NOT NULL," +
            "    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    last_used TIMESTAMP," +
            "    PRIMARY KEY (uuid, skill_id)," +
            "    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE," +
            "    FOREIGN KEY (skill_id) REFERENCES job_skills(id) ON DELETE CASCADE" +
            ");",

            // 土地所有テーブル
            "CREATE TABLE IF NOT EXISTS land_ownership (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    owner_uuid TEXT NOT NULL," +
            "    land_name TEXT," +
            "    world_name TEXT NOT NULL," +
            "    x1 INTEGER NOT NULL," +
            "    y1 INTEGER NOT NULL," +
            "    z1 INTEGER NOT NULL," +
            "    x2 INTEGER NOT NULL," +
            "    y2 INTEGER NOT NULL," +
            "    z2 INTEGER NOT NULL," +
            "    purchase_price REAL NOT NULL," +
            "    purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
            ");",

            // 職業変更履歴テーブル（1日1回制限管理用）
            "CREATE TABLE IF NOT EXISTS job_changes (" +
            "    uuid TEXT PRIMARY KEY," +
            "    last_change_date TEXT NOT NULL," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
            ");",

            // 取引チェストテーブル（フェーズ4用）
            "CREATE TABLE IF NOT EXISTS trade_chests (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    world_name TEXT NOT NULL," +
            "    x INTEGER NOT NULL," +
            "    y INTEGER NOT NULL," +
            "    z INTEGER NOT NULL," +
            "    job_type TEXT NOT NULL," +
            "    active BOOLEAN DEFAULT TRUE," +
            "    created_by TEXT NOT NULL," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    UNIQUE(world_name, x, y, z)," +
            "    FOREIGN KEY (created_by) REFERENCES players(uuid) ON DELETE SET NULL" +
            ");",

            // プレイヤー取引履歴テーブル（フェーズ4用）
            "CREATE TABLE IF NOT EXISTS player_trade_history (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    uuid TEXT NOT NULL," +
            "    trade_chest_id INTEGER NOT NULL," +
            "    item_type TEXT NOT NULL," +
            "    item_amount INTEGER NOT NULL," +
            "    sale_price REAL NOT NULL," +
            "    job_bonus REAL DEFAULT 0.0," +
            "    player_job TEXT," +
            "    player_job_level INTEGER DEFAULT 1," +
            "    traded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE," +
            "    FOREIGN KEY (trade_chest_id) REFERENCES trade_chests(id) ON DELETE CASCADE" +
            ");",

            // 住居物件マスタテーブル
            "CREATE TABLE IF NOT EXISTS housing_properties (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    property_name TEXT NOT NULL," +
            "    world_name TEXT NOT NULL," +
            "    x1 INTEGER," +
            "    y1 INTEGER," +
            "    z1 INTEGER," +
            "    x2 INTEGER," +
            "    y2 INTEGER," +
            "    z2 INTEGER," +
            "    worldguard_region_id TEXT," +
            "    description TEXT," +
            "    daily_rent REAL NOT NULL," +
            "    weekly_rent REAL," +
            "    monthly_rent REAL," +
            "    is_available BOOLEAN DEFAULT TRUE," +
            "    owner_uuid TEXT," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ");",

            // 住居賃貸契約テーブル
            "CREATE TABLE IF NOT EXISTS housing_rentals (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    property_id INTEGER NOT NULL," +
            "    tenant_uuid TEXT NOT NULL," +
            "    rental_period TEXT NOT NULL," +
            "    rental_days INTEGER NOT NULL," +
            "    total_cost REAL NOT NULL," +
            "    start_date TIMESTAMP NOT NULL," +
            "    end_date TIMESTAMP NOT NULL," +
            "    status TEXT NOT NULL," +
            "    auto_renew BOOLEAN DEFAULT FALSE," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (property_id) REFERENCES housing_properties(id) ON DELETE CASCADE," +
            "    FOREIGN KEY (tenant_uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
            ");",

            // 住居賃貸履歴テーブル
            "CREATE TABLE IF NOT EXISTS housing_rental_history (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    rental_id INTEGER NOT NULL," +
            "    property_id INTEGER NOT NULL," +
            "    tenant_uuid TEXT NOT NULL," +
            "    action_type TEXT NOT NULL," +
            "    amount REAL," +
            "    action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (rental_id) REFERENCES housing_rentals(id) ON DELETE CASCADE" +
            ");",

            // プレイヤーインベントリ保存テーブル
            "CREATE TABLE IF NOT EXISTS player_inventories (" +
            "    player_uuid TEXT PRIMARY KEY," +
            "    inventory_data TEXT," +
            "    armor_data TEXT," +
            "    offhand_data TEXT," +
            "    last_saved TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
            ");"
        };

        try (Statement statement = connection.createStatement()) {
            for (String query : tableCreationQueries) {
                statement.execute(query);
            }
            logger.info("データベーステーブルを作成しました");
            
            // マイグレーション処理を実行
            performMigrations();
            
            initializeDefaultJobs();
        } catch (SQLException e) {
            logger.severe("テーブル作成に失敗しました: " + e.getMessage());
        }
    }

    private void performMigrations() {
        try (Statement statement = connection.createStatement()) {
            // bank_balanceカラムが存在するかチェック
            try {
                statement.executeQuery("SELECT bank_balance FROM players LIMIT 1");
            } catch (SQLException e) {
                // カラムが存在しない場合、追加する
                logger.info("bank_balanceカラムを追加しています...");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN bank_balance REAL NOT NULL DEFAULT 0.0");
                
                // 既存のbalanceをbank_balanceに移行
                statement.executeUpdate("UPDATE players SET bank_balance = balance WHERE bank_balance = 0.0");
                statement.executeUpdate("UPDATE players SET balance = 0.0");
                
                logger.info("既存データをマイグレーションしました: balance → bank_balance");
            }
            
            // jobs テーブルの created_at カラムが存在するかチェック
            try {
                statement.executeQuery("SELECT created_at FROM jobs LIMIT 1");
                
                // カラムが存在する場合、タイムスタンプ形式を修正
                logger.info("jobsテーブルのタイムスタンプ形式を確認・修正しています...");
                
                // SQLiteでミリ秒なしのタイムスタンプをミリ秒付きに変換
                // まず現在のデータ形式を確認
                try {
                    // タイムスタンプ形式を統一（ミリ秒なし -> ミリ秒付き）
                    statement.executeUpdate(
                        "UPDATE jobs SET created_at = " +
                        "CASE " +
                        "  WHEN created_at LIKE '____-__-__ __:__:__' THEN created_at || '.000' " +
                        "  WHEN created_at LIKE '____-__-__T__:__:__' THEN REPLACE(created_at, 'T', ' ') || '.000' " +
                        "  ELSE created_at " +
                        "END " +
                        "WHERE created_at NOT LIKE '%.___'"
                    );
                    logger.info("jobsテーブルのタイムスタンプ形式を修正しました");
                } catch (SQLException updateEx) {
                    // 更新に失敗した場合はログに記録のみ
                    logger.warning("タイムスタンプ形式の修正に失敗しました（処理は継続）: " + updateEx.getMessage());
                }
                
            } catch (SQLException e) {
                // カラムが存在しない場合、追加する
                logger.info("jobs テーブルに created_at カラムを追加しています...");
                statement.executeUpdate("ALTER TABLE jobs ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                
                // 既存データに created_at を設定
                statement.executeUpdate("UPDATE jobs SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL");
                
                logger.info("jobs テーブルのマイグレーションが完了しました");
            }
            
            // rules_agreed カラムが存在するかチェック
            try {
                statement.executeQuery("SELECT rules_agreed FROM players LIMIT 1");
            } catch (SQLException e) {
                // カラムが存在しない場合、追加する
                logger.info("players テーブルに rules_agreed カラムを追加しています...");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN rules_agreed BOOLEAN DEFAULT FALSE");
                
                logger.info("players テーブルに rules_agreed カラムを追加しました");
            }
            
            // rules_agreed_at カラムが存在するかチェック
            try {
                statement.executeQuery("SELECT rules_agreed_at FROM players LIMIT 1");
            } catch (SQLException e) {
                // カラムが存在しない場合、追加する
                logger.info("players テーブルに rules_agreed_at カラムを追加しています...");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN rules_agreed_at TIMESTAMP");
                
                logger.info("players テーブルに rules_agreed_at カラムを追加しました");
            }
        } catch (SQLException e) {
            logger.warning("マイグレーション処理に失敗しました: " + e.getMessage());
        }
    }

    private void initializeDefaultJobs() {
        // SQLiteで適切なタイムスタンプ形式を使用
        String timestamp = "strftime('%Y-%m-%d %H:%M:%S.000', 'now')";
        
        String[] jobs = {
            "('miner', '鉱夫', 75, 1.0, " + timestamp + ")",
            "('woodcutter', '木こり', 75, 1.0, " + timestamp + ")",
            "('farmer', '農家', 75, 1.0, " + timestamp + ")",
            "('fisherman', '釣り人', 75, 1.0, " + timestamp + ")",
            "('blacksmith', '鍛冶屋', 75, 1.0, " + timestamp + ")",
            "('alchemist', 'ポーション屋', 75, 1.0, " + timestamp + ")",
            "('enchanter', 'エンチャンター', 75, 1.0, " + timestamp + ")",
            "('architect', '建築家', 75, 1.0, " + timestamp + ")"
        };

        String insertQuery = "INSERT OR IGNORE INTO jobs (name, display_name, max_level, base_income, created_at) VALUES ";
        
        try (PreparedStatement statement = connection.prepareStatement(
            insertQuery + String.join(", ", jobs))) {
            statement.executeUpdate();
            logger.info("デフォルト職業データを初期化しました");
        } catch (SQLException e) {
            logger.warning("職業データの初期化に失敗しました: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}