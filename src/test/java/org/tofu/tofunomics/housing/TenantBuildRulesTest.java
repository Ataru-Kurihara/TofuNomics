package org.tofu.tofunomics.housing;

import org.bukkit.Material;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 借主の設置判定のテスト。
 */
public class TenantBuildRulesTest {

    /** サーバーが無いと Material#hasGravity が使えないので、落ちるブロックを決め打ちにする */
    private static final java.util.function.Predicate<Material> GRAVITY =
            m -> EnumSet.of(Material.SAND, Material.GRAVEL, Material.ANVIL).contains(m);

    private final TenantBuildRules rules = new TenantBuildRules(TenantBuildRules.DEFAULT_DENIED, GRAVITY);

    @Test
    public void 空気の所にはチェストと作業台を置ける() {
        assertNull(rules.checkPlace(Material.CHEST, Collections.singletonList(Material.AIR)));
        assertNull(rules.checkPlace(Material.CRAFTING_TABLE, Collections.singletonList(Material.CAVE_AIR)));
    }

    @Test
    public void 草や雪など元からある物を置き換える設置はできない() {
        assertNotNull(rules.checkPlace(Material.CHEST, Collections.singletonList(Material.SHORT_GRASS)));
        assertNotNull(rules.checkPlace(Material.CHEST, Collections.singletonList(Material.SNOW)));
        assertNotNull(rules.checkPlace(Material.CHEST, Collections.singletonList(Material.WATER)));
    }

    @Test
    public void 二マスのブロックは片方でも空いていなければ置けない() {
        assertNull(rules.checkPlace(Material.OAK_DOOR, Arrays.asList(Material.AIR, Material.AIR)));
        assertNotNull(rules.checkPlace(Material.OAK_DOOR, Arrays.asList(Material.AIR, Material.POPPY)));
    }

    @Test
    public void ピストンやTNTや落ちるブロックは置けない() {
        assertNotNull(rules.checkPlace(Material.PISTON, Collections.singletonList(Material.AIR)));
        assertNotNull(rules.checkPlace(Material.TNT, Collections.singletonList(Material.AIR)));
        assertNotNull(rules.checkPlace(Material.SAND, Collections.singletonList(Material.AIR)));
        assertNotNull(rules.checkPlace(Material.ANVIL, Collections.singletonList(Material.AIR)));
        assertNotNull(rules.checkPlace(Material.RESPAWN_ANCHOR, Collections.singletonList(Material.AIR)));
    }

    @Test
    public void 設定の名前から作れて_知らない名前は飛ばす() {
        TenantBuildRules custom = TenantBuildRules.fromNames(Arrays.asList("barrel", "NO_SUCH_BLOCK"), null, GRAVITY);
        assertTrue(custom.isDenied(Material.BARREL));
        assertFalse(custom.isDenied(Material.PISTON));
        // 落ちるブロックは設定に関係なく不可
        assertTrue(custom.isDenied(Material.GRAVEL));
    }

    @Test
    public void 設定が空なら既定の禁止リストを使う() {
        TenantBuildRules empty = TenantBuildRules.fromNames(Collections.emptyList(), null, GRAVITY);
        assertTrue(empty.isDenied(Material.PISTON));
        assertFalse(new TenantBuildRules(EnumSet.noneOf(Material.class), GRAVITY).isDenied(Material.PISTON));
    }

    @Test
    public void 元のブロックを変える道具を見分ける() {
        assertTrue(TenantBuildRules.isModifyingTool(Material.IRON_AXE));
        assertTrue(TenantBuildRules.isModifyingTool(Material.WOODEN_SHOVEL));
        assertTrue(TenantBuildRules.isModifyingTool(Material.DIAMOND_HOE));
        assertTrue(TenantBuildRules.isModifyingTool(Material.HONEYCOMB));
        assertTrue(TenantBuildRules.isModifyingTool(Material.FLINT_AND_STEEL));
        assertTrue(TenantBuildRules.isModifyingTool(Material.RED_DYE));
        assertFalse(TenantBuildRules.isModifyingTool(Material.CHEST));
        assertFalse(TenantBuildRules.isModifyingTool(Material.IRON_PICKAXE));
        assertFalse(TenantBuildRules.isModifyingTool(null));
    }
}
