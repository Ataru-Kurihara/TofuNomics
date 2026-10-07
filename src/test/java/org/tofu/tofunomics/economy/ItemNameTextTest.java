package org.tofu.tofunomics.economy;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * チャット文の中のアイテム名の位置を分ける処理のテスト。
 */
public class ItemNameTextTest {

    @Test
    public void 目印の前後に分ける() {
        String[] parts = ItemNameText.splitAtPlaceholder("§c{item}をクラフトするには木こりである必要があります。", "{item}");

        assertNotNull(parts);
        assertEquals("§c", parts[0]);
        assertEquals("をクラフトするには木こりである必要があります。", parts[1]);
    }

    @Test
    public void 目印が無ければnull() {
        assertNull(ItemNameText.splitAtPlaceholder("§cこのアイテムはクラフトできません。", "{item}"));
        assertNull(ItemNameText.splitAtPlaceholder(null, "{item}"));
        assertNull(ItemNameText.splitAtPlaceholder("abc", ""));
    }
}
