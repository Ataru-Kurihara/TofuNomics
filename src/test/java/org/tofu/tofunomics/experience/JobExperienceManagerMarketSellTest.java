package org.tofu.tofunomics.experience;

import org.bukkit.Material;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobExperienceManager のマーケット売り出品経験値ロジックのテスト。
 * 経験値算出（基準値 × 数量 × 係数）と Material→職業マッピングを検証する。
 */
public class JobExperienceManagerMarketSellTest {

    private ConfigManager configManager;
    private JobExperienceManager manager;

    @Before
    public void setUp() {
        configManager = mock(ConfigManager.class);
        when(configManager.getMarketSellExpMultiplier()).thenReturn(0.3);
        // コンストラクタはマップ初期化のみ。DAO等は参照されないため null/mock で良い
        manager = new JobExperienceManager(configManager, null, null, null, null, null);
    }

    @Test
    public void testCalculateMarketSellExp() {
        // 基準値5.0 × 数量10 × 係数0.3 = 15.0
        assertEquals(15.0, manager.calculateMarketSellExp(5.0, 10), 0.0001);
        // 基準値2.0 × 数量1 × 係数0.3 = 0.6
        assertEquals(0.6, manager.calculateMarketSellExp(2.0, 1), 0.0001);
    }

    @Test
    public void testCalculateMarketSellExpInvalidInputs() {
        // 数量0以下・基準値0以下は0
        assertEquals(0.0, manager.calculateMarketSellExp(5.0, 0), 0.0001);
        assertEquals(0.0, manager.calculateMarketSellExp(5.0, -3), 0.0001);
        assertEquals(0.0, manager.calculateMarketSellExp(0.0, 10), 0.0001);
    }

    @Test
    public void testCalculateMarketSellExpReflectsConfigMultiplier() {
        // 係数1.0なら基準値×数量そのまま
        when(configManager.getMarketSellExpMultiplier()).thenReturn(1.0);
        assertEquals(50.0, manager.calculateMarketSellExp(5.0, 10), 0.0001);
    }

    @Test
    public void testMarketSellJobMapping() {
        // 鉱夫の素材
        assertEquals("miner", manager.getMarketSellJobName(Material.RAW_IRON));
        assertEquals("miner", manager.getMarketSellJobName(Material.DIAMOND));
        // 木こりの原木
        assertEquals("woodcutter", manager.getMarketSellJobName(Material.OAK_LOG));
        // 農家の収穫物
        assertEquals("farmer", manager.getMarketSellJobName(Material.WHEAT));
        assertEquals("farmer", manager.getMarketSellJobName(Material.CARROT));
        // 農産加工品も農家対象
        assertEquals("farmer", manager.getMarketSellJobName(Material.BREAD));
        assertEquals("farmer", manager.getMarketSellJobName(Material.BAKED_POTATO));
        assertEquals("farmer", manager.getMarketSellJobName(Material.CAKE));
        assertEquals("farmer", manager.getMarketSellJobName(Material.HAY_BLOCK));
        assertEquals("farmer", manager.getMarketSellJobName(Material.PUMPKIN_PIE));
        // 鍛冶屋の製作品
        assertEquals("blacksmith", manager.getMarketSellJobName(Material.IRON_INGOT));
        assertEquals("blacksmith", manager.getMarketSellJobName(Material.DIAMOND_PICKAXE));
    }

    @Test
    public void testNonMappedMaterialReturnsNull() {
        // マッピング対象外のアイテム・nullは null を返す
        assertNull(manager.getMarketSellJobName(Material.DIRT));
        assertNull(manager.getMarketSellJobName(Material.STICK));
        assertNull(manager.getMarketSellJobName(null));
    }
}
