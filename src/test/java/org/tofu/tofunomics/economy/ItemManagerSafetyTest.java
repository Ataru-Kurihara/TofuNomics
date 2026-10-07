package org.tofu.tofunomics.economy;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ItemManager のお金まわりの安全性テスト。
 *
 * 検証観点:
 * - 空き計算: 収納 36 枠だけで数える（防具・オフハンドの空きを数えない）
 * - 巻き戻し: 途中まで入ったコインを、入った枚数だけ取り除く
 * - 通貨判定: 金床で「TofuGold」と名付けただけの金インゴットを通貨と認めない
 */
public class ItemManagerSafetyTest {

    private ItemManager itemManager;
    private Player player;
    private PlayerInventory inventory;

    @Before
    public void setUp() {
        itemManager = spy(new ItemManager(null));
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
    }

    private ItemStack filler() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIRT);
        when(item.getAmount()).thenReturn(64);
        return item;
    }

    // ===== 空き計算 =====

    @Test
    public void 空き計算_枠数とスタックの残りから判定する() {
        assertTrue(ItemManager.fitsInStorage(64, 1, 0, 64));
        assertFalse(ItemManager.fitsInStorage(65, 1, 0, 64));
        assertTrue(ItemManager.fitsInStorage(65, 1, 1, 64));
        assertFalse(ItemManager.fitsInStorage(1, 0, 0, 64));
        assertTrue(ItemManager.fitsInStorage(0, 0, 0, 64));
    }

    @Test
    public void 空き計算_収納が満杯なら防具やオフハンドが空いていても入らない() {
        ItemStack[] storage = new ItemStack[36];
        Arrays.fill(storage, filler());
        when(inventory.getStorageContents()).thenReturn(storage);
        // 41 枠（収納 36 + 防具 4 + オフハンド 1）のうち、後ろ 5 枠が空いている状態
        ItemStack[] all = new ItemStack[41];
        System.arraycopy(storage, 0, all, 0, 36);
        when(inventory.getContents()).thenReturn(all);

        assertFalse("収納が満杯なら 1 枚も入らない", itemManager.hasInventorySpace(player, 1));
    }

    @Test
    public void 空き計算_収納に空きがあれば入る() {
        ItemStack[] storage = new ItemStack[36];
        Arrays.fill(storage, filler());
        storage[10] = null;
        when(inventory.getStorageContents()).thenReturn(storage);

        assertTrue(itemManager.hasInventorySpace(player, 64));
        assertFalse(itemManager.hasInventorySpace(player, 65));
    }

    // ===== 巻き戻し =====

    @Test
    public void 巻き戻し_途中まで入った分を入った枚数だけ取り除いて失敗を返す() {
        ItemStack[] storage = new ItemStack[36];
        when(inventory.getStorageContents()).thenReturn(storage);
        // 100 枚渡そうとして 30 枚が入りきらなかった（＝70 枚は既に入っている）
        doReturn(30).when(itemManager).addGoldNuggetsWithLeftover(player, 100);
        doAnswer(invocation -> {
            ItemStack coin = mock(ItemStack.class);
            int amount = invocation.getArgument(0);
            when(coin.getAmount()).thenReturn(amount);
            return coin;
        }).when(itemManager).createGoldNugget(anyInt());

        assertFalse(itemManager.addGoldNuggetsToInventory(player, 100));

        ArgumentCaptor<ItemStack> removed = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, atLeastOnce()).removeItem(removed.capture());
        int totalRemoved = removed.getAllValues().stream().mapToInt(ItemStack::getAmount).sum();
        assertEquals("入った 70 枚をすべて取り除く（残すと返金と合わせて増える）", 70, totalRemoved);
    }

    @Test
    public void 巻き戻し_全部入れば何も取り除かない() {
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        doReturn(0).when(itemManager).addGoldNuggetsWithLeftover(player, 100);

        assertTrue(itemManager.addGoldNuggetsToInventory(player, 100));
        verify(inventory, never()).removeItem(any(ItemStack.class));
    }

    // ===== 通貨判定 =====

    private ItemStack goldIngot(ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.GOLD_INGOT);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    @Test
    public void 通貨判定_金床で名前を付けただけの金インゴットは通貨ではない() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("TofuGold");
        when(meta.hasLore()).thenReturn(false);
        when(meta.hasCustomModelData()).thenReturn(false);
        ItemStack renamed = goldIngot(meta);

        assertFalse(itemManager.isLegacyGoldIngot(renamed));
        assertFalse(itemManager.isCurrencyItem(renamed));
    }

    @Test
    public void 通貨判定_説明文のある旧形式のTofuGoldは通貨のまま() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn(ChatColor.GOLD + "TofuGold");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Collections.singletonList(ChatColor.GREEN + "豆腐銀行発行"));
        ItemStack legacy = goldIngot(meta);

        assertTrue(itemManager.isLegacyGoldIngot(legacy));
        assertTrue(itemManager.isCurrencyItem(legacy));
    }

    @Test
    public void 通貨判定_CustomModelDataのある金インゴットは通貨のまま() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(false);
        when(meta.hasLore()).thenReturn(false);
        when(meta.hasCustomModelData()).thenReturn(true);
        when(meta.getCustomModelData()).thenReturn(1002);

        assertTrue(itemManager.isLegacyGoldIngot(goldIngot(meta)));
    }

    @Test
    public void 通貨判定_ただの金インゴットは通貨ではない() {
        ItemMeta meta = mock(ItemMeta.class);
        assertFalse(itemManager.isCurrencyItem(goldIngot(meta)));
        assertFalse(itemManager.isCurrencyItem(null));
    }

    // ===== 銀行が発行した通貨は新旧とも通る =====

    /** 現行の発行処理（createGoldNugget / createCurrencyGoldIngot）と同じ内容のメタ情報 */
    private ItemMeta issuedMeta(String displayName, List<String> lore, int customModelData) {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn(displayName);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(lore);
        when(meta.hasCustomModelData()).thenReturn(true);
        when(meta.getCustomModelData()).thenReturn(customModelData);
        return meta;
    }

    private ItemStack item(Material type, ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    @Test
    public void 発行済み_現行形式のTofuGoldは締めたあとの判定でも通る() {
        // 現行の厳密な判定（isValidCurrencyGoldIngot）は今回変えていない。エンチャントの確認に
        // サーバーが要るためここでは呼べないので、今回締めた側の判定（表示名だけでは通さない）でも
        // 発行済みの TofuGold が説明文と CustomModelData の両方で通ることを確かめる。
        ItemMeta meta = issuedMeta(ChatColor.GOLD + "" + ChatColor.BOLD + "TofuGold", Arrays.asList(
            ChatColor.YELLOW + "TofuNomics公式上位通貨",
            ChatColor.GREEN + "豆腐銀行発行 v2.0",
            ChatColor.AQUA + "1金貨 = 9コイン",
            ChatColor.GRAY + "採掘不可・偽造防止機能付き",
            ChatColor.BLUE + "銀行で金塊通貨と交換可能"), 1002);

        assertTrue(itemManager.isLegacyGoldIngot(item(Material.GOLD_INGOT, meta)));
    }

    @Test
    public void 発行済み_旧形式のコイン_金塊という名前と2行の説明文_は通貨() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn(ChatColor.GOLD + "金塊");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Arrays.asList(
            ChatColor.YELLOW + "TofuNomicsの通貨アイテム",
            ChatColor.GRAY + "銀行で預け入れできます"));

        assertTrue(itemManager.isCurrencyItem(item(Material.GOLD_NUGGET, meta)));
    }

    @Test
    public void 発行済み_説明文が現行と違う古いTofuGoldも通貨() {
        // 説明文の行数や文面が現行と違っても、銀行の説明文が付いていれば通貨として扱う
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn(ChatColor.GOLD + "TofuGold");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Arrays.asList(
            ChatColor.YELLOW + "TofuNomics公式上位通貨", ChatColor.AQUA + "1金貨 = 9コイン"));

        assertTrue(itemManager.isCurrencyItem(item(Material.GOLD_INGOT, meta)));
    }

    // ===== 普通の金塊・金インゴットを巻き込まない（通貨クラフトの禁止は全ワールドで働く） =====

    @Test
    public void 普通の金塊と金インゴットは通貨ではない() {
        ItemMeta empty = mock(ItemMeta.class);

        assertFalse(itemManager.isCurrencyItem(item(Material.GOLD_NUGGET, empty)));
        assertFalse(itemManager.isCurrencyItem(item(Material.GOLD_INGOT, empty)));
        assertFalse(itemManager.isCurrencyItem(item(Material.GOLD_NUGGET, null)));
        assertFalse(itemManager.isCurrencyItem(item(Material.GOLD_INGOT, null)));
    }

    @Test
    public void 名前を付けただけの金塊と金インゴットは通貨ではない() {
        for (String name : new String[] {"TofuCoin", "TofuGold", "金貨", "金塊", "金インゴット", "おこづかい"}) {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.hasDisplayName()).thenReturn(true);
            when(meta.getDisplayName()).thenReturn(name);

            assertFalse(name, itemManager.isCurrencyItem(item(Material.GOLD_NUGGET, meta)));
            assertFalse(name, itemManager.isCurrencyItem(item(Material.GOLD_INGOT, meta)));
        }
    }

    @Test
    public void 普通の金塊9個のクラフトは止めない() {
        ItemStack plainNugget = item(Material.GOLD_NUGGET, mock(ItemMeta.class));
        ItemStack[] matrix = new ItemStack[9];
        Arrays.fill(matrix, plainNugget);

        assertFalse(org.tofu.tofunomics.events.CraftRestrictionEventHandler
            .containsCurrency(matrix, itemManager::isCurrencyItem));
    }
}
