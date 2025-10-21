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
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.npc.TradingNPCManager;
import org.tofu.tofunomics.trade.TradePriceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TradingGUI implements Listener {
    
    public enum ItemCategory {
        ALL("§7すべて", Material.CHEST),
        MINING("§8鉱物", Material.DIAMOND_PICKAXE),
        FARMING("§a農作物", Material.WHEAT),
        LOGGING("§2木材", Material.OAK_LOG),
        FISHING("§9海産物", Material.COD),
        CRAFTING("§6製作品", Material.CRAFTING_TABLE),
        MATERIALS("§5素材", Material.BLAZE_POWDER);
        
        private final String displayName;
        private final Material icon;
        
        ItemCategory(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
        
        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
    }
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final JobManager jobManager;
    private final TradingNPCManager tradingNPCManager;
    private final TradePriceManager tradePriceManager;
    
    private final Map<UUID, TradingGUISession> activeSessions = new ConcurrentHashMap<>();
    
    public TradingGUI(TofuNomics plugin, ConfigManager configManager, CurrencyConverter currencyConverter,
                     JobManager jobManager, TradingNPCManager tradingNPCManager, TradePriceManager tradePriceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.jobManager = jobManager;
        this.tradingNPCManager = tradingNPCManager;
        this.tradePriceManager = tradePriceManager;
    }
    
    private static class TradingGUISession {
        private final UUID playerId;
        private final String tradingPostId;
        private final Inventory inventory;
        private final long createdTime;
        private int currentPage;
        private ItemCategory currentCategory;
        private String searchFilter;
        
        public TradingGUISession(UUID playerId, String tradingPostId, Inventory inventory) {
            this.playerId = playerId;
            this.tradingPostId = tradingPostId;
            this.inventory = inventory;
            this.createdTime = System.currentTimeMillis();
            this.currentPage = 0;
            this.currentCategory = ItemCategory.ALL;
            this.searchFilter = "";
        }
        
        public UUID getPlayerId() { return playerId; }
        public String getTradingPostId() { return tradingPostId; }
        public Inventory getInventory() { return inventory; }
        public long getCreatedTime() { return createdTime; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int page) { this.currentPage = page; }
        public ItemCategory getCurrentCategory() { return currentCategory; }
        public void setCurrentCategory(ItemCategory category) { this.currentCategory = category; }
        public String getSearchFilter() { return searchFilter; }
        public void setSearchFilter(String filter) { this.searchFilter = filter != null ? filter : ""; }
    }
    
    public void openTradingGUI(Player player, TradingNPCManager.TradingPost tradingPost) {
        plugin.getLogger().info("TradingGUI.openTradingGUI開始: プレイヤー=" + player.getName() + ", 取引所=" + tradingPost.getName());
        
        try {
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());
            plugin.getLogger().info("TradingGUI内での職業確認: " + (playerJob != null ? playerJob : "職業なし"));
            
            // 無職の場合、この取引所が無職を受け入れるかチェック
            if (playerJob == null) {
                plugin.getLogger().info("TradingGUI: 無職プレイヤーの受け入れ判定中...");
                boolean acceptsNoJob = tradingPost.acceptsJob(null);
                plugin.getLogger().info("TradingGUI: 取引所「" + tradingPost.getName() + "」の無職受け入れ: " + (acceptsNoJob ? "可能" : "不可"));
                
                if (!acceptsNoJob) {
                    // この取引所は無職を受け入れない
                    plugin.getLogger().info("TradingGUI: 職業なしのため処理中断");
                    String npcType = getNPCTypeFromTradingPostId(tradingPost.getId());
                    configManager.sendNPCSpecificMessageList(player, npcType, "no_job");
                    return;
                }
                
                // この取引所は無職も受け入れる
                plugin.getLogger().info("TradingGUI: この取引所は無職も受け入れます。GUI作成を続行");
            } else {
                // 職業がある場合、職業対応チェック
                plugin.getLogger().info("TradingGUI: 職業対応チェック - プレイヤー職業=" + playerJob + ", 対応職業=" + String.join(", ", tradingPost.getAcceptedJobTypes()));
                
                if (!tradingPost.acceptsJob(playerJob)) {
                    plugin.getLogger().info("TradingGUI: 職業不対応のため処理中断");
                    String npcType = getNPCTypeFromTradingPostId(tradingPost.getId());
                    String acceptedJobsStr = String.join(", ", tradingPost.getAcceptedJobTypes());
                    configManager.sendNPCSpecificMessageList(player, npcType, "job_not_accepted", 
                        "player", player.getName(), 
                        "job", playerJob, 
                        "accepted_jobs", acceptedJobsStr);
                    return;
                }
            }
            
            String title = "§6" + tradingPost.getName() + " - アイテム取引";
            plugin.getLogger().info("TradingGUI: GUIタイトル設定完了: " + title);
            
            Inventory gui = Bukkit.createInventory(null, 54, title);
            plugin.getLogger().info("TradingGUI: インベントリ作成完了");
            
            TradingGUISession session = new TradingGUISession(
                player.getUniqueId(),
                tradingPost.getId(),
                gui
            );
            plugin.getLogger().info("TradingGUI: セッション作成完了");
            
            setupTradingGUIItems(gui, player, tradingPost, session);
            plugin.getLogger().info("TradingGUI: GUIアイテム設定完了");
            
            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);
            plugin.getLogger().info("TradingGUI: インベントリ開起完了");
            
            plugin.getLogger().info("取引GUIを開きました: " + player.getName() + " -> " + tradingPost.getName());
            
        } catch (Exception e) {
            plugin.getLogger().severe("取引GUI作成中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("プレイヤー: " + player.getName() + ", 取引所: " + tradingPost.getName());
            player.sendMessage("§c取引画面の表示中にエラーが発生しました。");
            player.sendMessage("§c管理者にお知らせください。詳細はサーバーログを確認してください。");
            e.printStackTrace();
        }
    }
    
    private void setupTradingGUIItems(Inventory gui, Player player, TradingNPCManager.TradingPost tradingPost, TradingGUISession session) {
        gui.clear();
        
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        List<Map.Entry<Material, Double>> allItems = new ArrayList<>(tradingPost.getItemPrices().entrySet());
        
        // フィルタリング適用
        List<Map.Entry<Material, Double>> filteredItems = filterItems(allItems, session.getCurrentCategory(), session.getSearchFilter());
        
        // ページング設定
        int itemsPerPage = 21; // 3行 x 7列 (カテゴリボタン用に上部を確保)
        int startIndex = session.getCurrentPage() * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredItems.size());
        
        // カテゴリボタン表示
        setupCategoryButtons(gui, session.getCurrentCategory());
        
        // アイテム表示（2行目から開始）
        int slot = 18; // 2行目開始スロット
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<Material, Double> entry = filteredItems.get(i);
            Material material = entry.getKey();
            double basePrice = entry.getValue();
            
            // 最終価格を計算（職業ボーナス適用）
            double finalPrice = tradePriceManager.calculateFinalPrice(
                material.toString().toLowerCase(), 
                playerJob, 
                basePrice
            );
            
            // プレイヤーの手持ち数量をカウント
            int playerAmount = countPlayerItems(player, material);
            
            ItemStack displayItem = createTradingItem(material, basePrice, finalPrice, playerAmount, playerJob);
            gui.setItem(slot, displayItem);
            
            // スロット位置調整
            slot++;
            if ((slot - 18) % 9 == 7) { // 行末に達したら次の行の最初へ
                slot += 2;
            }
            if (slot >= 45) break; // 最下段に達したら終了
        }
        
        // 情報表示
        double balance = currencyConverter.getBalance(player.getUniqueId());
        ItemStack infoItem = createGUIItem(
            Material.GOLD_NUGGET,
            "§6取引情報",
            Arrays.asList(
                "§f現在の残高: §a" + currencyConverter.formatCurrency(balance),
                "§f職業: §e" + (playerJob != null ? playerJob : "無職"),
                "§f職業ボーナス: §a" + (playerJob != null ? String.format("%.0f%%", (configManager.getJobPriceMultiplier(playerJob) - 1.0) * 100) : "なし"),
                "",
                "§7アイテムをクリックして売却"
            )
        );
        gui.setItem(4, infoItem);
        
        // ページング
        if (session.getCurrentPage() > 0) {
            ItemStack prevPage = createGUIItem(
                Material.ARROW,
                "§a前のページ",
                Arrays.asList("§7ページ " + (session.getCurrentPage() + 1) + " → " + session.getCurrentPage())
            );
            gui.setItem(48, prevPage);
        }
        
        if (endIndex < filteredItems.size()) {
            ItemStack nextPage = createGUIItem(
                Material.ARROW,
                "§a次のページ",
                Arrays.asList("§7ページ " + (session.getCurrentPage() + 1) + " → " + (session.getCurrentPage() + 2))
            );
            gui.setItem(50, nextPage);
        }
        
        // 現在のフィルター状態表示
        ItemStack filterInfo = createGUIItem(
            Material.BOOK,
            "§6フィルター情報",
            Arrays.asList(
                "§fカテゴリ: " + session.getCurrentCategory().getDisplayName(),
                "§f検索: " + (session.getSearchFilter().isEmpty() ? "§7なし" : "§e" + session.getSearchFilter()),
                "§f表示中: §a" + filteredItems.size() + " §f/ " + allItems.size() + " アイテム",
                "§7カテゴリボタンでフィルタリング"
            )
        );
        gui.setItem(45, filterInfo);
        
        // 全て売却ボタン
        ItemStack sellAllItem = createGUIItem(
            Material.CHEST,
            "§c全アイテム売却",
            Arrays.asList(
                "§7売却可能な全てのアイテムを一度に売却",
                "§c※ 元に戻すことはできません",
                "§eクリックで実行"
            )
        );
        gui.setItem(49, sellAllItem);
        
        // 閉じるボタン
        ItemStack closeItem = createGUIItem(
            Material.BARRIER,
            "§c閉じる",
            Arrays.asList("§7GUIを閉じます")
        );
        gui.setItem(53, closeItem);
        
        // 装飾アイテム
        fillEmptySlots(gui);
    }
    
    private ItemStack createTradingItem(Material material, double basePrice, double finalPrice, 
                                      int playerAmount, String playerJob) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§f" + getDisplayName(material));
            
            List<String> lore = new ArrayList<>();
            lore.add("§f基本価格: §e" + currencyConverter.formatCurrency(basePrice));
            
            if (Math.abs(finalPrice - basePrice) > 0.01) {
                double bonusPercent = ((finalPrice / basePrice) - 1.0) * 100;
                lore.add("§f職業価格: §a" + currencyConverter.formatCurrency(finalPrice) + 
                        " §7(+" + String.format("%.1f", bonusPercent) + "%)");
            }
            
            lore.add("§f所持数: §b" + playerAmount + "個");
            
            if (playerAmount > 0) {
                double totalValue = finalPrice * playerAmount;
                lore.add("§f合計価値: §a" + currencyConverter.formatCurrency(totalValue));
                lore.add("");
                lore.add("§e左クリック: §f1個売却");
                lore.add("§e右クリック: §f10個売却");
                lore.add("§eシフト+クリック: §f全て売却");
            } else {
                lore.add("§c売却可能なアイテムがありません");
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private String getDisplayName(Material material) {
        String name = material.toString().toLowerCase().replace("_", " ");
        String[] parts = name.split(" ");
        StringBuilder displayName = new StringBuilder();
        
        for (String part : parts) {
            if (displayName.length() > 0) displayName.append(" ");
            displayName.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        
        return displayName.toString();
    }
    
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
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
    
    /**
     * カテゴリボタンを設定
     */
    private void setupCategoryButtons(Inventory gui, ItemCategory currentCategory) {
        int slot = 1;
        for (ItemCategory category : ItemCategory.values()) {
            boolean isSelected = category == currentCategory;
            ItemStack categoryButton = createGUIItem(
                category.getIcon(),
                (isSelected ? "§a§l" : "§f") + category.getDisplayName(),
                Arrays.asList(
                    isSelected ? "§a選択中のカテゴリ" : "§7クリックで選択",
                    "§7このカテゴリのアイテムを表示"
                )
            );
            gui.setItem(slot, categoryButton);
            slot++;
        }
    }
    
    /**
     * アイテムフィルタリング
     */
    private List<Map.Entry<Material, Double>> filterItems(List<Map.Entry<Material, Double>> items, 
                                                          ItemCategory category, String searchFilter) {
        return items.stream()
            .filter(entry -> matchesCategory(entry.getKey(), category))
            .filter(entry -> matchesSearch(entry.getKey(), searchFilter))
            .collect(Collectors.toList());
    }
    
    /**
     * カテゴリマッチング
     */
    private boolean matchesCategory(Material material, ItemCategory category) {
        if (category == ItemCategory.ALL) {
            return true;
        }
        
        String materialName = material.toString().toLowerCase();
        switch (category) {
            case MINING:
                return isMiningItem(materialName);
            case FARMING:
                return isFarmingItem(materialName);
            case LOGGING:
                return isLoggingItem(materialName);
            case FISHING:
                return isFishingItem(materialName);
            case CRAFTING:
                return isCraftingItem(materialName);
            case MATERIALS:
                return isMaterialItem(materialName);
            default:
                return true;
        }
    }
    
    /**
     * 検索フィルターマッチング
     */
    private boolean matchesSearch(Material material, String searchFilter) {
        if (searchFilter == null || searchFilter.trim().isEmpty()) {
            return true;
        }
        
        String materialName = material.toString().toLowerCase();
        String displayName = getDisplayName(material).toLowerCase();
        String filter = searchFilter.toLowerCase().trim();
        
        return materialName.contains(filter) || displayName.contains(filter);
    }

    /**
     * 原木アイテムかどうかを判定
     */
    private boolean isLogItem(Material material) {
        String materialName = material.toString();
        return materialName.endsWith("_LOG") || 
               materialName.equals("CRIMSON_STEM") || 
               materialName.equals("WARPED_STEM");
    }
    
    // カテゴリ判定メソッド群
    private boolean isMiningItem(String materialName) {
        return materialName.contains("ore") || materialName.contains("ingot") || 
               materialName.contains("coal") || materialName.contains("diamond") || 
               materialName.contains("emerald") || materialName.contains("redstone") ||
               materialName.contains("lapis") || materialName.contains("quartz") ||
               materialName.contains("stone") || materialName.contains("cobblestone");
    }
    
    private boolean isFarmingItem(String materialName) {
        return materialName.contains("wheat") || materialName.contains("potato") ||
               materialName.contains("carrot") || materialName.contains("beetroot") ||
               materialName.contains("pumpkin") || materialName.contains("melon") ||
               materialName.contains("apple") || materialName.contains("bread") ||
               materialName.contains("sugar") || materialName.contains("cocoa");
    }
    
    private boolean isLoggingItem(String materialName) {
        return materialName.contains("log") || materialName.contains("wood") ||
               materialName.contains("plank") || materialName.contains("stick") ||
               materialName.contains("bark") || materialName.contains("stem");
    }
    
    private boolean isFishingItem(String materialName) {
        return materialName.contains("cod") || materialName.contains("salmon") ||
               materialName.contains("fish") || materialName.contains("kelp") ||
               materialName.contains("seagrass") || materialName.contains("prismarine");
    }
    
    private boolean isCraftingItem(String materialName) {
        return materialName.contains("sword") || materialName.contains("pickaxe") ||
               materialName.contains("axe") || materialName.contains("shovel") ||
               materialName.contains("hoe") || materialName.contains("helmet") ||
               materialName.contains("chestplate") || materialName.contains("leggings") ||
               materialName.contains("boots");
    }
    
    private boolean isMaterialItem(String materialName) {
        return materialName.contains("powder") || materialName.contains("dust") ||
               materialName.contains("tear") || materialName.contains("eye") ||
               materialName.contains("membrane") || materialName.contains("cream");
    }
    
    private void fillEmptySlots(Inventory gui) {
        ItemStack glassPane = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
        
        // 上段と下段の装飾
        for (int i = 0; i < 9; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glassPane);
        }
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glassPane);
        }
        
        // 左右の装飾
        int[] sideSlots = {9, 18, 27, 36, 17, 26, 35, 44};
        for (int slot : sideSlots) {
            if (gui.getItem(slot) == null) gui.setItem(slot, glassPane);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        
        TradingGUISession session = activeSessions.get(playerId);
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        try {
            handleTradingGUIClick(player, session, event.getSlot(), event.getClick(), clickedItem);
        } catch (Exception e) {
            plugin.getLogger().severe("取引GUIクリック処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.trading.action_error"));
            e.printStackTrace();
        }
    }
    
    private void handleTradingGUIClick(Player player, TradingGUISession session, int slot, 
                                     org.bukkit.event.inventory.ClickType clickType, ItemStack clickedItem) {
        
        TradingNPCManager.TradingPost tradingPost = tradingNPCManager.getTradingPost(session.getTradingPostId());
        if (tradingPost == null) {
            player.sendMessage(configManager.getMessage("npc.trading.post_not_found"));
            return;
        }
        
        // カテゴリボタン処理 (スロット1-7)
        if (slot >= 1 && slot <= 7) {
            ItemCategory[] categories = ItemCategory.values();
            int categoryIndex = slot - 1;
            if (categoryIndex < categories.length) {
                session.setCurrentCategory(categories[categoryIndex]);
                session.setCurrentPage(0); // カテゴリ変更時はページをリセット
                setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
            }
            return;
        }
        
        switch (slot) {
            case 48: // 前のページ
                if (session.getCurrentPage() > 0) {
                    session.setCurrentPage(session.getCurrentPage() - 1);
                    setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                }
                break;
                
            case 50: // 次のページ
                session.setCurrentPage(session.getCurrentPage() + 1);
                setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                break;
                
            case 49: // 全て売却
                handleSellAll(player, tradingPost);
                setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                break;
                
            case 53: // 閉じる
                player.closeInventory();
                break;
                
            default:
                // アイテム売却処理 (スロット18-44の範囲)
                if (isItemSlot(slot)) {
                    Material material = clickedItem.getType();
                    if (tradingPost.getItemPrice(material) > 0) {
                        handleItemSell(player, tradingPost, material, clickType);
                        setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                    }
                }
                break;
        }
    }
    
    private boolean isItemSlot(int slot) {
        // アイテム表示エリアのスロット判定（2行目～4行目）
        if (slot < 18 || slot > 44) return false;
        int row = (slot - 18) / 9;
        int column = (slot - 18) % 9;
        return row >= 0 && row <= 2 && column >= 0 && column <= 6; // 3行 x 7列
    }
    
    private void handleItemSell(Player player, TradingNPCManager.TradingPost tradingPost, 
                               Material material, org.bukkit.event.inventory.ClickType clickType) {
        int sellAmount;
        
        switch (clickType) {
            case LEFT:
                sellAmount = 1;
                break;
            case RIGHT:
                sellAmount = 10;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                sellAmount = Integer.MAX_VALUE;
                break;
            default:
                return;
        }
        
        // プレイヤーのアイテムから売却処理
        List<ItemStack> itemsToSell = new ArrayList<>();
        int remaining = sellAmount;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && remaining > 0) {
                int available = item.getAmount();
                int takeAmount = Math.min(available, remaining);
                
                if (takeAmount > 0) {
                    ItemStack sellItem = item.clone();
                    sellItem.setAmount(takeAmount);
                    itemsToSell.add(sellItem);
                    remaining -= takeAmount;
                }
            }
        }
        
        if (itemsToSell.isEmpty()) {
            player.sendMessage(configManager.getMessage("npc.trading.no_items_to_sell"));
            return;
        }

        // 事前にスペースをチェック（売却金額を計算して必要なスロット数を確認）
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        double totalEarnings = 0.0;

        // 売却金額の合計を計算（木こりボーナスも考慮）
        for (ItemStack sellItem : itemsToSell) {
            Material mat = sellItem.getType();
            double basePrice = tradingPost.getItemPrice(mat);
            if (basePrice > 0) {
                double finalPrice = tradePriceManager.calculateFinalPrice(mat.toString().toLowerCase(), playerJob, basePrice);
                
                // 木こりが原木を売る場合は価格を2倍に
                if ("woodcutter".equals(playerJob) && isLogItem(mat)) {
                    finalPrice *= 2.0;
                }
                
                totalEarnings += finalPrice * sellItem.getAmount();
            }
        }

        // 必要な金塊数を計算
        int requiredNuggets = currencyConverter.convertBalanceToNuggets(totalEarnings);
        
        // デバッグログ
        plugin.getLogger().info("[TradingGUI] 事前チェック - totalEarnings: " + totalEarnings + ", requiredNuggets: " + requiredNuggets);
        
        // ItemManager.hasInventorySpaceを使用（既存の金塊スタックの空きスペースも考慮される）
        boolean hasSpace = currencyConverter.getItemManager().hasInventorySpace(player, requiredNuggets);
        plugin.getLogger().info("[TradingGUI] 事前チェック - hasInventorySpace result: " + hasSpace);
        
        if (!hasSpace) {
            player.sendMessage("§cインベントリに空きがありません。金塊を受け取るスペースを確保してください。");
            return;
        }

        // 先にインベントリからアイテムを削除（ロールバック用に記録）
        Map<Integer, ItemStack> removedItems = new HashMap<>();
        for (ItemStack sellItem : itemsToSell) {
            int remainingToRemove = sellItem.getAmount();
            Material sellMaterial = sellItem.getType();
            
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == sellMaterial && remainingToRemove > 0) {
                    int removeAmount = Math.min(item.getAmount(), remainingToRemove);
                    
                    // バックアップ
                    removedItems.put(i, item.clone());
                    
                    // 削除（0個になる場合はスロットをnullに設定）
                    if (item.getAmount() <= removeAmount) {
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - removeAmount);
                        player.getInventory().setItem(i, item);
                    }
                    remainingToRemove -= removeAmount;
                }
            }
        }
        
        // 売却処理を実行（スペースチェックはスキップ - 事前にチェック済み）
        TradingNPCManager.TradeResult result = tradingNPCManager.processItemSale(
            player, 
            tradingPost.getNpcId(), 
            itemsToSell,
            true  // skipSpaceCheck = true
        );
        
        if (result.isSuccess()) {
            // 成功 - アイテムは既に削除済み
            String earnings = currencyConverter.formatCurrency(result.getTotalEarnings());
            player.sendMessage(configManager.getMessage("npc.trading.sale_success", "total", earnings));
        } else {
            // 失敗 - アイテムをロールバック
            for (Map.Entry<Integer, ItemStack> entry : removedItems.entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue());
            }
            player.sendMessage(result.getMessage());
        }
    }
    
    private void handleSellAll(Player player, TradingNPCManager.TradingPost tradingPost) {
        List<ItemStack> allItems = new ArrayList<>();
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getAmount() > 0 && tradingPost.getItemPrice(item.getType()) > 0) {
                allItems.add(item.clone());
            }
        }
        
        if (allItems.isEmpty()) {
            player.sendMessage(configManager.getMessage("npc.trading.no_sellable_items"));
            return;
        }

        // 事前にスペースをチェック（売却金額を計算して必要なスロット数を確認）
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        double totalEarnings = 0.0;

        // 売却金額の合計を計算（木こりボーナスも考慮）
        for (ItemStack sellItem : allItems) {
            Material mat = sellItem.getType();
            double basePrice = tradingPost.getItemPrice(mat);
            if (basePrice > 0) {
                double finalPrice = tradePriceManager.calculateFinalPrice(mat.toString().toLowerCase(), playerJob, basePrice);
                
                // 木こりが原木を売る場合は価格を2倍に
                if ("woodcutter".equals(playerJob) && isLogItem(mat)) {
                    finalPrice *= 2.0;
                }
                
                totalEarnings += finalPrice * sellItem.getAmount();
            }
        }

        // 必要な金塊数を計算
        int requiredNuggets = currencyConverter.convertBalanceToNuggets(totalEarnings);
        
        // デバッグログ
        plugin.getLogger().info("[TradingGUI] 事前チェック - totalEarnings: " + totalEarnings + ", requiredNuggets: " + requiredNuggets);
        
        // ItemManager.hasInventorySpaceを使用（既存の金塊スタックの空きスペースも考慮される）
        boolean hasSpace = currencyConverter.getItemManager().hasInventorySpace(player, requiredNuggets);
        plugin.getLogger().info("[TradingGUI] 事前チェック - hasInventorySpace result: " + hasSpace);
        
        if (!hasSpace) {
            player.sendMessage("§cインベントリに空きがありません。金塊を受け取るスペースを確保してください。");
            return;
        }

        // 先にインベントリからアイテムを削除（ロールバック用に記録）
        Map<Integer, ItemStack> removedItems = new HashMap<>();
        for (ItemStack sellItem : allItems) {
            int remainingToRemove = sellItem.getAmount();
            Material sellMaterial = sellItem.getType();
            
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == sellMaterial && remainingToRemove > 0) {
                    int removeAmount = Math.min(item.getAmount(), remainingToRemove);
                    
                    // バックアップ
                    removedItems.put(i, item.clone());
                    
                    // 削除（0個になる場合はスロットをnullに設定）
                    if (item.getAmount() <= removeAmount) {
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - removeAmount);
                        player.getInventory().setItem(i, item);
                    }
                    remainingToRemove -= removeAmount;
                }
            }
        }
        
        // 売却処理を実行（スペースチェックはスキップ - 事前にチェック済み）
        TradingNPCManager.TradeResult result = tradingNPCManager.processItemSale(
            player, 
            tradingPost.getNpcId(), 
            allItems,
            true  // skipSpaceCheck = true
        );
        
        if (result.isSuccess()) {
            // 成功 - アイテムは既に削除済み
            String earnings = currencyConverter.formatCurrency(result.getTotalEarnings());
            player.sendMessage(configManager.getMessage("npc.trading.sell_all_success", 
                "total", earnings,
                "count", String.valueOf(result.getSoldItems().values().stream().mapToInt(Integer::intValue).sum())));
        } else {
            // 失敗 - アイテムをロールバック
            for (Map.Entry<Integer, ItemStack> entry : removedItems.entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue());
            }
            player.sendMessage(result.getMessage());
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        TradingGUISession session = activeSessions.remove(playerId);
        if (session != null) {
            plugin.getLogger().info("取引GUIを閉じました: " + player.getName());
        }
    }
    
    public void closeAllGUIs() {
        for (TradingGUISession session : activeSessions.values()) {
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
    
    /**
     * 取引所IDからNPCタイプを取得（個性的なメッセージ用）
     */
    private String getNPCTypeFromTradingPostId(String tradingPostId) {
        // config.ymlの設定と対応するNPCタイプにマッピング
        switch (tradingPostId) {
            case "central_market":
                return "central_market";
            case "mining_post":
                return "mining_post";
            case "wood_market":
                return "wood_market";
            default:
                // デフォルトは中央市場スタイル
                return "central_market";
        }
    }
}