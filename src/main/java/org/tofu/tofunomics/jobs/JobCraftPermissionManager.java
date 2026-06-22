package org.tofu.tofunomics.jobs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.*;

/**
 * 職業別クラフト制限管理クラス
 *
 * 【方式】デフォルト許可方式（ブラックリスト型）
 * 各職業の「専売リスト(jobCraftableItems)」に登録されたアイテムのみ、その職業に
 * 就いているプレイヤーだけがクラフト可能。専売リストに登録されていないアイテム
 * （建材・ブロック・装飾・染料・食料など大多数）は誰でもクラフトできる。
 *
 * これにより、新バージョンで追加された一般アイテムが自動的にクラフト可能となり、
 * 旧来のホワイトリスト方式で多発していた「追加漏れによるクラフト不可」を防ぐ。
 *
 * 専売の対象は「各職業を象徴する道具・武器・防具・固有設備」に限定する。
 */
public class JobCraftPermissionManager {

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final ConfigManager configManager;
    // 職業ごとの専売アイテム（このリストにあるものだけ職業限定になる）
    private final Map<String, Set<Material>> jobCraftableItems;

    public JobCraftPermissionManager(JavaPlugin plugin, JobManager jobManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.jobCraftableItems = new HashMap<>();

        initializeJobCraftableItems();
    }

    /**
     * 職業ごとの専売アイテム（道具・武器・防具・固有設備のみ）を初期化
     */
    private void initializeJobCraftableItems() {
        // 動物装備（鞍・各種馬鎧・オオカミ鎧）— 農家と鍛冶屋で共有
        List<Material> horseEquipment = Arrays.asList(
            Material.SADDLE, Material.LEATHER_HORSE_ARMOR,
            Material.COPPER_HORSE_ARMOR, Material.NETHERITE_HORSE_ARMOR,
            Material.WOLF_ARMOR
        );

        // 鍛冶屋 (blacksmith) - 防具・武器
        Set<Material> blacksmithItems = new HashSet<>(Arrays.asList(
            // 防具類
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.COPPER_HELMET, Material.COPPER_CHESTPLATE, Material.COPPER_LEGGINGS, Material.COPPER_BOOTS,
            Material.COPPER_NAUTILUS_ARMOR, Material.IRON_NAUTILUS_ARMOR, Material.GOLDEN_NAUTILUS_ARMOR,
            Material.DIAMOND_NAUTILUS_ARMOR, Material.NETHERITE_NAUTILUS_ARMOR,
            Material.TURTLE_HELMET,
            // 武器類
            Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.COPPER_SWORD,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE,
            // 槍（1.21.11追加）— 木の槍は誰でも可のため除外
            Material.STONE_SPEAR, Material.COPPER_SPEAR, Material.IRON_SPEAR,
            Material.GOLDEN_SPEAR, Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR,
            // 盾
            Material.SHIELD
        ));
        // 動物装備 — 農家と共有
        blacksmithItems.addAll(horseEquipment);
        jobCraftableItems.put("blacksmith", blacksmithItems);

        // 鉱夫 (miner) - つるはし・シャベル・鉱石分解
        Set<Material> minerItems = new HashSet<>(Arrays.asList(
            // つるはし類
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE, Material.COPPER_PICKAXE,
            // シャベル類
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL, Material.COPPER_SHOVEL,
            // 鉱石分解レシピ（ブロック→素材、インゴット→塊）— 鉱夫の加工特権
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.REDSTONE,
            Material.LAPIS_LAZULI, Material.EMERALD, Material.NETHERITE_INGOT, Material.COPPER_INGOT,
            Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.COPPER_NUGGET,
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER
        ));
        jobCraftableItems.put("miner", minerItems);

        // 木こり (woodcutter) - 斧
        Set<Material> woodcutterItems = new HashSet<>(Arrays.asList(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.COPPER_AXE
        ));
        jobCraftableItems.put("woodcutter", woodcutterItems);

        // 農家 (farmer) - くわ・動物装備
        Set<Material> farmerItems = new HashSet<>(Arrays.asList(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE, Material.COPPER_HOE
        ));
        // 動物装備 — 鍛冶屋と共有
        farmerItems.addAll(horseEquipment);
        jobCraftableItems.put("farmer", farmerItems);

        // 釣り人 (fisherman) - 釣竿
        Set<Material> fishermanItems = new HashSet<>(Arrays.asList(
            Material.FISHING_ROD
        ));
        jobCraftableItems.put("fisherman", fishermanItems);

        // ポーション屋 (alchemist) - 醸造台（固有設備）
        Set<Material> alchemistItems = new HashSet<>(Arrays.asList(
            Material.BREWING_STAND
        ));
        jobCraftableItems.put("alchemist", alchemistItems);

        // エンチャンター (enchanter) - エンチャント台（固有設備）
        Set<Material> enchanterItems = new HashSet<>(Arrays.asList(
            Material.ENCHANTING_TABLE
        ));
        jobCraftableItems.put("enchanter", enchanterItems);

        // 建築家 (builder) - 専売なし（建材・装飾は誰でもクラフト可）
    }

    /**
     * 指定アイテムがいずれかの職業の専売品かどうか
     */
    private boolean isJobRestrictedItem(Material material) {
        if (jobCraftableItems == null) {
            return false;
        }
        for (Set<Material> items : jobCraftableItems.values()) {
            if (items != null && items.contains(material)) {
                return true;
            }
        }
        return false;
    }

    /**
     * プレイヤーが指定されたアイテムをクラフトできるかチェック
     */
    public boolean canPlayerCraftItem(Player player, Material material) {
        // null チェック
        if (player == null || material == null) {
            plugin.getLogger().warning("canPlayerCraftItem: player または material が null です");
            return false;
        }

        // 職業専売品でなければ誰でもクラフト可能（デフォルト許可方式）
        if (!isJobRestrictedItem(material)) {
            return true;
        }

        // jobManager の null チェック
        if (jobManager == null) {
            plugin.getLogger().warning("jobManager が初期化されていません");
            return false;
        }

        // プレイヤーの職業を取得
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());

        // 無職の場合は専売品をクラフト不可
        if (playerJob == null) {
            return false;
        }

        // 該当職業の専売品に含まれるかチェック（複数職業に登録されていれば各職業で可）
        Set<Material> jobItems = jobCraftableItems.get(playerJob);
        return jobItems != null && jobItems.contains(material);
    }

    /**
     * クラフト制限時のメッセージを取得（専売品をクラフトできなかった場合のみ呼ばれる）
     */
    public String getCraftDeniedMessage(Player player, Material material) {
        // null チェック
        if (player == null || material == null) {
            plugin.getLogger().warning("getCraftDeniedMessage: player または material が null です");
            return "§cクラフト制限エラー: 無効なパラメータです。";
        }

        if (jobManager == null) {
            plugin.getLogger().warning("jobManager が初期化されていません");
            return "§cシステムエラー: 職業管理システムが利用できません。";
        }

        if (configManager == null) {
            plugin.getLogger().warning("configManager が初期化されていません");
            return "§cシステムエラー: 設定管理システムが利用できません。";
        }

        String playerJob = jobManager.getPlayerJob(player.getUniqueId());

        if (playerJob == null) {
            String message = configManager.getMessage("messages.craft.no_job_required");

            // フォールバック処理
            if (message.startsWith("メッセージが見つかりません:")) {
                return "§c職業に就いていないため、このアイテムをクラフトできません。";
            }
            return message;
        }

        // どの職業でクラフト可能かを調べる
        String requiredJob = null;
        for (Map.Entry<String, Set<Material>> entry : jobCraftableItems.entrySet()) {
            if (entry.getValue().contains(material)) {
                requiredJob = entry.getKey();
                break;
            }
        }

        if (requiredJob != null) {
            String message = configManager.getMessage("messages.craft.wrong_job_required")
                .replace("{item}", material.name().toLowerCase())
                .replace("{required_job}", configManager.getJobDisplayName(requiredJob))
                .replace("{current_job}", configManager.getJobDisplayName(playerJob));

            // フォールバック処理
            if (message.startsWith("メッセージが見つかりません:")) {
                return "§c" + material.name().toLowerCase() + "をクラフトするには" +
                    configManager.getJobDisplayName(requiredJob) + "である必要があります。（現在: " +
                    configManager.getJobDisplayName(playerJob) + "）";
            }
            return message;
        }

        String message = configManager.getMessage("messages.craft.item_not_craftable");

        // フォールバック処理
        if (message.startsWith("メッセージが見つかりません:")) {
            return "§cこのアイテムはクラフトできません。";
        }
        return message;
    }

    /**
     * デバッグ用：職業の専売アイテム数を表示
     */
    public void logJobCraftableItemCounts() {
        plugin.getLogger().info("=== 職業別 専売アイテム数 ===");
        for (Map.Entry<String, Set<Material>> entry : jobCraftableItems.entrySet()) {
            plugin.getLogger().info(entry.getKey() + ": " + entry.getValue().size() + "種類");
        }
    }
}
