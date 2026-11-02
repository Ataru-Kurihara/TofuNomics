package org.tofu.tofunomics.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * config_backup_&lt;ミリ秒&gt;.yml の作成と世代管理。
 * 内容が直近のバックアップと同じなら作らず、古いものは MAX_GENERATIONS を超えた分だけ消す。
 * 消すのは名前が完全に一致するファイルだけで、手で置いた別名のファイルには触らない。
 */
final class ConfigBackupStore {

    /** 残すバックアップの世代数 */
    static final int MAX_GENERATIONS = 10;

    private static final Pattern BACKUP_NAME = Pattern.compile("config_backup_(\\d{1,18})\\.yml");

    private ConfigBackupStore() {
    }

    /**
     * 直近のバックアップと内容が違うときだけバックアップを作る。
     *
     * @return 作ったファイル。直近と同じ内容で作らなかったときは null
     */
    static File createIfChanged(File configFile, File backupDir, long nowMillis) throws IOException {
        List<File> backups = listNewestFirst(backupDir);
        if (!backups.isEmpty() && Files.mismatch(configFile.toPath(), backups.get(0).toPath()) == -1) {
            return null;
        }
        File backupFile = new File(backupDir, "config_backup_" + nowMillis + ".yml");
        Files.copy(configFile.toPath(), backupFile.toPath());
        return backupFile;
    }

    /**
     * 新しい順に maxGenerations 個を残し、それより古いバックアップを消す。
     *
     * @return 消したファイルの数
     */
    static int prune(File backupDir, int maxGenerations) throws IOException {
        List<File> backups = listNewestFirst(backupDir);
        int deleted = 0;
        for (int i = maxGenerations; i < backups.size(); i++) {
            Files.delete(backups.get(i).toPath());
            deleted++;
        }
        return deleted;
    }

    private static List<File> listNewestFirst(File backupDir) {
        List<File> backups = new ArrayList<>();
        File[] files = backupDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (BACKUP_NAME.matcher(file.getName()).matches()) {
                    backups.add(file);
                }
            }
        }
        // 時刻はファイル名で比べる（コピーや展開で更新日時が変わっても順序が崩れないように）
        backups.sort(Comparator.comparingLong(ConfigBackupStore::timestampOf).reversed());
        return backups;
    }

    private static long timestampOf(File backup) {
        Matcher matcher = BACKUP_NAME.matcher(backup.getName());
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : 0L;
    }
}
