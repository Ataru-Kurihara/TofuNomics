# TofuNomics コードスタイル・規約

## 基本的なコード規約

### パッケージ構成
- **ルートパッケージ**: `org.tofu.tofunomics`
- **機能別パッケージ**: cache, commands, config, dao, database, economy, events, jobs, models, npc, performance, players, quests, rewards, scoreboard, skills, stats, tools, trade
- **テストパッケージ**: `org.tofu.tofunomics.*` (同一構造)

### 命名規則

#### クラス名
- **エンティティクラス**: 単数形名詞 (例: `Player`, `Job`, `TradeChest`)
- **マネージャークラス**: `XxxManager` (例: `JobManager`, `CurrencyConverter`)
- **DAOクラス**: `XxxDAO` (例: `PlayerDAO`, `JobDAO`)
- **コマンドクラス**: `XxxCommand` (例: `BalanceCommand`, `JobsCommand`)
- **イベントハンドラ**: `XxxEventHandler` または `XxxListener` (例: `BuildingEventHandler`, `TradeChestListener`)

#### 変数・メソッド名
- **camelCase** を使用
- **boolean変数**: `is`, `has`, `can` で始める (例: `isActive`, `hasPermission`, `canWithdraw`)
- **定数**: `UPPER_SNAKE_CASE` (例: `MAX_BALANCE`, `DEFAULT_JOB_LEVEL`)

### アクセス修飾子
- **フィールド**: 基本的に `private`
- **メソッド**: 必要最小限の公開範囲
- **クラス**: 適切な可視性設定

## データベース関連

### DAO実装パターン
```java
public class PlayerDAO {
    private final DatabaseManager databaseManager;
    
    public PlayerDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    // CRUD操作メソッド
    public boolean createPlayer(Player player) { }
    public Player getPlayer(UUID playerId) { }
    public boolean updatePlayer(Player player) { }
    public boolean deletePlayer(UUID playerId) { }
}
```

### データモデル
- **UUIDベース**: プレイヤー識別にはUUIDを使用
- **Immutableオブジェクト**: 可能な限り不変オブジェクト設計
- **バリデーション**: 必要なデータ検証を実装

## 設定ファイル規約

### config.yml構造
- **階層構造**: 機能ごとにセクション分け
- **デフォルト値**: 必ず適切なデフォルト値を設定
- **コメント**: 各設定項目に説明コメント
- **データ型**: 明確な型定義（boolean, int, double, string, list）

### 設定読み込みパターン
```java
public class ConfigManager {
    private FileConfiguration config;
    
    public void loadConfig() {
        // 設定読み込みロジック
    }
    
    public void reloadConfig() {
        // 設定リロードロジック
    }
    
    // 型安全なゲッター
    public double getDouble(String path, double defaultValue) { }
    public int getInt(String path, int defaultValue) { }
}
```

## エラーハンドリング

### 例外処理
- **チェック例外**: 適切にキャッチして処理
- **ログ出力**: エラー時は適切なレベルでログ出力
- **フォールバック**: 可能な限りフォールバック処理を実装

### ログレベル
- **SEVERE**: 致命的エラー（プラグイン動作停止レベル）
- **WARNING**: 警告（設定ミス、データ不整合等）
- **INFO**: 情報（プラグイン開始・終了、重要な状態変化）
- **FINE**: デバッグ情報（詳細な処理内容）

## 非同期処理

### Bukkit API 非同期パターン
```java
// 非同期でデータベース処理
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // データベース操作
    Player player = playerDAO.getPlayer(playerId);
    
    // 同期でUIに反映
    Bukkit.getScheduler().runTask(plugin, () -> {
        // プレイヤーへの通知等
    });
});
```

### パフォーマンス考慮
- **重い処理は非同期**: データベースアクセス、ファイルI/O
- **UI操作は同期**: プレイヤーへのメッセージ送信、インベントリ操作
- **適切なスレッドプール**: 長時間実行タスクの管理

## テスト

### テストクラス命名
- **対象クラス名 + Test**: `PlayerDAOTest`, `JobManagerTest`
- **テストメソッド**: `test` + `動作内容` + `期待結果` (例: `testCreatePlayer_Success`)

### テストパターン
```java
@Test
public void testMethodName_Condition_ExpectedResult() {
    // Arrange (準備)
    
    // Act (実行)
    
    // Assert (検証)
}
```

### モックオブジェクト使用
- **外部依存**: データベース、Bukkit API等はモック化
- **PowerMock**: 静的メソッドのモック化が必要な場合
- **テストデータ**: H2データベースを使用したインメモリテスト

## コメント・ドキュメント

### JavaDoc
```java
/**
 * プレイヤーの銀行残高を管理するクラス
 * 
 * @author TofuNomics Team
 * @since 1.0.0
 */
public class CurrencyConverter {
    
    /**
     * 指定金額を銀行から引き出します
     * 
     * @param playerId プレイヤーUUID
     * @param amount 引き出し金額
     * @return 引き出し結果
     * @throws IllegalArgumentException 不正な引数の場合
     */
    public WithdrawResult withdraw(UUID playerId, double amount) {
        // 実装
    }
}
```

### インラインコメント
- **複雑なロジック**: アルゴリズムの説明
- **マジックナンバー**: 定数の意味説明
- **外部仕様**: 外部プラグイン連携時の注意事項

## セキュリティ

### 入力検証
- **プレイヤー入力**: コマンド引数の適切な検証
- **SQL インジェクション**: PreparedStatementの使用
- **権限チェック**: 適切な権限確認

### データ保護
- **UUIDの使用**: プレイヤー名ではなくUUIDでデータ管理
- **データ整合性**: トランザクション管理
- **バックアップ**: 重要データの定期的なバックアップ推奨

## リソース管理

### メモリ管理
- **キャッシュ**: 適切なキャッシュサイズ制限
- **ガベージコレクション**: 不要オブジェクトの適切な解放
- **イベントリスナー**: プラグイン終了時の適切なクリーンアップ

### ファイルI/O
- **リソース管理**: try-with-resources文の使用
- **ファイルロック**: 適切なファイルアクセス制御
- **エラーハンドリング**: I/O例外の適切な処理

## 外部連携

### Bukkit/Spigot API
- **バージョン互換性**: 1.16.5 APIの使用
- **非推奨API**: 使用を避け、代替手段を使用
- **プラグイン間連携**: 適切なプラグイン依存関係管理

### WorldGuard連携 (将来実装)
- **API バージョン**: 対応バージョンの明確化
- **フォールバック**: 未インストール時の動作定義
- **権限統合**: 権限システムの適切な連携