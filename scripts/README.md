# サーバーConfig管理スクリプト

> **注意**: `download_config.sh` / `upload_config.sh` / `deploy_plugin.sh` と `.server-config.env` は、
> 旧サーバー（1.16.5、廃止済み）向けに作られたものです。現行サーバーは `ssh lobby` で入り、
> サーバー上の設定ファイルを直接編集します。
> 本番設定の写しは `server-config/`（.gitignore 済み）に置きます。`src/main/resources/` に置くと
> jar に同梱され、公開リポジトリにも入りかねないため、スクリプトはその置き場所を拒否します。

サーバー上の `config.yml` をローカルで編集しやすくするためのスクリプト集です。

## 初期セットアップ

### 1. 設定ファイルの作成

```bash
# テンプレートをコピー
cp .server-config.env.example .server-config.env

# エディタで開いて実際の接続情報を入力
vim .server-config.env  # または nano, code など
```

### 2. 設定ファイルの編集

`.server-config.env` を開いて、以下の項目を設定してください：

```bash
# SSHホスト（IPアドレスまたはドメイン名）
SERVER_HOST=your-server.example.com

# SSHユーザー名
SERVER_USER=your-username

# SSH鍵のパス（省略可能、デフォルトは ~/.ssh/id_rsa）
SSH_KEY_PATH=~/.ssh/id_rsa

# サーバー上のconfig.ymlのパス
SERVER_CONFIG_PATH=~/mc/plugins/TofuNomics/config.yml

# ローカルの保存先パス
LOCAL_CONFIG_PATH=server-config/server_config.yml
```

### 3. スクリプトに実行権限を付与

```bash
chmod +x scripts/download_config.sh
chmod +x scripts/upload_config.sh
```

## 使い方

### config.yml のダウンロード

サーバーから `config.yml` をダウンロードして、`server-config/server_config.yml` として保存します。

```bash
./scripts/download_config.sh
```

**機能:**
- サーバーから最新の `config.yml` を取得
- ローカルに `server_config.yml` として保存
- 既存ファイルがある場合は自動バックアップ（タイムスタンプ付き）

### config.yml のアップロード

ローカルで編集した `server_config.yml` をサーバーにアップロードします。

```bash
./scripts/upload_config.sh
```

**機能:**
- アップロード前に確認プロンプトを表示
- サーバー側のファイルを自動バックアップ（タイムスタンプ付き）
- `server_config.yml` を `config.yml` としてアップロード

**注意:** アップロード後は、サーバー側でプラグインまたはサーバーを再読み込みする必要があります。

## ワークフロー例

1. **最新のconfig.ymlを取得**
   ```bash
   ./scripts/download_config.sh
   ```

2. **ローカルで編集**
   ```bash
   # お好みのエディタで編集
   vim server-config/server_config.yml
   ```

3. **サーバーにアップロード**
   ```bash
   ./scripts/upload_config.sh
   ```

4. **サーバー側で反映**
   ```bash
   # サーバーにSSH接続して
   ssh user@server

   # プラグインをリロード（Minecraftサーバーコンソールまたはゲーム内で）
   /tofunomics reload
   ```

## トラブルシューティング

### SSH接続エラー

**エラー:** `Permission denied (publickey)`

**解決策:**
- SSH鍵のパスが正しいか確認
- SSH鍵の権限を確認: `chmod 600 ~/.ssh/id_rsa`
- パスワード認証を使用する場合は、`.server-config.env` の `SSH_KEY_PATH` をコメントアウト

### ファイルが見つからないエラー

**エラー:** `No such file or directory`

**解決策:**
- サーバー上のパスが正しいか確認
- `.server-config.env` の `SERVER_CONFIG_PATH` を確認
- サーバーにSSH接続して実際のパスを確認

### アップロード後に設定が反映されない

**解決策:**
- サーバー側でプラグインをリロード: `/tofunomics reload`
- または、サーバーを再起動

## セキュリティ

- `.server-config.env` は `.gitignore` に追加されており、Gitにコミットされません
- SSH鍵は安全に管理してください
- サーバーの接続情報を他人と共有しないでください

## バックアップ

- ダウンロード時: 既存の `server_config.yml` がタイムスタンプ付きでバックアップされます
- アップロード時: サーバー側の `config.yml` がタイムスタンプ付きでバックアップされます

バックアップファイルは以下の形式で保存されます：
```
config.yml.backup.20251017_143025
```
