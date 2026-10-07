package org.tofu.tofunomics.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.tofu.tofunomics.TofuNomics;

/**
 * /housing admin wand で配った賃貸選択ツールを識別するためのマーカー。
 *
 * 選択ツールの種類（既定は木の斧）だけで判定すると、木こりが使う普通の木の斧まで
 * 範囲選択として扱われ、ブロックを掘れなくなる。PersistentDataContainer に
 * 不可視のフラグを付け、配布した斧だけを選択ツールとして扱う。
 */
public final class HousingWandMarker {

    private static final String KEY = "housing_wand";

    private HousingWandMarker() {
    }

    private static NamespacedKey key(TofuNomics plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /**
     * アイテムに賃貸選択ツールのマーカーを付与する。
     *
     * @return マーカー付与後のItemStack（引数と同一インスタンス）
     */
    public static ItemStack mark(TofuNomics plugin, ItemStack item) {
        if (item == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * アイテムが賃貸選択ツールのマーカーを持つか判定する。
     */
    public static boolean isMarked(TofuNomics plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}
