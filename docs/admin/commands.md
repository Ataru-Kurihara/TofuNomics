# 管理者用コマンド一覧

## 基本管理コマンド

### `/eco <give|take|set|reload>` - 経済管理
**権限**: `tofunomics.admin`

#### サブコマンド
```bash
# プレイヤーに金塊を付与
/eco give <プレイヤー名> <金額>

# プレイヤーから金塊を取得
/eco take <プレイヤー名> <金額>

# プレイヤーの残高を設定
/eco set <プレイヤー名> <金額>

# 経済システムをリロード
/eco reload
```

#### 使用例
```bash
/eco give Taro_Player 1000    # Taro_Playerに1000金塊付与
/eco take Taro_Player 500     # Taro_Playerから500金塊取得
/eco set Taro_Player 2000     # Taro_Playerの残高を2000金塊に設定
/eco reload                   # 経済システムリロード
```

### `/tofunomics <reload|status|performance>` - メイン管理コマンド
**権限**: `tofunomics.admin`
**エイリアス**: `/tn`, `/tfn`

#### サブコマンド
```bash
# プラグイン全体をリロード
/tofunomics reload

# プラグインのステータス表示
/tofunomics status

# パフォーマンス統計表示
/tofunomics performance
```

### `/trade <setup|remove|list|history|reload>` - 取引システム管理
**権限**: `tofunomics.admin`

#### サブコマンド
```bash
# 取引チェストを設置（チェストを見ながら実行）
/trade setup <職業名>

# 取引チェストを削除（チェストを見ながら実行）
/trade remove

# すべての取引チェストを一覧表示
/trade list

# 取引履歴を表示
/trade history <プレイヤー名> [日数]

# 取引システムをリロード
/trade reload
```

#### 使用例
```bash
# 鉱夫用取引チェストを設置
/trade setup miner

# 過去7日間の取引履歴表示
/trade history Taro_Player 7
```

## NPCコマンド

### `/tofunomics npc` - NPC管理
**権限**: `tofunomics.npc.admin`

#### セットアップコマンド（推奨）
```bash
# 対話式NPC配置ガイド
/tofunomics npc setup

# 現在位置にNPC配置
/tofunomics npc setup-location <type> [args]

# 一括セットアップガイド
/tofunomics npc setup-all
```

#### 基本NPCコマンド
```bash
# 職業専用取引NPCをスポーン
/tofunomics npc spawn trader <職業名> [NPC名]

# 銀行NPCをスポーン
/tofunomics npc spawn banker [NPC名]

# 食料NPCをスポーン
/tofunomics npc spawn food_merchant [NPC名] [タイプ]

# NPCを削除
/tofunomics npc remove <NPC名>

# NPC完全削除（復元不可）
/tofunomics npc delete <NPC指定>

# NPC状況診断
/tofunomics npc status <NPC名>

# NPCの場所へテレポート
/tofunomics npc tp <NPC名>

# NPC一覧表示
/tofunomics npc list [--index]

# 全NPC完全削除（緊急用）
/tofunomics npc cleanup

# NPCシステムリロード
/tofunomics npc reload

# NPCシステム情報表示
/tofunomics npc info

# NPCを削除（復元可能）
/tofunomics npc purge <NPC名>

# 削除されたNPCを復元
/tofunomics npc restore <NPC名>
```

#### NPCタイプ一覧
**職業**: miner, woodcutter, farmer, fisherman, blacksmith, alchemist, enchanter, architect

**食料NPCタイプ**: general_store, bakery, butcher, fishmonger, greengrocer, specialty

#### 簡単指定方法
- **職業ショートカット**: m1,m2,w1,f1,fish1,b1,a1,e1,arch1
- **インデックス番号**: 1,2,3...
- **部分名マッチ**: NPCの部分名での指定も対応

## 職業管理コマンド

### `/jobs` - 職業システム管理
**権限**: `tofunomics.jobs.admin`

#### プレイヤーの職業を強制変更
```bash
/jobs setjob <プレイヤー名> <職業名>
```

#### 職業経験値を調整
```bash
/jobs addexp <プレイヤー名> <職業名> <経験値>
```

#### 職業レベルを設定
```bash
/jobs setlevel <プレイヤー名> <職業名> <レベル>
```

#### 職業情報確認
```bash
/jobs info <プレイヤー名>
/jobs stats <プレイヤー名>
```

#### 職業データリセット
```bash
/jobs reset <プレイヤー名> <職業名>
```

## 権限管理

### 基本権限構造
```yaml
# 最高権限（OPデフォルト）
tofunomics.*:
  description: 全ての権限
  default: op

# 管理者権限
tofunomics.admin:
  description: 管理者権限
  default: op

# 詳細管理権限
tofunomics.admin.reload:        # 設定リロード権限
tofunomics.admin.performance:   # パフォーマンス統計権限
tofunomics.admin.alerts:        # パフォーマンスアラート受信権限
```

### プレイヤー向け権限
```yaml
# 経済関連
tofunomics.balance.self:     # 自分の残高確認
tofunomics.balance.others:   # 他人の残高確認
tofunomics.pay:             # 送金権限
tofunomics.withdraw:        # 金塊引き出し
tofunomics.deposit:         # 金塊預け入れ

# 職業関連
tofunomics.jobs.basic:      # 基本職業コマンド
tofunomics.jobs.admin:      # 職業管理コマンド（OP）

# 取引関連
tofunomics.trade.setup:     # 取引チェスト設置（OP）
tofunomics.trade.remove:    # 取引チェスト削除（OP）
tofunomics.trade.basic:     # 基本取引機能

# NPC関連
tofunomics.npc.admin:       # NPC管理権限（OP）

# スコアボード関連
tofunomics.scoreboard.view:   # スコアボード表示
tofunomics.scoreboard.toggle: # スコアボード切り替え
```

## プレイヤー向けコマンド

### 経済コマンド
```bash
/balance [player]           # 残高確認
/pay <player> <amount>      # 送金
/balancetop                 # 残高ランキング
/withdraw <amount>          # 金塊引き出し
/deposit <amount|all>       # 金塊預け入れ
```

### 職業コマンド
```bash
/jobs join <jobname>        # 職業に就職
/jobs leave                 # 職業を辞職
/jobs stats [jobname]       # 職業統計表示
/jobstats [jobname|top]     # 詳細職業統計
```

### クエストコマンド
```bash
/quest list                 # クエスト一覧
/quest accept <questname>   # クエスト受注
/quest progress             # クエスト進捗確認
```

### その他
```bash
/scoreboard [toggle|on|off] # スコアボード切り替え
```

## コマンド使用時の注意点

### 1. 権限確認
- 各コマンドには適切な権限が必要
- 権限不足の場合はエラーメッセージが表示

### 2. プレイヤー名指定
- オンラインプレイヤーのみ指定可能
- 大文字小文字を区別

### 3. 職業名指定
- 正確な職業名を使用
- 略称での指定も一部対応

### 4. 数値指定
- 金額・経験値等は正の数値のみ
- 上限値を超える場合はエラー

## よく使用するコマンド組み合わせ

### 新規プレイヤーサポート
```bash
# 初期設定支援
/eco give <プレイヤー名> 500
/jobs setjob <プレイヤー名> miner
/jobs addexp <プレイヤー名> miner 1000
```

### NPCセットアップ
```bash
# 鉱夫用取引所設置
/tofunomics npc spawn trader miner 鉱山取引所
/trade setup miner
```

### 緊急対応
```bash
# システムリロード
/tofunomics reload
/eco reload
/tofunomics npc reload
```

---

**[← プラグイン概要](overview.md)**
**[次: 設定ファイル解説 →](configuration.md)**