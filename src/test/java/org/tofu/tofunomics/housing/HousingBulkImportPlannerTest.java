package org.tofu.tofunomics.housing;

import org.junit.Test;
import org.tofu.tofunomics.models.HousingProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 物件の一括登録で、行を「登録する／飛ばす」に分ける判定のテスト。
 */
public class HousingBulkImportPlannerTest {

    private static final String WORLD = "tofuNomics";

    private static Map<?, ?> row(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("x1", x1);
        m.put("y1", y1);
        m.put("z1", z1);
        m.put("x2", x2);
        m.put("y2", y2);
        m.put("z2", z2);
        return m;
    }

    private static HousingProperty existing(String name, String world, int x1, int z1, int x2, int z2) {
        return new HousingProperty(name, world, x1, 75, z1, x2, 83, z2, name, 100);
    }

    private static HousingBulkImportPlanner.Plan plan(List<Map<?, ?>> rows, List<HousingProperty> existing) {
        return HousingBulkImportPlanner.plan(rows, WORLD, 100, existing);
    }

    @Test
    public void 重ならない行は登録に回り日額は既定になる() {
        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(row("house-9", -1, 75, -120, 8, 85, -110)),
                Collections.<HousingProperty>emptyList());

        assertEquals(1, plan.toRegister.size());
        assertTrue(plan.skipped.isEmpty());
        assertEquals("house-9", plan.toRegister.get(0).name);
        assertEquals(100.0, plan.toRegister.get(0).dailyRent, 0.0);
    }

    @Test
    public void 行の日額があればそれを使う() {
        Map<String, Object> r = new HashMap<String, Object>((Map<String, Object>) row("a", 0, 75, 0, 5, 80, 5));
        r.put("daily_rent", 250);

        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(r), Collections.<HousingProperty>emptyList());

        assertEquals(250.0, plan.toRegister.get(0).dailyRent, 0.0);
    }

    @Test
    public void 同じ名前の物件があれば飛ばす() {
        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(row("house-1", 100, 75, 100, 110, 85, 110)),
                Arrays.asList(existing("house-1", WORLD, -43, -120, -34, -111)));

        assertTrue(plan.toRegister.isEmpty());
        assertEquals(1, plan.skipped.size());
    }

    @Test
    public void 登録済みの範囲と重なれば飛ばす_角の順番が逆でも() {
        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(row("new", -20, 85, -110, -29, 75, -120)),
                Arrays.asList(existing("house-3", WORLD, -30, -121, -20, -111)));

        assertTrue(plan.toRegister.isEmpty());
        assertEquals(1, plan.skipped.size());
    }

    @Test
    public void 別のワールドの物件とは重なりを見ない() {
        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(row("new", -29, 75, -120, -20, 85, -110)),
                Arrays.asList(existing("other", "survival", -30, -121, -20, -111)));

        assertEquals(1, plan.toRegister.size());
    }

    @Test
    public void 隣り合うだけなら重なりではない() {
        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(row("new", -19, 75, -121, -10, 85, -111)),
                Arrays.asList(existing("house-3", WORLD, -30, -121, -20, -111)));

        assertEquals(1, plan.toRegister.size());
    }

    @Test
    public void ファイルの中で名前や範囲が重なる後ろの行は飛ばす() {
        List<Map<?, ?>> rows = new ArrayList<>();
        rows.add(row("a", 0, 75, 0, 9, 85, 9));
        rows.add(row("a", 20, 75, 20, 29, 85, 29));
        rows.add(row("b", 5, 75, 5, 14, 85, 14));
        rows.add(row("c", 40, 75, 40, 49, 85, 49));

        HousingBulkImportPlanner.Plan plan = plan(rows, Collections.<HousingProperty>emptyList());

        assertEquals(2, plan.toRegister.size());
        assertEquals("a", plan.toRegister.get(0).name);
        assertEquals("c", plan.toRegister.get(1).name);
        assertEquals(2, plan.skipped.size());
    }

    @Test
    public void 名前や座標が欠けた行と日額が正でない行は飛ばす() {
        Map<String, Object> noName = new HashMap<String, Object>((Map<String, Object>) row("x", 0, 75, 0, 5, 80, 5));
        noName.remove("name");
        Map<String, Object> noCoord = new HashMap<String, Object>((Map<String, Object>) row("y", 10, 75, 10, 15, 80, 15));
        noCoord.remove("z2");
        Map<String, Object> textCoord = new HashMap<String, Object>((Map<String, Object>) row("z", 20, 75, 20, 25, 80, 25));
        textCoord.put("x1", "20");
        Map<String, Object> zeroRent = new HashMap<String, Object>((Map<String, Object>) row("w", 30, 75, 30, 35, 80, 35));
        zeroRent.put("daily_rent", 0);

        HousingBulkImportPlanner.Plan plan = plan(
                Arrays.<Map<?, ?>>asList(noName, noCoord, textCoord, zeroRent),
                Collections.<HousingProperty>emptyList());

        assertTrue(plan.toRegister.isEmpty());
        assertEquals(4, plan.skipped.size());
    }
}
