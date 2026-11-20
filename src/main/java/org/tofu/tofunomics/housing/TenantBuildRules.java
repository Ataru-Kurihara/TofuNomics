package org.tofu.tofunomics.housing;

import org.bukkit.Material;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * 借主が物件内で「置いてよい / 壊してよい / 道具で変えてよい」かの判定（Bukkit のイベントに依存しない部分）。
 *
 * - 置けるのは、空気の所だけ。既存のブロック（草・花・雪など）を置き換える設置は不可
 * - 置けないブロック：設定の denied_materials と、砂・砂利など落ちるブロック（落ちると記録とずれる）
 * - 元からあるブロックに、斧（樹皮はぎ）・シャベル（土の道）・クワ（耕す）・ハニカム（ろう引き）・
 *   火打石などを使うと建物が変わるので不可
 */
public final class TenantBuildRules {

    /** 設定に何も無いときの「置けないブロック」 */
    public static final Set<Material> DEFAULT_DENIED = EnumSet.of(
            Material.PISTON, Material.STICKY_PISTON, Material.TNT, Material.DISPENSER,
            Material.CRAFTER, Material.SPONGE, Material.WET_SPONGE, Material.RESPAWN_ANCHOR);

    private final Set<Material> deniedMaterials;
    /** 落ちるブロックか（本番は Material#hasGravity。サーバーの無いテストでは差し替える） */
    private final Predicate<Material> hasGravity;

    public TenantBuildRules(Set<Material> deniedMaterials) {
        this(deniedMaterials, Material::hasGravity);
    }

    TenantBuildRules(Set<Material> deniedMaterials, Predicate<Material> hasGravity) {
        this.deniedMaterials = deniedMaterials.isEmpty()
                ? EnumSet.noneOf(Material.class)
                : EnumSet.copyOf(deniedMaterials);
        this.hasGravity = hasGravity;
    }

    /**
     * 設定の文字列リストから作る。知らない名前は警告して飛ばす。
     */
    public static TenantBuildRules fromNames(Collection<String> names, Logger logger) {
        return fromNames(names, logger, Material::hasGravity);
    }

    static TenantBuildRules fromNames(Collection<String> names, Logger logger, Predicate<Material> hasGravity) {
        if (names == null || names.isEmpty()) {
            return new TenantBuildRules(DEFAULT_DENIED, hasGravity);
        }
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material m = Material.matchMaterial(name.trim());
            if (m == null) {
                if (logger != null) {
                    logger.warning("housing_rental.tenant_build.denied_materials に知らないブロック名: " + name);
                }
                continue;
            }
            set.add(m);
        }
        return new TenantBuildRules(set, hasGravity);
    }

    /** 置き換え前のブロックが「空いている所」か（空気だけ） */
    public static boolean isEmptySpace(Material replaced) {
        return replaced == null
                || replaced == Material.AIR
                || replaced == Material.CAVE_AIR
                || replaced == Material.VOID_AIR;
    }

    /** 置けないブロックか */
    public boolean isDenied(Material placed) {
        return deniedMaterials.contains(placed) || hasGravity.test(placed);
    }

    /**
     * 設置の判定。
     *
     * @return 置けない理由（置けるなら null）
     */
    public String checkPlace(Material placed, Collection<Material> replacedTypes) {
        if (isDenied(placed)) {
            return "このブロックは物件の中には置けません";
        }
        for (Material replaced : replacedTypes) {
            if (!isEmptySpace(replaced)) {
                return "物件の中では、空いている所にだけブロックを置けます";
            }
        }
        return null;
    }

    /**
     * 元からあるブロックに使うと、ブロックを変えてしまう道具か。
     */
    public static boolean isModifyingTool(Material tool) {
        if (tool == null) {
            return false;
        }
        String name = tool.name();
        return name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_DYE")
                || tool == Material.HONEYCOMB
                || tool == Material.FLINT_AND_STEEL
                || tool == Material.FIRE_CHARGE
                || tool == Material.BONE_MEAL
                || tool == Material.INK_SAC
                || tool == Material.GLOW_INK_SAC
                || tool == Material.BRUSH
                || tool == Material.SHEARS;
    }
}
