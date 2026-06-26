package org.tofu.tofunomics.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.tofu.tofunomics.TofuNomics;

/**
 * NPC（食料NPC等）から購入したアイテムを識別するためのマーカー。
 *
 * PersistentDataContainer（ItemMetaの一部）に不可視のフラグを付与する。
 * これにより「NPCから購入したアイテム」と「プレイヤーが自分で採取したアイテム」を
 * 区別でき、取引所での転売（食料NPC購入価格 &lt; 取引所売却価格 を悪用した
 * アービトラージ）を防止する。
 *
 * マーカー付きアイテムはItemMetaが無印アイテムと異なるため、インベントリ上で
 * 別スタックとして扱われ混ざらない（Minecraftの仕様）。これがマーカー方式の前提。
 */
public final class NPCPurchaseMarker {

    private static final String KEY = "npc_purchase";

    private NPCPurchaseMarker() {
    }

    private static NamespacedKey key(TofuNomics plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /**
     * アイテムにNPC購入マーカーを付与する。
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
     * アイテムがNPC購入マーカーを持つか判定する。
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
