package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * 住居物件情報を表すモデルクラス
 * ハイブリッド方式: 座標範囲 or WorldGuard領域IDで管理
 */
public class HousingProperty {
    private int id;
    private String propertyName;
    private String worldName;
    
    // 座標範囲方式
    private Integer x1, y1, z1;
    private Integer x2, y2, z2;
    
    // WorldGuard連携方式
    private String worldguardRegionId;
    
    // 物件情報
    private String description;
    private double dailyRent;
    private Double weeklyRent;
    private Double monthlyRent;
    
    // 状態管理
    private boolean isAvailable;
    private UUID ownerUuid;  // null=運営所有、UUID=プレイヤー所有（将来）
    
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public HousingProperty() {}

    /**
     * 座標範囲方式のコンストラクタ
     */
    public HousingProperty(String propertyName, String worldName, 
                          int x1, int y1, int z1, int x2, int y2, int z2,
                          String description, double dailyRent) {
        this.propertyName = propertyName;
        this.worldName = worldName;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.description = description;
        this.dailyRent = dailyRent;
        this.isAvailable = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * WorldGuard領域方式のコンストラクタ
     */
    public HousingProperty(String propertyName, String worldName, 
                          String worldguardRegionId,
                          String description, double dailyRent) {
        this.propertyName = propertyName;
        this.worldName = worldName;
        this.worldguardRegionId = worldguardRegionId;
        this.description = description;
        this.dailyRent = dailyRent;
        this.isAvailable = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * 座標範囲が設定されているかチェック
     */
    public boolean hasCoordinates() {
        return x1 != null && y1 != null && z1 != null && 
               x2 != null && y2 != null && z2 != null;
    }

    /**
     * WorldGuard領域が設定されているかチェック
     */
    public boolean hasWorldGuardRegion() {
        return worldguardRegionId != null && !worldguardRegionId.isEmpty();
    }

    /**
     * 運営所有物件かチェック
     */
    public boolean isSystemOwned() {
        return ownerUuid == null;
    }

    /**
     * 週額賃料を計算（設定されていない場合は日額×6.5）
     */
    public double getWeeklyRent() {
        return weeklyRent != null ? weeklyRent : dailyRent * 6.5;
    }

    /**
     * 月額賃料を計算（設定されていない場合は日額×25）
     */
    public double getMonthlyRent() {
        return monthlyRent != null ? monthlyRent : dailyRent * 25.0;
    }

    /**
     * 物件の面積を計算（座標範囲設定時のみ）
     */
    public int getArea() {
        if (!hasCoordinates()) {
            return 0;
        }
        return Math.abs(x2 - x1) * Math.abs(z2 - z1);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Integer getX1() {
        return x1;
    }

    public void setX1(Integer x1) {
        this.x1 = x1;
    }

    public Integer getY1() {
        return y1;
    }

    public void setY1(Integer y1) {
        this.y1 = y1;
    }

    public Integer getZ1() {
        return z1;
    }

    public void setZ1(Integer z1) {
        this.z1 = z1;
    }

    public Integer getX2() {
        return x2;
    }

    public void setX2(Integer x2) {
        this.x2 = x2;
    }

    public Integer getY2() {
        return y2;
    }

    public void setY2(Integer y2) {
        this.y2 = y2;
    }

    public Integer getZ2() {
        return z2;
    }

    public void setZ2(Integer z2) {
        this.z2 = z2;
    }

    public String getWorldguardRegionId() {
        return worldguardRegionId;
    }

    public void setWorldguardRegionId(String worldguardRegionId) {
        this.worldguardRegionId = worldguardRegionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getDailyRent() {
        return dailyRent;
    }

    public void setDailyRent(double dailyRent) {
        this.dailyRent = dailyRent;
    }

    public void setWeeklyRent(Double weeklyRent) {
        this.weeklyRent = weeklyRent;
    }

    public void setMonthlyRent(Double monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
