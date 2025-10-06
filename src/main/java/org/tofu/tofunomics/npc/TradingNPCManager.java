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
        private final Map<Material, Double> itemPrices;
        private UUID npcId; // 変更可能にしてリカバリー機能を実装
        
        public TradingPost(String id, String name, Location location, List<String> acceptedJobTypes, 
                         Map<Material, Double> itemPrices, UUID npcId) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.acceptedJobTypes = acceptedJobTypes;
            this.itemPrices = itemPrices;
            this.npcId = npcId;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public List<String> getAcceptedJobTypes() { return acceptedJobTypes; }
        public Map<Material, Double> getItemPrices() { return itemPrices; }
        public UUID getNpcId() { return npcId; }
        
        // UUID更新用のセッター（リカバリー機能）
        public void setNpcId(UUID npcId) { 
            this.npcId = npcId; 
        }
        
        public boolean acceptsJob(String jobType) {
            // 空配列または"all"が含まれている場合は全職業対応
            return acceptedJobTypes.isEmpty() || 
                   acceptedJobTypes.contains("all") || 
                   acceptedJobTypes.contains(jobType);
        }
        
        public double getItemPrice(Material material) {
            return itemPrices.getOrDefault(material, 0.0);
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
            e.printStackTrace();
        }
    }
    
    private void spawnTradingNPCs() {
        plugin.getLogger().info("=== 取引NPC生成開始 ===");
        
        // 既存の取引所データをクリア
        tradingPosts.clear();
        plugin.getLogger().info("既存の取引所データをクリアしました");
        
        List<Map<?, ?>> tradingPostConfigs = configManager.getTradingPostConfigs();
        plugin.getLogger().info("設定から " + tradingPostConfigs.size() + " 個の取引所を読み込み");
        
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
                plugin.getLogger().info("取引NPCを生成中: " + name + " at [" + x + ", " + y + ", " + z + "] (world: " + world + ")");
                Villager tradingNPC = npcManager.createNPC(location, "trader", name);
                
                if (tradingNPC != null) {
                    setupTradingNPC(tradingNPC);
                    
                    Map<Material, Double> prices = buildItemPrices(acceptedJobs);
                    TradingPost tradingPost = new TradingPost(id, name, location, acceptedJobs, prices, tradingNPC.getUniqueId());
                    tradingPosts.put(id, tradingPost);
                    
                    plugin.getLogger().info("========================================");
                    plugin.getLogger().info("取引NPC配置成功:");
                    plugin.getLogger().info("  名前: " + name);
                    plugin.getLogger().info("  ID: " + id);
                    plugin.getLogger().info("  UUID: " + tradingNPC.getUniqueId());
                    plugin.getLogger().info("  座標: [" + x + ", " + y + ", " + z + "]");
                    plugin.getLogger().info("  ワールド: " + world);
                    plugin.getLogger().info("========================================");
                } else {
                    plugin.getLogger().severe("取引NPCの生成に失敗しました: " + name + " at [" + x + ", " + y + ", " + z + "]");
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("取引NPC生成中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void setupTradingNPC(Villager npc) {
        npc.setProfession(Villager.Profession.WEAPONSMITH);
        npc.setVillagerType(Villager.Type.PLAINS);
        npc.setVillagerLevel(5);
    }
    
    private Map<Material, Double> buildItemPrices(List<String> acceptedJobs) {
        Map<Material, Double> prices = new HashMap<>();
        Map<String, Double> basePrices = configManager.getItemBasePrices();
        
        for (Map.Entry<String, Double> entry : basePrices.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey().toUpperCase());
                double basePrice = entry.getValue();
                
                // 職業別価格調整（最高価格を採用）
                double finalPrice = basePrice;
                if (!acceptedJobs.isEmpty()) {
                    for (String jobType : acceptedJobs) {
                        double jobMultiplier = configManager.getJobPriceMultiplier(jobType);
                        double adjustedPrice = basePrice * jobMultiplier;
                        if (adjustedPrice > finalPrice) {
                            finalPrice = adjustedPrice;
                        }
                    }
                }
                
                prices.put(material, finalPrice);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なマテリアル名です: " + entry.getKey());
            }
        }
        
        return prices;
    }
    
    public boolean handleTradingNPCInteraction(Player player, UUID npcId) {
        plugin.getLogger().info("=== 取引NPC相互作用開始 ===");
        plugin.getLogger().info("プレイヤー: " + player.getName() + ", NPC ID: " + npcId);
        
        try {
            // Step 1: NPC存在チェック
            if (!npcManager.isNPCEntity(npcId)) {
                plugin.getLogger().warning("NPCエンティティが見つかりません: " + npcId);
                return false;
            }
            plugin.getLogger().info("Step 1: NPCエンティティチェック - 成功");
            
            // Step 2: NPCデータ取得
            NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
            if (npcData == null || !npcData.getNpcType().equals("trader")) {
                plugin.getLogger().warning("取引NPCデータが見つかりません: " + npcId);
                return false;
            }
            plugin.getLogger().info("Step 2: NPCデータ取得 - 成功 (" + npcData.getName() + ")");
            
            // Step 3: 取引所データ取得
            plugin.getLogger().info("=== 取引所データ検索開始 ===");
            plugin.getLogger().info("検索対象NPC UUID: " + npcId);
            plugin.getLogger().info("登録済み取引所数: " + tradingPosts.size());
            
            // デバッグ：登録済み取引所のUUID一覧
            for (Map.Entry<String, TradingPost> entry : tradingPosts.entrySet()) {
                plugin.getLogger().info("  取引所[" + entry.getKey() + "]: NPC UUID=" + entry.getValue().getNpcId());
            }
            
            TradingPost tradingPost = getTradingPostByNPCId(npcId);
            if (tradingPost == null) {
                plugin.getLogger().warning("取引所データが見つかりません: " + npcId);
                plugin.getLogger().warning("NPCの名前: " + npcData.getName());
                
                // 位置情報での検索を試みる
                tradingPost = findTradingPostByLocation(npcData.getLocation());
                if (tradingPost != null) {
                    plugin.getLogger().info("位置情報で取引所を発見: " + tradingPost.getName());
                    // UUIDを更新してデータを同期
                    updateTradingPostNPCId(tradingPost, npcId);
                } else {
                    // 名前での検索を試みる
                    tradingPost = findTradingPostByName(npcData.getName());
                    if (tradingPost != null) {
                        plugin.getLogger().info("名前で取引所を発見: " + tradingPost.getName());
                        // UUIDを更新してデータを同期
                        updateTradingPostNPCId(tradingPost, npcId);
                    } else {
                        player.sendMessage("§c取引所の情報を読み込めませんでした。");
                        player.sendMessage("§e管理者に以下の情報をお伝えください:");
                        player.sendMessage("§7NPC UUID: " + npcId);
                        player.sendMessage("§7NPC名: " + npcData.getName());
                        return false;
                    }
                }
            }
            plugin.getLogger().info("Step 3: 取引所データ取得 - 成功 (" + tradingPost.getName() + ")");
            
            // Step 4: 権限チェック（基本的にすべて許可）
            boolean hasPermission = hasPermissionToUseTradingNPC(player);
            plugin.getLogger().info("Step 4: 権限チェック - " + (hasPermission ? "許可" : "拒否"));
            
            if (!hasPermission) {
                player.sendMessage("§c取引NPCを利用する権限がありません。");
                return true;
            }
            
            // 取引サービスGUIを開く
            openTradingServiceGUI(player, tradingPost, npcData);
            
            // 挨拶メッセージ
            String greetingMessage = configManager.getTradingNPCGreeting(tradingPost.getName(), player.getName());
            player.sendMessage(greetingMessage);
            
            plugin.getLogger().info("プレイヤー " + player.getName() + " が取引NPC " + tradingPost.getName() + " と取引しました");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("=== 取引NPC処理中に例外発生 ===");
            plugin.getLogger().severe("プレイヤー: " + player.getName());
            plugin.getLogger().severe("NPC ID: " + npcId);
            plugin.getLogger().severe("例外メッセージ: " + e.getMessage());
            plugin.getLogger().severe("例外クラス: " + e.getClass().getSimpleName());
            player.sendMessage("§c処理中にエラーが発生しました。管理者にお知らせください。");
            e.printStackTrace();
            return true;
        }
    }
    
    private boolean hasPermissionToUseTradingNPC(Player player) {
        boolean hasNPCUse = player.hasPermission("tofunomics.npc.trading.use");
        boolean hasTradeBasic = player.hasPermission("tofunomics.trade.basic");
        
        plugin.getLogger().info("権限詳細チェック - プレイヤー: " + player.getName());
        plugin.getLogger().info("  tofunomics.npc.trading.use: " + hasNPCUse);
        plugin.getLogger().info("  tofunomics.trade.basic: " + hasTradeBasic);
        plugin.getLogger().info("  OP権限: " + player.isOp());
        
        // 無職状態でもNPCとの基本相互作用は可能とする（職業チェックメッセージ表示まで）
        // 権限チェックは実際の取引時に行う
        boolean result = true; // 基本的にすべてのプレイヤーにNPCアクセスを許可
        plugin.getLogger().info("  最終権限判定: " + result + " (基本アクセス許可)");
        
        return result;
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
    
    private void openTradingServiceGUI(Player player, TradingPost tradingPost, NPCManager.NPCData npcData) {
        plugin.getLogger().info("Step 5: GUI表示処理開始");
        
        try {
            // まず職業チェックを先に行う（GUIの有無に関係なく）
            String playerJob = jobManager.getPlayerJob(player.getUniqueId());
            plugin.getLogger().info("職業チェック: " + (playerJob != null ? playerJob : "無職"));
            
            // 無職の場合は即座にメッセージ表示
            if (playerJob == null) {
                plugin.getLogger().info("無職のためメッセージ表示");
                String npcType = getNPCTypeFromTradingPost(tradingPost);
                plugin.getLogger().info("NPCタイプ: " + npcType);
                
                // メッセージ送信を確実に実行
                try {
                    // まず挨拶メッセージを送信
                    configManager.sendNPCWelcomeMessage(player, npcType);
                    // その後、無職者向けメッセージを送信
                    configManager.sendNPCSpecificMessageList(player, npcType, "no_job");
                    plugin.getLogger().info("挨拶メッセージと無職者向けメッセージ送信完了");
                } catch (Exception e) {
                    plugin.getLogger().severe("メッセージ送信でエラー: " + e.getMessage());
                    // 緊急フォールバック
                    player.sendMessage("§6「いらっしゃい、お客さん！」");
                    player.sendMessage("§e「まずは何か職業に就いてからお越しください」");
                    player.sendMessage("§7コマンド: §f/jobs join <職業名>");
                }
                return;
            }
            
            // 遅延してからTradingGUIを開く（職業がある場合）
            int delayTicks = configManager.getNPCGUIDelayTicks();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // プレイヤーがまだオンラインで、NPCの近くにいて、営業時間内かチェック
                if (player.isOnline() && isPlayerNearNPC(player, npcData) && isWithinTradingHours()) {
                    org.tofu.tofunomics.npc.gui.TradingGUI tradingGUI = plugin.getTradingGUI();
                    
                    if (tradingGUI != null) {
                        plugin.getLogger().info("TradingGUIでの処理");
                        tradingGUI.openTradingGUI(player, tradingPost);
                    } else {
                        plugin.getLogger().info("TradingGUI null - フォールバック処理で価格表示");
                        showTradingMenu(player, tradingPost);
                    }
                } else {
                    if (!player.isOnline()) {
                        plugin.getLogger().info("プレイヤー " + player.getName() + " がオフラインのため、GUIを開かませんでした");
                    } else if (!isPlayerNearNPC(player, npcData)) {
                        plugin.getLogger().info("プレイヤー " + player.getName() + " がNPCから離れたため、GUIを開かませんでした");
                    } else if (!isWithinTradingHours()) {
                        plugin.getLogger().info("営業時間外のため、GUIを開かませんでした");
                        // 営業時間外メッセージを送信
                        int startHour = configManager.getTradingStartHour();
                        int endHour = configManager.getTradingEndHour();
                        player.sendMessage("§c申し訳ありません。営業時間は" + startHour + ":00~" + endHour + ":00です。");
                    }
                }
            }, delayTicks);
            

        } catch (Exception e) {
            plugin.getLogger().severe("取引GUI開起中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("エラー発生場所: TradingNPCManager.openTradingServiceGUI");
            plugin.getLogger().severe("プレイヤー: " + player.getName() + ", 取引所: " + tradingPost.getName());
            player.sendMessage("§c取引画面の表示でエラーが発生しました。");
            player.sendMessage("§e再度お試しいただくか、管理者にお知らせください。");
            e.printStackTrace();
        }
    }
    
    public TradeResult processItemSale(Player player, UUID npcId, List<ItemStack> items) {
        TradingPost tradingPost = getTradingPostByNPCId(npcId);
        if (tradingPost == null) {
            return new TradeResult(false, "取引所が見つかりません", 0.0, new HashMap<>());
        }
        
        String playerJob = jobManager.getPlayerJob(player.getUniqueId());
        if (playerJob == null || !tradingPost.acceptsJob(playerJob)) {
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
                double finalPrice = tradePriceManager.calculateFinalPrice(material.toString().toLowerCase(), playerJob, basePrice);
                
                // 木こりが原木を売る場合は価格を2倍に
                if (isWoodcutter(player) && isLogItem(material)) {
                    finalPrice *= 2.0;
                    plugin.getLogger().info("木こりボーナス適用: " + material + " の価格を2倍に");
                }
                
                double itemTotal = finalPrice * amount;
                
                totalEarnings += itemTotal;
                soldItems.put(material, soldItems.getOrDefault(material, 0) + amount);
                
                // アイテムを削除
                item.setAmount(0);
            }
        }
        
        if (totalEarnings > 0) {
            // インベントリに金塊として支払い
            if (!currencyConverter.receiveCash(player, totalEarnings)) {
                return new TradeResult(false, "インベントリに空きがありません。金塊を受け取るスペースを確保してください", 0.0, new HashMap<>());
            }
            
            return new TradeResult(true, "取引が完了しました", totalEarnings, soldItems);
        } else {
            return new TradeResult(false, "売却可能なアイテムがありませんでした", 0.0, new HashMap<>());
        }
    }
    
    public static class TradeResult {
        private final boolean success;
        private final String message;
        private final double totalEarnings;
        private final Map<Material, Integer> soldItems;
        
        public TradeResult(boolean success, String message, double totalEarnings, Map<Material, Integer> soldItems) {
            this.success = success;
            this.message = message;
            this.totalEarnings = totalEarnings;
            this.soldItems = soldItems;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public double getTotalEarnings() { return totalEarnings; }
        public Map<Material, Integer> getSoldItems() { return soldItems; }
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
        plugin.getLogger().info("取引NPCをリロードしています...");
        removeTradingNPCs();
        spawnTradingNPCs();
        plugin.getLogger().info("取引NPCのリロードが完了しました");
    }
    
    /**
     * 取引所のNPC UUIDを更新（リカバリー機能）
     */
    private void updateTradingPostNPCId(TradingPost tradingPost, UUID newNpcId) {
        UUID oldNpcId = tradingPost.getNpcId();
        tradingPost.setNpcId(newNpcId);
        plugin.getLogger().info("取引所のNPC UUIDを更新:");
        plugin.getLogger().info("  取引所: " + tradingPost.getName());
        plugin.getLogger().info("  旧UUID: " + oldNpcId);
        plugin.getLogger().info("  新UUID: " + newNpcId);
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
        plugin.getLogger().info("取引所データを再読み込みしています...");
        
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
                plugin.getLogger().info("設定ファイルから削除されたNPCを除去: " + npcData.getName());
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
     * 設定ファイルからtradingPostsマップを更新（既存NPCは再スポーンしない）
     */
    private void updateTradingPostsFromConfig() {
        plugin.getLogger().info("=== 取引所マップ更新開始 ===");
        
        // 既存の取引所データをクリア
        tradingPosts.clear();
        
        List<Map<?, ?>> tradingPostConfigs = configManager.getTradingPostConfigs();
        plugin.getLogger().info("設定から " + tradingPostConfigs.size() + " 個の取引所を読み込み");
        
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
                    Map<Material, Double> prices = buildItemPrices(acceptedJobs);
                    TradingPost tradingPost = new TradingPost(id, name, location, acceptedJobs, prices, npcId);
                    tradingPosts.put(id, tradingPost);
                    
                    plugin.getLogger().info("取引所データ登録: " + name + " (既存NPC: " + npcId + ")");
                } else {
                    plugin.getLogger().warning("取引所 " + name + " に対応するNPCが見つかりません");
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("取引所データ更新中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        plugin.getLogger().info("=== 取引所マップ更新完了: " + tradingPosts.size() + "個 ===");
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
                plugin.getLogger().info("職業不対応: " + playerJob);
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
                
                // 職業ボーナスを適用
                double finalPrice = Math.ceil(tradePriceManager.calculateFinalPrice(material.toString().toLowerCase(), playerJob, price));
                String formattedPrice = currencyConverter.formatCurrency(finalPrice);
                
                player.sendMessage("§f• " + material.toString().toLowerCase() + ": §a" + formattedPrice);
            }
            
            player.sendMessage("§e手持ちのアイテムを持って再度話しかけると売却できます");
            
        } catch (Exception e) {
            plugin.getLogger().severe("取引メニュー表示中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c取引情報の表示でエラーが発生しました。");
            e.printStackTrace();
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
     */
    private boolean isWithinTradingHours() {
        if (!configManager.isTradingHoursEnabled()) {
            return true;
        }
        
        // 現在の時間を取得（Minecraft時間）
        long worldTime = plugin.getServer().getWorlds().get(0).getTime();
        // 正しいMinecraft時間計算: 0=朝6:00, 6000=正午12:00, 12000=夕方18:00, 18000=深夜0:00
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
        
        int startHour = configManager.getTradingStartHour();
        int endHour = configManager.getTradingEndHour();
        
        plugin.getLogger().info("営業時間チェック - worldTime: " + worldTime + ", 現在時刻: " + currentHour + ":00, 営業時間: " + startHour + ":00-" + endHour + ":00");
        
        if (startHour <= endHour) {
            boolean result = currentHour >= startHour && currentHour < endHour;
            plugin.getLogger().info("営業時間判定結果: " + result);
            return result;
        } else {
            // 日をまたぐ場合
            boolean result = currentHour >= startHour || currentHour < endHour;
            plugin.getLogger().info("営業時間判定結果（日またぎ): " + result);
            return result;
        }
    }
}