package org.tofu.tofunomics.market;

import java.util.UUID;

/**
 * 買い注文（募集）への供給決済（DB トランザクション）の結果を表す。
 *
 * {@code MarketManager.executeFulfillTransaction} が返し、ラッパー
 * {@code fulfillBuyOrder} が供給者への結果表示・募集者への通知に付帯情報
 * （requesterUuid/supplierProceeds）を用いる。
 */
public class FulfillOutcome {

    private final MarketResult result;
    private final UUID requesterUuid;     // 募集者（成立時の通知用）
    private final long supplierProceeds;  // 供給者の受取額（手数料控除後・floor）

    private FulfillOutcome(MarketResult result, UUID requesterUuid, long supplierProceeds) {
        this.result = result;
        this.requesterUuid = requesterUuid;
        this.supplierProceeds = supplierProceeds;
    }

    /**
     * 失敗結果（付帯情報なし）
     */
    public static FulfillOutcome failure(MarketResult result) {
        return new FulfillOutcome(result, null, 0L);
    }

    /**
     * 供給成立結果
     */
    public static FulfillOutcome success(UUID requesterUuid, long supplierProceeds) {
        return new FulfillOutcome(MarketResult.FULFILLED, requesterUuid, supplierProceeds);
    }

    public MarketResult getResult() {
        return result;
    }

    public boolean isSuccess() {
        return result == MarketResult.FULFILLED;
    }

    public UUID getRequesterUuid() {
        return requesterUuid;
    }

    public long getSupplierProceeds() {
        return supplierProceeds;
    }
}
