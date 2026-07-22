package org.tofu.tofunomics.config;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 職業制限（採掘・植え付け・クラフト）のワールド適用範囲テスト。
 *
 * 背景: 無職プレイヤーのブロック破壊制限が tofuNomics 以外のワールド
 * （ロビー・ミニゲーム等）でも発動していたため、ConfigManager に判定を集約した。
 *
 * ConfigManager のコンストラクタは Bukkit のファイルIOに依存するため、
 * ここではワールド判定に必要なアクセサのみを差し替えて実ロジックを検証する。
 */
public class JobRestrictionWorldScopeTest {

    private ConfigManager configManager;

    @Before
    public void setUp() {
        configManager = mock(ConfigManager.class);
        when(configManager.isJobRestrictionEnabledInWorld(anyString())).thenCallRealMethod();
        when(configManager.isJobRestrictionEnabledInWorld(isNull(String.class))).thenCallRealMethod();
        when(configManager.isEconomyEnabledInWorld(anyString())).thenCallRealMethod();
    }

    private void setWorlds(List<String> excluded, List<String> enabled) {
        when(configManager.getExcludedWorlds()).thenReturn(excluded);
        when(configManager.getEconomyEnabledWorlds()).thenReturn(enabled);
    }

    @Test
    public void ホワイトリストが空なら全ワールドで有効() {
        setWorlds(Collections.<String>emptyList(), Collections.<String>emptyList());

        assertTrue(configManager.isJobRestrictionEnabledInWorld("tofuNomics"));
        assertTrue(configManager.isJobRestrictionEnabledInWorld("world"));
    }

    @Test
    public void ホワイトリスト内のワールドのみ制限が有効() {
        setWorlds(Collections.<String>emptyList(), Collections.singletonList("tofuNomics"));

        assertTrue("対象ワールドでは制限を適用",
            configManager.isJobRestrictionEnabledInWorld("tofuNomics"));
        assertFalse("他ワールドには干渉しない",
            configManager.isJobRestrictionEnabledInWorld("world"));
        assertFalse("ロビーには干渉しない",
            configManager.isJobRestrictionEnabledInWorld("lobby"));
    }

    @Test
    public void 除外ワールドはホワイトリストに含まれていても無効() {
        setWorlds(Collections.singletonList("tofuNomics"),
                  Arrays.asList("tofuNomics", "world"));

        assertFalse(configManager.isJobRestrictionEnabledInWorld("tofuNomics"));
    }

    @Test
    public void ワールド名がnullなら無効() {
        setWorlds(Collections.<String>emptyList(), Collections.singletonList("tofuNomics"));

        assertFalse(configManager.isJobRestrictionEnabledInWorld(null));
    }
}
