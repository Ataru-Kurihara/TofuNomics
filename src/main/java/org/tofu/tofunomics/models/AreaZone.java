package org.tofu.tofunomics.models;

import org.bukkit.Location;
import org.bukkit.Sound;

/**
 * エリア情報を管理するモデルクラス
 */
public class AreaZone {
    private final String id;
    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final String title;
    private final String subtitle;
    private final String message;
    private final Sound sound;

    /**
     * コンストラクタ
     */
    public AreaZone(String id, String name, String world,
                    int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ,
                    String title, String subtitle, String message, Sound sound) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.title = title;
        this.subtitle = subtitle;
        this.message = message;
        this.sound = sound;
    }

    /**
     * 指定された位置がこのエリア内にあるかチェック
     *
     * @param location チェックする位置
     * @return エリア内の場合true
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // ワールドチェック
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }

        // 座標範囲チェック
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    // Getter メソッド
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getMessage() {
        return message;
    }

    public Sound getSound() {
        return sound;
    }

    @Override
    public String toString() {
        return "AreaZone{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", world='" + world + '\'' +
                '}';
    }
}
