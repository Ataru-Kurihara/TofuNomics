package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.npc.ProcessingNPCManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 木材加工NPC用GUIシステム
 */
public class ProcessingGUI implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final ProcessingNPCManager processingNPCManager;
    private final JobManager jobManager;
    
    private final Map<UUID, ProcessingGUISession> activeSessions = new ConcurrentHashMap<>();
    private final QuantitySelectorGUI quantitySelectorGUI;
    
    public ProcessingGUI(TofuNomics plugin, ConfigManager configManager, CurrencyConverter currencyConverter, 
                        ProcessingNPCManager processingNPCManager, JobManager jobManager,
                        QuantitySelectorGUI quantitySelectorGUI) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.processingNPCManager = processingNPCManager;
        this.jobManager = jobManager;
        this.quantitySelectorGUI = quantitySelectorGUI;
    }
    
    public static class ProcessingGUISession {
        private final UUID playerId;
        private final UUID npcId;
        private final Inventory inventory;
        
        public ProcessingGUISession(UUID playerId, UUID npcId, Inventory inventory) {
            this.playerId = playerId;
            this.npcId = npcId;
            this.inventory = inventory;
        }
        
        public UUID getPlayerId() { return playerId; }
        public UUID getNpcId() { return npcId; }
        public Inventory getInventory() { return inventory; }
    }
    
    /**
     * 加工NPCのGUIを開く
     */
    public void openProcessingGUI(Player player, UUID npcId) {
        try {
            // 既存のセッションチェック
            ProcessingGUISession existingSession = activeSessions.get(player.getUniqueId());
            if (existingSession != null) {
                plugin.getLogger().warning("プレイヤー " + player.getName() + " は既に加工GUIを開いています。重複開起を防止しました。");
                return;
            }
            
            String title = "§6木材加工所";
            Inventory gui = Bukkit.createInventory(null, 54, title);
            
            setupProcessingGUIItems(gui, player, npcId);
            
            ProcessingGUISession session = new ProcessingGUISession(
                player.getUniqueId(), 
                npcId,
                gui
            );
            
            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);
            
            plugin.getLogger().info("加工GUIを開きました: " + player.getName());
            
        } catch (Exception e) {
            plugin.getLogger().severe("加工GUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§cGUIの作成に失敗しました。管理者にお知らせください。");
            e.printStackTrace();
        }
    }
    
    /**
     * GUIアイテムのセットアップ
     */
    private void setupProcessingGUIItems(Inventory gui, Player player, UUID npcId) {
        // ヘッダー部分（0-8）
        setupHeaderItems(gui, player);
        
        // 原木表示部分（9-35）
        setupLogItems(gui, player);
        
        // 加工ボタン部分（36-44）
        setupProcessingButton(gui, player);
        
        // フッター部分（45-53）
        setupFooterItems(gui);
    }
    
    /**
     * ヘッダーアイテムのセットアップ
     */
    private void setupHeaderItems(Inventory gui, Player player) {
        // 残高表示（現金残高）
        double balance = currencyConverter.getCashBalance(player);
        String balanceText = currencyConverter.formatCurrency(balance);
        
        boolean isWoodcutter = isWoodcutter(player);
        double feePerLog = isWoodcutter ? 
            configManager.getProcessingWoodcutterFee() : 
            configManager.getProcessingBaseFee();
        
        List<String> lore = new ArrayList<>();
        lore.add("§f残高: §a" + balanceText);
        lore.add("");
        if (isWoodcutter) {
            lore.add("§a木こり特典: 加工無料！");
        } else {
            lore.add("§e加工料金: §f" + String.format("%.0f", feePerLog) + "G/個");
        }
        lore.add("§7原木1個 → 板材4個");
        
        ItemStack balanceItem = createGUIItem(
            Material.GOLD_INGOT,
            "§6現在の残高と料金",
            lore
        );
        gui.setItem(4, balanceItem);
        
        // 装飾アイテム
        ItemStack glassPane = createGUIItem(Material.LIME_STAINED_GLASS_PANE, "§7", Arrays.asList());
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                gui.setItem(i, glassPane);
            }
        }
    }
    
    /**
     * 原木アイテムのセットアップ
     */
    private void setupLogItems(Inventory gui, Player player) {
        Map<Material, Integer> playerLogs = getPlayerLogs(player);
        
        int slot = 9;
        for (Map.Entry<Material, Integer> entry : playerLogs.entrySet()) {
            if (slot >= 36) break;
            
            Material logType = entry.getKey();
            int amount = entry.getValue();
            Material planksType = processingNPCManager.getPlanksFromLog(logType);
            
            if (planksType != null) {
                ItemStack logItem = createLogItem(logType, planksType, amount, player);
                gui.setItem(slot, logItem);
                slot++;
            }
        }
        
        // 説明アイテム
        if (slot == 9) {
            ItemStack noLogsItem = createGUIItem(
                Material.BARRIER,
                "§c原木がありません",
                Arrays.asList(
                    "§7インベントリに原木を入れて",
                    "§7再度GUIを開いてください"
                )
            );
            gui.setItem(22, noLogsItem);
        }
    }
    
    /**
     * 加工ボタンのセットアップ
     */
    private void setupProcessingButton(Inventory gui, Player player) {
        Map<Material, Integer> playerLogs = getPlayerLogs(player);
        int totalLogs = playerLogs.values().stream().mapToInt(Integer::intValue).sum();
        
        boolean isWoodcutter = isWoodcutter(player);
        double feePerLog = isWoodcutter ? 
            configManager.getProcessingWoodcutterFee() : 
            configManager.getProcessingBaseFee();
        double totalFee = feePerLog * totalLogs;
        double balance = currencyConverter.getCashBalance(player);
        
        // 数量選択ボタン（slot 38）
        setupQuantitySelectorButton(gui, player, totalLogs, feePerLog, balance);
        
        // 全て加工ボタン（slot 40）
        setupProcessAllButton(gui, player, totalLogs, totalFee, balance);
        
        // 装飾
        ItemStack glassPane = createGUIItem(Material.YELLOW_STAINED_GLASS_PANE, "§7", Arrays.asList());
        for (int i = 36; i < 45; i++) {
            if (i != 38 && i != 40) {
                gui.setItem(i, glassPane);
            }
        }
    }
    
    /**
     * 数量選択ボタンのセットアップ
     */
    private void setupQuantitySelectorButton(Inventory gui, Player player, int totalLogs, double feePerLog, double balance) {
        Material buttonMaterial;
        String buttonName;
        List<String> buttonLore = new ArrayList<>();
        
        if (totalLogs == 0) {
            buttonMaterial = Material.ORANGE_DYE;
            buttonName = "§7数量選択";
            buttonLore.add("§7原木がありません");
        } else {
            // 残高で加工できる最大数を計算
            int maxAffordable = feePerLog > 0 ? (int)(balance / feePerLog) : totalLogs;
            int maxQuantity = Math.min(totalLogs, maxAffordable);
            
            buttonMaterial = Material.YELLOW_DYE;
            buttonName = "§e数量を選択して加工";
            buttonLore.add("§f所持原木: §e" + totalLogs + "個");
            
            if (maxQuantity > 0) {
                buttonLore.add("§f最大加工可能: §a" + maxQuantity + "個");
                buttonLore.add("");
                buttonLore.add("§eクリックして数量を選択");
            } else {
                buttonLore.add("§c残高不足で加工不可");
            }
        }
        
        ItemStack quantitySelectorButton = createGUIItem(buttonMaterial, buttonName, buttonLore);
        gui.setItem(38, quantitySelectorButton);
    }
    
    /**
     * 全て加工ボタンのセットアップ
     */
    private void setupProcessAllButton(Inventory gui, Player player, int totalLogs, double totalFee, double balance) {
        Material buttonMaterial;
        String buttonName;
        List<String> buttonLore = new ArrayList<>();
        
        if (totalLogs == 0) {
            buttonMaterial = Material.GRAY_DYE;
            buttonName = "§7加工する原木がありません";
            buttonLore.add("§7インベントリに原木を入れてください");
        } else if (balance < totalFee) {
            buttonMaterial = Material.RED_DYE;
            buttonName = "§c残高不足";
            buttonLore.add("§f加工する原木: §e" + totalLogs + "個");
            buttonLore.add("§f必要金額: §c" + String.format("%.0f", totalFee) + "G");
            buttonLore.add("§f現在残高: §7" + currencyConverter.formatCurrency(balance));
        } else {
            buttonMaterial = Material.LIME_DYE;
            buttonName = "§a全ての原木を加工する";
            buttonLore.add("§f加工する原木: §e" + totalLogs + "個");
            buttonLore.add("§f受け取る板材: §a" + (totalLogs * 4) + "個");
            if (totalFee > 0) {
                buttonLore.add("§f加工料金: §e" + String.format("%.0f", totalFee) + "G");
            } else {
                buttonLore.add("§a加工料金: 無料（木こり特典）");
            }
            buttonLore.add("");
            buttonLore.add("§eクリックして加工開始");
        }
        
        ItemStack processButton = createGUIItem(buttonMaterial, buttonName, buttonLore);
        gui.setItem(40, processButton);
    }

    /**
     * フッターアイテムのセットアップ
     */
    private void setupFooterItems(Inventory gui) {
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§7", Arrays.asList());
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, glassPane);
        }
        
        // 閉じるボタン
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7クリックしてGUIを閉じます")
        );
        gui.setItem(49, closeItem);
    }
    
    /**
     * 原木アイテムの作成
     */
    private ItemStack createLogItem(Material logType, Material planksType, int amount, Player player) {
        List<String> lore = new ArrayList<>();
        
        lore.add("§f所持数: §e" + amount + "個");
        lore.add("§7↓");
        lore.add("§f板材: §a" + (amount * 4) + "個");
        
        boolean isWoodcutter = isWoodcutter(player);
        double feePerLog = isWoodcutter ? 
            configManager.getProcessingWoodcutterFee() : 
            configManager.getProcessingBaseFee();
        double totalFee = feePerLog * amount;
        
        if (totalFee > 0) {
            lore.add("§f加工料金: §e" + String.format("%.0f", totalFee) + "G");
        } else {
            lore.add("§a加工料金: 無料");
        }
        
        String displayName = "§6" + getLogDisplayName(logType);
        return createGUIItem(logType, displayName, lore);
    }
    
    /**
     * プレイヤーが所持している原木を取得
     */
    private Map<Material, Integer> getPlayerLogs(Player player) {
        Map<Material, Integer> logs = new LinkedHashMap<>();
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            Material material = item.getType();
            if (processingNPCManager.isProcessableLog(material)) {
                logs.put(material, logs.getOrDefault(material, 0) + item.getAmount());
            }
        }
        
        return logs;
    }
    
    /**
     * 原木の表示名を取得
     */
    private String getLogDisplayName(Material logType) {
        switch (logType) {
            case OAK_LOG: return "オークの原木";
            case SPRUCE_LOG: return "マツの原木";
            case BIRCH_LOG: return "シラカバの原木";
            case JUNGLE_LOG: return "ジャングルの原木";
            case ACACIA_LOG: return "アカシアの原木";
            case DARK_OAK_LOG: return "ダークオークの原木";
            case CRIMSON_STEM: return "真紅の幹";
            case WARPED_STEM: return "歪んだ幹";
            default: return logType.toString().toLowerCase().replace("_", " ");
        }
    }
    
    /**
     * プレイヤーが木こりかどうか
     */
    private boolean isWoodcutter(Player player) {
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        return "woodcutter".equals(playerJob);
    }
    
    /**
     * GUIアイテムの作成
     */
    private ItemStack createGUIItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * インベントリクリックイベント
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ProcessingGUISession session = activeSessions.get(player.getUniqueId());
        
        if (session == null || !event.getInventory().equals(session.getInventory())) {
            return;
        }
        
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        if (slot == 49) { // 閉じるボタン
            player.closeInventory();
            return;
        }
        
        if (slot == 38) { // 数量選択ボタン
            handleQuantitySelectorClick(player, session);
            return;
        }
        
        if (slot == 40) { // 加工ボタン
            handleProcessingClick(player, session);
            return;
        }
    }
    
    /**
     * 数量選択ボタンクリック処理
     */
    private void handleQuantitySelectorClick(Player player, ProcessingGUISession session) {
        // プレイヤーのインベントリから原木を収集
        List<ItemStack> logItems = new ArrayList<>();
        int totalLogs = 0;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && processingNPCManager.isProcessableLog(item.getType())) {
                logItems.add(item);
                totalLogs += item.getAmount();
            }
        }
        
        if (logItems.isEmpty()) {
            player.sendMessage("§c加工する原木がありません。");
            return;
        }
        
        // 残高で加工できる最大数を計算
        boolean isWoodcutter = isWoodcutter(player);
        double feePerLog = isWoodcutter ? 
            configManager.getProcessingWoodcutterFee() : 
            configManager.getProcessingBaseFee();
        double balance = currencyConverter.getCashBalance(player);
        
        int maxAffordable = feePerLog > 0 ? (int)(balance / feePerLog) : totalLogs;
        int maxQuantity = Math.min(totalLogs, maxAffordable);
        
        if (maxQuantity <= 0) {
            player.sendMessage("§c残高が不足しているため、加工できません。");
            return;
        }
        
        // 追加情報
        List<String> additionalInfo = new ArrayList<>();
        additionalInfo.add("§f所持原木: §e" + totalLogs + "個");
        additionalInfo.add("§f加工料金: §e" + String.format("%.0f", feePerLog) + "G/個");
        if (feePerLog > 0) {
            additionalInfo.add("§f現在残高: §a" + currencyConverter.formatCurrency(balance));
        } else {
            additionalInfo.add("§a加工料金: 無料（木こり特典）");
        }
        
        // GUIを一旦閉じる
        player.closeInventory();
        
        // 数量選択GUIを開く
        quantitySelectorGUI.openQuantitySelector(
            player,
            "§6加工数量を選択",
            "原木",
            Material.OAK_LOG,
            maxQuantity, // 初期値は最大値
            1, // 最小1個
            maxQuantity, // 最大は所持数または残高で加工可能な数
            additionalInfo,
            // 確定時のコールバック
            (p, selectedQuantity) -> {
                plugin.getLogger().info("数量選択加工開始: " + p.getName() + " - " + selectedQuantity + "個");
                
                // 原木を再収集（GUIを閉じている間に変わっている可能性がある）
                List<ItemStack> currentLogItems = new ArrayList<>();
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null && processingNPCManager.isProcessableLog(item.getType())) {
                        currentLogItems.add(item);
                    }
                }
                
                // 加工処理実行
                ProcessingNPCManager.ProcessingResult result = processingNPCManager.processWoodConversionWithQuantity(
                    p, session.getNpcId(), selectedQuantity, currentLogItems
                );
                
                if (result.isSuccess()) {
                    p.sendMessage(result.getMessage());
                    p.sendMessage("§a原木 " + result.getLogsProcessed() + "個 → 板材 " + result.getPlanksCreated() + "個");
                } else {
                    p.sendMessage("§c" + result.getMessage());
                }
            },
            // キャンセル時のコールバック
            () -> {
                plugin.getLogger().info("数量選択がキャンセルされました: " + player.getName());
            }
        );
    }
    
    /**
     * 加工ボタンクリック処理
     */
    private void handleProcessingClick(Player player, ProcessingGUISession session) {
        // プレイヤーのインベントリから原木を収集
        List<ItemStack> logItems = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && processingNPCManager.isProcessableLog(item.getType())) {
                logItems.add(item);
            }
        }
        
        if (logItems.isEmpty()) {
            player.sendMessage("§c加工する原木がありません。");
            player.closeInventory();
            return;
        }
        
        plugin.getLogger().info("加工処理開始: " + player.getName());
        
        // 加工処理実行
        ProcessingNPCManager.ProcessingResult result = processingNPCManager.processWoodConversion(
            player, session.getNpcId(), logItems
        );
        
        if (result.isSuccess()) {
            player.sendMessage(result.getMessage());
            player.sendMessage("§a原木 " + result.getLogsProcessed() + "個 → 板材 " + result.getPlanksCreated() + "個");
            
            // GUIを閉じる
            player.closeInventory();
        } else {
            player.sendMessage("§c" + result.getMessage());
            
            // 残高不足などの場合はGUIを更新
            setupProcessingGUIItems(session.getInventory(), player, session.getNpcId());
        }
    }
    
    /**
     * インベントリクローズイベント
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        ProcessingGUISession session = activeSessions.get(player.getUniqueId());
        
        if (session != null && event.getInventory().equals(session.getInventory())) {
            activeSessions.remove(player.getUniqueId());
            plugin.getLogger().info("加工GUIを閉じました: " + player.getName());
        }
    }
    
    /**
     * 全GUIを閉じる
     */
    public void closeAllGUIs() {
        for (ProcessingGUISession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }
    
    /**
     * アクティブセッション数を取得
     */
    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
}
