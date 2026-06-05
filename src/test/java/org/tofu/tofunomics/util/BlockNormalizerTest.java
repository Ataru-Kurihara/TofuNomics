package org.tofu.tofunomics.util;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
