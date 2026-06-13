package org.tofu.tofunomics.market;

/**
 * マーケット操作（出品・購入・キャンセル・回収）の結果コード。
 *
 * 各コードは {@code messages.market.*} のメッセージキーに対応する。
 * メッセージ送信は呼び出し側（コマンド/GUI 層）が結果コードに応じて行う。
 */
public enum MarketResult {

    // 成功（売り）
    LISTED("listed"),
    PURCHASED("purchased"),
    CANCELLED("cancelled"),
    RECLAIMED("reclaimed"),

    // 成功（買い注文＝募集）
    REQUESTED("requested"),                 // 募集登録（前払い）
    FULFILLED("fulfilled"),                 // 供給成立
    ORDER_CANCELLED("request_cancelled"),   // 募集キャンセル＋返金
    ORDER_RECLAIMED("request_reclaimed"),   // 成立した募集のアイテム回収

    // 成功（サービス依頼＝修理・エンチャント募集）
    SERVICE_REQUESTED("service_requested"),     // サービス依頼登録（前払い＋アイテム預け）
    SERVICE_FULFILLED("service_fulfilled"),     // 加工成立（worker受取）
    SERVICE_CANCELLED("service_cancelled"),     // 依頼キャンセル＋アイテム＋報酬返却
    SERVICE_RECLAIMED("service_reclaimed"),     // 完成品/返却アイテムの回収

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

    // 失敗（買い注文＝募集）
    NO_MATCHING_ITEM("no_matching_item"),       // 供給アイテム不所持
    ORDER_NOT_AVAILABLE("request_not_available"),// 募集が成立済み or 存在しない
    ORDER_NOT_OWNER("request_not_owner"),        // 自分の募集ではない
    ORDER_LIMIT("request_limit"),                // 募集上限超過

    // 失敗（サービス依頼＝修理・エンチャント募集）
    INSUFFICIENT_RESOURCES("insufficient_resources"), // worker のリソース不足（経験値・ラピス等）
    INVALID_ENCHANT("invalid_enchant"),               // 不正・許可外のエンチャント指定
    INVALID_SERVICE_ITEM("invalid_service_item"),     // 対象アイテムが修理/エンチャント不可
    SERVICE_NOT_AVAILABLE("service_not_available"),   // 依頼が成立済み or 存在しない
    SERVICE_NOT_OWNER("service_not_owner"),           // 自分の依頼ではない
    SERVICE_LIMIT("service_limit"),                   // 依頼上限超過

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
        return this == LISTED || this == PURCHASED || this == CANCELLED || this == RECLAIMED
                || this == REQUESTED || this == FULFILLED || this == ORDER_CANCELLED || this == ORDER_RECLAIMED
                || this == SERVICE_REQUESTED || this == SERVICE_FULFILLED
                || this == SERVICE_CANCELLED || this == SERVICE_RECLAIMED;
    }
}
