package org.tofu.tofunomics.housing.gui;

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
 * housing GUI のクリック/クローズイベントを集約して各 GUI のクリック処理へ振り分けるリスナー。
 *
 * 開いている GUI のセッションを {@link ConcurrentHashMap} で管理し、
 * セッション種別に応じて各 GUI の handleClick へ振り分ける（market の MarketGUIListener と同型）。
 *
 * 配線順: 本リスナーを生成 → 各 GUI を生成（コンストラクタに本リスナーを渡す）
 * → {@link #setGUIs} で GUI 参照を注入 → registerEvents。
 */
public class HousingGUIListener implements Listener {

    private final TofuNomics plugin;
    private final Map<UUID, HousingGUISession> sessions = new ConcurrentHashMap<>();

    private HousingHubGUI hubGUI;
    private HousingBrowseGUI browseGUI;
    private HousingDetailGUI detailGUI;
    private MyRentalsGUI myRentalsGUI;
    private RentalManageGUI manageGUI;
    private HousingAdminRegisterGUI adminRegisterGUI;
    private HousingAdminListGUI adminListGUI;
    private HousingAdminEditGUI adminEditGUI;

    public HousingGUIListener(TofuNomics plugin) {
        this.plugin = plugin;
    }

    /**
     * GUI 参照を注入する（循環依存を避けるためセッターで後から設定）。
     */
    public void setGUIs(HousingHubGUI hubGUI, HousingBrowseGUI browseGUI, HousingDetailGUI detailGUI,
                        MyRentalsGUI myRentalsGUI, RentalManageGUI manageGUI,
                        HousingAdminRegisterGUI adminRegisterGUI,
                        HousingAdminListGUI adminListGUI, HousingAdminEditGUI adminEditGUI) {
        this.hubGUI = hubGUI;
        this.browseGUI = browseGUI;
        this.detailGUI = detailGUI;
        this.myRentalsGUI = myRentalsGUI;
        this.manageGUI = manageGUI;
        this.adminRegisterGUI = adminRegisterGUI;
        this.adminListGUI = adminListGUI;
        this.adminEditGUI = adminEditGUI;
    }

    /**
     * GUI を開いた際にセッションを登録する。
     */
    public void registerSession(UUID playerId, HousingGUISession session) {
        sessions.put(playerId, session);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        HousingGUISession session = sessions.get(player.getUniqueId());
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }

        // GUI 内のクリックは全てキャンセル（アイテムの持ち出し防止）
        event.setCancelled(true);

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
            plugin.getLogger().severe("住居GUIクリック処理中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void dispatchClick(Player player, HousingGUISession session, int slot,
                               org.bukkit.event.inventory.ClickType clickType) {
        switch (session.getType()) {
            case HUB:
                if (hubGUI != null) hubGUI.handleClick(player, session, slot, clickType);
                break;
            case BROWSE:
                if (browseGUI != null) browseGUI.handleClick(player, session, slot, clickType);
                break;
            case DETAIL:
                if (detailGUI != null) detailGUI.handleClick(player, session, slot, clickType);
                break;
            case MY_RENTALS:
                if (myRentalsGUI != null) myRentalsGUI.handleClick(player, session, slot, clickType);
                break;
            case MANAGE:
            case CANCEL_CONFIRM:
                if (manageGUI != null) manageGUI.handleClick(player, session, slot, clickType);
                break;
            case ADMIN_REGISTER:
                if (adminRegisterGUI != null) adminRegisterGUI.handleClick(player, session, slot, clickType);
                break;
            case ADMIN_LIST:
                if (adminListGUI != null) adminListGUI.handleClick(player, session, slot, clickType);
                break;
            case ADMIN_EDIT:
            case ADMIN_EDIT_DELETE_CONFIRM:
                if (adminEditGUI != null) adminEditGUI.handleClick(player, session, slot, clickType);
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
        HousingGUISession session = sessions.get(player.getUniqueId());
        if (session != null && session.getInventory().equals(event.getInventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    /**
     * 開いている全 housing GUI を閉じる（プラグイン無効化時用）。
     */
    public void closeAll() {
        for (HousingGUISession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }
}
