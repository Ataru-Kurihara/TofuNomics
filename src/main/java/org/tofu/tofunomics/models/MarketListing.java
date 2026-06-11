package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * プレイヤー間マーケットの出品を表すモデルクラス
 *
 * 公開マーケット型：出品者がオフラインでも他プレイヤーが購入できる。
 * 代金は出品者の bank_balance へ入金され、手数料は通貨総量から消却される。
 */
public class MarketListing {

    // 出品ステータス定数
    public static final String STATUS_ACTIVE = "active";       // 出品中（購入可能）
    public static final String STATUS_SOLD = "sold";           // 売却済み
    public static final String STATUS_EXPIRED = "expired";     // 期限切れ（出品者の回収待ち）
    public static final String STATUS_RECLAIMED = "reclaimed"; // 出品者が回収済み（キャンセル含む）

    private int id;
    private UUID sellerUuid;
    private String sellerName;
    private String itemData;       // Base64(BukkitObjectStream) でシリアライズした ItemStack
    private String displayName;    // GUI 表示用（nullable）
    private String material;       // フィルタ・表示用 Material 名
    private int amount;
    private double price;          // 出品価格（スタック全体の total）
    private String status;
    private UUID buyerUuid;        // 購入者（nullable）
    private Timestamp listedAt;
    private Long expiresAtMillis;  // 失効時刻（epoch millis）。NULL = 無期限（listing_duration_days=0）
    private Timestamp soldAt;      // nullable

    public MarketListing() {
    }

    /**
     * 新規出品用コンストラクタ（id・listedAt は INSERT 時/DB側で確定）
     */
    public MarketListing(UUID sellerUuid, String sellerName, String itemData, String displayName,
                         String material, int amount, double price, Long expiresAtMillis) {
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.displayName = displayName;
        this.material = material;
        this.amount = amount;
        this.price = price;
        this.status = STATUS_ACTIVE;
        this.expiresAtMillis = expiresAtMillis;
    }

    /**
     * 出品中（購入可能）かどうか
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * 期限切れ状態かどうか
     */
    public boolean isExpired() {
        return STATUS_EXPIRED.equals(status);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(UUID sellerUuid) {
        this.sellerUuid = sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public UUID getBuyerUuid() {
        return buyerUuid;
    }

    public void setBuyerUuid(UUID buyerUuid) {
        this.buyerUuid = buyerUuid;
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

    public Timestamp getSoldAt() {
        return soldAt;
    }

    public void setSoldAt(Timestamp soldAt) {
        this.soldAt = soldAt;
    }
}
