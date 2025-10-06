# トラブルシューティングガイド

> よくある問題と対処法、復旧手順

## 📋 目次
1. [緊急対応](#緊急対応)
2. [プラグイン起動問題](#プラグイン起動問題)
3. [データベース問題](#データベース問題)
4. [NPC関連問題](#npc関連問題)
5. [職業システム問題](#職業システム問題)
6. [経済システム問題](#経済システム問題)
7. [パフォーマンス問題](#パフォーマンス問題)
8. [ログファイル確認](#ログファイル確認)
9. [バックアップ・復旧手順](#バックアップ復旧手順)

---

## 緊急対応

### クリティカルエラー発生時

#### 即座に実行すべきコマンド
```bash
# 1. プラグインをセーフモードでリロード
/tofunomics reload --safe

# 2. パフォーマンス統計確認
/tofunomics performance

# 3. エラーログ確認
tail -f plugins/TofuNomics/logs/error.log
```

### サーバークラッシュ時

#### 復旧手順
```bash
# 1. サーバーログを確認
tail -100 logs/latest.log

# 2. プラグインが原因か特定
grep "TofuNomics" logs/latest.log

# 3. 必要に応じてプラグインを無効化
mv plugins/TofuNomics.jar plugins/TofuNomics.jar.disabled

# 4. サーバー起動
# 5. 原因を特定後、プラグインを再有効化
```

### データ損失の疑い

#### 確認手順
```bash
# 1. データベースファイルの存在確認
ls -lh plugins/TofuNomics/tofunomics_world.db

# 2. データベースのサイズ確認（異常に小さくないか）
# 3. バックアップから復元の検討
```

---

## プラグイン起動問題

### プラグインが読み込まれない

**症状**:
- `/plugins` リストにTofuNomicsが表示されない
- コマンドが認識されない

**原因と対処法**:

#### 1. ファイル配置ミス
```bash
# 確認
ls plugins/TofuNomics*.jar

# 正しい配置
plugins/TofuNomics.jar
```

#### 2. 依存プラグイン不足
**必要な依存関係**:
- Vault（経済システム連携）
- Citizens（NPC機能、オプション）

```bash
# 依存プラグインの確認
/plugins

# 不足している場合はインストール
```

#### 3. Javaバージョン不一致
**必要バージョン**: Java 11以上

```bash
# Javaバージョン確認
java -version

# アップデートが必要な場合
# Javaを更新してサーバー再起動
```

### プラグインがエラーで無効化される

**対処法**:
```bash
# 1. エラーログを確認
cat plugins/TofuNomics/logs/error.log

# 2. 設定ファイルの構文エラーチェック
# YAMLバリデーターで config.yml を検証

# 3. 設定をデフォルトに戻す
mv plugins/TofuNomics/config.yml plugins/TofuNomics/config.yml.backup
# サーバー再起動で自動生成

# 4. プラグインを再度有効化
/plugins enable TofuNomics
```

---

## データベース問題

### データベース接続エラー

**症状**:
```
[ERROR] Failed to connect to database
[ERROR] Database connection timeout
```

**対処法**:

#### 1. ファイル権限確認
```bash
# 権限確認
ls -l plugins/TofuNomics/tofunomics_world.db

# 権限修正（必要に応じて）
chmod 644 plugins/TofuNomics/tofunomics_world.db
```

#### 2. ファイルロック解除
```bash
# サーバー停止
/stop

# ロックファイル削除
rm plugins/TofuNomics/tofunomics_world.db-journal

# サーバー起動
```

#### 3. 接続設定確認
```yaml
# config.yml
database:
  connection_pool:
    max_connections: 10
    timeout: 30000  # タイムアウトを増やす
```

### データベース破損

**症状**:
```
[ERROR] Database corruption detected
[ERROR] Unable to read player data
```

**対処法**:

#### 1. 整合性チェック
```bash
# SQLiteがインストールされている場合
sqlite3 plugins/TofuNomics/tofunomics_world.db "PRAGMA integrity_check;"
```

#### 2. バックアップから復元
```bash
# サーバー停止
/stop

# 破損ファイルを保存
mv plugins/TofuNomics/tofunomics_world.db plugins/TofuNomics/tofunomics_world.db.corrupted

# バックアップから復元
cp plugins/TofuNomics/backups/tofunomics_world_backup.db plugins/TofuNomics/tofunomics_world.db

# サーバー起動
```

### プレイヤーデータが保存されない

**原因**:
- データベース書き込みエラー
- ディスク容量不足
- 権限問題

**対処法**:
```bash
# 1. ディスク容量確認
df -h

# 2. ログ確認
grep "save" plugins/TofuNomics/logs/error.log

# 3. 手動保存実行
/tofunomics save

# 4. データベース最適化
/tofunomics database optimize
```

---

## NPC関連問題

### NPCが表示されない

**症状**:
- NPCをスポーンしたが見えない
- サーバー再起動後にNPCが消える

**対処法**:

#### 1. NPC存在確認
```bash
# NPC一覧確認
/tofunomics npc list

# NPCの位置へテレポート
/tofunomics npc tp <NPC名>
```

#### 2. チャンク再読み込み
```bash
# プレイヤーを離れた場所にテレポート
/tp @s ~ ~ ~100

# 元の場所に戻る
/tp @s ~ ~ ~-100
```

#### 3. NPC再スポーン
```bash
# NPCを削除
/tofunomics npc remove <NPC名>

# 再度スポーン
/tofunomics npc spawn <タイプ> <NPC名>
```

### NPCが反応しない

**症状**:
- クリックしてもGUIが開かない
- インタラクションできない

**対処法**:

#### 1. 距離確認
```
NPCから5ブロック以内に近づく
```

#### 2. 営業時間確認（食料NPC）
```bash
# ゲーム内時刻確認
/time query daytime

# 営業時間: 6:00-22:00（食料NPC）
```

#### 3. セッションリセット
```bash
# プレイヤーを再ログイン
# またはNPCシステムをリロード
/tofunomics npc reload
```

#### 4. NPC状態診断
```bash
/tofunomics npc status <NPC名>
```

### NPC重複問題

**症状**:
- 同じNPCが複数表示される
- リロード時にNPCが増える

**対処法**:
```bash
# 1. 全NPCをクリーンアップ
/tofunomics npc cleanup

# 2. NPCを再セットアップ
/tofunomics npc setup-all

# 3. 設定をリロード
/tofunomics reload
```

---

## 職業システム問題

### 職業に就けない

**症状**:
```
[ERROR] Unable to join job
職業に就くことができません
```

**原因と対処法**:

#### 1. 既に職業に就いている
```bash
# 現在の職業確認
/jobs stats

# 職業を辞める
/jobs leave
```

#### 2. レベル要件不足（転職時）
```
転職にはレベル50以上が必要
```

**対処法**:
```bash
# 管理者が強制的に職業変更
/jobs setjob <プレイヤー名> <職業名>
```

#### 3. クールダウン中
```
24時間のクールダウン中です
```

**対処法**:
```bash
# 管理者がクールダウンをリセット
/jobs resetcooldown <プレイヤー名>
```

### 経験値が入らない

**原因**:
- 職業に対応していないブロック
- 経験値計算エラー
- データベース保存エラー

**対処法**:
```bash
# 1. 職業確認
/jobs stats

# 2. 対応ブロック確認（ログ）
/tofunomics debug jobs true

# 3. 経験値を手動付与
/jobs addexp <プレイヤー名> <職業名> <経験値>

# 4. デバッグモード終了
/tofunomics debug jobs false
```

### スキルが発動しない

**原因**:
- レベル不足
- 確率的に発動していない
- スキルシステムエラー

**対処法**:
```bash
# 1. レベル確認
/jobs stats <プレイヤー名>

# 2. スキル情報確認
/jobs skills <プレイヤー名>

# 3. スキルシステムリロード
/tofunomics reload
```

---

## 経済システム問題

### 残高が表示されない

**症状**:
```
/balance コマンドでエラー
残高: null
```

**対処法**:
```bash
# 1. プレイヤーデータ初期化
/eco reload

# 2. 残高を手動設定
/eco set <プレイヤー名> 100

# 3. データベース確認
/tofunomics database check
```

### 送金ができない

**症状**:
```
[ERROR] Payment failed
送金に失敗しました
```

**原因と対処法**:

#### 1. 残高不足
```bash
# 残高確認
/balance <プレイヤー名>
```

#### 2. 送金制限
```yaml
# config.yml
economy:
  pay:
    minimum_amount: 1.0
    maximum_amount: 5000.0
```

#### 3. 対象プレイヤー不在
```
プレイヤーがオンラインである必要があります
```

### 取引チェストが機能しない

**症状**:
- アイテムを入れても売却されない
- エラーメッセージが表示される

**対処法**:
```bash
# 1. 取引チェスト一覧確認
/trade list

# 2. 職業確認
/jobs stats

# 3. 取引チェスト再設定
/trade remove
/trade setup <職業名>

# 4. 取引システムリロード
/trade reload
```

---

## パフォーマンス問題

### サーバーが重い

**症状**:
- TPS低下（20未満）
- 遅延が発生
- プレイヤーからラグの報告

**対処法**:

#### 1. パフォーマンス統計確認
```bash
/tofunomics performance
```

#### 2. 重いプロセス特定
```bash
# TPS確認
/tps

# プラグイン別負荷確認（Timingsプラグイン使用）
/timings on
# 5分間待つ
/timings paste
```

#### 3. データベース最適化
```yaml
# config.yml
performance:
  database:
    batch_processing:
      enabled: true
      batch_size: 100
```

#### 4. キャッシュサイズ調整
```yaml
performance:
  caching:
    player_cache:
      max_size: 1000
      expire_after_access: 1800
```

### メモリ不足

**症状**:
```
[ERROR] OutOfMemoryError
Java heap space
```

**対処法**:

#### 1. サーバー起動オプション調整
```bash
# start.sh / start.bat
java -Xms2G -Xmx4G -jar server.jar
```

#### 2. キャッシュクリア
```bash
/tofunomics cache clear
```

#### 3. データベース最適化
```bash
/tofunomics database optimize
```

---

## ログファイル確認

### 重要なログファイル

#### 1. サーバーログ
```bash
# 最新ログ
tail -f logs/latest.log

# 過去100行
tail -100 logs/latest.log

# エラーのみ抽出
grep ERROR logs/latest.log
```

#### 2. プラグインログ
```bash
# TofuNomicsログ
tail -f plugins/TofuNomics/logs/tofunomics.log

# エラーログ
tail -f plugins/TofuNomics/logs/error.log
```

#### 3. デバッグログ
```yaml
# config.yml でデバッグモード有効化
debug:
  enabled: true
  verbose: true
  npc_debug: true
```

### エラーメッセージの読み方

#### スタックトレース
```
[ERROR] Failed to save player data
org.tofu.tofunomics.dao.PlayerDAO.savePlayer(PlayerDAO.java:123)
  at org.tofu.tofunomics.TofuNomics.onDisable(TofuNomics.java:45)
```

**重要な情報**:
- エラーメッセージ: "Failed to save player data"
- 発生場所: PlayerDAO.java の 123行目
- 呼び出し元: TofuNomics.java の 45行目

---

## バックアップ・復旧手順

### 自動バックアップ設定

#### バックアップスクリプト
```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/path/to/backups"
DATE=$(date +%Y%m%d_%H%M%S)

# プラグインフォルダをバックアップ
cp -r plugins/TofuNomics "$BACKUP_DIR/TofuNomics_$DATE"

# データベースのみバックアップ
cp plugins/TofuNomics/tofunomics_world.db "$BACKUP_DIR/tofunomics_world_$DATE.db"

# 古いバックアップを削除（30日以上前）
find $BACKUP_DIR -name "TofuNomics_*" -mtime +30 -exec rm -rf {} \;
```

#### cron設定
```bash
# 毎日午前3時にバックアップ
0 3 * * * /path/to/backup.sh
```

### 手動バックアップ

#### 完全バックアップ
```bash
# サーバー停止
/stop

# バックアップ作成
cp -r plugins/TofuNomics backups/TofuNomics_$(date +%Y%m%d_%H%M%S)

# サーバー起動
```

#### データベースのみバックアップ
```bash
cp plugins/TofuNomics/tofunomics_world.db \
   backups/tofunomics_world_$(date +%Y%m%d_%H%M%S).db
```

### 復元手順

#### 完全復元
```bash
# 1. サーバー停止
/stop

# 2. 現在のデータを保存
mv plugins/TofuNomics plugins/TofuNomics.old

# 3. バックアップから復元
cp -r backups/TofuNomics_20240115_030000 plugins/TofuNomics

# 4. サーバー起動
```

#### データベースのみ復元
```bash
# 1. サーバー停止
/stop

# 2. 現在のデータベースを保存
mv plugins/TofuNomics/tofunomics_world.db \
   plugins/TofuNomics/tofunomics_world.db.old

# 3. バックアップから復元
cp backups/tofunomics_world_20240115_030000.db \
   plugins/TofuNomics/tofunomics_world.db

# 4. サーバー起動
```

---

## エラーコード一覧

| コード | 説明 | 対処法 |
|--------|------|--------|
| **DB_001** | データベース接続エラー | 接続設定確認、ファイル権限確認 |
| **DB_002** | データベース書き込みエラー | ディスク容量確認、権限確認 |
| **DB_003** | データベース読み込みエラー | ファイル破損チェック、復元検討 |
| **NPC_001** | NPC作成失敗 | 座標確認、権限確認 |
| **NPC_002** | NPC削除失敗 | NPC存在確認、データベース確認 |
| **JOB_001** | 職業システムエラー | 職業設定確認、プレイヤーデータ確認 |
| **JOB_002** | 経験値計算エラー | 設定確認、データベース確認 |
| **ECO_001** | 経済システムエラー | 残高データ確認、設定確認 |
| **ECO_002** | 送金エラー | 残高確認、制限確認 |

---

## サポート・お問い合わせ

### 問題が解決しない場合

#### 1. GitHub Issues
https://github.com/tofu-server/TofuNomics/issues

**報告内容**:
- エラーメッセージ
- サーバーログ
- 再現手順
- 環境情報（Minecraftバージョン、Javaバージョン等）

#### 2. 開発チームへの連絡
- **開発チーム**: TofuNomics Team
- **メール**: [連絡先情報]

---

## 関連ドキュメント
- **[管理者ガイドTOP](README.md)** - 管理者ガイドトップ
- **[設定ファイル](configuration.md)** - 設定変更方法
- **[日常運用](daily-operations.md)** - 定期メンテナンス

---

**最終更新**: 2024年
**ドキュメントバージョン**: 1.0
