package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * マーケットサービス依頼（修理・エンチャント募集）のモデルクラス
 * プレイヤーが自分の道具を預け、「修理してほしい」「エンチャントしてほしい」と
 * 報酬付きで募集する委託依頼を表す。
 *
 * 委託・自動検証型 / システム自動代行型:
 *   依頼者は道具(itemData)と報酬(price)を前払いエスクローで預ける。
 *   引受人(worker)が必要リソースを所持していれば、システムが仮想的に加工し、
 *   完成品(resultItemData)を保管する。アイテムは常にシステム保管のため、
 *   すり替え・持ち逃げが原理的に発生しない。
 */
public class MarketServiceRequest {
    // ステータス定数
    public static final String STATUS_OPEN = "open";              // 募集中
    public static final String STATUS_FULFILLED = "fulfilled";    // 加工完了（受け取り待ち）
    public static final String STATUS_RECLAIMED = "reclaimed";    // 受け取り済み
    public static final String STATUS_CANCELLED = "cancelled";    // キャンセル済み
    public static final String STATUS_EXPIRED = "expired";        // 期限切れ

    // サービス種類定数
    public static final String TYPE_REPAIR = "REPAIR";            // 修理
    public static final String TYPE_ENCHANT = "ENCHANT";          // エンチャント

    private int id;
    private UUID requesterUuid;
    private String requesterName;
    private String serviceType;       // REPAIR | ENCHANT
    private String material;          // 預けたアイテムのMaterial名（表示・カテゴリ用）
    private String itemData;          // 預けた元アイテム（Base64、登録時に必須）
    private String enchantType;       // ENCHANT時のみ: エンチャントのminecraft key（例 'sharpness'）
    private Integer enchantLevel;     // ENCHANT時のみ: エンチャントレベル
    private double price;             // 報酬（前払いエスクロー総額）
    private String status;
    private UUID workerUuid;          // 引受人（成立時）
    private String resultItemData;    // 完成品（加工後、回収用）
    private Timestamp listedAt;
    private Long expiresAt;
    private Timestamp fulfilledAt;

    public MarketServiceRequest() {
    }

    /**
     * 修理依頼の生成
     */
    public static MarketServiceRequest createRepair(UUID requesterUuid, String requesterName,
                                                    String material, String itemData, double price) {
        MarketServiceRequest req = new MarketServiceRequest();
        req.requesterUuid = requesterUuid;
        req.requesterName = requesterName;
        req.serviceType = TYPE_REPAIR;
        req.material = material;
        req.itemData = itemData;
        req.price = price;
        req.status = STATUS_OPEN;
        return req;
    }

    /**
     * エンチャント依頼の生成
     */
    public static MarketServiceRequest createEnchant(UUID requesterUuid, String requesterName,
                                                     String material, String itemData,
                                                     String enchantType, int enchantLevel, double price) {
        MarketServiceRequest req = new MarketServiceRequest();
        req.requesterUuid = requesterUuid;
        req.requesterName = requesterName;
        req.serviceType = TYPE_ENCHANT;
        req.material = material;
        req.itemData = itemData;
        req.enchantType = enchantType;
        req.enchantLevel = enchantLevel;
        req.price = price;
        req.status = STATUS_OPEN;
        return req;
    }

    public boolean isRepair() {
        return TYPE_REPAIR.equals(serviceType);
    }

    public boolean isEnchant() {
        return TYPE_ENCHANT.equals(serviceType);
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

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public String getEnchantType() {
        return enchantType;
    }

    public void setEnchantType(String enchantType) {
        this.enchantType = enchantType;
    }

    public Integer getEnchantLevel() {
        return enchantLevel;
    }

    public void setEnchantLevel(Integer enchantLevel) {
        this.enchantLevel = enchantLevel;
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

    public UUID getWorkerUuid() {
        return workerUuid;
    }

    public void setWorkerUuid(UUID workerUuid) {
        this.workerUuid = workerUuid;
    }

    public String getResultItemData() {
        return resultItemData;
    }

    public void setResultItemData(String resultItemData) {
        this.resultItemData = resultItemData;
    }

    public Timestamp getListedAt() {
        return listedAt;
    }

    public void setListedAt(Timestamp listedAt) {
        this.listedAt = listedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Timestamp fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }
}
