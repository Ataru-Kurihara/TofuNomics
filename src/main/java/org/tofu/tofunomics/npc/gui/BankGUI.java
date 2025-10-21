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
                "§7左クリック: §f10 " + configManager.getCurrencyName(),
                "§7右クリック: §f100 " + configManager.getCurrencyName(),
                "§7シフト左クリック: §f500 " + configManager.getCurrencyName(),
                "§7シフト右クリック: §f全額引き出し"
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
        
        // 換金ボタン（TofuCoin → TofuGold）
        int nuggetsPerIngot = itemManager.getNuggetsPerIngot();
        ItemStack coinToGoldItem = createGUIItem(
            Material.GOLD_NUGGET,
            "§6TofuCoin → TofuGold",
            Arrays.asList(
                "§7TofuCoinをTofuGoldに換金します",
                "§e" + nuggetsPerIngot + "コイン §7→ §61金貨",
                "§7クリックして換金"
            )
        );
        gui.setItem(19, coinToGoldItem);
        
        // 換金ボタン（TofuGold → TofuCoin）
        ItemStack goldToCoinItem = createGUIItem(
            Material.GOLD_INGOT,
            "§6TofuGold → TofuCoin",
            Arrays.asList(
                "§7TofuGoldをTofuCoinに換金します",
                "§61金貨 §7→ §e" + nuggetsPerIngot + "コイン",
                "§7クリックして換金"
            )
        );
        gui.setItem(21, goldToCoinItem);
        
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
        int[] decorationSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 14, 16, 17, 18, 20, 23, 24, 25};
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
                
            case 19: // TofuCoin → TofuGold 換金
                handleCoinToGoldConversion(player);
                break;
                
            case 21: // TofuGold → TofuCoin 換金
                handleGoldToCoinConversion(player);
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
        double requestedAmount;
        boolean withdrawAll = false;
        
        switch (clickType) {
            case LEFT:
                requestedAmount = 10.0;
                break;
            case RIGHT:
                requestedAmount = 100.0;
                break;
            case SHIFT_LEFT:
                requestedAmount = 500.0;
                break;
            case SHIFT_RIGHT:
                // 全額引き出し
                withdrawAll = true;
                requestedAmount = 0.0;
                break;
            default:
                return;
        }
        
        double balance = currencyConverter.getBalance(player.getUniqueId());
        
        if (balance <= 0) {
            player.sendMessage(configManager.getMessage("insufficient_balance"));
            return;
        }
        
        // 全額引き出しまたは残高が引き出し額より少ない場合は残高分を引き出し
        double amount;
        if (withdrawAll || balance < requestedAmount) {
            amount = balance;
        } else {
            amount = requestedAmount;
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

    /**
     * TofuCoin → TofuGold 換金処理
     */
    private void handleCoinToGoldConversion(Player player) {
        int nuggetsPerIngot = itemManager.getNuggetsPerIngot();
        
        // インベントリ内の通貨版金塊を数える
        int coinCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemManager.isValidGoldNugget(item)) {
                coinCount += item.getAmount();
            }
        }
        
        // 必要枚数があるかチェック
        if (coinCount < nuggetsPerIngot) {
            player.sendMessage("§c" + nuggetsPerIngot + "枚以上のTofuCoinが必要です。(現在: " + coinCount + "枚)");
            return;
        }
        
        // 金塊を削除
        int remainingToRemove = nuggetsPerIngot;
        for (int i = 0; i < player.getInventory().getSize() && remainingToRemove > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && itemManager.isValidGoldNugget(item)) {
                int removeAmount = Math.min(item.getAmount(), remainingToRemove);
                if (item.getAmount() <= removeAmount) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - removeAmount);
                }
                remainingToRemove -= removeAmount;
            }
        }
        
        // 金インゴットを追加
        ItemStack goldIngot = itemManager.createCurrencyGoldIngot(1);
        player.getInventory().addItem(goldIngot);
        
        player.sendMessage("§a" + nuggetsPerIngot + "枚のTofuCoinを1枚のTofuGoldに換金しました！");
    }
    
    /**
     * TofuGold → TofuCoin 換金処理
     */
    private void handleGoldToCoinConversion(Player player) {
        int nuggetsPerIngot = itemManager.getNuggetsPerIngot();
        
        // インベントリ内の通貨版金インゴットを数える
        int goldCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemManager.isValidCurrencyGoldIngot(item)) {
                goldCount += item.getAmount();
            }
        }
        
        // 1枚以上あるかチェック
        if (goldCount < 1) {
            player.sendMessage("§cTofuGoldが必要です。");
            return;
        }
        
        // 金インゴットを削除
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && itemManager.isValidCurrencyGoldIngot(item)) {
                if (item.getAmount() <= 1) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                break;
            }
        }
        
        // 金塊を追加
        ItemStack goldNuggets = itemManager.createGoldNugget(nuggetsPerIngot);
        player.getInventory().addItem(goldNuggets);
        
        player.sendMessage("§a1枚のTofuGoldを" + nuggetsPerIngot + "枚のTofuCoinに換金しました！");
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