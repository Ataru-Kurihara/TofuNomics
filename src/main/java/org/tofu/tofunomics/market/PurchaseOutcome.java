package org.tofu.tofunomics.market;

import java.util.UUID;

/**
 * 購入決済（DB トランザクション）の結果を表す。
 *
 * {@code MarketManager.executePurchaseTransaction} が返し、ラッパー
 * {@code purchaseListing} が item 付与・出品者通知に付帯情報（itemData/sellerUuid/proceeds）を用いる。
 */
public class PurchaseOutcome {

    private final MarketResult result;
    private final String itemData;      // 付与すべきアイテムの Base64（成功時のみ）
    private final UUID sellerUuid;      // 出品者（成功時の通知用）
    private final long sellerProceeds;  // 出品者の受取額（手数料控除後・floor）

    private PurchaseOutcome(MarketResult result, String itemData, UUID sellerUuid, long sellerProceeds) {
        this.result = result;
        this.itemData = itemData;
        this.sellerUuid = sellerUuid;
        this.sellerProceeds = sellerProceeds;
    }

    /**
     * 失敗結果（付帯情報なし）
     */
    public static PurchaseOutcome failure(MarketResult result) {
        return new PurchaseOutcome(result, null, null, 0L);
    }

    /**
     * 購入成功結果
     */
    public static PurchaseOutcome success(String itemData, UUID sellerUuid, long sellerProceeds) {
        return new PurchaseOutcome(MarketResult.PURCHASED, itemData, sellerUuid, sellerProceeds);
    }

    public MarketResult getResult() {
        return result;
    }

    public boolean isSuccess() {
        return result == MarketResult.PURCHASED;
    }

    public String getItemData() {
        return itemData;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public long getSellerProceeds() {
        return sellerProceeds;
    }
}
