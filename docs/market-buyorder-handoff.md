# マーケット「買い注文（募集）」機能 実装 引き継ぎ書

> 別セッションで「募集（買い注文 / Buy Order）」機能を実装するための完全な引き継ぎ書。
> 作成日: 2026-06-11 / 作成: Claude Opus 4.8 セッション
> 前提: 売り出品マーケット（S1〜S4）は実装・デプロイ済み。本機能はその**対になる買い注文**を追加する。
> 関連: `docs/market-implementation-handoff.md`（売りマーケットの正本）, `docs/config-reference.md`

---

## 0. まず最初に確認すること

- **ブランチ**: `feature/player-market`（このまま続行）。`main` 直接コミット禁止、PR でマージ。
- **売りマーケットは完成済み**。直近コミット（新しい順）:
  - `5470065` feat: 一覧GUIにカテゴリフィルタ
  - `4f4a881` fix: sold_notify の %item%/%currency% 未置換修正
  - `6d1c452` fix: 購入を現金（手持ち金塊）払いに変更
  - `03cc03f` S4(コマンド＋配線) / `c3f7491` S3(GUI＋タスク) / `99ac0c8` S2 / `7cc1093` S1
- デプロイ済み（lobby-1.21）。リロードはユーザー手動。`tofu-mc-deploy --skip-build` でデプロイ。
- シェルは fish。bash 専用構文は `bash -c '...'` で囲む。

---

## 1. 確定仕様（ユーザー合意済み・2026-06-11）

「**出品アイテムの募集**」= 買い注文。あるプレイヤーが「このアイテムをこの値段で買いたい」と募集を出し、
別プレイヤーが手持ちから供給して成立させる。売り出品の鏡像。

| 項目 | 決定 |
|---|---|
| 代金拘束 | **募集時に前払い（エスクロー）**。募集を出した時点で募集者の**現金（金塊）から price 全額を回収**し拘束する。 |
| 手数料 | **売りと同じ消却型 5%**（`market.fee_rate` を共用）。供給者受取 = `floor(price × 0.95)`、差額は通貨総量から消却。 |
| 供給UI | **GUI 一覧からクリック供給**。募集一覧GUIで募集をクリックすると、手持ちから該当アイテムを供給して成立。 |
| オフライン対応 | 募集者がオフラインでも供給成立可。**供給されたアイテムは注文レコードに保存**し、募集者が後で「自分の募集」GUIから回収する（売りの expired 回収と同方式）。 |
| 対象アイテム | **Material + 個数のみ**で募集（特定NBT/エンチャントは対象外）。供給者は同 Material を合計個数ぶん供給。 |

### ライフサイクル（status）
- `open`: 募集中。price 全額をエスクロー済み（募集者の現金から回収済み）。
- `fulfilled`: 供給成立。供給アイテムを `item_data` に保存、供給者へ `floor(price×0.95)` を bank 入金。募集者は未回収。
- `reclaimed`: 募集者が供給アイテムを回収済み。
- `cancelled`: 募集者が open をキャンセル → **エスクロー全額（price）を募集者へ返金**（手数料なし。fulfillment 前なので消却も無し）。
- `expired`: 期限切れ → **エスクロー全額を募集者の bank へ自動返金**（オフライン安全）。

### 通貨保存則（重要・実装時に必ず確認）
- 募集時: 募集者の現金から `price` 回収（経済から退場＝拘束）。
- 成立時: 供給者 bank += `floor(price×0.95)`。差額 `price − floor(price×0.95)` は**消却**（再生成しない）。
- キャンセル/期限切れ: 募集者へ `price` 返金（再生成）。差し引きゼロ。
- → fee は**成立時のみ**発生。キャンセル/期限切れは全額返金。

---

## 2. 既存資産（売りマーケット）— 買い注文はこれらを再利用・踏襲する

### 2-1. MarketManager（`market/MarketManager.java`）
- コンストラクタ（**現金払い対応済み**）:
  `MarketManager(Connection, PlayerDAO, MarketListingDAO, ConfigManager, CurrencyConverter, Logger)`
- 現金API（`CurrencyConverter`、`economy/CurrencyConverter.java`）:
  - `payWithCash(Player, double)→boolean`（所持チェック＋金塊削除を原子的に。不足で false）
  - `receiveCash(Player, double, boolean skipSpaceCheck)→boolean`（現金付与＝返金に使う）
  - `canAffordWithCash(Player, double)→boolean`
  - 金額→金塊枚数換算は内部で実施（`convertBalanceToNuggets` 等）。
- bank 入金: `PlayerDAO.getOrCreatePlayer(UUID)` → `player.addBankBalance(double)` → `playerDAO.updatePlayer(player)`。**UUID直指定でオフライン可**。
- トランザクション作法: `synchronized(connection)` ＋ `setAutoCommit(false)` ＋ 楽観ロック（条件付きUPDATEの影響行数==1判定）＋ commit/rollback、finally で `setAutoCommit(true)`。
- アイテム付与ヘルパー `giveItem(Player, String itemData)`: deserialize → `addItem`、入りきらない分は足元 `dropItem`（喪失防止）。**買い注文の回収でも同じ方式**。

### 2-2. シリアライズ（`market/MarketItemSerializer.java`）
- `serialize(ItemStack)→String`（Base64、失敗時 null） / `deserialize(String)→ItemStack`（不正で null）。供給アイテムの保存に流用。

### 2-3. DAO パターン（`dao/MarketListingDAO.java` を雛形に）
- コンストラクタで `Connection` 受領。`PreparedStatement`。`RETURN_GENERATED_KEYS` で id 取得。
- 楽観ロック更新の例: `markAsSold(id, buyerUuid, soldAtMillis)` / `updateStatusConditional(id, expected, new)`（影響行数==1で成功）。
- 一覧取得: `getActiveListings()` / `getListingsBySeller(uuid)` / 期限切れ一括 `expireActiveListings(nowMillis)→int`。
- expires_at は **epoch millis（INTEGER）**。期限判定は数値比較。

### 2-4. DB テーブル追加方法（`database/DatabaseManager.java`）
- `createTables()` 内 `tableCreationQueries`（または `tableSQLs`）配列末尾に CREATE 文を**インライン追加**。
- インデックスは `performMigrations()` 末尾に `CREATE INDEX IF NOT EXISTS ...` を追加。
- `CREATE TABLE IF NOT EXISTS` なのでマイグレーション不要。
- 売りの `market_listings` 追加箇所（`idx_market_status/seller/expires`）を grep して直後に倣う。

### 2-5. GUI パターン（`market/gui/` 一式）
- `MarketGUIListener`（`implements Listener`）: `InventoryClickEvent`/`InventoryCloseEvent` を集約、`ConcurrentHashMap<UUID, MarketGUISession>` でセッション管理、`session.getType()` で各GUIへ振り分け。**買い注文GUIもこのListenerに統合する**（型を追加）。
  - `registerSession(uuid, session)` / `setGUIs(...)`（循環依存をセッター注入で解決）/ `closeAll()`。
- `MarketGUISession`: `type`（enum）, `inventory`, `page`, `category`(`MarketCategory`), `slotListings`(Map<Integer,MarketListing>)。**買い注文用に `slotBuyOrders` か、汎用化が必要**（下記4-3参照）。
- `MarketGUIUtil`: `createButton(Material,name,lore)` / `formatPrice(double)` / `prettifyMaterial(String)`。共用。
- `MarketCategory`: Material 分類（ALL/MINING/FARMING/LOGGING/FISHING/CRAFTING/MATERIALS/OTHER）。`classify(Material)` / `matches(Material)`。買い注文一覧のカテゴリ分けにも流用可。
- `MarketBrowseGUI`: レイアウト参考（0-7 カテゴリボタン、9-44 アイテム36件/ページ、48 前/49 情報/50 次/53 閉じる）。

### 2-6. コマンド（`commands/MarketCommand.java`）
- `implements CommandExecutor, TabCompleter`。`/market`（一覧）/`sell <価格>`/`mylistings`/`cancel <id>`/`reclaim <id>`/`reload`/`help`。
- 権限: `tofunomics.market.use`(true) / `.sell`(true) / `.admin`(op)。
- メッセージ送信: `MarketMessages.format(configManager, MarketResult, item, price)`（未使用プレースホルダは無視される。`item/price/currency/min/max/limit/amount` を一括で渡す方式）。
- **買い注文は同じ `/market` にサブコマンド追加**（下記4-5）。

### 2-7. 配線（`TofuNomics.java`）
- フィールド群（`marketListingDAO/marketManager/marketBrowseGUI/myListingsGUI/marketGUIListener/marketExpirationTask`）の直後に買い注文用フィールドを追加。
- `initializeDAOs()`: `marketBuyOrderDAO = new MarketBuyOrderDAO(databaseManager.getConnection());` 追加。
- `initializeMarketSystem()`: BuyOrder 用 GUI 生成・`marketGUIListener.setGUIs(...)` 拡張・定期タスクに買い注文期限切れも追加。
- `registerEventListeners()`: Listener は marketGUIListener 1個で足りる（型で分岐するため）。
- `registerCommands()`: MarketCommand のコンストラクタ引数に BuyOrder GUI を追加。
- `onDisable()`: 既存の `marketExpirationTask.cancel()` / `marketGUIListener.closeAll()` に乗る。

### 2-8. config / messages
- `ConfigManager`（約4260行〜）: market getter は `config.getDouble/getInt/getBoolean("market.xxx", default)` の直書き。`getMarketMessage(key, Object...)` が `messages.market.key` を `%k%` ペア置換。
- `src/main/resources/config.yml`: トップレベル `market:` と `messages.market:` に**ピンポイント追記**（全体上書き厳禁）。jar 同梱 config が本番へ不足キーのみ自動補完される。

---

## 3. config 追記（`src/main/resources/config.yml`）

```yaml
market:
  # 既存（売り）に加えて
  max_buy_orders_per_player: 10    # 募集の同時保有上限
  # fee_rate / listing_duration_days / min_price / max_price / expire_check_interval は売りと共用
```

`messages.market` に追記（書式は既存に倣う。`&` カラーコード、`%player% %item% %amount% %price% %currency% %min% %max% %limit%`）:
```yaml
    # 募集（買い注文）
    requested: "&a%item% x%amount% の募集を &e%price% %currency%&a で登録しました（前払い）。"
    fulfilled: "&a募集に応じ %item% x%amount% を供給しました。&e%amount_proceeds% %currency%&a を受け取りました。"
    fulfilled_notify: "&aあなたの募集（%item% x%amount%）が成立しました。アイテムは /market myrequests から回収できます。"
    request_cancelled: "&a募集をキャンセルし、前払い分を返金しました。"
    request_reclaimed: "&a成立した募集からアイテムを回収しました。"
    request_expired_refund: "&e募集が期限切れになり、前払い分を返金しました。"
    request_limit: "&c募集数の上限（%limit%）に達しています。"
    no_matching_item: "&c供給するアイテム（%item% x%amount%）を所持していません。"
    request_not_available: "&cこの募集は既に成立済みか、存在しません。"
    request_not_owner: "&cこれはあなたの募集ではありません。"
```
> 注意: `fulfilled` の受取額プレースホルダ名は既存 `%amount%` と衝突しないよう `%amount_proceeds%` 等にするか、`MarketMessages` 側で整理する。要は「個数」と「受取金額」を別プレースホルダにすること。

---

## 4. 実装設計

### 4-1. DB スキーマ（`DatabaseManager` にインライン追加）
```sql
CREATE TABLE IF NOT EXISTS market_buy_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    requester_uuid TEXT NOT NULL,
    requester_name TEXT NOT NULL,
    material TEXT NOT NULL,            -- 募集する Material 名
    amount INTEGER NOT NULL,          -- 募集個数
    price REAL NOT NULL,              -- 前払いエスクロー総額
    status TEXT NOT NULL DEFAULT 'open',  -- open/fulfilled/reclaimed/cancelled/expired
    supplier_uuid TEXT,              -- 供給者（成立時）
    item_data TEXT,                  -- 供給されたアイテム（Base64、成立時に保存、回収用）
    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at INTEGER,              -- epoch millis、NULL=無期限
    fulfilled_at TIMESTAMP
);
-- performMigrations() 末尾
CREATE INDEX IF NOT EXISTS idx_buyorder_status    ON market_buy_orders(status);
CREATE INDEX IF NOT EXISTS idx_buyorder_requester ON market_buy_orders(requester_uuid, status);
CREATE INDEX IF NOT EXISTS idx_buyorder_expires   ON market_buy_orders(status, expires_at);
```

### 4-2. 新規クラス
| ファイル | 役割 |
|---|---|
| `models/MarketBuyOrder.java` | エンティティ + status定数（OPEN/FULFILLED/RECLAIMED/CANCELLED/EXPIRED）+ getter/setter。コンストラクタ `(requesterUuid, requesterName, material, amount, price, Long expiresAtMillis)`。 |
| `dao/MarketBuyOrderDAO.java` | insert/getById/getOpenOrders/getOrdersByRequester/countOpenByRequester/getExpiredOpen/expireOpenOrders/markAsFulfilled（楽観ロック・supplier/item_data/fulfilled_at設定）/updateStatusConditional |
| `market/gui/BuyOrderBrowseGUI.java` | 募集一覧（open、カテゴリ＋ページング、クリックで供給） |
| `market/gui/MyBuyOrdersGUI.java` | 自分の募集管理（open→キャンセル、fulfilled→アイテム回収） |
| `commands/MarketCommand.java`（既存に追記） | buy/requests/myrequests/cancelrequest/reclaimrequest サブコマンド |

`MarketManager` に買い注文メソッドを追加（新規 Manager を作らず集約。connection/playerDAO/currencyConverter/configManager を既に保持）。
**`MarketResult` に値を追加**: `REQUESTED("requested")`, `FULFILLED("fulfilled")`, `ORDER_CANCELLED("request_cancelled")`, `ORDER_RECLAIMED("request_reclaimed")`, `NO_MATCHING_ITEM("no_matching_item")`, `ORDER_NOT_AVAILABLE("request_not_available")`, `ORDER_NOT_OWNER("request_not_owner")`, `ORDER_LIMIT("request_limit")`。`isSuccess()` に REQUESTED/FULFILLED/ORDER_CANCELLED/ORDER_RECLAIMED を含める。

### 4-3. MarketGUISession の拡張
現状 `slotListings: Map<Integer, MarketListing>` と `Type{BROWSE, MY_LISTINGS}`。買い注文用に:
- `Type` に `BUY_BROWSE`, `MY_BUY_ORDERS` を追加。
- スロット→オブジェクトのマップを売り/買いで分けるか汎用化する。**推奨**: `slotBuyOrders: Map<Integer, MarketBuyOrder>` を追加し、GUI 種別に応じて使い分け（`getBuyOrderAtSlot(slot)` / `putSlotBuyOrder`）。`clearSlotListings()` で両方クリア。
- `MarketGUIListener.dispatchClick` の switch に2分岐追加。

### 4-4. MarketManager 追加メソッド（設計）

**募集登録（Bukkit ラッパー）** `createBuyOrder(Player requester, Material material, int amount, double price)`:
```
1. enabled / isValidPrice(price) / amount>0 を検証 → 不正なら INVALID_*
2. countOpenByRequester < max_buy_orders_per_player → 超過 ORDER_LIMIT
   （countは synchronized(connection) 内で）
3. ★現金を先に回収★ payWithCash(requester, price) 失敗 → INSUFFICIENT_FUNDS
4. INSERT (status=open, expires_at=now+days)  ※executeCreateBuyOrder 内で
5. DB 失敗時は receiveCash で返金して ERROR
6. 成功 REQUESTED
```
順序: **現金回収 → INSERT、INSERT 失敗時は返金**（売りの purchase と同じ安全順序）。

**供給（Bukkit ラッパー）** `fulfillBuyOrder(Player supplier, int orderId)`:
```
1. order = getById; open でなければ ORDER_NOT_AVAILABLE
2. 自己供給チェック（自分の募集には供給不可。allow_self_purchase を流用 or 常に不可）
3. 供給者が material を amount 個所持しているか（インベントリ集計）→ 無ければ NO_MATCHING_ITEM
4. ★手持ちから amount 個を除去し、ItemStack(material, amount) を serialize★（除去はTradingGUIの手動removeパターン参照）
5. executeFulfillTransaction(supplierUuid, orderId, itemData, now):
     synchronized(connection)+autocommit(false)
     - markAsFulfilled(orderId, supplierUuid, itemData, now)  ※status=open限定の楽観ロック。影響行数!=1で ALREADY/NOT_AVAILABLE
     - 供給者 bank += floor(price*0.95)
     - commit
6. DB 失敗/楽観ロック失敗 → 除去したアイテムを供給者へ返却（giveItem）して結果コード返す
7. 成功時: 募集者がオンラインなら fulfilled_notify 通知。supplier へ FULFILLED（受取額含む）
```
※エスクロー消却の会計: price は募集時に回収済み。ここで floor(price*0.95) だけ再生成。差額は自動的に消却される（明示的な burn 処理は不要）。

**キャンセル** `cancelBuyOrder(Player requester, int orderId)`:
```
- order取得、所有者チェック（違えば ORDER_NOT_OWNER）、open でなければ ORDER_NOT_AVAILABLE
- updateStatusConditional(open→cancelled) 楽観ロック
- 成功時 ★エスクロー返金★ receiveCash(requester, price, true)（オンライン前提＝キャンセルは本人操作）
- ORDER_CANCELLED
```

**回収** `reclaimBuyOrder(Player requester, int orderId)`:
```
- order取得、所有者チェック、fulfilled でなければ ORDER_NOT_AVAILABLE
- updateStatusConditional(fulfilled→reclaimed) 楽観ロック
- 成功時 giveItem(requester, order.getItemData())
- ORDER_RECLAIMED
```

**期限切れ（定期タスク）** `expireBuyOrders()→int`:
```
synchronized(connection):
  open かつ expires_at<=now の注文を取得 → 各々 status=expired にしつつ
  ★募集者 bank へ price を返金★（getOrCreatePlayer→addBankBalance→updatePlayer）
  （単純な一括UPDATEだと返金漏れるので、対象を取得してループで返金＋status更新する）
return 件数
```
> 注意: 売りの `expireActiveListings` は単純UPDATEで良いが、買い注文は**返金を伴う**ため取得→ループ処理にする。各注文は楽観ロック（open限定UPDATE）で二重返金を防止。
> `MarketExpirationTask` に `marketManager.expireBuyOrders()` の呼び出しを追加（既存タスクに相乗り or 新タスク。既存タスクに1行追加が簡単）。

### 4-5. コマンド追加（`/market` サブコマンド）
| コマンド | 動作 | 権限 |
|---|---|---|
| `/market buy <material> <個数> <価格>` | 募集を登録（前払い） | `tofunomics.market.sell`（出品系）または新設 `.market.buy` |
| `/market requests` | 募集一覧GUI（BuyOrderBrowseGUI）を開く | use |
| `/market myrequests` | 自分の募集GUI（MyBuyOrdersGUI） | use |
| `/market cancelrequest <id>` | open 募集をキャンセル＋返金 | use |
| `/market reclaimrequest <id>` | fulfilled 募集のアイテム回収 | use |
- `material` は `Material.matchMaterial(arg.toUpperCase())` でパース。null なら INVALID_ITEM 相当のメッセージ。
- TabCompleter: 第1引数に buy/requests/myrequests/cancelrequest/reclaimrequest を追加。`cancelrequest`/`reclaimrequest` は自分の該当status注文ID補完。`buy` の第2引数は Material 候補（任意）。
- plugin.yml の usage 文字列を更新（権限を増やすなら permissions も追記）。

### 4-6. GUI 設計
- `BuyOrderBrowseGUI`: `getOpenOrders()` を描画。各注文アイテム= `new ItemStack(Material, amount)`（募集対象。NBT無しなので matchMaterial で生成）に lore（募集者・希望個数・支払価格・「クリックで供給」）。カテゴリ＋ページングは MarketBrowseGUI を踏襲。クリック→ `fulfillBuyOrder`。
- `MyBuyOrdersGUI`: `getOrdersByRequester(uuid)`。open→クリックでキャンセル、fulfilled→クリックで回収、それ以外は表示のみ。MyListingsGUI を踏襲。
- メッセージは結果コード→`getMarketMessage(result.getMessageKey(), ...)`。プレースホルダ（item/amount/price/currency/受取額/limit）を呼び出し側で埋める。

---

## 5. セッション分割（推奨・各回コンパイル可能）

- **B1: DB層** — `models/MarketBuyOrder` → `dao/MarketBuyOrderDAO` → `DatabaseManager` にDDL＋index。`MarketBuyOrderDAOTest`（CRUD・open取得・countOpen・expired取得・markAsFulfilled楽観ロック）green。
- **B2: ロジック層** — `MarketResult` 値追加 → `MarketManager` に createBuyOrder/fulfill/cancel/reclaim/expire のコア＋ラッパー → `config.yml` の market追記。`MarketManagerTest` に買い注文ケース追加（手数料floor・二重供給の楽観ロック・期限切れ返金・所有者チェック）。
- **B3: GUI＋タスク** — `MarketGUISession`拡張 → `MarketGUIListener`分岐追加 → `BuyOrderBrowseGUI`/`MyBuyOrdersGUI` → `MarketExpirationTask` に expireBuyOrders 追加。
- **B4: コマンド＋配線** — `MarketCommand` サブコマンド追加 → `TofuNomics.java` 配線 → `plugin.yml` usage/権限。`messages.market` 追記。
- **B5: ビルド＋デプロイ** — `mvn clean package`（テストgreen）→ `tofu-mc-deploy` → リロード案内 → E2E。

各単位で `mvn -q compile`、テストは `mvn test -Dtest='Market*'`。

---

## 6. E2E 検証
1. A が `/market buy DIAMOND 10 100` → 手持ち現金から100消費、募集登録。`/market requests` に表示。
2. A ログアウト → B が `/market requests` から募集をクリック → B の手持ちダイヤ10個が消え、B の bank に `floor(100×0.95)=95` 加算（5消却）。
3. A 再ログイン → `/market myrequests` → 成立した募集をクリックでダイヤ10個回収。
4. キャンセル: A が open 募集を `/market cancelrequest <id>` → 前払い100が現金で返金。
5. 期限切れ: `listing_duration_days` を短く → expired 化 → A の bank に100返金。
6. エッジ: 供給アイテム不足（NO_MATCHING_ITEM）、現金不足で募集不可、募集上限、自己供給拒否、二重供給防止。

---

## 7. プロジェクト規約（厳守）
- 応答・コメント・コミットメッセージは**日本語**。コミットプレフィックス `feat/fix/docs/refactor/test/config`。
- コミット末尾に `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。
- ビルド前 `mvn clean`。カバレッジ70%維持。作業は `feature/player-market`、`main` 直接コミット禁止。
- PR作成時・config変更時は `~/bin/worklog TofuNomics "..."`（フルパス）。
- **config.yml 全体上書き厳禁**（NPC座標等のサーバー固有設定を保護）。サーバー reload はユーザー手動依頼。
- デプロイは `tofu-mc-deploy`（deployスキル）。リロードは plugman unload/load をユーザーが手動実行。

---

## 8. 注意点・落とし穴
- **通貨保存則を必ずテスト**: 募集→キャンセルで純増減ゼロ、募集→供給で floor分だけ供給者に渡り差額消却。
- **エスクロー返金の二重実行防止**: キャンセル/期限切れは必ず楽観ロック（open限定UPDATEの影響行数==1）を経てから返金する。先に返金して後でUPDATE失敗、の順にしない。
- **供給時のアイテム除去とDBの順序**: 「手持ち除去→serialize→DBトランザクション、失敗時はアイテム返却」。売りの現金払い purchase と同じ安全順序。
- 期限切れ買い注文は**単純一括UPDATE不可**（返金が必要）。取得→ループ→注文ごとに楽観ロックUPDATE＋返金。
- `%amount%`（個数）と受取金額のプレースホルダ名を分ける（衝突回避）。
- 共有 Connection への非同期書き込み（PlayerJoinHandler）と競合するため、DB操作は全て `synchronized(connection)`。
- MockBukkit は依存に無い。ItemStack 往復・インベントリ操作はユニット不可 → コア（DB＋bank）のみ単体テスト、現金/インベントリは E2E 委譲（売りと同方針）。

> 新セッションは本書 → `docs/market-implementation-handoff.md`（既存APIの正本）の順に読み、B1 から着手すること。
