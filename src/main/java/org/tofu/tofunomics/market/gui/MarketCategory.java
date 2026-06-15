package org.tofu.tofunomics.market.gui;

import org.bukkit.Material;

/**
 * マーケット一覧 GUI のカテゴリ。出品アイテムの Material からカテゴリを分類し、
 * フィルタリングに用いる。分類は TradingGUI のカテゴリ体系に概ね合わせている。
 */
public enum MarketCategory {

    ALL("§7すべて", Material.CHEST),
    MINING("§8鉱物", Material.DIAMOND_PICKAXE),
    FARMING("§a農作物", Material.WHEAT),
    LOGGING("§2木材", Material.OAK_LOG),
    FISHING("§9海産物", Material.COD),
    CRAFTING("§6製作品", Material.CRAFTING_TABLE),
    MATERIALS("§5素材", Material.BLAZE_POWDER),
    OTHER("§fその他", Material.ENDER_PEARL);

    private final String displayName;
    private final Material icon;

    MarketCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * Material をいずれか1つのカテゴリに分類する（ALL を除く）。
     * どの具体カテゴリにも該当しなければ {@link #OTHER}。
     */
    public static MarketCategory classify(Material material) {
        if (material == null) {
            return OTHER;
        }
        String name = material.name().toLowerCase();
        // LOGGING を FARMING より先に判定（"log" 等の取りこぼし防止）
        if (isMining(name)) return MINING;
        if (isLogging(name)) return LOGGING;
        if (isFarming(name)) return FARMING;
        if (isFishing(name)) return FISHING;
        if (isCrafting(name)) return CRAFTING;
        if (isMaterials(name)) return MATERIALS;
        return OTHER;
    }

    /**
     * 指定 Material がこのカテゴリに含まれるか。ALL は常に true。
     */
    public boolean matches(Material material) {
        if (this == ALL) {
            return true;
        }
        return classify(material) == this;
    }

    private static boolean isMining(String name) {
        return name.contains("ore") || name.contains("ingot") || name.contains("coal")
                || name.contains("diamond") || name.contains("emerald") || name.contains("redstone")
                || name.contains("lapis") || name.contains("quartz") || name.contains("netherite")
                || name.contains("stone") || name.contains("cobblestone") || name.contains("andesite")
                || name.contains("diorite") || name.contains("granite") || name.startsWith("raw_");
    }

    private static boolean isLogging(String name) {
        return name.contains("log") || name.contains("wood") || name.contains("plank")
                || name.contains("stick") || name.contains("bark") || name.contains("stem")
                || name.endsWith("_sapling");
    }

    private static boolean isFarming(String name) {
        return name.contains("wheat") || name.contains("potato") || name.contains("carrot")
                || name.contains("beetroot") || name.contains("pumpkin") || name.contains("melon")
                || name.contains("apple") || name.contains("bread") || name.contains("sugar")
                || name.contains("cocoa") || name.contains("seeds") || name.contains("egg")
                || name.contains("beef") || name.contains("porkchop") || name.contains("chicken")
                || name.contains("mutton");
    }

    private static boolean isFishing(String name) {
        return name.contains("cod") || name.contains("salmon") || name.contains("fish")
                || name.contains("kelp") || name.contains("seagrass") || name.contains("prismarine")
                || name.contains("nautilus");
    }

    private static boolean isCrafting(String name) {
        return name.contains("sword") || name.contains("pickaxe") || name.contains("_axe")
                || name.endsWith("axe") || name.contains("shovel") || name.contains("hoe")
                || name.contains("helmet") || name.contains("chestplate") || name.contains("leggings")
                || name.contains("boots") || name.contains("bow") || name.contains("shield");
    }

    private static boolean isMaterials(String name) {
        return name.contains("powder") || name.contains("dust") || name.contains("tear")
                || name.contains("eye") || name.contains("membrane") || name.contains("cream")
                || name.contains("string") || name.contains("gunpowder") || name.contains("blaze")
                || name.contains("ender") || name.contains("slime");
    }
}
