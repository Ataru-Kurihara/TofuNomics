package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

public class Player {
    private UUID uuid;
    private double balance; // 持ち歩き現金（将来的に削除予定）
    private double bankBalance; // 銀行預金
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Player() {}

    public Player(UUID uuid, double balance) {
        this.uuid = uuid;
        this.balance = balance;
        this.bankBalance = 0.0;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public Player(UUID uuid, double balance, double bankBalance) {
        this.uuid = uuid;
        this.balance = balance;
        this.bankBalance = bankBalance;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
    
    public void setUuid(String uuidString) {
        this.uuid = UUID.fromString(uuidString);
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void addBalance(double amount) {
        this.balance += amount;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public boolean removeBalance(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            this.updatedAt = new Timestamp(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public void addBankBalance(double amount) {
        this.bankBalance += amount;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public boolean removeBankBalance(double amount) {
        if (this.bankBalance >= amount) {
            this.bankBalance -= amount;
            this.updatedAt = new Timestamp(System.currentTimeMillis());
            return true;
        }
        return false;
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