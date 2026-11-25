package org.tofu.tofunomics.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class AssetRankingSyncTest {

    /** 送った内容を覚え、決めた応答を返す偽の送信係 */
    private static class FakeClient implements StatsUpsertClient {
        final List<String> urls = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        int status = 200;
        String responseBody = "{\"success_count\":0,\"failed_count\":0,\"message\":\"ok\",\"errors\":[]}";
        boolean throwIo = false;

        @Override
        public Response put(String url, String apiKey, String json) throws IOException {
            urls.add(url);
            keys.add(apiKey);
            bodies.add(json);
            if (throwIo) {
                throw new IOException("timeout");
            }
            return new Response(status, responseBody);
        }
    }

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private FakeClient client;
    private AssetRankingSync sync;
    private AssetRankingSettings active;

    @Before
    public void setUp() {
        client = new FakeClient();
        sync = new AssetRankingSync(client, Logger.getLogger("test"));
        active = new AssetRankingSettings(true, true, "https://example.run.app/api", "key", 5, 10, 100);
    }

    private static List<AssetRankingSync.BalanceEntry> entries(long a, long b) {
        List<AssetRankingSync.BalanceEntry> list = new ArrayList<>();
        list.add(new AssetRankingSync.BalanceEntry(A, "Alice", a));
        list.add(new AssetRankingSync.BalanceEntry(B, "Bob", b));
        return list;
    }

    @Test
    public void 本文は契約どおりの形() {
        String body = AssetRankingSync.buildBody(entries(123, 0), 5);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(1, root.size());
        JsonArray stats = root.getAsJsonArray("stats");
        assertEquals(2, stats.size());
        JsonObject first = stats.get(0).getAsJsonObject();
        assertEquals(6, first.size());
        assertEquals(A.toString(), first.get("player_uuid").getAsString());
        assertEquals("Alice", first.get("player_name").getAsString());
        assertEquals(5, first.get("world_id").getAsInt());
        assertEquals("bank_balance", first.get("stat_type").getAsString());
        assertEquals("1970-01-01", first.get("stat_date").getAsString());
        // 整数で送る（123.0 ではない）
        assertEquals("123", first.get("value").toString());
    }

    @Test
    public void 残高は小数点以下を切り捨て_名前が無い人は飛ばす() {
        Map<UUID, Double> balances = new LinkedHashMap<>();
        balances.put(A, 99.99);
        balances.put(B, 10.0);
        List<AssetRankingSync.BalanceEntry> list =
                AssetRankingSync.toEntries(balances, uuid -> uuid.equals(A) ? "Alice" : null);
        assertEquals(1, list.size());
        assertEquals(99L, list.get(0).value());
    }

    @Test
    public void 初回は全員_次からは変わった人だけ送る_減っても送る() {
        sync.sync(entries(100, 50), active);
        assertEquals(1, client.bodies.size());
        assertEquals(2, JsonParser.parseString(client.bodies.get(0)).getAsJsonObject().getAsJsonArray("stats").size());

        // 変わらなければ送らない
        AssetRankingSync.Result none = sync.sync(entries(100, 50), active);
        assertEquals(0, none.sent());
        assertEquals(1, client.bodies.size());

        // B だけ減った
        sync.sync(entries(100, 20), active);
        assertEquals(2, client.bodies.size());
        JsonArray stats = JsonParser.parseString(client.bodies.get(1)).getAsJsonObject().getAsJsonArray("stats");
        assertEquals(1, stats.size());
        assertEquals(B.toString(), stats.get(0).getAsJsonObject().get("player_uuid").getAsString());
        assertEquals(20, stats.get(0).getAsJsonObject().get("value").getAsInt());
    }

    @Test
    public void 送り先とキー() {
        sync.sync(entries(1, 2), active);
        assertEquals("https://example.run.app/api/mc/stats/bulk/upsert", client.urls.get(0));
        assertEquals("key", client.keys.get(0));
    }

    @Test
    public void 無効のときは送らない() {
        sync.sync(entries(1, 2), new AssetRankingSettings(true, false, "https://x", "k", 5, 10, 100));
        sync.sync(entries(1, 2), new AssetRankingSettings(false, true, "https://x", "k", 5, 10, 100));
        sync.sync(entries(1, 2), null);
        assertTrue(client.bodies.isEmpty());
    }

    @Test
    public void 人数の上限で分けて送る() {
        AssetRankingSettings small = new AssetRankingSettings(true, true, "https://x", "", 5, 10, 2);
        List<AssetRankingSync.BalanceEntry> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new AssetRankingSync.BalanceEntry(UUID.randomUUID(), "p" + i, i));
        }
        AssetRankingSync.Result r = sync.sync(list, small);
        assertEquals(3, client.bodies.size());
        assertEquals(5, r.succeeded());
    }

    @Test
    public void 通信に失敗した人は次回また送る() {
        client.throwIo = true;
        AssetRankingSync.Result r = sync.sync(entries(100, 50), active);
        assertEquals(2, r.failed());
        assertTrue(sync.getLastSent().isEmpty());

        client.throwIo = false;
        sync.sync(entries(100, 50), active);
        assertEquals(2, JsonParser.parseString(client.bodies.get(1)).getAsJsonObject().getAsJsonArray("stats").size());
    }

    @Test
    public void HTTPエラーなら誰も更新しない() {
        client.status = 401;
        client.responseBody = "{\"detail\":\"invalid key\"}";
        AssetRankingSync.Result r = sync.sync(entries(100, 50), active);
        assertEquals(0, r.succeeded());
        assertTrue(sync.getLastSent().isEmpty());
    }

    @Test
    public void errors_に出た人だけ次回また送る() {
        client.responseBody = "{\"success_count\":1,\"failed_count\":1,\"message\":\"\",\"errors\":[{\"player_uuid\":\""
                + B + "\",\"stat_type\":\"bank_balance\",\"stat_date\":\"1970-01-01\",\"error\":\"x\"}]}";
        AssetRankingSync.Result r = sync.sync(entries(100, 50), active);
        assertEquals(1, r.succeeded());
        assertEquals(Long.valueOf(100), sync.getLastSent().get(A));
        assertFalse(sync.getLastSent().containsKey(B));
    }

    @Test
    public void failed_count_があるのに内訳が無ければ全員を次回また送る() {
        client.responseBody = "{\"success_count\":1,\"failed_count\":1,\"message\":\"\",\"errors\":[]}";
        sync.sync(entries(100, 50), active);
        assertTrue(sync.getLastSent().isEmpty());
    }

    @Test
    public void reset_すると次は全員送る() {
        sync.sync(entries(100, 50), active);
        sync.reset();
        AssetRankingSync.Result r = sync.sync(entries(100, 50), active);
        assertEquals(2, r.sent());
    }
}
