package org.tofu.tofunomics.housing;

import org.tofu.tofunomics.models.HousingProperty;

/**
 * 住居物件の場所を、プレイヤーが歩いて行ける形（座標）の文にするクラス。
 * 以前はワールド名しか出しておらず、物件がどこにあるか分からなかった。
 */
public final class HousingLocationFormatter {

    private HousingLocationFormatter() {
    }

    /**
     * 物件の中心あたりの座標を「X: 120 / Y: 64 / Z: -30 付近」の形で返す。
     * 範囲が登録されていない物件は「未登録」を返す。
     */
    public static String format(HousingProperty property) {
        if (property == null) {
            return "未登録";
        }
        return format(property.getX1(), property.getY1(), property.getZ1(),
                property.getX2(), property.getY2(), property.getZ2());
    }

    static String format(Integer x1, Integer y1, Integer z1, Integer x2, Integer y2, Integer z2) {
        if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
            return "未登録";
        }
        int centerX = Math.floorDiv(x1 + x2, 2);
        int floorY = Math.min(y1, y2);
        int centerZ = Math.floorDiv(z1 + z2, 2);
        return "X: " + centerX + " / Y: " + floorY + " / Z: " + centerZ + " 付近";
    }
}
