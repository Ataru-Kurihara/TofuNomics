package org.tofu.tofunomics.util;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * BlockNormalizer の正規化ロジックのテスト
 * 深層岩鉱石・深層岩石材が通常版へ正しく変換されることを検証
 */
public class BlockNormalizerTest {

    @Test
    public void 深層岩鉱石は通常鉱石へ正規化される() {
        assertEquals(Material.COAL_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_COAL_ORE));
        assertEquals(Material.IRON_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_IRON_ORE));
        assertEquals(Material.GOLD_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_GOLD_ORE));
        assertEquals(Material.DIAMOND_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_DIAMOND_ORE));
        assertEquals(Material.EMERALD_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_EMERALD_ORE));
        assertEquals(Material.LAPIS_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_LAPIS_ORE));
        assertEquals(Material.REDSTONE_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_REDSTONE_ORE));
        assertEquals(Material.COPPER_ORE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE_COPPER_ORE));
    }

    @Test
    public void 深層岩石材は石へ正規化される() {
        assertEquals(Material.STONE, BlockNormalizer.normalizeForJob(Material.DEEPSLATE));
        assertEquals(Material.STONE, BlockNormalizer.normalizeForJob(Material.COBBLED_DEEPSLATE));
        assertEquals(Material.STONE, BlockNormalizer.normalizeForJob(Material.TUFF));
    }

    @Test
    public void 通常版ブロックはそのまま返る() {
        assertEquals(Material.IRON_ORE, BlockNormalizer.normalizeForJob(Material.IRON_ORE));
        assertEquals(Material.COPPER_ORE, BlockNormalizer.normalizeForJob(Material.COPPER_ORE));
        assertEquals(Material.MANGROVE_LOG, BlockNormalizer.normalizeForJob(Material.MANGROVE_LOG));
        assertEquals(Material.CHERRY_LOG, BlockNormalizer.normalizeForJob(Material.CHERRY_LOG));
        assertEquals(Material.STONE, BlockNormalizer.normalizeForJob(Material.STONE));
        assertEquals(Material.DIAMOND_SWORD, BlockNormalizer.normalizeForJob(Material.DIAMOND_SWORD));
    }

    @Test
    public void nullはnullを返す() {
        assertNull(BlockNormalizer.normalizeForJob(null));
    }

    // --- 色違いブロックの統一正規化（取引価格用）---

    @Test
    public void 羊毛は色を問わず白羊毛へ正規化される() {
        assertEquals(Material.WHITE_WOOL, BlockNormalizer.normalizeColorVariant(Material.WHITE_WOOL));
        assertEquals(Material.WHITE_WOOL, BlockNormalizer.normalizeColorVariant(Material.RED_WOOL));
        assertEquals(Material.WHITE_WOOL, BlockNormalizer.normalizeColorVariant(Material.BLACK_WOOL));
        assertEquals(Material.WHITE_WOOL, BlockNormalizer.normalizeColorVariant(Material.LIGHT_GRAY_WOOL));
    }

    @Test
    public void 各色違いファミリーが代表色へ正規化される() {
        assertEquals(Material.WHITE_CARPET, BlockNormalizer.normalizeColorVariant(Material.BLUE_CARPET));
        assertEquals(Material.WHITE_CONCRETE, BlockNormalizer.normalizeColorVariant(Material.LIGHT_GRAY_CONCRETE));
        assertEquals(Material.WHITE_CONCRETE_POWDER, BlockNormalizer.normalizeColorVariant(Material.GREEN_CONCRETE_POWDER));
        assertEquals(Material.WHITE_GLAZED_TERRACOTTA, BlockNormalizer.normalizeColorVariant(Material.PINK_GLAZED_TERRACOTTA));
        assertEquals(Material.WHITE_STAINED_GLASS, BlockNormalizer.normalizeColorVariant(Material.CYAN_STAINED_GLASS));
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, BlockNormalizer.normalizeColorVariant(Material.RED_STAINED_GLASS_PANE));
    }

    @Test
    public void 色付きテラコッタは無着色テラコッタへ正規化される() {
        assertEquals(Material.TERRACOTTA, BlockNormalizer.normalizeColorVariant(Material.RED_TERRACOTTA));
        assertEquals(Material.TERRACOTTA, BlockNormalizer.normalizeColorVariant(Material.WHITE_TERRACOTTA));
        // 無着色テラコッタはそのまま
        assertEquals(Material.TERRACOTTA, BlockNormalizer.normalizeColorVariant(Material.TERRACOTTA));
    }

    @Test
    public void 色違いでないブロックはそのまま返る() {
        assertEquals(Material.STONE, BlockNormalizer.normalizeColorVariant(Material.STONE));
        assertEquals(Material.DIAMOND, BlockNormalizer.normalizeColorVariant(Material.DIAMOND));
        assertEquals(Material.OAK_LOG, BlockNormalizer.normalizeColorVariant(Material.OAK_LOG));
    }

    @Test
    public void 色正規化でnullはnullを返す() {
        assertNull(BlockNormalizer.normalizeColorVariant(null));
    }

    @Test
    public void 代表色は色違いベースと判定される() {
        assertTrue(BlockNormalizer.isColorVariantBase(Material.WHITE_WOOL));
        assertTrue(BlockNormalizer.isColorVariantBase(Material.WHITE_CONCRETE));
        assertTrue(BlockNormalizer.isColorVariantBase(Material.TERRACOTTA));
        assertTrue(BlockNormalizer.isColorVariantBase(Material.WHITE_STAINED_GLASS_PANE));
    }

    @Test
    public void 代表色以外は色違いベースではない() {
        // 非代表色（赤羊毛）や色と無関係なブロックは false
        assertFalse(BlockNormalizer.isColorVariantBase(Material.RED_WOOL));
        assertFalse(BlockNormalizer.isColorVariantBase(Material.STONE));
        assertFalse(BlockNormalizer.isColorVariantBase(Material.DIAMOND));
        assertFalse(BlockNormalizer.isColorVariantBase(null));
    }
}
