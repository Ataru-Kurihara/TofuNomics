package org.tofu.tofunomics.models;

import java.sql.Timestamp;

public class Job {
    private int id;
    private String name;
    private String displayName;
    private int maxLevel;
    private double baseIncome;
    private Timestamp createdAt;

    public Job() {}

    public Job(String name, String displayName, int maxLevel, double baseIncome) {
        this.name = name;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.baseIncome = baseIncome;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public double getBaseIncome() {
        return baseIncome;
    }

    public void setBaseIncome(double baseIncome) {
        this.baseIncome = baseIncome;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}