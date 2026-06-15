# プレイヤー間マーケット機能 実装 引き継ぎ書

> このドキュメントは、別セッションでマーケット機能の実装を引き継ぐための完全な引き継ぎ書です。
> 作成日: 2026-06-11 / 作成: Claude Opus 4.8 セッション
> 関連: `docs/market-feature-findings.md`（調査メモ）, `docs/config-reference.md`（config仕様の market セクション）

---

## ★ 進捗（2026-06-11 追記）: S1・S2 完了 → 次は S3

> **このセクションが S1/S2 については「正本」**。下の「## 4. 実装設計」は当初プランで、実装と一部食い違う（差分は本セクションに明記）。S3 以降の設計指針としては引き続き「## 4」「## 5」を参照してよい。

### 完了状況
- ブランチ `feature/player-market`（`main` から作成済み・このまま続行）。
- **S1（DB層）コミット `7cc1093`** / **S2（ロジック層）コミット `99ac0c8`**。
- マーケット関連テスト **計31件 green**（DAO 10・Serializer 5・Manager 16）。全体コンパイル・pre-commit の YAML 検証も通過。
- 未コミットの `.claude/settings.json`・`docs/` はコミットに含めていない（従来方針どおり）。

### 当初プランとの差分（重要・実装に合わせること）
1. **`expires_at` は epoch millis（INTEGER）**。当初案の TIMESTAMP+CURRENT_TIMESTAMP 比較は、SQLite の文字列比較フォーマット依存を避けるため不採用。モデルは `Long expiresAtMillis`（NULL=無期限）。期限判定は数値比較。
2. **DB テーブル追加はインライン方式**。`DatabaseManager.createTables()` 内の `tableCreationQueries` 配列末尾に `market_listings` の CREATE 文を追加済み。インデックス3本（`idx_market_status` / `idx_market_seller` / `idx_market_expires`）は `performMigrations()` 末尾に追加済み。当初案の `createMarketListingsTableSQL()` メソッド分割は実コードに存在せず不採用。
3. **テストは SQLite インメモリ（`jdbc:sqlite::memory:`）＋ JUnit4（`org.junit`）＋ Mockito**。新規テストは本番 DDL を流用するため SQLite を使用（`PlayerDAOTest` は H2 だが踏襲しない）。**MockBukkit は依存に無い** → ItemStack の往復シリアライズはユニット不可、E2E 委譲。
4. `ConfigManager` は **約4260行**、getter は `config.getDouble/getInt/getBoolean("path", default)` の直書き。`getMessage(key, Object... replacements)` が `%key%` ペア置換。
5. config.yml は約3485行、`messages:` は約2051行。market 設定は**トップレベル `market:`** と **`messages.market:`**（15メッセージ）を追加済み。

### S1/S2 で実際に作られたもの（API 一覧・S3 はこれを使う）
- `models/MarketListing`: フィールド `id, sellerUuid(UUID), sellerName, itemData(Base64), displayName, material, amount, price(double), status, buyerUuid(UUID), listedAt(Timestamp), expiresAtMillis(Long), soldAt(Timestamp)`。status 定数 `STATUS_ACTIVE/SOLD/EXPIRED/RECLAIMED`。コンストラクタ `(sellerUuid, sellerName, itemData, displayName, material, amount, price, Long expiresAtMillis)`。
- `market/MarketItemSerializer`: static `serialize(ItemStack)→String`（失敗時 null） / `deserialize(String)→ItemStack`（null/空/不正で null）。
- `dao/MarketListingDAO`: `insertListing(listing)→int` / `getById(int)` / `getActiveListings()` / `getListingsBySeller(UUID)` / `countActiveBySeller(UUID)` / `getExpiredActiveListings(long nowMillis)` / `expireActiveListings(long nowMillis)→int` / `markAsSold(id, buyerUuid, long soldAtMillis)→boolean`（楽観ロック） / `updateStatusConditional(id, expectedStatus, newStatus)→boolean`（楽観ロック）。
- `market/MarketResult`（enum）: 各値が `messages.market.*` キーに対応（`getMessageKey()`）。`isSuccess()` あり。値: LISTED/PURCHASED/CANCELLED/RECLAIMED/INVALID_ITEM/INVALID_PRICE/CURRENCY_NOT_ALLOWED/LISTING_LIMIT/NOT_AVAILABLE/ALREADY_SOLD/INSUFFICIENT_FUNDS/INVENTORY_FULL/NOT_OWNER/MARKET_DISABLED/ERROR。
- `market/PurchaseOutcome`: `getResult()/isSuccess()/getItemData()/getSellerUuid()/getSellerProceeds()`。`failure(result)` / `success(itemData, sellerUuid, proceeds)`。
- `market/MarketManager`: コンストラクタ `(Connection, PlayerDAO, MarketListingDAO, ConfigManager, Logger)`。
  - **Bukkit ラッパー（コマンド/GUI から呼ぶ）**: `createListing(Player, double price)→MarketResult` / `purchaseListing(Player, int listingId)→MarketResult` / `cancelListing(Player, int)→MarketResult` / `reclaimListing(Player, int)→MarketResult`。
  - **テスト可能コア**: `isValidPrice(double)` / `calculateSellerProceeds(double)→long` / `executeCreateListing(sellerUuid, sellerName, itemData, displayName, material, amount, price, nowMillis)→MarketResult` / `executePurchaseTransaction(buyerUuid, listingId, boolean hasSpace, nowMillis)→PurchaseOutcome` / `executeReclaim(sellerUuid, listingId, expectedStatus, boolean hasSpace)→MarketResult`。
  - 購入・回収は `synchronized(connection)`＋手動トランザクション。アイテム付与・出品者通知（`sold_notify`、プレースホルダ `%amount%`）は commit 後。アイテムは `addItem`、入りきらない分は足元に `dropItem`（喪失防止）。
- `ConfigManager` getter（9個）: `isMarketEnabled` / `getMarketFeeRate` / `getMarketMaxListingsPerPlayer` / `getMarketListingDurationDays` / `getMarketMinPrice` / `getMarketMaxPrice` / `isMarketAllowSelfPurchase` / `getMarketExpireCheckInterval` / `getMarketMessage(key, Object... replacements)`。

### S3 でやること（GUI＋定期タスク）
- `market/gui/MarketBrowseGUI`: `listingDAO.getActiveListings()` を描画（item_data を deserialize して表示、価格・出品者名）。購入クリック → `manager.purchaseListing(player, id)`。ページング。
- `market/gui/MyListingsGUI`: `listingDAO.getListingsBySeller(uuid)`。active クリック → `cancelListing`、expired クリック → `reclaimListing`。
- `market/gui/MarketGUIListener`: `InventoryClickEvent`/`InventoryCloseEvent`、`ConcurrentHashMap` でセッション（開いている GUI と listingId/ページ）管理。
- `market/MarketExpirationTask`: `BukkitRunnable.runTaskTimer(plugin, 0L, intervalTicks)`（`getMarketExpireCheckInterval()` 秒 ×20）。中で期限切れ一括処理。**`MarketManager` に `synchronized(connection)` で `listingDAO.expireActiveListings(System.currentTimeMillis())` を呼ぶ薄いメソッド（例 `expireListings()→int`）を追加して、それをタスクから呼ぶ**（現状 Manager には未実装なので S3 で追加）。
- 雛形にする既存 GUI: `npc/gui/TradingGUI.java`, `npc/gui/BankGUI.java`（実在を `ls src/main/java/org/tofu/tofunomics/npc/gui/` で確認してから読む）。
- メッセージ送信は GUI/コマンド側で `configManager.getMarketMessage(result.getMessageKey(), プレースホルダ...)`。プレースホルダ実値（`%item% %price% %currency% %amount% %min% %max% %limit%`）は呼び出し側で埋める。
- **配線（`TofuNomics.java` の Manager/DAO 生成・コマンド登録・リスナー登録・タスク起動、`plugin.yml`、`MarketCommand`、`MarketMessages`）は S4**。S3 は GUI とタスクのクラス実装まで（コンパイルが通る単位で）。

### S3 開始時の最初の一歩
1. `git log --oneline -3` で `99ac0c8`(S2)・`7cc1093`(S1) を確認。ブランチ `feature/player-market`。
2. `ls src/main/java/org/tofu/tofunomics/npc/gui/` → `TradingGUI`/`BankGUI` を読み、GUI の枠（Inventory 生成・Holder・クリックハンドリング）の流儀を把握。
3. `MarketManager` に `expireListings()` を追加 → `MarketExpirationTask` → GUI 群の順で実装。各単位で `mvn -q compile` を通す。

---

## 0. まず最初に確認すること

- **現在のブランチ**: `feature/player-market`（`main` から分岐済み・作成済み）。`develop` ブランチは存在しない（このプロジェクトは `main` ベース運用、feature→main へPRマージ）。
- **作業はこのブランチで続行**してよい。`main` への直接コミットは Stop hook（`~/.claude/scripts/no-direct-commit.sh`）が警告する＝正常動作。
- **過去の market 実装（24ファイル）は git gc で完全消失**（stash・dangling commit にも残っていない）。**ゼロから新規実装**する。仕様だけが docs に残っている。
- 未コミットの変更として `.claude/settings.json` の変更がある（ユーザーが設定ミスを修正したもの。実装とは無関係。コミットに含めない方が無難）。

---

## 1. ユーザーの要望（原文の意図）

「常にプレイヤーがいる状態じゃないとプレイヤー同士の取引ができないので、**プレイヤーがいなくても取引ができるトレード機能**を実装したい。実装が膨大になるので計画を立て、必要ならタスクごとにセッションを分割してよい」

→ **公開マーケット型**（出品者がオフラインでも他プレイヤーが購入できる）として実装することでユーザーと合意済み。

---

## 2. 確定仕様（ユーザー合意済み）

- 形態: **公開マーケット型**。出品者オフラインでも購入可。代金は出品者の `bank_balance` へ入金。
- 出品: コマンド `/market sell <価格>`。**手に持っているアイテム（メインハンドのスタック全体）**を出品。価格は total（スタック全体の値段）。
- 一覧・購入: **GUI**。
- 価格は出品者が自由設定。**手数料 5%（消却＝どこにも入金せず通貨総量から消える）**。出品者受取 = `Math.floor(price × (1 − fee_rate))`。
- 期限切れ（既定7日）は**アイテム喪失ゼロの回収方式**。expired になってもアイテムデータはDBに残り、出品者が手動回収するまで保持。

---

## 3. 技術前提（実コードで確認済み・重要）

### 3-1. ItemStack シリアライズ方式 ★確認済み
`PlayerInventoryManager.java`（214-249行）の方式を流用する。**YAML ではなく `BukkitObjectOutputStream/InputStream` + Base64**：

```java
// シリアライズ（単一 ItemStack 用に流用）
ByteArrayOutputStream out = new ByteArrayOutputStream();
BukkitObjectOutputStream bukkitOut = new BukkitObjectOutputStream(out);
bukkitOut.writeObject(item);          // 単一アイテム（PlayerInventoryManagerは配列をwriteInt+ループ）
bukkitOut.close();
String base64 = Base64.getEncoder().encodeToString(out.toByteArray());

// デシリアライズ
ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
BukkitObjectInputStream bukkitIn = new BukkitObjectInputStream(in);
ItemStack item = (ItemStack) bukkitIn.readObject();
bukkitIn.close();
```
- import: `org.bukkit.util.io.BukkitObjectOutputStream` / `org.bukkit.util.io.BukkitObjectInputStream`
- 例外: `IOException` / `ClassNotFoundException` を catch
- → これを `market/MarketItemSerializer.java` として `serialize(ItemStack)` / `deserialize(String)` の static メソッドに切り出す。

### 3-2. データベース層 ★確認済み
- アクティブDB層は `database/DatabaseManager.java`（**単一の共有 Connection**、`tofunomics.db` ハードコード）。`HikariDatabaseManager` は未使用。
- `getConnection()` は50行付近。`createTables()` は75行：
  ```java
  private void createTables() throws SQLException {
      String[] tableSQLs = {
          createPlayersTableSQL(),
          ...
          createPlayerInventoriesTableSQL()   // ← 配列の最後
      };
      for (String sql : tableSQLs) {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
              statement.execute();
          }
      }
      performMigrations();
  }
  ```
- **テーブル追加手順**: ①`createMarketListingsTableSQL()` という private メソッドを新設しSQL文字列を返す ②`tableSQLs` 配列の末尾に `createMarketListingsTableSQL()` を追加。
- **インデックス作成箇所は未確認**（grep が拒否された）。`createPlayerInventoriesTableSQL()` の実装と既存インデックス（`idx_*`）の作り方を最初に確認すること（`grep -n "idx_\|CREATE INDEX" src/main/java/org/tofu/tofunomics/database/DatabaseManager.java`）。インデックスを CREATE INDEX 文として tableSQLs に含めるか、別メソッドかを既存パターンに合わせる。
- 新規テーブルなので**マイグレーション不要**（`CREATE TABLE IF NOT EXISTS`）。

### 3-3. 残高操作 ★確認済み
- `bank_balance` は **double 型（REAL）**。価格入力は整数で受け付けるが、内部計算は double。手数料の端数は `Math.floor`。
- `PlayerDAO` の bank_balance 操作メソッド（`addBankBalance` / `removeBankBalance` 等）を使用。**UUID 直指定でオフラインプレイヤーにも適用可能**。
- `PlayerDAO.transferBalance`（220行付近）が `setAutoCommit(false)` 手動トランザクションの参考。**実装前にこのメソッドを必ず読むこと**（コミット/ロールバックの作法、メソッドシグネチャ確認のため）。

### 3-4. DAOパターン ★要確認
- DAOはコンストラクタで `Connection` を受け取る。`PreparedStatement` でSQLインジェクション対策。`ResultSet` → モデルのマッピング。
- 既存DAO（`PlayerDAO.java`, `dao/HousingRentalDAO.java`）を雛形として読むこと。

### 3-5. Risk B（重要な並行性の注意） ★確認済み
- `PlayerJoinHandler` がプレイヤー入場時に `runTaskAsynchronously` で**共有 Connection に非同期書き込み**する（insertPlayer/updateLastLogin/updatePlayerName）。
- マーケットの購入処理は `setAutoCommit(false)` の手動トランザクションを使うため、この非同期書き込みと**同一 Connection で競合しうる**。
- **対策**: `MarketManager.purchaseListing()`（および出品/回収のトランザクション）を `synchronized(connection)` ブロックで囲む。

### 3-6. config 自動補完の仕組み ★確認済み
- `ConfigManager.updateConfigWithDefaults()`（onEnable で実行）が **jar 同梱 `src/main/resources/config.yml` を読み、本番 config に不足キーを追加**（追加のみ・削除しない）。
- → `src/main/resources/config.yml` に `market:` と `messages.market:` を入れてビルド・デプロイすれば、本番へ自動補完される。**本番 config の手動編集・全体上書きは厳禁**（NPC座標等のサーバー固有設定が消える）。
- jar 名: `TofuNomics-1.0-SNAPSHOT.jar`。デプロイ先: `/opt/minecraft/servers/lobby-1.21/plugins/TofuNomics-1.0-SNAPSHOT.jar`。reload はユーザー手動。

---

## 4. 実装設計

### 4-1. DBスキーマ（`DatabaseManager` に追加）
```sql
CREATE TABLE IF NOT EXISTS market_listings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_uuid TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    item_data TEXT NOT NULL,                      -- Base64(BukkitObjectStream)
    display_name TEXT,                            -- GUI表示用
    material TEXT NOT NULL,                        -- フィルタ・表示用 Material名
    amount INTEGER NOT NULL,
    price REAL NOT NULL,                           -- 出品価格(total)
    status TEXT NOT NULL DEFAULT 'active',         -- active/sold/expired/reclaimed
    buyer_uuid TEXT,
    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,                          -- NULL = 無期限(listing_duration_days=0)
    sold_at TIMESTAMP,
    FOREIGN KEY (seller_uuid) REFERENCES players(uuid)
);
-- インデックス（既存パターンに合わせて配置）
CREATE INDEX IF NOT EXISTS idx_market_status  ON market_listings(status);
CREATE INDEX IF NOT EXISTS idx_market_seller  ON market_listings(seller_uuid, status);
CREATE INDEX IF NOT EXISTS idx_market_expires ON market_listings(status, expires_at);
```
期限は**リアルタイム（CURRENT_TIMESTAMP + 日数）基準**（housing の tick 基準ではない。オフライン中も経過する現実時間が公開マーケットに適切）。

### 4-2. 新規クラス（`market/` パッケージ新設）
| ファイルパス | 役割 |
|---|---|
| `models/MarketListing.java` | エンティティ + status定数(active/sold/expired/reclaimed) + getter/setter |
| `dao/MarketListingDAO.java` | CRUD、active一覧取得、seller別取得、expired検出、status条件付きUPDATE、countActiveBySeller |
| `market/MarketItemSerializer.java` | ItemStack ⇔ Base64（3-1の方式、static） |
| `market/MarketManager.java` | 出品/購入/キャンセル/回収、手数料計算、トランザクション制御、バリデーション |
| `market/MarketExpirationTask.java` | 定期タスク（`BukkitRunnable.runTaskTimer`、expired化） |
| `market/MarketMessages.java` | `messages.market.*` 読み込み + プレースホルダ置換ヘルパー |
| `market/gui/MarketBrowseGUI.java` | マーケット一覧GUI（全active、ページング、購入クリック） |
| `market/gui/MyListingsGUI.java` | 自分の出品管理GUI（active/expired、キャンセル・回収） |
| `market/gui/MarketGUIListener.java` | InventoryClickEvent/InventoryCloseEvent、ConcurrentHashMapセッション管理 |
| `commands/MarketCommand.java` | `/market`（CommandExecutor + TabCompleter） |

GUI実装は既存の `npc/gui/TradingGUI.java`, `npc/gui/BankGUI.java` を雛形にすること。

### 4-3. 既存クラスへの変更
- `database/DatabaseManager.java` — `createMarketListingsTableSQL()` 追加 + `tableSQLs` 配列に追加 + インデックス
- `config/ConfigManager.java` — market getter群（`isMarketEnabled` / `getMarketFeeRate` / `getMarketMaxListingsPerPlayer` / `getMarketListingDurationDays` / `getMarketMinPrice` / `getMarketMaxPrice` / `isMarketAllowSelfPurchase` / `getMarketExpireCheckInterval` / `getMarketMessage(key)`）。既存 getDouble/getInt/getBoolean ラッパーを利用。
- `TofuNomics.java` — `initializeManagers()` で Manager/DAO 生成、`registerCommands()` で `getCommand("market").setExecutor(...)`、`registerEventListeners()` で `MarketGUIListener` 登録、`onEnable()` 末尾で `MarketExpirationTask` を `runTaskTimer(this, 0L, intervalTicks)` 起動（`expire_check_interval` 秒 ×20 = ticks）、`onDisable()` でタスクキャンセル。
- `src/main/resources/plugin.yml` — `market` コマンド + パーミッション（`tofunomics.market.use` default:true / `.market.sell` default:true / `.market.admin` default:op / `.market.*`）。
- `src/main/resources/config.yml` — `market:` セクション（下記）+ `messages.market:`（14メッセージ）。

### 4-4. config 既定値（`src/main/resources/config.yml` に追記）
```yaml
market:
  enabled: true
  fee_rate: 0.05
  max_listings_per_player: 10
  listing_duration_days: 7        # 0で無期限
  min_price: 1
  max_price: 1000000
  allow_self_purchase: false
  expire_check_interval: 3600     # 秒
```
`messages.market`（14メッセージのキー）: `listed` / `purchased` / `sold_notify` / `cancelled` / `reclaimed` / `invalid_item` / `invalid_price` / `currency_not_allowed` / `listing_limit` / `not_available` / `already_sold` / `insufficient_funds` / `inventory_full` / `not_owner` / `error`
（既存 messages の書式・プレースホルダ %player% %amount% %currency% %item% %price% 等に倣う）

### 4-5. 購入処理のトランザクション設計（`MarketManager.purchaseListing(Player buyer, int listingId)`）
```
synchronized (connection) {              // Risk B 対策
  try {
    connection.setAutoCommit(false);
    1. SELECT ... WHERE id=? AND status='active'（再取得・再確認）→ 無ければ already_sold
    2. 自己購入チェック（allow_self_purchase=false かつ seller==buyer なら拒否）
    3. 購入者 bank_balance >= price 確認 → 不足なら insufficient_funds
    4. 購入者インベントリ空き確認（firstEmpty() != -1）→ 満杯なら inventory_full（購入させない）
    5. UPDATE market_listings SET status='sold', buyer_uuid=?, sold_at=? WHERE id=? AND status='active'
       → 影響行数 ≠ 1 なら rollback（already_sold）※二重購入防止の楽観ロック（最重要）
    6. 購入者 removeBankBalance(price)
    7. 出品者 addBankBalance(Math.floor(price * (1 - fee_rate)))  // UUID直指定・オフライン可
    8. connection.commit()
    9. commit成功後にアイテム付与（deserialize → addItem、失敗時は world.dropItem で喪失防止）
    10. 出品者がオンラインなら sold_notify 送信
  } catch (SQLException e) {
    connection.rollback(); return ERROR;
  } finally {
    connection.setAutoCommit(true);
  }
}
```

### 4-6. 出品処理（`MarketManager.createListing(Player seller, double price)`）
順序が重要（喪失防止）:
```
1. メインハンドのアイテム取得 → AIR/null なら invalid_item
2. 価格バリデーション（min_price <= price <= max_price、整数）→ 不正なら invalid_price
3. 出品数上限（countActiveBySeller < max_listings_per_player）→ 超過なら listing_limit
4. serialize(item) → Base64
5. expires_at 計算（duration==0 ? null : now + days）
6. INSERT market_listings (status='active')
7. ★DB保存成功を確認してから★ seller の手持ちアイテムを除去（setItemInMainHand(null)）
8. listed メッセージ
```
※「DB保存成功 → 手持ち除去」の順を厳守（逆だと保存失敗時にアイテム消失）。

### 4-7. 期限切れ・回収
- `MarketExpirationTask`（`expire_check_interval` 秒ごと）:
  `UPDATE market_listings SET status='expired' WHERE status='active' AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP`
- 出品者は `/market mylistings`（MyListingsGUI）から expired/active をクリックで回収/キャンセル:
  `reclaimListing(seller, id)`: 自分の出品取得 → インベントリ空き確認（満杯なら inventory_full・出品維持）→ デシリアライズして付与 → `UPDATE status='reclaimed'` → reclaimed/cancelled メッセージ。

### 4-8. コマンド体系
| コマンド | 動作 | 権限 |
|---|---|---|
| `/market` | MarketBrowseGUI を開く | `tofunomics.market.use`(true) |
| `/market sell <価格>` | 手持ちを出品 | `tofunomics.market.sell`(true) |
| `/market mylistings` | MyListingsGUI を開く | use |
| `/market cancel <id>` | active出品をキャンセル回収 | use |
| `/market reclaim <id>` | expired出品を回収 | use |
| `/market reload` | config再読込 | `tofunomics.market.admin`(op) |
| `/market help` | ヘルプ | use |
TabCompleter: 第1引数にサブコマンド、`cancel`/`reclaim` は自分の listing ID 候補。

---

## 5. セッション分割（実装順・各回コンパイル/テスト可能）

- **S0: フック修正 → 不要と判明**（`no-direct-commit.sh` は正常な Stop hook だった。実装しない）。
- **S1: DB層**（このセッションで途中まで調査済み）
  - `models/MarketListing.java` → `market/MarketItemSerializer.java` → `dao/MarketListingDAO.java` → `DatabaseManager` にDDL追加
  - 完了条件: `MarketItemSerializerTest` / `MarketListingDAOTest` が green
- **S2: ロジック層**: `MarketManager` / `ConfigManager` getter / `config.yml` の `market:` → `MarketManagerTest`
- **S3: GUI + 定期タスク**: `MarketBrowseGUI` / `MyListingsGUI` / `MarketGUIListener` / `MarketExpirationTask`
- **S4: コマンド + 統合**: `MarketCommand` / `MarketMessages` / `plugin.yml` / `TofuNomics.java` 配線 / `messages.market`
- **S5: テスト + ビルド**: ユニットテスト拡充（カバレッジ70%）→ `mvn clean package` → デプロイ案内

タスクは TaskCreate で S1〜S5 登録済み（新セッションでは TaskList で確認）。

---

## 6. テスト方針（カバレッジ70%目標）
- `src/test/java/org/tofu/tofunomics/` の既存テストパターン（JUnit + Mockito、インメモリSQLite `jdbc:sqlite::memory:`）を最初に確認して流用。
- `MarketItemSerializerTest`: 往復（通常/エンチャント/カスタム名/null）。
- `MarketListingDAOTest`: CRUD、expired検出クエリ、countActive。
- `MarketManagerTest`: **二重購入防止（条件付きUPDATEの影響行数）**、残高不足、出品上限、自己購入拒否、手数料floor、インベントリ満杯。
- `MarketExpirationTaskTest`: expires_at 境界値。

---

## 7. 検証（End-to-End）
1. `mvn clean package` でコンパイル（各セッション末）。
2. `mvn test` green（特に二重購入防止・手数料端数）。
3. サーバーで: A が `/market sell 100` → 手持ちから消え出品 → A ログアウト → B が `/market` で購入 → B にアイテム、A の `bank_balance` に `floor(100×0.95)=95` 加算（手数料5消却）。
4. 期限切れ: `listing_duration_days` を短く → expired 化 → A が回収でアイテムが戻る（喪失ゼロ）。
5. エッジ: 残高不足・インベントリ満杯・自己購入拒否・出品上限の各メッセージ表示。

---

## 8. プロジェクト規約（厳守）
- 応答・コメント・コミットメッセージは**日本語**。
- コミットプレフィックス: `feat` / `fix` / `docs` / `refactor` / `test` / `config`。
- コミット末尾に `Co-Authored-By: Claude <モデル名> <noreply@anthropic.com>`。
- ビルド前に `mvn clean`。カバレッジ70%維持。
- **作業は `feature/player-market` で。`main` 直接コミット禁止**。PR を通じてマージ。
- PR作成時・config変更時は worklog 記録: `~/bin/worklog TofuNomics "..."`（フルパスで実行）。
- **config.yml 全体上書き厳禁**（サーバー固有のNPC座標・銀行/取引所位置を保護）。サーバー reload はユーザー手動依頼。
- サーバー再起動が必要な場合は「別ターミナルでサーバーを停止してください」とユーザーに依頼。

---

## 9. 既知の環境メモ
- シェルは `fish`。Bash ツールで bash 専用構文（`for ...; do ... done` 等）はパースエラーになる。`bash -c '...'` で囲むか fish 互換にする。
- `~/.claude/scripts/no-direct-commit.sh` は **正常な Stop hook**（main/master で未コミット変更時に exit 2 で feature ブランチ作成を促す）。壊れていない。
- このセッション（opus）ではツール出力の truncate/混線が散発した。長い出力や複数コマンドの `;` 連結を避け、**単一コマンド・小さい範囲**でツールを呼ぶと安定する。

---

## 10. 引き継ぎ時点の状態（チェックリスト）
- [x] feature ブランチ `feature/player-market` 作成・切替済み
- [x] プランファイル `/Users/kuriharaataru/.claude/plans/validated-booping-deer.md` 作成済み
- [x] タスク S1〜S5 登録済み
- [x] 既存シリアライズ方式の特定（BukkitObjectStream + Base64）
- [x] `DatabaseManager.createTables()` の追加方法の特定
- [ ] **インデックス作成箇所の確認**（最初にやる）
- [ ] `PlayerDAO.transferBalance` / 既存DAO・既存GUI の精読
- [ ] S1 のコード実装（未着手）
- [ ] 以降 S2〜S5

> 新セッションは本書 → `docs/market-feature-findings.md` → `docs/config-reference.md` の順に読み、S1 のコード確認（インデックス箇所・既存DAO・既存GUI）から再開すること。
