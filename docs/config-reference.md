# TofuNomics config.yml リファレンス

本番サーバー（Lobby 1.21）で実際に稼働している `config.yml` を解析して作成した設定リファレンスです。

- **解析対象**: `/opt/minecraft/servers/lobby-1.21/plugins/TofuNomics/config.yml`
- **解析時点の規模**: 6212 行 / トップレベル 27 セクション
- **`config_version`**: `2.1`
- **値はすべて本番稼働中の実値**（既定値の参考として記載）

> ⚠️ このドキュメントは「現状把握」と「安全な編集」のための参照資料です。値の正解を定義するものではありません。

---

## 目次

1. [編集の鉄則（最重要）](#編集の鉄則最重要)
2. [既知の不整合・注意点](#既知の不整合注意点)
3. [セクション別リファレンス](#セクション別リファレンス)
   - [messages](#messages-メッセージ)
   - [player_join](#player_join-参加時処理)
   - [npc_system](#npc_system-npcシステムサーバー固有データ多数)
   - [database](#database-データベース)
   - [economy](#economy-経済)
   - [jobs](#jobs-職業)
   - [job_skills](#job_skills-職業スキル)
   - [leveling](#leveling-レベリング)
   - [event_rewards](#event_rewards-イベント報酬)
   - [events](#events-イベント処理)
   - [trade_system](#trade_system-取引チェスト/npc取引)
   - [land_protection](#land_protection-土地保護)
   - [housing_rental](#housing_rental-住居賃貸)
   - [area_system](#area_system-エリア通知)
   - [scoreboard](#scoreboard-スコアボード)
   - [time_announcement](#time_announcement-時報)
   - [clock_item](#clock_item-時計アイテム)
   - [rules](#rules-ルール確認)
   - [city_map](#city_map-都市マップ)
   - [tutorial](#tutorial-チュートリアル)
   - [guide_book_settings](#guide_book_settings-職業ガイドブック)
   - [ux_enhancements](#ux_enhancements-ux演出)
   - [food_buff](#food_buff-食事バフ)
   - [performance](#performance-パフォーマンス)
   - [api](#api-外部api連携)
   - [debug](#debug-デバッグ)
   - [market（新機能・現状未反映）](#market-プレイヤー間マーケット新機能現状本番未反映)

---

## 編集の鉄則（最重要）

1. **サーバーの config.yml をローカルのファイルで上書きしない**（`scp` での全体コピー含む）。NPC 座標・取引所配置・価格表などサーバー固有データが消失し、復旧に膨大な時間がかかる。
2. **編集は該当箇所のみをサーバー上で直接ピンポイント修正**する。編集前に必ずバックアップ（`cp config.yml config.yml.backup.$(date +%Y%m%d-%H%M%S)`）。
3. **配布 jar 同梱の `config.yml` は既存サーバーの config を上書きしない**（Bukkit は既存を優先）。新しい設定キーを本番へ反映するには手動追記が必要。
4. 反映は `/tofunomics reload`（config のみ）または `/plugman reload TofuNomics`（jar 含む）。後者は `saveConfig()` により config が書き戻される点に注意（[後述](#既知の不整合注意点)）。

特に絶対に触らない「サーバー固有データ」:

| パス | 内容 |
|---|---|
| `npc_system.item_prices` | アイテム買取価格（219 キー） |
| `npc_system.trading_posts` | 取引所の配置・対応職業（56 項目） |
| `npc_system.bank_npcs` / `trading_npcs` / `food_npc` / `processing_npc` | 各 NPC の配置座標 |
| `npc_system.bank_npc.locations` | 銀行 NPC 座標 |
| `economy.location_restrictions.banks` / `atms` | 銀行・ATM 位置 |
| `player_join.spawn_location` | スポーン座標 |
| `area_system.areas.*` | 各エリアの境界座標 |

---

## 既知の不整合・注意点

### 1. `database.filename` は無視される
- config: `database.filename: tofunomics_world.db`
- 実際にプラグインが使う DB は **`tofunomics.db`**（`TofuNomics.java:265` で `new File(dataFolder, "tofunomics.db")` とハードコード）。
- `database.filename` を読むのは未使用の `HikariDatabaseManager` のみ。よってこの設定値は**事実上効果がない**。
- 同様に `database.connection_pool.*` と `performance.database.connection_pool.*` も、現行の単一 Connection 方式（`DatabaseManager`）では実質未使用。

### 2. `/plugman reload` で手動追記が消えることがある
- onEnable → `initializeAutoConfig()` → `saveConfig()` が config.yml を書き戻すため、**プラグインが認識していない手動追記セクションが失われる場合がある**。
- 実例: 新機能のマーケット設定（`market` / `messages.market`）を本番へ追記したが、reload 後に消失（6240 → 6212 行）。[market セクション](#market-プレイヤー間マーケット新機能現状本番未反映)参照。

### 3. 通貨価格の整数制約
- 通貨は金塊（整数）ベース。`npc_system.item_prices` の価格 0.5 未満は売却 GUI で「0」表示＋1 個売却拒否になるため、価格は **1 以上**にする。

---

## セクション別リファレンス

各セクションの代表的な設定と本番実値を記載。座標・価格などの大量データは要約。

### messages （メッセージ）
プレイヤー向け各種メッセージ文言。`&` カラーコード、`%player%`/`%amount%` 等のプレースホルダを使用。

| サブセクション | 用途 |
|---|---|
| `messages.npc.*` | NPC 全般・取引 NPC・銀行 NPC のメッセージ（取引所別の `npc_specific.<id>` 文言含む） |
| `messages.craft.*` | クラフト制限メッセージ（`{item}`/`{required_job}` 等のプレースホルダ） |
| `messages.economy.*` | 残高・送金・入出金（10 種） |
| `messages.jobs.*` | 就職・辞職・エラー（5 種） |
| `messages.scoreboard.*` | スコアボード ON/OFF（4 種） |
| `messages.rules.*` | ルール関連（`§` カラーコード使用） |

### player_join （参加時処理）
tofuNomics ワールド入場時の処理。
- `spawn_location`: スポーン座標（`world=tofuNomics, x=-96, y=76, z=-247, yaw=-178.8, pitch=-18.8`）・`teleport_delay=60`（tick）。**座標はサーバー固有**。
- `welcome_messages`（6 行）/ `welcome_title` / `welcome_subtitle`
- `new_player_bonus`: `enabled=true, amount=100.0`、新規メッセージ 5 行
- `welcome_back_message`: `days_threshold=7`（日）以上ぶりで復帰歓迎

### npc_system （NPCシステム・サーバー固有データ多数）
> ⚠️ このセクションは座標・価格などサーバー固有データの塊。**ローカルから上書き厳禁**。

- `interaction`: `cooldown_ms=1000, session_timeout_ms=300000, access_range=5, look_at_player=true, gui_delay_ticks=40`
- `item_prices`（**219 キー**）: アイテム別買取価格表
- `trading_posts`（**56 項目**）: 取引所の配置・対応職業・営業設定
- `bank_npcs` / `trading_npcs` / `food_npc` / `processing_npc`: 各 NPC の配置（座標含む）
- `bank_npc.locations`（2 項目）

### database （データベース）
- `filename: tofunomics_world.db` … **実際は無視される**（[不整合 #1](#既知の不整合注意点)）。実 DB は `tofunomics.db`。
- `connection_pool.max_connections=10, timeout=30000` … 現行方式では実質未使用。

### economy （経済）
- `currency`: `name=金塊, symbol=G, decimal_places=2, coin_value=1, dynamic_value=true, min_value=1, max_value=1000`
- `starting_balance=100.0`
- `pay`: `minimum_amount=1.0, maximum_amount=5000.0, fee_percentage=0.0`（送金手数料 0%）
- `withdraw_deposit`: `max_withdraw=10000.0, max_deposit=10000.0`
- `location_restrictions`: `enabled=true, access_range=5`、`banks`/`atms` は位置リスト（**サーバー固有**）
- `enabled_worlds`（1 件）: 経済有効ワールド

### jobs （職業）
- `general`: `max_jobs_per_player=1, keep_level_on_change=true, job_change_cooldown=0`
- `block_restrictions`: `enabled=true`、`basic_blocks`（19 種）、職業別採掘許可ブロック（`miner`8 / `woodcutter`15 / `farmer`13 / `fisherman`14 / `blacksmith`9 / `alchemist`8 / `enchanter`4 / `architect`13）
- `job_settings.<job>`（8 職業）: 各職業の `display_name` / `description` / `max_level=100` / `base_income_multiplier=1.0` / `exp_multiplier=10.0` / `base_sell_bonus=0.05` / `guide_book`（タイトル・著者・ページ）
  - 職業: miner(鉱夫) / woodcutter(木こり) / farmer(農家) / fisherman(釣り人) / blacksmith(鍛冶屋) / alchemist(ポーション屋) / enchanter(エンチャンター) / architect(建築家)

### job_skills （職業スキル）
8 職業 × 各 3 スキルの発動パラメータ。各スキル共通: `base_probability`（基本確率） / `level_bonus`（レベル毎増加） / `max_probability`（上限） / `cooldown_seconds` ＋スキル固有効果値。

| 職業 | スキル |
|---|---|
| miner | fortune_strike / vein_discovery / mining_mastery |
| woodcutter | tree_feller / sapling_blessing / forest_guardian |
| farmer | harvest_blessing / twin_miracle / growth_acceleration / selective_breeding |
| fisherman | big_catch / treasure_hunter / sea_blessing |
| blacksmith | perfect_repair / master_craftsmanship / artifact_creation |
| alchemist | ingredient_conservation / double_brewing / alchemy_mastery |
| enchanter | experience_conservation / bonus_enchantment / mystical_arts |
| architect | material_efficiency / architectural_aesthetics / master_architect |

### leveling （レベリング）
- `experience`: `base_multiplier=100, exponent=1.9`、`level_penalty`（factor=0.005, minimum=0.4）、`level_scaling`（early/mid/advanced/master の 4 帯域で倍率）
- `rewards`: `base_rewards.money`（base=50.0, level_multiplier=2.5, max=500.0）、`skill_points`、`job_specific_rewards.milestone_rewards`（level_10/25/50/75 のマイルストーン報酬）

### event_rewards （イベント報酬）
行動ごとの経験値・収入。
- `global_multipliers`: `experience_multiplier=1.2, income_multiplier=1.1`、時間帯ボーナス（morning/day/evening/night）、天候ボーナス（rain/thunderstorm）
- `individual_events`: `block_break` / `block_place`（プロジェクト規模ボーナス） / `fishing`（魚種・宝ボーナス） / `crafting`（複雑度ボーナス）

### events （イベント処理）
- `enabled=true`、`excluded_worlds`(2) / `excluded_game_modes`(1)
- `caching`: `enabled=true, expiry_time=300000, cleanup_interval=60000`
- `async_processing`: `enabled=true, thread_pool_size=2, batch_interval=100, max_batch_size=50`
- `handlers`: `brewing` / `enchantment` / `breeding` / `growth` / `building`（建築プロジェクト追跡・装飾制限）
- `performance.level_up`: レベルアップ告知文言

### trade_system （取引チェスト/NPC取引）
プレイヤー間マーケットとは別の、職業別買取システム。
- `enabled=true, confirmation_required=true, global_price_multiplier=1.0`
- `job_price_multipliers`: 職業別買取倍率（miner=1.2, blacksmith/alchemist/enchanter=1.4 等）
- `history`: `max_days=30, max_records_per_player=1000, auto_cleanup=true`
- `limits`: `max_trades_per_day=50, max_items_per_trade=2304, max_earnings_per_day=10000.0`
- `chest_settings`: `max_chests_per_job=10` 等
- `messages`（9 種）

### land_protection （土地保護）
- `worldguard_integration=true, urban_land_price=100.0, max_lands_per_player=5`、`protected_worlds`(1)

### housing_rental （住居賃貸）
- `enabled=true, max_rentals_per_player=3, selection_tool=WOODEN_AXE`
- `rental_periods`: daily(1〜30 日) / weekly(multiplier=6.5) / monthly(multiplier=25.0)
- `worldguard_integration=true, world_name=tofuNomics, expire_check_interval=3600, expire_notification_days=3`
- `messages`(8) / `city_protection`（既定 disabled） / `worldguard_rental_regions`（自動フラグ設定）

### area_system （エリア通知）
ワールド内エリア入場時のタイトル通知。`enabled=true, cooldown_seconds=60`。
- `areas.north/south/east/west`: 各エリアの**境界座標**（min/max x,y,z＝サーバー固有）＋ title/subtitle/message/sound

### scoreboard （スコアボード）
- `enabled=true, default_enabled=true, update_interval=1`、`title=&6&l★ &eTofuNomics &6&l★`
- `display_settings`(9): 残高・職業・レベル・経験値・時刻・取引時間などの表示 ON/OFF
- `enabled_worlds`(1) / `toggle_hint` / `hint`

### time_announcement （時報）
- `enabled=true, interval_minutes=60, announce_trading_hours=true`
- `messages`: 時報・取引所開店/閉店/閉店予告・営業状態文言（営業時間 6:00-22:00）

### clock_item （時計アイテム）
- `enabled=true, purchase_price=500.0`、`seller_npcs`（0 件）
- `action_bar`: 所持時のアクションバー表示（`update_interval=20`、フォーマット・営業状態）
- `item`: `material=CLOCK, enchanted=true`、名前・lore
- `messages`(4)

### rules （ルール確認）
- `enabled=false, require_agreement=false`（**現在は無効**）、`url=https://to-fu.world/worlds/tofu-nomics/`
- `pages`(4): ようこそ/経済/ジョブ/禁止事項。`messages`(7)

### city_map （都市マップ）
- `map_id=''`（**未設定**）、`title=TofuNomics 中心都市マップ`、`messages`(5)、`facilities`(4)

### tutorial （チュートリアル）
- `enabled=true, auto_start=true, start_delay_seconds=10, step_message_delay=60`
- `completion_rewards`: `enabled=true, money=50.0`
- `messages`: 4 ステップ（就職→収入→銀行→取引）の案内・進捗・スキップ文言

### guide_book_settings （職業ガイドブック）
- `enabled=true, prevent_duplicate=true, drop_when_full=true`、`give_message`

### ux_enhancements （UX演出）
- `clickable_messages` / `levelup_title` / `levelup_particle` / `pay_actionbar` / `trading_bossbar` / `gui_decoration` … すべて `true`

### food_buff （食事バフ）
食事で職業経験値ブースト。`enabled=true`。
- `categories`: bread(+10%/180s) / vegetable(+15%/240s) / fish(+25%/360s) / meat(+30%/480s)
- `messages`(2): 付与・切替

### performance （パフォーマンス）
- `database.connection_pool`（maximum_pool_size=15 等）/ `batch_processing`（batch_size=100）/ `cleanup`（old_data_threshold_days=30）… **現行の単一 Connection 方式では実質未使用**
- `caching`: player/job/config キャッシュのサイズ・TTL
- `memory_management`: オブジェクトプール・メモリ監視閾値
- `monitoring`: 統計・リアルタイム監視（CPU/メモリ/TPS 閾値）・アラート（discord_webhook は空）

### api （外部API連携）
- `tohu_app`: `enabled=true, base_url=https://tohu-backend-prod-...run.app, token='', world_id=5, retry(max_attempts=3, initial_delay_seconds=5), debug=true`

### debug （デバッグ）
- `enabled=false, verbose=false`

### market （プレイヤー間マーケット・新機能／現状本番未反映）
> ⚠️ **現在、本番 config.yml には `market` も `messages.market` も存在しません**（解析時点）。新機能追加時に手動追記したものの、`/plugman reload` の `saveConfig()` で書き戻されて消失したとみられる（[不整合 #2](#既知の不整合注意点)）。

このセクションが無いと、出品/購入時に「メッセージが見つかりません」と表示される。jar 同梱の既定 config（`src/main/resources/config.yml`）には定義済みのため、本番へは再度手動追記が必要。

**トップレベル `market`（既定値）**:

| キー | 既定値 | 意味 |
|---|---|---|
| `enabled` | `true` | マーケット機能の有効化 |
| `fee_rate` | `0.05` | 販売手数料率（出品者受取 = price × (1 − fee_rate)） |
| `max_listings_per_player` | `10` | 1 人あたり同時出品上限 |
| `listing_duration_days` | `7` | 出品の有効日数（0 で無期限） |
| `min_price` | `1` | 最低出品価格 |
| `max_price` | `1000000` | 最高出品価格 |
| `allow_self_purchase` | `false` | 自分の出品を購入可能にするか |
| `expire_check_interval` | `3600` | 期限切れチェック間隔（秒） |

**`messages.market`（14 メッセージ）**: `listed` / `purchased` / `sold_notify` / `cancelled` / `reclaimed` / `invalid_item` / `invalid_price` / `currency_not_allowed` / `listing_limit` / `not_available` / `already_sold` / `insufficient_funds` / `inventory_full` / `not_owner` / `error`

> 再追記の正確な YAML は `src/main/resources/config.yml` の `market:` および `messages.market:` を参照。本番へは**全体上書きせず、当該 2 ブロックのみピンポイント追記**すること。
