package org.tofu.tofunomics.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.TofuNomics;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NPCPurchaseMarker のロジック分岐・null安全性を検証する。
 *
 * PersistentDataContainer の実際の永続化・スタック分離挙動はBukkitの保証であり、
 * テスト環境（実Bukkitサーバーなし）では検証できないため、ここでは
 * 「マーカー付与/判定が正しい分岐で呼ばれるか」をMockitoで検証する。
 */
public class NPCPurchaseMarkerTest {

    @Mock
    private TofuNomics plugin;
    @Mock
    private ItemStack item;
    @Mock
    private ItemMeta meta;
    @Mock
    private PersistentDataContainer pdc;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // NamespacedKey(plugin, key) は plugin.getName() を namespace に使う
        when(plugin.getName()).thenReturn("tofunomics");
    }

    @Test
    public void mark_metaがある場合はPDCにフラグを付与しsetItemMetaする() {
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        ItemStack result = NPCPurchaseMarker.mark(plugin, item);

        assertSame(item, result);
        verify(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 1));
        verify(item).setItemMeta(meta);
    }

    @Test
    public void mark_nullアイテムはNPEにならずnullを返す() {
        assertNull(NPCPurchaseMarker.mark(plugin, null));
    }

    @Test
    public void mark_metaがnullならsetItemMetaせずそのまま返す() {
        when(item.getItemMeta()).thenReturn(null);

        ItemStack result = NPCPurchaseMarker.mark(plugin, item);

        assertSame(item, result);
        verify(item, never()).setItemMeta(any());
    }

    @Test
    public void isMarked_マーカー付きはtrue() {
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);

        assertTrue(NPCPurchaseMarker.isMarked(plugin, item));
    }

    @Test
    public void isMarked_マーカー無しはfalse() {
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);

        assertFalse(NPCPurchaseMarker.isMarked(plugin, item));
    }

    @Test
    public void isMarked_metaを持たないアイテムはfalse() {
        when(item.hasItemMeta()).thenReturn(false);

        assertFalse(NPCPurchaseMarker.isMarked(plugin, item));
    }

    @Test
    public void isMarked_nullアイテムはfalse() {
        assertFalse(NPCPurchaseMarker.isMarked(plugin, null));
    }
}
