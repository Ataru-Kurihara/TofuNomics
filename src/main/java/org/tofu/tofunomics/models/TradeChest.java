package org.tofu.tofunomics.models;

import java.sql.Timestamp;

/**
 * 取引チェストデータモデル
 */
public class TradeChest {
    
    private int id;
    private String worldName;
    private int x;
    private int y;
    private int z;
    private String jobType;
    private boolean active;
    private String createdBy;
    private Timestamp createdAt;
    
    public TradeChest() {
        this.active = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    public TradeChest(String worldName, int x, int y, int z, String jobType, String createdBy) {
        this();
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.jobType = jobType;
        this.createdBy = createdBy;
    }
    
    // Getters
    public int getId() { return id; }
    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getJobType() { return jobType; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    
    // Setters
    public void setId(int id) { this.id = id; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setZ(int z) { this.z = z; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    /**
     * 座標文字列を取得
     */
    public String getLocationString() {
        return String.format("%s:%d,%d,%d", worldName, x, y, z);
    }
    
    /**
     * 指定座標と一致するかチェック
     */
    public boolean matchesLocation(String worldName, int x, int y, int z) {
        return this.worldName.equals(worldName) && 
               this.x == x && this.y == y && this.z == z;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TradeChest that = (TradeChest) obj;
        return x == that.x && y == that.y && z == that.z && 
               worldName.equals(that.worldName);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(worldName, x, y, z);
    }
    
    @Override
    public String toString() {
        return String.format("TradeChest{id=%d, location=%s, jobType=%s, active=%s}", 
                           id, getLocationString(), jobType, active);
    }
}