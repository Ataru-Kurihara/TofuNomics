package org.tofu.tofunomics.trade;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.PlayerTradeHistory;
import org.tofu.tofunomics.models.TradeChest;
import org.tofu.tofunomics.trade.TradePriceManager.TradeResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * チェストイベント監視システム
 */
public class TradeChestListener implements Listener {
    
    private final TradeChestManager tradeChestManager;
    private final TradePriceManager tradePriceManager;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    
    // プレイヤーが開いている取引チェストの追跡
    private final Map<String, TradeChestSession> activeTradesSessions;
    
    public TradeChestListener(TradeChestManager tradeChestManager, TradePriceManager tradePriceManager,
                             ConfigManager configManager, PlayerDAO playerDAO, JobManager jobManager) {
        this.tradeChestManager = tradeChestManager;
        this.tradePriceManager = tradePriceManager;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.activeTradesSessions = new HashMap<>();
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.CHEST) return;
        
        Player player = (Player) event.getPlayer();
        Location chestLocation = event.getInventory().getLocation();
        
        if (chestLocation == null) return;
        
        TradeChest tradeChest = tradeChestManager.getTradeChest(chestLocation);
        if (tradeChest == null) return;
        
        // 取引セッション開始
        TradeChestSession session = new TradeChestSession(player, tradeChest, 
                                                         captureInventoryContents(event.getInventory()));
        activeTradesSessions.put(player.getUniqueId().toString(), session);
        
        // プレイヤーに取引チェスト情報を表示
        String jobDisplayName = configManager.getJobDisplayName(tradeChest.getJobType());
        player.sendMessage(ChatColor.GOLD + "=== " + jobDisplayName + " 取引所 ===");
        player.sendMessage(ChatColor.YELLOW + "アイテムをチェストに入れると自動で買取します。");
        player.sendMessage(ChatColor.GRAY + "チェストを閉じると取引が実行されます。");
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.CHEST) return;
        
        Player player = (Player) event.getPlayer();
        String playerUUID = player.getUniqueId().toString();
        
        TradeChestSession session = activeTradesSessions.remove(playerUUID);
        if (session == null) return;
        
        // 取引処理実行
        processTrade(player, session, event.getInventory());
    }
    
    /**
     * 取引処理を実行
     */
    private void processTrade(Player player, TradeChestSession session, Inventory currentInventory) {
        TradeChest tradeChest = session.getTradeChest();
        Map<Material, Integer> initialContents = session.getInitialContents();
        Map<Material, Integer> currentContents = captureInventoryContents(currentInventory);
        
        // 追加されたアイテムを計算
        Map<Material, Integer> addedItems = calculateAddedItems(initialContents, currentContents);
        
        if (addedItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "取引するアイテムがありませんでした。");
            return;
        }
        
        // 各アイテムの取引を処理
        List<TradeTransaction> transactions = new ArrayList<>();
        double totalEarnings = 0.0;
        
        for (Map.Entry<Material, Integer> entry : addedItems.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            
            TradeResult result = tradePriceManager.calculateTradePrice(player, tradeChest, material, amount);
            if (result.isSuccess()) {
                TradeTransaction transaction = new TradeTransaction(material, amount, result);
                transactions.add(transaction);
                totalEarnings += result.getTotalPrice();
                
                // チェストからアイテムを削除
                removeItemFromInventory(currentInventory, material, amount);
            }
        }
        
        if (transactions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "買取できるアイテムがありませんでした。");
            return;
        }
        
        // プレイヤーに金額を付与
        if (totalEarnings > 0) {
            try {
                org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
                if (tofuPlayer != null) {
                    tofuPlayer.setBalance(tofuPlayer.getBalance() + totalEarnings);
                    playerDAO.updatePlayer(tofuPlayer);
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "残高の更新に失敗しました: " + e.getMessage());
                return;
            }
        }
        
        // 取引履歴を保存
        saveTradeHistory(player, tradeChest, transactions);
        
        // 取引結果をプレイヤーに表示
        displayTradeResult(player, transactions, totalEarnings);
    }
    
    /**
     * 追加されたアイテムを計算
     */
    private Map<Material, Integer> calculateAddedItems(Map<Material, Integer> initial, 
                                                      Map<Material, Integer> current) {
        Map<Material, Integer> added = new HashMap<>();
        
        for (Map.Entry<Material, Integer> entry : current.entrySet()) {
            Material material = entry.getKey();
            int currentAmount = entry.getValue();
            int initialAmount = initial.getOrDefault(material, 0);
            
            if (currentAmount > initialAmount) {
                added.put(material, currentAmount - initialAmount);
            }
        }
        
        return added;
    }
    
    /**
     * インベントリの内容を取得
     */
    private Map<Material, Integer> captureInventoryContents(Inventory inventory) {
        Map<Material, Integer> contents = new HashMap<>();
        
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                Material material = item.getType();
                int amount = contents.getOrDefault(material, 0) + item.getAmount();
                contents.put(material, amount);
            }
        }
        
        return contents;
    }
    
    /**
     * インベントリから指定アイテムを削除
     */
    private void removeItemFromInventory(Inventory inventory, Material material, int amountToRemove) {
        int remaining = amountToRemove;
        
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    inventory.setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }
    }
    
    /**
     * 取引履歴を保存
     */
    private void saveTradeHistory(Player player, TradeChest tradeChest, List<TradeTransaction> transactions) {
        String playerUUID = player.getUniqueId().toString();
        
        for (TradeTransaction transaction : transactions) {
            PlayerJob playerJob = jobManager.getPlayerJob(player, tradeChest.getJobType());
            
            PlayerTradeHistory history = new PlayerTradeHistory(
                playerUUID,
                tradeChest.getId(),
                transaction.getMaterial().name(),
                transaction.getAmount(),
                transaction.getResult().getBasePrice(),
                tradeChest.getJobType(),
                playerJob != null ? playerJob.getLevel() : 1
            );
            
            history.setJobBonus(transaction.getResult().getJobBonus());
            tradeChestManager.saveTradeHistory(history);
        }
    }
    
    /**
     * 取引結果を表示
     */
    private void displayTradeResult(Player player, List<TradeTransaction> transactions, double totalEarnings) {
        player.sendMessage(ChatColor.GOLD + "=== 取引完了 ===");
        
        for (TradeTransaction transaction : transactions) {
            String itemName = getItemDisplayName(transaction.getMaterial());
            String message = String.format("%s%s x%d §f→ §a%.2f金塊", 
                ChatColor.YELLOW, itemName, transaction.getAmount(), 
                transaction.getResult().getTotalPrice());
            
            if (transaction.getResult().getJobBonus() > 0) {
                message += String.format(" §7(ボーナス: +%.2f)", transaction.getResult().getJobBonus());
            }
            
            player.sendMessage(message);
        }
        
        player.sendMessage(String.format("%s合計収益: §a%.2f金塊", ChatColor.GOLD, totalEarnings));
        player.sendMessage(ChatColor.GRAY + "残高に追加されました。");
    }
    
    /**
     * アイテム表示名を取得
     */
    private String getItemDisplayName(Material material) {
        // 簡略実装
        return material.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * 取引セッションクラス
     */
    private static class TradeChestSession {
        private final Player player;
        private final TradeChest tradeChest;
        private final Map<Material, Integer> initialContents;
        
        public TradeChestSession(Player player, TradeChest tradeChest, Map<Material, Integer> initialContents) {
            this.player = player;
            this.tradeChest = tradeChest;
            this.initialContents = initialContents;
        }
        
        public Player getPlayer() { return player; }
        public TradeChest getTradeChest() { return tradeChest; }
        public Map<Material, Integer> getInitialContents() { return initialContents; }
    }
    
    /**
     * 取引トランザクションクラス
     */
    private static class TradeTransaction {
        private final Material material;
        private final int amount;
        private final TradeResult result;
        
        public TradeTransaction(Material material, int amount, TradeResult result) {
            this.material = material;
            this.amount = amount;
            this.result = result;
        }
        
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public TradeResult getResult() { return result; }
    }
}