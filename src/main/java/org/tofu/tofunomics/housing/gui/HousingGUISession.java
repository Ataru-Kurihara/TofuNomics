package org.tofu.tofunomics.housing.gui;

import org.bukkit.inventory.Inventory;
import org.tofu.tofunomics.models.HousingProperty;
import org.tofu.tofunomics.models.HousingRental;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * housing GUI（ハブ・物件一覧・詳細・自分の契約・契約管理）の開いている状態を保持するセッション。
 *
 * どの種類の GUI が開いているか、現在ページ、各スロットに対応する物件/契約、
 * 詳細・管理画面で選択中の物件ID/契約IDを保持し、クリック時に解決できるようにする。
 */
public class HousingGUISession {

    /** GUI の種別 */
    public enum Type {
        HUB,            // ハブ（全操作の入口）
        BROWSE,         // 物件一覧（利用可能）
        DETAIL,         // 物件詳細（借りる）
        MY_RENTALS,     // 自分の契約一覧
        MANAGE,         // 契約管理（延長・解約）
        CANCEL_CONFIRM, // 解約の確認
        ADMIN_REGISTER, // 管理者: 物件登録
        ADMIN_LIST,     // 管理者: 物件一覧
        ADMIN_EDIT,     // 管理者: 物件編集
        ADMIN_EDIT_DELETE_CONFIRM // 管理者: 物件削除の確認
    }

    private final UUID playerId;
    private final Type type;
    private final Inventory inventory;
    private int page;

    // 詳細・管理画面で選択中の対象（-1=未設定）
    private int selectedPropertyId = -1;
    private int selectedRentalId = -1;

    // スロット番号 → 表示中の物件/契約
    private final Map<Integer, HousingProperty> slotProperties = new HashMap<>();
    private final Map<Integer, HousingRental> slotRentals = new HashMap<>();

    public HousingGUISession(UUID playerId, Type type, Inventory inventory) {
        this.playerId = playerId;
        this.type = type;
        this.inventory = inventory;
        this.page = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Type getType() {
        return type;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSelectedPropertyId() {
        return selectedPropertyId;
    }

    public void setSelectedPropertyId(int selectedPropertyId) {
        this.selectedPropertyId = selectedPropertyId;
    }

    public int getSelectedRentalId() {
        return selectedRentalId;
    }

    public void setSelectedRentalId(int selectedRentalId) {
        this.selectedRentalId = selectedRentalId;
    }

    public void clearSlots() {
        slotProperties.clear();
        slotRentals.clear();
    }

    public void putSlotProperty(int slot, HousingProperty property) {
        slotProperties.put(slot, property);
    }

    public HousingProperty getPropertyAtSlot(int slot) {
        return slotProperties.get(slot);
    }

    public void putSlotRental(int slot, HousingRental rental) {
        slotRentals.put(slot, rental);
    }

    public HousingRental getRentalAtSlot(int slot) {
        return slotRentals.get(slot);
    }
}
