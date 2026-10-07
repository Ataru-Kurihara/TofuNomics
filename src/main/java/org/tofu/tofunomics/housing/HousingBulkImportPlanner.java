package org.tofu.tofunomics.housing;

import org.tofu.tofunomics.models.HousingProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物件の一括登録（/housing admin import）で、ファイルの各行を「登録する／飛ばす」に分ける。
 * Bukkit に依存しないので、判定だけを単体でテストできる。
 */
public final class HousingBulkImportPlanner {

    private HousingBulkImportPlanner() {}

    /** ファイルの 1 件分。 */
    public static final class Entry {
        public final String name;
        public final int x1, y1, z1, x2, y2, z2;
        public final double dailyRent;

        public Entry(String name, int x1, int y1, int z1, int x2, int y2, int z2, double dailyRent) {
            this.name = name;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.dailyRent = dailyRent;
        }
    }

    /** 振り分けの結果。 */
    public static final class Plan {
        public final List<Entry> toRegister = new ArrayList<>();
        /** 飛ばした理由（そのまま表示できる文）。 */
        public final List<String> skipped = new ArrayList<>();
    }

    /**
     * @param rows ファイルの properties（name, x1, y1, z1, x2, y2, z2, 任意で daily_rent）
     * @param worldName 登録先のワールド名
     * @param defaultDailyRent daily_rent を省いた行の日額
     * @param existing 登録済みの物件
     */
    public static Plan plan(List<Map<?, ?>> rows, String worldName, double defaultDailyRent,
                            List<HousingProperty> existing) {
        Plan plan = new Plan();
        Set<String> usedNames = new HashSet<>();
        List<int[]> usedBoxes = new ArrayList<>();
        for (HousingProperty property : existing) {
            if (property.getPropertyName() != null) {
                usedNames.add(property.getPropertyName());
            }
            // リージョン名が重なると WG 領域の作成が失敗するので、物件名と同じく避ける
            if (property.getWorldguardRegionId() != null) {
                usedNames.add(property.getWorldguardRegionId());
            }
            if (property.hasCoordinates() && worldName.equals(property.getWorldName())) {
                usedBoxes.add(box(property.getX1(), property.getY1(), property.getZ1(),
                        property.getX2(), property.getY2(), property.getZ2()));
            }
        }

        int index = 0;
        for (Map<?, ?> row : rows) {
            index++;
            Object nameObj = row.get("name");
            String name = nameObj == null ? "" : nameObj.toString().trim();
            String label = name.isEmpty() ? index + "件目" : name;
            if (name.isEmpty()) {
                plan.skipped.add(label + ": name がありません");
                continue;
            }

            Integer x1 = intOf(row.get("x1")), y1 = intOf(row.get("y1")), z1 = intOf(row.get("z1"));
            Integer x2 = intOf(row.get("x2")), y2 = intOf(row.get("y2")), z2 = intOf(row.get("z2"));
            if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
                plan.skipped.add(label + ": 座標（x1 y1 z1 x2 y2 z2）が整数でそろっていません");
                continue;
            }

            double rent = defaultDailyRent;
            Object rentObj = row.get("daily_rent");
            if (rentObj != null) {
                if (!(rentObj instanceof Number)) {
                    plan.skipped.add(label + ": daily_rent が数値ではありません");
                    continue;
                }
                rent = ((Number) rentObj).doubleValue();
            }
            if (rent <= 0) {
                plan.skipped.add(label + ": 日額は 0 より大きくしてください");
                continue;
            }

            if (usedNames.contains(name)) {
                plan.skipped.add(label + ": 同じ名前の物件かリージョンが既にあります");
                continue;
            }
            int[] box = box(x1, y1, z1, x2, y2, z2);
            if (overlapsAny(box, usedBoxes)) {
                plan.skipped.add(label + ": 範囲が登録済み（またはファイル内の前の行）の物件と重なっています");
                continue;
            }

            usedNames.add(name);
            usedBoxes.add(box);
            plan.toRegister.add(new Entry(name, x1, y1, z1, x2, y2, z2, rent));
        }
        return plan;
    }

    private static Integer intOf(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            long l = (Long) value;
            return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (int) l : null;
        }
        return null;
    }

    /** 角の順番をそろえた {minX, minY, minZ, maxX, maxY, maxZ}。 */
    private static int[] box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new int[] {
            Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        };
    }

    private static boolean overlapsAny(int[] box, List<int[]> others) {
        for (int[] o : others) {
            if (box[0] <= o[3] && o[0] <= box[3]
                    && box[1] <= o[4] && o[1] <= box[4]
                    && box[2] <= o[5] && o[2] <= box[5]) {
                return true;
            }
        }
        return false;
    }
}
