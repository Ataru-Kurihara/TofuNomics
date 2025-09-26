# 📚 TofuNomics ドキュメント

> TofuNomicsワールド専用経済プラグインの完全ドキュメント

## 🎯 目的別ガイド

### 🎮 プレイヤー向け
| ドキュメント | 説明 | 対象者 |
|------------|------|--------|
| **[プレイヤーガイド](user-guide.md)** | 職業システム・経済システムの使い方 | 一般プレイヤー |

### 🛡️ 管理者向け
| ドキュメント | 説明 | 対象者 |
|------------|------|--------|
| **[管理者ガイド](admin/README.md)** | サーバー管理・運用の完全ガイド | サーバー管理者 |
| **[インストールガイド](install-guide.md)** | プラグインの導入・初期設定 | サーバー管理者 |

### 🔧 開発者向け
| ドキュメント | 説明 | 対象者 |
|------------|------|--------|
| **[API リファレンス](api-reference.md)** | 開発者向けAPI仕様書 | プラグイン開発者 |

### 📋 企画・仕様
| ドキュメント | 説明 | 対象者 |
|------------|------|--------|
| **[要件定義書](requirements.md)** | プラグインの要件・仕様 | 企画者・開発者 |
| **[実装計画](implementation-plan.md)** | 開発フェーズ・実装計画 | 開発チーム |

## 🔍 クイックリンク

### 🚀 今すぐ始める
- **プレイヤー**: [基本操作](user-guide.md#基本操作) → [職業選択](user-guide.md#職業システム) → [お金稼ぎ](user-guide.md#経済システム)
- **管理者**: [インストール](install-guide.md) → [基本設定](admin/configuration.md) → [日常運用](admin/daily-operations.md)

### 💡 よくある質問
- **プレイヤー**: [FAQ](user-guide.md#よくある質問)
- **管理者**: [トラブルシューティング](admin/troubleshooting.md)

### 🎛️ 設定・管理
- [コマンド一覧](admin/commands.md)
- [設定ファイル](admin/configuration.md)
- [NPC管理](admin/npc-management.md)

## 📊 システム概要

### 🏗️ アーキテクチャ
```
TofuNomics プラグイン
├── 💰 経済システム (金塊ベース通貨)
├── 👷 職業システム (8つの専門職)
├── 🤖 NPCシステム (銀行・取引・食料)
├── 📈 レベル・スキルシステム
├── 🎯 クエストシステム
└── 📊 統計・監視システム
```

### 🎮 主要機能
- **8つの職業**: 鉱夫、木こり、農家、漁師、鍛冶屋、錬金術師、魔術師、建築家
- **経済システム**: 金塊通貨、送金、銀行、取引
- **NPCシステム**: 自動取引、銀行サービス、食料販売
- **スキルシステム**: レベル連動の特別能力
- **リアルタイム統計**: スコアボード、パフォーマンス監視

## 🔄 更新履歴

### 最新版: v${project.version}
- 📅 更新日: 2024年
- 🆕 新機能: [実装計画を確認](implementation-plan.md)

## 🤝 サポート・コミュニティ

### 📞 連絡先
- **GitHub Issues**: [問題報告・機能要求](https://github.com/tofu-server/TofuNomics/issues)
- **開発チーム**: TofuNomics Team

### 📖 貢献方法
1. **バグ報告**: [Issues](https://github.com/tofu-server/TofuNomics/issues) でバグを報告
2. **機能提案**: [Issues](https://github.com/tofu-server/TofuNomics/issues) で新機能を提案
3. **ドキュメント改善**: Pull Request でドキュメント改善を提案

## 📝 ライセンス・利用規約

- **対象**: tofunomicsワールド専用
- **Minecraft版**: 1.16+対応
- **更新日**: 2024年

---

## 🗂️ ディレクトリ構造
```
docs/
├── README.md               # このファイル（メイン索引）
├── user-guide.md           # プレイヤー向けガイド
├── install-guide.md        # インストール・セットアップ
├── api-reference.md        # 開発者向けAPI
├── requirements.md         # 要件定義
├── implementation-plan.md  # 実装計画
└── admin/                  # 管理者向けドキュメント
    ├── README.md           # 管理者ガイド索引
    ├── overview.md         # プラグイン概要
    ├── commands.md         # コマンド一覧
    ├── configuration.md    # 設定ファイル解説
    ├── npc-management.md   # NPC管理
    ├── job-system.md       # 職業システム
    ├── economy.md          # 経済システム
    ├── troubleshooting.md  # トラブルシューティング
    └── daily-operations.md # 日常運用
```