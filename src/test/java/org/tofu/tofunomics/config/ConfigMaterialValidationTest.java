package org.tofu.tofunomics.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * config.ymlのMaterial名整合テスト
 *
 * item_prices / food_items / processing_npc.wood_types に記載された
 * すべてのアイテム名がMinecraft(Bukkit) Material enumとして有効であることを検証する。
 * 1.21のenum名ミス（例: scute→turtle_scute）の混入をCIで自動検出するための回帰防止テスト。
 */
public class ConfigMaterialValidationTest {

    private static YamlConfiguration config;

    @BeforeClass
    public static void loadConfig() {
        File file = new File("src/main/resources/config.yml");
        assertTrue("config.ymlが存在すること: " + file.getAbsolutePath(), file.exists());
        config = YamlConfiguration.loadConfiguration(file);
    }

    private static boolean isValidMaterial(String name) {
        try {
            Material.valueOf(name.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static List<String> invalidKeys(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        assertNotNull("セクションが存在すること: " + path, section);
        List<String> invalid = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (!isValidMaterial(key)) {
                invalid.add(key);
            }
        }
        return invalid;
    }

    @Test
    public void item_pricesの全キーが有効なMaterialである() {
        List<String> invalid = invalidKeys("npc_system.item_prices");
        assertTrue("無効なMaterial名: " + invalid, invalid.isEmpty());
    }

    @Test
    public void food_itemsの全キーが有効なMaterialである() {
        List<String> invalid = invalidKeys("npc_system.food_npc.food_items");
        assertTrue("無効なMaterial名: " + invalid, invalid.isEmpty());
    }

    @Test
    public void wood_typesの原木と板材が有効なMaterialである() {
        ConfigurationSection section = config.getConfigurationSection("npc_system.processing_npc.wood_types");
        assertNotNull("wood_typesセクションが存在すること", section);
        List<String> invalid = new ArrayList<>();
        for (String logKey : section.getKeys(false)) {
            if (!isValidMaterial(logKey)) {
                invalid.add("log:" + logKey);
            }
            String planks = section.getString(logKey);
            if (planks == null || !isValidMaterial(planks)) {
                invalid.add("planks:" + planks);
            }
        }
        assertTrue("無効なMaterial名: " + invalid, invalid.isEmpty());
    }

    @Test
    public void item_pricesが十分な品目数を持つ() {
        ConfigurationSection section = config.getConfigurationSection("npc_system.item_prices");
        assertNotNull(section);
        // 拡充後は100品目以上であること
        assertTrue("item_pricesは100品目以上であること: " + section.getKeys(false).size(),
            section.getKeys(false).size() >= 100);
    }
}
