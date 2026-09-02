package org.tofu.tofunomics.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * チャットのワールド分離テスト
 *
 * 分離対象のワールドはそれぞれ独立した部屋として扱い、
 * 対象外のワールドは従来どおり1つの部屋を共有する。
 */
public class WorldChatIsolationTest {

    private static final Set<String> ISOLATED = new HashSet<>(Collections.singletonList("tofuNomics"));

    private static boolean deliver(String from, String to) {
        return WorldChatIsolationListener.shouldDeliver(from, to, ISOLATED);
    }

    @Test
    public void 分離ワールド内の発言は同じワールドに届く() {
        assertTrue(deliver("tofuNomics", "tofuNomics"));
    }

    @Test
    public void 分離ワールドの発言は外に出ない() {
        assertFalse(deliver("tofuNomics", "world"));
        assertFalse(deliver("tofuNomics", "TofuColosseum"));
    }

    @Test
    public void 外の発言は分離ワールドに入らない() {
        assertFalse(deliver("world", "tofuNomics"));
        assertFalse(deliver("TofuColosseum", "tofuNomics"));
    }

    @Test
    public void 分離対象外どうしは従来どおり共有される() {
        assertTrue(deliver("world", "TofuColosseum"));
        assertTrue(deliver("TofuColosseum", "world"));
        assertTrue(deliver("world", "world"));
    }

    @Test
    public void 複数ワールドを分離するとそれぞれ独立する() {
        Set<String> isolated = new HashSet<>(Arrays.asList("tofuNomics", "TofuColosseum"));

        assertTrue("同じ分離ワールド内は届く",
            WorldChatIsolationListener.shouldDeliver("tofuNomics", "tofuNomics", isolated));
        assertFalse("分離ワールドどうしは混ざらない",
            WorldChatIsolationListener.shouldDeliver("tofuNomics", "TofuColosseum", isolated));
        assertFalse("分離ワールドから対象外へは出ない",
            WorldChatIsolationListener.shouldDeliver("tofuNomics", "world", isolated));
        assertTrue("対象外どうしは共有される",
            WorldChatIsolationListener.shouldDeliver("world", "lobby", isolated));
    }

    @Test
    public void 分離設定が空なら全員に届く() {
        Set<String> none = Collections.emptySet();

        assertTrue(WorldChatIsolationListener.shouldDeliver("tofuNomics", "world", none));
        assertTrue(WorldChatIsolationListener.shouldDeliver("world", "tofuNomics", none));
    }

    @Test
    public void ワールド名が取得できない場合は配信する() {
        // 判定できないことを理由にチャットを消してしまわない
        assertTrue(deliver(null, "tofuNomics"));
        assertTrue(deliver("tofuNomics", null));
    }
}
