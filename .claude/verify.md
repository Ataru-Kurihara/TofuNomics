# Verify Command (Minecraft Plugin)
作成・修正したプラグインコードの整合性を検証します。

## 手順
エラーが発生した場合は、ログを読み解き、**コードを修正してビルドが成功するまで**ループを回してください。

### 1. Code Consistency Check
- `plugin.yml` の確認:
  - `main` クラスのパスが実際のJavaファイルと一致しているか確認。
  - `commands` や `permissions` がコード内の定義と矛盾していないか確認。

### 2. Java Build & Test
- ビルドツール: Maven
- アクション:
  1. クリーンビルド: `./mvn clean package`
  2. テスト実行: `./mvn test` 
### 3. Common Pitfalls Check (目視確認代行)
- Spigot/Paper APIのバージョンと、`build.gradle` の依存関係が一致しているか確認。
- イベントリスナー (`@EventHandler`) が `onEnable()` で登録されているかコードを確認。

## 完了条件
- ビルドが `BUILD SUCCESSFUL` で終了すること。
- 重大な警告（Deprecationなど）があれば報告すること。
