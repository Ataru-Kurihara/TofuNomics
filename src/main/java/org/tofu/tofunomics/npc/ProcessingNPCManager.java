package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.*;

/**
 * 木材加工NPCマネージャー
 * 原木を板材に加工するサービスを提供
 */
public class ProcessingNPCManager {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final CurrencyConverter currencyConverter;
    private final JobManager jobManager;
    
    // 加工所のデータを保持
    private final Map<String, ProcessingStation> processingStations;
    
    // 原木→板材のマッピング
    private final Map<Material, Material> logToPlanksMap;
    
    public ProcessingNPCManager(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager, 
                               CurrencyConverter currencyConverter, JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.currencyConverter = currencyConverter;
        this.jobManager = jobManager;
        this.processingStations = new HashMap<>();
        this.logToPlanksMap = new HashMap<>();
        
        initializeLogToPlanksMapping();
    }
    
    /**
     * 原木→板材のマッピングを初期化
     */
    private void initializeLogToPlanksMapping() {
        logToPlanksMap.put(Material.OAK_LOG, Material.OAK_PLANKS);
        logToPlanksMap.put(Material.SPRUCE_LOG, Material.SPRUCE_PLANKS);
        logToPlanksMap.put(Material.BIRCH_LOG, Material.BIRCH_PLANKS);
        logToPlanksMap.put(Material.JUNGLE_LOG, Material.JUNGLE_PLANKS);
        logToPlanksMap.put(Material.ACACIA_LOG, Material.ACACIA_PLANKS);
        logToPlanksMap.put(Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS);
        logToPlanksMap.put(Material.CRIMSON_STEM, Material.CRIMSON_PLANKS);
        logToPlanksMap.put(Material.WARPED_STEM, Material.WARPED_PLANKS);
        
        plugin.getLogger().info("原木→板材マッピング初期化完了: " + logToPlanksMap.size() + "種類");
    }
    
    /**
     * 加工所データクラス
     */
    public static class ProcessingStation {
        private final String id;
        private final String name;
        private final Location location;
        private UUID npcId;
        
        public ProcessingStation(String id, String name, Location location, UUID npcId) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.npcId = npcId;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public UUID getNpcId() { return npcId; }
        public void setNpcId(UUID npcId) { this.npcId = npcId; }
    }
    
    /**
     * 加工NPCシステムを初期化
     */
    public void initializeProcessingNPCs() {
        try {
            if (!configManager.isProcessingNPCEnabled()) {
                plugin.getLogger().info("加工NPCシステムは無効化されています");
                return;
            }
            
            spawnProcessingNPCs();
            plugin.getLogger().info("加工NPCシステムを初期化しました");
        } catch (Exception e) {
            plugin.getLogger().severe("加工NPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 加工NPCを生成
     */
    private void spawnProcessingNPCs() {
        plugin.getLogger().info("=== 加工NPC生成開始 ===");
        
        processingStations.clear();
        
        List<Map<?, ?>> processingConfigs = configManager.getProcessingNPCConfigs();
        plugin.getLogger().info("設定から " + processingConfigs.size() + " 個の加工所を読み込み");
        
        for (Map<?, ?> config : processingConfigs) {
            try {
                String id = (String) config.get("id");
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                int x = (Integer) config.get("x");
                int y = (Integer) config.get("y");
                int z = (Integer) config.get("z");
                
                if (id == null || name == null || world == null) {
                    plugin.getLogger().warning("加工NPCの設定が不完全です: " + config);
                    continue;
                }
                
                // yaw/pitchを取得
                float yaw = 0.0f;
                float pitch = 0.0f;
                
                if (config.containsKey("yaw") && config.get("yaw") != null) {
                    yaw = ((Number) config.get("yaw")).floatValue();
                }
                
                if (config.containsKey("pitch") && config.get("pitch") != null) {
                    pitch = ((Number) config.get("pitch")).floatValue();
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5, yaw, pitch);
                plugin.getLogger().info("加工NPCを生成中: " + name + " at [" + x + ", " + y + ", " + z + "]");
                
                Villager processingNPC = npcManager.createNPC(location, "processing", name);
                
                if (processingNPC != null) {
                    setupProcessingNPC(processingNPC);
                    
                    ProcessingStation station = new ProcessingStation(id, name, location, processingNPC.getUniqueId());
                    processingStations.put(id, station);
                    
                    plugin.getLogger().info("========================================");
                    plugin.getLogger().info("加工NPC配置成功:");
                    plugin.getLogger().info("  名前: " + name);
                    plugin.getLogger().info("  ID: " + id);
                    plugin.getLogger().info("  UUID: " + processingNPC.getUniqueId());
                    plugin.getLogger().info("========================================");
                } else {
                    plugin.getLogger().severe("加工NPCの生成に失敗しました: " + name);
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("加工NPC生成中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 加工NPCの外観設定
     */
    private void setupProcessingNPC(Villager npc) {
        npc.setProfession(Villager.Profession.TOOLSMITH);
        npc.setVillagerType(Villager.Type.PLAINS);
        npc.setVillagerLevel(5);
    }
    
    /**
     * 加工NPCとのインタラクション処理
     */
    public boolean handleProcessingNPCInteraction(Player player, UUID npcId) {
        plugin.getLogger().info("=== 加工NPC相互作用開始 ===");
        plugin.getLogger().info("プレイヤー: " + player.getName() + ", NPC ID: " + npcId);
        
        try {
            // NPC存在チェック
            if (!npcManager.isNPCEntity(npcId)) {
                plugin.getLogger().warning("NPCエンティティが見つかりません: " + npcId);
                return false;
            }
            
            // NPCデータ取得
            NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
            if (npcData == null || !npcData.getNpcType().equals("processing")) {
                plugin.getLogger().warning("加工NPCデータが見つかりません: " + npcId);
                return false;
            }
            
            // 加工所データ取得
            ProcessingStation station = getProcessingStationByNPCId(npcId);
            if (station == null) {
                plugin.getLogger().warning("加工所データが見つかりません: " + npcId);
                player.sendMessage("§c加工所の情報を読み込めませんでした。");
                return false;
            }
            
            // 挨拶メッセージ
            if (isWoodcutter(player)) {
                player.sendMessage(configManager.getProcessingNPCMessage("woodcutter_greeting"));
            } else {
                player.sendMessage(configManager.getProcessingNPCMessage("greeting"));
            }
            
            // GUIを開く
            openProcessingGUI(player, station, npcData);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("加工NPC処理中に例外発生: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c処理中にエラーが発生しました。");
            return true;
        }
    }
    
    /**
     * 加工GUIを開く
     */
    private void openProcessingGUI(Player player, ProcessingStation station, NPCManager.NPCData npcData) {
        int delayTicks = configManager.getNPCGUIDelayTicks();
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isPlayerNearNPC(player, npcData)) {
                org.tofu.tofunomics.npc.gui.ProcessingGUI processingGUI = plugin.getProcessingGUI();
                
                if (processingGUI != null) {
                    processingGUI.openProcessingGUI(player, station.getNpcId());
                } else {
                    player.sendMessage("§c加工メニューの表示に失敗しました。");
                }
            }
        }, delayTicks);
    }
    
    /**
     * 原木を板材に加工する処理
     */
    public ProcessingResult processWoodConversion(Player player, UUID npcId, List<ItemStack> logItems) {
        ProcessingStation station = getProcessingStationByNPCId(npcId);
        if (station == null) {
            return new ProcessingResult(false, "加工所が見つかりません", 0, 0, 0.0);
        }
        
        int totalLogs = 0;
        int totalPlanks = 0;
        double totalFee = 0.0;
        Map<Material, Integer> logsToProcess = new HashMap<>();
        
        // 原木をカウント
        for (ItemStack item : logItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            Material logType = item.getType();
            if (logToPlanksMap.containsKey(logType)) {
                int amount = item.getAmount();
                logsToProcess.put(logType, logsToProcess.getOrDefault(logType, 0) + amount);
                totalLogs += amount;
            }
        }
        
        if (totalLogs == 0) {
            return new ProcessingResult(false, configManager.getProcessingNPCMessage("no_logs"), 0, 0, 0.0);
        }
        
        // 加工料金計算
        totalFee = calculateProcessingFee(player, totalLogs);
        
        // 残高チェック
        double currentBalance = currencyConverter.getBalance(player.getUniqueId());
        if (currentBalance < totalFee) {
            String message = configManager.getProcessingNPCMessage("insufficient_funds")
                .replace("%required%", String.format("%.0f", totalFee));
            return new ProcessingResult(false, message, totalLogs, 0, totalFee);
        }
        
        // インベントリ空き容量チェック（板材は原木の4倍になる）
        int requiredSlots = calculateRequiredSlots(logsToProcess);
        if (!hasEnoughInventorySpace(player, requiredSlots)) {
            return new ProcessingResult(false, configManager.getProcessingNPCMessage("inventory_full"), totalLogs, 0, totalFee);
        }
        
        // 料金徴収
        if (totalFee > 0) {
            if (!currencyConverter.subtractBalance(player.getUniqueId(), totalFee)) {
                return new ProcessingResult(false, "§c料金の引き落としに失敗しました", totalLogs, 0, totalFee);
            }
        }
        
        // 原木を削除して板材を付与
        for (ItemStack item : logItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            Material logType = item.getType();
            Material planksType = logToPlanksMap.get(logType);
            
            if (planksType != null) {
                int logAmount = item.getAmount();
                int planksAmount = logAmount * 4; // 原木1個→板材4個
                
                // 原木を削除
                item.setAmount(0);
                
                // 板材を付与
                ItemStack planks = new ItemStack(planksType, planksAmount);
                player.getInventory().addItem(planks);
                
                totalPlanks += planksAmount;
            }
        }
        
        // 成功メッセージ
        String message;
        if (totalFee > 0) {
            message = configManager.getProcessingNPCMessage("processing_success_with_fee")
                .replace("%amount%", String.valueOf(totalLogs))
                .replace("%fee%", String.format("%.0f", totalFee));
        } else {
            message = configManager.getProcessingNPCMessage("processing_success")
                .replace("%amount%", String.valueOf(totalLogs));
        }
        
        return new ProcessingResult(true, message, totalLogs, totalPlanks, totalFee);
    }
    
    /**
     * 加工料金を計算
     */
    private double calculateProcessingFee(Player player, int logAmount) {
        double feePerLog;
        
        if (isWoodcutter(player)) {
            feePerLog = configManager.getProcessingWoodcutterFee();
        } else {
            feePerLog = configManager.getProcessingBaseFee();
        }
        
        return feePerLog * logAmount;
    }
    
    /**
     * 必要なインベントリスロット数を計算
     */
    private int calculateRequiredSlots(Map<Material, Integer> logsToProcess) {
        int totalPlanks = 0;
        for (int logAmount : logsToProcess.values()) {
            totalPlanks += logAmount * 4;
        }
        // 64個（1スタック）で割って切り上げ
        return (int) Math.ceil(totalPlanks / 64.0);
    }
    
    /**
     * インベントリに十分な空きがあるかチェック
     */
    private boolean hasEnoughInventorySpace(Player player, int requiredSlots) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= requiredSlots;
    }
    
    /**
     * プレイヤーが木こりかどうかを判定
     */
    private boolean isWoodcutter(Player player) {
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        return "woodcutter".equals(playerJob);
    }
    
    /**
     * プレイヤーがNPCの近くにいるかチェック
     */
    private boolean isPlayerNearNPC(Player player, NPCManager.NPCData npcData) {
        try {
            Villager npcEntity = null;
            for (Villager villager : player.getWorld().getEntitiesByClass(Villager.class)) {
                if (villager.getUniqueId().equals(npcData.getEntityId())) {
                    npcEntity = villager;
                    break;
                }
            }
            
            if (npcEntity == null) {
                return false;
            }
            
            double distance = player.getLocation().distance(npcEntity.getLocation());
            int accessRange = configManager.getNPCAccessRange();
            
            return distance <= accessRange;
            
        } catch (Exception e) {
            plugin.getLogger().warning("距離チェック中にエラー: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * NPC IDから加工所を取得
     */
    private ProcessingStation getProcessingStationByNPCId(UUID npcId) {
        return processingStations.values().stream()
            .filter(station -> station.getNpcId().equals(npcId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 原木が加工可能かチェック
     */
    public boolean isProcessableLog(Material material) {
        return logToPlanksMap.containsKey(material);
    }
    
    /**
     * 原木から変換される板材を取得
     */
    public Material getPlanksFromLog(Material logType) {
        return logToPlanksMap.get(logType);
    }
    
    /**
     * 加工NPCを削除
     */
    public void removeProcessingNPCs() {
        Collection<NPCManager.NPCData> processingNPCs = npcManager.getNPCsByType("processing");
        for (NPCManager.NPCData npcData : processingNPCs) {
            npcManager.removeNPC(npcData.getEntityId());
        }
        processingStations.clear();
        plugin.getLogger().info("全ての加工NPCを削除しました");
    }
    
    /**
     * 加工NPCをリロード
     */
    public void reloadProcessingNPCs() {
        plugin.getLogger().info("加工NPCをリロードしています...");
        removeProcessingNPCs();
        spawnProcessingNPCs();
        plugin.getLogger().info("加工NPCのリロードが完了しました");
    }
    
    /**
     * 加工結果クラス
     */
    public static class ProcessingResult {
        private final boolean success;
        private final String message;
        private final int logsProcessed;
        private final int planksCreated;
        private final double feeCharged;
        
        public ProcessingResult(boolean success, String message, int logsProcessed, int planksCreated, double feeCharged) {
            this.success = success;
            this.message = message;
            this.logsProcessed = logsProcessed;
            this.planksCreated = planksCreated;
            this.feeCharged = feeCharged;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getLogsProcessed() { return logsProcessed; }
        public int getPlanksCreated() { return planksCreated; }
        public double getFeeCharged() { return feeCharged; }
    }
}
