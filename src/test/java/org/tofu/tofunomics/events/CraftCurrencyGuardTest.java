package org.tofu.tofunomics.events;

import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 通貨を材料にしたクラフトを止める判定のテスト。
 * 材料に通貨が 1 つでもあれば、手動クラフトでは結果を空にし、自動クラフターでは中止する。
 */
public class CraftCurrencyGuardTest {

    @Test
    public void 材料に通貨が1つでもあれば検出する() {
        ItemStack coin = mock(ItemStack.class);
        ItemStack stick = mock(ItemStack.class);
        ItemStack[] matrix = {null, stick, null, null, coin, null, null, null, null};

        assertTrue(CraftRestrictionEventHandler.containsCurrency(matrix, item -> item == coin));
    }

    @Test
    public void 通貨が無ければ検出しない() {
        ItemStack stick = mock(ItemStack.class);
        ItemStack[] matrix = {stick, stick, null, null};

        assertFalse(CraftRestrictionEventHandler.containsCurrency(matrix, item -> false));
    }

    @Test
    public void 空や未設定の材料でも落ちない() {
        assertFalse(CraftRestrictionEventHandler.containsCurrency(null, item -> true));
        assertFalse(CraftRestrictionEventHandler.containsCurrency(new ItemStack[9], item -> true));
    }
}
