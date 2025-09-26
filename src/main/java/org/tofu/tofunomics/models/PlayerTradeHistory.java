package org.tofu.tofunomics.models;

import java.sql.Timestamp;

/**
 * プレイヤー取引履歴データモデル
 */
public class PlayerTradeHistory {
    
    private int id;
    private String uuid;
    private int tradeChestId;
    private String itemType;
    private int itemAmount;
    private double salePrice;
    private double jobBonus;
    private String playerJob;
    private int playerJobLevel;
    private Timestamp tradedAt;
    
    public PlayerTradeHistory() {
        this.jobBonus = 0.0;
        this.playerJobLevel = 1;
        this.tradedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public PlayerTradeHistory(String uuid, int tradeChestId, String itemType, 
                             int itemAmount, double salePrice, String playerJob, int playerJobLevel) {
        this();
        this.uuid = uuid;
        this.tradeChestId = tradeChestId;
        this.itemType = itemType;
        this.itemAmount = itemAmount;
        this.salePrice = salePrice;
        this.playerJob = playerJob;
        this.playerJobLevel = playerJobLevel;
    }
    
    // Getters
    public int getId() { return id; }
    public String getUuid() { return uuid; }
    public int getTradeChestId() { return tradeChestId; }
    public String getItemType() { return itemType; }
    public int getItemAmount() { return itemAmount; }
    public double getSalePrice() { return salePrice; }
    public double getJobBonus() { return jobBonus; }
    public String getPlayerJob() { return playerJob; }
    public int getPlayerJobLevel() { return playerJobLevel; }
    public Timestamp getTradedAt() { return tradedAt; }
    
    // Setters
    public void setId(int id) { this.id = id; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setTradeChestId(int tradeChestId) { this.tradeChestId = tradeChestId; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setItemAmount(int itemAmount) { this.itemAmount = itemAmount; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }
    public void setJobBonus(double jobBonus) { this.jobBonus = jobBonus; }
    public void setPlayerJob(String playerJob) { this.playerJob = playerJob; }
    public void setPlayerJobLevel(int playerJobLevel) { this.playerJobLevel = playerJobLevel; }
    public void setTradedAt(Timestamp tradedAt) { this.tradedAt = tradedAt; }
    
    /**
     * 合計売却価格を取得（基本価格＋職業ボーナス）
     */
    public double getTotalPrice() {
        return salePrice + jobBonus;
    }
    
    /**
     * 単価を計算
     */
    public double getUnitPrice() {
        if (itemAmount <= 0) return 0.0;
        return getTotalPrice() / itemAmount;
    }
    
    /**
     * 職業ボーナス率を計算
     */
    public double getBonusRate() {
        if (salePrice <= 0) return 0.0;
        return (jobBonus / salePrice) * 100.0;
    }
    
    @Override
    public String toString() {
        return String.format("PlayerTradeHistory{id=%d, uuid=%s, item=%s x%d, price=%.2f (bonus=%.2f), job=%s Lv%d}", 
                           id, uuid, itemType, itemAmount, salePrice, jobBonus, playerJob, playerJobLevel);
    }
}