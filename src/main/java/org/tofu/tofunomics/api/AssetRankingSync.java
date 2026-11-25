package org.tofu.tofunomics.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 銀行残高を tohu-app の資産ランキングへ送る中身（Bukkit に依存しない部分）。
 *
 * - 送るのは「今の銀行残高」（差分ではない）。受け口は置き換えで保存する。
 * - 前回送った値から変わった人だけ送る。起動直後（前回の値が無い）は全員送る。
 * - 送れた人だけ「前回送った値」を更新する。失敗した人は次回また送る。
 */
public class AssetRankingSync {

    /** 送る 1 人分 */
    public record BalanceEntry(UUID uuid, String name, long value) { }

    /** 1 回の同期の結果（ログとテスト用） */
    public record Result(int candidates, int sent, int succeeded, int failed) { }

    private static final Gson GSON = new Gson();
    private static final int MAX_ERRORS_IN_LOG = 3;
    private static final int MAX_BODY_IN_LOG = 300;

    private final StatsUpsertClient client;
    private final Logger logger;
    /** 前回送れた値。非同期スレッドから書くので並行用の Map */
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    public AssetRankingSync(StatsUpsertClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    /**
     * DB の残高と名前の引き方から、送る候補を作る。値は小数点以下を切り捨てる。名前が引けない人は飛ばす。
     */
    public static List<BalanceEntry> toEntries(Map<UUID, Double> balances, Function<UUID, String> nameLookup) {
        List<BalanceEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            String name = nameLookup.apply(e.getKey());
            if (name == null || name.isEmpty()) {
                continue;
            }
            double balance = e.getValue() == null ? 0.0 : e.getValue();
            entries.add(new BalanceEntry(e.getKey(), name, (long) Math.floor(balance)));
        }
        return entries;
    }

    /** 前回送った値と違う人だけを返す */
    public List<BalanceEntry> selectChanged(List<BalanceEntry> current) {
        List<BalanceEntry> changed = new ArrayList<>();
        for (BalanceEntry entry : current) {
            Long previous = lastSent.get(entry.uuid());
            if (previous == null || previous != entry.value()) {
                changed.add(entry);
            }
        }
        return changed;
    }

    /** 契約どおりの本文を作る */
    public static String buildBody(List<BalanceEntry> entries, int worldId) {
        JsonArray stats = new JsonArray();
        for (BalanceEntry entry : entries) {
            JsonObject stat = new JsonObject();
            stat.addProperty("player_uuid", entry.uuid().toString());
            stat.addProperty("player_name", entry.name());
            stat.addProperty("world_id", worldId);
            stat.addProperty("stat_type", AssetRankingSettings.STAT_TYPE);
            stat.addProperty("stat_date", AssetRankingSettings.STAT_DATE);
            stat.addProperty("value", entry.value());
            stats.add(stat);
        }
        JsonObject root = new JsonObject();
        root.add("stats", stats);
        return GSON.toJson(root);
    }

    /**
     * 変わった人だけを batch_size 人ずつ送る。無効のときは何もしない。
     * 非同期スレッドから呼ぶ前提（通信で待つため）。
     */
    public Result sync(List<BalanceEntry> current, AssetRankingSettings settings) {
        if (settings == null || !settings.isActive()) {
            return new Result(0, 0, 0, 0);
        }
        List<BalanceEntry> changed = selectChanged(current);
        int sent = 0;
        int succeeded = 0;
        int failed = 0;
        for (int from = 0; from < changed.size(); from += settings.getBatchSize()) {
            List<BalanceEntry> batch = changed.subList(from, Math.min(changed.size(), from + settings.getBatchSize()));
            sent += batch.size();
            int ok = sendBatch(batch, settings);
            succeeded += ok;
            failed += batch.size() - ok;
        }
        return new Result(changed.size(), sent, succeeded, failed);
    }

    /** 1 回分を送り、送れた人数を返す */
    private int sendBatch(List<BalanceEntry> batch, AssetRankingSettings settings) {
        String body = buildBody(batch, settings.getWorldId());
        StatsUpsertClient.Response response;
        try {
            response = client.put(settings.getUpsertUrl(), settings.getApiKey(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("[資産ランキング] 送信が中断されました（" + batch.size() + " 人分）");
            return 0;
        } catch (Exception e) {
            logger.warning("[資産ランキング] 送信に失敗しました（" + batch.size() + " 人分）: " + e);
            return 0;
        }

        if (!response.isSuccess()) {
            logger.warning("[資産ランキング] HTTP " + response.getStatusCode() + " が返りました（"
                    + batch.size() + " 人分）: " + truncate(response.getBody()));
            return 0;
        }

        Set<String> failedUuids = new HashSet<>();
        int failedCount = 0;
        List<String> errorSamples = new ArrayList<>();
        try {
            JsonObject json = JsonParser.parseString(response.getBody()).getAsJsonObject();
            if (json.has("failed_count") && !json.get("failed_count").isJsonNull()) {
                failedCount = json.get("failed_count").getAsInt();
            }
            if (json.has("errors") && json.get("errors").isJsonArray()) {
                for (JsonElement error : json.getAsJsonArray("errors")) {
                    if (error.isJsonObject() && error.getAsJsonObject().has("player_uuid")) {
                        failedUuids.add(error.getAsJsonObject().get("player_uuid").getAsString());
                    }
                    if (errorSamples.size() < MAX_ERRORS_IN_LOG) {
                        errorSamples.add(error.toString());
                    }
                }
            }
        } catch (Exception e) {
            // 2xx なのに本文が読めないときは、送れたものとして扱う（次回また送ると二重にはならない＝置き換え保存のため）
            logger.warning("[資産ランキング] 応答を読めませんでした: " + truncate(response.getBody()));
        }

        if (failedCount > 0) {
            logger.warning("[資産ランキング] " + failedCount + " 件が保存に失敗しました。例: " + errorSamples);
        }

        // 失敗の内訳が分からないのに failed_count があるときは、全員を次回また送る
        boolean unknownFailures = failedCount > 0 && failedUuids.isEmpty();
        int ok = 0;
        for (BalanceEntry entry : batch) {
            if (unknownFailures || failedUuids.contains(entry.uuid().toString())) {
                continue;
            }
            lastSent.put(entry.uuid(), entry.value());
            ok++;
        }
        return ok;
    }

    /** 前回送った値を捨てる（送り先が変わったときなど。次回は全員送る） */
    public void reset() {
        lastSent.clear();
    }

    /** テスト用 */
    Map<UUID, Long> getLastSent() {
        return lastSent;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_BODY_IN_LOG ? s : s.substring(0, MAX_BODY_IN_LOG) + "...";
    }
}
