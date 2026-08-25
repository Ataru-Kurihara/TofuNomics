package org.tofu.tofunomics.npc;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.QuestProgressDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.models.QuestProgress;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 討伐クエストのリピート受注クールダウンの回帰テスト
 *
 * 修正前は納品時に受注行を削除していたため同じクエストを即座に再受注でき、
 * モブトラップを持つプレイヤーが無制限に金塊を得られる状態だった
 * （上限のない faucet）。
 */
public class QuestNPCManagerCooldownTest {

    private static final String QUEST_ID = "rotten_flesh_1";
    private static final int COOLDOWN_MINUTES = 120;

    private QuestProgressDAO questProgressDAO;
    private QuestNPCManager manager;
    private Player player;
    private final UUID playerUuid = UUID.randomUUID();

    @Before
    public void setUp() {
        TofuNomics plugin = mock(TofuNomics.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("QuestNPCManagerCooldownTest"));

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getMaxConcurrentQuests()).thenReturn(5);
        when(configManager.getQuestDefinitions()).thenReturn(Collections.emptyList());
        when(configManager.getQuestRepeatCooldownMinutes()).thenReturn(COOLDOWN_MINUTES);

        questProgressDAO = mock(QuestProgressDAO.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        manager = new QuestNPCManager(plugin, configManager, mock(NPCManager.class),
            mock(CurrencyConverter.class), questProgressDAO);
    }

    private QuestProgress completedAt(long millis) {
        QuestProgress progress = new QuestProgress(playerUuid, QUEST_ID);
        progress.setStatus(QuestProgress.STATUS_COMPLETED);
        progress.setCompletedAt(new Timestamp(millis));
        return progress;
    }

    @Test
    public void 納品直後は再受注できない() throws Exception {
        when(questProgressDAO.getByPlayerAndQuest(playerUuid, QUEST_ID))
            .thenReturn(completedAt(System.currentTimeMillis()));

        long remaining = manager.getRepeatCooldownRemainingMillis(player, QUEST_ID);

        assertTrue("納品直後はクールダウンが残っていること", remaining > 0);
    }

    @Test
    public void クールダウン経過後は再受注できる() throws Exception {
        long past = System.currentTimeMillis() - (COOLDOWN_MINUTES + 1) * 60_000L;
        when(questProgressDAO.getByPlayerAndQuest(playerUuid, QUEST_ID))
            .thenReturn(completedAt(past));

        assertEquals("クールダウン経過後は0であること",
            0, manager.getRepeatCooldownRemainingMillis(player, QUEST_ID));
    }

    @Test
    public void 未受注のクエストはクールダウン対象外() throws Exception {
        when(questProgressDAO.getByPlayerAndQuest(playerUuid, QUEST_ID)).thenReturn(null);

        assertEquals(0, manager.getRepeatCooldownRemainingMillis(player, QUEST_ID));
    }

    @Test
    public void 完了時刻が無い行はクールダウン対象外() throws Exception {
        QuestProgress progress = new QuestProgress(playerUuid, QUEST_ID);
        progress.setStatus(QuestProgress.STATUS_COMPLETED);
        progress.setCompletedAt(null);
        when(questProgressDAO.getByPlayerAndQuest(playerUuid, QUEST_ID)).thenReturn(progress);

        assertEquals("旧データで受注不能にならないこと",
            0, manager.getRepeatCooldownRemainingMillis(player, QUEST_ID));
    }

    @Test
    public void 受注中の行はクールダウン対象外() throws Exception {
        QuestProgress progress = new QuestProgress(playerUuid, QUEST_ID);
        progress.setStatus(QuestProgress.STATUS_ACCEPTED);
        when(questProgressDAO.getByPlayerAndQuest(playerUuid, QUEST_ID)).thenReturn(progress);

        assertEquals(0, manager.getRepeatCooldownRemainingMillis(player, QUEST_ID));
    }
}
