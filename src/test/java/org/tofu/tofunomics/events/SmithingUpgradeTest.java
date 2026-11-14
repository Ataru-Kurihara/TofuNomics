package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 鍛冶台の加工のうち、職業の専売を当てる「強化」だけを見分けられるかを確かめる。
 */
public class SmithingUpgradeTest {

    private static ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    @Test
    public void ダイヤからネザライトは強化() {
        assertTrue(CraftRestrictionEventHandler.isUpgrade(item(Material.DIAMOND_SWORD), item(Material.NETHERITE_SWORD)));
    }

    @Test
    public void 装飾は品が変わらないので強化ではない() {
        assertFalse(CraftRestrictionEventHandler.isUpgrade(item(Material.IRON_CHESTPLATE), item(Material.IRON_CHESTPLATE)));
    }

    @Test
    public void 材料や結果が無ければ強化ではない() {
        assertFalse(CraftRestrictionEventHandler.isUpgrade(null, item(Material.NETHERITE_SWORD)));
        assertFalse(CraftRestrictionEventHandler.isUpgrade(item(Material.DIAMOND_SWORD), null));
        assertFalse(CraftRestrictionEventHandler.isUpgrade(item(Material.DIAMOND_SWORD), item(Material.AIR)));
    }
}
