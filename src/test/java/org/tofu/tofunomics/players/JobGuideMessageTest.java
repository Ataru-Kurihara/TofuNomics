package org.tofu.tofunomics.players;

import org.bukkit.ChatColor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 入場時の職業案内の文面のテスト。
 * 以前は「/jobs join <職業名>」と日本語名を案内していたが、そのとおり打つと弾かれた。
 * また、Lv50 まで就けない上級職も「利用可能」と並べていた。
 */
public class JobGuideMessageTest {

    private static final List<String> STARTER_JOBS = Arrays.asList("鉱夫", "木こり", "農家", "釣り人", "鍛冶屋");

    private String plain(List<String> lines) {
        return ChatColor.stripColor(String.join("\n", lines));
    }

    @Test
    public void 未就職の人にはjobsの画面とLv50の注意を案内する() {
        String text = plain(PlayerJoinHandler.buildJobGuideLines(null, STARTER_JOBS));
        assertTrue(text.contains("/jobs"));
        assertTrue(text.contains("Lv50 になるまで選び直せません"));
        assertTrue(text.contains("鉱夫 | 木こり | 農家 | 釣り人 | 鍛冶屋"));
    }

    @Test
    public void 職業名を打たせる案内や実在しない案内はしない() {
        String text = plain(PlayerJoinHandler.buildJobGuideLines(null, STARTER_JOBS));
        assertFalse(text.contains("/jobs join"));
        assertFalse(text.contains("/quest"));
    }

    @Test
    public void 渡していない上級職は並ばない() {
        String text = plain(PlayerJoinHandler.buildJobGuideLines(null, STARTER_JOBS));
        assertFalse(text.contains("ポーション屋"));
        assertFalse(text.contains("エンチャンター"));
        assertFalse(text.contains("建築家"));
    }

    @Test
    public void 就職済みの人には1行だけ出す() {
        List<String> lines = PlayerJoinHandler.buildJobGuideLines("鉱夫", STARTER_JOBS);
        assertEquals(1, lines.size());
        assertTrue(plain(lines).contains("現在の職業: 鉱夫"));
    }
}
