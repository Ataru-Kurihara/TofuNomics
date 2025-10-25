package org.tofu.tofunomics.income;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 職業別動的収入システム
 * 職業活動に応じてリアルタイムで収入を獲得
 */
public class JobIncomeManager implements Listener {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final Random random;
    
    // 職業別収入テーブル
    private final Map<String, Map<Material, IncomeData>> jobIncomeMap;
    
    public JobIncomeManager(ConfigManager configManager, PlayerDAO playerDAO, JobManager jobManager) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.random = new Random();
        this.jobIncomeMap = new HashMap<>();
        
        initializeIncomeData();
    }
    
    private void initializeIncomeData() {
        // 鉱夫の収入データ
        Map<Material, IncomeData> minerIncome = new HashMap<>();
        minerIncome.put(Material.COAL_ORE, new IncomeData(2.0, 4.0));
        minerIncome.put(Material.IRON_ORE, new IncomeData(5.0, 8.0));
        minerIncome.put(Material.GOLD_ORE, new IncomeData(8.0, 12.0));
        minerIncome.put(Material.DIAMOND_ORE, new IncomeData(25.0, 40.0));
        minerIncome.put(Material.EMERALD_ORE, new IncomeData(20.0, 35.0));
        minerIncome.put(Material.ANCIENT_DEBRIS, new IncomeData(100.0, 150.0));
        minerIncome.put(Material.NETHER_QUARTZ_ORE, new IncomeData(6.0, 10.0));
        jobIncomeMap.put("miner", minerIncome);
        
        // 木こりの収入データ
        Map<Material, IncomeData> woodcutterIncome = new HashMap<>();
        woodcutterIncome.put(Material.OAK_LOG, new IncomeData(1.5, 3.0));
        woodcutterIncome.put(Material.BIRCH_LOG, new IncomeData(1.5, 3.0));
        woodcutterIncome.put(Material.SPRUCE_LOG, new IncomeData(1.5, 3.0));
        woodcutterIncome.put(Material.JUNGLE_LOG, new IncomeData(2.0, 4.0));
        woodcutterIncome.put(Material.ACACIA_LOG, new IncomeData(2.0, 4.0));
        woodcutterIncome.put(Material.DARK_OAK_LOG, new IncomeData(2.0, 4.0));
        woodcutterIncome.put(Material.WARPED_STEM, new IncomeData(5.0, 8.0));
        woodcutterIncome.put(Material.CRIMSON_STEM, new IncomeData(5.0, 8.0));
        jobIncomeMap.put("woodcutter", woodcutterIncome);
        
        // 農家の収入データ
        Map<Material, IncomeData> farmerIncome = new HashMap<>();
        farmerIncome.put(Material.WHEAT, new IncomeData(1.0, 2.0));
        farmerIncome.put(Material.POTATO, new IncomeData(0.8, 1.5));
        farmerIncome.put(Material.CARROT, new IncomeData(0.8, 1.5));
        farmerIncome.put(Material.BEETROOT, new IncomeData(1.2, 2.5));
        farmerIncome.put(Material.PUMPKIN, new IncomeData(3.0, 5.0));
        farmerIncome.put(Material.MELON, new IncomeData(2.5, 4.0));
        farmerIncome.put(Material.SUGAR_CANE, new IncomeData(0.5, 1.0));
        farmerIncome.put(Material.COCOA_BEANS, new IncomeData(2.0, 3.5));
        jobIncomeMap.put("farmer", farmerIncome);
        
        // 釣り人の収入データ  
        Map<Material, IncomeData> fishermanIncome = new HashMap<>();
        fishermanIncome.put(Material.COD, new IncomeData(3.0, 5.0));
        fishermanIncome.put(Material.SALMON, new IncomeData(4.0, 6.0));
        fishermanIncome.put(Material.TROPICAL_FISH, new IncomeData(6.0, 10.0));
        fishermanIncome.put(Material.PUFFERFISH, new IncomeData(8.0, 15.0));
        fishermanIncome.put(Material.NAUTILUS_SHELL, new IncomeData(50.0, 80.0));
        fishermanIncome.put(Material.PRISMARINE_SHARD, new IncomeData(10.0, 18.0));
        jobIncomeMap.put("fisherman", fishermanIncome);
        
        // 鍛冶屋の収入データ
        Map<Material, IncomeData> blacksmithIncome = new HashMap<>();
        blacksmithIncome.put(Material.IRON_SWORD, new IncomeData(15.0, 25.0));
        blacksmithIncome.put(Material.IRON_PICKAXE, new IncomeData(20.0, 30.0));
        blacksmithIncome.put(Material.IRON_AXE, new IncomeData(20.0, 30.0));
        blacksmithIncome.put(Material.DIAMOND_SWORD, new IncomeData(50.0, 80.0));
        blacksmithIncome.put(Material.DIAMOND_PICKAXE, new IncomeData(60.0, 100.0));
        blacksmithIncome.put(Material.NETHERITE_SWORD, new IncomeData(200.0, 300.0));
        blacksmithIncome.put(Material.NETHERITE_PICKAXE, new IncomeData(250.0, 400.0));
        jobIncomeMap.put("blacksmith", blacksmithIncome);
        
        // ポーション屋の収入データ
        Map<Material, IncomeData> alchemistIncome = new HashMap<>();
        alchemistIncome.put(Material.POTION, new IncomeData(10.0, 20.0));
        alchemistIncome.put(Material.SPLASH_POTION, new IncomeData(15.0, 25.0));
        alchemistIncome.put(Material.LINGERING_POTION, new IncomeData(25.0, 40.0));
        jobIncomeMap.put("alchemist", alchemistIncome);
        
        // エンチャンターの収入データ（エンチャントレベル別）
        Map<Material, IncomeData> enchanterIncome = new HashMap<>();
        enchanterIncome.put(Material.ENCHANTED_BOOK, new IncomeData(20.0, 50.0));
        jobIncomeMap.put("enchanter", enchanterIncome);
        
        // 建築家の収入データ（建築ブロック設置）
        Map<Material, IncomeData> architectIncome = new HashMap<>();
        architectIncome.put(Material.STONE_BRICKS, new IncomeData(0.5, 1.0));
        architectIncome.put(Material.QUARTZ_BLOCK, new IncomeData(2.0, 4.0));
        architectIncome.put(Material.PRISMARINE_BRICKS, new IncomeData(3.0, 5.0));
        architectIncome.put(Material.PURPUR_BLOCK, new IncomeData(4.0, 6.0));
        jobIncomeMap.put("architect", architectIncome);
    }
    
    // 収入システム無効化: 以下のイベントハンドラーをコメントアウト
    /*
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        // 各職業での収入チェック
        checkAndGiveIncome(player, "miner", blockType);
        checkAndGiveIncome(player, "woodcutter", blockType);
        checkAndGiveIncome(player, "farmer", blockType);
    }
    */
    
    /*
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        
        org.bukkit.entity.Player player = event.getPlayer();
        if (jobManager.hasJob(player, "fisherman") && event.getCaught() instanceof org.bukkit.entity.Item) {
            ItemStack caughtItem = ((org.bukkit.entity.Item) event.getCaught()).getItemStack();
            checkAndGiveIncome(player, "fisherman", caughtItem.getType());
        }
    }
    */
    
    /*
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();
        Material craftedType = event.getRecipe().getResult().getType();
        
        // 各職業での収入チェック
        checkAndGiveIncome(player, "blacksmith", craftedType);
        checkAndGiveIncome(player, "alchemist", craftedType);
    }
    */
    
    /**
     * 職業別収入の計算と付与
     */
    private void checkAndGiveIncome(org.bukkit.entity.Player player, String jobName, Material material) {
        if (!jobManager.hasJob(player, jobName)) {
            return;
        }
        
        Map<Material, IncomeData> jobIncome = jobIncomeMap.get(jobName);
        if (jobIncome == null || !jobIncome.containsKey(material)) {
            return;
        }
        
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) {
            return;
        }
        
        IncomeData incomeData = jobIncome.get(material);
        double income = calculateJobIncome(playerJob, incomeData);
        
        if (income > 0) {
            givePlayerIncome(player, income, jobName, material);
        }
    }
    
    /**
     * 職業レベルとスキルに基づく収入計算
     */
    private double calculateJobIncome(PlayerJob playerJob, IncomeData incomeData) {
        // ベース収入（最小値と最大値の間でランダム）
        double baseIncome = incomeData.minIncome + 
            (random.nextDouble() * (incomeData.maxIncome - incomeData.minIncome));
        
        // レベルボーナス（レベル1で100%、レベル25で150%、レベル50で200%）
        double levelMultiplier = 1.0 + (playerJob.getLevel() * 0.02);
        
        // 職業設定による倍率
        String jobName = getJobNameByPlayerJob(playerJob);
        double configMultiplier = configManager.getJobIncomeMultiplier(jobName);
        
        return baseIncome * levelMultiplier * configMultiplier;
    }
    
    /**
     * プレイヤーに収入を付与
     */
    private void givePlayerIncome(org.bukkit.entity.Player player, double income, String jobName, Material material) {
        String uuid = player.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(configManager.getStartingBalance());
            playerDAO.insertPlayer(tofuPlayer);
        }
        
        tofuPlayer.addBalance(income);
        
        if (playerDAO.updatePlayerData(tofuPlayer)) {
            // 5金塊以上の場合のみ収入メッセージを表示
            if (income >= 5.0) {
                String materialName = getMaterialDisplayName(material);
                player.sendMessage(ChatColor.YELLOW + String.format("+ %.1f %s (%s - %s)", 
                    income, configManager.getCurrencySymbol(), 
                    configManager.getJobDisplayName(jobName), materialName));
            }
        }
    }
    
    /**
     * PlayerJobから職業名を取得
     */
    private String getJobNameByPlayerJob(PlayerJob playerJob) {
        // jobIdから職業名を逆引き（実装は簡略化）
        switch (playerJob.getJobId()) {
            case 1: return "miner";
            case 2: return "woodcutter";  
            case 3: return "farmer";
            case 4: return "fisherman";
            case 5: return "blacksmith";
            case 6: return "alchemist";
            case 7: return "enchanter";
            case 8: return "architect";
            default: return "unknown";
        }
    }
    
    /**
     * マテリアルの表示名を取得
     */
    private String getMaterialDisplayName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * 手動収入付与（管理者用）
     */
    public boolean giveIncomeManual(org.bukkit.entity.Player player, String jobName, double amount) {
        if (!jobManager.hasJob(player, jobName)) {
            return false;
        }
        
        String uuid = player.getUniqueId().toString();
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid);
        
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(uuid);
            tofuPlayer.setBalance(configManager.getStartingBalance());
            playerDAO.insertPlayer(tofuPlayer);
        }
        
        tofuPlayer.addBalance(amount);
        return playerDAO.updatePlayerData(tofuPlayer);
    }
    
    /**
     * 収入データクラス
     */
    private static class IncomeData {
        final double minIncome;
        final double maxIncome;
        
        IncomeData(double minIncome, double maxIncome) {
            this.minIncome = minIncome;
            this.maxIncome = maxIncome;
        }
    }
}