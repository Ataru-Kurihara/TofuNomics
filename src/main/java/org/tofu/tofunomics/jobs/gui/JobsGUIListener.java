package org.tofu.tofunomics.jobs.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jobs GUI（ハブ・詳細・ステータス・確認）のクリック/クローズイベントを集約して処理するリスナー。
 *
 * 開いている GUI のセッションを {@link ConcurrentHashMap} で管理し、
 * セッション種別に応じて各 GUI のクリック処理へ振り分ける。
 *
 * 配線順: 本リスナーを生成 → 各 GUI を生成（コンストラクタに本リスナーを渡す）
 * → {@link #setGUIs} で GUI 参照を注入 → registerEvents。
 */
public class JobsGUIListener implements Listener {

    private final TofuNomics plugin;
    private final Map<UUID, JobsGUISession> sessions = new ConcurrentHashMap<>();

    private JobsHubGUI hubGUI;
    private JobDetailGUI detailGUI;
    private JobStatsGUI statsGUI;
    private JobConfirmGUI confirmGUI;

    public JobsGUIListener(TofuNomics plugin) {
        this.plugin = plugin;
    }

    /**
     * GUI 参照を注入する（循環依存を避けるためセッターで後から設定）。
     */
    public void setGUIs(JobsHubGUI hubGUI, JobDetailGUI detailGUI,
                        JobStatsGUI statsGUI, JobConfirmGUI confirmGUI) {
        this.hubGUI = hubGUI;
        this.detailGUI = detailGUI;
        this.statsGUI = statsGUI;
        this.confirmGUI = confirmGUI;
    }

    /**
     * GUI を開いた際にセッションを登録する。
     */
    public void registerSession(UUID playerId, JobsGUISession session) {
        sessions.put(playerId, session);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        JobsGUISession session = sessions.get(player.getUniqueId());
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }

        // GUI 内のクリックは全てキャンセル（アイテムの持ち出し防止）
        event.setCancelled(true);

        // クリック対象スロットが GUI の範囲外（プレイヤーインベントリ等）の場合は無視
        if (event.getRawSlot() < 0 || event.getRawSlot() >= session.getInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        try {
            dispatchClick(player, session, event.getSlot());
        } catch (Exception e) {
            plugin.getLogger().severe("職業GUIクリック処理中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void dispatchClick(Player player, JobsGUISession session, int slot) {
        switch (session.getType()) {
            case HUB:
                if (hubGUI != null) {
                    hubGUI.handleClick(player, session, slot);
                }
                break;
            case JOB_DETAIL:
                if (detailGUI != null) {
                    detailGUI.handleClick(player, session, slot);
                }
                break;
            case STATS:
                if (statsGUI != null) {
                    statsGUI.handleClick(player, session, slot);
                }
                break;
            case CONFIRM_JOIN:
            case CONFIRM_LEAVE:
                if (confirmGUI != null) {
                    confirmGUI.handleClick(player, session, slot);
                }
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        JobsGUISession session = sessions.get(player.getUniqueId());
        if (session != null && session.getInventory().equals(event.getInventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    /**
     * 開いている全 Jobs GUI を閉じる（プラグイン無効化時用）。
     */
    public void closeAll() {
        for (JobsGUISession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }
}
