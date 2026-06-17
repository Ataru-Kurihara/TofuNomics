package org.tofu.tofunomics.models;

import org.bukkit.Location;

import java.sql.Timestamp;

/**
 * 農家の畑区画（プロット）モデル
 * 座標範囲とWorldGuardリージョン、所有者(農家)を保持する。
 * owner_uuid が null の区画は「空き（未割り当て）」を表す。
 */
public class FarmPlot {

    private int id;
    private String plotName;
    private String worldName;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private String worldguardRegionId;
    private String ownerUuid; // null = 空き
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public FarmPlot() {
    }

    public FarmPlot(String plotName, String worldName, int x1, int y1, int z1,
                    int x2, int y2, int z2, String worldguardRegionId) {
        this.plotName = plotName;
        this.worldName = worldName;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.worldguardRegionId = worldguardRegionId;
        this.ownerUuid = null;
    }

    /**
     * 指定座標がこの区画の範囲内かを判定（立方体判定）
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) &&
               y >= Math.min(y1, y2) && y <= Math.max(y1, y2) &&
               z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    /**
     * 区画が割り当て済み（所有者あり）か
     */
    public boolean isAssigned() {
        return ownerUuid != null && !ownerUuid.isEmpty();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPlotName() { return plotName; }
    public void setPlotName(String plotName) { this.plotName = plotName; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public int getX1() { return x1; }
    public void setX1(int x1) { this.x1 = x1; }
    public int getY1() { return y1; }
    public void setY1(int y1) { this.y1 = y1; }
    public int getZ1() { return z1; }
    public void setZ1(int z1) { this.z1 = z1; }
    public int getX2() { return x2; }
    public void setX2(int x2) { this.x2 = x2; }
    public int getY2() { return y2; }
    public void setY2(int y2) { this.y2 = y2; }
    public int getZ2() { return z2; }
    public void setZ2(int z2) { this.z2 = z2; }

    public String getWorldguardRegionId() { return worldguardRegionId; }
    public void setWorldguardRegionId(String worldguardRegionId) { this.worldguardRegionId = worldguardRegionId; }

    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
