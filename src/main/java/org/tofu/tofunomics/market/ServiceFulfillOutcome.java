package org.tofu.tofunomics.market;

/**
 * サービス依頼（修理・エンチャント募集）の加工決済（DB トランザクション）の結果を表す。
 *
 * {@code MarketManager.executeFulfillServiceTransaction} が返し、ラッパー
 * {@code fulfillServiceRequest} が worker への結果表示に付帯情報（proceeds）を用いる。
 * 依頼者への通知はラッパー側が保持する依頼オブジェクトから行う。
 */
public class ServiceFulfillOutcome {

    private final MarketResult result;
    private final long workerProceeds;   // worker の受取額（手数料控除後・floor）

    private ServiceFulfillOutcome(MarketResult result, long workerProceeds) {
        this.result = result;
        this.workerProceeds = workerProceeds;
    }

    public static ServiceFulfillOutcome failure(MarketResult result) {
        return new ServiceFulfillOutcome(result, 0L);
    }

    public static ServiceFulfillOutcome success(long workerProceeds) {
        return new ServiceFulfillOutcome(MarketResult.SERVICE_FULFILLED, workerProceeds);
    }

    public MarketResult getResult() {
        return result;
    }

    public boolean isSuccess() {
        return result == MarketResult.SERVICE_FULFILLED;
    }

    public long getWorkerProceeds() {
        return workerProceeds;
    }
}
