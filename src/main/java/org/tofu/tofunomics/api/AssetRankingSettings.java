package org.tofu.tofunomics.api;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 資産ランキング送信の設定（config/system.yml の api.tohu_app の下）。
 * 非同期の送信処理から読むため、メインスレッドで作った不変の値として渡す。
 */
public final class AssetRankingSettings {

    /** 送り先のパス（base_url の後ろに付ける） */
    public static final String UPSERT_PATH = "/api/mc/stats/bulk/upsert";
    /** 送る統計の種類 */
    public static final String STAT_TYPE = "bank_balance";
    /** 日付は受け口側で無視される。契約どおり固定値を送る */
    public static final String STAT_DATE = "1970-01-01";
    /** 認証ヘッダー名（TofuStats と同じ） */
    public static final String API_KEY_HEADER = "X-Mc-Api-Key";

    public static final int DEFAULT_INTERVAL_MINUTES = 10;
    public static final int DEFAULT_BATCH_SIZE = 100;

    private final boolean apiEnabled;
    private final boolean rankingEnabled;
    private final String baseUrl;
    private final String apiKey;
    private final int worldId;
    private final int intervalMinutes;
    private final int batchSize;

    public AssetRankingSettings(boolean apiEnabled, boolean rankingEnabled, String baseUrl, String apiKey,
                                int worldId, int intervalMinutes, int batchSize) {
        this.apiEnabled = apiEnabled;
        this.rankingEnabled = rankingEnabled;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.worldId = worldId;
        this.intervalMinutes = Math.max(1, intervalMinutes);
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * api.tohu_app セクションから読む。セクションが無ければ無効の設定を返す。
     */
    public static AssetRankingSettings fromConfig(ConfigurationSection tohuApp) {
        if (tohuApp == null) {
            return new AssetRankingSettings(false, false, "", "", 1, DEFAULT_INTERVAL_MINUTES, DEFAULT_BATCH_SIZE);
        }
        return new AssetRankingSettings(
                tohuApp.getBoolean("enabled", false),
                tohuApp.getBoolean("asset_ranking.enabled", false),
                tohuApp.getString("base_url", ""),
                tohuApp.getString("token", ""),
                tohuApp.getInt("world_id", 1),
                tohuApp.getInt("asset_ranking.interval_minutes", DEFAULT_INTERVAL_MINUTES),
                tohuApp.getInt("asset_ranking.batch_size", DEFAULT_BATCH_SIZE));
    }

    /**
     * base_url の前後の空白、末尾の "/" と "/api" を取り除く。
     * 本番は "/api" なし、TofuStats の設定は "/api" ありで書かれているため、どちらでも同じ送り先にする。
     */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
                changed = true;
            }
            if (url.endsWith("/api")) {
                url = url.substring(0, url.length() - "/api".length());
                changed = true;
            }
        }
        return url;
    }

    /** 両方のスイッチが入っていて、送り先があるときだけ送る */
    public boolean isActive() {
        return apiEnabled && rankingEnabled && !baseUrl.isEmpty();
    }

    public String getUpsertUrl() {
        return baseUrl + UPSERT_PATH;
    }

    /** 送り先が変わったか（変わったら「前回送った値」を捨てて全員送り直す） */
    public boolean sameTarget(AssetRankingSettings other) {
        return other != null && baseUrl.equals(other.baseUrl) && worldId == other.worldId;
    }

    public boolean isApiEnabled() { return apiEnabled; }
    public boolean isRankingEnabled() { return rankingEnabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public int getWorldId() { return worldId; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public int getBatchSize() { return batchSize; }
}
