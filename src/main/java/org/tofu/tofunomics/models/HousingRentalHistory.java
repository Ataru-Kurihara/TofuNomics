package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * 住居賃貸履歴を表すモデルクラス
 */
public class HousingRentalHistory {
    private int id;
    private int rentalId;
    private int propertyId;
    private UUID tenantUuid;
    private String actionType;  // "rent", "extend", "cancel", "expire"
    private Double amount;
    private Timestamp actionDate;

    public HousingRentalHistory() {}

    public HousingRentalHistory(int rentalId, int propertyId, UUID tenantUuid,
                               String actionType, Double amount) {
        this.rentalId = rentalId;
        this.propertyId = propertyId;
        this.tenantUuid = tenantUuid;
        this.actionType = actionType;
        this.amount = amount;
        this.actionDate = new Timestamp(System.currentTimeMillis());
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRentalId() {
        return rentalId;
    }

    public void setRentalId(int rentalId) {
        this.rentalId = rentalId;
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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Timestamp getActionDate() {
        return actionDate;
    }

    public void setActionDate(Timestamp actionDate) {
        this.actionDate = actionDate;
    }
}
