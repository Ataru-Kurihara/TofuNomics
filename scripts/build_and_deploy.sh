#!/bin/bash

# ビルド＆デプロイ統合スクリプト
# Mavenビルドとサーバーへのデプロイを一括実行

set -e  # エラーが発生したら即座に終了

# スクリプトのディレクトリを取得
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 色付きメッセージ用
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  TofuNomics ビルド＆デプロイ${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# ステップ1: Mavenビルド
echo -e "${YELLOW}[1/2] Mavenビルドを開始...${NC}"
echo ""

cd "${PROJECT_ROOT}"

# ビルド実行
if mvn clean package -DskipTests; then
    echo ""
    echo -e "${GREEN}✓ ビルド成功${NC}"
else
    echo ""
    echo -e "${RED}✗ ビルド失敗${NC}"
    echo -e "${YELLOW}エラーを確認して修正してください${NC}"
    exit 1
fi

# JARファイルの存在確認
JAR_FILE="${PROJECT_ROOT}/target/TofuNomics-1.0-SNAPSHOT.jar"
if [ ! -f "${JAR_FILE}" ]; then
    echo ""
    echo -e "${RED}エラー: JARファイルが生成されていません${NC}"
    echo "  期待されるパス: ${JAR_FILE}"
    exit 1
fi

# ファイルサイズを取得
JAR_SIZE=$(du -h "${JAR_FILE}" | cut -f1)
echo -e "${GREEN}  生成されたJARファイル: ${JAR_SIZE}${NC}"
echo ""

# ステップ2: デプロイ
echo -e "${YELLOW}[2/2] サーバーへデプロイ...${NC}"
echo ""

# deploy_plugin.shを実行
DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy_plugin.sh"
if [ ! -f "${DEPLOY_SCRIPT}" ]; then
    echo -e "${RED}エラー: deploy_plugin.sh が見つかりません${NC}"
    echo "  期待されるパス: ${DEPLOY_SCRIPT}"
    exit 1
fi

# deploy_plugin.shに実行権限があるか確認
if [ ! -x "${DEPLOY_SCRIPT}" ]; then
    chmod +x "${DEPLOY_SCRIPT}"
fi

# デプロイスクリプトを実行
"${DEPLOY_SCRIPT}"

# 完了メッセージはdeploy_plugin.shが表示するので、ここでは追加メッセージのみ
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  すべての処理が完了しました${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""
