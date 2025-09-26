package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.economy.ItemManager;
import org.tofu.tofunomics.npc.NPCManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BankGUI implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final ItemManager itemManager;
    
    private final Map<UUID, BankGUISession> activeSessions = new ConcurrentHashMap<>();
    
    public BankGUI(TofuNomics plugin, ConfigManager configManager, 
                   CurrencyConverter currencyConverter, ItemManager itemManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.itemManager = itemManager;
    }
    
    private static class BankGUISession {
        private final UUID playerId;
        private final UUID npcId;
        private final String npcName;
        private final Inventory inventory;
        private final long createdTime;
        
        public BankGUISession(UUID playerId, UUID npcId, String npcName, Inventory inventory) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.npcName = npcName;
            this.inventory = inventory;
            this.createdTime = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() { return playerId; }
        public UUID getNpcId() { return npcId; }
        public String getNpcName() { return npcName; }
        public Inventory getInventory() { return inventory; }
        public long getCreatedTime() { return createdTime; }
    }
    
    public void openBankGUI(Player player, NPCManager.NPCData npcData) {
        try {
            String title = "§6" + npcData.getName() + " - 銀行サービス";
            Inventory gui = Bukkit.createInventory(null, 27, title);
            
            setupBankGUIItems(gui, player);
            
            BankGUISession session = new BankGUISession(
                player.getUniqueId(), 
                npcData.getEntityId(), 
                npcData.getName(), 
                gui
            );
            
            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);
            
            plugin.getLogger().info("銀行GUIを開きました: " + player.getName() + " -> " + npcData.getName());
            
        } catch (Exception e) {
            plugin.getLogger().severe("銀行GUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.bank.gui_error"));
            e.printStackTrace();
        }
    }
    
    private void setupBankGUIItems(Inventory gui, Player player) {
        // 残高表示
        double balance = currencyConverter.getBalance(player.getUniqueId());
        String balanceText = currencyConverter.formatCurrency(balance);
        
        ItemStack balanceItem = createGUIItem(
            Material.GOLD_INGOT,
            "§6残高照会",
            Arrays.asList(
                "§f現在の残高: §a" + balanceText,
                "§7クリックして更新"
            )
        );
        gui.setItem(4, balanceItem);
        
        // 引き出しボタン
        ItemStack withdrawItem = createGUIItem(
            Material.HOPPER,
            "§c金塊を引き出し",
            Arrays.asList(
                "§7銀行から金塊を引き出します",
                "§7左クリック: §f100 " + configManager.getCurrencyName(),
                "§7右クリック: §f500 " + configManager.getCurrencyName(),
                "§7シフト+クリック: §f1000 " + configManager.getCurrencyName()
            )
        );
        gui.setItem(11, withdrawItem);
        
        // 預け入れボタン
        ItemStack depositItem = createGUIItem(
            Material.CHEST,
            "§a金塊を預け入れ",
            Arrays.asList(
                "§7手持ちの金塊を銀行に預けます",
                "§7左クリック: §f手持ち金塊を1個預入",
                "§7右クリック: §f手持ち金塊を10個預入",
                "§7シフト+クリック: §f手持ち金塊を全て預入"
            )
        );
        gui.setItem(13, depositItem);
        
        // 送金ボタン
        ItemStack payItem = createGUIItem(
            Material.PAPER,
            "§e送金サービス",
            Arrays.asList(
                "§7他のプレイヤーに送金します",
                "§c※ チャットで /pay <プレイヤー> <金額>",
                "§7を入力してください"
            )
        );
        gui.setItem(15, payItem);
        
        // 取引履歴ボタン
        ItemStack historyItem = createGUIItem(
            Material.BOOK,
            "§b取引履歴",
            Arrays.asList(
                "§7最近の取引履歴を表示",
                "§8※ 実装予定機能"
            )
        );
        gui.setItem(22, historyItem);
        
        // 閉じるボタン
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7GUIを閉じます")
        );
        gui.setItem(26, closeItem);
        
        // 装飾アイテム
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
        int[] decorationSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 14, 16, 17, 18, 19, 20, 21, 23, 24, 25};
        for (int slot : decorationSlots) {
            gui.setItem(slot, glassPane);
        }
    }
    
    private ItemStack createGUIItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        
        BankGUISession session = activeSessions.get(playerId);
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        try {
            handleBankGUIClick(player, session, event.getSlot(), event.getClick());
        } catch (Exception e) {
            plugin.getLogger().severe("銀行GUIクリック処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.bank.action_error"));
            e.printStackTrace();
        }
    }
    
    private void handleBankGUIClick(Player player, BankGUISession session, int slot, 
                                  org.bukkit.event.inventory.ClickType clickType) {
        switch (slot) {
            case 4: // 残高照会
                handleBalanceCheck(player, session);
                break;
                
            case 11: // 引き出し
                handleWithdraw(player, session, clickType);
                break;
                
            case 13: // 預け入れ
                handleDeposit(player, session, clickType);
                break;
                
            case 15: // 送金
                handlePayInfo(player);
                break;
                
            case 22: // 取引履歴
                handleTransactionHistory(player);
                break;
                
            case 26: // 閉じる
                player.closeInventory();
                break;
                
            default:
                // 他のスロットは無視
                break;
        }
    }
    
    private void handleBalanceCheck(Player player, BankGUISession session) {
        double balance = currencyConverter.getBalance(player.getUniqueId());
        String balanceText = currencyConverter.formatCurrency(balance);
        
        player.sendMessage(configManager.getMessage("economy.balance_self", 
            "amount", balanceText, 
            "currency", configManager.getCurrencyName()));
        
        // GUIアイテムも更新
        setupBankGUIItems(session.getInventory(), player);
    }
    
    private void handleWithdraw(Player player, BankGUISession session, 
                              org.bukkit.event.inventory.ClickType clickType) {
        double amount;
        
        switch (clickType) {
            case LEFT:
                amount = 100.0;
                break;
            case RIGHT:
                amount = 500.0;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                amount = 1000.0;
                break;
            default:
                return;
        }
        
        double balance = currencyConverter.getBalance(player.getUniqueId());
        if (balance < amount) {
            player.sendMessage(configManager.getMessage("insufficient_balance"));
            return;
        }
        
        double maxWithdraw = configManager.getMaxWithdrawAmount();
        if (amount > maxWithdraw) {
            player.sendMessage(configManager.getMessage("economy.exceed_max_withdraw", 
                "max_amount", currencyConverter.formatCurrency(maxWithdraw)));
            return;
        }
        
        // 引き出し処理
        if (currencyConverter.subtractBalance(player.getUniqueId(), amount)) {
            // 金インゴットではなく豆腐コイン（カスタム金塊）を作成
            ItemStack tofuCoins = itemManager.createGoldNugget((int) amount);
            player.getInventory().addItem(tofuCoins);
            
            String amountText = currencyConverter.formatCurrency(amount);
            player.sendMessage(configManager.getMessage("economy.withdraw_success", 
                "amount", amountText, 
                "currency", configManager.getCurrencyName()));
            
            // GUIを更新
            setupBankGUIItems(session.getInventory(), player);
        } else {
            player.sendMessage(configManager.getMessage("npc.bank.withdraw_failed"));
        }
    }
    
    private void handleDeposit(Player player, BankGUISession session, 
                             org.bukkit.event.inventory.ClickType clickType) {
        int amount;
        
        switch (clickType) {
            case LEFT:
                amount = 1;
                break;
            case RIGHT:
                amount = 10;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                amount = Integer.MAX_VALUE; // 全て
                break;
            default:
                return;
        }
        
        // 手持ちの豆腐コインをカウント
        int goldCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemManager.isAnyValidGoldNugget(item)) {
                goldCount += item.getAmount();
            }
        }
        
        if (goldCount == 0) {
            player.sendMessage(configManager.getMessage("npc.bank.no_tofu_coins"));
            return;
        }
        
        // 実際に預ける量を決定
        int depositAmount = Math.min(amount, goldCount);
        double maxDeposit = configManager.getMaxDepositAmount();
        if (depositAmount > maxDeposit) {
            depositAmount = (int) maxDeposit;
        }
        
        // 豆腐コインをインベントリから削除
        int remaining = depositAmount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            
            if (itemManager.isAnyValidGoldNugget(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    item.setAmount(0);
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }
        
        // 残高に追加
        currencyConverter.addBalance(player.getUniqueId(), depositAmount);
        
        String amountText = currencyConverter.formatCurrency(depositAmount);
        player.sendMessage(configManager.getMessage("economy.deposit_success", 
            "amount", amountText, 
            "currency", configManager.getCurrencyName()));
        
        // GUIを更新
        setupBankGUIItems(session.getInventory(), player);
    }
    
    private void handlePayInfo(Player player) {
        player.sendMessage("§6=== 送金サービス ===");
        player.sendMessage("§fコマンド: §a/pay <プレイヤー名> <金額>");
        player.sendMessage("§f例: §a/pay Steve 100");
        player.sendMessage("§7※ GUIを閉じてからコマンドを入力してください");
        player.closeInventory();
    }
    
    private void handleTransactionHistory(Player player) {
        player.sendMessage("§6=== 取引履歴 ===");
        player.sendMessage("§c※ この機能は実装予定です");
        player.sendMessage("§7将来的に取引履歴を確認できるようになります");
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        BankGUISession session = activeSessions.remove(playerId);
        if (session != null) {
            plugin.getLogger().info("銀行GUIを閉じました: " + player.getName());
        }
    }
    
    public void closeAllGUIs() {
        for (BankGUISession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }
    
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
}