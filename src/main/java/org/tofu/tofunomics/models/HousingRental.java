package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 住居賃貸契約を表すモデルクラス
 */
public class HousingRental {
    private int id;
    private int propertyId;
    private UUID tenantUuid;
    private String rentalPeriod;  // "daily", "weekly", "monthly"
    private int rentalDays;
    private double totalCost;
    private Timestamp startDate;
    private Timestamp endDate;
    private String status;  // "active", "expired", "cancelled"
    private boolean autoRenew;
    private Timestamp createdAt;

    public HousingRental() {}

    public HousingRental(int propertyId, UUID tenantUuid, String rentalPeriod,
                        int rentalDays, double totalCost) {
        this.propertyId = propertyId;
        this.tenantUuid = tenantUuid;
        this.rentalPeriod = rentalPeriod;
        this.rentalDays = rentalDays;
        this.totalCost = totalCost;
        this.startDate = new Timestamp(System.currentTimeMillis());
        this.endDate = calculateEndDate(this.startDate, rentalDays);
        this.status = "active";
        this.autoRenew = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * 終了日時を計算
     */
    private Timestamp calculateEndDate(Timestamp start, int days) {
        long endMillis = start.getTime() + TimeUnit.DAYS.toMillis(days);
        return new Timestamp(endMillis);
    }

    /**
     * 契約が有効期限内かチェック
     */
    public boolean isActive() {
        if (!"active".equals(status)) {
            return false;
        }
        return new Timestamp(System.currentTimeMillis()).before(endDate);
    }

    /**
     * 契約が期限切れかチェック
     */
    public boolean isExpired() {
        return new Timestamp(System.currentTimeMillis()).after(endDate);
    }

    /**
     * 残り日数を取得
     */
    public long getRemainingDays() {
        long diff = endDate.getTime() - System.currentTimeMillis();
        return Math.max(0, TimeUnit.MILLISECONDS.toDays(diff));
    }

    /**
     * 残り時間（ミリ秒）を取得
     */
    public long getRemainingMillis() {
        return Math.max(0, endDate.getTime() - System.currentTimeMillis());
    }

    /**
     * 契約を期限切れにする
     */
    public void expire() {
        this.status = "expired";
    }

    /**
     * 契約をキャンセルする
     */
    public void cancel() {
        this.status = "cancelled";
    }

    /**
     * 契約を延長する
     */
    public void extend(int additionalDays, double additionalCost) {
        this.rentalDays += additionalDays;
        this.totalCost += additionalCost;
        long newEndMillis = endDate.getTime() + TimeUnit.DAYS.toMillis(additionalDays);
        this.endDate = new Timestamp(newEndMillis);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(int propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getTenantUuid() {
        return tenantUuid;
    }

    public void setTenantUuid(UUID tenantUuid) {
        this.tenantUuid = tenantUuid;
    }

    public String getRentalPeriod() {
        return rentalPeriod;
    }

    public void setRentalPeriod(String rentalPeriod) {
        this.rentalPeriod = rentalPeriod;
    }

    public int getRentalDays() {
        return rentalDays;
    }

    public void setRentalDays(int rentalDays) {
        this.rentalDays = rentalDays;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
