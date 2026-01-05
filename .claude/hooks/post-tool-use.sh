#!/bin/bash

# 変更されたファイルパスが引数として渡されます
CHANGED_FILES="$@"

# ファイルがない場合は終了
if [ -z "$CHANGED_FILES" ]; then
    exit 0
fi

# Pythonファイルの整形 (Black / Ruff)
if echo "$CHANGED_FILES" | grep -q "\.py$"; then
    # プロジェクトで使っている方を有効にしてください
    # black $CHANGED_FILES
    ruff format $CHANGED_FILES
fi

# JS/TS/Vueファイルの整形 (Prettier)
if echo "$CHANGED_FILES" | grep -qE "\.(js|ts|vue|json)$"; then
    npx prettier --write $CHANGED_FILES
fi

# Javaファイルの整形 (Google Java Format 等があれば)
# if echo "$CHANGED_FILES" | grep -q "\.java$"; then
#     ./gradlew spotlessApply  # もしSpotlessプラグインなどを入れていれば
# fi
