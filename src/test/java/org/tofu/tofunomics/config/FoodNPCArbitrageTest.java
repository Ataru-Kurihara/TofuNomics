package org.tofu.tofunomics.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 食料NPCの裁定（無限マネー）防止テスト
 *
 * 取引所での売却額には職業倍率がかかるが、NPCからの購入価格にはかからない
 * （TradingGUI は purchase_prices をそのまま使う）。そのため
 *
 *     NPCでの購入価格 ≦ item_prices の買取価格 × 職業倍率
 *
 * となるアイテムがあると、「NPCで購入 → 職業専用取引所で売却」を繰り返すだけで
 * 無限に金銭を得られてしまう。
 *
 * 実行時には NPCPurchaseMarker が購入アイテムに転売防止マーカーを付けるため
 * 実害は出ないが、価格設計そのものが破綻していないことをCIで担保する。
 *
 * なお npc_system.trading_posts はサーバー固有データでリポジトリのconfig.ymlには
 * 含まれないため、取引所側の検証は ConfigValidator が起動時に行う。
 */
public class FoodNPCArbitrageTest {

    private static YamlConfiguration config;
    private static YamlConfiguration gameplay;

    @BeforeClass
    public static void loadConfigs() {
        File configFile = new File("src/main/resources/config.yml");
        assertTrue("config.ymlが存在すること: " + configFile.getAbsolutePath(), configFile.exists());
        config = YamlConfiguration.loadConfiguration(configFile);

        File gameplayFile = new File("src/main/resources/config/gameplay.yml");
        assertTrue("gameplay.ymlが存在すること: " + gameplayFile.getAbsolutePath(), gameplayFile.exists());
        gameplay = YamlConfiguration.loadConfiguration(gameplayFile);
    }

    /**
     * 全職業のうち最大の売却倍率。最も有利な職業でも裁定が成立しないことを検証するため
     * 最悪ケースとして使う。
     */
    private static double maxJobPriceMultiplier() {
        ConfigurationSection section = gameplay.getConfigurationSection("trade_system.job_price_multipliers");
        assertNotNull("job_price_multipliersが存在すること", section);

        double max = 1.0;
        for (String job : section.getKeys(false)) {
            max = Math.max(max, section.getDouble(job));
        }
        return max;
    }

    /**
     * 食料NPCタイプのうち最小の価格倍率。最も安く買える店を最悪ケースとして使う。
     */
    private static double minNpcTypeMultiplier() {
        ConfigurationSection section = config.getConfigurationSection("npc_system.food_npc.npc_types");
        assertNotNull("npc_typesが存在すること", section);

        double min = Double.MAX_VALUE;
        for (String type : section.getKeys(false)) {
            min = Math.min(min, section.getDouble(type + ".price_multiplier", 1.0));
        }
        return (min == Double.MAX_VALUE) ? 1.0 : min;
    }

    @Test
    public void 食料NPCの販売価格が取引所の売却額を上回る() {
        ConfigurationSection foodItems = config.getConfigurationSection("npc_system.food_npc.food_items");
        assertNotNull("food_itemsが存在すること", foodItems);

        ConfigurationSection itemPrices = config.getConfigurationSection("npc_system.item_prices");
        assertNotNull("item_pricesが存在すること", itemPrices);

        double globalMultiplier = gameplay.getDouble("trade_system.global_price_multiplier", 1.0);
        double maxJobMultiplier = maxJobPriceMultiplier();
        double npcTypeMultiplier = minNpcTypeMultiplier();

        List<String> violations = new ArrayList<>();

        for (String item : foodItems.getKeys(false)) {
            // item_prices に無いアイテムは取引所で売れないため裁定は成立しない
            if (!itemPrices.contains(item)) {
                continue;
            }

            // 購入価格は ceil(基本価格 × NPCタイプ倍率)（FoodNPCManager と同じ計算）
            double purchaseCost = Math.ceil(foodItems.getDouble(item) * npcTypeMultiplier);
            double sellRevenue = itemPrices.getDouble(item) * maxJobMultiplier * globalMultiplier;

            if (purchaseCost <= sellRevenue) {
                violations.add(String.format(
                    "%s（購入 %.0f ≦ 売却 %.2f = 買取 %.2f × 職業倍率 %.2f）",
                    item, purchaseCost, sellRevenue, itemPrices.getDouble(item), maxJobMultiplier));
            }
        }

        assertTrue(
            "NPCで購入して取引所で売ると利益が出るアイテムがあります（無限マネーループ）: " + violations,
            violations.isEmpty());
    }
}
