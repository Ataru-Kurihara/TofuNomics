# TofuNomics 管理者ガイド

## 目次
1. [プラグイン概要](#プラグイン概要)
2. [管理者用コマンド](#管理者用コマンド)
3. [設定ファイル解説](#設定ファイル解説)
4. [NPCシステム管理](#npgシステム管理)
5. [職業システム管理](#職業システム管理)
6. [経済システム管理](#経済システム管理)
7. [トラブルシューティング](#トラブルシューティング)
8. [日常的な管理作業](#日常的な管理作業)

---

## プラグイン概要

### 基本情報
- **プラグイン名**: TofuNomics
- **バージョン**: ${project.version}
- **説明**: tofunomicsワールド専用経済プラグイン
- **対応Minecraft版**: 1.16+
- **API バージョン**: 1.16
- **メインクラス**: org.tofu.tofunomics.TofuNomics
- **開発チーム**: TofuNomics Team

### 主要機能
- **経済システム**: 金塊ベースの通貨システム
- **職業システム**: 8つの専門職業（鉱夫、木こり、農家、漁師、鍛冶屋、錬金術師、魔術師、建築家）
- **取引システム**: 職業別取引チェスト、NPCとの取引
- **NPCシステム**: 銀行NPC、取引NPC、食料NPC
- **スキルシステム**: レベルベースのスキル発動システム
- **クエストシステム**: 職業別クエスト管理
- **スコアボードシステム**: リアルタイム情報表示

---

## 管理者用コマンド

### 基本管理コマンド

#### `/eco <give|take|set|reload>` - 経済管理
**権限**: `tofunomics.admin`

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

**使用例**:
```bash
/eco give Taro_Player 1000    # Taro_Playerに1000金塊付与
/eco take Taro_Player 500     # Taro_Playerから500金塊取得
/eco set Taro_Player 2000     # Taro_Playerの残高を2000金塊に設定
/eco reload                   # 経済システムリロード
```

#### `/tofunomics <reload|status|performance>` - メイン管理コマンド
**権限**: `tofunomics.admin`
**エイリアス**: `/tn`, `/tfn`

```bash
# プラグイン全体をリロード
/tofunomics reload

# プラグインのステータス表示
/tofunomics status

# パフォーマンス統計表示
/tofunomics performance
```

#### `/trade <setup|remove|list|history|reload>` - 取引システム管理
**権限**: `tofunomics.admin`

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

### 権限管理

#### 基本権限構造
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

#### プレイヤー向け権限
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
```

---

## 設定ファイル解説

### config.yml の主要セクション

#### 1. データベース設定
```yaml
database:
  filename: "tofunomics_world.db"  # SQLiteファイル名
  connection_pool:
    max_connections: 10            # 最大接続数
    timeout: 30000                # タイムアウト（ミリ秒）
```

#### 2. 経済システム設定
```yaml
economy:
  currency:
    name: "金塊"                  # 通貨名
    symbol: "G"                   # 通貨記号
    decimal_places: 2             # 小数点桁数
  
  starting_balance: 100.0         # 初期残高
  
  pay:
    minimum_amount: 1.0           # 最低送金額
    maximum_amount: 5000.0        # 最高送金額
    fee_percentage: 0.0           # 送金手数料
```

#### 3. 職業システム設定
```yaml
jobs:
  general:
    max_jobs_per_player: 1        # プレイヤー当たりの最大職業数
    keep_level_on_change: true    # 転職時レベル保持
    # 転職にはレベル50以上が必要（コードで制御）
  
  # 職業別設定
  job_settings:
    miner:
      display_name: "鉱夫"
      max_level: 75
      base_income_multiplier: 1.0
      exp_multiplier: 1.0
```

#### 4. NPCシステム設定
```yaml
npc_system:
  enabled: true                   # NPCシステム有効化
  
  interaction:
    cooldown_ms: 1000            # 相互作用クールダウン
    session_timeout_ms: 300000   # セッションタイムアウト
    access_range: 5              # アクセス範囲
```

### 重要な設定項目の変更方法

#### 職業の最大レベルを変更
```yaml
jobs:
  job_settings:
    miner:
      max_level: 100  # 鉱夫の最大レベルを100に変更
```

#### アイテム価格を調整
```yaml
item_prices:
  diamond: 80.0     # ダイヤモンドの価格を80金塊に変更
  iron_ingot: 8.0   # 鉄インゴットの価格を8金塊に変更
```

#### NPCの場所を設定
```yaml
npc_system:
  bank_npcs:
    locations:
      - world: "tofunomics"
        x: -200
        y: 70
        z: -100
        name: "§bATM案内係"
        type: "atm_assistant"
```

---

## NPCシステム管理

### NPCコマンド一覧

#### セットアップコマンド（推奨）
```bash
# 対話式NPC配置ガイド
/tofunomics npc setup

# 現在位置にNPC配置
/tofunomics npc setup-location <type> [args]

# 一括セットアップガイド
/tofunomics npc setup-all
```

#### 基本コマンド
```bash
# 職業専用取引NPCをスポーン
/tofunomics npc spawn trader <職業名> [NPC名]

# 銀行NPCをスポーン
/tofunomics npc spawn banker [NPC名]

# 食料NPCをスポーン（タイプ: general_store, bakery, butcher, fishmonger, greengrocer, specialty）
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

#### 利用可能な職業
- miner, woodcutter, farmer, fisherman, blacksmith, alchemist, enchanter, architect

#### 簡単指定方法
- 職業ショートカット: m1,m2,w1,f1,fish1,b1,a1,e1,arch1
- インデックス番号: 1,2,3...
- 部分名マッチも対応

### 銀行NPC管理

#### 銀行NPCの設置
1. 設置したい場所に移動
2. `/tofunomics npc spawn banker <NPC名>` コマンドを実行
3. `config.yml`の`bank_npcs.locations`セクションに座標を追加
4. `/tofunomics reload` でリロード

#### 設定例
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

### 取引NPC管理

#### 取引NPCの設置方法
1. `/tofunomics npc spawn trader <職業名> <NPC名>` コマンドで作成
2. チェストを見ながら `/trade setup <職業名>` で取引チェストを関連付け
3. `config.yml`で職業別メッセージを設定

#### 職業別取引NPC設定
```yaml
# 鉱夫用取引NPC
mining_post:
  welcome: "&8「よう、%player%。鉱山の取引所にようこそ」"
  accepted_jobs: ["miner"]
  
# 木こり用取引NPC  
wood_market:
  welcome: "&2「%player%さん、森の市場へようこそ」"
  accepted_jobs: ["woodcutter"]
```

### 食料NPC管理

#### 食料NPCの特徴
- 夜間・緊急時の食料供給
- 営業時間: 6:00-22:00
- 購入制限: 1日32個まで
- 在庫システム: 1日64個まで

#### 食料NPCの設置
1. 設置したい場所に移動
2. `/tofunomics npc spawn food_merchant <NPC名> <タイプ>` コマンドで作成
   - タイプ: general_store, bakery, butcher, fishmonger, greengrocer, specialty

```yaml
food_npc:
  locations:
    - world: "tofunomics"
      x: -50
      y: 70
      z: -200
      name: "§6夜間食料品商人"
      description: "緊急時の食料品を販売"
```

---

## 職業システム管理

### 8つの職業詳細

#### 1. 鉱夫（miner）
- **専門分野**: 鉱物資源、石材
- **専用ブロック**: 各種鉱石、石材
- **スキル**: 幸運の一撃、鉱脈発見、採掘の極意

#### 2. 木こり（woodcutter）
- **専門分野**: 木材資源
- **専用ブロック**: 原木、葉ブロック、キノコブロック
- **スキル**: 一斉伐採、苗木の恵み、森の番人

#### 3. 農家（farmer）
- **専門分野**: 農作物、畜産
- **専用ブロック**: 作物、畜産関連ブロック
- **スキル**: 豊穣の恵み、双子の奇跡、成長促進、品種改良

#### 4. 漁師（fisherman）
- **専門分野**: 魚類、海産物
- **専用ブロック**: 水関連、海洋ブロック
- **スキル**: 大物釣り、宝探し、海の加護

#### 5. 鍛冶屋（blacksmith）
- **専門分野**: ツール、武器、防具
- **専用ブロック**: 鍛冶関連ブロック
- **スキル**: 完璧な修理、名工の技、神器創造

#### 6. 錬金術師（alchemist）
- **専門分野**: ポーション、薬品
- **専用ブロック**: 醸造関連ブロック
- **スキル**: 材料節約、二重醸造、錬金の極意

#### 7. 魔術師（enchanter）
- **専門分野**: エンチャント、魔術
- **専用ブロック**: エンチャント関連ブロック
- **スキル**: 経験値節約、ボーナスエンチャント、魔術の神秘

#### 8. 建築家（architect）
- **専門分野**: 建築、装飾
- **専用ブロック**: 建材、装飾ブロック
- **スキル**: 材料節約、建築の美学、巨匠の設計

### 職業管理コマンド

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

### レベルアップ報酬システム

#### 基本報酬
- **金塊**: レベル × 2.5 × 50金塊（最大500金塊）
- **スキルポイント**: レベル10, 25, 50, 75で1ポイント

#### マイルストーン報酬
```yaml
level_25:
  miner:
    - ダイヤのツルハシ（効率3、耐久2）
  woodcutter:  
    - ダイヤの斧（効率3、耐久2）
```

---

## 経済システム管理

### 通貨システム

#### 金塊ベースの通貨
- **基本通貨**: 金塊（Gold）
- **記号**: G
- **小数点**: 2桁まで
- **豆腐コイン**: 設定可能な価値システム

#### 残高管理
```bash
# 残高確認
/balance <プレイヤー名>

# 残高ランキング
/balancetop

# 送金
/pay <プレイヤー名> <金額>
```

### アイテム価格システム

#### 基準価格設定
```yaml
item_prices:
  # 鉱物系
  coal: 2.5
  iron_ingot: 6.0
  gold_ingot: 12.0
  diamond: 60.0
  
  # 木材系
  oak_log: 1.8
  birch_log: 1.8
  
  # 農作物系
  wheat: 1.2
  potato: 1.0
```

#### 職業別価格ボーナス
```yaml
job_price_multipliers:
  miner: 1.2      # 鉱夫は鉱石を20%高く売却
  woodcutter: 1.1 # 木こりは木材を10%高く売却
  farmer: 1.1     # 農家は農作物を10%高く売却
```

### 取引制限設定

#### 日次制限
- **最大取引回数**: 1日50回
- **最大取引額**: 1日10,000金塊
- **最大アイテム数**: 1回2304個（チェスト満杯分）

#### 送金制限
- **最低送金額**: 1.0金塊
- **最高送金額**: 5,000金塊
- **送金手数料**: 0%（設定変更可能）

---

## トラブルシューティング

### よくある問題と対処法

#### 1. プラグインが正常に動作しない
**症状**: コマンドが認識されない、エラーメッセージが出る
**対処法**:
```bash
# サーバーログを確認
tail -f logs/latest.log

# プラグインリストを確認  
/plugins

# プラグインをリロード
/tofunomics reload
```

#### 2. データベースエラー
**症状**: プレイヤーデータが保存されない
**対処法**:
```bash
# データベースファイルの権限を確認
ls -la plugins/TofuNomics/tofunomics_world.db

# バックアップから復元
cp plugins/TofuNomics/backups/tofunomics_world_backup.db plugins/TofuNomics/tofunomics_world.db
```

#### 3. NPCが応答しない
**症状**: NPCをクリックしても反応がない
**対処法**:
```bash
# NPC設定を確認
/tofunomics npc list

# NPCを再配置
/tofunomics npc remove <NPC名>
/tofunomics npc spawn <タイプ> <NPC名>

# 設定をリロード
/tofunomics reload
```

#### 4. 職業スキルが発動しない
**症状**: レベルが上がってもスキルが使えない
**対処法**:
```bash
# プレイヤーの職業情報を確認
/jobs stats <プレイヤー名>

# 職業データをリセット
/jobs reset <プレイヤー名> <職業名>

# 経験値を再付与
/jobs addexp <プレイヤー名> <職業名> <経験値>
```

#### 5. パフォーマンス問題
**症状**: サーバーが重い、TPS低下
**対処法**:
```bash
# パフォーマンス統計を確認
/tofunomics performance

# 重いプロセスを特定
/tofunomics status

# 設定を最適化（config.yml）
performance:
  database:
    batch_processing:
      enabled: true
      batch_size: 50
```

### ログファイル確認

#### 重要なログファイル
1. **サーバーログ**: `logs/latest.log`
2. **プラグインログ**: `plugins/TofuNomics/logs/tofunomics.log`
3. **エラーログ**: `plugins/TofuNomics/logs/error.log`

#### ログレベル設定
```yaml
debug:
  enabled: false        # 通常は false
  verbose: false        # 詳細ログが必要な時のみ true
  npc_debug: false      # NPCデバッグが必要な時のみ true
```

### バックアップ・復旧手順

#### 手動バックアップ
```bash
# プラグインフォルダ全体をバックアップ
cp -r plugins/TofuNomics/ backups/TofuNomics_$(date +%Y%m%d_%H%M%S)/

# データベースのみバックアップ
cp plugins/TofuNomics/tofunomics_world.db backups/tofunomics_world_$(date +%Y%m%d_%H%M%S).db
```

#### 自動バックアップ設定（cron例）
```bash
# 毎日午前3時にバックアップ
0 3 * * * cp /path/to/server/plugins/TofuNomics/tofunomics_world.db /path/to/backups/tofunomics_world_$(date +%Y%m%d).db
```

---

## 日常的な管理作業

### 定期的な作業チェックリスト

#### 毎日
- [ ] サーバーログの確認
- [ ] プレイヤーアクティビティの確認
- [ ] エラーログのチェック
- [ ] パフォーマンス統計の確認

#### 毎週
- [ ] データベースバックアップ
- [ ] 設定ファイルのバックアップ
- [ ] プレイヤーフィードバックの確認
- [ ] 経済バランスの調査

#### 毎月
- [ ] 古いログファイルの削除
- [ ] データベースクリーンアップ
- [ ] 価格設定の見直し
- [ ] 新機能の検討

### パフォーマンス監視

#### 重要な指標
```bash
# TPS（Ticks Per Second）確認
/tofunomics performance

# メモリ使用量確認
/tofunomics status

# データベース統計
/tofunomics database stats
```

#### パフォーマンス最適化設定
```yaml
performance:
  # データベース最適化
  database:
    connection_pool:
      maximum_pool_size: 15
      minimum_idle: 3
    
    batch_processing:
      enabled: true
      batch_size: 100
  
  # キャッシュ最適化
  caching:
    player_cache:
      max_size: 1000
      expire_after_access: 1800
```

### プレイヤーサポート

#### よくある質問と回答

**Q: 職業を変更したい**
**A**: `/jobs leave` で現在の職業を辞めてから `/jobs join <職業名>` で新しい職業に就職してください。ただし、24時間のクールダウンがあります。

**Q: NPCが反応しない**
**A**: NPCから5ブロック以内に近づいて右クリックしてください。営業時間外の場合は利用できません。

**Q: スキルが使えない**
**A**: スキルは特定のレベルに達すると確率で発動します。発動確率はレベルが上がると向上します。

#### 管理者向けサポートコマンド
```bash
# プレイヤーの詳細情報確認
/jobs info <プレイヤー名>

# 残高を強制調整
/eco set <プレイヤー名> <金額>

# 職業を強制変更
/jobs setjob <プレイヤー名> <職業名>

# 取引履歴確認
/trade history <プレイヤー名> 30
```

### アップデート・メンテナンス

#### アップデート前の準備
1. **フルバックアップ**: プラグインフォルダとワールドデータ
2. **テスト環境での検証**: 新バージョンの動作確認
3. **プレイヤー告知**: メンテナンス時間の事前通知

#### メンテナンス手順
1. プレイヤーに告知（10分前、5分前、1分前）
2. サーバー停止
3. バックアップ実行
4. プラグインファイル更新
5. 設定ファイルの互換性確認
6. サーバー起動・動作確認

### トラブル時の緊急対応

#### プラグイン無効化
```bash
# プラグインを一時的に無効化
/plugins disable TofuNomics

# プラグインを有効化
/plugins enable TofuNomics
```

#### セーフモード起動
```yaml
# config.yml に追加
safe_mode:
  enabled: true
  disable_economy: false
  disable_jobs: false  
  disable_npc: false
```

#### 緊急連絡先
- **開発チーム**: [連絡先情報]
- **サーバー管理者**: [連絡先情報]
- **GitHub Issues**: https://github.com/tofu-server/TofuNomics/issues

---

## 付録

### 設定テンプレート

#### 小規模サーバー向け設定
```yaml
economy:
  starting_balance: 50.0
jobs:
  general:
    max_jobs_per_player: 2
performance:
  database:
    connection_pool:
      maximum_pool_size: 5
```

#### 大規模サーバー向け設定  
```yaml
economy:
  starting_balance: 200.0
performance:
  database:
    connection_pool:
      maximum_pool_size: 20
    batch_processing:
      batch_size: 200
```

### よく使用するコマンド一覧
```bash
# システム管理
/tofunomics reload
/tofunomics status  
/tofunomics performance

# 経済管理
/eco give <プレイヤー> <金額>
/eco take <プレイヤー> <金額>
/balancetop

# 職業管理
/jobs setjob <プレイヤー> <職業>
/jobs addexp <プレイヤー> <職業> <経験値>
/jobs stats <プレイヤー>

# 取引管理
/trade setup <職業>
/trade list
/trade history <プレイヤー>

# NPC管理
/tofunomics npc spawn <タイプ> <名前>  # NPCをスポーン
/tofunomics npc remove <名前>          # NPCを削除
/tofunomics npc list                   # NPC一覧表示
```

### エラーコード一覧
| コード | 説明 | 対処法 |
|--------|------|--------|
| DB_001 | データベース接続エラー | 接続設定確認、ファイル権限確認 |
| NPC_001 | NPC作成失敗 | 座標確認、権限確認 |
| JOB_001 | 職業システムエラー | 職業設定確認、プレイヤーデータ確認 |
| ECO_001 | 経済システムエラー | 残高データ確認、設定確認 |

---

**最終更新**: 2024年
**ドキュメントバージョン**: 1.0
**対応プラグインバージョン**: ${project.version}