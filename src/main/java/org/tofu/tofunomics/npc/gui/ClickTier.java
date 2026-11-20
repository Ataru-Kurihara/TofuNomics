package org.tofu.tofunomics.npc.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * GUI の「数量切り替え」ボタンで選んだ段階を、左クリックに当てはめる。
 *
 * 統合版（Geyser 経由。タッチ・コントローラ）は箱 GUI で右クリックやシフトクリックを送れないことがある。
 * 数量ボタンで段階を選んでおけば、左クリックだけで右クリック・シフトクリックと同じ個数を扱える。
 * 段階 0 は左クリック、1 は右クリック、2 はシフト左、3 はシフト右と同じ扱い。
 * 左クリック以外（Java 版の右・シフト）は、段階に関係なくそのまま通す。
 */
final class ClickTier {

    private static final ClickType[] TIERS = {
        ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT
    };

    private ClickTier() {
    }

    /** 選んだ段階を左クリックに当てはめたクリックの種類 */
    static ClickType apply(ClickType click, int tier) {
        if (click != ClickType.LEFT || tier <= 0 || tier >= TIERS.length) {
            return click;
        }
        return TIERS[tier];
    }

    /** 次の段階（tierCount 段で一周する） */
    static int next(int tier, int tierCount) {
        if (tierCount <= 1) {
            return 0;
        }
        int t = tier + 1;
        return (t < 0 || t >= Math.min(tierCount, TIERS.length)) ? 0 : t;
    }
}
