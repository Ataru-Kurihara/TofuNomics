# マーケット機能 実装前の調査メモ（事実のみ）

> このファイルは「問題点・既知の事実」の記録です。**設計・実装計画は含みません**（別セッションでクリーンに作成する）。
> 前回セッションでツール出力の破損・誤りが多発したため、ここに書くのは**実コマンド/ファイルで検証した事実のみ**。次セッションでも各ステップを「ファイル出力→読み取り→exit code」で必ず検証すること。

## デプロイに関する事実

- **jar 名は `TofuNomics-1.0-SNAPSHOT.jar`**（pom.xml の `<version>` が `1.0-SNAPSHOT`）。`mvn clean package` はこの名前で shaded jar（依存同梱・約11MB）を生成する。
- 本番サーバーがロードするのは **`/opt/minecraft/servers/lobby-1.21/plugins/TofuNomics-1.0-SNAPSHOT.jar`**。この**正確な名前を上書き**しないとデプロイは反映されない（別名 `TofuNomics-1.0.jar` 等を置いても無効）。
- ネットワーク: local `192.168.100.200:22222`（`tofu-mc-deploy` が auto 判定）。
- デプロイ後の反映は `/tofunomics reload`（config のみ）/ `/plugman reload TofuNomics`（jar 含む）/ サーバー再起動（最も確実）。

## config.yml 反映の仕組み（重要）

- 配布 jar の同梱 config はサーバーの既存 config を**直接上書きしない**。
- ただし `ConfigManager.updateConfigWithDefaults()`（`initializeAutoConfig()` → onEnable で実行）が **jar 同梱 config.yml を読み、サーバー config に「不足キーを追加」する**（追加のみ・削除しない）。
- 結論: **`src/main/resources/config.yml` に `market` と `messages.market` を入れて jar をビルド・デプロイすれば、onEnable 時にサーバー config へ自動補完される**。サーバー config への手動追記は不要（前回手動で追記したものが「消えた」のは、そもそも正しい jar が反映されておらず、同梱 config 経由の自動補完も働かなかったため）。

## アクティブな DB 層

- 実際に使われるのは `database/DatabaseManager.java`（**単一の共有 Connection**、ファイル名は `tofunomics.db` を**ハードコード** at `TofuNomics.java:265`）。
- `HikariDatabaseManager` は**未使用**。よって `config.database.filename`（= `tofunomics_world.db`）や `database.connection_pool.*` は**実質無視される**。

## Risk B（共有 Connection × 手動トランザクション）

- `PlayerJoinHandler` が tofuNomics 入場時に `runTaskAsynchronously` → `handlePlayerData()` で**共有 Connection に非同期書き込み**する（insertPlayer / updateLastLogin / updatePlayerName）。
- マーケットの購入処理で `setAutoCommit(false)` の手動トランザクションを使うなら、この非同期書き込みと同一 Connection で競合しうる。
- 対策案: `PlayerJoinHandler` の DB 書き込みをメインスレッド化（`runTaskAsynchronously` → `runTask`）。handlePlayerData は DB + スケジュールのみで Bukkit API 直呼びが無いため、メインスレッド化は安全（SQLite ローカル書き込みは数ms）。
- 同種の手動トランザクションは `PlayerDAO.transferBalance`（メインスレッド・/pay）にも存在。`OptimizedBatchProcessor` も同パターン＋非同期だが **onEnable で未起動（休眠）**。

## pre-commit フック（未解決の注意点）

- husky の pre-commit フックが `python3 -c "import yaml; yaml.safe_load(open('src/main/resources/config.yml'))"` で config.yml の YAML 構文を検証し、失敗するとコミットを中止する。
- **未解決の不整合**: 前回コミット時は `husky > pre-commit (node v20.11.0)` バナーが出てフックが走り「config.yml 構文エラー」で失敗したが、その後の調査時点では `.git/hooks/pre-commit` が存在せず `core.hooksPath` も未設定だった。**フックが本当にどう起動されるのか、なぜそのとき失敗したのかは未確定**。次セッションでコミット前に `.git/hooks/pre-commit` の有無と husky の導入状態を確認すること。
- PATH 上に python3 が複数（pyenv shim / /usr/local / /usr/bin）。フックが引く python3 が PyYAML を持つか要確認。

## 前回の作業物の所在

- 前回作った market 実装一式（MarketManager / MarketListingDAO / MarketGUI / MyListingsGUI / MarketCommand / MarketMessages / models / util / tests / sql migration / docs/config-reference.md）は **`git stash@{0}`「market-feature-wip-discarded-20260611」に退避**（24ファイル）。
  - 参照: `git stash show -p stash@{0}` / 復元: `git stash apply stash@{0}` / 破棄: `git stash drop stash@{0}`
- ベースのコミットは `0339240`（#55）。market のコミット・PR・push は**いずれも未実施**（前回「完了」報告は誤り）。

## 確定仕様（前回ユーザーと合意済み・参考）

- 形態: 公開マーケット型（出品者オフラインでも購入可、代金は出品者の `bank_balance` へ）
- 出品=コマンド `/market sell <価格>`（手持ちアイテム）、一覧/購入=GUI
- 価格は出品者が自由設定、手数料 5%（消却）
- 期限切れはアイテム喪失ゼロの回収方式
