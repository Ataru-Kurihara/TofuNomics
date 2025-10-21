package org.tofu.tofunomics.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.npc.BankNPCManager;
import org.tofu.tofunomics.npc.NPCManager;
import org.tofu.tofunomics.npc.TradingNPCManager;
import org.tofu.tofunomics.npc.FoodNPCManager;
import org.tofu.tofunomics.npc.ProcessingNPCManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NPCCommand implements CommandExecutor, TabCompleter {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final BankNPCManager bankNPCManager;
    private final TradingNPCManager tradingNPCManager;
    private final FoodNPCManager foodNPCManager;
    private final ProcessingNPCManager processingNPCManager;
    
    private static final String[] VALID_JOBS = {
        "miner", "woodcutter", "farmer", "fisherman",
        "blacksmith", "alchemist", "enchanter", "architect"
    };
    
    public NPCCommand(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager,
                    BankNPCManager bankNPCManager, TradingNPCManager tradingNPCManager,
                    FoodNPCManager foodNPCManager, ProcessingNPCManager processingNPCManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.bankNPCManager = bankNPCManager;
        this.tradingNPCManager = tradingNPCManager;
        this.foodNPCManager = foodNPCManager;
        this.processingNPCManager = processingNPCManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                return handleSpawnCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender, args);
            case "tp":
                return handleTpCommand(sender, args);
            case "list":
                return handleListCommand(sender, args);
            case "cleanup":
                return handleCleanupCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "info":
                return handleInfoCommand(sender, args);
            case "purge":
                return handlePurgeCommand(sender, args);
            case "restore":
                return handleRestoreCommand(sender, args);
            case "delete":
                return handleDeleteCommand(sender, args);
            case "status":
                return handleStatusCommand(sender, args);
            case "setup":
                return handleSetupCommand(sender, args);
            case "setup-location":
                return handleSetupLocationCommand(sender, args);
            case "setup-all":
                return handleSetupAllCommand(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }
    
    /**
     * NPCスポーンコマンド
     * /npc spawn trader <職業名> [NPC名] - 職業専用取引NPCをスポーン
     * /npc spawn banker [NPC名] - 銀行NPCをスポーン
     */
    private boolean handleSpawnCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc spawn <trader|banker> [職業名|NPC名]");
            return true;
        }
        
        String npcType = args[1].toLowerCase();
        Location spawnLocation = player.getLocation();
        
        switch (npcType) {
            case "trader":
                return handleSpawnTrader(player, args, spawnLocation);
            case "banker":
                return handleSpawnBanker(player, args, spawnLocation);
            case "food_merchant":
            case "food":
                return handleSpawnFoodMerchant(player, args, spawnLocation);
            case "processing":
                return handleSpawnProcessing(player, args, spawnLocation);
            default:
                sender.sendMessage("§c無効なNPCタイプです: " + npcType);
                sender.sendMessage("§7有効なタイプ: trader, banker, food_merchant, processing");
                return true;
        }
    }
    
    private boolean handleSpawnTrader(Player player, String[] args, Location spawnLocation) {
        if (args.length < 3) {
            player.sendMessage("§c使用法: /npc spawn trader <職業名|all> [NPC名]");
            player.sendMessage("§7職業: " + String.join(", ", VALID_JOBS) + ", all");
            return true;
        }
        
        String jobType = args[2].toLowerCase();
        if (!isValidJobType(jobType)) {
            player.sendMessage("§c無効な職業名です: " + jobType);
            player.sendMessage("§7有効な職業: " + String.join(", ", VALID_JOBS) + ", all");
            return true;
        }
        
        String npcName = args.length > 3 ? 
            String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : 
            ("all".equalsIgnoreCase(jobType) ? "§6総合取引所" : "§6" + configManager.getJobDisplayName(jobType) + "取引商人");
        
        // 重複チェック
        if (configManager.hasTradingPostWithName(npcName)) {
            player.sendMessage("§c同じ名前の取引NPCが既に存在します: " + npcName);
            player.sendMessage("§e既存NPCを削除してから再度スポーンするか、別の名前を使用してください。");
            player.sendMessage("§7削除コマンド: §f/npc remove " + npcName);
            return true;
        }
        
        try {
            // 取引NPCを作成
            Villager npc = npcManager.createNPC(spawnLocation, "trader", npcName);
            boolean success = (npc != null);
            
            if (success) {
                // 取引所データをconfig.ymlに自動追加
                try {
                    configManager.addTradingPost(npcName, spawnLocation, jobType);
                    
                    // メッセージ設定も自動確認・追加
                    configManager.ensureNPCMessagesExist();
                    
                    // TradingNPCManagerに即座に反映（reloadを使わず直接登録）
                    if (tradingNPCManager != null) {
                        // 取引所IDを生成（jobType_market形式）
                        String tradingPostId = ("all".equalsIgnoreCase(jobType)) ? 
                            "central_market" : jobType + "_market";
                        
                        // accepted_jobsリストを作成
                        List<String> acceptedJobs = ("all".equalsIgnoreCase(jobType)) ? 
                            Arrays.asList("all") : Arrays.asList(jobType);
                        
                        // NPCをTradingNPCManagerに即座に登録
                        tradingNPCManager.registerTradingNPC(
                            npc.getUniqueId(), 
                            tradingPostId, 
                            npcName, 
                            spawnLocation, 
                            acceptedJobs
                        );
                        
                        plugin.getLogger().info("取引NPCをTradingNPCManagerに即座に登録: " + npcName);
                    }
                    
                    String typeDesc = "all".equalsIgnoreCase(jobType) ? "全職業対応" : jobType + "専用";
                    player.sendMessage("§a" + typeDesc + "取引NPC「" + npcName + "」をスポーンし、取引所データを追加しました。");
                    player.sendMessage("§7座標: " + formatLocation(spawnLocation));
                    player.sendMessage("§e取引所データがconfig.ymlに自動追加され、即座に利用可能になりました。");
                } catch (Exception configError) {
                    plugin.getLogger().severe("取引所データの追加に失敗しました: " + configError.getMessage());
                    player.sendMessage("§a" + jobType + "専用取引NPC「" + npcName + "」をスポーンしました。");
                    player.sendMessage("§c取引所データの自動追加に失敗しました。手動でconfig.ymlを編集してください。");
                    player.sendMessage("§7座標: " + formatLocation(spawnLocation));
                }
            } else {
                player.sendMessage("§c取引NPCのスポーンに失敗しました。");
            }
            
            return true;
            
        } catch (Exception e) {
            player.sendMessage("§c取引NPCのスポーン中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("取引NPCスポーンエラー: " + e.getMessage());
            return true;
        }
    }
    
    private boolean handleSpawnBanker(Player player, String[] args, Location spawnLocation) {
        String npcName = args.length > 2 ? 
            String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : 
            "§6銀行員";
            
        try {
            // 銀行NPCを作成
            Villager npc = npcManager.createNPC(spawnLocation, "banker", npcName);
            boolean success = (npc != null);
            
            if (success) {
                // 銀行NPCデータをconfig.ymlに自動追加
                try {
                    configManager.addBankNPC(npcName, spawnLocation, "bank");
                    
                    player.sendMessage("§a銀行NPC「" + npcName + "」をスポーンし、データを追加しました。");
                    player.sendMessage("§7座標: " + formatLocation(spawnLocation));
                    player.sendMessage("§e銀行NPCデータがconfig.ymlに自動追加されました。");
                } catch (Exception configError) {
                    plugin.getLogger().severe("銀行NPCデータの追加に失敗しました: " + configError.getMessage());
                    player.sendMessage("§a銀行NPC「" + npcName + "」をスポーンしました。");
                    player.sendMessage("§c銀行NPCデータの自動追加に失敗しました。手動でconfig.ymlを編集してください。");
                    player.sendMessage("§7座標: " + formatLocation(spawnLocation));
                }
            } else {
                player.sendMessage("§c銀行NPCのスポーンに失敗しました。");
            }
            
            return true;
            
        } catch (Exception e) {
            player.sendMessage("§c銀行NPCのスポーン中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("銀行NPCスポーンエラー: " + e.getMessage());
            return true;
        }
    }
    
    private boolean handleSpawnFoodMerchant(Player player, String[] args, Location spawnLocation) {
        // 使用法: /npc spawn food_merchant [NPC名] [タイプ]
        String npcName;
        String npcType;
        
        if (args.length >= 4) {
            // 名前とタイプの両方が指定された場合
            npcName = args[2];
            npcType = args[3];
        } else if (args.length >= 3) {
            // 名前のみが指定された場合
            npcName = args[2];
            npcType = "general_store"; // デフォルト
        } else {
            // 何も指定されていない場合
            npcName = "§6食料品商人";
            npcType = "general_store";
        }
        
        try {
            plugin.getLogger().info("=== 食料NPCスポーン処理開始 ===");
            plugin.getLogger().info("プレイヤー: " + player.getName() + ", NPC名: " + npcName + ", タイプ: " + npcType);
            
            // 食料NPCを生成
            org.bukkit.entity.Villager foodNPC = npcManager.createNPC(spawnLocation, "food_merchant", npcName);
            
            if (foodNPC != null) {
                plugin.getLogger().info("NPCManagerでの作成成功: UUID=" + foodNPC.getUniqueId());
                
                // FoodNPCManagerに登録
                if (foodNPCManager != null) {
                    plugin.getLogger().info("FoodNPCManagerへの登録を開始");
                    foodNPCManager.registerFoodNPC(foodNPC, npcName, npcType);
                    
                    // 食料NPCデータをconfig.ymlに自動追加
                    try {
                        configManager.addFoodNPC(npcName, spawnLocation, npcType);
                        
                        player.sendMessage("§a食料NPCを生成し、データを追加しました！");
                        player.sendMessage("§7タイプ: " + npcType + " (" + configManager.getFoodNPCTypeName(npcType) + ")");
                        player.sendMessage("§7名前: " + npcName);
                        player.sendMessage("§7場所: " + formatLocation(spawnLocation));
                        player.sendMessage("§7UUID: " + foodNPC.getUniqueId());
                        player.sendMessage("§e食料NPCデータがconfig.ymlに自動追加されました。");
                    } catch (Exception configError) {
                        plugin.getLogger().severe("食料NPCデータの追加に失敗しました: " + configError.getMessage());
                        player.sendMessage("§a食料NPCを生成しました！");
                        player.sendMessage("§c食料NPCデータの自動追加に失敗しました。手動でconfig.ymlを編集してください。");
                        player.sendMessage("§7タイプ: " + npcType + " (" + configManager.getFoodNPCTypeName(npcType) + ")");
                        player.sendMessage("§7名前: " + npcName);
                        player.sendMessage("§7場所: " + formatLocation(spawnLocation));
                        player.sendMessage("§7UUID: " + foodNPC.getUniqueId());
                    }
                    int startHour = configManager.getFoodNPCStartHour();
                    int endHour = configManager.getFoodNPCEndHour();
                    player.sendMessage(String.format("§e営業時間: %d:00-%d:00", startHour, endHour));
                    player.sendMessage("§e§l右クリックで取引開始");
                    
                    plugin.getLogger().info("=== 食料NPCスポーン処理完了 ===");
                } else {
                    player.sendMessage("§c警告: 食料NPCは生成されましたが、システムへの登録に失敗しました");
                    plugin.getLogger().warning("FoodNPCManagerがnullのため、食料NPCの登録に失敗しました");
                }
                
                return true;
            } else {
                player.sendMessage("§c食料NPCの生成に失敗しました");
                return true;
            }
        } catch (Exception e) {
            player.sendMessage("§c食料NPC生成中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("食料NPC生成エラー: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    private boolean handleSpawnProcessing(Player player, String[] args, Location spawnLocation) {
        String npcName = args.length > 2 ? 
            String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : 
            "§6木材加工職人";
            
        try {
            plugin.getLogger().info("=== 加工NPCスポーン処理開始 ===");
            plugin.getLogger().info("プレイヤー: " + player.getName() + ", NPC名: " + npcName);
            
            // 加工NPCを生成
            Villager processingNPC = npcManager.createNPC(spawnLocation, "processing", npcName);
            
            if (processingNPC != null) {
                plugin.getLogger().info("NPCManagerでの作成成功: UUID=" + processingNPC.getUniqueId());
                
                // 加工NPCデータをconfig.ymlに自動追加
                try {
                    configManager.addProcessingNPC(npcName, spawnLocation);
                    
                    // ProcessingNPCManagerに即座に反映（reloadを使わず直接登録）
                    if (processingNPCManager != null) {
                        // 加工所IDを生成（processing_station形式）
                        String stationId = "processing_station_" + System.currentTimeMillis();
                        
                        // NPCをProcessingNPCManagerに即座に登録
                        processingNPCManager.registerProcessingNPC(
                            processingNPC.getUniqueId(),
                            stationId,
                            npcName,
                            spawnLocation
                        );
                        
                        plugin.getLogger().info("加工NPCをProcessingNPCManagerに即座に登録: " + npcName);
                    }
                    
                    player.sendMessage("§a加工NPCを生成し、データを追加しました！");
                    player.sendMessage("§7名前: " + npcName);
                    player.sendMessage("§7場所: " + formatLocation(spawnLocation));
                    player.sendMessage("§7UUID: " + processingNPC.getUniqueId());
                    player.sendMessage("§e加工NPCデータがconfig.ymlに自動追加されました。");
                    player.sendMessage("§e§l右クリックで加工メニューを開く");
                } catch (Exception configError) {
                    plugin.getLogger().severe("加工NPCデータの追加に失敗しました: " + configError.getMessage());
                    player.sendMessage("§a加工NPCを生成しました！");
                    player.sendMessage("§c加工NPCデータの自動追加に失敗しました。手動でconfig.ymlを編集してください。");
                    player.sendMessage("§7名前: " + npcName);
                    player.sendMessage("§7場所: " + formatLocation(spawnLocation));
                    player.sendMessage("§7UUID: " + processingNPC.getUniqueId());
                }
                
                plugin.getLogger().info("=== 加工NPCスポーン処理完了 ===");
                return true;
            } else {
                player.sendMessage("§c加工NPCの生成に失敗しました");
                return true;
            }
        } catch (Exception e) {
            player.sendMessage("§c加工NPC生成中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("加工NPC生成エラー: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    /**
     * NPC削除コマンド
     */
    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc remove <NPC名>");
            return true;
        }
        
        String npcName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // NPCを名前で検索して削除
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        NPCManager.NPCData targetNPC = null;
        
        for (NPCManager.NPCData npcData : allNPCs) {
            if (npcData.getName().contains(npcName) || npcData.getName().equals(npcName)) {
                targetNPC = npcData;
                break;
            }
        }
        
        if (targetNPC == null) {
            sender.sendMessage("§c指定された名前のNPCが見つかりません: " + npcName);
            return true;
        }
        
        try {
            String npcNameToRemove = targetNPC.getName();
            String npcTypeToRemove = targetNPC.getNpcType();
            
            // NPCエンティティを削除
            npcManager.removeNPC(targetNPC.getEntityId());
            
            // config.ymlに削除フラグを設定（論理削除）
            configManager.markNPCAsDeleted(npcNameToRemove, npcTypeToRemove);
            
            // TradingNPCManagerの更新（必要に応じて）
            if ("trader".equals(npcTypeToRemove) && tradingNPCManager != null) {
                tradingNPCManager.reloadTradingPosts();
            }
            
            sender.sendMessage("§aNPC「" + npcNameToRemove + "」を削除しました。");
            sender.sendMessage("§7プラグインリロード後も削除状態が保持されます。");
        } catch (Exception e) {
            sender.sendMessage("§cNPC削除中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPC削除エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * NPCテレポートコマンド
     */
    private boolean handleTpCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc tp <NPC名>");
            return true;
        }
        
        Player player = (Player) sender;
        String npcName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // NPCを名前で検索
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        NPCManager.NPCData targetNPC = null;
        
        for (NPCManager.NPCData npcData : allNPCs) {
            if (npcData.getName().contains(npcName) || npcData.getName().equals(npcName)) {
                targetNPC = npcData;
                break;
            }
        }
        
        if (targetNPC == null) {
            player.sendMessage("§c指定された名前のNPCが見つかりません: " + npcName);
            return true;
        }
        
        try {
            Location npcLocation = targetNPC.getLocation();
            player.teleport(npcLocation);
            player.sendMessage("§aNPC「" + targetNPC.getName() + "」の場所にテレポートしました。");
        } catch (Exception e) {
            player.sendMessage("§cテレポート中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPCテレポートエラー: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        // --index オプションの確認
        boolean showIndex = args.length > 1 && "--index".equals(args[1]);
        
        sender.sendMessage("§6=== システムNPC一覧" + (showIndex ? "（番号付き）" : "") + " ===");
        
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        if (allNPCs.isEmpty()) {
            sender.sendMessage("§e現在、スポーンしているNPCはありません。");
            return true;
        }
        
        int bankNPCs = 0;
        int tradingNPCs = 0;
        int foodNPCs = 0;
        int index = 1;
        
        for (NPCManager.NPCData npcData : allNPCs) {
            String status = npcData.getEntity().isValid() ? "§a正常" : "§c無効";
            
            if (showIndex) {
                sender.sendMessage(String.format("§f[%d] %s §7(%s) - %s", 
                    index, npcData.getName(), npcData.getNpcType(), status));
            } else {
                sender.sendMessage(String.format("§f• %s §7(%s) - %s", 
                    npcData.getName(), npcData.getNpcType(), status));
            }
                
            if ("banker".equals(npcData.getNpcType())) {
                bankNPCs++;
            } else if ("trader".equals(npcData.getNpcType())) {
                tradingNPCs++;
            } else if ("food_merchant".equals(npcData.getNpcType())) {
                foodNPCs++;
            }
            
            index++;
        }
        
        sender.sendMessage("§e合計: §f" + allNPCs.size() + " §e個 (銀行: §f" + bankNPCs + "§e, 取引: §f" + tradingNPCs + "§e, 食料: §f" + foodNPCs + "§e)");
        
        if (showIndex) {
            sender.sendMessage("§7ヒント: §e/npc delete <番号> §7で番号指定削除ができます");
        } else {
            sender.sendMessage("§7ヒント: §e/npc list --index §7で番号付き表示、§e/npc delete m1,w1,f1等 §7で簡単削除");
        }
        
        return true;
    }
    
    private boolean handleCleanupCommand(CommandSender sender) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        sender.sendMessage("§c警告: 全NPCとその設定データを完全削除します（復元不可）");
        sender.sendMessage("§7個別削除は /npc remove <NPC名> を使用してください");
        sender.sendMessage("§e既存システムNPCを削除中...");
        
        try {
            // NPCエンティティを削除
            npcManager.removeExistingSystemNPCs();
            
            // config.ymlからすべての取引所データも削除
            configManager.clearAllTradingPosts();
            
            // TradingNPCManagerも更新
            if (tradingNPCManager != null) {
                tradingNPCManager.reloadTradingPosts();
            }
            
            sender.sendMessage("§a全NPCと設定データの完全削除が完了しました。");
            sender.sendMessage("§c注意: この操作は復元できません。");
        } catch (Exception e) {
            sender.sendMessage("§cNPC削除中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPC削除中にエラーが発生: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        sender.sendMessage("§eNPCデータをリロード中（既存NPCは保持）...");
        
        try {
            // 既存NPCを削除せずに、内部データのみリロード
            plugin.getLogger().info("=== NPCデータリロード開始 ===");
            
            int reloadedCount = 0;
            
            // 取引NPCデータをリロード
            if (tradingNPCManager != null) {
                tradingNPCManager.reloadTradingPosts();
                reloadedCount++;
                sender.sendMessage("§a取引NPCデータをリロードしました");
            }
            
            // 加工NPCデータをリロード
            if (processingNPCManager != null) {
                processingNPCManager.reloadProcessingStations();
                reloadedCount++;
                sender.sendMessage("§a加工NPCデータをリロードしました");
            }
            
            plugin.getLogger().info("=== NPCデータリロード完了 (" + reloadedCount + "種類) ===");
            
            sender.sendMessage("§aNPCデータのリロードが完了しました。");
            sender.sendMessage("§7既存のNPCは保持され、config.ymlの設定と照合されました。");
            sender.sendMessage("§e詳細はサーバーログを確認してください。");
            sender.sendMessage("§7ヒント: NPCが見つからない場合は、同じ位置に再作成するか、config.ymlから削除してください。");
            
        } catch (Exception e) {
            sender.sendMessage("§cNPCリロード中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPCリロード中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        
        Player player = (Player) sender;
        sender.sendMessage("§6=== NPC システム情報 ===");
        sender.sendMessage("§eシステム有効: §f" + (configManager.isNPCSystemEnabled() ? "有効" : "無効"));
        sender.sendMessage("§e管理NPC数: §f" + npcManager.getAllNPCs().size() + " 個");
        
        // 最寄りのNPCを探す
        double minDistance = Double.MAX_VALUE;
        NPCManager.NPCData nearestNPC = null;
        
        for (NPCManager.NPCData npcData : npcManager.getAllNPCs()) {
            double distance = player.getLocation().distance(npcData.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearestNPC = npcData;
            }
        }
        
        if (nearestNPC != null) {
            sender.sendMessage(String.format("§e最寄りNPC: §f%s §7(%.1fブロック先)", 
                nearestNPC.getName(), minDistance));
        }
        
        return true;
    }
    
    /**
     * NPC完全削除コマンド（物理削除）
     */
    private boolean handlePurgeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc purge <NPC名>");
            sender.sendMessage("§c警告: このコマンドはNPCを削除します（復元可能）");
            return true;
        }
        
        String npcName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // NPCを名前で検索して削除
        Collection<NPCManager.NPCData> allNPCs = npcManager.getAllNPCs();
        NPCManager.NPCData targetNPC = null;
        
        for (NPCManager.NPCData npcData : allNPCs) {
            if (npcData.getName().contains(npcName) || npcData.getName().equals(npcName)) {
                targetNPC = npcData;
                break;
            }
        }
        
        if (targetNPC == null) {
            sender.sendMessage("§c指定された名前のNPCが見つかりません: " + npcName);
            return true;
        }
        
        // 削除前に詳細情報を表示
        sender.sendMessage("§e=== NPC削除確認 ===");
        sender.sendMessage("§7NPC名: §f" + targetNPC.getName());
        sender.sendMessage("§7NPCタイプ: §f" + targetNPC.getNpcType());
        sender.sendMessage("§7座標: §f" + formatLocation(targetNPC.getLocation()));
        
        try {
            String npcNameToRemove = targetNPC.getName();
            String npcTypeToRemove = targetNPC.getNpcType();
            
            // NPCエンティティを削除
            npcManager.removeNPC(targetNPC.getEntityId());
            
            // config.ymlから物理削除
            boolean configDeleted = false;
            if ("trader".equals(npcTypeToRemove)) {
                configManager.removeTradingPostByName(npcNameToRemove);
                
                // TradingNPCManagerも更新
                if (tradingNPCManager != null) {
                    tradingNPCManager.reloadTradingPosts();
                }
                configDeleted = true;
                sender.sendMessage("§aNPC「" + npcNameToRemove + "」を完全削除しました。");
            } else if ("banker".equals(npcTypeToRemove)) {
                configManager.removeBankNPCByName(npcNameToRemove);
                configDeleted = true;
                sender.sendMessage("§aNPC「" + npcNameToRemove + "」を完全削除しました。");
            } else if ("food_merchant".equals(npcTypeToRemove)) {
                configManager.removeFoodNPCByName(npcNameToRemove);
                
                // FoodNPCManagerも更新
                if (foodNPCManager != null) {
                    foodNPCManager.reloadFoodNPCs();
                }
                configDeleted = true;
                sender.sendMessage("§aNPC「" + npcNameToRemove + "」を完全削除しました。");
            } else {
                sender.sendMessage("§aNPC「" + npcNameToRemove + "」をエンティティから削除しました。");
                sender.sendMessage("§e注意: このNPCタイプは設定ファイル削除に対応していません。");
            }
            
            if (configDeleted) {
                sender.sendMessage("§7設定ファイルから完全に削除されました。");
            }
        } catch (Exception e) {
            sender.sendMessage("§cNPC完全削除中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPC完全削除エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * NPC復元コマンド（論理削除されたNPCを復元）
     */
    private boolean handleRestoreCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc restore <NPC名>");
            return true;
        }
        
        String npcName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        try {
            // 取引NPCの復元を試行
            // 物理削除方式では復元機能は利用できません
            sender.sendMessage("§c物理削除方式では復元機能は利用できません。");
            sender.sendMessage("§7NPCを再配置するには '/npc spawn' コマンドを使用してください。");
            
            // 削除されたNPCが見つからない場合
            sender.sendMessage("§c指定された名前の削除済みNPCが見つかりません: " + npcName);
            sender.sendMessage("§7削除済みNPCの一覧は '/npc list deleted' で確認できます。");
            
        } catch (Exception e) {
            sender.sendMessage("§cNPC復元中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPC復元エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * NPC完全削除コマンド（物理削除・復元不可）
     */
    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc delete <NPC指定>");
            sender.sendMessage("§7指定方法:");
            sender.sendMessage("§7  - NPC名: '鉱夫取引商人'");
            sender.sendMessage("§7  - 部分名: '鉱夫'");
            sender.sendMessage("§7  - 職業ショートカット: m1,m2,w1,f1,fish1,b1,a1,e1,arch1");
            sender.sendMessage("§7  - インデックス: 1,2,3... (/npc list --index で確認)");
            return true;
        }
        
        String npcTarget = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // NPC簡単指定を解決
        NPCManager.NPCData targetNPC = resolveNPCTarget(npcTarget);
        
        if (targetNPC == null) {
            sender.sendMessage("§c指定されたNPCが見つかりません: " + npcTarget);
            sender.sendMessage("§7§l利用可能な指定方法:");
            sender.sendMessage("§e/npc list --index §7でNPC一覧と番号を確認できます");
            return true;
        }
        
        // 削除前に詳細情報を表示
        sender.sendMessage("§c§l=== NPC完全削除確認 ===");
        sender.sendMessage("§7NPC名: §f" + targetNPC.getName());
        sender.sendMessage("§7NPCタイプ: §f" + targetNPC.getNpcType());
        sender.sendMessage("§7座標: §f" + formatLocation(targetNPC.getLocation()));
        sender.sendMessage("§c§l警告: この操作は復元できません！");
        
        try {
            String npcNameToDelete = targetNPC.getName();
            String npcTypeToDelete = targetNPC.getNpcType();
            
            // 1. まず設定ファイルから削除（削除失敗時のエンティティ残留を防ぐため）
            boolean configDeleted = false;
            if ("trader".equals(npcTypeToDelete)) {
                configManager.permanentlyDeleteTradingPost(npcNameToDelete);
                configDeleted = true;
            } else if ("banker".equals(npcTypeToDelete)) {
                configManager.permanentlyDeleteBankNPC(npcNameToDelete);
                configDeleted = true;
            } else if ("food_merchant".equals(npcTypeToDelete)) {
                configManager.permanentlyDeleteFoodNPC(npcNameToDelete);
                configDeleted = true;
            }
            
            // 2. NPCエンティティを削除
            npcManager.removeNPC(targetNPC.getEntityId());
            
            // 3. 該当NPCManagerでリロード処理を実行（設定ファイルとの同期）
            if ("trader".equals(npcTypeToDelete) && tradingNPCManager != null) {
                tradingNPCManager.reloadTradingPosts();
            } else if ("banker".equals(npcTypeToDelete) && bankNPCManager != null) {
                bankNPCManager.reloadBankNPCs();
            } else if ("food_merchant".equals(npcTypeToDelete) && foodNPCManager != null) {
                foodNPCManager.reloadFoodNPCs();
            }
            
            sender.sendMessage("§aNPC「" + npcNameToDelete + "」を完全削除しました（復元不可）。");
            
            if (configDeleted) {
                sender.sendMessage("§7設定ファイルからも完全に削除されました。");
            } else {
                sender.sendMessage("§e注意: このNPCタイプは設定ファイル削除に対応していません。");
            }
            
        } catch (Exception e) {
            sender.sendMessage("§cNPC完全削除中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("NPC完全削除エラー: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * NPC状況診断コマンド（削除状況の確認）
     */
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /npc status <NPC名>");
            sender.sendMessage("§7指定されたNPCの設定状況と削除フラグを確認します");
            return true;
        }
        
        String npcName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        sender.sendMessage("§6=== NPC状況診断: " + npcName + " ===");
        
        // 1. アクティブなNPCエンティティの確認
        boolean foundActiveNPC = false;
        for (NPCManager.NPCData npcData : npcManager.getAllNPCs()) {
            if (npcData.getName().contains(npcName) || npcData.getName().equals(npcName)) {
                sender.sendMessage("§a✓ アクティブなNPCエンティティ: " + npcData.getName());
                sender.sendMessage("  §7タイプ: " + npcData.getNpcType());
                sender.sendMessage("  §7座標: " + formatLocation(npcData.getLocation()));
                sender.sendMessage("  §7エンティティID: " + npcData.getEntityId());
                sender.sendMessage("  §7有効状態: " + (npcData.getEntity().isValid() ? "§a有効" : "§c無効"));
                foundActiveNPC = true;
            }
        }
        
        if (!foundActiveNPC) {
            sender.sendMessage("§c✗ アクティブなNPCエンティティが見つかりません");
        }
        
        // 2. 取引NPCの設定確認
        checkTradingPostStatus(sender, npcName);
        
        // 3. 銀行NPCの設定確認
        checkBankNPCStatus(sender, npcName);
        
        // 4. 重複チェック
        checkDuplicateNPCs(sender, npcName);
        
        sender.sendMessage("§6=== 診断完了 ===");
        
        return true;
    }
    
    /**
     * 取引NPCの設定状況を確認
     */
    private void checkTradingPostStatus(CommandSender sender, String npcName) {
        List<Map<?, ?>> tradingPosts = configManager.getTradingPostConfigs();
        boolean foundInTrading = false;
        
        for (Map<?, ?> post : tradingPosts) {
            String postName = (String) post.get("name");
            if (npcName.equals(postName) || (postName != null && postName.contains(npcName))) {
                Object deletedFlag = post.get("deleted");
                boolean isDeleted = deletedFlag != null && (Boolean) deletedFlag;
                
                sender.sendMessage("§e○ 取引NPC設定: " + postName);
                sender.sendMessage("  §7削除フラグ: " + (isDeleted ? "§c削除済み" : "§a有効"));
                sender.sendMessage("  §7職業: " + post.get("job"));
                sender.sendMessage("  §7世界: " + post.get("world"));
                sender.sendMessage("  §7座標: (" + post.get("x") + ", " + post.get("y") + ", " + post.get("z") + ")");
                foundInTrading = true;
            }
        }
        
        if (!foundInTrading) {
            sender.sendMessage("§7- 取引NPC設定には見つかりません");
        }
    }
    
    /**
     * 銀行NPCの設定状況を確認
     */
    private void checkBankNPCStatus(CommandSender sender, String npcName) {
        List<Map<?, ?>> bankNPCs = configManager.getBankNPCs();
        boolean foundInBank = false;
        
        for (Map<?, ?> npc : bankNPCs) {
            String npcNameInConfig = (String) npc.get("name");
            if (npcName.equals(npcNameInConfig) || (npcNameInConfig != null && npcNameInConfig.contains(npcName))) {
                Object deletedFlag = npc.get("deleted");
                boolean isDeleted = deletedFlag != null && (Boolean) deletedFlag;
                
                sender.sendMessage("§e○ 銀行NPC設定: " + npcNameInConfig);
                sender.sendMessage("  §7削除フラグ: " + (isDeleted ? "§c削除済み" : "§a有効"));
                sender.sendMessage("  §7世界: " + npc.get("world"));
                sender.sendMessage("  §7座標: (" + npc.get("x") + ", " + npc.get("y") + ", " + npc.get("z") + ")");
                foundInBank = true;
            }
        }
        
        if (!foundInBank) {
            sender.sendMessage("§7- 銀行NPC設定には見つかりません");
        }
    }
    
    /**
     * 重複NPCの確認
     */
    private void checkDuplicateNPCs(CommandSender sender, String npcName) {
        int totalMatches = 0;
        
        // アクティブNPCでの重複
        long activeMatches = npcManager.getAllNPCs().stream()
            .filter(npc -> npc.getName().contains(npcName) || npc.getName().equals(npcName))
            .count();
        
        // 設定ファイルでの重複
        long tradingMatches = configManager.getTradingPostConfigs().stream()
            .filter(post -> {
                String postName = (String) post.get("name");
                return npcName.equals(postName) || (postName != null && postName.contains(npcName));
            })
            .count();
        
        long bankMatches = configManager.getBankNPCs().stream()
            .filter(npc -> {
                String npcNameInConfig = (String) npc.get("name");
                return npcName.equals(npcNameInConfig) || (npcNameInConfig != null && npcNameInConfig.contains(npcName));
            })
            .count();
        
        totalMatches = (int) (activeMatches + tradingMatches + bankMatches);
        
        if (totalMatches > 1) {
            sender.sendMessage("§c⚠ 重複検出: " + totalMatches + "個の一致");
            sender.sendMessage("  §7アクティブNPC: " + activeMatches + "個");
            sender.sendMessage("  §7取引NPC設定: " + tradingMatches + "個");
            sender.sendMessage("  §7銀行NPC設定: " + bankMatches + "個");
            sender.sendMessage("  §e重複がある場合、予期しない動作の原因となる可能性があります");
        } else if (totalMatches == 1) {
            sender.sendMessage("§a✓ 重複なし（1個の一致のみ）");
        } else {
            sender.sendMessage("§c✗ 該当するNPCが見つかりません");
        }
    }
    
    /**
     * NPC指定（ショートカット、部分名、インデックス）を解決する
     */
    private NPCManager.NPCData resolveNPCTarget(String target) {
        // 1. インデックス指定の場合
        try {
            int index = Integer.parseInt(target);
            List<NPCManager.NPCData> allNPCs = new ArrayList<>(npcManager.getAllNPCs());
            if (index >= 1 && index <= allNPCs.size()) {
                return allNPCs.get(index - 1); // 1ベースのインデックス
            }
        } catch (NumberFormatException ignored) {
            // インデックス指定ではない
        }
        
        // 2. 職業ショートカット指定の場合
        NPCManager.NPCData shortcutNPC = resolveJobShortcut(target);
        if (shortcutNPC != null) {
            return shortcutNPC;
        }
        
        // 3. 完全名マッチ
        for (NPCManager.NPCData npcData : npcManager.getAllNPCs()) {
            if (npcData.getName().equals(target)) {
                return npcData;
            }
        }
        
        // 4. 部分名マッチ
        for (NPCManager.NPCData npcData : npcManager.getAllNPCs()) {
            if (npcData.getName().contains(target)) {
                return npcData;
            }
        }
        
        return null;
    }
    
    /**
     * 職業ショートカット（m1, w1, f1など）を解決する
     */
    private NPCManager.NPCData resolveJobShortcut(String shortcut) {
        String jobType = null;
        int index = 1;
        
        // ショートカットを解析
        if (shortcut.matches("m\\d+")) {
            jobType = "miner";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("w\\d+")) {
            jobType = "woodcutter";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("f\\d+")) {
            jobType = "farmer";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("fish\\d+")) {
            jobType = "fisherman";
            index = Integer.parseInt(shortcut.substring(4));
        } else if (shortcut.matches("b\\d+")) {
            jobType = "blacksmith";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("a\\d+")) {
            jobType = "alchemist";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("e\\d+")) {
            jobType = "enchanter";
            index = Integer.parseInt(shortcut.substring(1));
        } else if (shortcut.matches("arch\\d+")) {
            jobType = "architect";
            index = Integer.parseInt(shortcut.substring(4));
        }
        
        if (jobType == null) {
            return null;
        }
        
        // 指定された職業のNPCを取得
        final String finalJobType = jobType;
        List<NPCManager.NPCData> jobNPCs = npcManager.getAllNPCs().stream()
            .filter(npc -> "trader".equals(npc.getNpcType()))
            .filter(npc -> npc.getName().toLowerCase().contains(finalJobType) || 
                          npc.getName().contains(configManager.getJobDisplayName(finalJobType)))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (index >= 1 && index <= jobNPCs.size()) {
            return jobNPCs.get(index - 1);
        }
        
        return null;
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics NPC コマンド ===");
        sender.sendMessage("§a【セットアップコマンド（新機能）】");
        sender.sendMessage("§f/npc setup §7- 対話式NPC配置ガイド");
        sender.sendMessage("§f/npc setup-location <type> [args] §7- 現在位置にNPC配置");
        sender.sendMessage("§f/npc setup-all §7- 一括セットアップガイド");
        sender.sendMessage("");
        sender.sendMessage("§e【基本コマンド】");
        sender.sendMessage("§f/npc spawn trader <職業名> [NPC名] §7- 職業専用取引NPCをスポーン");
        sender.sendMessage("§f/npc spawn banker [NPC名] §7- 銀行NPCをスポーン");
        sender.sendMessage("§f/npc spawn food_merchant [NPC名] [タイプ] §7- 食料NPCをスポーン");
        sender.sendMessage("§7食料NPCタイプ: general_store, bakery, butcher, fishmonger, greengrocer, specialty");
        sender.sendMessage("§f/npc remove <NPC名> §7- NPCを削除");
        sender.sendMessage("§f/npc delete <NPC指定> §7- NPC完全削除（復元不可・簡単指定対応）");
        sender.sendMessage("§f/npc status <NPC名> §7- NPC状況診断（削除フラグ・重複チェック）");
        sender.sendMessage("§f/npc tp <NPC名> §7- NPCの場所へテレポート");
        sender.sendMessage("§f/npc list [--index] §7- システムNPCの一覧表示（番号付き）");
        sender.sendMessage("§f/npc cleanup §7- 全NPCを完全削除（復元不可・緊急用）");
        sender.sendMessage("§f/npc reload §7- NPCシステムをリロード");
        sender.sendMessage("§f/npc info §7- NPCシステム情報を表示");
        sender.sendMessage("§f/npc purge <NPC名> §7- NPCを削除（復元可能）");
        sender.sendMessage("§f/npc restore <NPC名> §7- 削除されたNPCを復元");
        sender.sendMessage("§7職業: " + String.join(", ", VALID_JOBS));
        sender.sendMessage("§7簡単指定: m1,m2,w1,f1,fish1,b1,a1,e1,arch1（職業ショートカット）");
        sender.sendMessage("§7          1,2,3...（インデックス番号）、部分名マッチも対応");
    }
    
    /**
     * ユーティリティメソッド群
     */
    private boolean isValidJobType(String jobType) {
        // "all"は全職業対応の特別な職業タイプとして許可
        if ("all".equalsIgnoreCase(jobType)) {
            return true;
        }
        for (String validJob : VALID_JOBS) {
            if (validJob.equalsIgnoreCase(jobType)) {
                return true;
            }
        }
        return false;
    }
    
    private String formatLocation(Location location) {
        return String.format("%s (%.1f, %.1f, %.1f)", 
            location.getWorld().getName(), 
            location.getX(), location.getY(), location.getZ());
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String[] subCommands = {"spawn", "remove", "tp", "list", "cleanup", "reload", "info", "purge", "restore", "delete", "status", "setup", "setup-location", "setup-all"};
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "spawn":
                    String[] npcTypes = {"trader", "banker", "food_merchant", "processing"};
                    for (String npcType : npcTypes) {
                        if (npcType.startsWith(args[1].toLowerCase())) {
                            completions.add(npcType);
                        }
                    }
                    break;
                case "list":
                    if ("--index".startsWith(args[1].toLowerCase())) {
                        completions.add("--index");
                    }
                    break;
                case "remove":
                case "tp":
                case "purge":
                case "restore":
                case "delete":
                case "status":
                    // NPCの名前を補完（簡略化のため省略）
                    // 実際の実装では、NPCの名前やショートカット（m1, w1, f1等）を補完
                    break;
                case "setup-location":
                    String[] setupTypes = {"trader", "banker", "food", "processing"};
                    for (String setupType : setupTypes) {
                        if (setupType.startsWith(args[1].toLowerCase())) {
                            completions.add(setupType);
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("trader")) {
                // "all"を最初に追加
                if ("all".startsWith(args[2].toLowerCase())) {
                    completions.add("all");
                }
                for (String job : VALID_JOBS) {
                    if (job.startsWith(args[2].toLowerCase())) {
                        completions.add(job);
                    }
                }
            } else if (args[0].equalsIgnoreCase("setup-location") && args[1].equalsIgnoreCase("trader")) {
                // "all"を最初に追加
                if ("all".startsWith(args[2].toLowerCase())) {
                    completions.add("all");
                }
                for (String job : VALID_JOBS) {
                    if (job.startsWith(args[2].toLowerCase())) {
                        completions.add(job);
                    }
                }
            }
        }
        
        return completions;
    }
    
    /**
     * セットアップコマンドハンドラー
     * /npc setup - 対話式NPC配置ガイド
     */
    private boolean handleSetupCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        player.sendMessage("§6=== TofuNomics NPC セットアップガイド ===");
        player.sendMessage("");
        player.sendMessage("§eNPCの配置を開始します。各NPCタイプを順番に配置できます。");
        player.sendMessage("§7配置したい場所に移動してから対応するコマンドを実行してください。");
        player.sendMessage("");
        player.sendMessage("§f利用可能なコマンド:");
        player.sendMessage("§a  /tofunomics npc setup-location trader <職業> §7- 取引NPCを現在位置に配置");
        player.sendMessage("§a  /tofunomics npc setup-location banker §7- 銀行NPCを現在位置に配置");
        player.sendMessage("§a  /tofunomics npc setup-location food §7- 食料NPCを現在位置に配置");
        player.sendMessage("");
        player.sendMessage("§f職業リスト: §7" + String.join(", ", VALID_JOBS));
        player.sendMessage("");
        player.sendMessage("§e例: §f/tofunomics npc setup-location trader miner");
        player.sendMessage("§e例: §f/tofunomics npc setup-location banker");
        player.sendMessage("");
        player.sendMessage("§6一括セットアップ: §f/tofunomics npc setup-all");
        
        return true;
    }
    
    /**
     * 現在位置セットアップコマンドハンドラー
     * /npc setup-location <type> [args...] - 現在位置にNPCを配置
     */
    private boolean handleSetupLocationCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§c使用法: /npc setup-location <trader|banker|food> [引数...]");
            player.sendMessage("§7trader: /npc setup-location trader <職業名> [NPC名]");
            player.sendMessage("§7banker: /npc setup-location banker [NPC名]");
            player.sendMessage("§7food: /npc setup-location food [NPC名]");
            return true;
        }
        
        String npcType = args[1].toLowerCase();
        Location currentLocation = player.getLocation();
        
        player.sendMessage("§e現在位置に" + npcType + "NPCを配置します...");
        player.sendMessage("§7座標: " + formatLocation(currentLocation));
        
        // 適切なspawnコマンド引数を構築
        String[] spawnArgs;
        if (args.length >= 3) {
            // 名前が指定されている場合: setup-location banker 銀行員 -> spawn banker 銀行員
            spawnArgs = new String[args.length];
            spawnArgs[0] = "spawn";
            spawnArgs[1] = args[1]; // npcType (banker/trader/food)
            // 残りの引数をコピー (名前など)
            System.arraycopy(args, 2, spawnArgs, 2, args.length - 2);
        } else {
            // 名前が指定されていない場合: setup-location banker -> spawn banker
            spawnArgs = new String[2];
            spawnArgs[0] = "spawn";
            spawnArgs[1] = args[1]; // npcType
        }
        
        return handleSpawnCommand(sender, spawnArgs);
    }
    
    /**
     * 一括セットアップコマンドハンドラー
     * /npc setup-all - 必要なNPCを順番に配置するガイド
     */
    private boolean handleSetupAllCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tofunomics.npc.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。");
            return true;
        }
        
        Player player = (Player) sender;
        
        player.sendMessage("§6=== 一括NPCセットアップガイド ===");
        player.sendMessage("");
        player.sendMessage("§e以下の手順でNPCを配置してください：");
        player.sendMessage("");
        
        player.sendMessage("§f1. §a銀行NPC §7- お金の管理");
        player.sendMessage("   §7配置場所に移動して: §f/tofunomics npc setup-location banker");
        player.sendMessage("");
        
        player.sendMessage("§f2. §a食料NPC §7- 緊急時の食料供給");
        player.sendMessage("   §7配置場所に移動して: §f/tofunomics npc setup-location food");
        player.sendMessage("");
        
        player.sendMessage("§f3. §a取引NPC §7- 各職業専用の取引所");
        for (String job : VALID_JOBS) {
            String displayName = configManager.getJobDisplayName(job);
            player.sendMessage("   §7" + displayName + ": §f/tofunomics npc setup-location trader " + job);
        }
        player.sendMessage("");
        
        player.sendMessage("§e注意事項:");
        player.sendMessage("§7• 各NPCは適切な場所に配置してください");
        player.sendMessage("§7• 配置後は自動的にconfig.ymlに保存されます");
        player.sendMessage("§7• プレイヤーがアクセスしやすい場所を選んでください");
        player.sendMessage("");
        
        player.sendMessage("§6進捗確認: §f/tofunomics npc list");
        
        return true;
    }
}