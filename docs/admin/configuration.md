# 設定ファイル解説

> TofuNomicsの設定ファイル（config.yml）の詳細解説

## 📋 目次
1. [設定ファイル概要](#設定ファイル概要)
2. [データベース設定](#データベース設定)
3. [経済システム設定](#経済システム設定)
4. [職業システム設定](#職業システム設定)
5. [NPCシステム設定](#npcシステム設定)
6. [パフォーマンス設定](#パフォーマンス設定)
7. [設定変更のベストプラクティス](#設定変更のベストプラクティス)

---

## 設定ファイル概要

### ファイルの場所
```
plugins/TofuNomics/config.yml
```

### 基本構造
config.ymlは以下の主要セクションで構成されています：
- **database**: データベース接続設定
- **economy**: 経済システムの設定
- **jobs**: 職業システムの設定
- **npc_system**: NPCシステムの設定
- **performance**: パフォーマンス最適化設定
- **debug**: デバッグ設定

---

## データベース設定

### 基本設定
```yaml
database:
  filename: "tofunomics_world.db"  # SQLiteファイル名
  connection_pool:
    max_connections: 10            # 最大接続数
    timeout: 30000                # タイムアウト（ミリ秒）
```

### 詳細オプション
```yaml
database:
  connection_pool:
    maximum_pool_size: 15    # 最大プールサイズ
    minimum_idle: 3          # 最小アイドル接続数

  batch_processing:
    enabled: true            # バッチ処理の有効化
    batch_size: 100         # バッチサイズ
```

### 推奨設定

#### 小規模サーバー（1-20人）
```yaml
database:
  connection_pool:
    max_connections: 5
    timeout: 30000
```

#### 中規模サーバー（20-50人）
```yaml
database:
  connection_pool:
    max_connections: 10
    timeout: 30000
```

#### 大規模サーバー（50人以上）
```yaml
database:
  connection_pool:
    max_connections: 20
    timeout: 30000
```

---

## 経済システム設定

### 通貨設定
```yaml
economy:
  currency:
    name: "金塊"                  # 通貨名
    symbol: "G"                   # 通貨記号
    decimal_places: 2             # 小数点桁数
```

### 初期設定
```yaml
economy:
  starting_balance: 100.0         # 初期残高
```

**推奨値**:
- 新規サーバー: 50-100金塊
- 既存サーバー: 100-200金塊
- 経済重視サーバー: 200-500金塊

### 送金設定
```yaml
economy:
  pay:
    minimum_amount: 1.0           # 最低送金額
    maximum_amount: 5000.0        # 最高送金額
    fee_percentage: 0.0           # 送金手数料（%）
```

**カスタマイズ例**:
```yaml
# 手数料を設定する場合
economy:
  pay:
    fee_percentage: 2.0           # 2%の手数料

# 送金制限を緩和する場合
economy:
  pay:
    maximum_amount: 10000.0       # 最高送金額を10,000に
```

### アイテム価格設定
```yaml
item_prices:
  # 鉱物系
  coal: 2.5
  iron_ingot: 6.0
  gold_ingot: 12.0
  diamond: 60.0
  emerald: 80.0

  # 木材系
  oak_log: 1.8
  birch_log: 1.8
  spruce_log: 1.8

  # 農作物系
  wheat: 1.2
  potato: 1.0
  carrot: 1.0
  beetroot: 1.5
```

### 職業別価格ボーナス
```yaml
job_price_multipliers:
  miner: 1.2      # 鉱夫は鉱石を20%高く売却
  woodcutter: 1.1 # 木こりは木材を10%高く売却
  farmer: 1.1     # 農家は農作物を10%高く売却
  fisherman: 1.15 # 漁師は魚を15%高く売却
```

---

## 職業システム設定

### 全般設定
```yaml
jobs:
  general:
    max_jobs_per_player: 1        # プレイヤー当たりの最大職業数
    keep_level_on_change: true    # 転職時レベル保持
    job_change_cooldown: 86400    # 転職クールダウン（秒）
```

**注意**: 転職にはレベル50以上が必要（コードで制御）

### 職業別設定
```yaml
jobs:
  job_settings:
    miner:
      display_name: "鉱夫"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    woodcutter:
      display_name: "木こり"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    farmer:
      display_name: "農家"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    fisherman:
      display_name: "漁師"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    blacksmith:
      display_name: "鍛冶屋"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    alchemist:
      display_name: "錬金術師"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    enchanter:
      display_name: "魔術師"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0

    architect:
      display_name: "建築家"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0
```

### レベル上限のカスタマイズ
```yaml
# 全職業のレベル上限を100に変更
jobs:
  job_settings:
    miner:
      max_level: 100
    woodcutter:
      max_level: 100
    # ... 以下同様
```

### 経験値倍率のカスタマイズ
```yaml
# 特定の職業の経験値獲得を2倍に
jobs:
  job_settings:
    farmer:
      exp_multiplier: 2.0
```

---

## NPCシステム設定

### 基本設定
```yaml
npc_system:
  enabled: true                   # NPCシステム有効化

  interaction:
    cooldown_ms: 1000            # 相互作用クールダウン（ミリ秒）
    session_timeout_ms: 300000   # セッションタイムアウト（5分）
    access_range: 5              # アクセス範囲（ブロック）
```

### 銀行NPC設定
```yaml
npc_system:
  bank_npcs:
    locations:
      - world: "tofunomics"
        x: -200
        y: 70
        z: -100
        name: "§b中央銀行窓口"
        type: "main_bank"

      - world: "tofunomics"
        x: 50
        y: 65
        z: -50
        name: "§e商店街支店"
        type: "branch"
```

### 取引NPC設定
```yaml
# 鉱夫用取引NPC
mining_post:
  welcome: "&8「よう、%player%。鉱山の取引所にようこそ」"
  accepted_jobs: ["miner"]

# 木こり用取引NPC
wood_market:
  welcome: "&2「%player%さん、森の市場へようこそ」"
  accepted_jobs: ["woodcutter"]

# 農家用取引NPC
farm_market:
  welcome: "&6「%player%さん、農場市場へようこそ」"
  accepted_jobs: ["farmer"]

# 漁師用取引NPC
fishing_dock:
  welcome: "&b「%player%さん、漁港へようこそ」"
  accepted_jobs: ["fisherman"]

# 鍛冶屋用取引NPC
blacksmith_forge:
  welcome: "&c「%player%さん、鍛冶場へようこそ」"
  accepted_jobs: ["blacksmith"]

# 錬金術師用取引NPC
alchemist_lab:
  welcome: "&5「%player%さん、錬金術ラボへようこそ」"
  accepted_jobs: ["alchemist"]

# 魔術師用取引NPC
enchanter_tower:
  welcome: "&d「%player%さん、魔術の塔へようこそ」"
  accepted_jobs: ["enchanter"]

# 建築家用取引NPC
architect_studio:
  welcome: "&e「%player%さん、建築スタジオへようこそ」"
  accepted_jobs: ["architect"]
```

### 食料NPC設定
```yaml
food_npc:
  operating_hours:
    start: 6      # 開始時刻（6:00）
    end: 22       # 終了時刻（22:00）

  purchase_limits:
    daily_per_player: 32    # 1日あたりの購入制限
    max_stock: 64          # 最大在庫数

  locations:
    - world: "tofunomics"
      x: -50
      y: 70
      z: -200
      name: "§6夜間食料品商人"
      description: "緊急時の食料品を販売"
```

---

## パフォーマンス設定

### データベース最適化
```yaml
performance:
  database:
    connection_pool:
      maximum_pool_size: 15
      minimum_idle: 3

    batch_processing:
      enabled: true
      batch_size: 100
```

### キャッシュ設定
```yaml
performance:
  caching:
    player_cache:
      max_size: 1000              # 最大キャッシュサイズ
      expire_after_access: 1800   # アクセス後の有効期限（秒）

    job_cache:
      max_size: 500
      expire_after_access: 3600

    npc_cache:
      max_size: 200
      expire_after_access: 7200
```

### 非同期処理設定
```yaml
performance:
  async:
    thread_pool_size: 4           # スレッドプールサイズ
    queue_capacity: 1000          # キュー容量
```

---

## デバッグ設定

### 基本デバッグ
```yaml
debug:
  enabled: false        # 通常は false
  verbose: false        # 詳細ログが必要な時のみ true
  npc_debug: false      # NPCデバッグが必要な時のみ true
```

### ログレベル設定
```yaml
logging:
  level: INFO           # DEBUG, INFO, WARNING, ERROR
  file_output: true     # ファイル出力の有効化
  console_output: true  # コンソール出力の有効化
```

---

## 設定変更のベストプラクティス

### 1. 変更前のバックアップ
```bash
# 設定ファイルのバックアップ
cp plugins/TofuNomics/config.yml plugins/TofuNomics/config.yml.backup
```

### 2. 段階的な変更
- 一度に1つのセクションのみ変更
- テスト環境での検証を推奨
- プレイヤーへの事前通知

### 3. 変更後の確認
```bash
# 設定をリロード
/tofunomics reload

# ステータス確認
/tofunomics status

# エラーログ確認
tail -f plugins/TofuNomics/logs/error.log
```

### 4. パフォーマンスモニタリング
```bash
# パフォーマンス統計確認
/tofunomics performance

# TPS確認
/tps
```

---

## トラブルシューティング

### 設定ファイルが読み込まれない
**原因**: YAML構文エラー
**対処法**:
1. YAML構文チェッカーで検証
2. インデント（スペース2つ）を確認
3. タブ文字が含まれていないか確認

### 変更が反映されない
**対処法**:
```bash
# サーバーを完全に再起動
/stop

# または設定をリロード
/tofunomics reload
```

### デフォルト設定に戻す
```bash
# 設定ファイルを削除（サーバー停止中）
rm plugins/TofuNomics/config.yml

# サーバー起動時に自動生成される
```

---

## 関連ドキュメント
- **[管理者ガイドTOP](README.md)** - 管理者ガイドトップ
- **[コマンド一覧](commands.md)** - 管理者コマンド
- **[NPC管理](npc-management.md)** - NPC管理ガイド
- **[トラブルシューティング](troubleshooting.md)** - 問題解決

---

**最終更新**: 2024年
**ドキュメントバージョン**: 1.0
