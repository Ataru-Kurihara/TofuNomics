package org.tofu.tofunomics.area;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.models.AreaZone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * エリア進入イベントを監視するリスナー
 */
public class AreaListener implements Listener {
    private final TofuNomics plugin;
    private final AreaManager areaManager;

    // プレイヤーの現在いるエリアを記録（チャンク移動時のみチェックするための最適化）
    private final Map<UUID, String> playerCurrentArea;

    // プレイヤーの最後のチャンク位置（パフォーマンス最適化用）
    private final Map<UUID, String> playerLastChunk;

    /**
     * コンストラクタ
     *
     * @param plugin プラグインインスタンス
     * @param areaManager エリアマネージャー
     */
    public AreaListener(TofuNomics plugin, AreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.playerCurrentArea = new HashMap<>();
        this.playerLastChunk = new HashMap<>();
    }

    /**
     * プレイヤーの移動イベントを監視
     * パフォーマンス最適化：チャンク移動時のみエリアチェック
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!areaManager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // チャンク移動チェック（パフォーマンス最適化）
        String currentChunk = getChunkKey(to);
        String lastChunk = playerLastChunk.get(player.getUniqueId());

        // 同じチャンク内の移動はスキップ
        if (currentChunk.equals(lastChunk)) {
            return;
        }

        playerLastChunk.put(player.getUniqueId(), currentChunk);

        // エリア判定
        AreaZone currentArea = areaManager.getAreaAtLocation(to);
        String currentAreaId = currentArea != null ? currentArea.getId() : null;
        String previousAreaId = playerCurrentArea.get(player.getUniqueId());

        // エリアが変わっていない場合はスキップ
        if (currentAreaId != null && currentAreaId.equals(previousAreaId)) {
            return;
        }

        // エリア外に出た場合
        if (currentAreaId == null) {
            playerCurrentArea.remove(player.getUniqueId());
            return;
        }

        // 新しいエリアに進入
        if (areaManager.canEnterArea(player.getUniqueId(), currentAreaId)) {
            handleAreaEntry(player, currentArea);
            playerCurrentArea.put(player.getUniqueId(), currentAreaId);
            areaManager.recordAreaEntry(player.getUniqueId(), currentAreaId);
        }
    }

    /**
     * エリア進入時の処理
     *
     * @param player プレイヤー
     * @param area 進入したエリア
     */
    private void handleAreaEntry(Player player, AreaZone area) {
        // タイトルを表示
        if (area.getTitle() != null && !area.getTitle().isEmpty()) {
            String title = ChatColor.translateAlternateColorCodes('&', area.getTitle());
            String subtitle = area.getSubtitle() != null ?
                            ChatColor.translateAlternateColorCodes('&', area.getSubtitle()) : "";

            player.sendTitle(title, subtitle, 10, 70, 20);
        }

        // メッセージを送信
        if (area.getMessage() != null && !area.getMessage().isEmpty()) {
            String message = ChatColor.translateAlternateColorCodes('&', area.getMessage());
            player.sendMessage(message);
        }

        // 効果音を再生
        if (area.getSound() != null) {
            player.playSound(player.getLocation(), area.getSound(), 1.0f, 1.0f);
        }

        // デバッグログ
        plugin.getLogger().info(player.getName() + " が " + area.getName() + " に進入しました");
    }

    /**
     * チャンクキーを生成（ワールド名:チャンクX:チャンクZ）
     */
    private String getChunkKey(Location location) {
        return location.getWorld().getName() + ":" +
               (location.getBlockX() >> 4) + ":" +
               (location.getBlockZ() >> 4);
    }

    /**
     * プレイヤーログアウト時のクリーンアップ
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerCurrentArea.remove(playerId);
        playerLastChunk.remove(playerId);
        areaManager.clearPlayerHistory(playerId);
    }

    /**
     * クリーンアップ処理
     */
    public void cleanup() {
        playerCurrentArea.clear();
        playerLastChunk.clear();
    }
}
