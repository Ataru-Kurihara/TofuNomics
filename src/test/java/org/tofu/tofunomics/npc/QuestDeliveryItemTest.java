package org.tofu.tofunomics.npc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.tofu.tofunomics.npc.gui.GuiSafety;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * クエスト納品に使える品の判定テスト。
 * 通貨と NPC 購入品は、対象と同じ種類でも納品に使えない。名前を付けただけの品は使える。
 * 数える側と消す側が同じ判定を使うので、「数には入るのに消えない」「消えるのに数に入らない」が起きない。
 */
public class QuestDeliveryItemTest {

    private ItemStack stack(Material type, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @Test
    public void 通貨とNPC購入品は納品に使えず_普通の品と改名した品は使える() {
        ItemStack tofuCoin = stack(Material.GOLD_NUGGET, 20);      // 通貨（種類は金塊）
        ItemStack boughtNugget = stack(Material.GOLD_NUGGET, 5);   // NPC 購入マーカー付き
        ItemStack plainNugget = stack(Material.GOLD_NUGGET, 3);
        ItemStack renamedNugget = stack(Material.GOLD_NUGGET, 4);  // 金床で改名しただけ
        java.util.function.Predicate<ItemStack> excluded =
            item -> item == tofuCoin || item == boughtNugget;

        assertFalse(QuestNPCManager.isDeliverable(tofuCoin, Material.GOLD_NUGGET, excluded));
        assertFalse(QuestNPCManager.isDeliverable(boughtNugget, Material.GOLD_NUGGET, excluded));
        assertTrue(QuestNPCManager.isDeliverable(plainNugget, Material.GOLD_NUGGET, excluded));
        assertTrue(QuestNPCManager.isDeliverable(renamedNugget, Material.GOLD_NUGGET, excluded));
        assertFalse(QuestNPCManager.isDeliverable(stack(Material.BONE, 1), Material.GOLD_NUGGET, excluded));
        assertFalse(QuestNPCManager.isDeliverable(null, Material.GOLD_NUGGET, excluded));

        // 消す側: 通貨と NPC 購入品のマスには触らず、使える品のマスからだけ取る
        ItemStack[] storage = new ItemStack[36];
        storage[0] = tofuCoin;
        storage[1] = boughtNugget;
        storage[2] = plainNugget;
        storage[3] = renamedNugget;
        Map<Integer, Integer> selected = GuiSafety.selectSellSlots(
            storage, item -> QuestNPCManager.isDeliverable(item, Material.GOLD_NUGGET, excluded), 5);

        assertFalse(selected.containsKey(0));
        assertFalse(selected.containsKey(1));
        assertEquals(Integer.valueOf(3), selected.get(2));
        assertEquals(Integer.valueOf(2), selected.get(3));
    }
}
