package org.tofu.tofunomics.quests;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * QuestDefinition 単体テスト（不変オブジェクトのアクセサ検証）
 */
public class QuestDefinitionTest {

    @Test
    public void testGetters() {
        QuestDefinition def = new QuestDefinition(
                "rotten_flesh_1", "§2腐肉の処理", "ゾンビの腐肉を集めて納品しよう",
                Material.ROTTEN_FLESH, 32, 40);

        assertEquals("rotten_flesh_1", def.getQuestId());
        assertEquals("§2腐肉の処理", def.getDisplayName());
        assertEquals("ゾンビの腐肉を集めて納品しよう", def.getDescription());
        assertEquals(Material.ROTTEN_FLESH, def.getTargetMaterial());
        assertEquals(32, def.getRequiredAmount());
        assertEquals(40, def.getRewardNuggets());
    }
}
