package org.tofu.tofunomics.npc;

import org.bukkit.Material;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 加工NPCのconfig駆動マッピングのテスト
 * wood_types設定からの動的読み込み・フォールバック・無効Material名のスキップを検証
 */
public class ProcessingNPCManagerTest {

    @Mock
    private TofuNomics plugin;
    @Mock
    private ConfigManager configManager;
    @Mock
    private NPCManager npcManager;
    @Mock
    private CurrencyConverter currencyConverter;
    @Mock
    private JobManager jobManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // 無効Material名のスキップ時に警告ログ出力で使用される
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ProcessingNPCManagerTest"));
        when(configManager.getProcessingPlanksPerLog()).thenReturn(4);
    }

    private ProcessingNPCManager newManager() {
        return new ProcessingNPCManager(plugin, configManager, npcManager, currencyConverter, jobManager);
    }

    @Test
    public void configの木材マッピングが反映される() {
        Map<String, String> woodTypes = new LinkedHashMap<>();
        woodTypes.put("oak_log", "oak_planks");
        woodTypes.put("mangrove_log", "mangrove_planks");
        woodTypes.put("cherry_log", "cherry_planks");
        woodTypes.put("bamboo_block", "bamboo_planks");
        when(configManager.getProcessingWoodTypes()).thenReturn(woodTypes);

        ProcessingNPCManager manager = newManager();

        assertTrue(manager.isProcessableLog(Material.OAK_LOG));
        assertTrue(manager.isProcessableLog(Material.MANGROVE_LOG));
        assertTrue(manager.isProcessableLog(Material.CHERRY_LOG));
        assertEquals(Material.MANGROVE_PLANKS, manager.getPlanksFromLog(Material.MANGROVE_LOG));
        assertEquals(Material.BAMBOO_PLANKS, manager.getPlanksFromLog(Material.BAMBOO_BLOCK));
        // config未定義の木材は加工対象外
        assertFalse(manager.isProcessableLog(Material.SPRUCE_LOG));
    }

    @Test
    public void configが空ならデフォルト8種にフォールバックする() {
        when(configManager.getProcessingWoodTypes()).thenReturn(new LinkedHashMap<>());

        ProcessingNPCManager manager = newManager();

        assertTrue(manager.isProcessableLog(Material.OAK_LOG));
        assertTrue(manager.isProcessableLog(Material.SPRUCE_LOG));
        assertTrue(manager.isProcessableLog(Material.BIRCH_LOG));
        assertTrue(manager.isProcessableLog(Material.JUNGLE_LOG));
        assertTrue(manager.isProcessableLog(Material.ACACIA_LOG));
        assertTrue(manager.isProcessableLog(Material.DARK_OAK_LOG));
        assertTrue(manager.isProcessableLog(Material.CRIMSON_STEM));
        assertTrue(manager.isProcessableLog(Material.WARPED_STEM));
        assertEquals(Material.OAK_PLANKS, manager.getPlanksFromLog(Material.OAK_LOG));
    }

    @Test
    public void 無効なMaterial名はスキップされ有効なもののみ登録される() {
        Map<String, String> woodTypes = new LinkedHashMap<>();
        woodTypes.put("oak_log", "oak_planks");
        woodTypes.put("not_a_real_log", "not_a_real_planks");  // 無効
        woodTypes.put("birch_log", "totally_invalid_output");  // 出力が無効
        when(configManager.getProcessingWoodTypes()).thenReturn(woodTypes);

        ProcessingNPCManager manager = newManager();

        assertTrue(manager.isProcessableLog(Material.OAK_LOG));
        // 無効エントリは登録されない（getでnull）
        assertNull(manager.getPlanksFromLog(Material.BIRCH_LOG));
    }

    @Test
    public void 全エントリ無効ならデフォルトにフォールバックする() {
        Map<String, String> woodTypes = new LinkedHashMap<>();
        woodTypes.put("not_a_real_log", "not_a_real_planks");
        when(configManager.getProcessingWoodTypes()).thenReturn(woodTypes);

        ProcessingNPCManager manager = newManager();

        // 全て無効 → デフォルト8種にフォールバック
        assertTrue(manager.isProcessableLog(Material.OAK_LOG));
        assertTrue(manager.isProcessableLog(Material.WARPED_STEM));
    }
}
