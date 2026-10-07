package org.tofu.tofunomics.market;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * マーケットの取引で入る職業経験値の「稼ぎすぎ」を防ぐ判定。
 *
 * 2 人で同じ品をごく安い価格で売り買いし合うと、品もお金もほとんど減らさずに経験値だけが
 * 入り続ける。次の 2 つで止める（正当な取引の経験値は残す）。
 * <ul>
 *   <li>価格の下限: 1 個あたりの価格が、その品の基準価格に対して一定の割合に満たない取引は対象外</li>
 *   <li>相手ごとの間隔: 同じ 2 人の間（売り・買いどちら向きでも）で経験値が入るのは一定時間に 1 回まで</li>
 * </ul>
 * 間隔の記録はメモリ上だけに持つ（サーバーを再起動すると数え直しになる）。
 */
public class MarketExpGuard {

    private final Map<String, Long> lastGrantedAt = new ConcurrentHashMap<>();

    /**
     * 価格が下限を満たすか。
     *
     * @param totalPrice 取引の合計価格
     * @param amount     個数
     * @param basePrice  その品 1 個の基準価格（取引所の買取価格）。0 以下なら基準なし＝制限しない
     * @param minRatio   基準価格に対する下限の割合（0 以下なら制限しない）
     */
    public static boolean meetsPriceFloor(double totalPrice, int amount, double basePrice, double minRatio) {
        if (amount <= 0) {
            return false;
        }
        if (basePrice <= 0 || minRatio <= 0) {
            return true;
        }
        double unitPrice = totalPrice / amount;
        // 浮動小数の誤差で、ちょうど下限の価格が弾かれないようにする
        return unitPrice + 1e-9 >= basePrice * minRatio;
    }

    /**
     * 2 人の組を表すキー。どちらが売り手でも同じキーになる。
     */
    public static String pairKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * この 2 人の間で、いま経験値を出してよいかを確かめ、よければ「出した」と記録する。
     *
     * @param cooldownMillis 同じ 2 人の間で次に経験値が入るまでの時間。0 以下なら制限しない
     * @return 出してよければ true
     */
    public boolean tryAcquire(UUID first, UUID second, long nowMillis, long cooldownMillis) {
        if (first == null || second == null) {
            return false;
        }
        if (cooldownMillis <= 0) {
            return true;
        }
        String key = pairKey(first, second);
        Long last = lastGrantedAt.get(key);
        if (last != null && nowMillis - last < cooldownMillis) {
            return false;
        }
        lastGrantedAt.put(key, nowMillis);
        // 古い記録をためこまない
        if (lastGrantedAt.size() > 2000) {
            lastGrantedAt.values().removeIf(time -> nowMillis - time >= cooldownMillis);
        }
        return true;
    }
}
