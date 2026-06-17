package org.tofu.tofunomics.models;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * QuestProgress モデル単体テスト
 */
public class QuestProgressTest {

    @Test
    public void testNewProgressConstructorDefaultsToAccepted() {
        UUID player = UUID.randomUUID();
        QuestProgress progress = new QuestProgress(player, "rotten_flesh_1");

        assertEquals(player, progress.getPlayerUuid());
        assertEquals("rotten_flesh_1", progress.getQuestId());
        assertEquals(QuestProgress.STATUS_ACCEPTED, progress.getStatus());
        assertTrue("新規受注は accepted 判定であるべき", progress.isAccepted());
    }

    @Test
    public void testIsAcceptedReflectsStatus() {
        QuestProgress progress = new QuestProgress(UUID.randomUUID(), "bone_1");

        progress.setStatus(QuestProgress.STATUS_COMPLETED);
        assertFalse("completed は accepted ではない", progress.isAccepted());

        progress.setStatus(QuestProgress.STATUS_ACCEPTED);
        assertTrue("accepted に戻せば accepted 判定", progress.isAccepted());
    }

    @Test
    public void testIsAcceptedNullStatus() {
        QuestProgress progress = new QuestProgress();
        assertFalse("status 未設定なら accepted ではない", progress.isAccepted());
    }
}
