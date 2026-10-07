package org.tofu.tofunomics.scoreboard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * スコアボードの負荷対策のテスト。
 * 「変わった行だけ書き換える」判定と、「DB の値を数秒使い回す」キャッシュを確かめる。
 */
public class SidebarAndCacheTest {

    @Test
    public void 同じ内容なら書き換える行は無い() {
        List<String> lines = Arrays.asList("時刻: 12:00", "預金: 10G");
        assertTrue(PlayerSidebar.changedIndices(lines, lines).isEmpty());
    }

    @Test
    public void 変わった行だけを書き換える() {
        List<String> before = Arrays.asList("時刻: 12:00", "職業: 鉱夫", "預金: 10G");
        List<String> after = Arrays.asList("時刻: 12:01", "職業: 鉱夫", "預金: 15G");
        assertEquals(Arrays.asList(0, 2), PlayerSidebar.changedIndices(before, after));
    }

    @Test
    public void 初回と行が増えた分はすべて書く() {
        assertEquals(Arrays.asList(0, 1),
                PlayerSidebar.changedIndices(Collections.emptyList(), Arrays.asList("a", "b")));
        assertEquals(Collections.singletonList(1),
                PlayerSidebar.changedIndices(Collections.singletonList("a"), Arrays.asList("a", "b")));
    }

    @Test
    public void 行の目印は行ごとに違う() {
        Set<String> entries = new HashSet<>();
        for (int i = 0; i < PlayerSidebar.MAX_LINES; i++) {
            entries.add(PlayerSidebar.entryFor(i));
        }
        assertEquals(PlayerSidebar.MAX_LINES, entries.size());
    }

    @Test
    public void 上限を超えた行は切り捨てる() {
        String[] many = new String[20];
        Arrays.fill(many, "x");
        assertEquals(PlayerSidebar.MAX_LINES, PlayerSidebar.limit(Arrays.asList(many)).size());
    }

    @Test
    public void 期限内は読み直さない() {
        AtomicLong now = new AtomicLong(0);
        AtomicInteger loads = new AtomicInteger();
        TimedCache<String, Integer> cache = new TimedCache<>(5000, now::get);

        assertEquals(Integer.valueOf(1), cache.get("p", loads::incrementAndGet));
        now.set(4999);
        assertEquals(Integer.valueOf(1), cache.get("p", loads::incrementAndGet));
        assertEquals("DB を読むのは 1 回だけ", 1, loads.get());
    }

    @Test
    public void 期限を過ぎたら読み直す() {
        AtomicLong now = new AtomicLong(0);
        AtomicInteger loads = new AtomicInteger();
        TimedCache<String, Integer> cache = new TimedCache<>(5000, now::get);

        cache.get("p", loads::incrementAndGet);
        now.set(5000);
        assertEquals(Integer.valueOf(2), cache.get("p", loads::incrementAndGet));
    }

    @Test
    public void 捨てたらすぐ読み直す() {
        AtomicLong now = new AtomicLong(0);
        AtomicInteger loads = new AtomicInteger();
        TimedCache<String, Integer> cache = new TimedCache<>(5000, now::get);

        cache.get("p", loads::incrementAndGet);
        cache.invalidate("p");
        assertEquals(Integer.valueOf(2), cache.get("p", loads::incrementAndGet));
    }

    @Test
    public void 読めなかった値は覚えない() {
        AtomicInteger loads = new AtomicInteger();
        TimedCache<String, Integer> cache = new TimedCache<>(5000, () -> 0L);

        assertNull(cache.get("p", () -> { loads.incrementAndGet(); return null; }));
        assertNull(cache.get("p", () -> { loads.incrementAndGet(); return null; }));
        assertEquals(2, loads.get());
    }
}
