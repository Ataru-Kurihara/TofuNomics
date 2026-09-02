package org.tofu.tofunomics.events;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * 職業名リテラルの妥当性テスト
 *
 * 職業名は内部職業名（DBの jobs.name）でなければならない。実際に "builder" / "wizard"
 * という存在しない名前が使われており、建築家の設置経験値とエンチャンター特典が
 * まるごと失われていた。文字列リテラルなのでコンパイラでは検出できず、ソースを走査して固定する。
 *
 * 経験値付与の呼び出し（updateJobExperience）だけでなく、その手前の職業判定
 * （getPlayerJob / hasJob）も対象にする。判定側が誤っているとハンドラが早期 return し、
 * 付与処理にそもそも到達しないため。
 */
public class JobNameLiteralTest {

    /** DatabaseManager がシードする実在の職業名 */
    private static final Set<String> VALID_JOB_NAMES = new HashSet<>(Arrays.asList(
        "miner", "woodcutter", "farmer", "fisherman",
        "blacksmith", "alchemist", "enchanter", "architect"
    ));

    /**
     * 呼び出し1行の中の文字列リテラル引数のみを拾う（メソッド定義側は対象外）。
     * 職業名を受け取るメソッドを列挙する。
     */
    /** 職業名を受け取るメソッドを定義している側。引数名やログ文言を拾うため対象外にする */
    private static final Set<String> DEFINITION_FILES = new HashSet<>(Arrays.asList(
        "AsyncEventUpdater.java", "JobManager.java", "JobExperienceManager.java"
    ));

    private static final Pattern CALL_PATTERN = Pattern.compile(
        "(?:updateJobExperience|getPlayerJob|hasJob|giveExperienceManual)\\s*\\([^)]*?\"([^\"]+)\"");

    @Test
    public void 職業名リテラルがすべて実在する職業を指している() throws IOException {
        // イベント経路に限らず、職業名を渡している箇所すべてを対象にする
        Path sourceDir = new File("src/main/java/org/tofu/tofunomics").toPath();
        assertTrue("ソースディレクトリが存在すること", Files.isDirectory(sourceDir));

        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                // メソッドを定義している側は検査対象外（引数名やログ文言を拾ってしまうため）
                if (DEFINITION_FILES.contains(file.getFileName().toString())) {
                    continue;
                }

                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Matcher matcher = CALL_PATTERN.matcher(line);
                    while (matcher.find()) {
                        String jobName = matcher.group(1);
                        if (!VALID_JOB_NAMES.contains(jobName)) {
                            violations.add(file.getFileName() + " → \"" + jobName + "\"");
                        }
                    }
                }
            }
        }

        assertTrue(
            "存在しない職業名が使われています（判定が常に失敗し、処理が黙ってスキップされます）: " + violations,
            violations.isEmpty());
    }
}
