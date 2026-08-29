package org.tofu.tofunomics.events;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 毛刈り（シアー）制限のワールド適用範囲テスト。
 *
 * 背景: 「毛刈りができるのは農家のみです。」が tofuNomics 以外のワールド
 * （ロビー・ミニゲーム等）でも発動し、羊を刈れない不具合があった。
 * onPlayerShearRestriction が採掘・植え付け制限と違いワールド判定を通していなかったため。
 *
 * UnifiedEventHandler は依存が多くインスタンス化しづらいため、
 * 判定を純関数 shouldDenyShear に切り出して検証する
 * （既存の shouldTrackPlacedBlock と同じ流儀）。
 */
public class ShearRestrictionWorldScopeTest {

    private static final boolean 対象ワールド = true;
    private static final boolean 対象外ワールド = false;
    private static final boolean 羊 = true;
    private static final boolean 雪ゴーレム = false;
    private static final boolean 管理者 = true;
    private static final boolean 一般 = false;

    @Test
    public void 他ワールドでは農家でなくても毛刈りできる() {
        // 本バグの回帰テスト: ロビーやミニゲームワールドに干渉しない
        assertFalse(UnifiedEventHandler.shouldDenyShear(
            対象外ワールド, 羊, 一般, () -> false));
    }

    @Test
    public void 他ワールドでは職業判定すら行わない() {
        // hasJob はDB/キャッシュ参照のため、対象外ワールドでは呼ばれてはいけない
        UnifiedEventHandler.shouldDenyShear(対象外ワールド, 羊, 一般, () -> {
            fail("対象外ワールドで職業判定が実行された");
            return false;
        });
    }

    @Test
    public void 対象ワールドで農家以外は毛刈りを拒否される() {
        assertTrue(UnifiedEventHandler.shouldDenyShear(
            対象ワールド, 羊, 一般, () -> false));
    }

    @Test
    public void 対象ワールドでも農家は毛刈りできる() {
        assertFalse(UnifiedEventHandler.shouldDenyShear(
            対象ワールド, 羊, 一般, () -> true));
    }

    @Test
    public void 管理者はバイパスされる() {
        assertFalse(UnifiedEventHandler.shouldDenyShear(
            対象ワールド, 羊, 管理者, () -> false));
    }

    @Test
    public void 羊とキノコ牛以外は制限対象外() {
        assertFalse(UnifiedEventHandler.shouldDenyShear(
            対象ワールド, 雪ゴーレム, 一般, () -> false));
    }
}
