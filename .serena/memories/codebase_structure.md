# TofuNomics コードベース構造

## プロジェクト全体構造
```
TofuNomics/
├── pom.xml                          # Maven設定ファイル
├── dependency-reduced-pom.xml       # シェードプラグイン生成ファイル
├── TofuNomics.iml                   # IntelliJ IDEA設定
├── docs/                            # ドキュメント類
│   ├── 要件定義書.md
│   ├── implementation-plan.md
│   ├── INSTALL.md
│   ├── USER_MANUAL.md
│   ├── ADMIN_GUIDE.md
│   └── API_REFERENCE.md
├── src/                             # ソースコード
│   ├── main/
│   └── test/
└── target/                          # ビルド成果物
```

## メインソースコード構造 (src/main/java/org/tofu/tofunomics/)

### 1. コアシステム
- **TofuNomics.java** - メインプラグインクラス（エントリーポイント）

### 2. データ層 (dao/, database/, models/)
- **dao/** - データアクセスオブジェクト
  - `PlayerDAO.java` - プレイヤーデータアクセス
  - `JobDAO.java` - 職業データアクセス
  - `PlayerJobDAO.java` - プレイヤー職業関連データアクセス
  - `JobChangeDAO.java` - 職業変更履歴アクセス

- **database/** - データベース管理
  - `DatabaseManager.java` - 抽象データベースマネージャー
  - `HikariDatabaseManager.java` - HikariCP実装

- **models/** - データモデル
  - `Player.java` - プレイヤーエンティティ
  - `Job.java` - 職業エンティティ
  - `PlayerJob.java` - プレイヤー職業関連エンティティ
  - `JobChange.java` - 職業変更履歴エンティティ
  - `JobSkill.java` - 職業スキルエンティティ
  - `PlayerSkill.java` - プレイヤースキル取得状況エンティティ
  - `TradeChest.java` - 取引チェストエンティティ
  - `PlayerTradeHistory.java` - 取引履歴エンティティ
  - `LandOwnership.java` - 土地所有エンティティ

### 3. ビジネスロジック層

#### 経済システム (economy/)
- `ItemManager.java` - アイテム管理
- `CurrencyConverter.java` - 通貨変換システム
- `BankLocationManager.java` - 銀行位置管理

#### 職業システム (jobs/, experience/, skills/, tools/)
- **jobs/**
  - `JobManager.java` - 職業管理システム
  - `ExperienceManager.java` - 経験値管理システム
- **experience/**
  - `JobExperienceManager.java` - 職業経験値詳細管理
- **skills/**
  - `SkillType.java` - スキル種別定義
- **tools/**
  - `JobToolManager.java` - 職業ツール管理
  - `JobToolSet.java` - ツールセット定義

#### 取引システム (trade/)
- `TradeChestManager.java` - 取引チェスト管理
- `TradePriceManager.java` - 取引価格管理
- `TradeChestListener.java` - 取引イベント処理

#### その他システム
- **income/** - 職業収入管理
- **quests/** - クエストシステム
- **rewards/** - 報酬システム
- **stats/** - 統計システム

### 4. プレゼンテーション層

#### コマンド (commands/)
- `BalanceCommand.java` - 残高確認コマンド
- `PayCommand.java` - 送金コマンド
- `BalanceTopCommand.java` - 残高ランキング
- `WithdrawCommand.java` - 引き出しコマンド
- `DepositCommand.java` - 預け入れコマンド
- `JobsCommand.java` - 職業関連コマンド
- `JobStatsCommand.java` - 職業統計コマンド
- `JobQuestCommand.java` - 職業クエストコマンド
- `TradeCommand.java` - 取引関連コマンド
- `EcoCommand.java` - 管理者経済コマンド
- `TofuNomicsCommand.java` - メインプラグインコマンド
- `ReloadCommand.java` - リロードコマンド
- `ScoreboardCommand.java` - スコアボードコマンド
- `NPCCommand.java` - NPC管理コマンド

#### GUI・NPC (npc/)
- `NPCManager.java` - NPC管理
- `TradingNPCManager.java` - 取引NPC管理
- `BankNPCManager.java` - 銀行NPC管理
- `NPCListener.java` - NPCインタラクション処理
- **npc/gui/** - GUI関連
  - `TradingGUI.java` - 取引GUI
  - `BankGUI.java` - 銀行GUI

### 5. 横断的関心事

#### イベント処理 (events/)
- `UnifiedEventHandler.java` - 統合イベントハンドラ
- `EventProcessor.java` - イベント処理エンジン
- `EventCache.java` - イベントキャッシュ
- `AsyncEventUpdater.java` - 非同期イベント更新
- **events/handlers/** - 個別イベントハンドラ
  - `BuildingEventHandler.java` - 建築イベント
  - `BrewingEventHandler.java` - 醸造イベント
  - `EnchantmentEventHandler.java` - エンチャントイベント
  - `BreedingEventHandler.java` - 繁殖イベント
  - `GrowthEventHandler.java` - 成長イベント

#### プレイヤー管理 (players/)
- `PlayerJoinHandler.java` - プレイヤー参加処理

#### システム管理
- **config/** - 設定管理
  - `ConfigManager.java` - 設定マネージャー
  - `ConfigValidator.java` - 設定バリデーション
- **cache/** - キャッシュ管理
  - `CacheManager.java` - キャッシュシステム
- **performance/** - パフォーマンス監視
  - `PerformanceMonitor.java` - パフォーマンス監視
- **batch/** - バッチ処理
  - `OptimizedBatchProcessor.java` - 最適化バッチ処理
- **scoreboard/** - スコアボード
  - `ScoreboardManager.java` - スコアボード管理

## テストコード構造 (src/test/java/)

### 単体テスト
```
org/tofu/tofunomics/
├── dao/                             # DAO層テスト
│   ├── PlayerDAOTest.java
│   ├── JobDAOTest.java
│   ├── PlayerJobDAOTest.java
│   └── JobChangeDAOTest.java
├── models/                          # モデル層テスト
│   ├── PlayerTest.java
│   ├── JobTest.java
│   └── PlayerJobTest.java
├── jobs/                            # 職業システムテスト
│   └── JobManagerTest.java
├── integration/                     # 統合テスト
├── managers/                        # マネージャー系テスト
└── utils/                          # ユーティリティテスト
```

## 設定・リソースファイル

### メインリソース (src/main/resources/)
- **plugin.yml** - Bukkitプラグイン設定（コマンド定義、権限設定）
- **config.yml** - 詳細なプラグイン設定（業務ロジック、バランス調整）

### テストリソース (src/test/resources/)
- テスト用設定ファイル
- モックデータ

## ビルド成果物 (target/)

### 重要なディレクトリ
- **target/classes/** - コンパイル済みクラスファイル
- **target/test-classes/** - テスト用クラスファイル
- **target/surefire-reports/** - テスト実行結果
- **target/site/jacoco/** - テストカバレッジレポート

### 成果物ファイル
- **TofuNomics-1.0-SNAPSHOT.jar** - 配布用JARファイル
- **original-TofuNomics-1.0-SNAPSHOT.jar** - シェード前のJAR
- **jacoco.exec** - カバレッジデータ

## アーキテクチャ特徴

### レイヤー構造
1. **プレゼンテーション層** - コマンド、GUI、NPCインタラクション
2. **ビジネスロジック層** - 職業システム、経済システム、取引システム
3. **データアクセス層** - DAO、データベース管理
4. **データ層** - SQLiteデータベース

### 横断的関心事
- **設定管理** - 全システム共通の設定機能
- **キャッシュ** - パフォーマンス向上
- **ログ・監視** - システム状態把握
- **イベント処理** - Minecraft との統合

### 依存性の方向
- 上位層は下位層に依存
- ビジネスロジックはプレゼンテーション層に依存しない
- データアクセス層は具体実装を隠蔽

この構造により、保守性、拡張性、テスタビリティを確保しています。