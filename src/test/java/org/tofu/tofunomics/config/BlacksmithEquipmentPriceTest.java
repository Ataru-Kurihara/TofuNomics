package org.tofu.tofunomics.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 鍛冶屋の装備の売値が、材料のまま売るより高いことを確かめる。
 *
 * 装備の売値が材料より安いと、鍛冶屋は作るほど損をし、
 * 「インゴットをそのまま流す」ほうが得になってしまう（作って売る稼ぎ方が成り立たない）。
 */
public class BlacksmithEquipmentPriceTest {

    private static ConfigurationSection prices;

    /** 装備ごとの材料（キー: 材料の item_prices キー、値: 個数）。ネザライトは「ダイヤ版 + インゴット 1」 */
    private static final Map<String, Map<String, Integer>> RECIPES = new LinkedHashMap<>();

    private static void recipe(String item, Object... materialAndCount) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < materialAndCount.length; i += 2) {
            m.put((String) materialAndCount[i], (Integer) materialAndCount[i + 1]);
        }
        RECIPES.put(item, m);
    }

    static {
        String[][] metals = {{"iron", "iron_ingot"}, {"golden", "gold_ingot"}, {"diamond", "diamond"}};
        for (String[] metal : metals) {
            recipe(metal[0] + "_sword", metal[1], 2, "stick", 1);
            recipe(metal[0] + "_helmet", metal[1], 5);
            recipe(metal[0] + "_chestplate", metal[1], 8);
            recipe(metal[0] + "_leggings", metal[1], 7);
            recipe(metal[0] + "_boots", metal[1], 4);
        }
        for (String part : new String[]{"sword", "helmet", "chestplate", "leggings", "boots"}) {
            recipe("netherite_" + part, "diamond_" + part, 1, "netherite_ingot", 1);
        }
        recipe("shield", "iron_ingot", 1, "oak_planks", 6);
        recipe("bow", "string", 3, "stick", 3);
    }

    @BeforeClass
    public static void load() {
        File file = new File("src/main/resources/config.yml");
        assertTrue(file.exists());
        prices = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npc_system.item_prices");
        assertNotNull("item_prices が存在すること", prices);
    }

    /** 材料の売値（ネザライトの元になるダイヤ装備は、その材料の売値で数える） */
    private static double materialValue(String key) {
        if (RECIPES.containsKey(key)) {
            double sum = 0;
            for (Map.Entry<String, Integer> e : RECIPES.get(key).entrySet()) {
                sum += materialValue(e.getKey()) * e.getValue();
            }
            return sum;
        }
        assertTrue("材料の価格があること: " + key, prices.contains(key));
        return prices.getDouble(key);
    }

    @Test
    public void 装備の売値は材料のまま売るより高い() {
        for (String item : RECIPES.keySet()) {
            assertTrue("価格があること: " + item, prices.contains(item));
            double price = prices.getDouble(item);
            double material = materialValue(item);
            assertTrue(item + " の売値 " + price + " が材料 " + material + " より高いこと", price > material);
        }
    }

    @Test
    public void 装備の売値は1以上の整数() {
        // 通貨は整数。0.5 未満は 0 表示になり 1 個では売れない
        for (String item : RECIPES.keySet()) {
            double price = prices.getDouble(item);
            assertTrue(item + " は 1 以上", price >= 1);
            assertEquals(item + " は整数", Math.rint(price), price, 0.0);
        }
    }

    @Test
    public void 鍛冶屋が作れないツールと釣竿は価格を載せない() {
        for (String metal : new String[]{"wooden", "stone", "iron", "golden", "diamond", "netherite"}) {
            for (String tool : new String[]{"pickaxe", "axe", "shovel", "hoe"}) {
                assertFalse(metal + "_" + tool + " は載せない", prices.contains(metal + "_" + tool));
            }
        }
        assertFalse(prices.contains("fishing_rod"));
    }
}
