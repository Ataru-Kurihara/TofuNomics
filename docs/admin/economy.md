# 経済システム管理ガイド

> 通貨・価格・取引システムの管理ガイド

## 📋 目次
1. [経済システム概要](#経済システム概要)
2. [通貨システム](#通貨システム)
3. [価格管理](#価格管理)
4. [取引システム](#取引システム)
5. [銀行システム](#銀行システム)
6. [経済バランス調整](#経済バランス調整)
7. [モニタリング・分析](#モニタリング分析)

---

## 経済システム概要

### 基本構造
TofuNomicsの経済は以下の要素で構成されています：
- **通貨**: 金塊（Gold、記号: G）
- **銀行**: 預金・引き出しシステム
- **取引**: NPCとの売買
- **送金**: プレイヤー間送金
- **職業報酬**: 職業活動による収入

### 経済フロー
```
プレイヤー活動
    ↓
職業経験値獲得
    ↓
レベルアップ報酬（金塊）
    ↓
アイテム取引（NPC）
    ↓
銀行預金
    ↓
プレイヤー間送金
```

---

## 通貨システム

### 金塊（Gold）

#### 基本設定
```yaml
economy:
  currency:
    name: "金塊"                  # 通貨名
    symbol: "G"                   # 通貨記号
    decimal_places: 2             # 小数点桁数（0.01G単位）
```

#### 通貨の特徴
- **完全デジタル**: アイテムとしての金塊とは別
- **無限供給**: 職業報酬で自動生成
- **残高管理**: データベースで一元管理
- **送金可能**: プレイヤー間で自由に送金

### 初期残高設定
```yaml
economy:
  starting_balance: 100.0         # 初期残高
```

**推奨設定**:
| サーバータイプ | 初期残高 | 理由 |
|--------------|----------|------|
| 新規サーバー | 50-100G | 経済を徐々に成長させる |
| 既存サーバー | 100-200G | スムーズな移行 |
| 経済重視 | 200-500G | 活発な取引を促進 |

### 残高管理コマンド

#### 残高確認
```bash
# 自分の残高確認
/balance

# 他プレイヤーの残高確認
/balance <プレイヤー名>

# 残高ランキング
/balancetop
```

#### 残高操作（管理者のみ）
```bash
# 残高を付与
/eco give <プレイヤー名> <金額>

# 残高を取得
/eco take <プレイヤー名> <金額>

# 残高を設定
/eco set <プレイヤー名> <金額>
```

**使用例**:
```bash
/eco give Taro_Player 1000    # 1000金塊付与
/eco take Taro_Player 500     # 500金塊取得
/eco set Taro_Player 2000     # 残高を2000金塊に設定
```

---

## 価格管理

### アイテム基準価格

#### 基本設定
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
  jungle_log: 2.0
  acacia_log: 2.0
  dark_oak_log: 2.0

  # 農作物系
  wheat: 1.2
  potato: 1.0
  carrot: 1.0
  beetroot: 1.5
  pumpkin: 3.0
  melon_slice: 0.5

  # 魚類
  cod: 3.0
  salmon: 4.0
  tropical_fish: 8.0
  pufferfish: 10.0

  # 特殊アイテム
  ender_pearl: 40.0
  blaze_rod: 25.0
  ghast_tear: 50.0
```

### 職業別価格ボーナス

#### 設定方法
```yaml
job_price_multipliers:
  miner: 1.2      # 鉱夫は鉱石を20%高く売却
  woodcutter: 1.1 # 木こりは木材を10%高く売却
  farmer: 1.1     # 農家は農作物を10%高く売却
  fisherman: 1.15 # 漁師は魚を15%高く売却
  blacksmith: 1.1 # 鍛冶屋は装備を10%高く売却
  alchemist: 1.15 # 錬金術師はポーションを15%高く売却
  enchanter: 1.1  # 魔術師はエンチャント品を10%高く売却
  architect: 1.05 # 建築家は建材を5%高く売却
```

#### 価格計算例
```
基準価格: ダイヤモンド 60.0G
職業: 鉱夫（倍率1.2）
売却価格: 60.0 × 1.2 = 72.0G
```

### 価格変動システム（オプション）

#### 動的価格設定
```yaml
dynamic_pricing:
  enabled: true                    # 動的価格の有効化

  supply_demand:
    update_interval: 3600          # 更新間隔（秒）
    max_multiplier: 1.5            # 最大倍率
    min_multiplier: 0.7            # 最小倍率

  trending_items:
    diamond: 1.2                   # トレンドアイテム
    emerald: 1.3
```

### 価格調整のベストプラクティス

#### 1. 段階的な変更
```bash
# 価格を10%ずつ調整
# 変更前: 60.0G → 変更後: 66.0G
```

#### 2. プレイヤーへの告知
```yaml
# 価格変更の1週間前に告知
# サーバー内掲示板やDiscordで通知
```

#### 3. バランステスト
```bash
# テストサーバーで価格変更をテスト
# 経済への影響を分析
```

---

## 取引システム

### 取引チェストシステム

#### 設定方法
```bash
# 1. 取引チェストを設置
# 2. チェストを見ながら実行
/trade setup <職業名>

# 3. 取引チェスト一覧確認
/trade list
```

#### 取引制限
```yaml
trade_limits:
  daily:
    max_transactions: 50           # 1日の最大取引回数
    max_amount: 10000              # 1日の最大取引額

  per_transaction:
    max_items: 2304                # 1回の最大アイテム数（チェスト満杯）
    min_value: 1.0                 # 最小取引額
```

### 取引履歴管理

#### 履歴確認コマンド
```bash
# プレイヤーの取引履歴確認
/trade history <プレイヤー名> [日数]
```

**使用例**:
```bash
/trade history Taro_Player 7    # 過去7日間の履歴
/trade history Taro_Player 30   # 過去30日間の履歴
```

#### 履歴データ
```yaml
transaction_history:
  player: "Taro_Player"
  timestamp: "2024-01-15 14:30:00"
  items:
    - material: DIAMOND
      amount: 64
      unit_price: 72.0
      total: 4608.0
  total_value: 4608.0
  balance_after: 5108.0
```

### 取引チェストの管理

#### チェストの削除
```bash
# チェストを見ながら実行
/trade remove
```

#### チェストのリロード
```bash
/trade reload
```

---

## 銀行システム

### 銀行機能

#### 基本機能
- **預け入れ**: 金塊をNPCに預ける
- **引き出し**: 預けた金塊を引き出す
- **残高確認**: 現在の残高を確認
- **取引履歴**: 入出金履歴の確認

#### 銀行NPC設置
```bash
# 銀行NPCをスポーン
/tofunomics npc spawn banker 中央銀行窓口
```

#### 設定
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

    transaction_limits:
      min_deposit: 1.0             # 最小預入額
      min_withdraw: 1.0            # 最小引出額
      max_withdraw: 10000.0        # 最大引出額
```

### 利息システム（オプション）

#### 設定
```yaml
banking:
  interest:
    enabled: true                  # 利息の有効化
    rate: 0.01                     # 利率（1%）
    interval: 86400                # 計算間隔（秒、24時間）
    max_balance: 100000            # 利息対象の最大残高
```

#### 利息計算例
```
残高: 10,000G
利率: 1%
24時間後: 10,000 × 1.01 = 10,100G
```

---

## 経済バランス調整

### インフレーション対策

#### 1. 通貨供給管理
```yaml
# 職業報酬の調整
jobs:
  job_settings:
    all:
      base_income_multiplier: 0.9  # 報酬を10%減少
```

#### 2. 価格調整
```yaml
# アイテム価格の引き上げ
item_prices:
  diamond: 70.0  # 60.0 → 70.0
```

#### 3. 取引制限
```yaml
# 日次取引制限の強化
trade_limits:
  daily:
    max_amount: 5000  # 10000 → 5000
```

### デフレーション対策

#### 1. 報酬増加
```yaml
# 職業報酬の増加
jobs:
  job_settings:
    all:
      base_income_multiplier: 1.2  # 報酬を20%増加
```

#### 2. イベント開催
```yaml
# 期間限定ボーナス
event:
  double_rewards:
    enabled: true
    duration: 604800  # 7日間
```

### 経済バランスの目安

#### 健全な経済指標
- **平均残高**: 1,000-5,000G
- **上位10%残高**: 10,000-50,000G
- **下位10%残高**: 100-500G
- **1日の取引総額**: サーバー総資産の10-20%

---

## 送金システム

### 送金設定
```yaml
economy:
  pay:
    minimum_amount: 1.0           # 最低送金額
    maximum_amount: 5000.0        # 最高送金額
    fee_percentage: 0.0           # 送金手数料（%）
```

### 送金コマンド
```bash
# プレイヤーに送金
/pay <プレイヤー名> <金額>
```

**使用例**:
```bash
/pay Taro_Player 1000    # 1000金塊を送金
```

### 送金手数料の設定
```yaml
# 2%の送金手数料を設定
economy:
  pay:
    fee_percentage: 2.0
```

**計算例**:
```
送金額: 1000G
手数料: 1000 × 0.02 = 20G
受取額: 980G
```

---

## モニタリング・分析

### 経済統計コマンド
```bash
# 経済システムステータス
/tofunomics economy stats

# 残高ランキング
/balancetop

# 取引統計
/trade stats [期間]
```

### モニタリング指標

#### 1. 通貨供給量
```yaml
metrics:
  total_currency: 1000000        # 総通貨量
  active_players: 100            # アクティブプレイヤー数
  average_balance: 10000         # 平均残高
```

#### 2. 取引量
```yaml
metrics:
  daily_transactions: 500        # 1日の取引数
  daily_volume: 50000           # 1日の取引額
```

#### 3. 価格トレンド
```yaml
metrics:
  trending_items:
    - item: DIAMOND
      average_price: 72.0
      transactions: 150
    - item: EMERALD
      average_price: 96.0
      transactions: 80
```

### レポート生成

#### 週次レポート
```bash
# 過去7日間の経済レポート
/tofunomics economy report 7
```

**レポート内容**:
- 総通貨量の変化
- 取引量の推移
- 人気アイテムランキング
- 経済健全性スコア

---

## トラブルシューティング

### 通貨の不正増加

**対処法**:
```bash
# プレイヤーの残高を確認
/balance <プレイヤー名>

# 取引履歴を確認
/trade history <プレイヤー名> 30

# 必要に応じて残高を調整
/eco set <プレイヤー名> <正しい金額>
```

### 価格の異常

**対処法**:
```bash
# config.ymlの価格設定を確認
# 異常な価格を修正
# 設定をリロード
/tofunomics reload
```

### 取引エラー

**対処法**:
```bash
# 取引チェストの状態確認
/trade list

# 取引システムをリロード
/trade reload
```

---

## 関連ドキュメント
- **[管理者ガイドTOP](README.md)** - 管理者ガイドトップ
- **[設定ファイル](configuration.md)** - 経済設定の詳細
- **[NPC管理](npc-management.md)** - 銀行・取引NPC
- **[職業システム](job-system.md)** - 職業報酬システム

---

**最終更新**: 2024年
**ドキュメントバージョン**: 1.0
