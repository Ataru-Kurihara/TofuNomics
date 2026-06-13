package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * プレイヤー間マーケットの買い注文（募集）を表すモデルクラス
 *
 * 売り出品（{@link MarketListing}）の鏡像。募集者が「このアイテムをこの値段で買いたい」と
 * 募集を出し、別プレイヤーが手持ちから供給して成立させる。
 *
 * 代金は募集時に前払い（エスクロー）として募集者の現金から全額拘束される。
 * 成立時は供給者の bank へ floor(price×0.95) が入金され、差額は通貨総量から消却される。
 * キャンセル/期限切れ時は前払い分を全額返金する。
 */
public class MarketBuyOrder {

    // 募集ステータス定数
    public static final String STATUS_OPEN = "open";             // 募集中（前払いエスクロー済み）
    public static final String STATUS_FULFILLED = "fulfilled";   // 供給成立（募集者の回収待ち）
    public static final String STATUS_RECLAIMED = "reclaimed";   // 募集者が供給アイテムを回収済み
    public static final String STATUS_CANCELLED = "cancelled";   // 募集者がキャンセル（前払い返金済み）
    public static final String STATUS_EXPIRED = "expired";       // 期限切れ（前払い返金済み）

    private int id;
    private UUID requesterUuid;
    private String requesterName;
    private String material;       // 募集する Material 名
    private int amount;            // 募集個数
    private double price;          // 前払いエスクロー総額
    private String status;
    private UUID supplierUuid;     // 供給者（成立時、nullable）
    private String itemData;       // 供給されたアイテム（Base64、成立時に保存、回収用、nullable）
    private Timestamp listedAt;
    private Long expiresAtMillis;  // 失効時刻（epoch millis）。NULL = 無期限
    private Timestamp fulfilledAt; // 成立時刻（nullable）

    public MarketBuyOrder() {
    }

    /**
     * 新規募集用コンストラクタ（id・listedAt は INSERT 時/DB側で確定）
     */
    public MarketBuyOrder(UUID requesterUuid, String requesterName, String material,
                          int amount, double price, Long expiresAtMillis) {
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.material = material;
        this.amount = amount;
        this.price = price;
        this.status = STATUS_OPEN;
        this.expiresAtMillis = expiresAtMillis;
    }

    /**
     * 募集中（供給可能）かどうか
     */
    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    /**
     * 供給成立済み（募集者の回収待ち）かどうか
     */
    public boolean isFulfilled() {
        return STATUS_FULFILLED.equals(status);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getRequesterUuid() {
        return requesterUuid;
    }

    public void setRequesterUuid(UUID requesterUuid) {
        this.requesterUuid = requesterUuid;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSupplierUuid() {
        return supplierUuid;
    }

    public void setSupplierUuid(UUID supplierUuid) {
        this.supplierUuid = supplierUuid;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public Timestamp getListedAt() {
        return listedAt;
    }

    public void setListedAt(Timestamp listedAt) {
        this.listedAt = listedAt;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(Long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }

    public Timestamp getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Timestamp fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }
}
