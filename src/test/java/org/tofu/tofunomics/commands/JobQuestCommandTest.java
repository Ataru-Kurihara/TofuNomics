package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * /quest が、クエスト NPC（依頼受付所）への案内だけを出すことのテスト。
 */
public class JobQuestCommandTest {

    @Test
    public void 案内に依頼受付所が書かれている() {
        String text = ChatColor.stripColor(String.join("\n", JobQuestCommand.guidanceLines()));
        assertTrue(text.contains("依頼受付所"));
    }

    @Test
    public void サブコマンドを付けても同じ案内だけを出す() {
        JobQuestCommand command = new JobQuestCommand(mock(ConfigManager.class), null);
        int lines = JobQuestCommand.guidanceLines().size();

        ConsoleCommandSender noArgs = mock(ConsoleCommandSender.class);
        assertTrue(command.onCommand(noArgs, mock(Command.class), "quest", new String[0]));
        verify(noArgs, times(lines)).sendMessage(anyString());

        ConsoleCommandSender accept = mock(ConsoleCommandSender.class);
        assertTrue(command.onCommand(accept, mock(Command.class), "quest", new String[]{"accept", "1"}));
        verify(accept, times(lines)).sendMessage(anyString());
    }
}
