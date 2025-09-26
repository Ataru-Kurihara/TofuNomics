# TofuNomics柔軟通貨価値システム実装記録

## 実装背景
- 問題：$100が100Tofuコインでプレイヤーのインベントリが嵩張る
- 解決策：1コインの価値を柔軟に変更できるシステムの実装

## 実装内容

### 1. 設定ファイル更新（config.yml）
```yaml
economy:
  currency:
    coin_value: 10.0      # 1コイン=$10（デフォルト）
    dynamic_value: true   # 動的価値変更を有効
    min_value: 0.1       # 最小価値
    max_value: 1000.0    # 最大価値
```

### 2. ConfigManagerクラス拡張
- `getCoinValue()` - 現在のコイン価値取得
- `setCoinValue(double value)` - コイン価値設定（範囲チェック付き）
- `getMinCoinValue()` / `getMaxCoinValue()` - 範囲制限値取得
- `isDynamicValueEnabled()` - 動的価値変更の有効性確認

### 3. CurrencyConverterクラス改修
- ConfigManagerの依存関係追加
- `convertBalanceToNuggets()` - 残高をコイン数に変換（可変価値対応）
- `convertNuggetsToBalance()` - コイン数を残高に変換（可変価値対応）

### 4. ItemManagerクラス改修
- 動的lore生成機能追加
- コインのloreに現在価値を表示「1コイン = $XX.X」
- ConfigManager依存関係追加

### 5. EcoCommandクラス拡張
新しい管理コマンド追加：
- `/eco setcoinvalue <値>` - コイン価値設定
- `/eco getcoinvalue` - 現在価値表示
- `/eco resetcoinvalue` - デフォルト値リセット

### 6. BankGUIクラス修正
- 引き出し・預け入れ処理でtofu coin使用を確実に

## 技術的特徴
- リアルタイム価値変更
- 範囲制限による安全性
- レガシーコイン対応
- 動的lore更新
- 管理者向けコマンド

## 効果
- インベントリ負荷軽減（$100 = 10コイン）
- 柔軟な経済バランス調整
- ゲーム進行に応じた価値調整可能

## コンパイル状況
全てのエラーを修正し、正常にコンパイル完了