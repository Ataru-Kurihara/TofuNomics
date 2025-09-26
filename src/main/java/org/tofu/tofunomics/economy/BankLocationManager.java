package org.tofu.tofunomics.economy;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BankLocationManager {
    
    private final ConfigManager configManager;
    
    public BankLocationManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public static class BankLocation {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String name;
        
        public BankLocation(String world, int x, int y, int z, String name) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }
        
        public String getWorld() {
            return world;
        }
        
        public int getX() {
            return x;
        }
        
        public int getY() {
            return y;
        }
        
        public int getZ() {
            return z;
        }
        
        public String getName() {
            return name;
        }
        
        public double getDistance(Location playerLocation) {
            if (!playerLocation.getWorld().getName().equals(world)) {
                return Double.MAX_VALUE;
            }
            
            return Math.sqrt(
                Math.pow(playerLocation.getX() - x, 2) +
                Math.pow(playerLocation.getY() - y, 2) +
                Math.pow(playerLocation.getZ() - z, 2)
            );
        }
        
        public boolean isWithinRange(Location playerLocation, double range) {
            return getDistance(playerLocation) <= range;
        }
    }
    
    public boolean isLocationRestrictionsEnabled() {
        return configManager.isLocationRestrictionsEnabled();
    }
    
    public int getAccessRange() {
        return configManager.getBankAccessRange();
    }
    
    public List<BankLocation> getBankLocations() {
        List<Map<?, ?>> bankList = configManager.getBankLocations();
        
        return bankList.stream()
            .map(this::parseBankLocation)
            .filter(location -> location != null)
            .collect(Collectors.toList());
    }
    
    public List<BankLocation> getAtmLocations() {
        List<Map<?, ?>> atmList = configManager.getAtmLocations();
        
        return atmList.stream()
            .map(this::parseBankLocation)
            .filter(location -> location != null)
            .collect(Collectors.toList());
    }
    
    private BankLocation parseBankLocation(Map<?, ?> map) {
        try {
            String world = (String) map.get("world");
            int x = (Integer) map.get("x");
            int y = (Integer) map.get("y");
            int z = (Integer) map.get("z");
            String name = (String) map.get("name");
            
            if (world == null || name == null) {
                return null;
            }
            
            return new BankLocation(world, x, y, z, name);
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean isPlayerNearBankOrAtm(Player player) {
        if (!isLocationRestrictionsEnabled()) {
            return true;
        }
        
        Location playerLocation = player.getLocation();
        int range = getAccessRange();
        
        // 銀行をチェック
        for (BankLocation bank : getBankLocations()) {
            if (bank.isWithinRange(playerLocation, range)) {
                return true;
            }
        }
        
        // ATMをチェック
        for (BankLocation atm : getAtmLocations()) {
            if (atm.isWithinRange(playerLocation, range)) {
                return true;
            }
        }
        
        return false;
    }
    
    public BankLocation getNearestLocation(Player player) {
        Location playerLocation = player.getLocation();
        BankLocation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        // 銀行から最寄りを検索
        for (BankLocation bank : getBankLocations()) {
            double distance = bank.getDistance(playerLocation);
            if (distance < nearestDistance) {
                nearest = bank;
                nearestDistance = distance;
            }
        }
        
        // ATMから最寄りを検索
        for (BankLocation atm : getAtmLocations()) {
            double distance = atm.getDistance(playerLocation);
            if (distance < nearestDistance) {
                nearest = atm;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }
    
    public String getLocationDeniedMessage(Player player) {
        if (!isLocationRestrictionsEnabled()) {
            return configManager.getMessage("economy.location_restrictions_disabled");
        }
        
        String baseMessage = configManager.getMessage("economy.bank_access_required");
        
        BankLocation nearest = getNearestLocation(player);
        if (nearest != null) {
            double distance = Math.round(nearest.getDistance(player.getLocation()));
            String locationInfo = configManager.getMessage("economy.nearest_location_info",
                "location_name", nearest.getName(),
                "distance", String.valueOf((int) distance));
            return baseMessage + "\n" + locationInfo;
        }
        
        return baseMessage;
    }
}