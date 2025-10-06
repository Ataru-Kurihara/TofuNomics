# 住居賃貸システム管理ガイド

## 概要

住居賃貸システムは、ワールド内の建築物をプレイヤーに期間制で賃貸できる機能です。運営者が事前に登録した物件を、プレイヤーが日・週・月単位で借りることができます。

## システムの特徴

- **ハイブリッド物件管理**: 座標範囲指定またはWorldGuard領域IDで物件を定義
- **期間制賃貸**: 日額・週額・月額の柔軟な料金設定
- **自動期限管理**: 契約期限の自動チェックと終了処理
- **範囲選択ツール**: WorldEditライクな木の斧での範囲選択
- **WorldGuard統合**: 賃貸契約と連動した自動アクセス権限管理
- **一括設定**: 物件登録とWorldGuard領域作成を1コマンドで実行

## 基本設定

### config.ymlの設定

```yaml
housing_rental:
  enabled: true                    # システムの有効/無効
  max_rentals_per_player: 3       # プレイヤー1人あたりの最大契約数
  selection_tool: WOODEN_AXE       # 範囲選択ツール
  rental_periods:
    daily:
      enabled: true
      min_days: 1                  # 最小賃貸日数
      max_days: 30                 # 最大賃貸日数
    weekly:
      enabled: true
      multiplier: 6.5              # 週額 = 日額 × 6.5
    monthly:
      enabled: true
      multiplier: 25.0             # 月額 = 日額 × 25
  worldguard_integration: true     # WorldGuard連携（自動有効化）
  expire_check_interval: 3600      # 期限チェック間隔（秒）
  expire_notification_days: 3      # 期限前通知日数
```

## 物件管理

### 物件の登録

物件を登録するには、以下の手順を実行します：

1. **範囲選択**（座標方式の場合）
   ```
   - 木の斧を手に持つ
   - 左クリックで第1座標を設定
   - 右クリックで第2座標を設定
   - 選択範囲が緑のパーティクルで表示される
   ```

2. **物件登録コマンド**
   
   **基本的な登録（座標範囲のみ）**:
   ```bash
   /housing admin register <物件名> <日額賃料>
   ```
   
   例:
   ```bash
   /housing admin register "中央区マンション101号室" 100
   ```

3. **WorldGuard統合登録（推奨）**
   
   物件登録と同時にWorldGuard領域を自動作成し、アクセス権限を自動管理します：
   
   ```bash
   /housing admin register <物件名> <日額賃料> --wg <領域名>
   ```
   
   例:
   ```bash
   /housing admin register "中央区マンション101号室" 100 --wg mansion_101
   ```
   
   **WorldGuard統合のメリット**:
   - 賃貸契約時に自動的にプレイヤーをWorldGuardメンバーに追加
   - プレイヤーはドア、チェスト、アイテムを自動的に使用可能に
   - 契約終了・キャンセル時に自動的にメンバーから削除
   - 非メンバーは物件内のドアやチェストにアクセス不可

### 物件情報の確認

```bash
# 全物件一覧
/housing admin list

# 特定物件の詳細
/housing info <物件ID>
```

出力例:
```
========= 物件情報 =========
ID: 1
名前: 中央区マンション101号室
ワールド: world
説明: 駅近の便利な物件
賃料:
  日額: 100.0
  週額: 650.0
  月額: 2500.0
状態: 利用可能
============================
```

### 賃料の変更

```bash
/housing admin setrent <物件ID> <新しい日額>
```

例:
```
/housing admin setrent 1 150
```

### 物件の削除

```bash
/housing admin remove <物件ID>
```

⚠️ **注意**: 現在賃貸中の物件を削除する場合は、事前にプレイヤーに通知してください。

## プレイヤー向け機能

### 物件の賃貸

プレイヤーは以下のコマンドで物件を借りられます：

```bash
# 物件一覧を見る
/housing list

# 物件詳細を見る
/housing info <物件ID>

# 物件を借りる
/housing rent <物件ID> <期間>
```

期間の指定方法:
- `7` - 7日間
- `2w` - 2週間
- `1m` - 1ヶ月

例:
```bash
/housing rent 1 7      # 物件#1を7日間借りる
/housing rent 2 2w     # 物件#2を2週間借りる
/housing rent 3 1m     # 物件#3を1ヶ月借りる
```

### 契約の管理

```bash
# 自分の契約一覧
/housing myrentals

# 契約延長
/housing extend <物件ID> <追加日数>

# 契約キャンセル
/housing cancel <物件ID>
```

## 自動処理

### 期限切れチェック

システムは設定した間隔（デフォルト: 1時間）で期限切れの契約をチェックし、自動的に以下の処理を実行します：

1. 契約ステータスを「expired」に更新
2. 物件を「利用可能」状態に戻す
3. 契約履歴に記録
4. プレイヤーがオンラインの場合は通知

### 期限前通知（将来実装予定）

契約期限の3日前にプレイヤーに通知を送信する機能を実装予定です。

## データベース構造

### housing_properties テーブル

物件情報を格納します：

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | INTEGER | 物件ID |
| property_name | TEXT | 物件名 |
| world_name | TEXT | ワールド名 |
| x1, y1, z1 | INTEGER | 座標1（NULL可） |
| x2, y2, z2 | INTEGER | 座標2（NULL可） |
| worldguard_region_id | TEXT | WG領域ID（NULL可） |
| description | TEXT | 説明 |
| daily_rent | REAL | 日額賃料 |
| weekly_rent | REAL | 週額賃料（NULL時は自動計算） |
| monthly_rent | REAL | 月額賃料（NULL時は自動計算） |
| is_available | BOOLEAN | 利用可能フラグ |
| owner_uuid | TEXT | 所有者UUID（NULL=運営） |

### housing_rentals テーブル

賃貸契約情報を格納します：

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | INTEGER | 契約ID |
| property_id | INTEGER | 物件ID |
| tenant_uuid | TEXT | 借主UUID |
| start_date | TIMESTAMP | 開始日時 |
| end_date | TIMESTAMP | 終了日時 |
| period | TEXT | 期間種別 |
| rental_days | INTEGER | 賃貸日数 |
| total_cost | REAL | 合計費用 |
| status | TEXT | ステータス |

### housing_rental_history テーブル

賃貸履歴を格納します：

| カラム名 | 型 | 説明 |
|---------|-----|------|
| id | INTEGER | 履歴ID |
| rental_id | INTEGER | 契約ID |
| property_id | INTEGER | 物件ID |
| tenant_uuid | TEXT | 借主UUID |
| action | TEXT | アクション |
| action_date | TIMESTAMP | 実行日時 |
| amount | REAL | 金額 |

## トラブルシューティング

### 物件が登録できない

**症状**: 物件登録時にエラーが発生する

**原因と対処法**:
1. 範囲選択が完了していない
   - 木の斧で両方の座標を設定したか確認

2. 座標がワールド外
   - 選択範囲が有効なワールド座標内にあるか確認

3. 権限不足
   - `tofunomics.housing.admin` 権限があるか確認

### プレイヤーが物件を借りられない

**症状**: `/housing rent` コマンドでエラーが発生

**原因と対処法**:
1. 残高不足
   - `/balance` で残高を確認
   - 必要な金額は `/housing info <ID>` で確認可能

2. 契約上限に達している
   - `/housing myrentals` で現在の契約数を確認
   - `max_rentals_per_player` 設定を見直し

3. 物件が既に賃貸中
   - `/housing list` で利用可能な物件を確認

### 期限切れ処理が実行されない

**症状**: 契約期限が過ぎても自動終了しない

**原因と対処法**:
1. バッチ処理の間隔が長い
   - `expire_check_interval` を短く設定（最低でも1時間推奨）

2. サーバーの再起動
   - サーバー再起動後、次のチェック時刻まで処理されない
   - 手動で契約をキャンセルする場合は `/housing admin cancel <契約ID>`

## ベストプラクティス

### 物件登録時

1. **明確な命名規則**
   - 地区名や部屋番号を含める
   - 例: "中央区マンション101号室"、"商店街テナント#5"
   - WorldGuard領域名も統一規則で命名（例: mansion_101, shop_5）

2. **適切な賃料設定**
   - 物件の立地や広さに応じた料金設定
   - 週額・月額は自動計算されるため、日額のみ設定

3. **WorldGuard統合の活用**
   - 可能な限り`--wg`オプションを使用
   - セキュリティと管理の自動化が向上
   - 領域名は物件名と関連付けると管理しやすい

4. **説明文の追加**
   - 物件の特徴や設備を記載
   - 将来的に description フィールドをサポート予定

### 運用管理

1. **定期的な契約確認**
   - 週1回程度、全契約をチェック
   - トラブルや不正利用がないか確認

2. **プレイヤーへの事前通知**
   - 賃料変更や物件削除の際は事前に告知
   - 最低でも1週間前に通知

3. **バックアップ**
   - データベースの定期バックアップ
   - `tofunomics.db` ファイルを保存

## 権限一覧

| 権限ノード | 説明 | デフォルト |
|-----------|------|-----------|
| tofunomics.housing.use | 基本機能の使用 | true |
| tofunomics.housing.admin | 管理者機能の使用 | op |

## WorldGuard統合の詳細

### 自動権限管理

WorldGuard統合を有効にすると、以下の処理が自動的に行われます：

#### 物件登録時
1. 選択範囲からWorldGuard保護領域を自動作成
2. 以下のフラグを自動設定：
   - `use: deny` - 非メンバーはドアやボタンを使用不可
   - `chest-access: deny` - 非メンバーはチェストにアクセス不可
   - `interact: deny` - 非メンバーはアイテムの使用不可

#### 賃貸契約時
1. プレイヤーを自動的にWorldGuard領域のメンバーに追加
2. プレイヤーはドア、チェスト、アイテムの使用が可能に

#### 契約終了・キャンセル時
1. プレイヤーを自動的にWorldGuard領域のメンバーから削除
2. プレイヤーのアクセス権が即座に剥奪

### WorldGuard統合の確認

```bash
# WorldGuard領域の確認
/rg info <領域名>

# 領域メンバーの確認
/rg info <領域名> -m

# 領域フラグの確認
/rg info <領域名> -f
```

### トラブルシューティング

#### WorldGuardが見つからない
**症状**: 物件登録時にWorldGuard統合が機能しない

**対処法**:
1. WorldGuardプラグインがインストールされているか確認
2. サーバーログで「WorldGuard統合が有効化されました」を確認
3. WorldGuardのバージョンを確認（7.0.7以上推奨）

#### 領域が既に存在する
**症状**: 領域名が重複しているとエラーが出る

**対処法**:
1. 別の領域名を使用する
2. 既存の領域を削除してから再登録
3. `/rg list` で既存の領域を確認

## 今後の機能拡張予定

- 期限前通知機能
- プレイヤー間の物件売買
- 物件の所有権譲渡
- 賃料の自動徴収（週払い・月払い）
- 延滞料金システム
- 物件のアップグレード機能
- 複数プレイヤーでの共同賃貸
