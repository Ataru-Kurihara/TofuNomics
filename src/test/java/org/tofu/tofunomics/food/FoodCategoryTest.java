package org.tofu.tofunomics.food;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * FoodCategory の Material -> カテゴリ対応のテスト
 */
public class FoodCategoryTest {

    @Test
    public void breadMaterialsMapToBread() {
        assertEquals(FoodCategory.BREAD, FoodCategory.fromMaterial(Material.BREAD));
        assertEquals(FoodCategory.BREAD, FoodCategory.fromMaterial(Material.COOKIE));
        assertEquals(FoodCategory.BREAD, FoodCategory.fromMaterial(Material.CAKE));
    }

    @Test
    public void vegetableMaterialsMapToVegetable() {
        assertEquals(FoodCategory.VEGETABLE, FoodCategory.fromMaterial(Material.CARROT));
        assertEquals(FoodCategory.VEGETABLE, FoodCategory.fromMaterial(Material.APPLE));
        assertEquals(FoodCategory.VEGETABLE, FoodCategory.fromMaterial(Material.GOLDEN_CARROT));
    }

    @Test
    public void fishMaterialsMapToFish() {
        assertEquals(FoodCategory.FISH, FoodCategory.fromMaterial(Material.COD));
        assertEquals(FoodCategory.FISH, FoodCategory.fromMaterial(Material.COOKED_SALMON));
        assertEquals(FoodCategory.FISH, FoodCategory.fromMaterial(Material.DRIED_KELP));
    }

    @Test
    public void meatMaterialsMapToMeat() {
        assertEquals(FoodCategory.MEAT, FoodCategory.fromMaterial(Material.COOKED_BEEF));
        assertEquals(FoodCategory.MEAT, FoodCategory.fromMaterial(Material.COOKED_CHICKEN));
        assertEquals(FoodCategory.MEAT, FoodCategory.fromMaterial(Material.RABBIT_STEW));
    }

    @Test
    public void specialAndNonFoodMaterialsReturnNull() {
        // 特殊食料はバフ対象外
        assertNull(FoodCategory.fromMaterial(Material.GOLDEN_APPLE));
        assertNull(FoodCategory.fromMaterial(Material.ENCHANTED_GOLDEN_APPLE));
        assertNull(FoodCategory.fromMaterial(Material.MILK_BUCKET));
        // 非食材
        assertNull(FoodCategory.fromMaterial(Material.STONE));
        assertNull(FoodCategory.fromMaterial(null));
    }

    @Test
    public void configKeysAreStable() {
        assertEquals("bread", FoodCategory.BREAD.getConfigKey());
        assertEquals("vegetable", FoodCategory.VEGETABLE.getConfigKey());
        assertEquals("fish", FoodCategory.FISH.getConfigKey());
        assertEquals("meat", FoodCategory.MEAT.getConfigKey());
    }
}
