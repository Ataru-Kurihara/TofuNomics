---
name: server-config
description: TofuNomics 1.21 Lobbyサーバーのconfig.ymlを安全に修正する。設定値（item_prices価格、メッセージ、数値設定など）の変更を、バックアップ→該当箇所のみピンポイント編集→YAML検証→reload案内の手順で実行。サーバーのconfig変更・価格修正・設定反映を求められたときに使用。
---

# TofuNomics サーバーconfig.yml 安全修正スキル

稼働中の1.21 Lobbyサーバーの `config.yml` を、サーバー固有設定（NPC座標・銀行/取引所位置・trading_posts配置）を壊さずに修正するための手順。`ssh lobby` でサーバー上の該当箇所のみを直接編集する。

## 前提・接続情報

- 接続: `ssh lobby`（`.claude/settings.json` で `Bash(ssh lobby:*)` 許可が必要。未許可ならユーザーに追加依頼）
- configパス: `/opt/minecraft/servers/lobby-1.21/plugins/TofuNomics/config.yml`
- ⚠️ 旧 `scripts/download_config.sh` / `upload_config.sh` は1.16.5専用。1.21では**使わない**（`ssh lobby` 直編集が正規手順）

## 絶対禁止

- ❌ ローカルのconfig.ymlでサーバーのconfig.ymlを上書きコピー（`scp`含む）
- ❌ ファイル全体の置換・再生成
- ❌ NPC座標・銀行/ATM位置・`npc_system.trading_posts[]` 配置情報の削除/改変
- これらを消すと全NPC再配置が必要になり復旧に膨大な時間がかかる

## 手順

### 1. 修正対象の現在値を確認
変更したいキーの現在値と行・インデントを把握する。
```bash
ssh lobby 'grep -nE "^\s+(キー1|キー2):" /opt/minecraft/servers/lobby-1.21/plugins/TofuNomics/config.yml'
```
- `item_prices` はインデント4スペース。同名キーが他セクション（`trading_posts[].items` 等、6スペース）にも存在しうるので、インデントを `^    ` で固定して対象を絞ること。

### 2. バックアップ作成（必須）
```bash
ssh lobby 'cd /opt/minecraft/servers/lobby-1.21/plugins/TofuNomics && cp config.yml config.yml.backup.$(date +%Y%m%d-%H%M%S) && ls -t config.yml.backup.* | head -1'
```

### 3. 該当箇所のみピンポイント編集
`sed` でアンカー付き（`^...$`）に置換。インデントを含めて完全一致させ、意図しない別セクションに当てない。小数のドットは `\.` でエスケープ。
```bash
ssh lobby 'cd /opt/minecraft/servers/lobby-1.21/plugins/TofuNomics && sed -i \
  -e "s/^    stone: 0\.3$/    stone: 1/" \
  -e "s/^    cobblestone: 0\.1$/    cobblestone: 1/" \
  config.yml'
```

### 4. 編集結果とYAML構文を検証
```bash
ssh lobby 'cd /opt/minecraft/servers/lobby-1.21/plugins/TofuNomics && grep -nE "^    (stone|cobblestone):" config.yml && python3 -c "import yaml; yaml.safe_load(open(\"config.yml\")); print(\"YAML OK\")"'
```
変更後の値が正しく、サーバー固有設定が消えていないことを確認。

### 5. リロード案内（ユーザー手動）
反映には `/tofunomics reload` が必要。**リロードはユーザーがサーバーコンソール/ゲーム内で実行**するため、Claudeは実行せず案内のみ。
> 「サーバーコンソールまたはゲーム内で `/tofunomics reload` を実行してください。反映後、動作を確認してください。」

### 6. 問題時の復元
```bash
ssh lobby 'cd /opt/minecraft/servers/lobby-1.21/plugins/TofuNomics && cp config.yml.backup.YYYYMMDD-HHMMSS config.yml'
```
その後 reload を再案内。

## 補足ルール

- **ソース正本も更新する場合:** jar同梱 `src/main/resources/config.yml` は既存サーバーconfigを上書きしないが、正本管理のため通常はソースも別途修正してPRにする（`develop`不在のため `main` から作業ブランチ→PR）。サーバー反映とソース修正は独立した2作業。
- **item_prices価格の注意:** 通貨は金塊（整数）ベース。価格0.5未満は売却GUIで「0」表示＋1個売却拒否になるため**1以上**にする。
- **売却品の追加時:** `item_prices` 価格だけでなく `trading_posts[].items` への追記とコード側タブ判定（`TradingGUI.isMiningItem()` 等）も必要な場合がある。
