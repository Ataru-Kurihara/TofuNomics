package org.tofu.tofunomics.config;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ConfigBackupStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File dir;
    private File config;

    @Before
    public void setUp() throws IOException {
        dir = folder.getRoot();
        config = new File(dir, "config.yml");
        write(config, "a: 1\n");
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private boolean exists(long millis) {
        return new File(dir, "config_backup_" + millis + ".yml").exists();
    }

    @Test
    public void 最初のバックアップは作られる() throws IOException {
        File created = ConfigBackupStore.createIfChanged(config, dir, 100L);
        assertEquals("config_backup_100.yml", created.getName());
        assertTrue(exists(100L));
    }

    @Test
    public void 直近と同じ内容なら作らない() throws IOException {
        ConfigBackupStore.createIfChanged(config, dir, 100L);
        assertNull(ConfigBackupStore.createIfChanged(config, dir, 200L));
        assertFalse(exists(200L));
    }

    @Test
    public void 内容が変わったら作る() throws IOException {
        ConfigBackupStore.createIfChanged(config, dir, 100L);
        write(config, "a: 2\n");
        assertNotNull(ConfigBackupStore.createIfChanged(config, dir, 200L));
        assertTrue(exists(200L));
    }

    @Test
    public void 比べる相手は最も新しいバックアップ() throws IOException {
        ConfigBackupStore.createIfChanged(config, dir, 100L);
        write(config, "a: 2\n");
        ConfigBackupStore.createIfChanged(config, dir, 200L);
        write(config, "a: 1\n");
        // 100 と同じ内容に戻しても、直近の 200 とは違うので作る
        assertNotNull(ConfigBackupStore.createIfChanged(config, dir, 300L));
    }

    @Test
    public void 上限を超えた古いものから消す() throws IOException {
        // 桁数が違っても文字列順ではなく時刻の順で判定する
        for (long millis : new long[] {9L, 10L, 11L, 100L, 1000L}) {
            write(new File(dir, "config_backup_" + millis + ".yml"), "x");
        }
        assertEquals(3, ConfigBackupStore.prune(dir, 2));
        assertTrue(exists(1000L));
        assertTrue(exists(100L));
        assertFalse(exists(11L));
        assertFalse(exists(10L));
        assertFalse(exists(9L));
    }

    @Test
    public void 上限以内なら消さない() throws IOException {
        write(new File(dir, "config_backup_1.yml"), "x");
        assertEquals(0, ConfigBackupStore.prune(dir, 2));
        assertTrue(exists(1L));
    }

    @Test
    public void 名前が一致しないファイルは消さない() throws IOException {
        String[] others = {"config_backup_before_upgrade.yml", "config_backup_1.yml.bak", "config.yml.bak"};
        for (String name : others) {
            write(new File(dir, name), "x");
        }
        write(new File(dir, "config_backup_1.yml"), "x");
        assertEquals(1, ConfigBackupStore.prune(dir, 0));
        for (String name : others) {
            assertTrue(name, new File(dir, name).exists());
        }
        assertTrue(config.exists());
    }
}
