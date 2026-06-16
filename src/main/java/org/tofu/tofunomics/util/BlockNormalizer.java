package org.tofu.tofunomics.util;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

/**
 * ブロックの職業判定用正規化ユーティリティ
 *
 * 1.17「洞窟と崖」以降に追加された深層岩鉱石（DEEPSLATE_*_ORE）や
 * 深層岩石材（DEEPSLATE / COBBLED_DEEPSLATE / TUFF）を、
 * 1.16.5時代に作られた経験値・収入・採掘権限の各マップが想定する
 * 「通常版ブロック」へ寄せるための変換を一元管理する。
 *
 * 注意: ここでの変換はあくまで「職業システムの内部判定用」であり、
 *       実際のドロップ品やワールド改変には一切影響しない。
 */
public final class BlockNormalizer {

    /** Minecraftの16色プレフィックス */
    private static final String[] COLORS = {
        "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
        "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };

    /**
     * 色違いブロックのファミリーごとの「代表色（正規化先）」定義。
     * key=ファミリー名サフィックス, value=代表Materialの名前。
     * terracotta は無着色版（TERRACOTTA）が存在するためそれを代表とする。
     */
    private static final String[][] COLOR_FAMILIES = {
        {"WOOL", "WHITE_WOOL"},
        {"CARPET", "WHITE_CARPET"},
        {"CONCRETE", "WHITE_CONCRETE"},
        {"CONCRETE_POWDER", "WHITE_CONCRETE_POWDER"},
        {"TERRACOTTA", "TERRACOTTA"},
        {"GLAZED_TERRACOTTA", "WHITE_GLAZED_TERRACOTTA"},
        {"STAINED_GLASS", "WHITE_STAINED_GLASS"},
        {"STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE"}
    };

    /** 各色違いMaterial → 代表Material のルックアップ表（起動時に1回構築） */
    private static final Map<Material, Material> COLOR_VARIANT_MAP = buildColorVariantMap();

    private static Map<Material, Material> buildColorVariantMap() {
        Map<Material, Material> map = new EnumMap<>(Material.class);
        for (String[] family : COLOR_FAMILIES) {
            Material canonical = Material.getMaterial(family[1]);
            if (canonical == null) {
                continue;
            }
            for (String color : COLORS) {
                Material variant = Material.getMaterial(color + "_" + family[0]);
                // 代表色自身も含め、全色を代表色へ寄せる（価格を完全統一するため）
                if (variant != null) {
                    map.put(variant, canonical);
                }
            }
        }
        return map;
    }

    private BlockNormalizer() {
        // ユーティリティクラスのためインスタンス化禁止
    }

    /**
     * 色違いブロック（羊毛・コンクリート・テラコッタ・ガラス等）を、色を問わず
     * ファミリーの代表色へ正規化する。取引価格を色に依存せず統一するために使用。
     * 対象外のMaterialはそのまま返す。
     *
     * @param material 元のMaterial
     * @return 正規化後のMaterial（色違いでなければ同一）
     */
    public static Material normalizeColorVariant(Material material) {
        if (material == null) {
            return null;
        }
        return COLOR_VARIANT_MAP.getOrDefault(material, material);
    }

    /**
     * 職業判定用にMaterialを正規化する。
     * 変換対象でないMaterialはそのまま返す。
     *
     * @param material 元のMaterial
     * @return 正規化後のMaterial
     */
    public static Material normalizeForJob(Material material) {
        if (material == null) {
            return null;
        }
        switch (material) {
            // 深層岩鉱石 → 通常鉱石
            case DEEPSLATE_COAL_ORE:     return Material.COAL_ORE;
            case DEEPSLATE_IRON_ORE:     return Material.IRON_ORE;
            case DEEPSLATE_GOLD_ORE:     return Material.GOLD_ORE;
            case DEEPSLATE_DIAMOND_ORE:  return Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE:  return Material.EMERALD_ORE;
            case DEEPSLATE_LAPIS_ORE:    return Material.LAPIS_ORE;
            case DEEPSLATE_REDSTONE_ORE: return Material.REDSTONE_ORE;
            case DEEPSLATE_COPPER_ORE:   return Material.COPPER_ORE;
            // 深層岩石材 → 石（建材掘りの扱いをSTONEに寄せる。経験値マップにSTONEがあるため）
            case DEEPSLATE:
            case COBBLED_DEEPSLATE:
            case TUFF:                   return Material.STONE;
            default:                     return material;
        }
    }
}
