package org.tofu.tofunomics.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 職業ガイドブックの必要経験値表が実装と一致することを検証する
 *
 * ガイドブックは「100 × (Lv-1)^1.9」で計算された値のまま固定されていたが、
 * 実装は「(Lv-1)^2.2 × 100」のハードコードで動いており、
 * プレイヤーに見せている数字が実値の 1/3〜1/4 になっていた。
 * 経験値カーブを config 駆動にした今、両者がずれたらCIで落とす。
 */
public class GuideBookExperienceTest {

    /** 「&8■Lv50: 59,762」形式の行から レベルと累計経験値 を取り出す */
    private static final Pattern EXP_LINE = Pattern.compile("&8■Lv(\\d+): ([\\d,]+)\\s*$");

    private static YamlConfiguration jobsConfig;

    @BeforeClass
    public static void loadConfig() {
        File file = new File("src/main/resources/config/jobs.yml");
        assertTrue("jobs.ymlが存在すること: " + file.getAbsolutePath(), file.exists());
        jobsConfig = YamlConfiguration.loadConfiguration(file);
    }

    private static double requiredExperience(int level, double base, double exponent) {
        return (level <= 1) ? 0.0 : Math.pow(level - 1, exponent) * base;
    }

    @Test
    public void ガイドブックの必要経験値が設定のカーブと一致する() {
        double base = jobsConfig.getDouble("leveling.experience.base_multiplier");
        double exponent = jobsConfig.getDouble("leveling.experience.exponent");
        assertTrue("base_multiplierが設定されていること", base > 0);
        assertTrue("exponentが設定されていること", exponent > 0);

        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (String jobName : jobsConfig.getConfigurationSection("jobs.job_settings").getKeys(false)) {
            List<String> pages = jobsConfig.getStringList("jobs.job_settings." + jobName + ".guide_book.pages");

            for (String page : pages) {
                // 必要経験値のページだけを対象にする（報酬ページも「■Lv10: 80金塊」形式のため）
                if (!page.contains("【必要経験値】")) {
                    continue;
                }

                for (String line : page.split("\n")) {
                    Matcher matcher = EXP_LINE.matcher(line.trim());
                    if (!matcher.find()) {
                        continue;
                    }

                    int level = Integer.parseInt(matcher.group(1));
                    long shown = Long.parseLong(matcher.group(2).replace(",", ""));
                    long actual = Math.round(requiredExperience(level, base, exponent));
                    checked++;

                    if (shown != actual) {
                        violations.add(String.format("%s Lv%d: 表示 %,d ≠ 実際 %,d",
                            jobName, level, shown, actual));
                    }
                }
            }
        }

        assertTrue("必要経験値の行が検出されていること", checked > 0);
        assertTrue("ガイドブックの必要経験値が実装とずれています: " + violations, violations.isEmpty());
    }

    @Test
    public void ガイドブックが最大レベルを超えるレベルを案内しない() {
        List<String> violations = new ArrayList<>();

        for (String jobName : jobsConfig.getConfigurationSection("jobs.job_settings").getKeys(false)) {
            int maxLevel = jobsConfig.getInt("jobs.job_settings." + jobName + ".max_level");
            List<String> pages = jobsConfig.getStringList("jobs.job_settings." + jobName + ".guide_book.pages");

            for (String page : pages) {
                for (String line : page.split("\n")) {
                    Matcher matcher = EXP_LINE.matcher(line.trim());
                    if (matcher.find() && Integer.parseInt(matcher.group(1)) > maxLevel) {
                        violations.add(jobName + " Lv" + matcher.group(1) + "（最大 Lv" + maxLevel + "）");
                    }
                }
            }
        }

        assertTrue("到達できないレベルがガイドブックに載っています: " + violations, violations.isEmpty());
    }
}
