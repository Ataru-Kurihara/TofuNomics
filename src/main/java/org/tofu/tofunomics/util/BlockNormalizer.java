package org.tofu.tofunomics.util;

import org.bukkit.Material;

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

    private BlockNormalizer() {
        // ユーティリティクラスのためインスタンス化禁止
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
