#!/bin/bash

# config.ymlアップロードスクリプト
# ローカルのserver_config.ymlをサーバーにアップロード

set -e  # エラーが発生したら即座に終了

# スクリプトのディレクトリを取得
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 設定ファイルのパス
CONFIG_FILE="${PROJECT_ROOT}/.server-config.env"

# 色付きメッセージ用
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 設定ファイルの存在確認
if [ ! -f "${CONFIG_FILE}" ]; then
    echo -e "${RED}エラー: .server-config.env ファイルが見つかりません${NC}"
    echo -e "${YELLOW}以下の手順で設定ファイルを作成してください:${NC}"
    echo "  1. cp .server-config.env.example .server-config.env"
    echo "  2. .server-config.env を編集して実際の接続情報を入力"
    exit 1
fi

# 設定ファイルを読み込み
source "${CONFIG_FILE}"

# 必須変数のチェック
if [ -z "${SERVER_HOST}" ] || [ -z "${SERVER_USER}" ] || [ -z "${SERVER_CONFIG_PATH}" ] || [ -z "${LOCAL_CONFIG_PATH}" ]; then
    echo -e "${RED}エラー: 必須の環境変数が設定されていません${NC}"
    echo "SERVER_HOST, SERVER_USER, SERVER_CONFIG_PATH, LOCAL_CONFIG_PATH を .server-config.env に設定してください"
    exit 1
fi

# ローカルファイルの存在確認
if [ ! -f "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}" ]; then
    echo -e "${RED}エラー: ${LOCAL_CONFIG_PATH} が見つかりません${NC}"
    echo "先に ./scripts/download_config.sh を実行してファイルをダウンロードするか、"
    echo "手動でファイルを作成してください。"
    exit 1
fi

# SSH鍵のパスを設定（デフォルトは ~/.ssh/id_rsa）
SSH_KEY="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"

echo -e "${YELLOW}以下の設定でサーバーにアップロードします:${NC}"
echo "  ローカルパス: ${LOCAL_CONFIG_PATH}"
echo "  サーバー: ${SERVER_USER}@${SERVER_HOST}"
echo "  リモートパス: ${SERVER_CONFIG_PATH}"
echo ""

# 確認プロンプト
read -p "本当にアップロードしますか？ (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}アップロードをキャンセルしました${NC}"
    exit 0
fi

echo -e "${YELLOW}サーバー側のファイルをバックアップしています...${NC}"

# サーバー側のバックアップ
BACKUP_NAME="config.yml.backup.$(date +%Y%m%d_%H%M%S)"
if [ -f "${SSH_KEY}" ]; then
    ssh -i "${SSH_KEY}" "${SERVER_USER}@${SERVER_HOST}" "cp ${SERVER_CONFIG_PATH} ${SERVER_CONFIG_PATH}.backup.$(date +%Y%m%d_%H%M%S) 2>/dev/null || true"
else
    ssh "${SERVER_USER}@${SERVER_HOST}" "cp ${SERVER_CONFIG_PATH} ${SERVER_CONFIG_PATH}.backup.$(date +%Y%m%d_%H%M%S) 2>/dev/null || true"
fi

echo -e "${YELLOW}ファイルをアップロードしています...${NC}"

# SCPでアップロード
if [ -f "${SSH_KEY}" ]; then
    # SSH鍵を使用
    scp -i "${SSH_KEY}" "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}" "${SERVER_USER}@${SERVER_HOST}:${SERVER_CONFIG_PATH}"
else
    # SSH鍵なし（パスワード認証）
    scp "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}" "${SERVER_USER}@${SERVER_HOST}:${SERVER_CONFIG_PATH}"
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ アップロード完了${NC}"
    echo -e "${YELLOW}※ サーバー側の設定を反映するには、プラグインまたはサーバーの再読み込みが必要です${NC}"
else
    echo -e "${RED}✗ アップロード失敗${NC}"
    exit 1
fi
