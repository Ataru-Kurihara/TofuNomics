package org.tofu.tofunomics.api.client;

import com.google.gson.Gson;
import okhttp3.*;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.api.model.PlayerStatsData;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * tohu-app APIとの通信を担当するクライアント
 * POST /api/mc/stats エンドポイントを使用
 */
public class TohuAppApiClient {
    private final TofuNomics plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiBaseUrl;
    private final String apiToken;
    private final int maxRetryAttempts;
    private final int initialRetryDelay;

    public TohuAppApiClient(TofuNomics plugin) {
        this.plugin = plugin;
        this.apiBaseUrl = plugin.getConfig().getString("api.tohu_app.base_url");
        this.apiToken = plugin.getConfig().getString("api.tohu_app.token", "");
        this.maxRetryAttempts = plugin.getConfig().getInt("api.tohu_app.retry.max_attempts", 3);
        this.initialRetryDelay = plugin.getConfig().getInt("api.tohu_app.retry.initial_delay_seconds", 5);

        // OkHttpClientの初期化（タイムアウト設定）
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        this.gson = new Gson();
    }

    /**
     * 単一の統計データを送信
     *
     * @param data 送信する統計データ
     * @return 送信成功時true、失敗時false
     */
    public boolean submitStats(PlayerStatsData data) {
        try {
            return executeWithRetry(() -> sendStatsRequest(data));
        } catch (Exception e) {
            plugin.getLogger().severe("統計データ送信に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * 実際のHTTPリクエストを送信
     */
    private boolean sendStatsRequest(PlayerStatsData data) throws IOException {
        // リクエストボディをJSON化
        String json = gson.toJson(data);

        // デバッグモード時はデータをログ出力
        if (plugin.getConfig().getBoolean("api.tohu_app.debug", false)) {
            plugin.getLogger().info("送信データ: " + json);
        }

        RequestBody body = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );

        // HTTPリクエストを構築
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiBaseUrl + "/api/mc/stats")
                .post(body);

        // 認証トークンが設定されている場合はヘッダーに追加
        if (apiToken != null && !apiToken.isEmpty()) {
            requestBuilder.header("X-API-Key", apiToken);
        }

        Request request = requestBuilder.build();

        // リクエスト送信
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                plugin.getLogger().fine("統計データ送信成功: " + data.getPlayer_name());
                return true;
            } else {
                // HTTP 4xxエラー（クライアントエラー）の場合はリトライしない
                if (response.code() >= 400 && response.code() < 500) {
                    plugin.getLogger().warning(String.format(
                            "統計データ送信失敗（クライアントエラー）: HTTP %d - %s",
                            response.code(), data.getPlayer_name()
                    ));
                    return false;
                }

                // HTTP 5xxエラー（サーバーエラー）の場合は例外をスロー（リトライ対象）
                throw new IOException("サーバーエラー: HTTP " + response.code());
            }
        }
    }

    /**
     * リトライ処理を含むリクエスト実行
     * ネットワークエラー、タイムアウト、HTTP 5xxエラーの場合にリトライ
     */
    private boolean executeWithRetry(IOOperation operation) throws Exception {
        int delaySeconds = initialRetryDelay;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                return operation.execute();
            } catch (IOException e) {
                if (attempt == maxRetryAttempts) {
                    // 最終試行で失敗した場合は例外をスロー
                    throw e;
                }

                plugin.getLogger().warning(String.format(
                        "API送信失敗（%d/%d回目）、%d秒後にリトライします: %s",
                        attempt, maxRetryAttempts, delaySeconds, e.getMessage()
                ));

                // 指数バックオフ（待機時間を2倍にする）
                Thread.sleep(delaySeconds * 1000L);
                delaySeconds *= 2;
            }
        }

        throw new RuntimeException("リトライ処理が異常終了しました");
    }

    /**
     * IO操作を表す関数型インターフェース
     */
    @FunctionalInterface
    private interface IOOperation {
        boolean execute() throws IOException;
    }

    /**
     * クライアントのクリーンアップ
     */
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}
