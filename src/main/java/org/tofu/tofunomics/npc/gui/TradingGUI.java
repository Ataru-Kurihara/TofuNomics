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
import org.tofu.tofunomics.economy.ItemNameText;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.npc.TradingNPCManager;
import org.tofu.tofunomics.trade.TradePriceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.tofu.tofunomics.util.NPCPurchaseMarker;

public class TradingGUI implements Listener {
    
    public enum ItemCategory {
        ALL("§7すべて", Material.CHEST),
        MINING("§8鉱物", Material.DIAMOND_PICKAXE),
        FARMING("§a農作物", Material.WHEAT),
        LOGGING("§2木材", Material.OAK_LOG),
        FISHING("§9海産物", Material.COD),
        CRAFTING("§6製作品", Material.CRAFTING_TABLE),
        BUILDING("§e建材", Material.BRICKS),
        FOOD("§c食料", Material.COOKED_BEEF),
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
    
    public enum TradingMode {
        SELL("§a売却モード", Material.EMERALD),
        BUY("§e購入モード", Material.GOLD_INGOT);
        
        private final String displayName;
        private final Material icon;
        
        TradingMode(String displayName, Material icon) {
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
        private TradingMode tradingMode;
        private String searchFilter;
        
        public TradingGUISession(UUID playerId, String tradingPostId, Inventory inventory) {
            this.playerId = playerId;
            this.tradingPostId = tradingPostId;
            this.inventory = inventory;
            this.createdTime = System.currentTimeMillis();
            this.currentPage = 0;
            this.currentCategory = ItemCategory.ALL;
            this.tradingMode = TradingMode.SELL;
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
        public TradingMode getTradingMode() { return tradingMode; }
        public void setTradingMode(TradingMode mode) { this.tradingMode = mode; }
        public String getSearchFilter() { return searchFilter; }
        public void setSearchFilter(String filter) { this.searchFilter = filter != null ? filter : ""; }
    }
    
    public void openTradingGUI(Player player, TradingNPCManager.TradingPost tradingPost) {
        try {
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());

            // 無職の場合、この取引所が無職を受け入れるかチェック
            if (playerJob == null) {
                boolean acceptsNoJob = tradingPost.acceptsJob(null);

                if (!acceptsNoJob) {
                    // この取引所は無職を受け入れない
                    String npcType = getNPCTypeFromTradingPostId(tradingPost.getId());
                    configManager.sendNPCSpecificMessageList(player, npcType, "no_job");
                    return;
                }
            } else {
                // 職業がある場合、職業対応チェック
                if (!tradingPost.acceptsJob(playerJob)) {
                    String npcType = getNPCTypeFromTradingPostId(tradingPost.getId());
                    String acceptedJobsStr = jobDisplayNames(tradingPost.getAcceptedJobTypes());
                    configManager.sendNPCSpecificMessageList(player, npcType, "job_not_accepted", 
                        "player", player.getName(), 
                        "job", configManager.getJobDisplayName(playerJob), 
                        "accepted_jobs", acceptedJobsStr);
                    return;
                }
            }
            
            String title = "§6" + tradingPost.getName() + " - アイテム取引";

            Inventory gui = Bukkit.createInventory(null, 54, title);

            TradingGUISession session = new TradingGUISession(
                player.getUniqueId(),
                tradingPost.getId(),
                gui
            );

            setupTradingGUIItems(gui, player, tradingPost, session);

            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);

            plugin.getLogger().info("取引GUIを開きました: " + player.getName() + " -> " + tradingPost.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("取引GUI作成中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("プレイヤー: " + player.getName() + ", 取引所: " + tradingPost.getName());
            player.sendMessage("§c取引画面の表示中にエラーが発生しました。");
            player.sendMessage("§c管理者にお知らせください。詳細はサーバーログを確認してください。");
        }
    }
    
    /**
     * 指定されたモードで取引GUIを開く
     * @param player プレイヤー
     * @param tradingPost 取引所
     * @param initialMode 初期モード（SELL or BUY）
     */
    public void openTradingGUIWithMode(Player player, TradingNPCManager.TradingPost tradingPost, TradingMode initialMode) {
        try {
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());

            // 職業チェック（購入・売却ともに職業一致が必須）
            boolean isMatchingJob = tradingPost.isMatchingJob(playerJob);

            if (!isMatchingJob) {
                // 別職業または無職の場合、購入・売却ともに制限
                sendJobMismatchMessage(player, tradingPost, playerJob, initialMode);
                return;
            }

            String title = "§6" + tradingPost.getName() + " - アイテム取引";

            Inventory gui = Bukkit.createInventory(null, 54, title);

            TradingGUISession session = new TradingGUISession(
                player.getUniqueId(),
                tradingPost.getId(),
                gui
            );

            // 初期モードを設定
            session.setTradingMode(initialMode);

            setupTradingGUIItems(gui, player, tradingPost, session);

            activeSessions.put(player.getUniqueId(), session);
            player.openInventory(gui);

            plugin.getLogger().info("取引GUIを開きました: " + player.getName() + " -> " + tradingPost.getName() + " (" + initialMode + "モード)");

        } catch (Exception e) {
            plugin.getLogger().severe("取引GUI作成中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("プレイヤー: " + player.getName() + ", 取引所: " + tradingPost.getName());
            player.sendMessage("§c取引画面の表示中にエラーが発生しました。");
            player.sendMessage("§c管理者にお知らせください。詳細はサーバーログを確認してください。");
        }
    }

    /**
     * 職業不一致時のエラーメッセージを送信する（購入・売却共通）
     * @param player プレイヤー
     * @param tradingPost 取引所
     * @param playerJob プレイヤーの職業（無職の場合null）
     * @param mode 操作モード（メッセージの文言切り替えに使用）
     */
    private void sendJobMismatchMessage(Player player, TradingNPCManager.TradingPost tradingPost,
                                        String playerJob, TradingMode mode) {
        String acceptedJobsStr = jobDisplayNames(tradingPost.getAcceptedJobTypes());
        String action = (mode == TradingMode.BUY) ? "購入" : "売却";

        player.sendMessage("§c" + action + "はこの取引所の対応職業のみ可能です");
        if (playerJob == null) {
            player.sendMessage("§e対応職業: §f" + acceptedJobsStr);
            player.sendMessage("§7コマンド: §f/jobs join <職業名>");
        } else {
            player.sendMessage("§eお客様の職業: §f" + configManager.getJobDisplayName(playerJob)
                + "§e、対応職業: §f" + acceptedJobsStr);
        }
    }

    /**
     * 職業の内部名（miner 等）の一覧を、表示名（鉱夫 等）を「、」でつないだ文字列にする。
     */
    private String jobDisplayNames(Collection<String> jobTypes) {
        List<String> names = new ArrayList<>();
        for (String jobType : jobTypes) {
            names.add(configManager.getJobDisplayName(jobType));
        }
        return String.join("、", names);
    }

    private void setupTradingGUIItems(Inventory gui, Player player, TradingNPCManager.TradingPost tradingPost, TradingGUISession session) {
        gui.clear();
        
        // スロット46: モード切り替えボタン
        setupModeButton(gui, session.getTradingMode(), !tradingPost.getPurchasePrices().isEmpty());
        
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        
        // モードによってアイテムリストを切り替え
        List<Map.Entry<Material, Double>> allItems;
        if (session.getTradingMode() == TradingMode.BUY) {
            allItems = new ArrayList<>(tradingPost.getPurchasePrices().entrySet());
        } else {
            allItems = new ArrayList<>(tradingPost.getItemPrices().entrySet());
        }
        
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
            double price = entry.getValue();
            
            ItemStack displayItem;
            if (session.getTradingMode() == TradingMode.BUY) {
                // 購入モード（対応職業のみ購入可能なため割増は適用しない）
                displayItem = createPurchaseItem(material, price);
            } else {
                // 売却モード
                // 職業ボーナスは職業専用取引所でのみ適用（総合取引所では素の価格）
                String effectiveJob = tradingPost.isGeneralStore() ? null : playerJob;
                double finalPrice = tradePriceManager.calculateFinalPrice(
                    material.toString().toLowerCase(),
                    effectiveJob,
                    price
                );

                // 木こりが原木を見る場合、GUI表示にも木こりボーナスを適用（職業専用取引所のみ）
                if (!tradingPost.isGeneralStore() && "woodcutter".equals(playerJob) && isLogItem(material)) {
                    finalPrice *= 2.0;
                }

                int playerAmount = countPlayerItems(player, material);
                displayItem = createTradingItem(material, price, finalPrice, playerAmount, playerJob);
            }
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
                "§f職業: §e" + (playerJob != null ? configManager.getJobDisplayName(playerJob) : "無職"),
                "§f職業ボーナス: §a" + ((playerJob != null && !tradingPost.isGeneralStore()) ? String.format("%.0f%%", (configManager.getJobPriceMultiplier(playerJob) - 1.0) * 100) : "なし"),
                "",
                "§7アイテムをクリックして売却"
            )
        );
        gui.setItem(47, infoItem);
        
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
            // 色違いブロックの代表色は「色不問」であることを名称・説明で明示する
            // 表示名は付けない。付けなければクライアントが自分の言語の名前で表示する
            //（内部名を整形した "Wooden Pickaxe" のような英語表示を避ける）
            boolean isColorBase = org.tofu.tofunomics.util.BlockNormalizer.isColorVariantBase(material);

            List<String> lore = new ArrayList<>();

            if (isColorBase) {
                lore.add("§b※ 色不問: 色違い(全16色)も同価格で売却できます");
                lore.add("");
            }

            // 原木の場合は10個分の価格を表示
            int displayMultiplier = isLogItem(material) ? 10 : 1;
            String unitSuffix = displayMultiplier > 1 ? " (" + displayMultiplier + "個)" : "";
            
            double displayBasePrice = basePrice * displayMultiplier;
            double displayFinalPrice = finalPrice * displayMultiplier;

            lore.add("§f基本価格: §e" + currencyConverter.formatCurrency(displayBasePrice) + unitSuffix);
            
            if (Math.abs(finalPrice - basePrice) > 0.01) {
                double bonusPercent = ((finalPrice / basePrice) - 1.0) * 100;
                lore.add("§f職業価格: §a" + currencyConverter.formatCurrency(displayFinalPrice) + unitSuffix +
                        " §7(+" + String.format("%.1f", bonusPercent) + "%)");
            }
            
            lore.add("§f所持数: §b" + playerAmount + "個");
            
            if (playerAmount > 0) {
                double totalValue = finalPrice * playerAmount;
                lore.add("§f合計価値: §a" + currencyConverter.formatCurrency(totalValue));
                lore.add("");
                
                // まとめ売りアイテム（displayMultiplier > 1）の場合は表記を変更
                if (displayMultiplier > 1) {
                    lore.add("§e左クリック: §f" + displayMultiplier + "個売却");
                    lore.add("§e右クリック: §f" + (displayMultiplier * 2) + "個売却");
                } else {
                    lore.add("§e左クリック: §f1個売却");
                    lore.add("§e右クリック: §f10個売却");
                }
                lore.add("§eシフト+クリック: §f全て売却");
            } else {
                lore.add("§c売却可能なアイテムがありません");
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createPurchaseItem(Material material, double price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 表示名は付けない（クライアントの翻訳名で表示される）
            List<String> lore = new ArrayList<>();
            lore.add("§f購入価格: §e" + currencyConverter.formatCurrency(price));
            lore.add("");
            lore.add("§e左クリック: §f1個購入");
            lore.add("§e右クリック: §f16個購入");
            lore.add("§eシフト+クリック: §f64個購入");

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
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && matchesSellable(item.getType(), material)
                    && isSellableStack(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * インベントリ内アイテムが、カタログ上の品目として売却対象に一致するか判定する。
     * 色違い代表色の品目に対しては、全16色の同ファミリーブロックを一致扱いにする。
     */
    private boolean matchesSellable(Material inInventory, Material catalogMaterial) {
        if (inInventory == catalogMaterial) {
            return true;
        }
        return org.tofu.tofunomics.util.BlockNormalizer.normalizeColorVariant(inInventory) == catalogMaterial;
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
     * モード切り替えボタンを設定
     */
    private void setupModeButton(Inventory gui, TradingMode currentMode, boolean purchaseEnabled) {
        if (!purchaseEnabled) {
            // 購入機能が無効な場合はボタンを表示しない
            return;
        }
        
        ItemStack modeButton;
        if (currentMode == TradingMode.SELL) {
            // 現在売却モード -> 購入モードへ切り替えるボタン
            modeButton = createGUIItem(
                TradingMode.BUY.getIcon(),
                "§e§l購入モードに切り替え",
                Arrays.asList(
                    "§7現在: " + TradingMode.SELL.getDisplayName(),
                    "§7クリックで購入モードに変更"
                )
            );
        } else {
            // 現在購入モード -> 売却モードへ切り替えるボタン
            modeButton = createGUIItem(
                TradingMode.SELL.getIcon(),
                "§a§l売却モードに切り替え",
                Arrays.asList(
                    "§7現在: " + TradingMode.BUY.getDisplayName(),
                    "§7クリックで売却モードに変更"
                )
            );
        }
        gui.setItem(46, modeButton);
    }
    
    /**
     * カテゴリボタンを設定
     */
    private void setupCategoryButtons(Inventory gui, ItemCategory currentCategory) {
        int slot = 0; // 上段(スロット0-8)をカテゴリボタン専用に使用
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
        // 各アイテムはちょうど1つのカテゴリに分類される（重複・無分類なし）
        return classifyCategory(material) == category;
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

    /**
     * アイテムが通貨（TofuCoinまたはTofuGold）かどうかを判定
     * 売却処理で通貨アイテムを除外するために使用
     */
    private boolean isCurrencyItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        return currencyConverter.getItemManager().isCurrencyItem(item);
    }
    
    /**
     * アイテムをちょうど1つのカテゴリに分類する。
     * 上から順に最初に一致したカテゴリを返すため、重複表示・無分類が発生しない。
     * どれにも当てはまらないアイテムは最終的に MATERIALS（素材/その他）へ集約される。
     */
    private ItemCategory classifyCategory(Material material) {
        String n = material.toString().toLowerCase();

        // 1) 製作品（道具・武器・防具）… 鉱物より先に判定し diamond_sword 等の重複を防ぐ
        if (containsAny(n, "sword", "pickaxe", "_axe", "shovel", "_hoe", "helmet",
                "chestplate", "leggings", "boots", "bow", "crossbow", "shield",
                "shears", "fishing_rod", "flint_and_steel", "elytra")) {
            return ItemCategory.CRAFTING;
        }

        // 2) 海産物（本物の魚介のみ。prismarine 建材や fishing_rod は除外済み）
        if (containsAny(n, "cod", "salmon", "tropical_fish", "pufferfish", "kelp",
                "seagrass", "nautilus", "heart_of_the_sea", "sea_pickle")) {
            return ItemCategory.FISHING;
        }

        // 3) 鉱物（原石・インゴット・宝石・ストレージブロック）… 建材より先に判定し
        //    deepslate_*_ore 等の鉱石が建材に吸われるのを防ぐ
        if (containsAny(n, "ore", "ingot", "coal", "diamond", "emerald", "redstone",
                "lapis", "amethyst_shard", "netherite_scrap", "ancient_debris")
                || n.startsWith("raw_")
                || containsAny(n, "iron_block", "gold_block", "diamond_block",
                    "emerald_block", "coal_block", "redstone_block", "netherite_block")) {
            return ItemCategory.MINING;
        }

        // 4) 建材（石材・レンガ・ガラス・コンクリ・羊毛・銅/水晶ブロック等）
        //    ※ "stone" を素朴に部分一致させると redstone/glowstone を巻き込むため
        //      ブロック名は具体トークン＋単体 "stone" の完全一致で判定する
        if (containsAny(n, "brick", "deepslate", "concrete", "terracotta", "_wool",
                "carpet", "glass", "prismarine", "quartz", "sandstone", "blackstone", "purpur",
                "sea_lantern", "copper_block", "cut_copper", "oxidized_copper", "calcite",
                "tuff", "dripstone", "amethyst_block", "obsidian", "cobblestone",
                "end_stone", "cobbled", "smooth_", "polished", "chiseled", "sponge",
                "anvil", "bookshelf", "brewing_stand", "enchanting_table", "hay_block",
                "nether_wart_block")
                || n.equals("stone")) {
            return ItemCategory.BUILDING;
        }

        // 5) 木材
        if (containsAny(n, "_log", "planks", "_stem", "_wood", "stick", "bamboo",
                "_sapling", "propagule")) {
            return ItemCategory.LOGGING;
        }

        // 6) 農作物・植物（作物・花・きのこ・葉）
        if (containsAny(n, "wheat", "potato", "carrot", "beetroot", "pumpkin", "melon",
                "apple", "bread", "sugar", "cocoa", "nether_wart", "glow_berries",
                "lily_pad", "poppy", "dandelion", "allium", "orchid", "cornflower",
                "sunflower", "rose", "tulip", "lilac", "peony", "mushroom")) {
            return ItemCategory.FARMING;
        }

        // 7) 食料・モブドロップ（肉類・モブ素材）
        if (containsAny(n, "beef", "chicken", "porkchop", "mutton", "rabbit", "leather",
                "feather", "bone", "egg", "milk", "honey", "slime", "ink_sac", "turtle",
                "string", "rotten_flesh", "gunpowder", "spider_eye", "blaze_rod",
                "ender_pearl", "phantom_membrane", "ghast_tear", "scute", "shulker_shell",
                "dragon_breath")) {
            return ItemCategory.FOOD;
        }

        // 8) 素材・その他（粉・塵・醸造・雑貨）= フォールバック
        return ItemCategory.MATERIALS;
    }

    /** いずれかの部分文字列を含むか判定するヘルパー */
    private boolean containsAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private void fillEmptySlots(Inventory gui) {
        // 装飾が無効な場合は枠を付けない
        if (!configManager.isGuiDecorationEnabled()) {
            return;
        }
        // 取引テーマ: 緑ガラス
        ItemStack glassPane = createGUIItem(Material.LIME_STAINED_GLASS_PANE, "§r", Collections.emptyList());

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
        
        // 手持ち側のクリックではボタンを反応させない
        if (!GuiSafety.isTopSlot(event.getRawSlot(), session.getInventory().getSize())) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        try {
            handleTradingGUIClick(player, session, event.getRawSlot(), event.getClick(), clickedItem);
        } catch (Exception e) {
            plugin.getLogger().severe("取引GUIクリック処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.trading.action_error"));
        }
    }
    
    private void handleTradingGUIClick(Player player, TradingGUISession session, int slot, 
                                     org.bukkit.event.inventory.ClickType clickType, ItemStack clickedItem) {
        
        TradingNPCManager.TradingPost tradingPost = tradingNPCManager.getTradingPost(session.getTradingPostId());
        if (tradingPost == null) {
            player.sendMessage(configManager.getMessage("npc.trading.post_not_found"));
            return;
        }
        
        // モード切り替えボタン処理 (スロット46)
        if (slot == 46) {
            // 別職業・無職は購入・売却ともに不可のため、モード切替自体をブロック
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());
            if (!tradingPost.isMatchingJob(playerJob)) {
                TradingMode targetMode = (session.getTradingMode() == TradingMode.SELL)
                    ? TradingMode.BUY : TradingMode.SELL;
                sendJobMismatchMessage(player, tradingPost, playerJob, targetMode);
                return;
            }

            // モード切り替え
            if (session.getTradingMode() == TradingMode.SELL) {
                session.setTradingMode(TradingMode.BUY);
            } else {
                session.setTradingMode(TradingMode.SELL);
            }
            session.setCurrentPage(0); // ページをリセット
            setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
            return;
        }
        
        // カテゴリボタン処理 (スロット0-8)
        if (slot >= 0 && slot <= 8) {
            ItemCategory[] categories = ItemCategory.values();
            int categoryIndex = slot;
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
                // アイテム取引処理 (スロット18-44の範囲)
                if (isItemSlot(slot)) {
                    Material material = clickedItem.getType();
                    if (session.getTradingMode() == TradingMode.SELL) {
                        // 売却モード
                        if (tradingPost.getItemPrice(material) > 0) {
                            handleItemSell(player, tradingPost, material, clickType);
                            setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                        }
                    } else {
                        // 購入モード
                        if (tradingPost.getPurchasePrice(material) > 0) {
                            handleItemPurchase(player, tradingPost, material, clickType);
                            setupTradingGUIItems(session.getInventory(), player, tradingPost, session);
                        }
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
        
        // まとめ売りアイテムかどうかを判定
        boolean isBulkItem = isLogItem(material);
        
        switch (clickType) {
            case LEFT:
                sellAmount = isBulkItem ? 10 : 1;
                break;
            case RIGHT:
                sellAmount = isBulkItem ? 20 : 10;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                sellAmount = Integer.MAX_VALUE;
                break;
            default:
                return;
        }
        
        // 売却時の職業チェック
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        if (!tradingPost.acceptsJobForSale(playerJob)) {
            sendJobMismatchMessage(player, tradingPost, playerJob, TradingMode.SELL);
            return;
        }

        sellSelectedSlots(player, tradingPost,
            item -> matchesSellable(item.getType(), material) && isSellableStack(item),
            sellAmount, false);
    }

    /**
     * 取引所で売ってよいスタックかどうか（通貨と NPC 購入品は売れない）。
     */
    private boolean isSellableStack(ItemStack item) {
        return item != null && !isCurrencyItem(item) && !NPCPurchaseMarker.isMarked(plugin, item);
    }

    /**
     * 収納 36 枠から条件に合う品を選んで売却する（個別売却・全売却の共通処理）。
     *
     * 売る品は「どのマスから何個」という形で決め、同じマスから消す。
     * 装備中・オフハンドの品は対象にしない。
     */
    private void sellSelectedSlots(Player player, TradingNPCManager.TradingPost tradingPost,
                                   java.util.function.Predicate<ItemStack> sellable, int limit, boolean sellAll) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        Map<Integer, Integer> selected = GuiSafety.selectSellSlots(inventory.getStorageContents(), sellable, limit);

        if (selected.isEmpty()) {
            player.sendMessage(configManager.getMessage(
                sellAll ? "npc.trading.no_sellable_items" : "npc.trading.no_items_to_sell"));
            return;
        }

        // 先にインベントリからアイテムを削除（ロールバック用に記録）
        // 金塊が入りきらない場合は口座へ自動入金されるため、事前のスペースチェックは行わない
        List<ItemStack> itemsToSell = new ArrayList<>();
        Map<Integer, ItemStack> removedItems = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : selected.entrySet()) {
            int slot = entry.getKey();
            int takeAmount = entry.getValue();
            ItemStack item = inventory.getItem(slot);
            if (item == null) {
                continue;
            }

            // バックアップ
            removedItems.put(slot, item.clone());

            ItemStack sellItem = item.clone();
            sellItem.setAmount(takeAmount);
            itemsToSell.add(sellItem);

            // 削除（0個になる場合はスロットをnullに設定）
            if (item.getAmount() <= takeAmount) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - takeAmount);
                inventory.setItem(slot, item);
            }
        }

        // 売却処理を実行（満杯分は口座へフォールバックするためスペースチェックは行わない）
        TradingNPCManager.TradeResult result = tradingNPCManager.processItemSale(
            player,
            tradingPost.getNpcId(),
            itemsToSell);

        if (result.isSuccess()) {
            // 成功 - アイテムは既に削除済み
            String earnings = currencyConverter.formatCurrency(result.getTotalEarnings());
            if (sellAll) {
                player.sendMessage(configManager.getMessage("npc.trading.sell_all_success",
                    "total", earnings,
                    "count", String.valueOf(result.getSoldItems().values().stream().mapToInt(Integer::intValue).sum())));
            } else {
                player.sendMessage(configManager.getMessage("npc.trading.sale_success", "total", earnings));
            }
            // 所持枠が満杯で金塊が入りきらなかった分は口座へ入金済みであることを通知
            if (result.getBankedNuggets() > 0) {
                player.sendMessage(configManager.getMessage("npc.trading.banked_overflow",
                    "amount", result.getBankedNuggets()));
            }
        } else {
            // 失敗 - アイテムをロールバック
            for (Map.Entry<Integer, ItemStack> entry : removedItems.entrySet()) {
                inventory.setItem(entry.getKey(), entry.getValue());
            }
            player.sendMessage(result.getMessage());
        }
    }
    
    private void handleItemPurchase(Player player, TradingNPCManager.TradingPost tradingPost,
                                    Material material, org.bukkit.event.inventory.ClickType clickType) {
        int purchaseAmount;

        switch (clickType) {
            case LEFT:
                purchaseAmount = 1;
                break;
            case RIGHT:
                purchaseAmount = 16;
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                purchaseAmount = 64;
                break;
            default:
                return;
        }

        // プレイヤーの職業を取得
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());

        // 職業一致チェック（購入も売却同様に対応職業のみ可能）
        if (!tradingPost.acceptsJobForPurchase(playerJob)) {
            sendJobMismatchMessage(player, tradingPost, playerJob, TradingMode.BUY);
            return;
        }

        // 購入価格を取得（緊急モードの価格倍率を適用）
        double unitPrice = tradingPost.getEffectivePurchasePrice(material);
        if (unitPrice <= 0) {
            player.sendMessage("§cこのアイテムは購入できません");
            return;
        }

        // 合計金額を計算
        double totalPrice = unitPrice * purchaseAmount;
        
        // 所持金チェック（インベントリの金塊）
        if (!currencyConverter.canAffordWithCash(player, totalPrice)) {
            player.sendMessage("§c所持金が不足しています。必要: " + currencyConverter.formatCurrency(totalPrice));
            return;
        }
        
        // インベントリ空きチェック
        // 購入品には NPC 購入マーカーが付くので、手持ちの同じ種類の品（マーカー無し）には重ならない。
        // 実際に渡す品と同じ物で空きを数え、入りきらないなら代金を受け取る前に断る。
        ItemStack purchasedItem = NPCPurchaseMarker.mark(plugin, new ItemStack(material, purchaseAmount));
        int capacity = GuiSafety.freeCapacityFor(
            player.getInventory().getStorageContents(), purchasedItem, material.getMaxStackSize());
        if (capacity < purchaseAmount) {
            player.sendMessage("§cインベントリに空きがありません");
            return;
        }
        
        // 購入処理（インベントリから金塊を削除）
        boolean success;
        try (org.tofu.tofunomics.economy.TransactionContext.Scope scope = org.tofu.tofunomics.economy.TransactionContext.open(
                org.tofu.tofunomics.economy.TransactionType.NPC_BUY, "NPC:" + tradingPost.getName(), material.name() + " x" + purchaseAmount)) {
            success = currencyConverter.payWithCash(player, totalPrice);
        }
        if (!success) {
            player.sendMessage("§c購入処理に失敗しました");
            return;
        }
        
        // アイテムを付与
        // NPC購入マーカー付きで渡し、購入したアイテムの取引所での再売却を防止する。
        // 売却価格には職業倍率がかかるが購入価格にはかからないため、マーカーが無いと
        // 「同一NPCで購入 → 即売却」で差額を無限に得られてしまう。
        // 支払いで金塊のマスが空くことはあっても埋まることは無いが、念のため入らなかった分は足元へ落とす
        for (ItemStack notAdded : player.getInventory().addItem(purchasedItem).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), notAdded);
        }

        // メッセージ（アイテム名はクライアントの翻訳名で表示する）
        String message = "§a{item} を " + purchaseAmount + "個購入しました（" +
                          currencyConverter.formatCurrency(totalPrice) + "）";
        ItemNameText.send(player, message, "{item}", material);
    }
    
    private void handleSellAll(Player player, TradingNPCManager.TradingPost tradingPost) {
        // 売却時の職業チェック（個別売却と同じ条件）
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        if (!tradingPost.acceptsJobForSale(playerJob)) {
            sendJobMismatchMessage(player, tradingPost, playerJob, TradingMode.SELL);
            return;
        }

        // 通貨（TofuGold は種類としては金インゴット）と NPC 購入品は売らない。装備中・オフハンドも対象外
        sellSelectedSlots(player, tradingPost,
            item -> tradingPost.getItemPrice(item.getType()) > 0 && isSellableStack(item),
            Integer.MAX_VALUE, true);
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 閉じられたのがこのセッションの GUI のときだけ消す（理由は GuiSafety を参照）
        TradingGUISession session = activeSessions.get(playerId);
        if (session != null && GuiSafety.isSessionInventory(session.getInventory(), event.getInventory())) {
            activeSessions.remove(playerId);
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        TradingGUISession session = activeSessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }
        // 手持ちの品を GUI のマスへ置けないようにする
        if (GuiSafety.dragTouchesTop(event.getRawSlots(), session.getInventory().getSize())) {
            event.setCancelled(true);
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