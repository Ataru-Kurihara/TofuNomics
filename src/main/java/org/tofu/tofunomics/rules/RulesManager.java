package org.tofu.tofunomics.rules;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RulesManager implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final RulesGUI rulesGUI;
    private final org.tofu.tofunomics.jobs.JobManager jobManager;
    
    // 未同意プレイヤーのUUIDを記録（制限対象）
    private final Set<UUID> unagreedPlayers = ConcurrentHashMap.newKeySet();
    
    public RulesManager(TofuNomics plugin, ConfigManager configManager, PlayerDAO playerDAO, org.tofu.tofunomics.jobs.JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.rulesGUI = new RulesGUI(plugin, configManager, this, jobManager);
    }
    
    /**
     * ページ2の内容を動的に生成（経済システム）
     */
    private List<String> generatePage2Content() {
        List<String> content = new ArrayList<>();
        
        try {
            // 通貨設定を取得
            String currencyName = plugin.getConfig().getString("economy.currency.name", "金塊");
            String currencySymbol = plugin.getConfig().getString("economy.currency.symbol", "G");
            int startingBalance = plugin.getConfig().getInt("economy.starting_balance", 100);
            
            content.add("§e▼ 通貨システム");
            content.add("§f通貨: §6" + currencyName + " (" + currencySymbol + ")");
            content.add("§f新規プレイヤーは" + startingBalance + currencySymbol + "からスタート");
            content.add("");
            content.add("§e▼ 銀行システム");
            content.add("§f・§b/balance §f- 残高確認");
            content.add("§f・§b/deposit <金額> §f- 金塊を預ける");
            content.add("§f・§b/withdraw <金額> §f- 金塊を引き出す");
            content.add("§f・銀行NPC周辺でのみ取引可能");
            content.add("");
            content.add("§e▼ 送金");
            content.add("§f・§b/pay <プレイヤー名> <金額>");
            content.add("§f・他のプレイヤーにお金を送れます");
            content.add("");
            content.add("§e▼ NPCとの取引");
            content.add("§f・取引NPCで物を売買できます");
            content.add("§f・ジョブレベルで価格ボーナスあり");
            content.add("§f・食料NPCで食べ物を購入できます");
            
        } catch (Exception e) {
            plugin.getLogger().warning("ページ2の動的生成に失敗しました。フォールバックします: " + e.getMessage());
            // フォールバック: config.ymlから静的に読み込み
            return plugin.getConfig().getStringList("rules.pages.2.content");
        }
        
        return content;
    }
    
    /**
     * ページ3の内容を動的に生成（ジョブ・スキルシステム）
     */
    private List<String> generatePage3Content() {
        List<String> content = new ArrayList<>();
        
        try {
            // ジョブ設定を取得
            int maxJobsPerPlayer = plugin.getConfig().getInt("jobs.general.max_jobs_per_player", 1);
            
            content.add("§e▼ ジョブの種類");
            
            // 全ジョブをconfig.ymlから取得して動的に生成
            if (plugin.getConfig().isConfigurationSection("jobs.job_settings")) {
                Set<String> jobKeys = plugin.getConfig().getConfigurationSection("jobs.job_settings").getKeys(false);
                
                // ジョブの表示順序を定義（優先度順）
                List<String> jobOrder = Arrays.asList("miner", "woodcutter", "farmer", "fisherman", "blacksmith", "alchemist", "enchanter", "architect");
                
                // 最大レベルを取得（全ジョブ共通と仮定）
                int maxLevel = 100;
                
                for (String jobKey : jobOrder) {
                    if (jobKeys.contains(jobKey)) {
                        String displayName = plugin.getConfig().getString("jobs.job_settings." + jobKey + ".display_name", jobKey);
                        String description = plugin.getConfig().getString("jobs.job_settings." + jobKey + ".description", "");
                        maxLevel = plugin.getConfig().getInt("jobs.job_settings." + jobKey + ".max_level", 100);
                        
                        // ジョブリストに追加
                        content.add("§f・" + displayName + "（" + capitalize(jobKey) + "） - " + description);
                    }
                }
                
                // 同時保有可能ジョブ数を明記
                if (maxJobsPerPlayer == 1) {
                    content.add("§f※ 同時に就けるジョブは1つのみです");
                } else {
                    content.add("§f※ 同時に" + maxJobsPerPlayer + "つのジョブに就けます");
                }
                content.add("");
                content.add("§e▼ レベルアップ");
                content.add("§f・ジョブ関連の作業で経験値を獲得");
                content.add("§f・レベルが上がるとスキルを習得");
                content.add("§f・最大レベル: " + maxLevel);
            } else {
                throw new Exception("ジョブ設定が見つかりません");
            }
            
            content.add("");
            content.add("§e▼ スキル");
            content.add("§f・各ジョブ専用のスキルが習得可能");
            content.add("§f・作業効率アップや特殊能力を獲得");
            content.add("");
            content.add("§e▼ ジョブ変更");
            content.add("§f・§b/jobs leave §fで現在のジョブを辞める");
            content.add("§f・§b/jobs join <ジョブ名> §fで新しいジョブに就く");
            content.add("§f・レベル50に達すると他の職業に転職できます");
            
        } catch (Exception e) {
            plugin.getLogger().warning("ページ3の動的生成に失敗しました。フォールバックします: " + e.getMessage());
            // フォールバック: config.ymlから静的に読み込み
            return plugin.getConfig().getStringList("rules.pages.3.content");
        }
        
        return content;
    }
    
    /**
     * 文字列の最初の文字を大文字にする
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * ルールブックアイテムを生成
     */
    public ItemStack createRulebook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            // 本の基本情報
            String bookName = configManager.getMessage("rules.book_name");
            meta.setTitle(bookName);
            meta.setAuthor("§6TofuNomics Server");
            
            // ページ内容を追加
            List<String> pages = new ArrayList<>();
            
            // ページ1: 初心者ガイド（静的）
            List<String> page1Content = plugin.getConfig().getStringList("rules.pages.1.content");
            pages.add(String.join("\n", page1Content));
            
            // ページ2: 経済システム（動的生成）
            List<String> page2Content = generatePage2Content();
            pages.add(String.join("\n", page2Content));
            
            // ページ3: ジョブ・スキルシステム（動的生成）
            List<String> page3Content = generatePage3Content();
            pages.add(String.join("\n", page3Content));
            
            // ページ4: 禁止事項（静的）
            List<String> page4Content = plugin.getConfig().getStringList("rules.pages.4.content");
            pages.add(String.join("\n", page4Content));
            
            // 最終ページ: GUI案内
            pages.add("§6§l【ルール確認】\n\n§7このルールブックを右クリックするか、\n§7/rules コマンドを実行すると、\n§7詳細なルールGUIが開きます。\n\n§eルールに同意してプレイを開始しましょう！");
            
            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * プレイヤーにルールブックを配布
     */
    public void giveRulebook(Player player) {
        ItemStack rulebook = createRulebook();
        player.getInventory().addItem(rulebook);
        player.sendMessage(configManager.getMessage("rules.book_given"));
    }
    
    /**
     * プレイヤーがルールに同意しているか確認
     */
    public boolean hasAgreedToRules(UUID uuid) {
        return playerDAO.hasAgreedToRules(uuid);
    }
    
    /**
     * ルール同意の強制（未同意プレイヤーの行動制限）が有効かどうか
     * rules.enabled と rules.require_agreement の両方が true の場合のみ制限を行う。
     * 外部サイト確認方式（require_agreement: false）では制限を一切かけない。
     */
    public boolean isAgreementEnforced() {
        return configManager.isRulesEnabled() && configManager.isRulesAgreementRequired();
    }

    /**
     * プレイヤーをルール未同意リストに追加（行動制限対象）
     * 同意強制が無効な場合は登録しない
     */
    public void markAsUnagreed(UUID uuid) {
        if (!isAgreementEnforced()) {
            return;
        }
        unagreedPlayers.add(uuid);
    }
    
    /**
     * プレイヤーのルール同意を記録し、制限を解除
     */
    public void agreeToRules(Player player) {
        UUID uuid = player.getUniqueId();
        
        // データベースに記録
        playerDAO.setRulesAgreed(uuid, true);
        
        // 制限リストから削除
        unagreedPlayers.remove(uuid);
        
        // メッセージ送信
        player.sendMessage(configManager.getMessage("rules.agreed"));
        
        plugin.getLogger().info("プレイヤー " + player.getName() + " がルールに同意しました");
    }

    /**
     * プレイヤーのルール同意状態をリセットし、未同意状態に戻す（テスト用）
     * @param uuid プレイヤーのUUID
     * @return リセット成功時 true
     */
    public boolean resetPlayerRules(UUID uuid) {
        try {
            // データベースの同意状態を FALSE に更新
            playerDAO.setRulesAgreed(uuid, false);
            
            // 未同意プレイヤーリストに追加（キャッシュ更新）
            unagreedPlayers.add(uuid);
            
            plugin.getLogger().info("プレイヤー " + uuid + " のルール同意状態をリセットしました");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("ルール同意状態のリセットに失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * RulesGUIを取得
     */
    public RulesGUI getRulesGUI() {
        return rulesGUI;
    }
    
    /**
     * プレイヤーが未同意リストに含まれているか確認
     */
    public boolean isUnagreed(UUID uuid) {
        return unagreedPlayers.contains(uuid);
    }

    /**
     * 現在のワールドでルール制限を適用すべきか確認
     * 同意強制が無効な場合、および economy.enabled_worlds（ワールド制限ホワイトリスト）の
     * 対象ワールド外（tofunomics系以外）ではルール同意システムを動作させない
     */
    private boolean isRulesEnabledInWorld(Player player) {
        return isAgreementEnforced() && configManager.isEconomyEnabledInWorld(player.getWorld().getName());
    }
    
    // ========== イベントハンドラ：未同意プレイヤーの行動制限 ==========
    
    /**
     * 移動制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (isUnagreed(player.getUniqueId()) && isRulesEnabledInWorld(player)) {
            // 移動先と移動元が異なるブロックの場合のみキャンセル
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
                player.sendMessage(configManager.getMessage("rules.action_blocked"));
            }
        }
    }
    
    /**
     * ブロック破壊制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId()) && isRulesEnabledInWorld(player)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }

    /**
     * ブロック設置制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        if (isUnagreed(player.getUniqueId()) && isRulesEnabledInWorld(player)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }

    /**
     * アイテム使用制限
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (isUnagreed(player.getUniqueId()) && isRulesEnabledInWorld(player)) {
            // ルールブック使用は許可（右クリックでGUI開く）
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && meta.hasTitle()) {
                    String bookTitle = meta.getTitle();
                    String expectedTitle = configManager.getMessage("rules.book_name");
                    if (bookTitle.equals(expectedTitle)) {
                        // ルールブックの右クリック → GUI表示
                        event.setCancelled(true);
                        rulesGUI.openRulesGUI(player, 1);
                        return;
                    }
                }
            }
            
            // その他のアイテム使用は制限
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.action_blocked"));
        }
    }
    
    /**
     * コマンド実行制限（/rulesのみ許可）
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (isUnagreed(player.getUniqueId()) && isRulesEnabledInWorld(player)) {
            String command = event.getMessage().toLowerCase();
            
            // /rules コマンドは許可
            if (command.startsWith("/rules")) {
                return;
            }
            
            // その他のコマンドは制限
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("rules.must_agree"));
        }
    }
}
