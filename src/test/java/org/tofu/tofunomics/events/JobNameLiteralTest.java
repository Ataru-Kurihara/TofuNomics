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
 * イベントハンドラが渡す職業名の妥当性テスト
 *
 * updateJobExperience に渡す職業名は内部職業名（DBの jobs.name）でなければならない。
 * 実際に "builder" / "wizard" という存在しない名前が渡されており、建築家の設置経験値と
 * エンチャント経験値がまるごと失われていた。職業名は文字列リテラルで渡されるため
 * コンパイラでは検出できず、ソースを走査して固定する。
 */
public class JobNameLiteralTest {

    /** DatabaseManager がシードする実在の職業名 */
    private static final Set<String> VALID_JOB_NAMES = new HashSet<>(Arrays.asList(
        "miner", "woodcutter", "farmer", "fisherman",
        "blacksmith", "alchemist", "enchanter", "architect"
    ));

    /** 呼び出し1行の中の文字列リテラル引数のみを拾う（メソッド定義側は対象外） */
    private static final Pattern CALL_PATTERN =
        Pattern.compile("updateJobExperience\\s*\\([^)]*?\"([^\"]+)\"");

    @Test
    public void 経験値付与に渡す職業名がすべて実在する() throws IOException {
        Path handlersDir = new File("src/main/java/org/tofu/tofunomics/events").toPath();
        assertTrue("イベントハンドラのディレクトリが存在すること", Files.isDirectory(handlersDir));

        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(handlersDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                // メソッドを定義している側は検査対象外（引数名やログ文言を拾ってしまうため）
                if (file.getFileName().toString().equals("AsyncEventUpdater.java")) {
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
            "存在しない職業名が経験値付与に渡されています（経験値が黙って失われます）: " + violations,
            violations.isEmpty());
    }
}
