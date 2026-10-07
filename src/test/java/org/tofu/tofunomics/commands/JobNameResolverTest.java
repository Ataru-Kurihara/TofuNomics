package org.tofu.tofunomics.commands;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * /jobs join などで、日本語の表示名（鉱夫）でも内部名（miner）でも職業を指定できることのテスト。
 */
public class JobNameResolverTest {

    private Map<String, String> jobs;

    @Before
    public void setUp() {
        jobs = new LinkedHashMap<>();
        jobs.put("miner", "鉱夫");
        jobs.put("woodcutter", "&a木こり");
        jobs.put("alchemist", "§dポーション屋");
    }

    @Test
    public void 内部名で解決できる() {
        assertEquals("miner", JobNameResolver.resolve("miner", jobs));
    }

    @Test
    public void 内部名は大文字小文字を区別しない() {
        assertEquals("miner", JobNameResolver.resolve("Miner", jobs));
    }

    @Test
    public void 日本語の表示名で解決できる() {
        assertEquals("miner", JobNameResolver.resolve("鉱夫", jobs));
    }

    @Test
    public void 色コード付きの表示名でも解決できる() {
        assertEquals("woodcutter", JobNameResolver.resolve("木こり", jobs));
        assertEquals("alchemist", JobNameResolver.resolve("ポーション屋", jobs));
    }

    @Test
    public void 前後の空白は無視する() {
        assertEquals("miner", JobNameResolver.resolve(" 鉱夫 ", jobs));
    }

    @Test
    public void 存在しない職業はnull() {
        assertNull(JobNameResolver.resolve("勇者", jobs));
        assertNull(JobNameResolver.resolve("", jobs));
        assertNull(JobNameResolver.resolve(null, jobs));
    }

    @Test
    public void 補完は表示名と内部名の両方を出す() {
        List<String> all = JobNameResolver.suggest("", jobs);
        assertEquals(Arrays.asList("鉱夫", "木こり", "ポーション屋", "miner", "woodcutter", "alchemist"), all);
    }

    @Test
    public void 補完は前方一致で絞り込む() {
        assertEquals(Arrays.asList("miner"), JobNameResolver.suggest("mi", jobs));
        assertEquals(Arrays.asList("鉱夫"), JobNameResolver.suggest("鉱", jobs));
    }

    @Test
    public void 非OPの補完に管理用サブコマンドは出ない() {
        List<String> forPlayer = JobsCommand.subCommandsFor(false);
        assertFalse(forPlayer.contains("admin"));
        assertFalse(forPlayer.contains("debug"));
        assertTrue(forPlayer.contains("join"));

        List<String> forAdmin = JobsCommand.subCommandsFor(true);
        assertTrue(forAdmin.contains("admin"));
        assertTrue(forAdmin.contains("debug"));
    }
}
