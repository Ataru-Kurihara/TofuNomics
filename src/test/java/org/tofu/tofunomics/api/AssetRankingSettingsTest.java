package org.tofu.tofunomics.api;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.*;

public class AssetRankingSettingsTest {

    @Test
    public void base_urlの末尾のスラッシュと_apiを取り除く() {
        assertEquals("https://example.run.app", AssetRankingSettings.normalizeBaseUrl("https://example.run.app"));
        assertEquals("https://example.run.app", AssetRankingSettings.normalizeBaseUrl("https://example.run.app/"));
        assertEquals("https://example.run.app", AssetRankingSettings.normalizeBaseUrl("https://example.run.app/api"));
        assertEquals("https://example.run.app", AssetRankingSettings.normalizeBaseUrl(" https://example.run.app/api/ "));
        assertEquals("", AssetRankingSettings.normalizeBaseUrl(null));
    }

    @Test
    public void 送り先は_api_mc_stats_bulk_upsert() {
        AssetRankingSettings s = new AssetRankingSettings(true, true, "https://example.run.app/api/", "k", 5, 10, 100);
        assertEquals("https://example.run.app/api/mc/stats/bulk/upsert", s.getUpsertUrl());
    }

    @Test
    public void 設定から読む_キーは前後の空白と改行を取り除く() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "api:\n" +
                "  tohu_app:\n" +
                "    enabled: true\n" +
                "    base_url: \"https://example.run.app\"\n" +
                "    token: \" secret\\n\"\n" +
                "    world_id: 5\n" +
                "    asset_ranking:\n" +
                "      enabled: true\n" +
                "      interval_minutes: 3\n" +
                "      batch_size: 50\n");
        AssetRankingSettings s = AssetRankingSettings.fromConfig(yaml.getConfigurationSection("api.tohu_app"));
        assertTrue(s.isActive());
        assertEquals("secret", s.getApiKey());
        assertEquals(5, s.getWorldId());
        assertEquals(3, s.getIntervalMinutes());
        assertEquals(50, s.getBatchSize());
    }

    @Test
    public void asset_ranking_の節が無ければ無効_間隔は既定10分() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "api:\n" +
                "  tohu_app:\n" +
                "    enabled: true\n" +
                "    base_url: \"https://example.run.app\"\n" +
                "    world_id: 5\n");
        AssetRankingSettings s = AssetRankingSettings.fromConfig(yaml.getConfigurationSection("api.tohu_app"));
        assertFalse(s.isActive());
        assertEquals(10, s.getIntervalMinutes());
        assertEquals(100, s.getBatchSize());
    }

    @Test
    public void どちらかのスイッチが切れていれば無効() {
        assertFalse(new AssetRankingSettings(false, true, "https://x", "", 5, 10, 100).isActive());
        assertFalse(new AssetRankingSettings(true, false, "https://x", "", 5, 10, 100).isActive());
        assertFalse(new AssetRankingSettings(true, true, "", "", 5, 10, 100).isActive());
        assertFalse(AssetRankingSettings.fromConfig(null).isActive());
    }

    @Test
    public void 送り先かワールドが変われば別の送り先() {
        AssetRankingSettings a = new AssetRankingSettings(true, true, "https://x/api", "", 5, 10, 100);
        assertTrue(a.sameTarget(new AssetRankingSettings(true, true, "https://x", "other", 5, 3, 10)));
        assertFalse(a.sameTarget(new AssetRankingSettings(true, true, "https://y", "", 5, 10, 100)));
        assertFalse(a.sameTarget(new AssetRankingSettings(true, true, "https://x", "", 1, 10, 100)));
        assertFalse(a.sameTarget(null));
    }
}
