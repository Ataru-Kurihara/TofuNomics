# TofuNomics 推奨コマンド集

## ビルドとコンパイル

### 基本的なMavenコマンド
```bash
# プロジェクトのクリーンビルド
mvn clean compile

# 完全ビルド（テスト含む）
mvn clean package

# テスト実行
mvn test

# テストスキップしてビルド
mvn clean package -DskipTests

# 依存関係の更新
mvn dependency:resolve
```

### 開発用コマンド
```bash
# 開発用の高速ビルド
mvn compile

# 特定のテストクラスのみ実行
mvn test -Dtest=PlayerDAOTest

# JaCoCo テストカバレッジレポート生成
mvn test jacoco:report
```

## テストとカバレッジ

### テスト実行
```bash
# 全テスト実行
mvn test

# 特定パッケージのテスト実行
mvn test -Dtest="org.tofu.tofunomics.dao.*"

# テスト結果の確認
ls target/surefire-reports/

# カバレッジレポートの表示
open target/site/jacoco/index.html
```

### テストファイル確認
```bash
# テストクラス一覧
find src/test -name "*.java"

# テスト結果ファイル
ls target/surefire-reports/*.txt
```

## 開発とデバッグ

### ファイル検索
```bash
# Javaファイル検索
find src -name "*.java" | head -10

# 設定ファイル確認
cat src/main/resources/plugin.yml
cat src/main/resources/config.yml

# 特定クラスの検索
find src -name "*Manager.java"
find src -name "*DAO.java"
```

### ログとデバッグ
```bash
# ビルドログの確認
mvn clean compile -X > build.log 2>&1

# エラー詳細表示
mvn compile -e
```

## プラグイン配布

### JARファイル生成
```bash
# 配布用JARファイル作成
mvn clean package

# シェードプラグイン使用（依存関係含む）
mvn clean package shade:shade

# 生成されたJARファイルの確認
ls target/*.jar
```

### プラグイン配置
```bash
# Minecraftサーバーのpluginsフォルダへコピー
cp target/TofuNomics-1.0-SNAPSHOT.jar /path/to/server/plugins/

# ファイルサイズ確認
ls -lh target/TofuNomics-1.0-SNAPSHOT.jar
```

## コード品質管理

### コード解析
```bash
# Maven依存関係ツリー表示
mvn dependency:tree

# プロジェクト情報表示
mvn help:describe -Dplugin=help

# 有効なプロファイル確認
mvn help:active-profiles
```

### クリーンアップ
```bash
# ビルド成果物のクリーンアップ
mvn clean

# IDE設定ファイルのクリーンアップ
rm -rf .idea/
rm *.iml
```

## 設定とメンテナンス

### 設定確認
```bash
# Maven設定確認
mvn help:effective-pom

# Java環境確認
java -version
javac -version

# Maven環境確認
mvn --version
```

### プロジェクト管理
```bash
# プロジェクト構造確認
tree src/ -I target

# ディスク使用量確認
du -sh target/

# 最新の変更ファイル確認
find src -name "*.java" -mtime -1
```

## Darwin (macOS) 固有のコマンド

### システム情報
```bash
# macOS バージョン確認
sw_vers

# Java インストール場所確認
/usr/libexec/java_home -V

# Homebrew でインストールしたMaven確認
which mvn
brew list maven
```

### ファイル操作
```bash
# Finder で結果を開く
open target/site/jacoco/

# ファイル権限確認（macOS向け）
ls -la@ src/main/resources/

# 隠しファイル表示
ls -la
```

## 緊急時・トラブルシューティング

### エラー対応
```bash
# Maven キャッシュクリア
mvn dependency:purge-local-repository

# 強制的な依存関係更新
mvn clean install -U

# ビルドエラー詳細表示
mvn clean compile -X -e > error.log 2>&1
```

### バックアップ
```bash
# 重要ファイルのバックアップ
cp pom.xml pom.xml.backup
cp -r src/ src.backup/

# 設定ファイルバックアップ
cp src/main/resources/config.yml config.yml.backup
```

## 注意事項
- Java 8+ が必要です
- Spigot 1.16.5 環境でのテストが推奨されます
- テスト実行時はH2データベースが使用されます
- 本番環境ではSQLiteが使用されます