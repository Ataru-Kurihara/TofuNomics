package org.tofu.tofunomics.events;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link UnifiedEventHandler#shouldTrackPlacedBlock(Material)} の追跡判定テスト。
 *
 * 農家が自分で植えた成熟度ゲート作物（小麦・ニンジン・ジャガイモ・ビートルート・ネザーウォート）は
 * 設置追跡の対象外であり、収穫時に経験値が付与されることを保証する（回帰防止）。
 * 一方、カボチャ・スイカ・サトウキビは手動設置→破壊の悪用防止のため追跡対象に残す。
 */
public class UnifiedEventHandlerTrackingTest {

    @Test
    public void 成熟度ゲート作物は追跡対象外() {
        // これらはプレイヤーが植えた作物ブロックそのものを収穫するため、
        // 追跡すると農業の基本ループで経験値が入らなくなる。
        // 無限ファーミングは成熟度チェック（完全成長時のみ付与）で防止済み。
        assertFalse("小麦は追跡対象外", UnifiedEventHandler.shouldTrackPlacedBlock(Material.WHEAT));
        assertFalse("ニンジンは追跡対象外", UnifiedEventHandler.shouldTrackPlacedBlock(Material.CARROTS));
        assertFalse("ジャガイモは追跡対象外", UnifiedEventHandler.shouldTrackPlacedBlock(Material.POTATOES));
        assertFalse("ビートルートは追跡対象外", UnifiedEventHandler.shouldTrackPlacedBlock(Material.BEETROOTS));
        assertFalse("ネザーウォートは追跡対象外", UnifiedEventHandler.shouldTrackPlacedBlock(Material.NETHER_WART));
    }

    @Test
    public void 自然成長系作物は追跡対象に残す() {
        // 収穫対象は茎から自然成長したブロックでありプレイヤー設置ブロックではないため、
        // 追跡しても正規収穫には影響せず、手動設置→破壊の悪用のみ防ぐ。
        assertTrue("カボチャは追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.PUMPKIN));
        assertTrue("スイカは追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.MELON));
        assertTrue("サトウキビは追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.SUGAR_CANE));
    }

    @Test
    public void 無限経験値防止系ブロックは追跡対象に残す() {
        assertTrue("石は追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.STONE));
        assertTrue("丸石は追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.COBBLESTONE));
        assertTrue("原木は追跡対象", UnifiedEventHandler.shouldTrackPlacedBlock(Material.OAK_LOG));
    }
}
