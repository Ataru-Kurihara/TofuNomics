package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.dao.HousingParcelDAO;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.market.MarketItemSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 退去時の預かり荷物の受け取り GUI。
 *
 * 1 マス = 荷物 1 件（アイテム 1 スタック）。クリックで手持ちに入れる。手持ちが一杯なら入った分だけ受け取る。
 * 45 件より多いときは、受け取ると次の荷物が出てくる。
 */
public class ParcelGUI {

    private static final int SIZE = 54;
    private static final int ITEM_SLOTS = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_TAKE_ALL = 49;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final HousingRentalManager rentalManager;
    private final HousingGUIListener listener;
    private HousingHubGUI hubGUI;

    /** 開いている人ごとの「マス → 荷物」 */
    private final Map<UUID, Map<Integer, HousingParcelDAO.Parcel>> slotParcels = new HashMap<>();

    public ParcelGUI(TofuNomics plugin, HousingRentalManager rentalManager, HousingGUIListener listener) {
        this.plugin = plugin;
        this.rentalManager = rentalManager;
        this.listener = listener;
    }

    public void setHub(HousingHubGUI hubGUI) {
        this.hubGUI = hubGUI;
    }

    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6預かり荷物");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.PARCELS, gui);
            render(player, gui);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("預かり荷物GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void render(Player player, Inventory gui) {
        gui.clear();
        List<HousingParcelDAO.Parcel> parcels = rentalManager.getParcels(player.getUniqueId());
        Map<Integer, HousingParcelDAO.Parcel> map = new HashMap<>();
        int slot = 0;
        for (HousingParcelDAO.Parcel parcel : parcels) {
            if (slot >= ITEM_SLOTS) {
                break;
            }
            ItemStack item = MarketItemSerializer.deserialize(parcel.getItemData());
            if (item == null) {
                continue;
            }
            gui.setItem(slot, item);
            map.put(slot, parcel);
            slot++;
        }
        slotParcels.put(player.getUniqueId(), map);

        if (parcels.isEmpty()) {
            gui.setItem(22, GuiUtil.createButton(Material.GRAY_DYE, "§7預かり荷物はありません",
                    Collections.singletonList("§7退去のとき物件に置いていた物がここに届きます")));
        } else {
            gui.setItem(SLOT_TAKE_ALL, GuiUtil.createButton(Material.HOPPER, "§a§lすべて受け取る",
                    Arrays.asList("§7荷物 " + parcels.size() + " 件",
                            "§7手持ちに入る分だけ受け取ります",
                            "§7（1 つずつならアイテムをクリック）")));
        }
        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.ARROW, "§e戻る",
                Collections.singletonList("§7住居賃貸のメニューへ")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));
    }

    public void handleClick(Player player, HousingGUISession session, int slot, ClickType clickType) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            if (hubGUI != null) {
                hubGUI.open(player);
            }
            return;
        }
        Map<Integer, HousingParcelDAO.Parcel> map =
                slotParcels.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        if (slot == SLOT_TAKE_ALL) {
            int taken = 0;
            boolean full = false;
            for (HousingParcelDAO.Parcel parcel : rentalManager.getParcels(player.getUniqueId())) {
                int got = rentalManager.takeParcel(player, parcel);
                taken += got;
                if (player.getInventory().firstEmpty() == -1) {
                    full = true;
                    break;
                }
            }
            player.sendMessage("§a預かり荷物を受け取りました（" + taken + " 個）");
            if (full) {
                player.sendMessage("§e手持ちが一杯です。空けてからもう一度受け取ってください");
            }
            render(player, session.getInventory());
            return;
        }
        HousingParcelDAO.Parcel parcel = map.get(slot);
        if (parcel == null) {
            return;
        }
        int got = rentalManager.takeParcel(player, parcel);
        if (got == 0) {
            player.sendMessage("§e手持ちが一杯です。空けてから受け取ってください");
        }
        render(player, session.getInventory());
    }
}
