package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.trade.TradePriceManager;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.*;

public class TradingNPCManager {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final CurrencyConverter currencyConverter;
    private final JobManager jobManager;
    private final TradePriceManager tradePriceManager;
    private final PlayerDAO playerDAO;
    
    private final Map<String, TradingPost> tradingPosts = new HashMap<>();
    
    public TradingNPCManager(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager,
                           CurrencyConverter currencyConverter, JobManager jobManager, 
                           TradePriceManager tradePriceManager, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.currencyConverter = currencyConverter;
        this.jobManager = jobManager;
        this.tradePriceManager = tradePriceManager;
        this.playerDAO = playerDAO;
    }
    
    public static class TradingPost {
        private final String id;
        private final String name;
        private final Location location;
        private final List<String> acceptedJobTypes;
        private final List<String> items; // 取引可能なアイテムリスト（空の場合は全アイテム）
        private final Map<Material, Double> itemPrices;
        private final Map<Material, Double> purchasePrices; // 購入価格マップ
        private UUID npcId; // 変更可能にしてリカバリー機能を実装
        
        // 緊急モード関連のフィールド
        private final boolean emergencyMode; // 緊急モードフラグ（24時間営業）
        private final double emergencyPriceMultiplier; // 緊急時の価格倍率
        private final List<Material> emergencyItems; // 緊急時の取扱アイテムリスト
        
        public TradingPost(String id, String name, Location location, List<String> acceptedJobTypes, 
                         List<String> items, Map<Material, Double> itemPrices, Map<Material, Double> purchasePrices, UUID npcId) {
            this(id, name, location, acceptedJobTypes, items, itemPrices, purchasePrices, npcId, 
                 false, 1.0, new ArrayList<>());
        }
        
        public TradingPost(String id, String name, Location location, List<String> acceptedJobTypes, 
                         List<String> items, Map<Material, Double> itemPrices, Map<Material, Double> purchasePrices, UUID npcId,
                         boolean emergencyMode, double emergencyPriceMultiplier, List<Material> emergencyItems) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.acceptedJobTypes = acceptedJobTypes;
            this.items = items != null ? items : new ArrayList<>();
            this.itemPrices = itemPrices;
            this.purchasePrices = purchasePrices != null ? purchasePrices : new HashMap<>();
            this.npcId = npcId;
            this.emergencyMode = emergencyMode;
            this.emergencyPriceMultiplier = emergencyPriceMultiplier;
            this.emergencyItems = emergencyItems != null ? emergencyItems : new ArrayList<>();
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public List<String> getAcceptedJobTypes() { return acceptedJobTypes; }
        public List<String> getItems() { return items; }
        public Map<Material, Double> getItemPrices() { return itemPrices; }
        public Map<Material, Double> getPurchasePrices() { return purchasePrices; }
        public UUID getNpcId() { return npcId; }
        
        // 緊急モード関連のゲッター
        public boolean isEmergencyMode() { return emergencyMode; }
        public double getEmergencyPriceMultiplier() { return emergencyPriceMultiplier; }
        public List<Material> getEmergencyItems() { return emergencyItems; }
        
        // UUID更新用のセッター（リカバリー機能）
        public void setNpcId(UUID npcId) { 
            this.npcId = npcId; 
        }
        
        /**
         * 緊急モードを考慮した実際の購入価格を取得
         * @param material アイテム
         * @return 実際の購入価格
         */
        public double getEffectivePurchasePrice(Material material) {
            double basePrice = purchasePrices.getOrDefault(material, 0.0);
            
            // 緊急モードで、緊急アイテムリストに含まれている場合は倍率を適用
            if (emergencyMode && !emergencyItems.isEmpty() && emergencyItems.contains(material)) {
                return basePrice * emergencyPriceMultiplier;
            }
            
            return basePrice;
        }
        
        /**
         * 緊急モード時に購入可能なアイテムかどうかを判定
         * @param material アイテム
         * @return 購入可能な場合true
         */
        public boolean isAvailableForPurchase(Material material) {
            // 緊急モードでない場合は通常の判定
            if (!emergencyMode) {
                return purchasePrices.containsKey(material);
            }
            
            // 緊急モードの場合、緊急アイテムリストに含まれているかチェック
            if (!emergencyItems.isEmpty()) {
                return emergencyItems.contains(material) && purchasePrices.containsKey(material);
            }
            
            // 緊急アイテムリストが空の場合は全アイテム対応
            return purchasePrices.containsKey(material);
        }
        
        public boolean acceptsJob(String jobType) {
            // 空配列または"all"が含まれている場合は全職業対応（無職も含む）
            if (acceptedJobTypes.isEmpty() || acceptedJobTypes.contains("all")) {
                return true;
            }

            // jobTypeがnull（無職）の場合、上記の条件を満たさない限りfalse
            if (jobType == null) {
                return false;
            }

            // 指定された職業が含まれているかチェック
            return acceptedJobTypes.contains(jobType);
        }

        /**
         * 総合取引所（全職業対応）かどうか。
         * 職業ボーナス（職業倍率・木こり原木2倍）を職業専用NPCに限定するための判定。
         */
        public boolean isGeneralStore() {
            return acceptedJobTypes.isEmpty() || acceptedJobTypes.contains("all");
        }

        /**
         * 売却時の職業チェック（従来の厳格な制限）
         * @param jobType プレイヤーの職業
         * @return 売却可能な場合true
         */
        public boolean acceptsJobForSale(String jobType) {
            return acceptsJob(jobType);
        }

        /**
         * 購入時の職業チェック（売却と同様に職業一致が必須）
         * @param jobType プレイヤーの職業
         * @return 購入可能な場合true
         */
        public boolean acceptsJobForPurchase(String jobType) {
            return acceptsJob(jobType);
        }

        /**
         * プレイヤーがこの取引所の対応職業かどうかをチェック
         * @param jobType プレイヤーの職業
         * @return 対応職業の場合true、別職業の場合false
         */
        public boolean isMatchingJob(String jobType) {
            return acceptsJob(jobType);
        }
        
        public double getItemPrice(Material material) {
            // 色違いブロックは色を問わず代表色の価格に統一して売却可能にする
            Material lookup = org.tofu.tofunomics.util.BlockNormalizer.normalizeColorVariant(material);
            return itemPrices.getOrDefault(lookup, 0.0);
        }
        
        public double getPurchasePrice(Material material) {
            return purchasePrices.getOrDefault(material, 0.0);
        }
    }
    
    public void initializeTradingNPCs() {
        try {
            if (!configManager.isTradingNPCEnabled()) {
                plugin.getLogger().info("取引NPCシステムは無効化されています");
                return;
            }
            
            spawnTradingNPCs();
            plugin.getLogger().info("取引NPCシステムを初期化しました");
        } catch (Exception e) {
            plugin.getLogger().severe("取引NPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void spawnTradingNPCs() {
        // 既存の取引所データをクリア
        tradingPosts.clear();

        // 現在スポーン中の全NPCを取得（座標ベースでの重複チェック用）
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        Map<String, UUID> existingNPCsByLocation = new HashMap<>();
        Map<String, String> existingNPCsNameByLocation = new HashMap<>();
        
        for (NPCManager.NPCData npc : allNPCs) {
            if ("trader".equals(npc.getNpcType())) {
                Location loc = npc.getLocation();
                String locationKey = loc.getWorld().getName() + "," + 
                                   (int)loc.getX() + "," + 
                                   (int)loc.getY() + "," + 
                                   (int)loc.getZ();
                existingNPCsByLocation.put(locationKey, npc.getEntityId());
                existingNPCsNameByLocation.put(locationKey, npc.getName());
            }
        }

        List<Map<?, ?>> tradingPostConfigs = configManager.getTradingPostConfigs();

        // config.ymlに記載されている座標を記録（警告用）
        Set<String> configLocationKeys = new HashSet<>();
        
        for (Map<?, ?> config : tradingPostConfigs) {
            try {
                String id = (String) config.get("id");
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                int x = (Integer) config.get("x");
                int y = (Integer) config.get("y");
                int z = (Integer) config.get("z");
                
                Object acceptedJobsObj = config.get("accepted_jobs");
                List<String> acceptedJobs;
                if (acceptedJobsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tempList = (List<String>) acceptedJobsObj;
                    acceptedJobs = tempList;
                } else {
                    acceptedJobs = new ArrayList<String>();
                }
                
                // アイテムリストを読み込み（未指定の場合は空リスト = 全アイテム対応）
                Object itemsObj = config.get("items");
                List<String> items;
                if (itemsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tempList = (List<String>) itemsObj;
                    items = tempList;
                } else {
                    items = new ArrayList<String>();
                }
                
                if (id == null || name == null || world == null) {
                    plugin.getLogger().warning("取引NPCの設定が不完全です: " + config);
                    continue;
                }
                
                // yaw/pitchをconfigから取得（デフォルト値: 0.0f）
                float yaw = 0.0f;
                float pitch = 0.0f;
                
                if (config.containsKey("yaw") && config.get("yaw") != null) {
                    yaw = ((Number) config.get("yaw")).floatValue();
                }
                
                if (config.containsKey("pitch") && config.get("pitch") != null) {
                    pitch = ((Number) config.get("pitch")).floatValue();
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5, yaw, pitch);
                
                // 座標キーを生成
                String locationKey = world + "," + x + "," + y + "," + z;
                configLocationKeys.add(locationKey);
                
                UUID npcId;
                Villager tradingNPC = null;
                
                // 既存NPCチェック
                if (existingNPCsByLocation.containsKey(locationKey)) {
                    // 既存NPCを再利用
                    npcId = existingNPCsByLocation.get(locationKey);
                    NPCManager.NPCData existingNPC = npcManager.getNPCData(npcId);
                    
                    if (existingNPC != null) {
                        tradingNPC = existingNPC.getEntity();

                        // 名前が変更されている場合は更新
                        if (!name.equals(existingNPC.getName())) {
                            tradingNPC.setCustomName(name);
                        }
                    } else {
                        // NPCDataが見つからない場合は新規作成
                        plugin.getLogger().warning("既存NPCのデータが見つからないため、新規作成します: " + locationKey);
                        tradingNPC = npcManager.createNPC(location, "trader", name);
                        if (tradingNPC != null) {
                            npcId = tradingNPC.getUniqueId();
                            setupTradingNPC(tradingNPC);
                        } else {
                            plugin.getLogger().severe("取引NPCの生成に失敗しました: " + name);
                            continue;
                        }
                    }
                } else {
                    // 新規作成
                    tradingNPC = npcManager.createNPC(location, "trader", name);

                    if (tradingNPC != null) {
                        npcId = tradingNPC.getUniqueId();
                        setupTradingNPC(tradingNPC);
                    } else {
                        plugin.getLogger().severe("取引NPCの生成に失敗しました: " + name);
                        continue;
                    }
                }
                
                // 取引所データを登録
                Map<Material, Double> prices = buildItemPrices(acceptedJobs, items);
                
                // 購入価格を読み込み
                    Map<String, Double> purchasePricesConfig = configManager.getTradingPostPurchasePrices(id);
                    Map<Material, Double> purchasePrices = new HashMap<>();
                    for (Map.Entry<String, Double> entry : purchasePricesConfig.entrySet()) {
                        try {
                            Material material = Material.valueOf(entry.getKey().toUpperCase());
                            purchasePrices.put(material, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("無効なMaterial名: " + entry.getKey());
                        }
                    }
                    
                    // 緊急モード設定を読み込み（configマップから直接取得）
                    boolean emergencyMode = false;
                    double emergencyPriceMultiplier = 2.0;
                    List<String> emergencyItemNames = new ArrayList<>();
                    
                    if (config.containsKey("emergency_mode") && config.get("emergency_mode") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> emergencyConfig = (Map<String, Object>) config.get("emergency_mode");
                        
                        Object enabledObj = emergencyConfig.get("enabled");
                        if (enabledObj instanceof Boolean) {
                            emergencyMode = (Boolean) enabledObj;
                        }
                        
                        Object multiplierObj = emergencyConfig.get("price_multiplier");
                        if (multiplierObj instanceof Number) {
                            emergencyPriceMultiplier = ((Number) multiplierObj).doubleValue();
                        }
                        
                        Object emergencyItemsObj = emergencyConfig.get("items");
                        if (emergencyItemsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> tempList = (List<String>) emergencyItemsObj;
                            emergencyItemNames = tempList;
                        }
                    }
                    
                    // 緊急アイテムリストをMaterialに変換
                    List<Material> emergencyItems = new ArrayList<>();
                    for (String itemName : emergencyItemNames) {
                        try {
                            Material material = Material.valueOf(itemName.toUpperCase());
                            emergencyItems.add(material);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("無効な緊急アイテム名: " + itemName);
                        }
                    }

                    TradingPost tradingPost = new TradingPost(id, name, location, acceptedJobs, items, prices, purchasePrices, npcId,
                        emergencyMode, emergencyPriceMultiplier, emergencyItems);
                tradingPosts.put(id, tradingPost);

                plugin.getLogger().info("取引所を登録しました: " + name + " [" + x + ", " + y + ", " + z + "]");

            } catch (Exception e) {
                plugin.getLogger().warning("取引NPC処理中にエラーが発生しました: " + e.getMessage());
            }
        }

        // config.ymlに記載されていないNPCを検出して警告
        int orphanedCount = 0;
        for (Map.Entry<String, UUID> entry : existingNPCsByLocation.entrySet()) {
            String locationKey = entry.getKey();
            if (!configLocationKeys.contains(locationKey)) {
                String npcName = existingNPCsNameByLocation.get(locationKey);
                UUID npcId = entry.getValue();
                plugin.getLogger().warning("警告: config.ymlに記載されていない取引NPCを発見: " + npcName + " at " + locationKey + " (UUID: " + npcId + ")");
                plugin.getLogger().warning("  このNPCを削除する場合は /npc delete コマンドを使用してください");
                orphanedCount++;
            }
        }
        
        if (orphanedCount > 0) {
            plugin.getLogger().warning("config.yml未登録の取引NPC: " + orphanedCount + "体");
        }
    }
    
    private void setupTradingNPC(Villager npc) {
        // NPCManagerのcreateNPC()で基本設定は完了しているため、
        // 追加設定のみを行う（職業はNITWITのまま維持）
        npc.setVillagerLevel(5);
    }
    
    private Map<Material, Double> buildItemPrices(List<String> acceptedJobs, List<String> allowedItems) {
        Map<Material, Double> prices = new HashMap<>();
        Map<String, Double> basePrices = configManager.getItemBasePrices();

        for (Map.Entry<String, Double> entry : basePrices.entrySet()) {
            String itemName = entry.getKey();

            // allowedItemsが指定されている場合は、そのリストに含まれるアイテムのみ処理
            if (allowedItems != null && !allowedItems.isEmpty() && !allowedItems.contains(itemName)) {
                continue;
            }

            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                double basePrice = entry.getValue();

                // 基本価格をそのまま保存（職業倍率は売却時に適用）
                prices.put(material, basePrice);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なマテリアル名です: " + itemName);
            }
        }

        return prices;
    }
    
    public boolean handleTradingNPCInteraction(Player player, UUID npcId) {
        try {
            // Step 1: NPC存在チェック
            if (!npcManager.isNPCEntity(npcId)) {
                plugin.getLogger().warning("NPCエンティティが見つかりません: " + npcId);
                return false;
            }

            // Step 2: NPCデータ取得
            NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
            if (npcData == null || !npcData.getNpcType().equals("trader")) {
                plugin.getLogger().warning("取引NPCデータが見つかりません: " + npcId);
                return false;
            }

            // Step 3: 取引所データ取得
            TradingPost tradingPost = getTradingPostByNPCId(npcId);
            if (tradingPost == null) {
                // 位置情報での検索を試みる
                tradingPost = findTradingPostByLocation(npcData.getLocation());
                if (tradingPost != null) {
                    // UUIDを更新してデータを同期
                    updateTradingPostNPCId(tradingPost, npcId);
                } else {
                    // 名前での検索を試みる
                    tradingPost = findTradingPostByName(npcData.getName());
                    if (tradingPost != null) {
                        // UUIDを更新してデータを同期
                        updateTradingPostNPCId(tradingPost, npcId);
                    } else {
                        plugin.getLogger().warning("取引所データが見つかりません: " + npcId + " (" + npcData.getName() + ")");
                        player.sendMessage("§c取引所の情報を読み込めませんでした。");
                        player.sendMessage("§e管理者に以下の情報をお伝えください:");
                        player.sendMessage("§7NPC UUID: " + npcId);
                        player.sendMessage("§7NPC名: " + npcData.getName());
                        return false;
                    }
                }
            }

            // Step 3.5: 営業時間チェック（emergency_mode対応）
            if (!isWithinTradingHours(player, tradingPost)) {
                player.sendMessage(configManager.getMessage("npc.trading.outside_hours"));
                return true;
            }

            // Step 4: 権限チェック（基本的にすべて許可）
            if (!hasPermissionToUseTradingNPC(player)) {
                player.sendMessage("§c取引NPCを利用する権限がありません。");
                return true;
            }

            // 挨拶メッセージを先に表示
            String greetingMessage = configManager.getTradingNPCGreeting(tradingPost.getName(), player.getName());
            player.sendMessage(greetingMessage);

            // 取引サービスGUIを開く
            openTradingServiceGUI(player, tradingPost, npcData);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("取引NPC処理中に例外発生: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。管理者にお知らせください。");
            return true;
        }
    }

    private boolean hasPermissionToUseTradingNPC(Player player) {
        // 無職状態でもNPCとの基本相互作用は可能とする（職業チェックメッセージ表示まで）
        // 権限チェックは実際の取引時に行う
        return true; // 基本的にすべてのプレイヤーにNPCアクセスを許可
    }
    
    /**
     * プレイヤーが木こりかどうかを判定
     */
    private boolean isWoodcutter(Player player) {
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        return "woodcutter".equals(playerJob);
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
    
    private boolean openTradingServiceGUI(Player player, TradingPost tradingPost, NPCManager.NPCData npcData) {
        try {
            // まず営業時間チェック（遅延前に実行）
            if (!isWithinTradingHours(player, tradingPost)) {
                int startHour = configManager.getTradingStartHour();
                int endHour = configManager.getTradingEndHour();
                player.sendMessage("§c申し訳ありません。営業時間は" + startHour + ":00~" + endHour + ":00です。");
                return false;
            }

            // TradingGUIインスタンスの確認
            org.tofu.tofunomics.npc.gui.TradingGUI tradingGUI = plugin.getTradingGUI();

            if (tradingGUI == null) {
                plugin.getLogger().severe("TradingGUIがnullです！初期化に失敗している可能性があります");
                player.sendMessage("§c取引システムの初期化に失敗しています。");
                player.sendMessage("§e管理者にお知らせください。");
                // フォールバック処理
                showTradingMenu(player, tradingPost);
                return false;
            }

            // 遅延してからTradingGUIを開く
            int delayTicks = configManager.getNPCGUIDelayTicks();

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // プレイヤーがまだオンラインかチェック
                if (!player.isOnline()) {
                    return;
                }

                // NPCの近くにいるかチェック
                if (!isPlayerNearNPC(player, npcData)) {
                    player.sendMessage("§cNPCから離れすぎています。");
                    return;
                }

                // 営業時間内か再チェック（念のため）
                if (!isWithinTradingHours(player, tradingPost)) {
                    int startHour = configManager.getTradingStartHour();
                    int endHour = configManager.getTradingEndHour();
                    player.sendMessage("§c申し訳ありません。営業時間は" + startHour + ":00~" + endHour + ":00です。");
                    return;
                }

                // モード選択GUIを開く
                try {
                    org.tofu.tofunomics.npc.gui.TradingModeSelectionGUI modeSelectionGUI = plugin.getTradingModeSelectionGUI();
                    if (modeSelectionGUI != null) {
                        modeSelectionGUI.openModeSelectionGUI(player, tradingPost);
                    } else {
                        // フォールバック: 直接売却モードで開く
                        plugin.getLogger().warning("TradingModeSelectionGUI が初期化されていません。直接TradingGUIを開きます。");
                        tradingGUI.openTradingGUI(player, tradingPost);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("TradingGUI表示中にエラーが発生: " + e.getMessage());
                    player.sendMessage("§c取引画面の表示中にエラーが発生しました。");
                    player.sendMessage("§e管理者にお知らせください。");
                }
            }, delayTicks);

            // 遅延実行が開始されたのでtrueを返す
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("取引GUI開起中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c取引画面の表示でエラーが発生しました。");
            player.sendMessage("§e再度お試しいただくか、管理者にお知らせください。");
            return false;
        }
    }
    
    public TradeResult processItemSale(Player player, UUID npcId, List<ItemStack> items) {
        TradingPost tradingPost = getTradingPostByNPCId(npcId);
        if (tradingPost == null) {
            return new TradeResult(false, "取引所が見つかりません", 0.0, new HashMap<>());
        }
        
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        if (!tradingPost.acceptsJob(playerJob)) {
            return new TradeResult(false, "この取引所を利用する権限がありません", 0.0, new HashMap<>());
        }
        
        Map<Material, Integer> soldItems = new HashMap<>();
        double totalEarnings = 0.0;
        
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            Material material = item.getType();
            double basePrice = tradingPost.getItemPrice(material);
            
            if (basePrice > 0) {
                int amount = item.getAmount();
                // 職業ボーナスは職業専用取引所でのみ適用（総合取引所では素のitem_prices×1.0）
                String effectiveJob = tradingPost.isGeneralStore() ? null : playerJob;
                double finalPrice = tradePriceManager.calculateFinalPrice(material.toString().toLowerCase(), effectiveJob, basePrice);

                // 木こりが原木を売る場合は価格を2倍に（職業専用取引所のみ）
                if (!tradingPost.isGeneralStore() && isWoodcutter(player) && isLogItem(material)) {
                    finalPrice *= 2.0;
                    plugin.getLogger().info("木こりボーナス適用: " + material + " の価格を2倍に");
                }
                
                // アイテム単位では切り捨てず、合計に端数を累積する
                // （安価アイテムが floor で0になり売却不能になるのを防ぐ）
                totalEarnings += finalPrice * amount;
                soldItems.put(material, soldItems.getOrDefault(material, 0) + amount);
            }
        }

        // 累積した合計を通貨換算（四捨五入）。1コイン以上で売却成立
        int payableNuggets = currencyConverter.convertBalanceToNuggets(totalEarnings);
        if (payableNuggets > 0) {
            // 入る分は金塊で受け取り、入りきらない分は口座へ自動入金（満杯でも代金を取りこぼさない）
            int bankedNuggets = currencyConverter.receiveCashWithBankFallback(player, payableNuggets);

            return new TradeResult(true, "取引が完了しました", payableNuggets, soldItems, bankedNuggets);
        } else if (!soldItems.isEmpty()) {
            // 売却対象はあったが、合計額が安すぎて1コインに満たない
            return new TradeResult(false, "売却額が少なすぎます。もう少しまとめて売却してください", 0.0, new HashMap<>());
        } else {
            return new TradeResult(false, "売却可能なアイテムがありませんでした", 0.0, new HashMap<>());
        }
    }

    public static class TradeResult {
        private final boolean success;
        private final String message;
        private final double totalEarnings;
        private final Map<Material, Integer> soldItems;
        private final int bankedNuggets; // インベントリに入りきらず口座へ入金された金塊枚数

        public TradeResult(boolean success, String message, double totalEarnings, Map<Material, Integer> soldItems) {
            this(success, message, totalEarnings, soldItems, 0);
        }

        public TradeResult(boolean success, String message, double totalEarnings, Map<Material, Integer> soldItems, int bankedNuggets) {
            this.success = success;
            this.message = message;
            this.totalEarnings = totalEarnings;
            this.soldItems = soldItems;
            this.bankedNuggets = bankedNuggets;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public double getTotalEarnings() { return totalEarnings; }
        public Map<Material, Integer> getSoldItems() { return soldItems; }
        public int getBankedNuggets() { return bankedNuggets; }
    }
    
    private TradingPost getTradingPostByNPCId(UUID npcId) {
        return tradingPosts.values().stream()
            .filter(post -> post.getNpcId().equals(npcId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 位置情報から取引所を検索（UUID不一致時のフォールバック）
     */
    private TradingPost findTradingPostByLocation(Location npcLocation) {
        double minDistance = 3.0; // 3ブロック以内を同じ位置とみなす
        
        for (TradingPost post : tradingPosts.values()) {
            Location postLocation = post.getLocation();
            if (postLocation.getWorld().equals(npcLocation.getWorld())) {
                double distance = postLocation.distance(npcLocation);
                if (distance < minDistance) {
                    plugin.getLogger().info("位置情報マッチング: 距離=" + distance + "ブロック");
                    return post;
                }
            }
        }
        return null;
    }
    
    /**
     * NPC名から取引所を検索（最終フォールバック）
     */
    private TradingPost findTradingPostByName(String npcName) {
        if (npcName == null) return null;
        
        // 完全一致を試みる
        for (TradingPost post : tradingPosts.values()) {
            if (npcName.equals(post.getName())) {
                return post;
            }
        }
        
        // 部分一致を試みる（色コードを除去して比較）
        String cleanName = npcName.replaceAll("§[0-9a-fk-or]", "");
        for (TradingPost post : tradingPosts.values()) {
            String cleanPostName = post.getName().replaceAll("§[0-9a-fk-or]", "");
            if (cleanName.equals(cleanPostName)) {
                return post;
            }
        }
        
        return null;
    }
    
    public Collection<TradingPost> getAllTradingPosts() {
        return tradingPosts.values();
    }
    
    public TradingPost getTradingPost(String id) {
        return tradingPosts.get(id);
    }
    
    public void removeTradingNPCs() {
        Collection<NPCManager.NPCData> tradingNPCs = npcManager.getNPCsByType("trader");
        for (NPCManager.NPCData npcData : tradingNPCs) {
            npcManager.removeNPC(npcData.getEntityId());
        }
        tradingPosts.clear();
        plugin.getLogger().info("全ての取引NPCを削除しました");
    }
    
    public void reloadTradingNPCs() {
        removeTradingNPCs();
        spawnTradingNPCs();
        plugin.getLogger().info("取引NPCのリロードが完了しました");
    }

    /**
     * 取引所のNPC UUIDを更新（リカバリー機能）
     */
    private void updateTradingPostNPCId(TradingPost tradingPost, UUID newNpcId) {
        tradingPost.setNpcId(newNpcId);
    }
    
    /**
     * 取引所からNPCタイプを取得（個性的なメッセージ用）
     */
    private String getNPCTypeFromTradingPost(TradingPost tradingPost) {
        String tradingPostId = tradingPost.getId();
        
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
    
    /**
     * 設定変更後に取引所データを再読み込み（NPCスポーン後の即座反映用）
     */
    public void reloadTradingPosts() {
        // 現在の設定ファイルから取引所一覧を取得
        List<Map<?, ?>> configTradingPosts = configManager.getTradingPostConfigs();
        Set<String> configNPCNames = new HashSet<>();
        
        // 設定ファイルに存在するNPC名を収集
        for (Map<?, ?> config : configTradingPosts) {
            String name = (String) config.get("name");
            if (name != null) {
                configNPCNames.add(name);
            }
        }
        
        // 既存のNPCの中で、設定ファイルに存在しないものを削除
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        List<NPCManager.NPCData> npcToRemove = new ArrayList<>();
        
        for (NPCManager.NPCData npcData : allNPCs) {
            if ("trader".equals(npcData.getNpcType()) && !configNPCNames.contains(npcData.getName())) {
                npcToRemove.add(npcData);
            }
        }

        // 削除対象のNPCを除去
        for (NPCManager.NPCData npc : npcToRemove) {
            npcManager.removeNPC(npc.getEntityId());
        }

        // tradingPostsマップを更新（既存NPCは再スポーンしない）
        updateTradingPostsFromConfig();

        plugin.getLogger().info("取引所データの再読み込みが完了しました");
    }

    
    /**
     * コマンドで生成されたNPCを即座に登録（リロードなし）
     * NPCCommand からの直接呼び出し用
     */
    public void registerTradingNPC(UUID npcId, String id, String name, Location location, List<String> acceptedJobs) {
        try {
            // アイテムリストは空（全アイテム対応）
            List<String> items = new ArrayList<>();

            // アイテム価格を構築
            Map<Material, Double> prices = buildItemPrices(acceptedJobs, items);

            // 購入価格は空（コマンドで作成時は購入不可）
            Map<Material, Double> purchasePrices = new HashMap<>();

            // TradingPostオブジェクトを作成
            TradingPost tradingPost = new TradingPost(id, name, location, acceptedJobs, items, prices, purchasePrices, npcId);

            // tradingPostsマップに登録
            tradingPosts.put(id, tradingPost);

            plugin.getLogger().info("取引NPCを登録しました: " + name);

        } catch (Exception e) {
            plugin.getLogger().severe("取引NPC即座登録中にエラーが発生: " + e.getMessage());
        }
    }
    
    /**
     * 設定ファイルからtradingPostsマップを更新（既存NPCは再スポーンしない）
     */
    private void updateTradingPostsFromConfig() {
        // 既存の取引所データをクリア
        tradingPosts.clear();

        List<Map<?, ?>> tradingPostConfigs = configManager.getTradingPostConfigs();

        for (Map<?, ?> config : tradingPostConfigs) {
            try {
                String id = (String) config.get("id");
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                int x = (Integer) config.get("x");
                int y = (Integer) config.get("y");
                int z = (Integer) config.get("z");
                
                Object acceptedJobsObj = config.get("accepted_jobs");
                List<String> acceptedJobs;
                if (acceptedJobsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tempList = (List<String>) acceptedJobsObj;
                    acceptedJobs = tempList;
                } else {
                    acceptedJobs = new ArrayList<String>();
                }
                
                // アイテムリストを読み込み（未指定の場合は空リスト = 全アイテム対応）
                Object itemsObj = config.get("items");
                List<String> items;
                if (itemsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tempList = (List<String>) itemsObj;
                    items = tempList;
                } else {
                    items = new ArrayList<String>();
                }
                
                if (id == null || name == null || world == null) {
                    plugin.getLogger().warning("取引NPCの設定が不完全です: " + config);
                    continue;
                }
                
                // yaw/pitchをconfigから取得
                float yaw = 0.0f;
                float pitch = 0.0f;
                
                if (config.containsKey("yaw") && config.get("yaw") != null) {
                    yaw = ((Number) config.get("yaw")).floatValue();
                }
                
                if (config.containsKey("pitch") && config.get("pitch") != null) {
                    pitch = ((Number) config.get("pitch")).floatValue();
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5, yaw, pitch);

                // 既存のスポーン済みNPCを名前で検索
                UUID npcId = findExistingNPCIdByName(name);
                
                if (npcId != null) {
                    // 既存NPCが見つかった場合、データのみ登録
                    // アイテムリストは上で既に読み込み済み
                    
                    Map<Material, Double> prices = buildItemPrices(acceptedJobs, items);
                    
                    // 購入価格を読み込み
                    Map<String, Double> purchasePricesConfig = configManager.getTradingPostPurchasePrices(id);
                    Map<Material, Double> purchasePrices = new HashMap<>();
                    for (Map.Entry<String, Double> entry : purchasePricesConfig.entrySet()) {
                        try {
                            Material material = Material.valueOf(entry.getKey().toUpperCase());
                            purchasePrices.put(material, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("無効なMaterial名: " + entry.getKey());
                        }
                    }
                    
                    // 緊急モード設定を読み込み（configマップから直接取得）
                    boolean emergencyMode = false;
                    double emergencyPriceMultiplier = 2.0;
                    List<String> emergencyItemNames = new ArrayList<>();
                    
                    if (config.containsKey("emergency_mode") && config.get("emergency_mode") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> emergencyConfig = (Map<String, Object>) config.get("emergency_mode");
                        
                        Object enabledObj = emergencyConfig.get("enabled");
                        if (enabledObj instanceof Boolean) {
                            emergencyMode = (Boolean) enabledObj;
                        }
                        
                        Object multiplierObj = emergencyConfig.get("price_multiplier");
                        if (multiplierObj instanceof Number) {
                            emergencyPriceMultiplier = ((Number) multiplierObj).doubleValue();
                        }
                        
                        Object emergencyItemsObj = emergencyConfig.get("items");
                        if (emergencyItemsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> tempList = (List<String>) emergencyItemsObj;
                            emergencyItemNames = tempList;
                        }
                    }
                    
                    // 緊急アイテムリストをMaterialに変換
                    List<Material> emergencyItems = new ArrayList<>();
                    for (String itemName : emergencyItemNames) {
                        try {
                            Material material = Material.valueOf(itemName.toUpperCase());
                            emergencyItems.add(material);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("無効な緊急アイテム名: " + itemName);
                        }
                    }

                    TradingPost tradingPost = new TradingPost(id, name, location, acceptedJobs, items, prices, purchasePrices, npcId,
                        emergencyMode, emergencyPriceMultiplier, emergencyItems);
                    tradingPosts.put(id, tradingPost);
                } else {
                    plugin.getLogger().warning("取引所 " + name + " に対応するNPCが見つかりません");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("取引所データ更新中にエラーが発生しました: " + e.getMessage());
            }
        }
    }
    
    /**
     * 名前から既存のNPCのUUIDを検索
     */
    private UUID findExistingNPCIdByName(String name) {
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        // 色コードを除去した検索名
        String searchName = org.bukkit.ChatColor.stripColor(name);

        for (NPCManager.NPCData npcData : allNPCs) {
            if ("trader".equals(npcData.getNpcType())) {
                // NPCの名前からも色コードを除去して比較
                String npcName = org.bukkit.ChatColor.stripColor(npcData.getName());
                if (searchName.equals(npcName)) {
                    return npcData.getEntityId();
                }
            }
        }

        return null;
    }
    
    /**
     * TradingGUIが利用できない場合のフォールバック表示メソッド
     */
    private void showTradingMenu(Player player, TradingPost tradingPost) {
        try {
            // プレイヤーの職業を取得
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());
            // 取引可能職業のチェック
            if (!tradingPost.acceptsJob(playerJob)) {
                String npcType = getNPCTypeFromTradingPost(tradingPost);
                String acceptedJobsStr = String.join(", ", tradingPost.getAcceptedJobTypes());
                configManager.sendNPCSpecificMessageList(player, npcType, "job_not_accepted", 
                    "player", player.getName(), 
                    "job", playerJob, 
                    "accepted_jobs", acceptedJobsStr);
                return;
            }
            
            // 取引可能アイテムと価格を表示
            player.sendMessage("§a=== 買取価格表 ===");
            for (Map.Entry<Material, Double> entry : tradingPost.getItemPrices().entrySet()) {
                Material material = entry.getKey();
                double price = entry.getValue();
                
                // 職業ボーナスは職業専用取引所でのみ適用
                String effectivePriceJob = tradingPost.isGeneralStore() ? null : playerJob;
                double finalPrice = Math.ceil(tradePriceManager.calculateFinalPrice(material.toString().toLowerCase(), effectivePriceJob, price));
                String formattedPrice = currencyConverter.formatCurrency(finalPrice);
                
                player.sendMessage("§f• " + material.toString().toLowerCase() + ": §a" + formattedPrice);
            }
            
            player.sendMessage("§e手持ちのアイテムを持って再度話しかけると売却できます");
            
        } catch (Exception e) {
            plugin.getLogger().severe("取引メニュー表示中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c取引情報の表示でエラーが発生しました。");
        }
    }
    
    /**
     * プレイヤーがNPCの近くにいるかどうかをチェック
     */
    private boolean isPlayerNearNPC(Player player, NPCManager.NPCData npcData) {
        try {
            // NPCエンティティを取得
            Villager npcEntity = null;
            for (Villager villager : player.getWorld().getEntitiesByClass(Villager.class)) {
                if (villager.getUniqueId().equals(npcData.getEntityId())) {
                    npcEntity = villager;
                    break;
                }
            }

            if (npcEntity == null) {
                return false; // NPCが見つからない場合
            }

            // 距離をチェック（設定可能な範囲内）
            double distance = player.getLocation().distance(npcEntity.getLocation());
            int accessRange = configManager.getNPCAccessRange();

            return distance <= accessRange;

        } catch (Exception e) {
            plugin.getLogger().warning("プレイヤーとNPCの距離チェック中にエラーが発生: " + e.getMessage());
            return false; // エラーの場合は安全のためfalseを返す
        }
    }
    
    /**
     * 取引営業時間内かどうかをチェック
     * 緊急モードのNPCは24時間営業
     */
    private boolean isWithinTradingHours(Player player, TradingPost tradingPost) {
        // 緊急モードの場合は常に営業時間内として扱う
        if (tradingPost != null && tradingPost.isEmergencyMode()) {
            return true;
        }

        if (!configManager.isTradingHoursEnabled()) {
            return true;
        }

        // 現在の時間を取得（プレイヤーがいるワールドのMinecraft時間）
        long worldTime = player.getWorld().getTime();
        // 正しいMinecraft時間計算: 0=朝6:00, 6000=正午12:00, 12000=夕方18:00, 18000=深夜0:00
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);

        int startHour = configManager.getTradingStartHour();
        int endHour = configManager.getTradingEndHour();

        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour < endHour;
        } else {
            // 日をまたぐ場合
            return currentHour >= startHour || currentHour < endHour;
        }
    }
}