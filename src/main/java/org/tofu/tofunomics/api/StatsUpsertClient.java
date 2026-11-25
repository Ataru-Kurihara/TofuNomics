package org.tofu.tofunomics.api;

import java.io.IOException;

/**
 * 統計の一括 upsert を送る係。テストで差し替えられるようにインターフェースにしている。
 */
public interface StatsUpsertClient {

    /**
     * PUT で JSON を送り、応答を返す。通信そのものの失敗は IOException。
     *
     * @param url    送り先
     * @param apiKey X-Mc-Api-Key に入れる値（空ならヘッダーを付けない）
     * @param json   本文
     */
    Response put(String url, String apiKey, String json) throws IOException, InterruptedException;

    /** HTTP の応答（状態コードと本文） */
    final class Response {
        private final int statusCode;
        private final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
    }
}
