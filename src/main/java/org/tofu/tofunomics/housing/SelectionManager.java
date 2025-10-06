package org.tofu.tofunomics.housing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 範囲選択を管理するクラス（WorldEditライク）
 */
public class SelectionManager {
    private final Map<UUID, Location> firstPositions;
    private final Map<UUID, Location> secondPositions;
    private Material selectionTool;

    public SelectionManager() {
        this.firstPositions = new HashMap<>();
        this.secondPositions = new HashMap<>();
        this.selectionTool = Material.WOODEN_AXE; // デフォルト
    }

    /**
     * 第1座標を設定
     */
    public void setFirstPosition(Player player, Location location) {
        firstPositions.put(player.getUniqueId(), location);
        player.sendMessage("§a第1座標を設定しました: " + formatLocation(location));
        
        // パーティクルで視覚化
        visualizePosition(location);
    }

    /**
     * 第2座標を設定
     */
    public void setSecondPosition(Player player, Location location) {
        secondPositions.put(player.getUniqueId(), location);
        player.sendMessage("§a第2座標を設定しました: " + formatLocation(location));
        
        // パーティクルで視覚化
        visualizePosition(location);
        
        // 両方の座標が設定されている場合、選択範囲を表示
        if (hasCompleteSelection(player)) {
            displaySelection(player);
        }
    }

    /**
     * 第1座標を取得
     */
    public Location getFirstPosition(UUID uuid) {
        return firstPositions.get(uuid);
    }

    /**
     * 第2座標を取得
     */
    public Location getSecondPosition(UUID uuid) {
        return secondPositions.get(uuid);
    }

    /**
     * 完全な選択範囲を持っているかチェック
     */
    public boolean hasCompleteSelection(Player player) {
        UUID uuid = player.getUniqueId();
        return firstPositions.containsKey(uuid) && secondPositions.containsKey(uuid);
    }

    /**
     * 選択範囲をクリア
     */
    public void clearSelection(Player player) {
        UUID uuid = player.getUniqueId();
        firstPositions.remove(uuid);
        secondPositions.remove(uuid);
        player.sendMessage("§e選択範囲をクリアしました");
    }

    /**
     * 選択範囲の体積を計算
     */
    public int getSelectionVolume(Player player) {
        if (!hasCompleteSelection(player)) {
            return 0;
        }
        
        Location pos1 = firstPositions.get(player.getUniqueId());
        Location pos2 = secondPositions.get(player.getUniqueId());
        
        int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int dy = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        
        return dx * dy * dz;
    }

    /**
     * 選択範囲の面積を計算（XZ平面）
     */
    public int getSelectionArea(Player player) {
        if (!hasCompleteSelection(player)) {
            return 0;
        }
        
        Location pos1 = firstPositions.get(player.getUniqueId());
        Location pos2 = secondPositions.get(player.getUniqueId());
        
        int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        
        return dx * dz;
    }

    /**
     * 選択ツールを設定
     */
    public void setSelectionTool(Material material) {
        this.selectionTool = material;
    }

    /**
     * 選択ツールを取得
     */
    public Material getSelectionTool() {
        return selectionTool;
    }

    /**
     * 座標をフォーマット
     */
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d) in %s", 
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 
            loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
    }

    /**
     * 座標をパーティクルで視覚化
     */
    private void visualizePosition(Location location) {
        if (location.getWorld() == null) return;
        
        // グリーンのパーティクルで表示
        location.getWorld().spawnParticle(
            Particle.VILLAGER_HAPPY,
            location.clone().add(0.5, 0.5, 0.5),
            10,
            0.5, 0.5, 0.5,
            0.1
        );
    }

    /**
     * 選択範囲全体を表示
     */
    private void displaySelection(Player player) {
        UUID uuid = player.getUniqueId();
        Location pos1 = firstPositions.get(uuid);
        Location pos2 = secondPositions.get(uuid);
        
        if (pos1 == null || pos2 == null) return;
        
        int volume = getSelectionVolume(player);
        int area = getSelectionArea(player);
        
        player.sendMessage("§6========= 選択範囲情報 =========");
        player.sendMessage("§e体積: " + volume + " ブロック");
        player.sendMessage("§e面積(XZ): " + area + " ブロック");
        player.sendMessage("§6================================");
    }

    /**
     * 全選択をクリア（プラグイン無効化時など）
     */
    public void clearAllSelections() {
        firstPositions.clear();
        secondPositions.clear();
    }
}
