#!/bin/bash

# config.ymlダウンロードスクリプト
# サーバーからconfig.ymlをダウンロードしてローカルに保存

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

# ローカルの保存先ディレクトリを作成
LOCAL_DIR="$(dirname "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}")"
mkdir -p "${LOCAL_DIR}"

# SSH鍵のパスを設定（デフォルトは ~/.ssh/id_rsa）
SSH_KEY="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"

echo -e "${YELLOW}サーバーからconfig.ymlをダウンロードしています...${NC}"
echo "  サーバー: ${SERVER_USER}@${SERVER_HOST}"
echo "  リモートパス: ${SERVER_CONFIG_PATH}"
echo "  ローカルパス: ${LOCAL_CONFIG_PATH}"

# 既存ファイルがある場合はバックアップ
if [ -f "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}" ]; then
    BACKUP_PATH="${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}.backup.$(date +%Y%m%d_%H%M%S)"
    echo -e "${YELLOW}既存ファイルをバックアップしています: ${BACKUP_PATH}${NC}"
    cp "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}" "${BACKUP_PATH}"
fi

# SCPでダウンロード
if [ -f "${SSH_KEY}" ]; then
    # SSH鍵を使用
    scp -i "${SSH_KEY}" "${SERVER_USER}@${SERVER_HOST}:${SERVER_CONFIG_PATH}" "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}"
else
    # SSH鍵なし（パスワード認証）
    scp "${SERVER_USER}@${SERVER_HOST}:${SERVER_CONFIG_PATH}" "${PROJECT_ROOT}/${LOCAL_CONFIG_PATH}"
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ ダウンロード完了: ${LOCAL_CONFIG_PATH}${NC}"
else
    echo -e "${RED}✗ ダウンロード失敗${NC}"
    exit 1
fi
