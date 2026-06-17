package org.tofu.tofunomics.farming;

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
import java.util.UUID;

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

    private FarmPlotManager manager;
    private final UUID uuid = UUID.randomUUID();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(plugin.getWorldGuardIntegration()).thenReturn(worldGuard);
        when(player.getUniqueId()).thenReturn(uuid);
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
}
