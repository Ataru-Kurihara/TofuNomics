package org.tofu.tofunomics.rewards;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業レベルアップ報酬システム
 */
public class JobLevelRewardManager {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    
    // 職業別レベル報酬設定
    private final Map<String, Map<Integer, LevelReward>> jobRewards;
    
    public JobLevelRewardManager(ConfigManager configManager, PlayerDAO playerDAO) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobRewards = new HashMap<>();
        
        initializeLevelRewards();
    }
    
    private void initializeLevelRewards() {
        // 鉱夫の報酬設定
        Map<Integer, LevelReward> minerRewards = new HashMap<>();
        minerRewards.put(5, new LevelReward(100.0, 
            Arrays.asList(
                createRewardItem(Material.IRON_INGOT, "鉄インゴット", 16),
                createRewardItem(Material.COAL, "石炭", 32)
            ),
            "鉱石探知能力開放"));
        minerRewards.put(10, new LevelReward(50.0,
            Arrays.asList(createRewardItem(Material.IRON_ORE, "鉄鉱石", 16)),
            "採掘技術向上"));
        minerRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.DIAMOND, "ダイヤモンド", 5),
                createRewardItem(Material.GOLD_INGOT, "金インゴット", 16)
            ),
            "採掘効率大幅向上"));
        minerRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.NETHERITE_INGOT, "ネザライトインゴット", 2),
                createRewardItem(Material.DIAMOND, "ダイヤモンド", 10),
                createRewardItem(Material.BEACON, "鉱夫の栄光", 1)
            ),
            "伝説的採掘能力獲得"));
        jobRewards.put("miner", minerRewards);
        
        // 木こりの報酬設定
        Map<Integer, LevelReward> woodcutterRewards = new HashMap<>();
        woodcutterRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.OAK_LOG, "オークの原木", 32),
                createRewardItem(Material.STICK, "棒", 64)
            ),
            "一括伐採能力開放"));
        woodcutterRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.OAK_LOG, "オークの原木", 64),
                createRewardItem(Material.OAK_SAPLING, "オークの苗木", 32)
            ),
            "伐採速度大幅向上"));
        woodcutterRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.BAMBOO, "竹", 128),
                createRewardItem(Material.OAK_LOG, "各種原木", 128),
                createRewardItem(Material.TOTEM_OF_UNDYING, "森の守護者", 1)
            ),
            "自然回復能力獲得"));
        jobRewards.put("woodcutter", woodcutterRewards);
        
        // 農家の報酬設定
        Map<Integer, LevelReward> farmerRewards = new HashMap<>();
        farmerRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.BONE_MEAL, "骨粉", 32),
                createRewardItem(Material.WHEAT_SEEDS, "小麦の種", 16)
            ),
            "作物成長加速能力開放"));
        farmerRewards.put(12, new LevelReward(50.0,
            Arrays.asList(createRewardItem(Material.BONE_MEAL, "骨粉", 16)),
            "収穫技術向上"));
        farmerRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.BONE_MEAL, "特製肥料", 64),
                createRewardItem(Material.WHEAT, "小麦", 32)
            ),
            "収穫量向上"));
        farmerRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.ENCHANTED_GOLDEN_APPLE, "豊穣の果実", 5),
                createRewardItem(Material.GOLDEN_CARROT, "金のニンジン", 16),
                createRewardItem(Material.GOLDEN_HOE, "農家の証", 1)
            ),
            "動物繁殖効率大幅向上"));
        jobRewards.put("farmer", farmerRewards);
        
        // 釣り人の報酬設定
        Map<Integer, LevelReward> fishermanRewards = new HashMap<>();
        fishermanRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.FISHING_ROD, "釣り竿", 3),
                createRewardItem(Material.STRING, "糸", 32)
            ),
            "宝物発見能力開放"));
        fishermanRewards.put(8, new LevelReward(50.0,
            Arrays.asList(createRewardItem(Material.INK_SAC, "イカスミ", 16)),
            "釣り運向上"));
        fishermanRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.COD, "鱈", 32),
                createRewardItem(Material.PRISMARINE, "プリズマリン", 16)
            ),
            "レア魚獲得確率向上"));
        fishermanRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.TRIDENT, "海の守護者", 1),
                createRewardItem(Material.HEART_OF_THE_SEA, "海の心", 1),
                createRewardItem(Material.SEA_LANTERN, "海晶ブロック", 64)
            ),
            "伝説の釣り能力獲得"));
        jobRewards.put("fisherman", fishermanRewards);
        
        // 鍛冶屋の報酬設定
        Map<Integer, LevelReward> blacksmithRewards = new HashMap<>();
        blacksmithRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.IRON_INGOT, "鉄インゴット", 32),
                createRewardItem(Material.ANVIL, "金床", 1)
            ),
            "製作効率向上"));
        blacksmithRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.DIAMOND, "ダイヤモンド", 5),
                createRewardItem(Material.NETHERITE_SCRAP, "ネザライトの欠片", 4),
                createRewardItem(Material.SMITHING_TABLE, "鍛冶台", 1)
            ),
            "高品質製作能力開放"));
        blacksmithRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.NETHERITE_INGOT, "ネザライトインゴット", 3),
                createRewardItem(Material.DIAMOND, "ダイヤモンド", 10),
                createRewardItem(Material.ANVIL, "金床", 2)
            ),
            "伝説装備製作能力獲得"));
        jobRewards.put("blacksmith", blacksmithRewards);
        
        // ポーション屋の報酬設定
        Map<Integer, LevelReward> alchemistRewards = new HashMap<>();
        alchemistRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.NETHER_WART, "ネザーウォート", 32),
                createRewardItem(Material.GLASS_BOTTLE, "ガラス瓶", 64),
                createRewardItem(Material.BREWING_STAND, "醸造台", 1)
            ),
            "ポーション効果延長"));
        alchemistRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.GLOWSTONE_DUST, "グロウストーン", 32),
                createRewardItem(Material.REDSTONE, "レッドストーン", 32),
                createRewardItem(Material.FERMENTED_SPIDER_EYE, "発酵したクモの目", 16)
            ),
            "特殊ポーション製作開放"));
        alchemistRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.DRAGON_BREATH, "ドラゴンの息", 16),
                createRewardItem(Material.BLAZE_POWDER, "ブレイズパウダー", 32),
                createRewardItem(Material.CAULDRON, "魔法の大釜", 1)
            ),
            "最上級ポーション製作能力"));
        jobRewards.put("alchemist", alchemistRewards);
        
        // エンチャンターの報酬設定  
        Map<Integer, LevelReward> enchanterRewards = new HashMap<>();
        enchanterRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.LAPIS_LAZULI, "ラピスラズリ", 64),
                createRewardItem(Material.BOOK, "本", 16),
                createRewardItem(Material.ENCHANTING_TABLE, "エンチャントテーブル", 1)
            ),
            "エンチャント成功確率向上"));
        enchanterRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.BOOKSHELF, "魔法書棚", 15),
                createRewardItem(Material.OBSIDIAN, "黒曜石", 8),
                createRewardItem(Material.BOOK, "本", 32)
            ),
            "高レベルエンチャント開放"));
        enchanterRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.NETHER_STAR, "魔導師の星", 1),
                createRewardItem(Material.LAPIS_BLOCK, "ラピスラズリブロック", 16),
                createRewardItem(Material.BOOK, "本", 64)
            ),
            "最高レベルエンチャント能力"));
        jobRewards.put("enchanter", enchanterRewards);
        
        // 建築家の報酬設定
        Map<Integer, LevelReward> architectRewards = new HashMap<>();
        architectRewards.put(5, new LevelReward(100.0,
            Arrays.asList(
                createRewardItem(Material.STONE, "石", 64),
                createRewardItem(Material.GLASS, "ガラス", 64)
            ),
            "建築効率向上"));
        architectRewards.put(15, new LevelReward(250.0,
            Arrays.asList(
                createRewardItem(Material.QUARTZ_BLOCK, "クォーツブロック", 32),
                createRewardItem(Material.WHITE_STAINED_GLASS, "色付きガラス", 16)
            ),
            "材料節約能力開放"));
        architectRewards.put(30, new LevelReward(600.0,
            Arrays.asList(
                createRewardItem(Material.END_STONE, "エンドストーン", 16),
                createRewardItem(Material.SHULKER_BOX, "シュルカーボックス", 4),
                createRewardItem(Material.BEACON, "建築の証", 1)
            ),
            "高速建築能力獲得"));
        jobRewards.put("architect", architectRewards);
    }
    
    private ItemStack createRewardItem(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "職業レベルアップ報酬",
                ChatColor.YELLOW + "TofuNomics特別アイテム"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * レベルアップ報酬をプレイヤーに付与
     */
    public boolean giveJobLevelReward(org.bukkit.entity.Player player, String jobName, int level) {
        Map<Integer, LevelReward> jobRewardMap = jobRewards.get(jobName.toLowerCase());
        if (jobRewardMap == null) {
            return false;
        }
        
        LevelReward reward = jobRewardMap.get(level);
        if (reward == null) {
            return false;
        }
        
        // 金銭報酬
        if (reward.moneyReward > 0) {
            giveMoney(player, reward.moneyReward);
        }
        
        // アイテム報酬
        if (reward.itemRewards != null) {
            giveItems(player, reward.itemRewards);
        }
        
        // レベルアップメッセージ
        sendLevelUpRewardMessage(player, jobName, level, reward);
        
        return true;
    }
    
    private void giveMoney(org.bukkit.entity.Player player, double amount) {
        String uuid = player.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(configManager.getStartingBalance());
            playerDAO.insertPlayer(tofuPlayer);
        }
        
        tofuPlayer.addBalance(amount);
        playerDAO.updatePlayerData(tofuPlayer);
    }
    
    private void giveItems(org.bukkit.entity.Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.YELLOW + "インベントリが満杯のため、" + 
                    item.getItemMeta().getDisplayName() + " を足元に落としました。");
            } else {
                player.getInventory().addItem(item);
            }
        }
    }
    
    private void sendLevelUpRewardMessage(org.bukkit.entity.Player player, String jobName, int level, LevelReward reward) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GOLD + "★ " + configManager.getJobDisplayName(jobName) + 
                          " レベル " + level + " 達成報酬 ★");
        
        if (reward.moneyReward > 0) {
            player.sendMessage(ChatColor.GREEN + "金銭報酬: " + reward.moneyReward + " " + 
                              configManager.getCurrencySymbol());
        }
        
        if (reward.itemRewards != null && !reward.itemRewards.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "アイテム報酬: " + reward.itemRewards.size() + "種類");
        }
        
        if (reward.specialAbility != null && !reward.specialAbility.isEmpty()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "特殊能力: " + reward.specialAbility);
        }
        
        player.sendMessage(ChatColor.LIGHT_PURPLE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * 指定された職業の次の報酬レベルを取得
     */
    public int getNextRewardLevel(String jobName, int currentLevel) {
        Map<Integer, LevelReward> jobRewardMap = jobRewards.get(jobName.toLowerCase());
        if (jobRewardMap == null) {
            return -1;
        }
        
        for (int level : jobRewardMap.keySet()) {
            if (level > currentLevel) {
                return level;
            }
        }
        
        return -1; // 次の報酬レベルなし
    }
    
    /**
     * 指定された職業の全報酬レベル一覧を取得
     */
    public List<Integer> getAllRewardLevels(String jobName) {
        Map<Integer, LevelReward> jobRewardMap = jobRewards.get(jobName.toLowerCase());
        if (jobRewardMap == null) {
            return Arrays.asList();
        }
        
        return Arrays.asList(jobRewardMap.keySet().toArray(new Integer[0]));
    }
    
    /**
     * 指定レベルに報酬があるかチェック
     */
    public boolean hasRewardAtLevel(String jobName, int level) {
        Map<Integer, LevelReward> jobRewardMap = jobRewards.get(jobName.toLowerCase());
        return jobRewardMap != null && jobRewardMap.containsKey(level);
    }
    
    /**
     * レベル報酬データクラス
     */
    private static class LevelReward {
        final double moneyReward;
        final List<ItemStack> itemRewards;
        final String specialAbility;
        
        LevelReward(double moneyReward, List<ItemStack> itemRewards, String specialAbility) {
            this.moneyReward = moneyReward;
            this.itemRewards = itemRewards;
            this.specialAbility = specialAbility;
        }
    }
}