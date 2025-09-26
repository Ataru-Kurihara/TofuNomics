# TofuNomics プラグイン インストール・設定ガイド

## 1. 前提条件

### 必要な環境
- **Java**: Java 8 以上
- **Minecraft**: 1.16.5 対応
- **サーバーソフトウェア**: Spigot 1.16.5 または Paper 1.16.5
- **外部プラグイン（推奨）**: WorldGuard（土地保護連携用）

### 推奨スペック
- **メモリ**: 2GB以上
- **CPU**: 2コア以上
- **ストレージ**: 500MB以上の空き容量

## 2. インストール手順

### Step 1: プラグインファイルのダウンロード
1. `TofuNomics-1.0.jar` をダウンロード
2. サーバーの `plugins/` フォルダに配置

### Step 2: 依存プラグインの確認
```bash
# 推奨プラグイン
plugins/
├── WorldGuard-7.0.x.jar  # 土地保護連携（推奨）
└── TofuNomics-1.0.jar     # 本プラグイン
```

### Step 3: 初回起動
1. サーバーを起動
2. プラグインが自動的に初期化ファイルを生成
```
plugins/TofuNomics/
├── config.yml          # 設定ファイル
├── data/
│   └── database.db     # SQLiteデータベース
└── logs/
    └── performance.log  # パフォーマンスログ
```

## 3. 設定ファイル（config.yml）

### 主要設定項目

#### 3.1 データベース設定
```yaml
database:
  type: "sqlite"
  file: "data/database.db"
  connection-pool:
    max-connections: 10
    min-idle: 2
    connection-timeout: 30000
```

#### 3.2 経済システム設定
```yaml
economy:
  starting-balance: 1000.0
  max-balance: 1000000.0
  currency-name: "金塊"
  transaction-fee-rate: 0.01
```

#### 3.3 職業設定
```yaml
jobs:
  farmer:
    max-level: 100
    base-income: 10.0
    experience-multiplier: 1.0
    skills:
      harvest-boost:
        unlock-level: 10
        effect-chance: 0.15
```

### 3.4 パフォーマンス設定
```yaml
performance:
  cache:
    player-data-cache-size: 100
    cache-expiry-minutes: 30
  async:
    worker-threads: 4
    task-queue-size: 1000
```

## 4. 権限設定

### 基本権限
```yaml
permissions:
  tofunomics.use:
    description: "基本的なプラグイン使用権限"
    default: true
    
  tofunomics.jobs.join:
    description: "職業に参加する権限"
    default: true
    
  tofunomics.economy.use:
    description: "経済機能を使用する権限"
    default: true
```

### 管理者権限
```yaml
  tofunomics.admin:
    description: "管理者権限（全機能アクセス）"
    default: op
    
  tofunomics.reload:
    description: "設定リロード権限"
    default: op
```

## 5. データベース初期化

### 自動初期化
初回起動時に以下のテーブルが自動作成されます：
- `players` - プレイヤー基本情報
- `jobs` - 職業マスターデータ
- `player_jobs` - プレイヤー職業情報
- `job_skills` - 職業スキル情報
- `player_skills` - プレイヤースキル取得状況
- `land_ownership` - 土地所有情報（将来用）

### 手動リセット
```bash
# データベースリセット（注意：全データが削除されます）
rm plugins/TofuNomics/data/database.db
# サーバー再起動で自動再作成
```

## 6. 設定の適用

### リロードコマンド
```bash
/tofunomics reload
```

### 設定変更の反映
- 多くの設定はリロードで即座に反映
- 一部のシステム設定（データベース接続など）はサーバー再起動が必要

## 7. ログとモニタリング

### ログファイル
```
plugins/TofuNomics/logs/
├── performance.log    # パフォーマンス情報
├── transactions.log   # 経済取引ログ
└── errors.log        # エラーログ
```

### モニタリングコマンド
```bash
/tofunomics stats          # システム統計情報
/tofunomics performance    # パフォーマンス情報
/tofunomics cache-info     # キャッシュ使用状況
```

## 8. バックアップ推奨設定

### 自動バックアップスクリプト例
```bash
#!/bin/bash
# TofuNomicsデータバックアップ
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/path/to/backup/tofunomics"

mkdir -p "$BACKUP_DIR"
cp plugins/TofuNomics/data/database.db "$BACKUP_DIR/database_$DATE.db"
cp plugins/TofuNomics/config.yml "$BACKUP_DIR/config_$DATE.yml"

# 30日以上古いバックアップを削除
find "$BACKUP_DIR" -name "database_*.db" -mtime +30 -delete
find "$BACKUP_DIR" -name "config_*.yml" -mtime +30 -delete
```

## 9. トラブルシューティング

### よくある問題

#### 問題1: プラグインが起動しない
**症状**: サーバー起動時にエラーが発生
**解決策**:
1. Java 8以上がインストールされているか確認
2. Spigot 1.16.5対応版を使用しているか確認
3. `plugins/` フォルダの権限を確認

#### 問題2: データベースエラー
**症状**: SQLite関連エラー
**解決策**:
1. `plugins/TofuNomics/data/` フォルダの書き込み権限確認
2. データベースファイルの破損チェック
3. 必要に応じてデータベースファイルの再作成

#### 問題3: パフォーマンス低下
**症状**: サーバーが重くなる
**解決策**:
1. `config.yml` のキャッシュ設定調整
2. 非同期処理スレッド数の調整
3. パフォーマンスログの確認

### 設定値調整ガイド

#### 少人数サーバー（1-20人）
```yaml
performance:
  cache:
    player-data-cache-size: 50
  async:
    worker-threads: 2
```

#### 中規模サーバー（20-100人）
```yaml
performance:
  cache:
    player-data-cache-size: 200
  async:
    worker-threads: 4
```

#### 大規模サーバー（100人以上）
```yaml
performance:
  cache:
    player-data-cache-size: 500
  async:
    worker-threads: 8
```

## 10. アップデート手順

### 通常アップデート
1. サーバー停止
2. 古い `TofuNomics-x.x.jar` を削除
3. 新しい `TofuNomics-x.x.jar` を配置
4. サーバー起動
5. 設定ファイルの互換性確認

### 設定ファイル更新
1. 新しいバージョンで設定項目が追加された場合
2. `config.yml.new` が生成される場合がある
3. 必要に応じて設定項目を統合

## サポート

### 問題報告
- GitHub Issues: [リポジトリURL]
- 管理者Discord: [Discord URL]

### 設定サポート
- 設定例集: `docs/config-examples/`
- FAQ: `docs/FAQ.md`

---

**注意**: 本プラグインは正式リリース版です。プロダクション環境での使用前に必ずテスト環境で動作確認を行ってください。