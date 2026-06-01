#!/bin/bash

# プラグインJARファイルデプロイスクリプト
# ローカルのビルド済みJARファイルをサーバーにアップロード

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
BLUE='\033[0;34m'
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
if [ -z "${SERVER_HOST}" ] || [ -z "${SERVER_USER}" ] || [ -z "${SERVER_PLUGIN_PATH}" ] || [ -z "${LOCAL_JAR_PATH}" ]; then
    echo -e "${RED}エラー: 必須の環境変数が設定されていません${NC}"
    echo "SERVER_HOST, SERVER_USER, SERVER_PLUGIN_PATH, LOCAL_JAR_PATH を .server-config.env に設定してください"
    exit 1
fi

# ローカルJARファイルの存在確認
if [ ! -f "${PROJECT_ROOT}/${LOCAL_JAR_PATH}" ]; then
    echo -e "${RED}エラー: ${LOCAL_JAR_PATH} が見つかりません${NC}"
    echo ""
    echo -e "${YELLOW}先にビルドを実行してください:${NC}"
    echo "  mvn clean package -DskipTests"
    exit 1
fi

# JARファイルのサイズを取得（確認用）
JAR_SIZE=$(du -h "${PROJECT_ROOT}/${LOCAL_JAR_PATH}" | cut -f1)

# SSH鍵のパスを設定（デフォルトは ~/.ssh/id_rsa）
SSH_KEY="${SSH_KEY_PATH:-$HOME/.ssh/id_rsa}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  TofuNomics プラグインデプロイ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}以下の設定でサーバーにデプロイします:${NC}"
echo "  ローカルパス: ${LOCAL_JAR_PATH}"
echo "  ファイルサイズ: ${JAR_SIZE}"
echo "  サーバー: ${SERVER_USER}@${SERVER_HOST}"
echo "  リモートパス: ${SERVER_PLUGIN_PATH}"
echo ""

# デプロイ確認プロンプト
echo ""
read -p "本当にデプロイしますか？ (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}デプロイをキャンセルしました${NC}"
    exit 0
fi

echo ""
echo -e "${YELLOW}JARファイルをアップロードしています...${NC}"

# SCPでアップロード
if [ -f "${SSH_KEY}" ]; then
    # SSH鍵を使用
    scp -i "${SSH_KEY}" "${PROJECT_ROOT}/${LOCAL_JAR_PATH}" "${SERVER_USER}@${SERVER_HOST}:${SERVER_PLUGIN_PATH}"
else
    # SSH鍵なし（パスワード認証）
    scp "${PROJECT_ROOT}/${LOCAL_JAR_PATH}" "${SERVER_USER}@${SERVER_HOST}:${SERVER_PLUGIN_PATH}"
fi

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  ✓ デプロイ完了${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}次の手順:${NC}"
    echo "  プラグインをリロードしてください:"
    echo ""
    echo "    方法1: サーバーコンソールで実行"
    echo "      /tofunomics reload"
    echo ""
    echo "    方法2: 全プラグインリロード"
    echo "      /reload confirm"
    echo ""
    echo -e "${BLUE}  ※ サーバーを停止する必要はありません（ホットリロード対応）${NC}"
    echo ""
    echo -e "${YELLOW}動作確認:${NC}"
    echo "  - 夜間（Minecraft時間22:00以降）に24時間営業NPCをテスト"
    echo "  - サーバーログで「緊急モードNPCのため、営業時間チェックをスキップ」を確認"
    echo ""
else
    echo ""
    echo -e "${RED}✗ デプロイ失敗${NC}"
    exit 1
fi
