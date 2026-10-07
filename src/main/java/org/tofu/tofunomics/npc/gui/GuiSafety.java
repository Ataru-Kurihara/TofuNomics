package org.tofu.tofunomics.npc.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * NPC の GUI で共通して使う、お金とアイテムを守るための判定。
 * Bukkit のサーバーが無くてもテストできるよう、判定だけを小さな関数に切り出している。
 */
public final class GuiSafety {

    private GuiSafety() {
    }

    /**
     * 閉じられたインベントリが、セッションの GUI そのものかどうか。
     *
     * GUI を開いたまま別の GUI を開くと、新しい GUI のセッションを登録した直後に
     * 古い GUI の close が届く。ここで無条件にセッションを消すと、新しい GUI の保護が外れ、
     * クリックがキャンセルされずにアイコンを取り出せてしまう。
     */
    public static boolean isSessionInventory(Inventory sessionInventory, Inventory closedInventory) {
        return sessionInventory != null && sessionInventory.equals(closedInventory);
    }

    /**
     * クリックされたのが GUI 側（上段）のマスかどうか。
     * getSlot() は手持ち側でも 0 始まりの番号を返すため、ボタンの判定には使えない。
     *
     * @param rawSlot InventoryClickEvent#getRawSlot()
     * @param topSize GUI（上段）のマス数
     */
    public static boolean isTopSlot(int rawSlot, int topSize) {
        return rawSlot >= 0 && rawSlot < topSize;
    }

    /**
     * ドラッグ操作が GUI 側（上段）のマスに 1 つでもかかっているかどうか。
     */
    public static boolean dragTouchesTop(Collection<Integer> rawSlots, int topSize) {
        if (rawSlots == null) {
            return false;
        }
        for (int rawSlot : rawSlots) {
            if (isTopSlot(rawSlot, topSize)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 売却するマスと個数を決める。
     *
     * 「数える」と「消す」を同じマスに対して行うための一覧を返す。種類だけで消すと、
     * 売れないはずの品（通貨・NPC 購入品）が同じ種類というだけで先に消えてしまう。
     *
     * @param storage  収納 36 枠（装備中・オフハンドは含めない）
     * @param sellable 売ってよい品かどうか
     * @param limit    売る個数の上限
     * @return マス番号 → そのマスから売る個数（マス番号の昇順）
     */
    public static Map<Integer, Integer> selectSellSlots(ItemStack[] storage, Predicate<ItemStack> sellable, int limit) {
        Map<Integer, Integer> selected = new LinkedHashMap<>();
        if (storage == null) {
            return selected;
        }
        int remaining = limit;
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getAmount() <= 0 || !sellable.test(item)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            selected.put(slot, take);
            remaining -= take;
        }
        return selected;
    }

    /**
     * 収納枠に、prototype と同じ品があと何個入るかを数える。
     *
     * 空きマスは 1 スタック分、同じ品（名前や印まで同じ）の途中まで入ったマスは残りの分だけ数える。
     * 種類が同じでも印（NPC 購入マーカー等）が違う品には重ならないので、数えない。
     *
     * @param storage      収納 36 枠
     * @param prototype    入れたい品
     * @param maxStackSize 1 マスに入る最大数
     */
    public static int freeCapacityFor(ItemStack[] storage, ItemStack prototype, int maxStackSize) {
        if (storage == null) {
            return 0;
        }
        long capacity = 0;
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                capacity += maxStackSize;
            } else if (item.isSimilar(prototype)) {
                capacity += Math.max(0, maxStackSize - item.getAmount());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }
}
