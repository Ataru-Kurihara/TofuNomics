# NPC管理ガイド

> NPCシステムの設置・管理・トラブル対応ガイド

## 📋 目次
1. [NPCシステム概要](#npcシステム概要)
2. [NPCコマンド一覧](#npcコマンド一覧)
3. [銀行NPC管理](#銀行npc管理)
4. [取引NPC管理](#取引npc管理)
5. [食料NPC管理](#食料npc管理)
6. [加工NPC管理](#加工npc管理)
7. [初期セットアップ](#初期セットアップ)
8. [トラブルシューティング](#トラブルシューティング)

---

## NPCシステム概要

### NPCの種類
TofuNomicsでは4種類のNPCが利用可能です：

| タイプ | 識別名 | 機能 | 営業時間 |
|--------|--------|------|----------|
| 銀行NPC | `banker` | 預け入れ・引き出し・残高確認 | 24時間 |
| 取引NPC | `trader` | 職業別アイテム買取 | 24時間 |
| 食料NPC | `food_merchant` | 食料販売 | 6:00-22:00 |
| 加工NPC | `processing` | 木材加工サービス | 24時間 |

### NPCの特徴
- **永続性**: サーバー再起動後も維持
- **保護**: 自然削除や攻撃から保護
- **インタラクティブ**: クリックでGUI表示
- **職業連動**: プレイヤーの職業に応じた対応

---

## NPCコマンド一覧

### セットアップコマンド（推奨）

#### 対話式NPC配置ガイド
```bash
/tofunomics npc setup
```
対話式でNPCの配置をガイドします。初めての設置時に推奨。

#### 現在位置にNPC配置
```bash
/tofunomics npc setup-location <type> [args]
```
現在いる場所にNPCを配置します。

**使用例**:
```bash
# 銀行NPCを配置
/tofunomics npc setup-location banker

# 鉱夫用取引NPCを配置
/tofunomics npc setup-location trader miner

# 食料NPCを配置
/tofunomics npc setup-location food_merchant bakery
```

#### 一括セットアップガイド
```bash
/tofunomics npc setup-all
```
全てのNPCを一括で設置するガイドを開始します。

### 基本管理コマンド

#### NPCのスポーン
```bash
# 取引NPCをスポーン
/tofunomics npc spawn trader <職業名> [NPC名]

# 銀行NPCをスポーン
/tofunomics npc spawn banker [NPC名]

# 食料NPCをスポーン
/tofunomics npc spawn food_merchant [NPC名] [タイプ]

# 加工NPCをスポーン
/tofunomics npc spawn processing [NPC名]
```

**使用例**:
```bash
/tofunomics npc spawn trader miner 鉱山取引所
/tofunomics npc spawn banker 中央銀行窓口
/tofunomics npc spawn food_merchant パン屋 bakery
/tofunomics npc spawn processing 木材加工職人
```

#### NPCの削除
```bash
# NPCを削除（復元可能）
/tofunomics npc remove <NPC名>

# NPCを完全削除（復元不可）
/tofunomics npc delete <NPC指定>

# 削除されたNPCを復元
/tofunomics npc restore <NPC名>

# NPCを一時削除（復元可能）
/tofunomics npc purge <NPC名>
```

#### NPC情報・管理
```bash
# NPC一覧表示
/tofunomics npc list [--index]

# NPC状況診断
/tofunomics npc status <NPC名>

# NPCの場所へテレポート
/tofunomics npc tp <NPC名>

# NPCシステム情報表示
/tofunomics npc info
```

#### システム管理
```bash
# NPCシステムリロード
/tofunomics npc reload

# 全NPC完全削除（緊急用）
/tofunomics npc cleanup
```

### 便利な指定方法

#### 職業ショートカット
```bash
m1, m2      # miner（鉱夫）
w1, w2      # woodcutter（木こり）
f1, f2      # farmer（農家）
fish1       # fisherman（漁師）
b1, b2      # blacksmith（鍛冶屋）
a1, a2      # alchemist（錬金術師）
e1, e2      # enchanter（魔術師）
arch1       # architect（建築家）
```

#### インデックス指定
```bash
/tofunomics npc delete 1    # 1番目のNPCを削除
/tofunomics npc tp 3        # 3番目のNPCにテレポート
```

#### 部分名マッチ
```bash
/tofunomics npc remove 鉱山    # "鉱山"を含むNPCを削除
```

---

## 銀行NPC管理

### 銀行NPCの設置手順

#### 1. 設置場所の決定
- 中央エリアの分かりやすい場所
- プレイヤーがアクセスしやすい位置
- 複数設置も可能（支店として）

#### 2. NPCのスポーン
```bash
# 設置したい場所に移動してから実行
/tofunomics npc spawn banker 中央銀行窓口
```

#### 3. config.ymlへの登録
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

#### 4. リロード
```bash
/tofunomics reload
```

### 銀行NPCの機能

#### プレイヤー向け機能
- **預け入れ**: 金塊をNPCに預ける
- **引き出し**: 預けた金塊を引き出す
- **残高確認**: 現在の残高を確認

#### 管理者向け機能
```bash
# プレイヤーの残高を確認
/eco balance <プレイヤー名>

# 残高を強制設定
/eco set <プレイヤー名> <金額>
```

### 設置推奨場所
- **中央銀行**: スポーン地点近く
- **支店**: 各職業エリア近く
- **出張所**: 遠隔地やダンジョン入口

---

## 取引NPC管理

### 取引NPCの設置手順

#### 1. 職業別取引NPCをスポーン
```bash
# 鉱夫用
/tofunomics npc spawn trader miner 鉱山取引所

# 木こり用
/tofunomics npc spawn trader woodcutter 森林市場

# 農家用
/tofunomics npc spawn trader farmer 農場市場

# 漁師用
/tofunomics npc spawn trader fisherman 漁港

# 鍛冶屋用
/tofunomics npc spawn trader blacksmith 鍛冶場

# 錬金術師用
/tofunomics npc spawn trader alchemist 錬金ラボ

# 魔術師用
/tofunomics npc spawn trader enchanter 魔術塔

# 建築家用
/tofunomics npc spawn trader architect 建築スタジオ

# 全職業対応の総合取引所
/tofunomics npc spawn trader all 総合取引所
```

**📌 全職業対応取引NPC（総合取引所）**

職業制限のない総合取引所を設置することで、すべてのプレイヤーがアイテムを売却できる場所を提供できます。

**使用例**:
```bash
# 総合取引所をスポーン
/tofunomics npc spawn trader all 中央総合取引所

# setup-locationでも配置可能
/tofunomics npc setup-location trader all
```

**設置推奨場所**:
- スポーン地点近く（新規プレイヤーのアクセス向上）
- 中央マーケットエリア（交易の中心地）
- 各職業エリアの中間地点（利便性向上）

**特徴**:
- すべての職業のプレイヤーが利用可能
- 職業専用取引所と併用可能
- 職業ボーナス価格は適用されない（基準価格で買取）
```

#### 2. 取引チェストの設置
```bash
# チェストを見ながら実行
/trade setup <職業名>
```

**手順**:
1. 取引用チェストを設置
2. チェストを見る（カーソルを合わせる）
3. `/trade setup miner` コマンドを実行
4. 成功メッセージを確認

#### 3. config.ymlで設定
```yaml
# 鉱夫用取引NPC
mining_post:
  welcome: "&8「よう、%player%。鉱山の取引所にようこそ」"
  accepted_jobs: ["miner"]

# 木こり用取引NPC
wood_market:
  welcome: "&2「%player%さん、森の市場へようこそ」"
  accepted_jobs: ["woodcutter"]

# 全職業対応の総合取引所
general_trading_post:
  welcome: "&6「%player%さん、総合取引所へようこそ！」"
  accepted_jobs: ["all"]  # "all"で全職業対応
  description: "全職業対応の総合取引所"
```

#### 4. リロード
```bash
/tofunomics reload
```

### 取引NPCの機能

#### アイテム買取
- 職業に応じたアイテムを買取
- 職業ボーナス価格を適用
- 取引履歴を記録

#### 管理者向けコマンド
```bash
# 取引チェスト一覧
/trade list

# 取引履歴確認
/trade history <プレイヤー名> [日数]

# 取引チェスト削除
/trade remove
```

### 職業別設置推奨場所

| 職業 | 推奨場所 |
|------|---------|
| 鉱夫 | 鉱山エリア・採掘場近く |
| 木こり | 森林エリア・製材所近く |
| 農家 | 農場エリア・牧場近く |
| 漁師 | 海岸・港・漁場近く |
| 鍛冶屋 | 工房エリア・溶鉱炉近く |
| 錬金術師 | 研究所・醸造所近く |
| 魔術師 | 魔法塔・エンチャントテーブル近く |
| 建築家 | 建築エリア・資材置き場近く |
| **総合取引所（all）** | **スポーン地点・中央マーケット** |

---

## 食料NPC管理

### 食料NPCの特徴
- **営業時間**: 6:00-22:00（ゲーム内時間）
- **購入制限**: 1日32個まで/プレイヤー
- **在庫システム**: 1日64個まで
- **緊急食料**: 夜間・緊急時の食料供給

### 食料NPCの種類

| タイプ | 識別名 | 取扱商品 |
|--------|--------|----------|
| 総合店 | `general_store` | 各種食料品 |
| パン屋 | `bakery` | パン・ケーキ類 |
| 肉屋 | `butcher` | 肉類 |
| 魚屋 | `fishmonger` | 魚類 |
| 八百屋 | `greengrocer` | 野菜・果物 |
| 専門店 | `specialty` | 特殊食料 |

### 食料NPCの設置手順

#### 1. NPCをスポーン
```bash
# 総合店
/tofunomics npc spawn food_merchant 夜間食料商人 general_store

# パン屋
/tofunomics npc spawn food_merchant パン屋 bakery

# 肉屋
/tofunomics npc spawn food_merchant 肉屋 butcher
```

#### 2. config.ymlで設定
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

#### 3. 価格設定
```yaml
food_prices:
  bread: 5.0
  cooked_beef: 8.0
  cooked_porkchop: 8.0
  cooked_chicken: 6.0
  cooked_salmon: 7.0
  apple: 3.0
  golden_apple: 50.0
```

### 営業時間のカスタマイズ
```yaml
# 24時間営業にする場合
food_npc:
  operating_hours:
    start: 0
    end: 24

# 夜間のみ営業（緊急用）
food_npc:
  operating_hours:
    start: 18
    end: 6
```

---

## 加工NPC管理

### 加工NPCの特徴
- **営業時間**: 24時間
- **加工サービス**: 原木から板材への変換
- **料金体系**: 
  - 木こり: 無料
  - 一般プレイヤー: 1G/原木
- **変換比率**: 原木4個 → 板材16個

### 対応木材タイプ
- オーク（樫）
- 白樺（シラカバ）
- 松（トウヒ）
- ジャングル
- アカシア
- ダークオーク（黒樫）
- 真紅（ネザー）
- 歪んだ（ネザー）

### 加工NPCの設置手順

#### 1. NPCをスポーン
```bash
# 基本的な加工NPC
/tofunomics npc spawn processing 木材加工職人

# カスタム名を指定
/tofunomics npc spawn processing 製材所職人
```

#### 2. config.ymlで設定
```yaml
npc_system:
  processing_npc:
    enabled: true
    
    # 料金設定
    base_fee: 1.0              # 基本料金（1G/原木）
    woodcutter_discount: 1.0   # 木こりの割引（100%=無料）
    
    # アイテム制限
    restrictions:
      max_logs_per_transaction: 64  # 1回の最大加工数
    
    # NPC配置
    locations:
      - world: "world"
        x: 100
        y: 70
        z: -50
        id: "processing_1"
        name: "§6木材加工職人"
        yaw: 0.0
        pitch: 0.0
    
    # メッセージ設定
    messages:
      greeting: "&e「木材の加工はお任せください」"
      success: "&a{amount}個の原木を板材に加工しました"
      insufficient_funds: "&c加工料金が不足しています（必要: {fee}G）"
      inventory_full: "&cインベントリに空きがありません"
```

#### 3. リロード
```bash
/tofunomics reload
```

### 加工NPCの機能

#### プレイヤー向け機能
- **木材加工**: 原木を板材に変換
- **自動計算**: 加工料金の自動計算
- **職業特典**: 木こりは無料で加工可能

#### 管理者向け機能
```bash
# 加工NPCの配置確認
/tofunomics npc list

# 加工NPCへテレポート
/tofunomics npc tp 木材加工職人

# 加工NPCの削除
/tofunomics npc remove 木材加工職人
```

### 設置推奨場所
- **製材所**: 森林エリア近く
- **木こり拠点**: 木こり職業の作業場近く
- **中央市場**: アクセスしやすい場所

### 料金体系の調整
```yaml
# 料金を2Gに変更
processing_npc:
  base_fee: 2.0

# 木こりも50%割引に変更
processing_npc:
  woodcutter_discount: 0.5  # 50%割引
```

---

## 初期セットアップ

### 新規サーバー向け基本配置

#### ステップ1: 銀行NPCを配置
```bash
# スポーン地点に移動
/spawn

# 中央銀行を配置
/tofunomics npc spawn banker 中央銀行
```

#### ステップ2: 各職業の取引NPCを配置
```bash
# 各職業エリアに移動して配置
/tofunomics npc spawn trader miner 鉱山取引所
/tofunomics npc spawn trader woodcutter 森林市場
/tofunomics npc spawn trader farmer 農場市場
/tofunomics npc spawn trader fisherman 漁港
/tofunomics npc spawn trader blacksmith 鍛冶場
/tofunomics npc spawn trader alchemist 錬金ラボ
/tofunomics npc spawn trader enchanter 魔術塔
/tofunomics npc spawn trader architect 建築スタジオ
```

#### ステップ3: 取引チェストを設置
各取引NPCの近くにチェストを設置し、関連付け：
```bash
/trade setup miner
/trade setup woodcutter
/trade setup farmer
/trade setup fisherman
/trade setup blacksmith
/trade setup alchemist
/trade setup enchanter
/trade setup architect
```

#### ステップ4: 食料NPCを配置
```bash
# 中央エリアに食料NPCを配置
/tofunomics npc spawn food_merchant 夜間食料商人 general_store
```

#### ステップ5: 加工NPCを配置
```bash
# 製材所エリアに加工NPCを配置
/tofunomics npc spawn processing 木材加工職人
```

#### ステップ6: 設定確認
```bash
# NPC一覧確認
/tofunomics npc list

# 設定をリロード
/tofunomics reload

# ステータス確認
/tofunomics status
```

---

## トラブルシューティング

### NPCが表示されない

**原因**:
- スポーン失敗
- チャンク読み込み問題
- プラグインエラー

**対処法**:
```bash
# NPCリスト確認
/tofunomics npc list

# NPCにテレポート
/tofunomics npc tp <NPC名>

# NPCを再スポーン
/tofunomics npc remove <NPC名>
/tofunomics npc spawn <タイプ> <NPC名>
```

### NPCが反応しない

**原因**:
- アクセス範囲外
- 営業時間外（食料NPC）
- セッションタイムアウト

**対処法**:
```bash
# NPC状況診断
/tofunomics npc status <NPC名>

# 5ブロック以内に近づく
# 右クリックで再度インタラクト

# 営業時間確認（食料NPC）
/time query daytime
```

### 取引チェストが機能しない

**原因**:
- チェスト関連付け失敗
- 職業不一致
- 権限不足

**対処法**:
```bash
# 取引チェスト一覧確認
/trade list

# 取引チェストを再設置
/trade remove
/trade setup <職業名>

# プレイヤーの職業確認
/jobs stats <プレイヤー名>
```

### NPCが重複している

**原因**:
- 複数回スポーン実行
- リロード時の問題

**対処法**:
```bash
# 全NPCをクリーンアップ
/tofunomics npc cleanup

# 必要なNPCを再スポーン
/tofunomics npc setup-all
```

### 設定変更が反映されない

**対処法**:
```bash
# 設定をリロード
/tofunomics reload

# NPCシステムをリロード
/tofunomics npc reload

# サーバーを再起動（最終手段）
/stop
```

---

## ベストプラクティス

### 配置のコツ
1. **視認性**: プレイヤーから見やすい場所
2. **アクセス**: 移動しやすい位置
3. **保護**: 建築保護エリア内に配置
4. **看板**: NPCの機能を説明する看板を設置

### メンテナンス
- **定期確認**: 週1回のNPC動作確認
- **ログ確認**: エラーログの定期チェック
- **バックアップ**: NPC設定のバックアップ

### プレイヤー案内
- **チュートリアル**: 新規プレイヤー向けNPC使用ガイド
- **看板**: 各NPCの機能説明看板
- **ワールドマップ**: NPC位置の明示

---

## 関連ドキュメント
- **[管理者ガイドTOP](README.md)** - 管理者ガイドトップ
- **[設定ファイル](configuration.md)** - NPC設定の詳細
- **[コマンド一覧](commands.md)** - NPCコマンド詳細
- **[トラブルシューティング](troubleshooting.md)** - 問題解決

---

**最終更新**: 2024年
**ドキュメントバージョン**: 1.0
