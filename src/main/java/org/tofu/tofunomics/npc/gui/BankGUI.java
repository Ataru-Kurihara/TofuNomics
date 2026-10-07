package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        
        // 出金ボタン
        ItemStack withdrawItem = createGUIItem(
            Material.HOPPER,
            "§c出金",
            Arrays.asList(
                "§7銀行残高からコインを引き出します",
                "§7左クリック: §f10コイン",
                "§7右クリック: §f100コイン",
                "§7シフト左クリック: §f500コイン",
                "§7シフト右クリック: §f全額引き出し"
            )
        );
        gui.setItem(11, withdrawItem);
        
        // 入金ボタン
        ItemStack depositItem = createGUIItem(
            Material.CHEST,
            "§a入金",
            Arrays.asList(
                "§7手持ちのコインを銀行に入金します",
                "§7左クリック: §fコイン1個入金",
                "§7右クリック: §fコイン9個入金",
                "§7シフト+クリック: §f全てのコイン入金"
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
        
        // 閉じるボタン
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7GUIを閉じます")
        );
        gui.setItem(26, closeItem);
        
        // 装飾アイテム（銀行テーマ: 水色ガラス。無効時は装飾なし）
        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glassPane = createGUIItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            int[] decorationSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 14, 16, 17, 18, 20, 22, 23, 24, 25};
            for (int slot : decorationSlots) {
                gui.setItem(slot, glassPane);
            }
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
        
        // 手持ち側のクリックではボタンを反応させない
        if (!GuiSafety.isTopSlot(event.getRawSlot(), session.getInventory().getSize())) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        try {
            handleBankGUIClick(player, session, event.getRawSlot(), event.getClick());
        } catch (Exception e) {
            plugin.getLogger().severe("銀行GUIクリック処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.bank.action_error"));
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
        
        // 全額引き出しまたは残高が引き出し額より少ない場合は残高分を引き出し
        double amount;
        if (withdrawAll || balance < requestedAmount) {
            amount = balance;
        } else {
            amount = requestedAmount;
        }
        
        // コインは整数枚でしか渡せない。端数は残高に残し、渡す枚数の分だけ引き落とす
        int nuggets = wholeNuggets(amount);
        if (nuggets < 1) {
            player.sendMessage(configManager.getMessage("insufficient_balance"));
            return;
        }
        
        double maxWithdraw = configManager.getMaxWithdrawAmount();
        if (nuggets > maxWithdraw) {
            player.sendMessage(configManager.getMessage("economy.exceed_max_withdraw", 
                "max_amount", currencyConverter.formatCurrency(maxWithdraw)));
            return;
        }
        
        // 引き出し処理
        if (!currencyConverter.subtractBalance(player.getUniqueId(), nuggets)) {
            player.sendMessage(configManager.getMessage("npc.bank.withdraw_failed"));
            return;
        }
        
        // 手持ちに入った分だけを出金とし、入りきらなかった分は残高に戻す
        int notAdded = itemManager.addGoldNuggetsWithLeftover(player, nuggets);
        if (notAdded > 0) {
            currencyConverter.bankUnplacedNuggets(player, notAdded);
        }
        int withdrawn = withdrawnNuggets(nuggets, notAdded);
        
        if (withdrawn > 0) {
            player.sendMessage(configManager.getMessage("economy.withdraw_success", 
                "amount", currencyConverter.formatCurrency(withdrawn), 
                "currency", configManager.getCurrencyName()));
        }
        if (notAdded > 0) {
            player.sendMessage("§eインベントリに空きがないため、" + notAdded + "コインは口座に残しました。");
        }
        
        // GUIを更新
        setupBankGUIItems(session.getInventory(), player);
    }
    
    /**
     * 残高から、コインとして渡せる整数枚数を求める（端数は切り捨てて残高に残す）。
     */
    static int wholeNuggets(double amount) {
        if (Double.isNaN(amount) || amount < 1) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(amount));
    }
    
    /**
     * 実際に手持ちへ入った枚数（＝出金として扱う枚数）。
     */
    static int withdrawnNuggets(int requested, int notAdded) {
        return Math.max(0, requested - Math.max(0, notAdded));
    }
    
    private void handleDeposit(Player player, BankGUISession session,
                             org.bukkit.event.inventory.ClickType clickType) {
        int amount;

        switch (clickType) {
            case LEFT:
                amount = 1;
                break;
            case RIGHT:
                amount = 9;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                amount = Integer.MAX_VALUE; // 全て
                break;
            default:
                return;
        }

        // ItemManagerのメソッドを使用してTofuCoinとTofuGoldの両方をカウント
        int availableAmount = itemManager.countGoldNuggetsInInventory(player);

        if (availableAmount == 0) {
            player.sendMessage("§cインベントリに有効なTofuCoinまたはTofuGoldがありません。");
            player.sendMessage("§7※ TofuGoldは通貨専用の金インゴットです（1個 = 9 TofuCoin）");
            return;
        }

        // 実際に預ける量を決定
        int depositAmount = Math.min(amount, availableAmount);
        double maxDeposit = configManager.getMaxDepositAmount();
        if (depositAmount > maxDeposit) {
            depositAmount = (int) maxDeposit;
        }

        // ItemManagerのメソッドを使用してTofuCoin/TofuGoldを削除（自動的に両方対応）
        int unplacedChange = itemManager.removeGoldNuggetsReturningUnplacedChange(player, depositAmount);
        if (unplacedChange < 0) {
            player.sendMessage("§c入金処理中にエラーが発生しました。");
            return;
        }
        // TofuGold を崩したお釣りが手持ちに入りきらなかった分は、一緒に預け入れる
        depositAmount += unplacedChange;

        // 残高に追加
        double depositBalance = currencyConverter.convertNuggetsToBalance(depositAmount);
        currencyConverter.addBalance(player.getUniqueId(), depositBalance);

        String amountText = currencyConverter.formatCurrency(depositBalance);
        player.sendMessage(configManager.getMessage("economy.deposit_success",
            "amount", amountText,
            "currency", configManager.getCurrencyName()));

        player.sendMessage("§7預け入れた金額: " + depositAmount + " TofuCoin相当");

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
        
        // 金インゴットを追加。入らなければ、取り除いたコインを戻して換金を取りやめる
        ItemStack goldIngot = itemManager.createCurrencyGoldIngot(1);
        if (!player.getInventory().addItem(goldIngot).isEmpty()) {
            int notReturned = itemManager.addGoldNuggetsWithLeftover(player, nuggetsPerIngot);
            currencyConverter.bankUnplacedNuggets(player, notReturned);
            player.sendMessage("§cインベントリに空きがないため換金できません。");
            return;
        }
        
        player.sendMessage("§a" + nuggetsPerIngot + "枚のTofuCoinを1枚のTofuGoldに換金しました！");
    }
    
    /**
     * TofuGold → TofuCoin 換金処理
     */
    private void handleGoldToCoinConversion(Player player) {
        int nuggetsPerIngot = itemManager.getNuggetsPerIngot();
        
        // インベントリ内の通貨版金インゴットを数える（新旧両対応）
        int goldCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemManager.isAnyValidGoldIngot(item)) {
                goldCount += item.getAmount();
            }
        }

        // 1枚以上あるかチェック
        if (goldCount < 1) {
            player.sendMessage("§cTofuGoldが必要です。");
            return;
        }

        // 金インゴットを削除（旧形式も対応）
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && itemManager.isAnyValidGoldIngot(item)) {
                if (item.getAmount() <= 1) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                break;
            }
        }
        
        // 金塊を追加。入りきらない分は口座へ入金する
        int banked = currencyConverter.receiveCashWithBankFallback(player, nuggetsPerIngot);
        
        player.sendMessage("§a1枚のTofuGoldを" + nuggetsPerIngot + "枚のTofuCoinに換金しました！");
        if (banked > 0) {
            player.sendMessage("§eインベントリに空きがないため、" + banked + "コインは口座に入金しました。");
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 閉じられたのがこのセッションの GUI のときだけ消す（理由は GuiSafety を参照）
        BankGUISession session = activeSessions.get(playerId);
        if (session != null && GuiSafety.isSessionInventory(session.getInventory(), event.getInventory())) {
            activeSessions.remove(playerId);
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        BankGUISession session = activeSessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }
        // 手持ちの品を GUI のマスへ置けないようにする
        if (GuiSafety.dragTouchesTop(event.getRawSlots(), session.getInventory().getSize())) {
            event.setCancelled(true);
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