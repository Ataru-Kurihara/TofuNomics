package org.tofu.tofunomics.market.gui;

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
 * マーケット GUI（一覧・自分の出品）のクリック/クローズイベントを集約して処理するリスナー。
 *
 * 開いている GUI のセッションを {@link ConcurrentHashMap} で管理し、
 * セッション種別に応じて {@link MarketBrowseGUI} / {@link MyListingsGUI} のクリック処理へ振り分ける。
 *
 * 配線順（S4）: 本リスナーを生成 → 各 GUI を生成（コンストラクタに本リスナーを渡す）
 * → {@link #setGUIs(MarketBrowseGUI, MyListingsGUI)} で GUI 参照を注入 → registerEvents。
 */
public class MarketGUIListener implements Listener {

    private final TofuNomics plugin;
    private final Map<UUID, MarketGUISession> sessions = new ConcurrentHashMap<>();

    private MarketBrowseGUI browseGUI;
    private MyListingsGUI myListingsGUI;
    private BuyOrderBrowseGUI buyOrderBrowseGUI;
    private MyBuyOrdersGUI myBuyOrdersGUI;
    private ServiceBrowseGUI serviceBrowseGUI;
    private MyServicesGUI myServicesGUI;

    public MarketGUIListener(TofuNomics plugin) {
        this.plugin = plugin;
    }

    /**
     * 売り GUI 参照を注入する（循環依存を避けるためセッターで後から設定）。
     */
    public void setGUIs(MarketBrowseGUI browseGUI, MyListingsGUI myListingsGUI) {
        this.browseGUI = browseGUI;
        this.myListingsGUI = myListingsGUI;
    }

    /**
     * 買い注文（募集）GUI 参照を注入する。
     */
    public void setBuyOrderGUIs(BuyOrderBrowseGUI buyOrderBrowseGUI, MyBuyOrdersGUI myBuyOrdersGUI) {
        this.buyOrderBrowseGUI = buyOrderBrowseGUI;
        this.myBuyOrdersGUI = myBuyOrdersGUI;
    }

    /**
     * サービス依頼（修理・エンチャント募集）GUI 参照を注入する。
     */
    public void setServiceGUIs(ServiceBrowseGUI serviceBrowseGUI, MyServicesGUI myServicesGUI) {
        this.serviceBrowseGUI = serviceBrowseGUI;
        this.myServicesGUI = myServicesGUI;
    }

    /**
     * GUI を開いた際にセッションを登録する。
     */
    public void registerSession(UUID playerId, MarketGUISession session) {
        sessions.put(playerId, session);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MarketGUISession session = sessions.get(player.getUniqueId());
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
            dispatchClick(player, session, event.getSlot(), event.getClick());
        } catch (Exception e) {
            plugin.getLogger().severe("マーケットGUIクリック処理中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void dispatchClick(Player player, MarketGUISession session, int slot,
                               org.bukkit.event.inventory.ClickType clickType) {
        switch (session.getType()) {
            case BROWSE:
                if (browseGUI != null) {
                    browseGUI.handleClick(player, session, slot, clickType);
                }
                break;
            case MY_LISTINGS:
                if (myListingsGUI != null) {
                    myListingsGUI.handleClick(player, session, slot, clickType);
                }
                break;
            case BUY_BROWSE:
                if (buyOrderBrowseGUI != null) {
                    buyOrderBrowseGUI.handleClick(player, session, slot, clickType);
                }
                break;
            case MY_BUY_ORDERS:
                if (myBuyOrdersGUI != null) {
                    myBuyOrdersGUI.handleClick(player, session, slot, clickType);
                }
                break;
            case SERVICE_BROWSE:
                if (serviceBrowseGUI != null) {
                    serviceBrowseGUI.handleClick(player, session, slot, clickType);
                }
                break;
            case MY_SERVICES:
                if (myServicesGUI != null) {
                    myServicesGUI.handleClick(player, session, slot, clickType);
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
        MarketGUISession session = sessions.get(player.getUniqueId());
        if (session != null && session.getInventory().equals(event.getInventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    /**
     * 開いている全マーケット GUI を閉じる（プラグイン無効化時用）。
     */
    public void closeAll() {
        for (MarketGUISession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }
}
