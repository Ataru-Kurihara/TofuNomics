package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.TofuNomics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NPCManager {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final Map<UUID, NPCData> npcs = new ConcurrentHashMap<>();
    
    public NPCManager(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    public static class NPCData {
        private final UUID entityId;
        private final String npcType;
        private final String name;
        private final Location location;
        private final Villager entity;
        
        public NPCData(UUID entityId, String npcType, String name, Location location, Villager entity) {
            this.entityId = entityId;
            this.npcType = npcType;
            this.name = name;
            this.location = location;
            this.entity = entity;
        }
        
        public UUID getEntityId() { return entityId; }
        public String getNpcType() { return npcType; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public Villager getEntity() { return entity; }
    }
    
    public Villager createNPC(Location location, String npcType, String name) {
        if (location.getWorld() == null) {
            plugin.getLogger().warning("NPCの生成に失敗: ワールドが無効です - " + location);
            return null;
        }
        
        try {
            Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            
            // NPC設定
            villager.setCustomName(name);
            villager.setCustomNameVisible(true);
            villager.setProfession(getNPCProfession(npcType));
            villager.setVillagerType(Villager.Type.PLAINS);
            villager.setAI(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setRemoveWhenFarAway(false);
            
            // システムNPC識別用のメタデータを設定
            villager.setMetadata("tofunomics_system_npc", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            villager.setMetadata("tofunomics_npc_type", new org.bukkit.metadata.FixedMetadataValue(plugin, npcType));
            villager.setMetadata("tofunomics_creation_time", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
            
            // 取引を無効化
            villager.setRecipes(new ArrayList<>());
            
            // NPCデータを保存
            NPCData npcData = new NPCData(villager.getUniqueId(), npcType, name, location, villager);
            npcs.put(villager.getUniqueId(), npcData);
            
            plugin.getLogger().info("NPCを生成しました: " + name + " (" + npcType + ") at " + 
                location.getX() + ", " + location.getY() + ", " + location.getZ());
            
            return villager;
        } catch (Exception e) {
            plugin.getLogger().severe("NPC生成中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private Villager.Profession getNPCProfession(String npcType) {
        switch (npcType.toLowerCase()) {
            case "banker":
                return Villager.Profession.LIBRARIAN;
            case "trader":
                return Villager.Profession.WEAPONSMITH;
            case "merchant":
                return Villager.Profession.TOOLSMITH;
            case "food_merchant":
                return Villager.Profession.BUTCHER;
            default:
                return Villager.Profession.NITWIT;
        }
    }
    
    public boolean removeNPC(UUID npcId) {
        NPCData npcData = npcs.get(npcId);
        if (npcData != null) {
            try {
                npcData.getEntity().remove();
                npcs.remove(npcId);
                plugin.getLogger().info("NPCを削除しました: " + npcData.getName());
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("NPC削除中にエラーが発生しました: " + e.getMessage());
            }
        }
        return false;
    }
    
    public NPCData getNPCData(UUID entityId) {
        return npcs.get(entityId);
    }
    
    public Collection<NPCData> getAllNPCs() {
        return npcs.values();
    }
    
    public Collection<NPCData> getNPCsByType(String npcType) {
        return npcs.values().stream()
            .filter(npcData -> npcData.getNpcType().equals(npcType))
            .collect(ArrayList::new, (list, npc) -> list.add(npc), ArrayList::addAll);
    }
    
    public boolean isNPCEntity(UUID entityId) {
        return npcs.containsKey(entityId);
    }
    
    public void removeAllNPCs() {
        for (NPCData npcData : npcs.values()) {
            try {
                npcData.getEntity().remove();
            } catch (Exception e) {
                plugin.getLogger().warning("NPC削除中にエラーが発生しました: " + e.getMessage());
            }
        }
        npcs.clear();
        plugin.getLogger().info("全てのNPCを削除しました");
    }
    
    /**
     * ワールド内の既存システムNPCを削除する
     * プラグイン再起動時の重複NPC問題を解決
     */
    public void removeExistingSystemNPCs() {
        int removedCount = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            try {
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                    if (isSystemNPC(entity)) {
                        plugin.getLogger().info("既存システムNPCを削除: " + entity.getCustomName() + " at " + 
                            entity.getLocation().getX() + ", " + entity.getLocation().getY() + ", " + entity.getLocation().getZ());
                        entity.remove();
                        removedCount++;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("ワールド " + world.getName() + " でのNPC削除中にエラー: " + e.getMessage());
            }
        }
        
        // 内部データもクリア
        npcs.clear();
        
        plugin.getLogger().info("既存システムNPCを " + removedCount + " 個削除しました");
    }
    
    /**
     * エンティティがシステム作成のNPCかどうかを判定
     */
    public boolean isSystemNPC(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof org.bukkit.entity.Villager)) {
            return false;
        }
        
        // メタデータでの判定
        if (entity.hasMetadata("tofunomics_system_npc")) {
            return entity.getMetadata("tofunomics_system_npc").get(0).asBoolean();
        }
        
        // カスタムネームでの判定（フォールバック）
        String customName = entity.getCustomName();
        if (customName != null) {
            return customName.contains("銀行員") || 
                   customName.contains("中央市場") || 
                   customName.contains("鉱夫取引所") || 
                   customName.contains("木材市場") || 
                   customName.contains("農産物市場") || 
                   customName.contains("漁港") ||
                   customName.contains("ATM");
        }
        
        return false;
    }
    
    public void spawnConfiguredNPCs() {
        try {
            // 銀行NPCの生成
            List<Map<?, ?>> bankNPCs = configManager.getBankNPCs();
            for (Map<?, ?> npcConfig : bankNPCs) {
                spawnNPCFromConfig(npcConfig, "banker");
            }
            
            // 取引NPCの生成
            List<Map<?, ?>> tradeNPCs = configManager.getTradingPostConfigs();
            for (Map<?, ?> npcConfig : tradeNPCs) {
                spawnNPCFromConfig(npcConfig, "trader");
            }
            
            plugin.getLogger().info("設定ファイルからNPCを生成しました");
        } catch (Exception e) {
            plugin.getLogger().severe("設定ファイルからのNPC生成中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void spawnNPCFromConfig(Map<?, ?> npcConfig, String npcType) {
        try {
            String world = (String) npcConfig.get("world");
            int x = (Integer) npcConfig.get("x");
            int y = (Integer) npcConfig.get("y");
            int z = (Integer) npcConfig.get("z");
            String name = (String) npcConfig.get("name");
            
            if (world == null || name == null) {
                plugin.getLogger().warning("NPC設定が不完全です: " + npcConfig);
                return;
            }
            
            // 削除フラグをチェック
            Object deletedObj = npcConfig.get("deleted");
            boolean isDeleted = deletedObj != null && (Boolean) deletedObj;
            
            if (isDeleted) {
                plugin.getLogger().info("削除済みNPCのためスポーンをスキップ: " + name + " (" + npcType + ")");
                return;
            }
            
            Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5);
            createNPC(location, npcType, name);
            
        } catch (Exception e) {
            plugin.getLogger().warning("NPC生成中にエラーが発生しました: " + e.getMessage());
        }
    }
    
    public double getDistanceToNearestNPC(Player player, String npcType) {
        Location playerLocation = player.getLocation();
        double minDistance = Double.MAX_VALUE;
        
        for (NPCData npcData : getNPCsByType(npcType)) {
            Location npcLocation = npcData.getLocation();
            if (npcLocation.getWorld().equals(playerLocation.getWorld())) {
                double distance = playerLocation.distance(npcLocation);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }
        
        return minDistance;
    }
    
    public NPCData getNearestNPC(Player player, String npcType) {
        Location playerLocation = player.getLocation();
        NPCData nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (NPCData npcData : getNPCsByType(npcType)) {
            Location npcLocation = npcData.getLocation();
            if (npcLocation.getWorld().equals(playerLocation.getWorld())) {
                double distance = playerLocation.distance(npcLocation);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = npcData;
                }
            }
        }
        
        return nearest;
    }
}