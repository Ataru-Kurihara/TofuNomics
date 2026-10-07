package org.tofu.tofunomics.commands;

import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 経済ワールドの外（ロビーなど）で打たれたコマンドを、案内を出して止めることのテスト。
 */
public class EconomyWorldGuardTest {

    private Player playerIn(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    @Test
    public void 経済ワールドの外では案内を出して止める() {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isEconomyEnabledInWorld("lobby")).thenReturn(false);
        Player player = playerIn("lobby");

        assertTrue(EconomyWorldGuard.blockIfOutside(player, configManager));
        verify(player).sendMessage(EconomyWorldGuard.MESSAGE);
    }

    @Test
    public void 経済ワールドの中では止めない() {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isEconomyEnabledInWorld("tofuNomics")).thenReturn(true);
        Player player = playerIn("tofuNomics");

        assertFalse(EconomyWorldGuard.blockIfOutside(player, configManager));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    public void コンソールは止めない() {
        ConfigManager configManager = mock(ConfigManager.class);
        assertFalse(EconomyWorldGuard.blockIfOutside(mock(ConsoleCommandSender.class), configManager));
    }
}
