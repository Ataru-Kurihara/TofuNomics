package org.tofu.tofunomics.config;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 数値設定の型キャストに関する回帰テスト
 *
 * YAML は `0` を Integer、`0.0` を Double として読む。
 * ConfigManager が `(Double) getCachedValue(...)` のように直接キャストしていると、
 * 管理者が設定を手で `100.0` から `0` に書き換えただけで ClassCastException になり、
 * 設定のリロードが丸ごと失敗する。実際に本番で発生した。
 *
 * 設定ファイルは運用中に手で編集されるものなので、型で落ちない作りを固定する。
 */
public class NumericConfigTypeTest {

    private static final Pattern UNSAFE_CAST =
        Pattern.compile("\\((Double|Integer|Long|Float)\\)\\s*getCachedValue\\s*\\(\\s*\"([^\"]+)\"");

    @Test
    public void 数値設定を直接キャストしていない() throws IOException {
        File source = new File("src/main/java/org/tofu/tofunomics/config/ConfigManager.java");
        assertTrue("ConfigManager.java が存在すること", source.exists());

        String code = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

        List<String> violations = new ArrayList<>();
        Matcher matcher = UNSAFE_CAST.matcher(code);
        while (matcher.find()) {
            violations.add(matcher.group(2) + " を (" + matcher.group(1) + ") でキャスト");
        }

        assertTrue(
            "数値設定を直接キャストしています。YAMLの整数/小数の書き方でリロードが失敗します。"
                + " getCachedDouble / getCachedInt を使ってください: " + violations,
            violations.isEmpty());
    }

    /**
     * 設定ファイル側も、小数を期待する項目が整数で書かれていないかを確認する。
     * コード側が型安全になった今は落ちないが、意図せぬ丸めを避けるため揃えておく。
     */
    @Test
    public void 小数を期待する設定が実際に小数で書かれている() throws IOException {
        File config = new File("src/main/resources/config.yml");
        assertTrue("config.yml が存在すること", config.exists());

        String[] doublePaths = {
            "starting_balance", "coin_value", "min_value", "max_value",
        };

        List<String> lines = Files.readAllLines(config.toPath(), StandardCharsets.UTF_8);
        List<String> notes = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            for (String key : doublePaths) {
                if (!trimmed.startsWith(key + ":")) continue;
                String value = trimmed.substring(key.length() + 1).split("#")[0].trim();
                if (value.isEmpty()) continue;
                if (value.matches("-?\\d+")) {
                    notes.add(key + ": " + value + "（小数表記にすること）");
                }
            }
        }

        assertTrue("小数を期待する設定が整数で書かれています: " + notes, notes.isEmpty());
    }
}
