package org.tofu.tofunomics.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 取引所が「耐久が減った装備」を見分ける判定を確かめる。
 */
public class ItemWearTest {

    private static ItemStack withMeta(ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(meta != null);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    @Test
    public void 耐久が減っていれば傷みあり() {
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        when(((Damageable) meta).getDamage()).thenReturn(10);
        assertTrue(ItemWear.isWorn(withMeta(meta)));
    }

    @Test
    public void 無傷なら傷みなし() {
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        when(((Damageable) meta).getDamage()).thenReturn(0);
        assertFalse(ItemWear.isWorn(withMeta(meta)));
    }

    @Test
    public void 耐久を持たない品_メタ無し_nullは傷みなし() {
        assertFalse(ItemWear.isWorn(withMeta(mock(ItemMeta.class))));
        assertFalse(ItemWear.isWorn(withMeta(null)));
        assertFalse(ItemWear.isWorn(null));
    }
}
