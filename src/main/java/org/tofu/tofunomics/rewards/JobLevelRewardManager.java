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
        minerRewards.put(5, new LevelReward(50.0, 
            Arrays.asList(createRewardItem(Material.IRON_PICKAXE, "見習い鉱夫の証", 1)),
            "鉱石探知能力開放"));
        minerRewards.put(10, new LevelReward(100.0,
            Arrays.asList(createRewardItem(Material.DIAMOND, "品質証明書", 3)),
            "採掘効率向上"));
        minerRewards.put(20, new LevelReward(200.0,
            Arrays.asList(createRewardItem(Material.NETHERITE_INGOT, "マスター鉱夫の証", 1)),
            "レア鉱石発見確率大幅向上"));
        minerRewards.put(35, new LevelReward(500.0,
            Arrays.asList(createRewardItem(Material.BEACON, "鉱夫の栄光", 1)),
            "伝説的採掘能力獲得"));
        jobRewards.put("miner", minerRewards);
        
        // 木こりの報酬設定
        Map<Integer, LevelReward> woodcutterRewards = new HashMap<>();
        woodcutterRewards.put(5, new LevelReward(50.0,
            Arrays.asList(createRewardItem(Material.IRON_AXE, "見習い木こりの証", 1)),
            "一括伐採能力開放"));
        woodcutterRewards.put(15, new LevelReward(150.0,
            Arrays.asList(createRewardItem(Material.DIAMOND_AXE, "熟練木こりの証", 1)),
            "伐採速度大幅向上"));
        woodcutterRewards.put(30, new LevelReward(300.0,
            Arrays.asList(createRewardItem(Material.TOTEM_OF_UNDYING, "森の守護者", 1)),
            "自然回復能力獲得"));
        jobRewards.put("woodcutter", woodcutterRewards);
        
        // 農家の報酬設定
        Map<Integer, LevelReward> farmerRewards = new HashMap<>();
        farmerRewards.put(5, new LevelReward(50.0,
            Arrays.asList(createRewardItem(Material.GOLDEN_HOE, "見習い農家の証", 1)),
            "作物成長加速能力開放"));
        farmerRewards.put(12, new LevelReward(120.0,
            Arrays.asList(createRewardItem(Material.BONE_MEAL, "特製肥料", 32)),
            "収穫量向上"));
        farmerRewards.put(25, new LevelReward(250.0,
            Arrays.asList(createRewardItem(Material.ENCHANTED_GOLDEN_APPLE, "豊穣の果実", 3)),
            "動物繁殖効率大幅向上"));
        jobRewards.put("farmer", farmerRewards);
        
        // 釣り人の報酬設定
        Map<Integer, LevelReward> fishermanRewards = new HashMap<>();
        fishermanRewards.put(8, new LevelReward(80.0,
            Arrays.asList(createRewardItem(Material.FISHING_ROD, "見習い釣り師の証", 1)),
            "宝物発見能力開放"));
        fishermanRewards.put(18, new LevelReward(180.0,
            Arrays.asList(createRewardItem(Material.TRIDENT, "海の守護者", 1)),
            "レア魚獲得確率向上"));
        fishermanRewards.put(40, new LevelReward(400.0,
            Arrays.asList(createRewardItem(Material.HEART_OF_THE_SEA, "海の心", 1)),
            "伝説の釣り能力獲得"));
        jobRewards.put("fisherman", fishermanRewards);
        
        // 鍛冶屋の報酬設定
        Map<Integer, LevelReward> blacksmithRewards = new HashMap<>();
        blacksmithRewards.put(6, new LevelReward(60.0,
            Arrays.asList(createRewardItem(Material.ANVIL, "見習い鍛冶台", 1)),
            "製作効率向上"));
        blacksmithRewards.put(16, new LevelReward(160.0,
            Arrays.asList(createRewardItem(Material.SMITHING_TABLE, "熟練鍛冶台", 1)),
            "高品質製作能力開放"));
        blacksmithRewards.put(28, new LevelReward(280.0,
            Arrays.asList(createRewardItem(Material.PAPER, "マスター設計図", 1)),
            "伝説装備製作能力獲得"));
        jobRewards.put("blacksmith", blacksmithRewards);
        
        // ポーション屋の報酬設定
        Map<Integer, LevelReward> alchemistRewards = new HashMap<>();
        alchemistRewards.put(7, new LevelReward(70.0,
            Arrays.asList(createRewardItem(Material.BREWING_STAND, "見習い錬金台", 1)),
            "ポーション効果延長"));
        alchemistRewards.put(14, new LevelReward(140.0,
            Arrays.asList(createRewardItem(Material.CAULDRON, "魔法の大釜", 1)),
            "特殊ポーション製作開放"));
        alchemistRewards.put(35, new LevelReward(350.0,
            Arrays.asList(createRewardItem(Material.DRAGON_BREATH, "ドラゴンの息", 8)),
            "最上級ポーション製作能力"));
        jobRewards.put("alchemist", alchemistRewards);
        
        // エンチャンターの報酬設定  
        Map<Integer, LevelReward> enchanterRewards = new HashMap<>();
        enchanterRewards.put(9, new LevelReward(90.0,
            Arrays.asList(createRewardItem(Material.ENCHANTING_TABLE, "見習いエンチャント台", 1)),
            "エンチャント成功確率向上"));
        enchanterRewards.put(22, new LevelReward(220.0,
            Arrays.asList(createRewardItem(Material.BOOKSHELF, "魔法書棚", 15)),
            "高レベルエンチャント開放"));
        enchanterRewards.put(45, new LevelReward(450.0,
            Arrays.asList(createRewardItem(Material.NETHER_STAR, "魔導師の星", 1)),
            "最高レベルエンチャント能力"));
        jobRewards.put("enchanter", enchanterRewards);
        
        // 建築家の報酬設定
        Map<Integer, LevelReward> architectRewards = new HashMap<>();
        architectRewards.put(4, new LevelReward(40.0,
            Arrays.asList(createRewardItem(Material.STRUCTURE_BLOCK, "設計ブロック", 4)),
            "建築効率向上"));
        architectRewards.put(13, new LevelReward(130.0,
            Arrays.asList(createRewardItem(Material.BARRIER, "保護ブロック", 16)),
            "材料節約能力開放"));
        architectRewards.put(27, new LevelReward(270.0,
            Arrays.asList(createRewardItem(Material.COMMAND_BLOCK, "マスター設計ブロック", 1)),
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