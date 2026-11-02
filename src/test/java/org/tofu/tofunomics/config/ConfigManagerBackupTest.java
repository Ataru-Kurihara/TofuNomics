package org.tofu.tofunomics.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 起動の流れ（TofuNomics.onEnable と同じ呼び順）で、バックアップが増えないことの回帰テスト。
 * 以前は内容が同じでも起動 1 回で最大 5 個作られていた。
 */
public class ConfigManagerBackupTest {

    /** 本番の config.yml に無く、jar 同梱のデフォルト層でだけ足りているキーを含むテンプレート */
    private static final String TEMPLATE = "config_version: \"2.1\"\nsection:\n  in_file: 1\n  only_in_template: 2\n";
    private static final String CONFIG_WITHOUT_KEY = "config_version: '2.1'\nsection:\n  in_file: 1\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File dir;
    private File configFile;
    private JavaPlugin plugin;
    private YamlConfiguration loaded;

    @Before
    public void setUp() {
        dir = folder.getRoot();
        configFile = new File(dir, "config.yml");
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getResource("config.yml")).thenAnswer(invocation -> new ByteArrayInputStream(bytes(TEMPLATE)));
        // JavaPlugin.reloadConfig と同じく、ファイルを読み直して jar 同梱の内容をデフォルト層に入れる
        doAnswer(invocation -> {
            loaded = YamlConfiguration.loadConfiguration(configFile);
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.loadFromString(TEMPLATE);
            loaded.setDefaults(defaults);
            return null;
        }).when(plugin).reloadConfig();
        when(plugin.getConfig()).thenAnswer(invocation -> loaded);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private int backupCount() {
        return dir.list((d, name) -> name.startsWith("config_backup_")).length;
    }

    /** TofuNomics.onEnable の設定まわりの呼び順 */
    private void simulateStartup() {
        ConfigManager manager = new ConfigManager(plugin);
        manager.migrateConfig();
        manager.updateConfigWithDefaults();
        manager.saveConfigSlim();
        manager.saveConfigSlim();
        manager.reloadConfig();
    }

    @Test
    public void デフォルト層で足りていれば起動してもバックアップを作らない() throws IOException {
        Files.write(configFile.toPath(), bytes(CONFIG_WITHOUT_KEY));
        simulateStartup();
        simulateStartup();
        assertEquals(0, backupCount());
    }

    @Test
    public void バージョンが変わるマイグレーションでは1個だけ作り元の内容を残す() throws IOException {
        String old = "config_version: '1.0'\nsection:\n  in_file: 1\n";
        Files.write(configFile.toPath(), bytes(old));
        simulateStartup();
        assertEquals(1, backupCount());
        File backup = dir.listFiles((d, name) -> name.startsWith("config_backup_"))[0];
        assertEquals(old, new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void 起動時に上限を超えた古いバックアップを消す() throws IOException {
        Files.write(configFile.toPath(), bytes(CONFIG_WITHOUT_KEY));
        for (long millis = 1; millis <= ConfigBackupStore.MAX_GENERATIONS + 5; millis++) {
            Files.write(new File(dir, "config_backup_" + millis + ".yml").toPath(), bytes("x"));
        }
        new ConfigManager(plugin);
        assertEquals(ConfigBackupStore.MAX_GENERATIONS, backupCount());
        assertFalse(new File(dir, "config_backup_5.yml").exists());
        assertTrue(new File(dir, "config_backup_6.yml").exists());
    }

    @Test
    public void 手動のバックアップは内容が同じならtrueを返して増やさない() throws IOException {
        Files.write(configFile.toPath(), bytes(CONFIG_WITHOUT_KEY));
        ConfigManager manager = new ConfigManager(plugin);
        assertTrue(manager.createConfigBackup());
        assertTrue(manager.createConfigBackup());
        assertEquals(1, backupCount());
    }
}
