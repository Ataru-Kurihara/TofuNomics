package org.tofu.tofunomics.market;

/**
 * マーケット操作（出品・購入・キャンセル・回収）の結果コード。
 *
 * 各コードは {@code messages.market.*} のメッセージキーに対応する。
 * メッセージ送信は呼び出し側（コマンド/GUI 層）が結果コードに応じて行う。
 */
public enum MarketResult {

    // 成功
    LISTED("listed"),
    PURCHASED("purchased"),
    CANCELLED("cancelled"),
    RECLAIMED("reclaimed"),

    // 失敗（出品）
    INVALID_ITEM("invalid_item"),
    INVALID_PRICE("invalid_price"),
    CURRENCY_NOT_ALLOWED("currency_not_allowed"),
    LISTING_LIMIT("listing_limit"),

    // 失敗（購入・回収）
    NOT_AVAILABLE("not_available"),
    ALREADY_SOLD("already_sold"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    INVENTORY_FULL("inventory_full"),
    NOT_OWNER("not_owner"),

    // 失敗（共通）
    MARKET_DISABLED("error"),
    ERROR("error");

    private final String messageKey;

    MarketResult(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * 対応する messages.market 配下のメッセージキー（プレフィックス無し）を返す。
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * 成功結果かどうか
     */
    public boolean isSuccess() {
        return this == LISTED || this == PURCHASED || this == CANCELLED || this == RECLAIMED;
    }
}
