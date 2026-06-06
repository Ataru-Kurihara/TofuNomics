#!/usr/bin/env python3
"""
trading_posts の purchase_prices を職業別に一括刷新するスクリプト

【目的】
  server_config.yml（サーバーからdownloadしたconfig.yml）の各取引所NPCの
  `purchase_prices`（NPCがプレイヤーに販売するアイテムと価格）を、
  職業ごとの設計に基づいて刷新する。

【設計方針】
  - 各職業NPCを「仕事を回すための道具・消耗品の補給所」に再設計。
  - 価格はやや割高（売却基準価格の概ね1.5〜2倍。自給を基本とする補助的供給）。
  - 同職業の全NPCに同一の purchase_prices を適用（上書き刷新）。

【安全設計】
  - 変更するのは各取引所の `purchase_prices` のみ。
  - 以下は一切読み書きしない＝保護される:
      x / y / z / id / name / description / accepted_jobs / items / emergency_mode 等
  - accepted_jobs が ["all"] の取引所:
      * 'cobblestone' キー（テスト残骸）があれば削除。
      * それ以外の購入設定（24時間ショップ等）は維持。
  - 書き込み後、purchase_prices 以外の全フィールドが不変であることを自動検証。

【使い方】
  1. ./scripts/download_config.sh でサーバーのconfig.ymlを取得
  2. python3 scripts/bulk_update_purchase_prices.py src/main/resources/server_config.yml --dry-run
  3. python3 scripts/bulk_update_purchase_prices.py src/main/resources/server_config.yml
  4. yamllint src/main/resources/server_config.yml
  5. ./scripts/upload_config.sh でアップロード
  6. /tofunomics reload

  --dry-run を付けると変更内容のみ表示し、ファイルは書き換えない。
"""
import copy
import sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAMLが必要です: pip install pyyaml")


# 職業ごとの purchase_prices（NPCがプレイヤーに販売する: アイテム→価格）
# 価格はやや割高（売却基準の1.5〜2倍）。wooden/stone系の初期道具は残して値下げ。
JOB_PURCHASE = {
    "miner": {
        "torch": 1, "iron_pickaxe": 25, "bucket": 8, "ladder": 1, "rail": 2,
        "wooden_pickaxe": 5, "stone_pickaxe": 8,
    },
    "woodcutter": {
        "iron_axe": 25, "oak_sapling": 1.5, "birch_sapling": 1.5,
        "spruce_sapling": 1.5, "bone_meal": 1,
        "wooden_axe": 5, "stone_axe": 8,
    },
    "farmer": {
        "wheat_seeds": 1, "beetroot_seeds": 1, "bone_meal": 1, "iron_hoe": 18,
        "bucket": 8, "wooden_hoe": 5, "stone_hoe": 8,
    },
    "fisherman": {
        "fishing_rod": 6, "string": 4, "oak_boat": 5, "name_tag": 50,
    },
    "blacksmith": {
        "coal": 3, "furnace": 5, "anvil": 100, "grindstone": 15,
        "flint_and_steel": 4,
    },
    "alchemist": {
        "glass_bottle": 2, "brewing_stand": 100, "blaze_powder": 15,
        "nether_wart": 4, "glowstone_dust": 5,
    },
    "enchanter": {
        "book": 4, "lapis_lazuli": 4, "bookshelf": 12,
        "experience_bottle": 25, "paper": 1,
    },
    "architect": {
        "stone": 1, "glass": 1, "white_concrete": 1.5, "sandstone": 1,
        "scaffolding": 10, "lantern": 5, "torch": 2,
    },
}

# all取引所から削除する残骸キー
ALL_REMOVE_KEYS = ["cobblestone"]


def label_of(post):
    return post.get("id") or post.get("name") or "?"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if not args:
        sys.exit("使い方: python3 bulk_update_purchase_prices.py <server_config.yml> [--dry-run]")
    path = args[0]

    with open(path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    posts = (cfg.get("npc_system", {}) or {}).get("trading_posts")
    if not posts:
        sys.exit("trading_posts が見つかりません（パス: npc_system.trading_posts）")

    # 検証用に変更前を保持
    before = copy.deepcopy(cfg)

    changed = 0
    for post in posts:
        jobs = post.get("accepted_jobs") or []
        label = label_of(post)

        if jobs == ["all"]:
            pp = post.get("purchase_prices") or {}
            removed = [k for k in ALL_REMOVE_KEYS if k in pp]
            for k in removed:
                del pp[k]
            if removed:
                changed += 1
                print(f"[{label}] (all) 残骸削除: {removed} / 残り: {dict(pp)}")
            else:
                print(f"[{label}] (all) 変更なし（維持）: {dict(pp)}")
            continue

        # 職業取引所: 対象職業の購入設定をマージして上書き刷新
        new_pp = {}
        for job in jobs:
            new_pp.update(JOB_PURCHASE.get(job, {}))
        if not new_pp:
            print(f"[{label}] ({','.join(jobs)}) 対応定義なし・スキップ")
            continue
        old_pp = post.get("purchase_prices") or {}
        post["purchase_prices"] = new_pp
        changed += 1
        print(f"[{label}] ({','.join(jobs)}) 刷新: {dict(old_pp)} -> {dict(new_pp)}")

    print(f"\n合計: {changed}取引所を更新")

    # --- 安全検証: purchase_prices 以外が不変か ---
    def strip_pp(c):
        c2 = copy.deepcopy(c)
        for p in (c2.get("npc_system", {}) or {}).get("trading_posts", []) or []:
            p.pop("purchase_prices", None)
        return c2

    if strip_pp(before) != strip_pp(cfg):
        sys.exit("【中断】purchase_prices 以外のフィールドに差分が検出されました。書き込みません。")
    print("検証OK: purchase_prices 以外のフィールドは不変（座標・items・accepted_jobs等を保護）")

    if dry_run:
        print("--dry-run のためファイルは変更しませんでした。")
        return

    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(cfg, f, allow_unicode=True, sort_keys=False, default_flow_style=False)
    print(f"書き込み完了: {path}")
    print("次の手順: yamllint → ./scripts/upload_config.sh → /tofunomics reload")


if __name__ == "__main__":
    main()
