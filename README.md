# TofuNomics

TofuサーバーMinecraft専用の経済・職業システムプラグイン

## 概要

TofuNomicsは、8つの専門職業とレベルシステム、プレイヤー主導の経済活動を提供するMinecraftプラグインです。

## 技術スタック

- **Java**: 17
- **Minecraft**: Spigot/Paper 1.16.5
- **ビルドツール**: Maven
- **データベース**: SQLite + HikariCP
- **統合**: WorldGuard 7.0.7

## 主要機能

- 8つの専門職業システム
- プレイヤー経済システム
- WorldGuard統合による土地管理
- 住居賃貸システム
- NPC取引システム

## ビルド

```bash
mvn clean package
```

## テスト

```bash
mvn test
```

## ライセンス

このプロジェクトはオープンソースプロジェクトです。

## 貢献

貢献方法については [CONTRIBUTING.md](CONTRIBUTING.md) をご覧ください。
