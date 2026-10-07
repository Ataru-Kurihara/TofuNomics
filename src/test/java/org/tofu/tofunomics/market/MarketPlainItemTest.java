package org.tofu.tofunomics.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 買い注文に供給できる「素の品」の判定テスト。
 * 募集者には新品を渡すため、耐久の減った道具・エンチャント付き・通貨・NPC 購入品は供給できない。
 */
public class MarketPlainItemTest {

    private ItemStack stack(Material type, boolean hasMeta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.hasItemMeta()).thenReturn(hasMeta);
        return item;
    }

    @Test
    public void 何も付いていない品は供給できる() {
        assertTrue(MarketManager.isPlainItem(stack(Material.DIAMOND_PICKAXE, false), Material.DIAMOND_PICKAXE));
    }

    @Test
    public void 耐久の減りや名前や印が付いた品は供給できない() {
        // 耐久の減り・エンチャント・改名・通貨・NPC 購入マーカーは、どれもメタ情報として付く
        assertFalse(MarketManager.isPlainItem(stack(Material.DIAMOND_PICKAXE, true), Material.DIAMOND_PICKAXE));
        assertFalse(MarketManager.isPlainItem(stack(Material.GOLD_NUGGET, true), Material.GOLD_NUGGET));
    }

    @Test
    public void 種類が違う品や空のマスは供給できない() {
        assertFalse(MarketManager.isPlainItem(stack(Material.IRON_PICKAXE, false), Material.DIAMOND_PICKAXE));
        assertFalse(MarketManager.isPlainItem(null, Material.DIAMOND_PICKAXE));
    }

    @Test
    public void 品はあるのに素の品が足りないときだけ理由を伝える() {
        // 使いかけのツルハシ 1 本だけ持っている（素の品 0、種類では 1）
        assertTrue(MarketManager.hasOnlyNonPlainItems(0, 1, 1));
        // そもそも持っていない → 既存の「所持していません」だけでよい
        assertFalse(MarketManager.hasOnlyNonPlainItems(0, 0, 1));
        // 素の品で足りている
        assertFalse(MarketManager.hasOnlyNonPlainItems(1, 2, 1));
    }
}
