package org.tofu.tofunomics.food;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 食材バフのカテゴリ定義。
 *
 * 食材を食べた際に付与される職業経験値バフのカテゴリを表す。
 * 各カテゴリは config.yml の {@code food_buff.categories.<configKey>} から
 * 効果量（bonus_percent）と持続時間（duration_seconds）を引くためのキーを持つ。
 *
 * Material -> FoodCategory の対応は仕様であり頻繁に変えないため、
 * 既存の経験値テーブル（JobExperienceManager）と同様にここへハードコードする。
 */
public enum FoodCategory {
    BREAD("bread", "パン"),
    VEGETABLE("vegetable", "野菜"),
    FISH("fish", "魚"),
    MEAT("meat", "肉");

    private final String configKey;
    private final String displayName;

    FoodCategory(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    /**
     * config.yml の food_buff.categories 配下で使用するキー（bread/vegetable/fish/meat）
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * プレイヤー向け表示名（パン/野菜/魚/肉）
     */
    public String getDisplayName() {
        return displayName;
    }

    // Material -> カテゴリ の対応表
    private static final Map<Material, FoodCategory> MATERIAL_MAP = new HashMap<>();

    static {
        // パン屋系
        put(FoodCategory.BREAD,
            "BREAD", "COOKIE", "CAKE", "PUMPKIN_PIE");

        // 八百屋系（野菜・果物）
        put(FoodCategory.VEGETABLE,
            "CARROT", "APPLE", "POTATO", "BAKED_POTATO", "BEETROOT",
            "MELON_SLICE", "MELON", "GOLDEN_CARROT", "GLOW_BERRIES",
            "SWEET_BERRIES", "BEETROOT_SOUP", "MUSHROOM_STEW");

        // 魚屋系（魚・海産物）
        put(FoodCategory.FISH,
            "COD", "SALMON", "COOKED_COD", "COOKED_SALMON",
            "DRIED_KELP", "KELP", "TROPICAL_FISH", "PUFFERFISH");

        // 精肉店系（肉類）
        put(FoodCategory.MEAT,
            "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON",
            "COOKED_RABBIT", "RABBIT_STEW", "BEEF", "PORKCHOP",
            "CHICKEN", "MUTTON", "RABBIT");

        // 特殊食料（GOLDEN_APPLE / ENCHANTED_GOLDEN_APPLE / SUSPICIOUS_STEW /
        // HONEY_BOTTLE / CHORUS_FRUIT / MILK_BUCKET 等）はバフ対象外とする。
    }

    /**
     * Material名の配列を指定カテゴリにまとめて登録する。
     * バージョン差でMaterialが存在しない場合（Material.matchMaterialがnull）はスキップする。
     */
    private static void put(FoodCategory category, String... materialNames) {
        for (String name : materialNames) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                MATERIAL_MAP.put(material, category);
            }
        }
    }

    /**
     * 食材Materialから対応するバフカテゴリを取得する。
     * 対象外の食材・非食材の場合は null を返す。
     */
    public static FoodCategory fromMaterial(Material material) {
        if (material == null) {
            return null;
        }
        return MATERIAL_MAP.get(material);
    }
}
