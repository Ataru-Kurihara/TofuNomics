package org.tofu.tofunomics.economy;

import org.bukkit.entity.Player;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.text.DecimalFormat;

public class CurrencyConverter {
    
    private final PlayerDAO playerDAO;
    private final ItemManager itemManager;
    private final org.tofu.tofunomics.config.ConfigManager configManager;
    private final int decimalPlaces;
    private final DecimalFormat formatter;
    
    public CurrencyConverter(PlayerDAO playerDAO, ItemManager itemManager, 
                           org.tofu.tofunomics.config.ConfigManager configManager, int decimalPlaces) {
        this.playerDAO = playerDAO;
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.decimalPlaces = Math.max(0, Math.min(2, decimalPlaces));
        
        StringBuilder pattern = new StringBuilder("#,##0");
        if (this.decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < this.decimalPlaces; i++) {
                pattern.append("0");
            }
        }
        this.formatter = new DecimalFormat(pattern.toString());
    }
    
    public String formatCurrency(double amount) {
        return formatter.format(amount);
    }
    
    public boolean depositGoldNuggets(Player player, int nuggetAmount) {
        if (nuggetAmount <= 0) {
            return false;
        }
        
        int availableNuggets = itemManager.countGoldNuggetsInInventory(player);
        if (availableNuggets < nuggetAmount) {
            return false;
        }
        
        if (!itemManager.removeGoldNuggetsFromInventory(player, nuggetAmount)) {
            return false;
        }
        
        double bankAmount = convertNuggetsToBalance(nuggetAmount);
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        if (tofuPlayer == null) {
            tofuPlayer = new org.tofu.tofunomics.models.Player();
            tofuPlayer.setUuid(player.getUniqueId().toString());
            tofuPlayer.setBalance(0.0); // 現金は0
            tofuPlayer.setBankBalance(bankAmount); // 銀行預金に追加
            return playerDAO.insertPlayer(tofuPlayer);
        } else {
            tofuPlayer.addBankBalance(bankAmount); // 銀行預金に追加
            return playerDAO.updatePlayerData(tofuPlayer);
        }
    }
    
    public boolean depositAllGoldNuggets(Player player) {
        int availableNuggets = itemManager.countGoldNuggetsInInventory(player);
        return availableNuggets > 0 && depositGoldNuggets(player, availableNuggets);
    }
    
    public enum WithdrawResult {
        SUCCESS,
        INSUFFICIENT_BALANCE,
        INSUFFICIENT_INVENTORY_SPACE,
        INVALID_AMOUNT,
        DATABASE_ERROR
    }
    
    public WithdrawResult withdrawToGoldNuggets(Player player, double amount) {
        if (amount <= 0) {
            return WithdrawResult.INVALID_AMOUNT;
        }
        
        int nuggetAmount = convertBalanceToNuggets(amount);
        double exactAmount = convertNuggetsToBalance(nuggetAmount);
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        if (tofuPlayer == null || tofuPlayer.getBankBalance() < exactAmount) {
            return WithdrawResult.INSUFFICIENT_BALANCE;
        }
        
        if (!itemManager.hasInventorySpace(player, nuggetAmount)) {
            return WithdrawResult.INSUFFICIENT_INVENTORY_SPACE;
        }
        
        tofuPlayer.removeBankBalance(exactAmount); // 銀行預金から引き出し
        if (!playerDAO.updatePlayerData(tofuPlayer)) {
            return WithdrawResult.DATABASE_ERROR;
        }
        
        if (!itemManager.addGoldNuggetsToInventory(player, nuggetAmount)) {
            tofuPlayer.addBankBalance(exactAmount); // ロールバック
            playerDAO.updatePlayerData(tofuPlayer);
            return WithdrawResult.INSUFFICIENT_INVENTORY_SPACE;
        }
        
        return WithdrawResult.SUCCESS;
    }
    
    public int convertBalanceToNuggets(double balance) {
        if (balance <= 0) {
            return 0;
        }
        double coinValue = configManager.getCoinValue();
        return (int) Math.floor(balance / coinValue);
    }
    
    public double convertNuggetsToBalance(int nuggets) {
        if (nuggets <= 0) {
            return 0.0;
        }
        double coinValue = configManager.getCoinValue();
        return nuggets * coinValue;
    }

    
    /**
     * 現在の通貨価値を取得
     */
    public double getCurrentCoinValue() {
        return configManager.getCoinValue();
    }
    
    /**
     * 通貨価値の説明文を取得
     */
    public String getCoinValueDescription() {
        double coinValue = getCurrentCoinValue();
        return String.format("1コイン = $%.1f", coinValue);
    }
    
    public double roundToDecimalPlaces(double amount) {
        if (decimalPlaces == 0) {
            return Math.floor(amount);
        }
        
        double multiplier = Math.pow(10, decimalPlaces);
        return Math.round(amount * multiplier) / multiplier;
    }
    
    public boolean canAfford(Player player, double amount) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null && tofuPlayer.getBankBalance() >= amount;
    }
    
    public double getBalance(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    // 現金（金塊）残高を取得
    public double getCashBalance(Player player) {
        int goldNuggets = itemManager.countGoldNuggetsInInventory(player);
        return convertNuggetsToBalance(goldNuggets);
    }
    
    // 銀行預金残高を取得
    public double getBankBalance(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    // 総資産（現金+預金）を取得
    public double getTotalBalance(Player player) {
        return getCashBalance(player) + getBankBalance(player);
    }
    
    public boolean transfer(Player fromPlayer, Player toPlayer, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        String fromUuid = fromPlayer.getUniqueId().toString();
        String toUuid = toPlayer.getUniqueId().toString();
        
        return playerDAO.transferBalance(fromUuid, toUuid, amount);
    }
    
    public boolean hasEnoughGoldNuggets(Player player, int requiredAmount) {
        return itemManager.countGoldNuggetsInInventory(player) >= requiredAmount;
    }
    
    public int getMaxWithdrawableNuggets(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        if (tofuPlayer == null) {
            return 0;
        }
        return convertBalanceToNuggets(tofuPlayer.getBankBalance());
    }
    
    // UUIDベースのメソッド（NPCシステム用）
    public double getBalance(java.util.UUID uuid) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    public boolean addBalance(java.util.UUID uuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        if (tofuPlayer == null) {
            return false;
        }
        
        double newBalance = tofuPlayer.getBankBalance() + amount;
        tofuPlayer.setBankBalance(newBalance);
        return playerDAO.updatePlayerData(tofuPlayer);
    }
    
    public boolean subtractBalance(java.util.UUID uuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        if (tofuPlayer == null || tofuPlayer.getBankBalance() < amount) {
            return false;
        }
        
        double newBalance = tofuPlayer.getBankBalance() - amount;
        tofuPlayer.setBankBalance(newBalance);
        return playerDAO.updatePlayerData(tofuPlayer);
    }
    
    // 所持金での支払い処理（金塊をインベントリから削除）
    public boolean payWithCash(Player player, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        int requiredNuggets = convertBalanceToNuggets(amount);
        double exactAmount = convertNuggetsToBalance(requiredNuggets);
        
        // 必要な金塊数をチェック
        if (!hasEnoughGoldNuggets(player, requiredNuggets)) {
            return false;
        }
        
        // 金塊をインベントリから削除
        return itemManager.removeGoldNuggetsFromInventory(player, requiredNuggets);
    }
    
    // 所持金での受取り処理（金塊をインベントリに追加）
    public boolean receiveCash(Player player, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        int nuggetAmount = convertBalanceToNuggets(amount);
        
        // インベントリ容量チェック
        if (!itemManager.hasInventorySpace(player, nuggetAmount)) {
            return false;
        }
        
        // 金塊をインベントリに追加
        return itemManager.addGoldNuggetsToInventory(player, nuggetAmount);
    }
    
    // 所持金で支払い可能かチェック
    public boolean canAffordWithCash(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        
        int requiredNuggets = convertBalanceToNuggets(amount);
        return hasEnoughGoldNuggets(player, requiredNuggets);
    }
}