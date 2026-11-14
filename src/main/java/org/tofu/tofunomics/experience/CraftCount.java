package org.tofu.tofunomics.experience;

/**
 * クラフトの 1 回のクリックで、実際に何回分作られるかを数える。
 *
 * CraftItemEvent はクリックした時点で発火し、本当に作れたかどうかは見ない。
 * カーソルに別の物を持っている・インベントリが満杯、のときは何も作られないのに
 * 経験値だけが入ってしまう。逆にシフトクリックの一括クラフトでは何回分も作られるのに
 * 1 回分しか数えていなかった。ここで回数を出し、経験値をその回数分だけ渡す。
 *
 * サーバーなしでテストできるよう、判定に要る値だけを受け取る。
 */
public final class CraftCount {

    /** クリックの種類（ClickType を必要な分だけまとめたもの） */
    public enum Click {
        /** 普通のクリック（左・右）。カーソルに載せる */
        CURSOR,
        /** シフトクリック。材料が尽きるかインベントリが埋まるまで作る */
        SHIFT,
        /** 数字キー。ホットバーの指定のマスに入れる */
        NUMBER_KEY,
        /** Q キー。1 回分を足元に落とす */
        DROP,
        /** Ctrl+Q。材料が尽きるまで足元に落とす */
        CONTROL_DROP,
        /** それ以外（中クリックなど）。作られない */
        OTHER
    }

    private CraftCount() {
    }

    /**
     * 作られる回数（完成品の個数ではなく、レシピを回した回数）を返す。
     *
     * @param click             クリックの種類
     * @param resultAmount      1 回で出来る個数
     * @param resultMaxStack    完成品の最大スタック数
     * @param cursorEmpty       カーソルが空か
     * @param cursorSame        カーソルの物が完成品と同じ物か
     * @param cursorAmount      カーソルの物の個数
     * @param minIngredient     材料のマスのうち、いちばん少ない個数（作れる最大回数）
     * @param inventoryRoom     インベントリに完成品があと何個入るか（シフトクリック用）
     * @param hotbarSlotRoom    数字キーの行き先のマスにあと何個入るか（数字キー用）
     */
    public static int crafts(Click click, int resultAmount, int resultMaxStack,
                             boolean cursorEmpty, boolean cursorSame, int cursorAmount,
                             int minIngredient, int inventoryRoom, int hotbarSlotRoom) {
        if (resultAmount <= 0 || minIngredient <= 0) {
            return 0;
        }
        switch (click) {
            case CURSOR:
                if (cursorEmpty) {
                    return 1;
                }
                return cursorSame && cursorAmount + resultAmount <= resultMaxStack ? 1 : 0;
            case SHIFT:
                return Math.max(0, Math.min(minIngredient, inventoryRoom / resultAmount));
            case NUMBER_KEY:
                return hotbarSlotRoom >= resultAmount ? 1 : 0;
            case DROP:
                return 1;
            case CONTROL_DROP:
                return minIngredient;
            default:
                return 0;
        }
    }
}
