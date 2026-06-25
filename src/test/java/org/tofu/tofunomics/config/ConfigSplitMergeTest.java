package org.tofu.tofunomics.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * config.yml 機能別分割のマージ整合テスト。
 *
 * ConfigManager.loadAuxConfigs() と同じ「補助ファイルの葉キーを primary に in-memory マージ」
 * ロジックを再現し、移動したセクションのネストキーが実際の Bukkit YamlConfiguration 経由で
 * 読み取れることを保証する。
 *
 * 背景: Bukkit の setDefaults() デフォルト層は、親サブツリー全体が primary に存在しない場合
 * config.getString(path, default) がネストした値へ到達できず指定デフォルトを返す既知の挙動が
 * あり、これにより銀行NPCメッセージ等が表示されない回帰が発生した。primary への実体マージで
 * これを回避していることをCIで検証する。
 */
public class ConfigSplitMergeTest {

    private static final String[] AUX_FILES = {"jobs.yml", "messages.yml", "gameplay.yml", "system.yml"};

    private YamlConfiguration loadMergedPrimary() {
        YamlConfiguration primary = YamlConfiguration.loadConfiguration(
            new File("src/main/resources/config.yml"));
        for (String fn : AUX_FILES) {
            YamlConfiguration aux = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/" + fn));
            for (String key : aux.getKeys(true)) {
                if (!aux.isConfigurationSection(key) && !primary.isSet(key)) {
                    primary.set(key, aux.get(key));
                }
            }
        }
        return primary;
    }

    @Test
    public void config_ymlには補助セクションが無い() {
        YamlConfiguration primary = YamlConfiguration.loadConfiguration(
            new File("src/main/resources/config.yml"));
        // 移動済みセクションは config.yml 単体には存在しない
        assertFalse("messages は補助ファイル側", primary.isSet("messages"));
        assertFalse("performance は補助ファイル側", primary.isSet("performance"));
        assertFalse("leveling は補助ファイル側", primary.isSet("leveling"));
        // サーバー固有/残置セクションは config.yml にある
        assertTrue("jobs.block_restrictions は config.yml 残置",
            primary.isSet("jobs.block_restrictions.enabled"));
    }

    @Test
    public void マージ後は全補助キーがgetStringで読める() {
        YamlConfiguration primary = loadMergedPrimary();
        final String SENTINEL = "__NOT_FOUND__";
        int checked = 0;
        for (String fn : AUX_FILES) {
            YamlConfiguration aux = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/" + fn));
            for (String key : aux.getKeys(true)) {
                if (aux.isConfigurationSection(key)) {
                    continue;
                }
                // primary 経由で「見つからない」を返さないこと（ネスト到達できていること）
                Object v = primary.get(key, null);
                assertNotNull("マージ後 primary から読めない: " + key, v);
                assertNotEquals("getString がデフォルトにフォールバックした: " + key,
                    SENTINEL, primary.getString(key, SENTINEL));
                checked++;
            }
        }
        assertTrue("検証キーが0件", checked > 100);
    }

    @Test
    public void 銀行NPCメッセージがマージ後に読める() {
        YamlConfiguration primary = loadMergedPrimary();
        // 回帰対象だった具体キー
        assertNotEquals(primary.getString("messages.npc.bank.welcome", "NF"), "NF");
        assertNotEquals(primary.getString("messages.npc.bank.too_far", "NF"), "NF");
    }
}
