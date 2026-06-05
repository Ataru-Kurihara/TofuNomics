#!/usr/bin/env python3
"""
trading_posts の items リストを職業別に一括拡充するスクリプト

【目的】
  server_config.yml（サーバーからdownloadしたconfig.yml）の各取引所NPCの
  `items` リストに、職業ごとの売却対象アイテムを安全に追加する。

【安全設計】
  - items の「追加」のみ。既存アイテムは保持（重複は追加しない）。
  - 以下は一切読み書きしない＝保護される:
      x / y / z / id / name / description / accepted_jobs / purchase_prices
  - accepted_jobs が ["all"] の取引所（総合取引所）はスキップ。
  - items が未指定 / [] の取引所はスキップ（= 全アイテム対応のため変更不要）。

【使い方】
  1. ./scripts/download_config.sh でサーバーのconfig.ymlを取得
  2. python3 scripts/bulk_update_trading_items.py src/main/resources/server_config.yml
  3. yamllint で構文チェック
  4. git diff src/main/resources/server_config.yml で座標行にゼロ差分を確認
  5. ./scripts/upload_config.sh でアップロード
  6. /tofunomics reload

  --dry-run を付けると変更内容のみ表示し、ファイルは書き換えない。
"""
import sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAMLが必要です: pip install pyyaml")


# 職業ごとに items へ追加するアイテム（item_prices に存在するキーのみ）
JOB_ITEMS = {
    "miner": [
        "coal", "coal_ore", "iron_ore", "iron_ingot", "gold_ore", "gold_ingot",
        "diamond", "diamond_ore", "emerald", "emerald_ore", "redstone",
        "lapis_lazuli", "copper_ore", "copper_ingot",
        "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_gold_ore",
        "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_redstone_ore",
        "deepslate_lapis_ore", "deepslate_copper_ore", "nether_gold_ore",
        "nether_quartz_ore", "ancient_debris", "netherite_scrap", "netherite_ingot",
        "raw_iron", "raw_gold", "raw_copper", "quartz", "amethyst_shard",
    ],
    "woodcutter": [
        "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log",
        "dark_oak_log", "mangrove_log", "cherry_log",
        "oak_planks", "birch_planks", "spruce_planks", "jungle_planks",
        "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks",
        "crimson_planks", "warped_planks", "bamboo_planks", "bamboo", "stick",
        "stripped_oak_log", "stripped_birch_log", "stripped_spruce_log",
        "stripped_jungle_log", "stripped_acacia_log", "stripped_dark_oak_log",
        "stripped_mangrove_log", "stripped_cherry_log",
        "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling",
        "acacia_sapling", "dark_oak_sapling", "cherry_sapling", "mangrove_propagule",
    ],
    "farmer": [
        "wheat", "potato", "carrot", "beetroot", "pumpkin", "melon", "sugar_cane",
        "cocoa_beans", "nether_wart", "beef", "porkchop", "chicken", "mutton",
        "leather", "milk_bucket", "egg",
        "melon_slice", "glow_berries", "apple", "wheat_seeds", "beetroot_seeds",
        "pumpkin_seeds", "melon_seeds", "bone_meal", "bone", "rabbit", "rabbit_hide",
        "feather", "hay_block", "honeycomb", "honey_bottle", "brown_mushroom",
        "red_mushroom", "poppy", "dandelion", "blue_orchid", "allium", "cornflower",
        "sunflower", "wither_rose",
    ],
    "fisherman": [
        "cod", "salmon", "tropical_fish", "pufferfish", "prismarine_shard",
        "prismarine_crystals", "nautilus_shell", "heart_of_the_sea",
        "cooked_cod", "cooked_salmon", "kelp", "dried_kelp", "sea_pickle",
        "seagrass", "ink_sac", "glow_ink_sac", "sponge", "wet_sponge", "turtle_egg",
        "turtle_scute", "cod_bucket", "tropical_fish_bucket", "lily_pad",
        "fishing_rod", "name_tag", "saddle",
    ],
    "blacksmith": [
        "iron_ingot", "gold_ingot", "diamond", "netherite_ingot",
        "iron_pickaxe", "iron_sword", "iron_axe", "iron_shovel", "iron_hoe",
        "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
        "diamond_pickaxe", "diamond_sword", "diamond_axe", "diamond_shovel",
        "diamond_hoe", "diamond_helmet", "diamond_chestplate", "diamond_leggings",
        "diamond_boots", "chainmail_helmet", "chainmail_chestplate",
        "chainmail_leggings", "chainmail_boots", "golden_helmet", "golden_chestplate",
        "golden_leggings", "golden_boots", "golden_sword", "golden_pickaxe",
        "golden_axe", "golden_shovel", "golden_hoe", "netherite_helmet",
        "netherite_chestplate", "netherite_leggings", "netherite_boots",
        "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel",
        "netherite_hoe", "shield", "bow", "crossbow", "flint_and_steel", "shears",
        "bucket",
    ],
    "alchemist": [
        "blaze_powder", "glowstone_dust", "fermented_spider_eye",
        "glistering_melon_slice", "magma_cream", "blaze_rod", "ender_pearl",
        "ghast_tear", "experience_bottle", "spider_eye", "golden_carrot",
        "rabbit_foot", "phantom_membrane", "nether_wart_block", "sugar",
        "glass_bottle", "dragon_breath", "turtle_helmet", "gunpowder", "slime_ball",
        "ender_eye",
    ],
    "enchanter": [
        "experience_bottle", "enchanted_book", "book", "bookshelf", "paper",
        "writable_book", "lapis_lazuli", "lapis_block", "amethyst_block",
        "glowstone", "sea_lantern",
    ],
    "architect": [
        "stone_bricks", "smooth_stone", "quartz_block", "polished_blackstone",
        "bricks", "stone", "cobblestone", "smooth_stone_slab", "chiseled_stone_bricks",
        "cracked_stone_bricks", "mossy_stone_bricks", "deepslate", "cobbled_deepslate",
        "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "tuff", "calcite",
        "dripstone_block", "sandstone", "smooth_sandstone", "red_sandstone",
        "prismarine", "prismarine_bricks", "dark_prismarine", "purpur_block",
        "end_stone", "end_stone_bricks", "nether_bricks", "red_nether_bricks",
        "blackstone", "polished_blackstone_bricks", "gilded_blackstone", "glass",
        "glass_pane", "terracotta", "white_concrete", "quartz_pillar", "quartz_bricks",
        "copper_block", "cut_copper", "oxidized_copper",
    ],
}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--dry-run" in sys.argv
    if not args:
        sys.exit("使い方: python3 bulk_update_trading_items.py <server_config.yml> [--dry-run]")
    path = args[0]

    with open(path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    posts = (cfg.get("npc_system", {}) or {}).get("trading_posts")
    if not posts:
        sys.exit("trading_posts が見つかりません（パス: npc_system.trading_posts）")

    total_added = 0
    changed_posts = 0
    for post in posts:
        jobs = post.get("accepted_jobs") or []
        if jobs == ["all"]:
            continue  # 総合取引所は変更不要
        items = post.get("items")
        if items is None or items == []:
            continue  # 全アイテム対応のため変更不要

        added_here = []
        for job in jobs:
            for item in JOB_ITEMS.get(job, []):
                if item not in items:
                    items.append(item)
                    added_here.append(item)
        if added_here:
            changed_posts += 1
            total_added += len(added_here)
            label = post.get("id") or post.get("name") or "?"
            print(f"[{label}] ({','.join(jobs)}) +{len(added_here)}件: {', '.join(added_here)}")

    print(f"\n合計: {changed_posts}取引所 / {total_added}件追加")

    if dry_run:
        print("--dry-run のためファイルは変更しませんでした。")
        return

    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(cfg, f, allow_unicode=True, sort_keys=False, default_flow_style=False)
    print(f"書き込み完了: {path}")
    print("次の手順: yamllint → git diff で座標ゼロ差分確認 → ./scripts/upload_config.sh")


if __name__ == "__main__":
    main()
