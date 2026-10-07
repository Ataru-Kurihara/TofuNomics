package org.tofu.tofunomics.players;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ロビーのナビゲーションアイテムの置き場（スロット9〜11）に、プレイヤーの私物が
 * 保存されていたときの扱いを決めるクラス。
 * 以前は復元後にナビゲーションアイテムで上書きしていたため、そこに置いた私物が消えた。
 */
public final class NavigationSlotPolicy {

    /** ナビゲーションアイテムの置き場 */
    public static final int[] NAVIGATION_SLOTS = {9, 10, 11};

    /** そのスロットをどうするか */
    public enum Action {
        /** 復元した中身のままにする（入場時にナビゲーションアイテムが無く、見分けが付かない場合を含む） */
        KEEP_SAVED,
        /** ナビゲーションアイテムを置く（保存されていたのは空か、同じナビゲーションアイテム） */
        PLACE_NAVIGATION,
        /** ナビゲーションアイテムを置き、保存されていた私物は別の場所へ移す */
        PLACE_NAVIGATION_AND_RELOCATE_SAVED
    }

    private NavigationSlotPolicy() {
    }

    /**
     * @param navigationItem 入場時にそのスロットにあった物（ロビー側プラグインが置いたナビゲーションアイテム）
     * @param savedItem 保存データから復元された、同じスロットの物
     */
    public static Action decide(ItemStack navigationItem, ItemStack savedItem) {
        if (isEmpty(navigationItem)) {
            return Action.KEEP_SAVED;
        }
        if (isEmpty(savedItem) || isSameNavigationItem(navigationItem, savedItem)) {
            return Action.PLACE_NAVIGATION;
        }
        return Action.PLACE_NAVIGATION_AND_RELOCATE_SAVED;
    }

    /**
     * 保存されていた物が、ナビゲーションアイテムそのものか。
     * 以前の保存データにはナビゲーションアイテムも一緒に入っているので、これを私物と
     * 見なすと入場のたびに増えてしまう。完全に同じ物のほか、種類と表示名が同じ物も
     * 同じと見なす（説明文だけが変わるナビゲーションアイテムへの備え）。
     */
    static boolean isSameNavigationItem(ItemStack navigationItem, ItemStack savedItem) {
        if (savedItem.isSimilar(navigationItem)) {
            return true;
        }
        if (savedItem.getType() != navigationItem.getType()) {
            return false;
        }
        String navigationName = customName(navigationItem);
        return navigationName != null && navigationName.equals(customName(savedItem));
    }

    private static String customName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
