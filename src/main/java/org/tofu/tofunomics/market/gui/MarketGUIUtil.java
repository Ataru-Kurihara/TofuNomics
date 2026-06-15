package org.tofu.tofunomics.market.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.gui.GuiUtil;

import java.util.List;

/**
 * マーケット GUI 共通ヘルパーの後方互換ラッパー。
 *
 * 実体は共通の {@link GuiUtil} に移動済み。market 既存コードからの呼び出しを維持するため委譲する。
 * 新規コードは {@link GuiUtil} を直接利用すること。
 */
public final class MarketGUIUtil {

    private MarketGUIUtil() {
    }

    public static ItemStack createButton(Material material, String name, List<String> lore) {
        return GuiUtil.createButton(material, name, lore);
    }

    public static String formatPrice(double price) {
        return GuiUtil.formatPrice(price);
    }

    public static String prettifyMaterial(String material) {
        return GuiUtil.prettifyMaterial(material);
    }
}
