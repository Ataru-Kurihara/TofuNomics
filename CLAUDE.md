# TofuNomics プロジェクト固有ルール

## サーバーconfig.yml修正ルール

### 【最重要】config.yml上書き禁止ルール

**絶対に禁止される操作：**
- ❌ ローカルのconfig.ymlをサーバー上のconfig.ymlに上書きコピー
- ❌ ローカルのconfig.yml全体をサーバーにアップロード
- ❌ `scp config.yml server:/path/to/config.yml` のような全体コピー
- ❌ Git経由でサーバーのconfig.ymlを上書き

**理由：**
- サーバー上にはNPC配置座標、銀行位置、取引所位置など、ゲーム内で実際に設定された情報が含まれている
- ローカル環境のconfig.ymlにはこれらの情報がないため、上書きすると全て消失する
- **一度消失すると、全NPCを手動で再配置する必要があり、復旧に膨大な時間がかかる**

**正しい修正方法：**
- ✅ サーバー上のconfig.ymlを直接編集（vim、nano等で該当箇所のみ修正）
- ✅ 修正する設定項目のみをピンポイントで変更
- ✅ 必要に応じてサーバーのconfig.ymlをローカルにダウンロードして確認

---

### サーバーconfig.yml修正時の手順

#### 開発環境での修正手順

1. **修正前のバックアップ作成**
   ```bash
   cp src/main/resources/config.yml src/main/resources/config.yml.backup.$(date +%Y-%m-%d)
   ```

2. **config.ymlを編集**
   - 該当箇所のみを修正
   - インデントに注意（スペース2個）

3. **YAML構文チェック**
   - オンラインバリデーター使用: https://www.yamllint.com/
   - またはコマンド: `yamllint src/main/resources/config.yml`

4. **設定値の妥当性を確認**
   - 数値が適切な範囲内か
   - ワールド名、座標が正しいか
   - プレースホルダーが正しいか（%player%、%amount%など）

5. **変更内容をコミット**
   ```bash
   git add src/main/resources/config.yml
   git commit -m "config: [具体的な変更内容]"
   ```

6. **ユーザーに確認依頼**
   - 「別ターミナルでサーバーを停止してください」
   - 停止確認後、ビルド＆デプロイ

7. **再起動後の動作確認**
   - 変更した設定が正しく反映されているか確認

8. **作業ログに記録**
   - `/Users/kuriharaataru/Desktop/mark/work-logs/TofuNomics/YYYY-MM-DD.md` に記録

---

#### 本番環境（サーバー）での修正手順

1. **【重要】絶対にローカルのconfig.ymlで上書きしない**

2. **開発環境で事前テスト実施**
   - 必ずローカル環境で動作確認してから本番適用

3. **サーバーにSSH接続**
   ```bash
   ssh user@server
   cd /path/to/plugins/TofuNomics/
   ```

4. **修正前のバックアップ作成**
   ```bash
   cp config.yml config.yml.backup.$(date +%Y-%m-%d-%H%M)
   ```

5. **vim/nanoで該当箇所のみを直接編集**
   ```bash
   vim config.yml
   # または
   nano config.yml
   ```
   - 該当行のみを修正
   - **絶対に全体をコピペしない**

6. **YAML構文チェック**
   ```bash
   yamllint config.yml
   # またはPythonで簡易チェック
   python3 -c "import yaml; yaml.safe_load(open('config.yml'))"
   ```

7. **設定値の妥当性を厳密に確認**
   - 数値、文字列、boolean値が正しいか
   - **サーバー固有設定（NPC座標等）が消えていないか必ず確認**

8. **変更内容をユーザーに確認・承認依頼**
   - 変更箇所を明確に説明
   - 影響範囲を伝える

9. **ユーザー承認後、サーバー再起動を依頼**
   - 「別ターミナルでサーバーを再起動してください」

10. **再起動後の機能テスト実施**
    - 変更した機能が正しく動作するか確認
    - NPCが正常に機能するか確認

11. **問題発生時は即座にバックアップから復元**
    ```bash
    cp config.yml.backup.YYYY-MM-DD-HHmm config.yml
    # サーバー再起動
    ```

12. **作業ログに詳細な変更内容を記録**
    - 変更箇所、変更理由、影響範囲を記録

---

### 必須チェック項目

#### YAML構文チェック
- [ ] インデントが正しいか（スペース2個）
- [ ] コロン（:）の後にスペースがあるか
- [ ] ハイフン（-）の位置が正しいか
- [ ] 文字列のクォート（"）が必要な箇所で使われているか

#### 設定値の妥当性チェック
- [ ] 数値が適切な範囲内か（price、max値、min値など）
- [ ] ワールド名が正しいか
- [ ] 座標（x, y, z）が正しいか
- [ ] メッセージ内のプレースホルダーが正しいか（%player%、%amount%など）
- [ ] boolean値が true/false で正しく設定されているか
- [ ] 配列・リストの構文が正しいか

#### **サーバー固有設定の保護チェック（最重要）**
- [ ] NPC配置情報が消えていないか
- [ ] 銀行・ATMの位置情報が消えていないか
- [ ] 取引所の配置情報が消えていないか
- [ ] その他サーバーで設定された座標情報が消えていないか

---

### 危険な設定項目（サーバーで設定される項目、ローカルで触らない）

以下の項目はサーバー上で実際のゲーム内設定として登録されるため、**ローカル環境から上書きしてはいけません**：

#### NPC関連
- `npc_system.bank_npcs.locations[]` - 銀行NPCの配置座標
- `npc_system.trading_npcs` - 取引NPCの配置情報
- `npc_system.food_npc.locations[]` - 食料NPCの配置座標
- `npc_system.processing_npc.locations[]` - 加工NPCの配置座標
- `npc_system.trading_posts[]` - 取引所の配置情報

#### 経済システム関連
- `economy.location_restrictions.banks[]` - 銀行の位置情報
- `economy.location_restrictions.atms[]` - ATMの位置情報

#### その他
- ワールド固有の座標情報全般

**これらの設定を変更する必要がある場合：**
1. サーバー上で直接編集
2. またはゲーム内コマンドで設定変更
3. **絶対にローカルのconfig.ymlから上書きしない**

---

### よくある失敗例と対策

#### ❌ 失敗例1: ローカルのconfig.ymlをそのままサーバーにコピー
```bash
# これは絶対にやってはいけない
scp src/main/resources/config.yml server:/path/to/plugins/TofuNomics/config.yml
```
**結果:** サーバー上の全NPC配置情報が消失

**正しい方法:**
```bash
# サーバーに接続して直接編集
ssh server
vim /path/to/plugins/TofuNomics/config.yml
# 該当箇所のみ修正
```

---

#### ❌ 失敗例2: 新機能追加時にconfig.yml全体を更新
```bash
# 新機能の設定項目を追加したいがために全体をコピー
git pull
# サーバー上でもgit pullしてconfig.ymlを上書き
```
**結果:** サーバー固有設定が全て消失

**正しい方法:**
```bash
# 新機能の設定項目のみをサーバーのconfig.ymlに追記
ssh server
vim /path/to/plugins/TofuNomics/config.yml
# 新しいセクションを末尾に追加
# または既存セクションに新しい項目を追加
```

---

#### ❌ 失敗例3: バックアップを取らずに修正
```bash
vim config.yml
# 直接修正して保存
```
**結果:** 間違えた場合に元に戻せない

**正しい方法:**
```bash
# 必ずバックアップを取ってから修正
cp config.yml config.yml.backup.$(date +%Y-%m-%d-%H%M)
vim config.yml
```

---

## その他のプロジェクト固有ルール

### ビルド・デプロイルール
- ビルド前に必ず `mvn clean` を実行
- デプロイ前にサーバーが停止していることを確認
- デプロイ後は必ずサーバーログを確認

### テストルール
- 新機能追加時は必ずユニットテストを作成
- カバレッジ70%以上を維持

### コミットルール
- コミットメッセージは日本語で記述
- プレフィックスを使用: feat, fix, docs, refactor, test, config

### 作業ログルール
- プルリクエスト作成時は必ず作業ログに記録
- config.yml変更時も必ず作業ログに記録
