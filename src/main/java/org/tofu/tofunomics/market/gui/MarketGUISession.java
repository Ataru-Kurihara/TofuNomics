package org.tofu.tofunomics.market.gui;

import org.bukkit.inventory.Inventory;
import org.tofu.tofunomics.models.MarketBuyOrder;
import org.tofu.tofunomics.models.MarketListing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * マーケット GUI（一覧・自分の出品）の開いている状態を保持するセッション。
 *
 * どの種類の GUI が開いているか、現在ページ、各スロットに対応する出品を保持し、
 * クリック時にスロットから出品を解決できるようにする。
 */
public class MarketGUISession {

    /** GUI の種別 */
    public enum Type {
        BROWSE,        // マーケット一覧（全 active）
        MY_LISTINGS,   // 自分の出品管理
        BUY_BROWSE,    // 募集一覧（全 open）
        MY_BUY_ORDERS  // 自分の募集管理
    }

    private final UUID playerId;
    private final Type type;
    private final Inventory inventory;
    private int page;

    // 現在のカテゴリフィルタ（一覧 GUI でのみ使用）
    private MarketCategory category = MarketCategory.ALL;

    // スロット番号 → そのスロットに表示している出品
    private final Map<Integer, MarketListing> slotListings = new HashMap<>();

    // スロット番号 → そのスロットに表示している募集（買い注文 GUI でのみ使用）
    private final Map<Integer, MarketBuyOrder> slotBuyOrders = new HashMap<>();

    public MarketGUISession(UUID playerId, Type type, Inventory inventory) {
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

    public MarketCategory getCategory() {
        return category;
    }

    public void setCategory(MarketCategory category) {
        this.category = category;
    }

    /**
     * 描画時にスロット→出品/募集のマッピングをクリアする（売り・買い両方）
     */
    public void clearSlotListings() {
        slotListings.clear();
        slotBuyOrders.clear();
    }

    public void putSlotListing(int slot, MarketListing listing) {
        slotListings.put(slot, listing);
    }

    /**
     * クリックされたスロットに対応する出品を取得する（無ければ null）
     */
    public MarketListing getListingAtSlot(int slot) {
        return slotListings.get(slot);
    }

    public void putSlotBuyOrder(int slot, MarketBuyOrder order) {
        slotBuyOrders.put(slot, order);
    }

    /**
     * クリックされたスロットに対応する募集を取得する（無ければ null）
     */
    public MarketBuyOrder getBuyOrderAtSlot(int slot) {
        return slotBuyOrders.get(slot);
    }
}
