package org.tofu.tofunomics.players;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.tofu.tofunomics.players.NavigationSlotPolicy.Action;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * スロット9〜11に置いた私物が、復元のときにナビゲーションアイテムで消されないことのテスト。
 */
public class NavigationSlotPolicyTest {

    private ItemStack item(Material type, String displayName) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(displayName != null);
        when(meta.getDisplayName()).thenReturn(displayName);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    @Test
    public void 私物があればナビを置いて私物は移す() {
        ItemStack navigation = item(Material.COMPASS, "§aサーバー選択");
        ItemStack personal = item(Material.DIAMOND_PICKAXE, null);
        assertEquals(Action.PLACE_NAVIGATION_AND_RELOCATE_SAVED, NavigationSlotPolicy.decide(navigation, personal));
    }

    @Test
    public void 保存側が空ならナビを置くだけ() {
        ItemStack navigation = item(Material.COMPASS, "§aサーバー選択");
        assertEquals(Action.PLACE_NAVIGATION, NavigationSlotPolicy.decide(navigation, null));
        assertEquals(Action.PLACE_NAVIGATION, NavigationSlotPolicy.decide(navigation, item(Material.AIR, null)));
    }

    @Test
    public void 保存されていたのが同じナビなら増やさない() {
        ItemStack navigation = item(Material.COMPASS, "§aサーバー選択");
        ItemStack savedNavigation = item(Material.COMPASS, "§aサーバー選択");
        when(savedNavigation.isSimilar(navigation)).thenReturn(true);
        assertEquals(Action.PLACE_NAVIGATION, NavigationSlotPolicy.decide(navigation, savedNavigation));
    }

    @Test
    public void 説明文だけ違うナビも同じ物として増やさない() {
        ItemStack navigation = item(Material.COMPASS, "§aサーバー選択");
        ItemStack savedNavigation = item(Material.COMPASS, "§aサーバー選択");
        when(savedNavigation.isSimilar(navigation)).thenReturn(false);
        assertEquals(Action.PLACE_NAVIGATION, NavigationSlotPolicy.decide(navigation, savedNavigation));
    }

    @Test
    public void 同じ種類でも名前の無い私物は移す() {
        ItemStack navigation = item(Material.COMPASS, "§aサーバー選択");
        ItemStack plainCompass = item(Material.COMPASS, null);
        assertEquals(Action.PLACE_NAVIGATION_AND_RELOCATE_SAVED, NavigationSlotPolicy.decide(navigation, plainCompass));
    }

    @Test
    public void 入場時にナビが無ければ私物をそのまま戻す() {
        ItemStack personal = item(Material.DIAMOND_PICKAXE, null);
        assertEquals(Action.KEEP_SAVED, NavigationSlotPolicy.decide(null, personal));
        assertEquals(Action.KEEP_SAVED, NavigationSlotPolicy.decide(item(Material.AIR, null), personal));
    }
}
