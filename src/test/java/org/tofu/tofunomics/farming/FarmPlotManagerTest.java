package org.tofu.tofunomics.farming;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.FarmPlotDAO;
import org.tofu.tofunomics.integration.WorldGuardIntegration;
import org.tofu.tofunomics.models.FarmPlot;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FarmPlotManager の割り当て分岐テスト（Bukkit静的依存を踏まない経路のみ）
 */
public class FarmPlotManagerTest {

    @Mock private TofuNomics plugin;
    @Mock private ConfigManager configManager;
    @Mock private FarmPlotDAO farmPlotDAO;
    @Mock private WorldGuardIntegration worldGuard;
    @Mock private Player player;
    @Mock private Player admin;
    @Mock private World world;

    private FarmPlotManager manager;
    private final UUID uuid = UUID.randomUUID();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(plugin.getWorldGuardIntegration()).thenReturn(worldGuard);
        when(player.getUniqueId()).thenReturn(uuid);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FarmPlotManagerTest"));
        manager = new FarmPlotManager(plugin, configManager, farmPlotDAO);
    }

    @Test
    public void testAssignPlotSkipsWhenWorldGuardDisabled() throws Exception {
        when(worldGuard.isEnabled()).thenReturn(false);

        manager.assignPlot(player);

        // WG無効なら一切DBに触れない
        verifyNoInteractions(farmPlotDAO);
    }

    @Test
    public void testAssignPlotSkipsWhenAlreadyOwning() throws Exception {
        when(worldGuard.isEnabled()).thenReturn(true);
        when(farmPlotDAO.getPlotByOwner(uuid.toString())).thenReturn(new FarmPlot());

        manager.assignPlot(player);

        // 既に所有しているので空き検索は行わない
        verify(farmPlotDAO, never()).findAvailablePlot();
        verify(farmPlotDAO, never()).updateOwner(anyInt(), anyString());
    }

    @Test
    public void testAssignPlotSilentWhenNoPlotsRegistered() throws Exception {
        when(worldGuard.isEnabled()).thenReturn(true);
        when(farmPlotDAO.getPlotByOwner(uuid.toString())).thenReturn(null);
        when(farmPlotDAO.findAvailablePlot()).thenReturn(null);
        when(farmPlotDAO.getAllPlots()).thenReturn(Collections.emptyList());

        manager.assignPlot(player);

        // 区画未登録なら無言（所有者更新もメッセージもなし）
        verify(farmPlotDAO, never()).updateOwner(anyInt(), anyString());
        verify(player, never()).sendMessage(anyString());
    }

    // ========== 区画リージョンの保護設定 ==========

    private FarmPlot plotAt(int y1, int y2) {
        FarmPlot plot = new FarmPlot("farmplot_1", "tofuNomics", -148, y1, -385, -159, y2, -393, "farmplot_farmplot_1");
        plot.setId(1);
        return plot;
    }

    private void stubRegionOk() throws Exception {
        when(worldGuard.isEnabled()).thenReturn(true);
        when(worldGuard.hasRegion("farmplot_farmplot_1", world)).thenReturn(true);
        when(worldGuard.redefineRegion(anyString(), any(), any(), any())).thenReturn(true);
        when(worldGuard.setParentRegion(anyString(), anyString(), any())).thenReturn(true);
        when(worldGuard.applyRegionFlags(anyString(), any(), anyMap(), anyMap())).thenReturn(3);
        when(farmPlotDAO.updateCoordinates(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);
        when(configManager.isCityProtectionEnabled()).thenReturn(true);
        when(configManager.getCityProtectionRegionName()).thenReturn("city");
    }

    @Test
    public void testExpandedYRangeAddsThreeBelowAndTenAbove() {
        // 本番の区画は Y75〜76（角の順は逆のこともある）
        assertArrayEquals(new int[]{72, 86}, FarmPlotManager.expandedYRange(76, 75));
        assertArrayEquals(new int[]{71, 86}, FarmPlotManager.expandedYRange(74, 76));
    }

    @Test
    public void testIsYRangeExpanded() {
        assertFalse(FarmPlotManager.isYRangeExpanded(75, 76));
        assertFalse(FarmPlotManager.isYRangeExpanded(74, 76));
        // 1段だけの選択でも、広げた後は必ず「広げ済み」と判定される
        int[] y = FarmPlotManager.expandedYRange(75, 75);
        assertTrue(FarmPlotManager.isYRangeExpanded(y[0], y[1]));
        assertTrue(FarmPlotManager.isYRangeExpanded(86, 72));
    }

    @Test
    public void testResolveParentRegionName() {
        assertEquals("city", FarmPlotManager.resolveParentRegionName(true, "city"));
        assertEquals("city", FarmPlotManager.resolveParentRegionName(true, " city "));
        assertNull(FarmPlotManager.resolveParentRegionName(true, ""));
        assertNull(FarmPlotManager.resolveParentRegionName(true, "   "));
        assertNull(FarmPlotManager.resolveParentRegionName(true, null));
        assertNull(FarmPlotManager.resolveParentRegionName(false, "city"));
    }

    @Test
    public void testApplySettingsExpandsSetsParentAndMemberFlags() throws Exception {
        stubRegionOk();
        FarmPlot plot = plotAt(76, 75);

        assertNull(manager.applyPlotRegionSettings(plot, world));

        // WG と DB を同じ範囲（72〜86）にそろえる
        verify(worldGuard).redefineRegion(eq("farmplot_farmplot_1"), eq(world),
                argThat((Location l) -> l.getBlockY() == 72), argThat((Location l) -> l.getBlockY() == 86));
        verify(farmPlotDAO).updateCoordinates(1, -148, 72, -385, -159, 86, -393);
        assertEquals(72, plot.getY1());
        assertEquals(86, plot.getY2());
        verify(worldGuard).setParentRegion("farmplot_farmplot_1", "city", world);
        verify(worldGuard).applyRegionFlags(eq("farmplot_farmplot_1"), eq(world),
                argThat((Map<String, String> f) -> f.size() == 3 && "allow".equals(f.get("build"))
                        && "allow".equals(f.get("block-break")) && "allow".equals(f.get("interact"))),
                argThat((Map<String, String> g) -> g.size() == 3 && "members".equals(g.get("build"))
                        && "members".equals(g.get("block-break")) && "members".equals(g.get("interact"))));
    }

    @Test
    public void testApplySettingsTwiceDoesNotKeepExpanding() throws Exception {
        stubRegionOk();
        FarmPlot plot = plotAt(75, 76);

        assertNull(manager.applyPlotRegionSettings(plot, world));
        // 2回目は DB から読み直した想定（広げた後の値）
        FarmPlot reloaded = plotAt(plot.getY1(), plot.getY2());
        when(worldGuard.getParentRegionName("farmplot_farmplot_1", world)).thenReturn("city");
        assertNull(manager.applyPlotRegionSettings(reloaded, world));

        // 範囲を広げるのは1回だけ。2回目は Y が変わらない
        verify(worldGuard, times(1)).redefineRegion(anyString(), any(), any(), any());
        verify(farmPlotDAO, times(1)).updateCoordinates(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertEquals(72, reloaded.getY1());
        assertEquals(86, reloaded.getY2());
        // 親が付いていれば付け直さない。フラグは毎回同じ値で上書き
        verify(worldGuard, times(1)).setParentRegion(anyString(), anyString(), any());
        verify(worldGuard, times(2)).applyRegionFlags(anyString(), any(), anyMap(), anyMap());
    }

    @Test
    public void testApplySettingsSkipsParentWhenNameEmpty() throws Exception {
        stubRegionOk();
        when(configManager.getCityProtectionRegionName()).thenReturn("");

        assertNull(manager.applyPlotRegionSettings(plotAt(75, 76), world));

        // 親は付けないが、高さとフラグは直す
        verify(worldGuard, never()).setParentRegion(anyString(), anyString(), any());
        verify(worldGuard).redefineRegion(anyString(), any(), any(), any());
        verify(worldGuard).applyRegionFlags(anyString(), any(), anyMap(), anyMap());
    }

    @Test
    public void testApplySettingsSkipsParentWhenCityProtectionDisabled() throws Exception {
        stubRegionOk();
        when(configManager.isCityProtectionEnabled()).thenReturn(false);

        assertNull(manager.applyPlotRegionSettings(plotAt(75, 76), world));

        verify(worldGuard, never()).setParentRegion(anyString(), anyString(), any());
    }

    @Test
    public void testApplySettingsRollsBackRegionWhenDbUpdateFails() throws Exception {
        stubRegionOk();
        when(farmPlotDAO.updateCoordinates(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(false);
        FarmPlot plot = plotAt(75, 76);

        assertNotNull(manager.applyPlotRegionSettings(plot, world));

        // 広げた後、元の範囲（75〜76）に戻す
        verify(worldGuard).redefineRegion(eq("farmplot_farmplot_1"), eq(world),
                argThat((Location l) -> l.getBlockY() == 75), argThat((Location l) -> l.getBlockY() == 76));
        assertEquals(75, plot.getY1());
        assertEquals(76, plot.getY2());
        verify(worldGuard, never()).applyRegionFlags(anyString(), any(), anyMap(), anyMap());
    }

    @Test
    public void testApplySettingsFailsWhenRegionMissing() throws Exception {
        stubRegionOk();
        when(worldGuard.hasRegion("farmplot_farmplot_1", world)).thenReturn(false);

        assertNotNull(manager.applyPlotRegionSettings(plotAt(75, 76), world));
        verify(farmPlotDAO, never()).updateCoordinates(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testApplySettingsReportsFlagFailure() throws Exception {
        stubRegionOk();
        when(worldGuard.applyRegionFlags(anyString(), any(), anyMap(), anyMap())).thenReturn(1);

        assertNotNull(manager.applyPlotRegionSettings(plotAt(75, 76), world));
    }

    // ========== 手動割り当て ==========

    @Test
    public void testAssignPlotManuallyDoesNotUpdateOwnerWhenAddMemberFails() throws Exception {
        when(worldGuard.isEnabled()).thenReturn(true);
        when(farmPlotDAO.getPlotByOwner(uuid.toString())).thenReturn(null);
        FarmPlot plot = plotAt(72, 86);
        // 区画名を指定する経路（Bukkit.getWorld は下で差し替える）
        when(farmPlotDAO.getPlotByName("farmplot_1")).thenReturn(plot);
        when(worldGuard.addMember(anyString(), any(), any())).thenReturn(false);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("tofuNomics")).thenReturn(world);
            assertFalse(manager.assignPlotManually(admin, player, "farmplot_1"));
        }

        verify(farmPlotDAO, never()).updateOwner(anyInt(), anyString());
        verify(admin).sendMessage(contains("メンバー登録に失敗"));
        verify(player, never()).sendMessage(anyString());
    }
}
