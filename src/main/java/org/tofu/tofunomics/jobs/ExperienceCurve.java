package org.tofu.tofunomics.jobs;

/**
 * 職業レベルの必要経験値カーブ
 *
 * <p>必要経験値は「そのレベルに到達するための累計経験値」を表す（Lv1 は 0）。
 * 式は {@code base × (level - 1) ^ exponent}。
 *
 * <p>従来は {@link org.tofu.tofunomics.models.PlayerJob#calculateExperienceRequired(int)} と
 * {@link ExperienceManager#calculateRequiredExperience(int)} の2箇所に
 * {@code (level-1)^2.2 × 100} がハードコードされており、
 * config/jobs.yml の leveling.experience 設定（base_multiplier / exponent）が
 * まったく効いていなかった。ここに一本化して設定を反映できるようにする。
 *
 * <p><b>両者は必ず同じ値を返さなければならない。</b>
 * レベルアップ判定は ExperienceManager 側の値で行い、実際の加算は PlayerJob#levelUp が
 * 行うため、式が食い違うと「上げられると判定されたのに上がらない」無限ループになる。
 *
 * <p>既定値の 100 / 1.9 は、職業ガイドブックに記載されている累計経験値
 * （Lv5: 1,393 / Lv10: 6,502 / Lv50: 162,694）と一致する本来のカーブ。
 * 設定が読めない場合のフォールバックとして使う。
 */
public final class ExperienceCurve {

    /** 既定の基礎係数（ガイドブック記載値と一致するカーブ） */
    public static final double DEFAULT_BASE_MULTIPLIER = 100.0;

    /** 既定の指数（ガイドブック記載値と一致するカーブ） */
    public static final double DEFAULT_EXPONENT = 1.9;

    private static volatile double baseMultiplier = DEFAULT_BASE_MULTIPLIER;
    private static volatile double exponent = DEFAULT_EXPONENT;

    private ExperienceCurve() {
    }

    /**
     * 設定値でカーブを構成する。プラグイン起動時と設定リロード時に呼ぶ。
     * 不正な値（0以下）は既定値にフォールバックする。
     */
    public static void configure(double newBaseMultiplier, double newExponent) {
        baseMultiplier = (newBaseMultiplier > 0) ? newBaseMultiplier : DEFAULT_BASE_MULTIPLIER;
        exponent = (newExponent > 0) ? newExponent : DEFAULT_EXPONENT;
    }

    /** テストや異常系のために既定カーブへ戻す。 */
    public static void reset() {
        baseMultiplier = DEFAULT_BASE_MULTIPLIER;
        exponent = DEFAULT_EXPONENT;
    }

    public static double getBaseMultiplier() {
        return baseMultiplier;
    }

    public static double getExponent() {
        return exponent;
    }

    /**
     * 指定レベルに到達するために必要な累計経験値を返す。
     * Lv1以下は0（就職直後は経験値0でLv1）。
     */
    public static double requiredExperience(int level) {
        if (level <= 1) {
            return 0.0;
        }
        return Math.pow(level - 1, exponent) * baseMultiplier;
    }
}
