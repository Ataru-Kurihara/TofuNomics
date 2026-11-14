package org.tofu.tofunomics.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 装備・ツールの傷み（耐久の減り）を判定する。
 *
 * 取引所は装備を固定価格で買い取るので、使い古しを満額で売れると
 * 「使い切る直前に売る」転売ができてしまう。取引所は無傷の物だけ買い取る。
 */
public final class ItemWear {

    private ItemWear() {
    }

    /**
     * 耐久が 1 でも減っていれば true。耐久を持たない品は常に false。
     */
    public static boolean isWorn(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable && ((Damageable) meta).getDamage() > 0;
    }
}
