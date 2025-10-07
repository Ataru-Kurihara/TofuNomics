# 貢献ガイドライン

TofuNomicsプロジェクトへの貢献に興味を持っていただきありがとうございます！このドキュメントでは、プロジェクトに貢献する方法を説明します。

## 目次
- [行動規範](#行動規範)
- [貢献の種類](#貢献の種類)
- [開発環境のセットアップ](#開発環境のセットアップ)
- [開発ワークフロー](#開発ワークフロー)
- [コーディング規約](#コーディング規約)
- [プルリクエストの作成](#プルリクエストの作成)
- [テストについて](#テストについて)

## 行動規範

このプロジェクトに参加するすべての人は、敬意を持って協力することが期待されます。建設的なフィードバックを提供し、他の貢献者をサポートしてください。

## 貢献の種類

以下のような貢献を歓迎します：

- **バグ修正**: 既存のバグを修正
- **新機能**: 新しい機能の実装
- **ドキュメント改善**: README、コメント、ドキュメントの改善
- **テスト追加**: テストケースの追加や改善
- **パフォーマンス改善**: コードの最適化
- **コードリファクタリング**: コード品質の向上

## 開発環境のセットアップ

### 必要な環境
- Java 8以上
- Maven 3.6以上
- Minecraft Spigot/Paper 1.16.5サーバー（テスト用）
- Git

### セットアップ手順

1. リポジトリをフォーク
```bash
# GitHub上でフォークボタンをクリック
```

2. ローカルにクローン
```bash
git clone https://github.com/YOUR_USERNAME/TofuNomics.git
cd TofuNomics
```

3. 依存関係のインストール
```bash
mvn clean install
```

4. テストの実行
```bash
mvn test
```

## 開発ワークフロー

### 1. ブランチの作成

developブランチから新しいブランチを作成します：

```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

ブランチ命名規則：
- 機能追加: `feature/機能名`
- バグ修正: `fix/バグ名`
- ドキュメント: `docs/内容`
- リファクタリング: `refactor/内容`

### 2. コードの変更

- 小さく、理解しやすい変更を心がける
- コミットメッセージは明確かつ簡潔に
- 必要に応じてテストを追加

### 3. コミット

```bash
git add .
git commit -m "feat: 新機能の説明"
```

コミットメッセージの規約：
- `feat:` 新機能
- `fix:` バグ修正
- `docs:` ドキュメント
- `test:` テスト追加・修正
- `refactor:` リファクタリング
- `perf:` パフォーマンス改善
- `chore:` ビルド・設定変更

### 4. プッシュ

```bash
git push origin feature/your-feature-name
```

## コーディング規約

### Javaコーディング規約

- **インデント**: スペース4つ
- **命名規則**:
  - クラス名: `PascalCase`
  - メソッド名: `camelCase`
  - 定数: `UPPER_SNAKE_CASE`
  - 変数: `camelCase`
- **コメント**:
  - 複雑なロジックには必ずコメントを追加
  - Javadocを使用してパブリックメソッドを文書化
- **行の長さ**: 120文字以内を推奨

### 例

```java
/**
 * プレイヤーの残高を取得する
 *
 * @param player 対象プレイヤー
 * @return 残高（銀貨単位）
 */
public int getBalance(Player player) {
    // キャッシュから取得を試みる
    Integer cachedBalance = balanceCache.get(player.getUniqueId());
    if (cachedBalance != null) {
        return cachedBalance;
    }

    // データベースから取得
    return playerDAO.getBalance(player.getUniqueId());
}
```

## プルリクエストの作成

1. GitHub上でプルリクエストを作成
2. テンプレートに従って情報を記入
3. CIが通過することを確認
4. レビューを待つ
5. フィードバックがあれば対応

### プルリクエストのチェックリスト

- [ ] コードがプロジェクトの規約に従っている
- [ ] すべてのテストが通過している
- [ ] 新機能にはテストを追加した
- [ ] ドキュメントを更新した
- [ ] コミットメッセージが規約に従っている

## テストについて

### テストの実行

```bash
# すべてのテストを実行
mvn test

# カバレッジレポートの生成
mvn jacoco:report
```

### テストの作成

新機能を追加する場合は、必ずテストを追加してください：

```java
@Test
public void testGetBalance() {
    // Given
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());

    // When
    int balance = economyManager.getBalance(player);

    // Then
    assertEquals(0, balance);
}
```

## 質問がある場合

- Issueで質問を投稿
- DiscussionsでコミュニティとDiscussion
- 既存のIssueやPRを確認

## ライセンス

貢献したコードは、プロジェクトのライセンスに従います。

---

貢献していただきありがとうございます！ 🎉
