package org.tofu.tofunomics.market.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * マーケット GUI 共通のヘルパー（ボタン生成・価格整形・Material 名整形）。
 */
public final class MarketGUIUtil {

    private MarketGUIUtil() {
    }

    /**
     * 表示用ボタン（クリックしても消費されない装飾/操作アイテム）を生成する。
     */
    public static ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 価格を表示用文字列に整形する。整数値なら小数点を付けない。
     */
    public static String formatPrice(double price) {
        if (price == Math.floor(price) && !Double.isInfinite(price)) {
            return String.valueOf((long) price);
        }
        return String.valueOf(price);
    }

    /**
     * Material 名（例: DIAMOND_SWORD）を読みやすい表示名（Diamond Sword）に整形する。
     */
    public static String prettifyMaterial(String material) {
        if (material == null || material.isEmpty()) {
            return "アイテム";
        }
        String[] parts = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : "アイテム";
    }
}
