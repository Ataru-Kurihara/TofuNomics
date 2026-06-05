#!/usr/bin/env python3
"""
ローカルconfig.ymlの「値ブロック」をサーバーからdownloadしたserver_config.ymlへ
安全にマージするスクリプト（NPC座標等のサーバー固有設定を保護）。

【マージ対象（すべて追加・更新のみ。サーバー固有設定は保護）】
  - npc_system.item_prices            : ローカルの全キーを追加（既存値は上書きしない）
  - npc_system.food_npc.food_items    : 同上
  - npc_system.processing_npc.wood_types : ローカルの全エントリを追加
  - npc_system.processing_npc.planks_per_log : ローカル値を設定（未設定時のみ）
  - npc_system.food_npc.npc_types.{greengrocer,specialty}.items : ローカルのitemsを和集合
  - npc_system.trading_posts[].items  : 職業別アイテムを追加（bulkロジック）

【保護（読みのみ／一切変更しない）】
  - 全NPC座標(x/y/z) / id / name / description / accepted_jobs / purchase_prices
  - food_npc.locations / bank_npcs / その他サーバー固有設定

使い方:
  python3 scripts/merge_config_to_server.py \
      src/main/resources/config.yml src/main/resources/server_config.yml [--dry-run]
"""
import os
import sys

# どのカレントディレクトリから実行してもbulkスクリプトをimportできるようにする
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    import yaml
except ImportError:
    sys.exit("PyYAMLが必要です: pip install pyyaml")

# bulkスクリプトの職業別アイテム定義を再利用
from bulk_update_trading_items import JOB_ITEMS


def get(d, *keys):
    for k in keys:
        if not isinstance(d, dict):
            return None
        d = d.get(k)
    return d


def merge_dict_additive(target, source):
    """source のキーを target に追加（既存キーは上書きしない）。追加件数を返す。"""
    added = 0
    for k, v in source.items():
        if k not in target:
            target[k] = v
            added += 1
    return added


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if len(args) < 2:
        sys.exit("使い方: merge_config_to_server.py <local config.yml> <server_config.yml> [--dry-run]")
    local_path, server_path = args[0], args[1]

    local = yaml.safe_load(open(local_path, encoding="utf-8"))
    server = yaml.safe_load(open(server_path, encoding="utf-8"))

    sns = server.setdefault("npc_system", {})
    lns = local.get("npc_system", {})

    # --- item_prices ---
    sip = sns.setdefault("item_prices", {})
    n = merge_dict_additive(sip, lns.get("item_prices", {}))
    print(f"item_prices: +{n}件 (合計{len(sip)})")

    # --- food_items ---
    sfi = sns.setdefault("food_npc", {}).setdefault("food_items", {})
    n = merge_dict_additive(sfi, get(lns, "food_npc", "food_items") or {})
    print(f"food_items: +{n}件 (合計{len(sfi)})")

    # --- wood_types ---
    swt = sns.setdefault("processing_npc", {}).setdefault("wood_types", {})
    n = merge_dict_additive(swt, get(lns, "processing_npc", "wood_types") or {})
    print(f"wood_types: +{n}件 (合計{len(swt)})")

    # --- planks_per_log（未設定時のみ設定）---
    spn = sns["processing_npc"]
    if "planks_per_log" not in spn:
        spn["planks_per_log"] = get(lns, "processing_npc", "planks_per_log") or 4
        print(f"planks_per_log: 設定 {spn['planks_per_log']}")
    else:
        print(f"planks_per_log: 既存維持 {spn['planks_per_log']}")

    # --- npc_types greengrocer / specialty の items 和集合 ---
    s_types = get(sns, "food_npc", "npc_types") or {}
    l_types = get(lns, "food_npc", "npc_types") or {}
    for t in ("greengrocer", "specialty"):
        s_t = s_types.get(t)
        l_t = l_types.get(t)
        if not s_t or not l_t:
            continue
        s_items = s_t.setdefault("items", [])
        added = [it for it in l_t.get("items", []) if it not in s_items]
        s_items.extend(added)
        print(f"npc_types.{t}.items: +{len(added)}件 {added}")

    # --- trading_posts items（職業別追加）---
    posts = sns.get("trading_posts") or []
    total_added, changed = 0, 0
    for post in posts:
        jobs = post.get("accepted_jobs") or []
        if jobs == ["all"]:
            continue
        items = post.get("items")
        if items is None or items == []:
            continue
        added_here = 0
        for job in jobs:
            for item in JOB_ITEMS.get(job, []):
                if item not in items:
                    items.append(item)
                    added_here += 1
        if added_here:
            changed += 1
            total_added += added_here
    print(f"trading_posts: {changed}取引所 / +{total_added}件")

    if dry_run:
        print("\n--dry-run のためファイルは変更しませんでした。")
        return

    with open(server_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(server, f, allow_unicode=True, sort_keys=False, default_flow_style=False)
    print(f"\n書き込み完了: {server_path}")


if __name__ == "__main__":
    main()
