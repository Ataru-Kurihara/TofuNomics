package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.BankLocationManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.npc.gui.BankGUI;

import java.util.Collection;
import java.util.UUID;

public class BankNPCManager {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final BankLocationManager bankLocationManager;
    private final CurrencyConverter currencyConverter;
    private final BankGUI bankGUI;
    
    public BankNPCManager(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager, 
                         BankLocationManager bankLocationManager, CurrencyConverter currencyConverter,
                         BankGUI bankGUI) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.bankLocationManager = bankLocationManager;
        this.currencyConverter = currencyConverter;
        this.bankGUI = bankGUI;
    }
    
    public void initializeBankNPCs() {
        try {
            if (!configManager.isBankNPCEnabled()) {
                plugin.getLogger().info("銀行NPCシステムは無効化されています");
                return;
            }
            
            spawnBankNPCs();
            plugin.getLogger().info("銀行NPCシステムを初期化しました");
        } catch (Exception e) {
            plugin.getLogger().severe("銀行NPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void spawnBankNPCs() {
        plugin.getLogger().info("銀行NPCをスポーンしています...");
        
        // 既にスポーンした座標を記録するSet（重複防止用）
        java.util.Set<String> spawnedLocations = new java.util.HashSet<>();
        
        // config.ymlから銀行NPCの位置情報を取得してスポーン
        java.util.List<java.util.Map<?, ?>> bankNPCLocations = configManager.getBankNPCLocations();
        
        if (!bankNPCLocations.isEmpty()) {
            plugin.getLogger().info("config.ymlから" + bankNPCLocations.size() + "個の銀行NPC設定を読み込みました");
            
            for (java.util.Map<?, ?> npcData : bankNPCLocations) {
                try {
                    String worldName = (String) npcData.get("world");
                    int x = ((Number) npcData.get("x")).intValue();
                    int y = ((Number) npcData.get("y")).intValue();
                    int z = ((Number) npcData.get("z")).intValue();
                    String npcName = (String) npcData.get("name");
                    String npcType = npcData.get("type") != null ? (String) npcData.get("type") : "bank";
                    
                    // 座標をキーとして記録
                    String locationKey = worldName + "," + x + "," + y + "," + z;
                    spawnedLocations.add(locationKey);
                    
                    // yaw/pitchをconfigから取得（デフォルト値: 0.0f）
                    float yaw = 0.0f;
                    float pitch = 0.0f;
                    
                    if (npcData.containsKey("yaw") && npcData.get("yaw") != null) {
                        yaw = ((Number) npcData.get("yaw")).floatValue();
                    }
                    
                    if (npcData.containsKey("pitch") && npcData.get("pitch") != null) {
                        pitch = ((Number) npcData.get("pitch")).floatValue();
                    }
                    
                    Location npcLocation = new Location(
                        plugin.getServer().getWorld(worldName),
                        x + 0.5,
                        y,
                        z + 0.5,
                        yaw,
                        pitch
                    );
                    
                    Villager bankNPC = npcManager.createNPC(npcLocation, "banker", npcName);
                    
                    if (bankNPC != null) {
                        setupBankNPC(bankNPC);
                        plugin.getLogger().info("銀行NPCを配置しました: " + npcName + " (" + npcType + ") at " + 
                            worldName + " [" + x + ", " + y + ", " + z + "]");
                    } else {
                        plugin.getLogger().warning("銀行NPCの生成に失敗しました: " + npcName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("銀行NPCのスポーン中にエラー: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            plugin.getLogger().info("config.ymlに銀行NPC設定が見つかりません");
        }
        
        // 既存のBankLocationManagerベースの処理も保持（後方互換性）
        for (BankLocationManager.BankLocation bankLocation : bankLocationManager.getBankLocations()) {
            String worldName = bankLocation.getWorld();
            int x = (int) bankLocation.getX();
            int y = (int) bankLocation.getY();
            int z = (int) bankLocation.getZ();
            String locationKey = worldName + "," + x + "," + y + "," + z;
            
            // 重複チェック：既にconfig.ymlから同じ座標にスポーンしている場合はスキップ
            if (spawnedLocations.contains(locationKey)) {
                plugin.getLogger().warning("銀行NPC重複検出: " + bankLocation.getName() + " (" + locationKey + ") - config.ymlの設定を優先してスキップします");
                continue;
            }
            
            String npcName = configManager.getBankNPCName(bankLocation.getName());
            
            // 削除フラグをチェック

            
            Location npcLocation = new Location(
                plugin.getServer().getWorld(bankLocation.getWorld()),
                bankLocation.getX() + 0.5,
                bankLocation.getY(),
                bankLocation.getZ() + 0.5
            );
            
            Villager bankNPC = npcManager.createNPC(npcLocation, "banker", npcName);
            
            if (bankNPC != null) {
                setupBankNPC(bankNPC);
                spawnedLocations.add(locationKey);
                plugin.getLogger().info("銀行NPCを配置しました: " + npcName + " at " + bankLocation.getName());
            }
        }
        
        // ATM場所にもNPCを配置
        for (BankLocationManager.BankLocation atmLocation : bankLocationManager.getAtmLocations()) {
            String worldName = atmLocation.getWorld();
            int x = (int) atmLocation.getX();
            int y = (int) atmLocation.getY();
            int z = (int) atmLocation.getZ();
            String locationKey = worldName + "," + x + "," + y + "," + z;
            
            // 重複チェック：既にconfig.ymlから同じ座標にスポーンしている場合はスキップ
            if (spawnedLocations.contains(locationKey)) {
                plugin.getLogger().warning("ATM NPC重複検出: " + atmLocation.getName() + " (" + locationKey + ") - config.ymlの設定を優先してスキップします");
                continue;
            }
            
            String npcName = configManager.getATMNPCName(atmLocation.getName());
            
            // 削除フラグをチェック

            
            Location npcLocation = new Location(
                plugin.getServer().getWorld(atmLocation.getWorld()),
                atmLocation.getX() + 0.5,
                atmLocation.getY(),
                atmLocation.getZ() + 0.5
            );
            
            Villager atmNPC = npcManager.createNPC(npcLocation, "banker", npcName);
            
            if (atmNPC != null) {
                setupBankNPC(atmNPC);
                spawnedLocations.add(locationKey);
                plugin.getLogger().info("ATM NPCを配置しました: " + npcName + " at " + atmLocation.getName());
            }
        }
    }
    
    private void setupBankNPC(Villager npc) {
        // NPCの外見設定
        npc.setProfession(Villager.Profession.LIBRARIAN);
        npc.setVillagerType(Villager.Type.PLAINS);
        npc.setAdult();
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setCanPickupItems(false);
        
        // NPCのカスタム名を設定
        npc.setCustomNameVisible(true);
        
        // タグを付けて銀行NPCとして識別
        npc.addScoreboardTag("tofu_bank_npc");
    }
    
    public boolean handleBankNPCInteraction(Player player, UUID npcId) {
        NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
        if (npcData == null || !npcData.getNpcType().equals("banker")) {
            return false;
        }
        
        // 銀行操作距離の確認
        if (npcData.getLocation().distance(player.getLocation()) > configManager.getBankAccessRange()) {
            player.sendMessage(configManager.getMessage("npc.bank.too_far"));
            return false;
        }
        
        // 銀行NPCメニューを開く
        openBankMenu(player, npcData);
        return true;
    }
    
    private void openBankMenu(Player player, NPCManager.NPCData npcData) {
        // ウェルカムメッセージを送信
        player.sendMessage(configManager.getMessage("npc.bank.welcome", "player", player.getName()));
        
        // 遅延してからGUIを開く
        int delayTicks = configManager.getNPCGUIDelayTicks();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // プレイヤーがまだオンラインで、NPCの近くにいるかチェック
            if (player.isOnline() && isPlayerNearNPC(player, npcData)) {
                // 銀行GUIを開く
                bankGUI.openBankGUI(player, npcData);
            } else {
                plugin.getLogger().info("プレイヤー " + player.getName() + " がNPCから離れたため、GUIを開かませんでした");
            }
        }, delayTicks);
    }
    
    public void removeBankNPCs() {
        plugin.getLogger().info("全ての銀行NPCを削除しています...");
        
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        int removedCount = 0;
        
        for (NPCManager.NPCData npcData : allNPCs) {
            if (npcData.getNpcType().equals("banker")) {
                npcManager.removeNPC(npcData.getEntityId());
                removedCount++;
            }
        }
        
        plugin.getLogger().info("全ての銀行NPCを削除しました: " + removedCount + "体");
    }
    
    public void reloadBankNPCs() {
        plugin.getLogger().info("銀行NPCをリロードしています...");
        removeBankNPCs();
        spawnBankNPCs();
        plugin.getLogger().info("銀行NPCのリロードが完了しました");
    }
    
    public String getBankNPCInfo(UUID npcId) {
        NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
        if (npcData == null || !npcData.getNpcType().equals("banker")) {
            return null;
        }
        
        Location loc = npcData.getLocation();
        return String.format("銀行NPC: %s (%s) at %s [%.1f, %.1f, %.1f]",
            npcData.getName(),
            npcData.getNpcType(),
            loc.getWorld().getName(),
            loc.getX(),
            loc.getY(),
            loc.getZ()
        );
    }
    
    /**
     * プレイヤーがNPCの近くにいるかどうかをチェック
     */
    private boolean isPlayerNearNPC(Player player, NPCManager.NPCData npcData) {
        try {
            // 距離をチェック（設定可能な範囲内）
            double distance = player.getLocation().distance(npcData.getLocation());
            int accessRange = configManager.getBankAccessRange();
            
            return distance <= accessRange;
            
        } catch (Exception e) {
            plugin.getLogger().warning("プレイヤーとNPCの距離チェック中にエラーが発生: " + e.getMessage());
            return false; // エラーの場合は安全のためfalseを返す
        }
    }
}