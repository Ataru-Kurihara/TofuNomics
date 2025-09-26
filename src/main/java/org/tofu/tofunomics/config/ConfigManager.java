package org.tofu.tofunomics.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class ConfigManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> validationErrors = new HashSet<>();
    private long lastReloadTime = 0;
    
    public interface ConfigChangeListener {
        void onConfigChanged(String section);
    }
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }
    
    public void reloadConfig() {
        try {
            plugin.reloadConfig();
            
            // 設定自動更新を実行
            updateConfigWithDefaults();
            
            FileConfiguration newConfig = plugin.getConfig();
            
            Map<String, Object> oldValues = new HashMap<>(configCache);
            configCache.clear();
            validationErrors.clear();
            
            config = newConfig;
            lastReloadTime = System.currentTimeMillis();
            
            validateConfig();
            
            Set<String> changedSections = detectChanges(oldValues);
            
            for (String section : changedSections) {
                notifyListeners(section);
            }
            
            plugin.getLogger().info("設定ファイルを正常にリロードしました。");
            
            if (!validationErrors.isEmpty()) {
                plugin.getLogger().warning("設定エラーが見つかりました:");
                for (String error : validationErrors) {
                    plugin.getLogger().warning("  - " + error);
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("設定ファイルのリロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(String section) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(section);
            } catch (Exception e) {
                plugin.getLogger().warning("設定変更リスナーでエラーが発生しました: " + e.getMessage());
            }
        }
    }
    
    private Set<String> detectChanges(Map<String, Object> oldValues) {
        Set<String> changedSections = new HashSet<>();
        
        for (String key : configCache.keySet()) {
            Object oldValue = oldValues.get(key);
            Object newValue = configCache.get(key);
            
            if (!Objects.equals(oldValue, newValue)) {
                String section = key.substring(0, key.indexOf('.') > 0 ? key.indexOf('.') : key.length());
                changedSections.add(section);
            }
        }
        
        return changedSections;
    }
    
    private void validateConfig() {
        validateDatabaseSettings();
        validateEconomySettings();
        validateJobSettings();
        validatePerformanceSettings();
        validateSkillSettings();
        validateEventSettings();
    }
    
    private void validateDatabaseSettings() {
        int maxConnections = getDatabaseMaxConnections();
        if (maxConnections <= 0 || maxConnections > 100) {
            validationErrors.add("database.connection_pool.max_connections は1-100の範囲で設定してください (現在: " + maxConnections + ")");
        }
        
        int timeout = getDatabaseTimeout();
        if (timeout < 1000) {
            validationErrors.add("database.connection_pool.timeout は1000ms以上で設定してください (現在: " + timeout + ")");
        }
    }
    
    private void validateEconomySettings() {
        double startingBalance = getStartingBalance();
        if (startingBalance < 0) {
            validationErrors.add("economy.starting_balance は0以上で設定してください (現在: " + startingBalance + ")");
        }
        
        double minPay = getMinimumPayAmount();
        double maxPay = getMaximumPayAmount();
        if (maxPay > 0 && minPay > maxPay) {
            validationErrors.add("economy.pay.minimum_amount はmaximum_amount以下で設定してください");
        }
        
        double feePercentage = getPayFeePercentage();
        if (feePercentage < 0 || feePercentage > 1) {
            validationErrors.add("economy.pay.fee_percentage は0.0-1.0の範囲で設定してください (現在: " + feePercentage + ")");
        }
    }
    
    private void validateJobSettings() {
        int maxJobsPerPlayer = getMaxJobsPerPlayer();
        if (maxJobsPerPlayer <= 0 || maxJobsPerPlayer > 8) {
            validationErrors.add("jobs.general.max_jobs_per_player は1-8の範囲で設定してください (現在: " + maxJobsPerPlayer + ")");
        }
        
        int cooldown = getJobChangeCooldown();
        if (cooldown < 0) {
            validationErrors.add("jobs.general.job_change_cooldown は0以上で設定してください (現在: " + cooldown + ")");
        }
        
        String[] jobNames = {"miner", "woodcutter", "farmer", "fisherman", "blacksmith", "alchemist", "enchanter", "architect"};
        for (String jobName : jobNames) {
            int maxLevel = getJobMaxLevel(jobName);
            if (maxLevel <= 0 || maxLevel > 100) {
                validationErrors.add("jobs.job_settings." + jobName + ".max_level は1-100の範囲で設定してください (現在: " + maxLevel + ")");
            }
            
            double incomeMultiplier = getJobIncomeMultiplier(jobName);
            if (incomeMultiplier <= 0) {
                validationErrors.add("jobs.job_settings." + jobName + ".base_income_multiplier は0より大きく設定してください (現在: " + incomeMultiplier + ")");
            }
        }
        
        // 職業ブロック制限システムの検証
        validateJobBlockRestrictionSettings();
    }
    
    private void validatePerformanceSettings() {
        if (isConnectionPoolEnabled()) {
            int maxPoolSize = getMaximumPoolSize();
            int minIdle = getMinimumIdle();
            
            if (maxPoolSize <= 0 || maxPoolSize > 50) {
                validationErrors.add("performance.database.connection_pool.maximum_pool_size は1-50の範囲で設定してください (現在: " + maxPoolSize + ")");
            }
            
            if (minIdle < 0 || minIdle > maxPoolSize) {
                validationErrors.add("performance.database.connection_pool.minimum_idle は0以上かつmaximum_pool_size以下で設定してください (現在: " + minIdle + ")");
            }
        }
        
        if (isPlayerCacheEnabled()) {
            int cacheMaxSize = getPlayerCacheMaxSize();
            if (cacheMaxSize <= 0 || cacheMaxSize > 10000) {
                validationErrors.add("performance.caching.player_cache.max_size は1-10000の範囲で設定してください (現在: " + cacheMaxSize + ")");
            }
        }
    }
    
    private void validateSkillSettings() {
        String[] jobNames = {"miner", "woodcutter", "farmer", "fisherman", "blacksmith", "alchemist", "enchanter", "architect"};
        Map<String, String[]> jobSkills = new HashMap<>();
        jobSkills.put("miner", new String[]{"fortune_strike", "vein_discovery", "mining_mastery"});
        jobSkills.put("woodcutter", new String[]{"tree_feller", "sapling_blessing", "forest_guardian"});
        jobSkills.put("farmer", new String[]{"harvest_blessing", "twin_miracle", "growth_acceleration", "selective_breeding"});
        jobSkills.put("fisherman", new String[]{"big_catch", "treasure_hunter", "sea_blessing"});
        jobSkills.put("blacksmith", new String[]{"perfect_repair", "master_craftsmanship", "artifact_creation"});
        jobSkills.put("alchemist", new String[]{"ingredient_conservation", "double_brewing", "alchemy_mastery"});
        jobSkills.put("enchanter", new String[]{"experience_conservation", "bonus_enchantment", "mystical_arts"});
        jobSkills.put("architect", new String[]{"material_efficiency", "architectural_aesthetics", "master_architect"});
        
        for (String jobName : jobNames) {
            String[] skills = jobSkills.get(jobName);
            for (String skillName : skills) {
                double baseProbability = getJobSkillBaseProbability(jobName, skillName);
                double maxProbability = getJobSkillMaxProbability(jobName, skillName);
                double levelBonus = getJobSkillLevelBonus(jobName, skillName);
                
                if (!isValidProbability(baseProbability)) {
                    validationErrors.add("job_skills." + jobName + "." + skillName + ".base_probability は0.0-1.0の範囲で設定してください (現在: " + baseProbability + ")");
                }
                
                if (!isValidProbability(maxProbability)) {
                    validationErrors.add("job_skills." + jobName + "." + skillName + ".max_probability は0.0-1.0の範囲で設定してください (現在: " + maxProbability + ")");
                }
                
                if (baseProbability > maxProbability) {
                    validationErrors.add("job_skills." + jobName + "." + skillName + " base_probabilityはmax_probability以下で設定してください");
                }
                
                if (levelBonus < 0) {
                    validationErrors.add("job_skills." + jobName + "." + skillName + ".level_bonus は0以上で設定してください (現在: " + levelBonus + ")");
                }
            }
        }
    }
    
    private void validateEventSettings() {
        if (isEventCachingEnabled()) {
            long cacheExpiry = getEventCacheExpiry();
            if (cacheExpiry <= 0) {
                validationErrors.add("events.caching.expiry_time は0より大きく設定してください (現在: " + cacheExpiry + ")");
            }
        }
        
        double globalExpMultiplier = getGlobalExperienceMultiplier();
        double globalIncomeMultiplier = getGlobalIncomeMultiplier();
        double globalSkillMultiplier = getGlobalSkillProbabilityMultiplier();
        
        if (globalExpMultiplier <= 0) {
            validationErrors.add("event_rewards.global_multipliers.experience_multiplier は0より大きく設定してください (現在: " + globalExpMultiplier + ")");
        }
        
        if (globalIncomeMultiplier <= 0) {
            validationErrors.add("event_rewards.global_multipliers.income_multiplier は0より大きく設定してください (現在: " + globalIncomeMultiplier + ")");
        }
        
        if (globalSkillMultiplier <= 0) {
            validationErrors.add("event_rewards.global_multipliers.skill_probability_multiplier は0より大きく設定してください (現在: " + globalSkillMultiplier + ")");
        }
    }
    
    private Object getCachedValue(String path, Object defaultValue) {
        return configCache.computeIfAbsent(path, k -> config.get(path, defaultValue));
    }
    
    // データベース設定
    public String getDatabaseFilename() {
        return (String) getCachedValue("database.filename", "tofunomics.db");
    }
    
    public int getDatabaseMaxConnections() {
        return (Integer) getCachedValue("database.connection_pool.max_connections", 10);
    }
    
    public int getDatabaseTimeout() {
        return (Integer) getCachedValue("database.connection_pool.timeout", 30000);
    }
    
    // 通貨設定
    public String getCurrencyName() {
        return (String) getCachedValue("economy.currency.name", "金塊");
    }
    
    public String getCurrencySymbol() {
        return (String) getCachedValue("economy.currency.symbol", "G");
    }
    
    public int getCurrencyDecimalPlaces() {
        return (Integer) getCachedValue("economy.currency.decimal_places", 2);
    }

    
    public double getCoinValue() {
        return (Double) getCachedValue("economy.currency.coin_value", 10.0);
    }
    
    public boolean isDynamicValueEnabled() {
        return (Boolean) getCachedValue("economy.currency.dynamic_value", true);
    }
    
    public double getMinCoinValue() {
        return (Double) getCachedValue("economy.currency.min_value", 0.1);
    }
    
    public double getMaxCoinValue() {
        return (Double) getCachedValue("economy.currency.max_value", 1000.0);
    }
    
    public void setCoinValue(double value) {
        if (value < getMinCoinValue() || value > getMaxCoinValue()) {
            throw new IllegalArgumentException("通貨価値は " + getMinCoinValue() + " から " + getMaxCoinValue() + " の範囲内である必要があります");
        }
        updateConfigValue("economy.currency.coin_value", value);
    }
    
    public double getStartingBalance() {
        return (Double) getCachedValue("economy.starting_balance", 100.0);
    }
    
    // 送金設定
    public double getMinimumPayAmount() {
        return config.getDouble("economy.pay.minimum_amount", 1.0);
    }
    
    public double getMaximumPayAmount() {
        return config.getDouble("economy.pay.maximum_amount", 0);
    }
    
    public double getPayFeePercentage() {
        return config.getDouble("economy.pay.fee_percentage", 0.0);
    }
    
    // 引き出し・預け入れ設定
    public double getMaxWithdraw() {
        return config.getDouble("economy.withdraw_deposit.max_withdraw", 10000.0);
    }
    
    public double getMaxDeposit() {
        return config.getDouble("economy.withdraw_deposit.max_deposit", 10000.0);
    }
    
    // 職業設定
    public int getMaxJobsPerPlayer() {
        return config.getInt("jobs.general.max_jobs_per_player", 1);
    }
    
    public boolean isKeepLevelOnJobChange() {
        return config.getBoolean("jobs.general.keep_level_on_change", true);
    }
    
    public int getJobChangeCooldown() {
        return config.getInt("jobs.general.job_change_cooldown", 86400);
    }
    
    // ========== 職業ブロック制限システム設定 ==========
    
    /**
     * 職業ブロック制限システムが有効かどうか
     */
    public boolean isJobBlockRestrictionEnabled() {
        return (Boolean) getCachedValue("jobs.block_restrictions.enabled", true);
    }
    
    /**
     * 基本ブロック（全職業で採掘可能）のリストを取得
     */
    public List<String> getBasicBlocks() {
        return config.getStringList("jobs.block_restrictions.basic_blocks");
    }
    
    /**
     * 特定職業の制限ブロックリストを取得
     * 
     * @param jobName 職業名
     * @return 制限ブロックのリスト
     */
    public List<String> getJobRestrictedBlocks(String jobName) {
        String path = "jobs.block_restrictions.job_restricted_blocks." + jobName;
        return config.getStringList(path);
    }
    
    /**
     * 職業ブロック制限システムの設定を検証
     */
    private void validateJobBlockRestrictionSettings() {
        // 基本設定の存在確認
        if (!config.contains("jobs.block_restrictions")) {
            validationErrors.add("職業ブロック制限システムの設定が見つかりません");
            return;
        }
        
        // 基本ブロックの検証
        List<String> basicBlocks = getBasicBlocks();
        if (basicBlocks.isEmpty()) {
            validationErrors.add("基本ブロックが設定されていません");
        }
        
        // 職業専用ブロックの検証
        String[] jobs = {"miner", "woodcutter", "farmer", "fisherman", "blacksmith", "alchemist", "enchanter", "architect"};
        for (String job : jobs) {
            if (!config.contains("jobs.block_restrictions.job_restricted_blocks." + job)) {
                validationErrors.add("職業 " + job + " の制限ブロック設定が見つかりません");
            }
        }
    }
    
    // 職業別設定
    public String getJobDisplayName(String jobName) {
        return config.getString("jobs.job_settings." + jobName + ".display_name", jobName);
    }
    
    public String getJobDescription(String jobName) {
        return config.getString("jobs.job_settings." + jobName + ".description", "");
    }
    
    public int getJobMaxLevel(String jobName) {
        return config.getInt("jobs.job_settings." + jobName + ".max_level", 75);
    }
    
    public double getJobIncomeMultiplier(String jobName) {
        return config.getDouble("jobs.job_settings." + jobName + ".base_income_multiplier", 1.0);
    }
    
    // イベントシステム設定
    public boolean isEventSystemEnabled() {
        return config.getBoolean("events.enabled", true);
    }
    
    public java.util.List<String> getExcludedWorlds() {
        return config.getStringList("events.excluded_worlds");
    }
    
    public java.util.List<String> getExcludedGameModes() {
        return config.getStringList("events.excluded_game_modes");
    }
    
    public boolean isEventCachingEnabled() {
        return config.getBoolean("events.caching.enabled", true);
    }
    
    public long getEventCacheExpiry() {
        return config.getLong("events.caching.expiry_time", 300000); // 5分
    }
    
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    public double getJobExpMultiplier(String jobName) {
        return config.getDouble("jobs.job_settings." + jobName + ".exp_multiplier", 1.0);
    }
    
    public double getJobBaseSellBonus(String jobName) {
        return config.getDouble("jobs.job_settings." + jobName + ".base_sell_bonus", 0.05);
    }
    
    
    // 土地保護設定
    public boolean isWorldGuardIntegration() {
        return config.getBoolean("land_protection.worldguard_integration", true);
    }
    
    public double getUrbanLandPrice() {
        return config.getDouble("land_protection.urban_land_price", 100.0);
    }
    
    public int getMaxLandsPerPlayer() {
        return config.getInt("land_protection.max_lands_per_player", 5);
    }
    
    // メッセージ設定
    public String getMessagePrefix() {
        return config.getString("messages.prefix", "&6[TofuNomics] &f");
    }
    
    public String getMessage(String key) {
    String fullKey = "messages." + key;
    String message = config.getString(fullKey, "メッセージが見つかりません: " + key);
    
    // デバッグ情報をログに出力
    if (plugin.getConfig().getBoolean("debug.enabled", false)) {
        plugin.getLogger().info("getMessage(): key=" + key + ", fullKey=" + fullKey + ", message=" + message);
        if (message.startsWith("メッセージが見つかりません:")) {
            plugin.getLogger().warning("メッセージが見つかりません。設定ファイルを確認してください。");
            plugin.getLogger().info("利用可能なメッセージキー一覧:");
            if (config.getConfigurationSection("messages") != null) {
                for (String availableKey : config.getConfigurationSection("messages").getKeys(true)) {
                    plugin.getLogger().info("  - " + availableKey);
                }
            }
        }
    }
    
    return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
}
    
    public String getMessage(String key, Object... replacements) {
        String message = getMessage(key);
        if (replacements != null) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
                }
            }
        }
        return message;
    }
    
    /**
     * 複数行メッセージを取得
     */
    public java.util.List<String> getMessageList(String key) {
        String fullPath = "messages." + key;
        
        if (config.isList(fullPath)) {
            java.util.List<String> messages = config.getStringList(fullPath);
            if (messages != null && !messages.isEmpty()) {
                return messages;
            }
        } else if (config.contains(fullPath)) {
            // 単一文字列の場合は1行のリストとして返す
            String singleMessage = getMessage(key);
            if (singleMessage != null && !singleMessage.contains("メッセージが見つかりません")) {
                return java.util.Arrays.asList(singleMessage);
            }
        }
        
        // デバッグ情報：重要なエラーのみログ出力
        if (isDebugEnabled()) {
            plugin.getLogger().warning("メッセージパスが見つからない: " + fullPath);
        }
        
        // 空のリストを返すのではなく、nullを返してフォールバック処理を促す
        return null;
    }
    
    /**
     * 複数行メッセージをプレイヤーに送信
     */
    public void sendMessageList(org.bukkit.entity.Player player, String key, Object... replacements) {
        java.util.List<String> messages = getMessageList(key);
        for (String message : messages) {
            if (replacements != null) {
                for (int i = 0; i < replacements.length; i += 2) {
                    if (i + 1 < replacements.length) {
                        message = message.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
                    }
                }
            }
            String coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(coloredMessage);
        }
    }
    
    /**
     * NPC固有の複数行メッセージを取得
     */
    public java.util.List<String> getNPCSpecificMessageList(String npcType, String key) {
        String specificPath = "messages.npc.trading.npc_specific." + npcType + "." + key;
        
        // デバッグレベルログ：必要な時のみ表示
        if (isDebugEnabled()) {
            plugin.getLogger().info("NPCメッセージ検索: npcType=" + npcType + ", key=" + key);
            plugin.getLogger().info("検索パス: " + specificPath);
        }
        
        if (config.isList(specificPath)) {
            java.util.List<String> messages = config.getStringList(specificPath);
            if (messages != null && !messages.isEmpty()) {
                if (isDebugEnabled()) {
                    plugin.getLogger().info("NPC固有メッセージを取得: " + messages.size() + "行");
                }
                return messages;
            }
        }
        
        // NPC固有メッセージがない場合は共通メッセージを使用
        String fallbackPath = "npc.trading." + key;
        java.util.List<String> fallbackMessages = getMessageList(fallbackPath);
        
        // 重要：メッセージが見つからない場合の最終フォールバック
        if (fallbackMessages == null || fallbackMessages.isEmpty()) {
            plugin.getLogger().warning("NPCメッセージが見つかりません: " + specificPath + " (フォールバック: " + fallbackPath + ")");
            return java.util.Arrays.asList("§c申し訳ありませんが、メッセージの読み込みでエラーが発生しました。");
        }
        
        return fallbackMessages;
    }

    
    /**
     * NPC固有の単一メッセージを取得
     */
    public String getNPCSpecificMessage(String npcType, String messageType, String playerName) {
        try {
            // まずリスト形式のメッセージを確認
            List<String> messages = getNPCSpecificMessageList(npcType, messageType);
            if (!messages.isEmpty()) {
                // 複数メッセージがある場合はランダムに選択
                String message = messages.get(new java.util.Random().nextInt(messages.size()));
                return message.replace("%player%", playerName);
            }
            
            // 単一メッセージを確認
            String path = "messages.npc.trading.npc_specific." + npcType + "." + messageType;
            if (config.contains(path)) {
                String message = config.getString(path);
                if (message != null) {
                    return message.replace("%player%", playerName);
                }
            }
            
            // デフォルトメッセージを返す
            String defaultPath = "npc_system.trading_npcs.default_messages." + messageType;
            if (config.contains(defaultPath)) {
                String message = config.getString(defaultPath);
                if (message != null) {
                    return message.replace("%player%", playerName).replace("%npc_name%", npcType);
                }
            }
            
            return "§7[" + npcType + "] メッセージが設定されていません";
            
        } catch (Exception e) {
            plugin.getLogger().warning("NPCメッセージの取得中にエラーが発生: " + e.getMessage());
            return "§cメッセージの取得に失敗しました";
        }
    }
    
    /**
     * NPC固有の複数行メッセージをプレイヤーに送信
     */
    public void sendNPCSpecificMessageList(org.bukkit.entity.Player player, String npcType, String key, Object... replacements) {
        try {
            java.util.List<String> messages = getNPCSpecificMessageList(npcType, key);
            
            if (messages == null || messages.isEmpty()) {
                plugin.getLogger().warning("NPCメッセージが見つかりません: " + npcType + "." + key);
                player.sendMessage("§c申し訳ありませんが、メッセージの読み込みに失敗しました。");
                return;
            }
            
            // メッセージの送信
            int sentCount = 0;
            for (String message : messages) {
                if (message == null || message.trim().isEmpty()) {
                    continue;
                }
                
                // 置換処理
                String processedMessage = message;
                if (replacements != null) {
                    for (int i = 0; i < replacements.length; i += 2) {
                        if (i + 1 < replacements.length) {
                            String placeholder = "%" + replacements[i] + "%";
                            String replacement = String.valueOf(replacements[i + 1]);
                            processedMessage = processedMessage.replace(placeholder, replacement);
                        }
                    }
                }
                
                String coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', processedMessage);
                player.sendMessage(coloredMessage);
                sentCount++;
            }
            
            // デバッグログ：送信成功の場合のみ
            if (isDebugEnabled() && sentCount > 0) {
                plugin.getLogger().info("NPCメッセージ送信完了: " + sentCount + "行 (" + npcType + "." + key + ")");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPCメッセージ送信中にエラー: " + e.getMessage());
            player.sendMessage("§cメッセージ処理中にエラーが発生しました。");
        }
    }
    
    /**
     * NPC挨拶メッセージをプレイヤーに送信
     */
    public void sendNPCWelcomeMessage(org.bukkit.entity.Player player, String npcType) {
        try {
            String welcomePath = "messages.npc_specific." + npcType + ".welcome";
            
            if (config.contains(welcomePath)) {
                String welcomeMessage = config.getString(welcomePath, "§6「いらっしゃいませ、%player%さん！」");
                
                // プレイヤー名の置換
                String processedMessage = welcomeMessage.replace("%player%", player.getName());
                
                // 色コード変換
                String coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', processedMessage);
                
                // メッセージ送信
                player.sendMessage(coloredMessage);
                
                // デバッグログ
                if (isDebugEnabled()) {
                    plugin.getLogger().info("NPC挨拶メッセージ送信: " + npcType + " -> " + player.getName());
                }
            } else {
                // デフォルト挨拶メッセージ
                String defaultWelcome = "§6「いらっしゃいませ、" + player.getName() + "さん！」";
                player.sendMessage(defaultWelcome);
                
                if (isDebugEnabled()) {
                    plugin.getLogger().warning("挨拶メッセージが見つかりません: " + welcomePath + " (デフォルト使用)");
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPC挨拶メッセージ送信中にエラー: " + e.getMessage());
            player.sendMessage("§6「いらっしゃいませ、" + player.getName() + "さん！」");
        }
    }
    
    // デバッグ設定
    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }
    
    public boolean isVerboseEnabled() {
        return config.getBoolean("debug.verbose", false);
    }
    
    // 職業変更制限の日付チェック用メソッド
    public boolean isDailyJobChangeLimitEnabled() {
        return getJobChangeCooldown() >= 86400;
    }
    
    // 取引システム設定（フェーズ4）
    public boolean isTradeSystemEnabled() {
        return config.getBoolean("trade_system.enabled", true);
    }
    
    public boolean isTradeConfirmationRequired() {
        return config.getBoolean("trade_system.confirmation_required", true);
    }
    
    public double getTradePriceMultiplier() {
        return config.getDouble("trade_system.global_price_multiplier", 1.0);
    }
    
    public double getJobPriceMultiplier(String jobType) {
        return config.getDouble("trade_system.job_price_multipliers." + jobType.toLowerCase(), 1.0);
    }
    
    public int getTradeHistoryMaxDays() {
        return config.getInt("trade_system.history.max_days", 30);
    }
    
    public int getTradeHistoryMaxRecordsPerPlayer() {
        return config.getInt("trade_system.history.max_records_per_player", 1000);
    }
    
    public boolean isTradeHistoryAutoCleanupEnabled() {
        return config.getBoolean("trade_system.history.auto_cleanup", true);
    }
    
    public int getMaxTradesPerDay() {
        return config.getInt("trade_system.limits.max_trades_per_day", 0);
    }
    
    public int getMaxItemsPerTrade() {
        return config.getInt("trade_system.limits.max_items_per_trade", 2304);
    }
    
    public double getMaxEarningsPerDay() {
        return config.getDouble("trade_system.limits.max_earnings_per_day", 0.0);
    }
    
    public int getMaxChestsPerJob() {
        return config.getInt("trade_system.chest_settings.max_chests_per_job", 10);
    }
    
    public boolean isPreventDuplicateLocationEnabled() {
        return config.getBoolean("trade_system.chest_settings.prevent_duplicate_location", true);
    }
    
    public boolean isRequireConfirmationOnRemove() {
        return config.getBoolean("trade_system.chest_settings.require_confirmation_on_remove", true);
    }
    
    public String getTradeMessage(String key) {
        return config.getString("trade_system.messages." + key, "メッセージが見つかりません: " + key);
    }
    
    public String getTradeMessage(String key, Object... replacements) {
        String message = getTradeMessage(key);
        if (replacements != null) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
                }
            }
        }
        return message;
    }
    
    // ========== フェーズ6: 職業別スキル設定 ==========
    
    /**
     * 職業別スキルの基本発動確率を取得
     */
    public double getJobSkillBaseProbability(String jobName, String skillName) {
        return config.getDouble("job_skills." + jobName + "." + skillName + ".base_probability", 0.0);
    }
    
    /**
     * 職業別スキルのレベルボーナスを取得
     */
    public double getJobSkillLevelBonus(String jobName, String skillName) {
        return config.getDouble("job_skills." + jobName + "." + skillName + ".level_bonus", 0.0);
    }
    
    /**
     * 職業別スキルの最大発動確率を取得
     */
    public double getJobSkillMaxProbability(String jobName, String skillName) {
        return config.getDouble("job_skills." + jobName + "." + skillName + ".max_probability", 1.0);
    }
    
    /**
     * 職業別スキルのクールダウン時間を取得
     */
    public int getJobSkillCooldown(String jobName, String skillName) {
        return config.getInt("job_skills." + jobName + "." + skillName + ".cooldown_seconds", 0);
    }
    
    /**
     * 職業別スキルの効果倍率を取得
     */
    public double getJobSkillEffectMultiplier(String jobName, String skillName) {
        return config.getDouble("job_skills." + jobName + "." + skillName + ".effect_multiplier", 1.0);
    }
    
    /**
     * 職業別スキルの効果範囲を取得
     */
    public int getJobSkillEffectRange(String jobName, String skillName) {
        return config.getInt("job_skills." + jobName + "." + skillName + ".effect_range", 1);
    }
    
    /**
     * 職業別スキルの節約率を取得
     */
    public double getJobSkillSaveRate(String jobName, String skillName) {
        return config.getDouble("job_skills." + jobName + "." + skillName + ".save_rate", 0.0);
    }
    
    /**
     * 職業別スキルの最大ブロック数を取得
     */
    public int getJobSkillMaxBlocks(String jobName, String skillName) {
        return config.getInt("job_skills." + jobName + "." + skillName + ".max_blocks", 64);
    }
    
    /**
     * スキル確率を計算（レベル考慮）
     */
    public double calculateSkillProbability(String jobName, String skillName, int level) {
        double baseProbability = getJobSkillBaseProbability(jobName, skillName);
        double levelBonus = getJobSkillLevelBonus(jobName, skillName);
        double maxProbability = getJobSkillMaxProbability(jobName, skillName);
        
        double calculatedProbability = baseProbability + (levelBonus * level);
        return Math.min(calculatedProbability, maxProbability);
    }
    
    // ========== レベルアップシステム設定 ==========
    
    /**
     * 基本経験値倍率を取得
     */
    public int getExperienceBaseMultiplier() {
        return config.getInt("leveling.experience.base_multiplier", 100);
    }
    
    /**
     * 経験値計算指数を取得
     */
    public double getExperienceExponent() {
        return config.getDouble("leveling.experience.exponent", 2.0);
    }
    
    /**
     * レベル範囲別倍率を取得
     */
    public double getLevelScalingMultiplier(int level) {
        if (level >= 1 && level <= 10) {
            return config.getDouble("leveling.experience.level_scaling.early_levels.multiplier", 1.0);
        } else if (level >= 11 && level <= 25) {
            return config.getDouble("leveling.experience.level_scaling.mid_levels.multiplier", 0.9);
        } else if (level >= 26 && level <= 50) {
            return config.getDouble("leveling.experience.level_scaling.advanced_levels.multiplier", 1.0);
        } else if (level >= 51 && level <= 75) {
            return config.getDouble("leveling.experience.level_scaling.master_levels.multiplier", 1.5);
        }
        return 1.0;
    }
    
    /**
     * レベルアップ報酬（金塊）の基本額を取得
     */
    public double getLevelRewardBaseAmount() {
        return config.getDouble("leveling.rewards.base_rewards.money.base_amount", 50.0);
    }
    
    /**
     * レベルアップ報酬（金塊）のレベル倍率を取得
     */
    public double getLevelRewardLevelMultiplier() {
        return config.getDouble("leveling.rewards.base_rewards.money.level_multiplier", 2.5);
    }
    
    /**
     * レベルアップ報酬（金塊）の最大額を取得
     */
    public double getLevelRewardMaxAmount() {
        return config.getDouble("leveling.rewards.base_rewards.money.max_amount", 500.0);
    }
    
    /**
     * スキルポイント付与レベルを取得
     */
    public java.util.List<Integer> getSkillPointLevels() {
        return config.getIntegerList("leveling.rewards.base_rewards.skill_points.levels");
    }
    
    // ========== イベント報酬バランス設定 ==========
    
    /**
     * 全体経験値倍率を取得
     */
    public double getGlobalExperienceMultiplier() {
        return config.getDouble("event_rewards.global_multipliers.experience_multiplier", 1.0);
    }
    
    /**
     * 全体収入倍率を取得
     */
    public double getGlobalIncomeMultiplier() {
        return config.getDouble("event_rewards.global_multipliers.income_multiplier", 1.0);
    }
    
    /**
     * スキル発動確率倍率を取得
     */
    public double getGlobalSkillProbabilityMultiplier() {
        return config.getDouble("event_rewards.global_multipliers.skill_probability_multiplier", 1.0);
    }
    
    /**
     * 時間帯別ボーナスを取得
     */
    public double getTimeBonus(String timeOfDay, String bonusType) {
        return config.getDouble("event_rewards.global_multipliers.time_bonuses." + timeOfDay + "." + bonusType, 0.0);
    }
    
    /**
     * 天候ボーナスを取得
     */
    public double getWeatherBonus(String weather, String bonusType) {
        return config.getDouble("event_rewards.global_multipliers.weather_bonuses." + weather + "." + bonusType, 0.0);
    }
    
    /**
     * 個別イベントの基本経験値を取得
     */
    public double getIndividualEventBaseExperience(String eventType) {
        return config.getDouble("event_rewards.individual_events." + eventType + ".base_experience", 1.0);
    }
    
    /**
     * 個別イベントの基本収入を取得
     */
    public double getIndividualEventBaseIncome(String eventType) {
        return config.getDouble("event_rewards.individual_events." + eventType + ".base_income", 1.0);
    }
    
    /**
     * 個別イベントのレベル経験値ボーナスを取得
     */
    public double getIndividualEventLevelExperienceBonus(String eventType) {
        return config.getDouble("event_rewards.individual_events." + eventType + ".level_experience_bonus", 0.0);
    }
    
    /**
     * 個別イベントのレベル収入ボーナスを取得
     */
    public double getIndividualEventLevelIncomeBonus(String eventType) {
        return config.getDouble("event_rewards.individual_events." + eventType + ".level_income_bonus", 0.0);
    }
    
    // ========== パフォーマンス最適化設定 ==========
    
    /**
     * コネクションプール有効化状態を取得
     */
    public boolean isConnectionPoolEnabled() {
        return config.getBoolean("performance.database.connection_pool.enabled", true);
    }
    
    /**
     * 最大コネクションプールサイズを取得
     */
    public int getMaximumPoolSize() {
        return config.getInt("performance.database.connection_pool.maximum_pool_size", 15);
    }
    
    /**
     * 最小アイドル接続数を取得
     */
    public int getMinimumIdle() {
        return config.getInt("performance.database.connection_pool.minimum_idle", 3);
    }
    
    /**
     * 接続タイムアウト時間を取得
     */
    public long getConnectionTimeout() {
        return config.getLong("performance.database.connection_pool.connection_timeout", 30000);
    }
    
    /**
     * プレイヤーキャッシュ有効化状態を取得
     */
    public boolean isPlayerCacheEnabled() {
        return config.getBoolean("performance.caching.player_cache.enabled", true);
    }
    
    /**
     * プレイヤーキャッシュ最大サイズを取得
     */
    public int getPlayerCacheMaxSize() {
        return config.getInt("performance.caching.player_cache.max_size", 1000);
    }
    
    /**
     * プレイヤーキャッシュアクセス後期限を取得（秒）
     */
    public long getPlayerCacheExpireAfterAccess() {
        return config.getLong("performance.caching.player_cache.expire_after_access", 1800);
    }
    
    /**
     * プレイヤーキャッシュ書き込み後期限を取得（秒）
     */
    public long getPlayerCacheExpireAfterWrite() {
        return config.getLong("performance.caching.player_cache.expire_after_write", 3600);
    }
    
    /**
     * 職業キャッシュ有効化状態を取得
     */
    public boolean isJobCacheEnabled() {
        return config.getBoolean("performance.caching.job_cache.enabled", true);
    }
    
    /**
     * バッチ処理有効化状態を取得
     */
    public boolean isBatchProcessingEnabled() {
        return config.getBoolean("performance.database.batch_processing.enabled", true);
    }
    
    /**
     * バッチサイズを取得
     */
    public int getBatchSize() {
        return config.getInt("performance.database.batch_processing.batch_size", 100);
    }
    
    /**
     * バッチタイムアウトを取得
     */
    public int getBatchTimeout() {
        return config.getInt("performance.database.batch_processing.batch_timeout", 5000);
    }
    
    /**
     * メモリ監視有効化状態を取得
     */
    public boolean isMemoryMonitoringEnabled() {
        return config.getBoolean("performance.memory_management.memory_monitoring.enabled", true);
    }
    
    /**
     * メモリ警告閾値を取得
     */
    public double getMemoryWarningThreshold() {
        return config.getDouble("performance.memory_management.memory_monitoring.warning_threshold", 0.8);
    }
    
    /**
     * パフォーマンス統計収集有効化状態を取得
     */
    public boolean isStatisticsEnabled() {
        return config.getBoolean("performance.monitoring.statistics.enabled", true);
    }
    
    /**
     * 統計収集間隔を取得（秒）
     */
    public int getStatisticsCollectionInterval() {
        return config.getInt("performance.monitoring.statistics.collection_interval", 300);
    }
    
    /**
     * TPS警告閾値を取得
     */
    public double getTpsWarningThreshold() {
        return config.getDouble("performance.monitoring.realtime_monitoring.tps_threshold", 18.0);
    }
    
    // ========== 設定値検証メソッド ==========
    
    /**
     * 設定値が有効な範囲内かチェック
     */
    public boolean isValidProbability(double value) {
        return value >= 0.0 && value <= 1.0;
    }
    
    /**
     * 設定値が正の数かチェック
     */
    public boolean isPositiveNumber(double value) {
        return value > 0.0;
    }
    
    /**
     * 設定値が有効なレベル範囲内かチェック
     */
    public boolean isValidLevel(int level) {
        return level >= 1 && level <= 75;
    }
    
    /**
     * レベルに応じた必要経験値を計算
     */
    public int calculateRequiredExperience(int level) {
        if (level <= 0) return 0;
        
        double baseMultiplier = getExperienceBaseMultiplier();
        double exponent = getExperienceExponent();
        double scalingMultiplier = getLevelScalingMultiplier(level);
        
        return (int) (baseMultiplier * Math.pow(level, exponent) * scalingMultiplier);
    }
    
    /**
     * レベルアップ時の金塊報酬を計算
     */
    public double calculateLevelUpReward(int level) {
        double baseAmount = getLevelRewardBaseAmount();
        double levelMultiplier = getLevelRewardLevelMultiplier();
        double maxAmount = getLevelRewardMaxAmount();
        
        double calculatedAmount = baseAmount + (level * levelMultiplier);
        return Math.min(calculatedAmount, maxAmount);
    }
    
    // ========== 拡張機能メソッド ==========
    
    /**
     * 設定の妥当性検証結果を取得
     */
    public Set<String> getValidationErrors() {
        return new HashSet<>(validationErrors);
    }
    
    /**
     * 設定が有効かチェック
     */
    public boolean isConfigValid() {
        return validationErrors.isEmpty();
    }
    
    /**
     * 最後のリロード時刻を取得
     */
    public long getLastReloadTime() {
        return lastReloadTime;
    }
    
    /**
     * 設定統計情報を取得
     */
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("last_reload", new Date(lastReloadTime));
        stats.put("validation_errors", validationErrors.size());
        stats.put("cache_size", configCache.size());
        stats.put("listeners_count", listeners.size());
        stats.put("config_valid", validationErrors.isEmpty());
        return stats;
    }
    
    /**
     * 設定セクション別統計を取得
     */
    public Map<String, Integer> getSectionStats() {
        Map<String, Integer> sectionCounts = new HashMap<>();
        
        for (String key : configCache.keySet()) {
            String section = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
            sectionCounts.put(section, sectionCounts.getOrDefault(section, 0) + 1);
        }
        
        return sectionCounts;
    }
    
    /**
     * 特定設定パスの値の型チェック
     */
    public boolean isConfigPathOfType(String path, Class<?> expectedType) {
        Object value = config.get(path);
        return value != null && expectedType.isInstance(value);
    }
    
    /**
     * 設定ファイルの整合性をチェック
     */
    public boolean checkConfigIntegrity() {
        try {
            plugin.saveDefaultConfig();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("設定ファイルの整合性チェックに失敗: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * デフォルト設定値に戻す
     */
    public void resetToDefaults() {
        try {
            plugin.saveDefaultConfig();
            reloadConfig();
            plugin.getLogger().info("設定をデフォルトに戻しました。");
        } catch (Exception e) {
            plugin.getLogger().severe("設定のリセット中にエラーが発生: " + e.getMessage());
        }
    }
    
    /**
     * 設定値の動的更新（runtime中の一時的変更）
     */
    public void updateConfigValue(String path, Object value) {
        if (config != null) {
            config.set(path, value);
            configCache.put(path, value);
            
            String section = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
            notifyListeners(section);
            
            plugin.getLogger().info("設定値を更新しました: " + path + " = " + value);
        }
    }
    
    /**
     * 設定のバックアップ作成
     */
    public boolean createConfigBackup() {
        try {
            java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
            java.io.File backupFile = new java.io.File(plugin.getDataFolder(), 
                "config_backup_" + System.currentTimeMillis() + ".yml");
                
            java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath());
            plugin.getLogger().info("設定のバックアップを作成しました: " + backupFile.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("設定のバックアップ作成に失敗: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 設定値の変更履歴を記録
     */
    private final Map<String, List<ConfigChange>> changeHistory = new ConcurrentHashMap<>();
    
    private static class ConfigChange {
        public final long timestamp;
        public final Object oldValue;
        public final Object newValue;
        
        public ConfigChange(Object oldValue, Object newValue) {
            this.timestamp = System.currentTimeMillis();
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
    
    /**
     * 設定変更を履歴に記録
     */
    private void recordConfigChange(String path, Object oldValue, Object newValue) {
        changeHistory.computeIfAbsent(path, k -> new ArrayList<>())
                    .add(new ConfigChange(oldValue, newValue));
        
        List<ConfigChange> history = changeHistory.get(path);
        if (history.size() > 10) {
            history.remove(0);
        }
    }
    
    /**
     * 設定変更履歴を取得
     */
    public Map<String, List<ConfigChange>> getChangeHistory() {
        return new HashMap<>(changeHistory);
    }
    
    /**
     * 設定のホットリロード（一部設定のみ）
     */
    public void hotReloadSection(String section) {
        try {
            plugin.reloadConfig();
            FileConfiguration newConfig = plugin.getConfig();
            
            Set<String> sectionKeys = new HashSet<>();
            for (String key : configCache.keySet()) {
                if (key.startsWith(section + ".")) {
                    sectionKeys.add(key);
                }
            }
            
            for (String key : sectionKeys) {
                Object oldValue = configCache.get(key);
                Object newValue = newConfig.get(key);
                
                if (!Objects.equals(oldValue, newValue)) {
                    configCache.put(key, newValue);
                    recordConfigChange(key, oldValue, newValue);
                }
            }
            
            config = newConfig;
            notifyListeners(section);
            
            plugin.getLogger().info("設定セクション '" + section + "' をホットリロードしました。");
            
        } catch (Exception e) {
            plugin.getLogger().severe("設定セクションのホットリロード中にエラーが発生: " + e.getMessage());
        }
    }
    
    // ========== プレイヤー参加時の設定 ==========
    
    /**
     * ウェルカムメッセージ一覧を取得
     */
    public java.util.List<String> getWelcomeMessages() {
        return config.getStringList("player_join.welcome_messages");
    }
    
    /**
     * ウェルカムタイトルを取得
     */
    public String getWelcomeTitle() {
        return config.getString("player_join.welcome_title", "&6&lようこそ TofuNomicsワールドへ!");
    }
    
    /**
     * ウェルカムサブタイトルを取得
     */
    public String getWelcomeSubtitle() {
        return config.getString("player_join.welcome_subtitle", "&e%player%さん、お帰りなさい!");
    }
    
    /**
     * 新規プレイヤーボーナスの有効化状態を取得
     */
    public boolean isNewPlayerBonusEnabled() {
        return config.getBoolean("player_join.new_player_bonus.enabled", true);
    }
    
    /**
     * 新規プレイヤーボーナス金額を取得
     */
    public double getNewPlayerBonusAmount() {
        return config.getDouble("player_join.new_player_bonus.amount", 100.0);
    }
    
    /**
     * 新規プレイヤーメッセージを取得
     */
    public java.util.List<String> getNewPlayerMessages() {
        return config.getStringList("player_join.new_player_bonus.messages");
    }
    
    /**
     * 復帰プレイヤーメッセージの有効化状態を取得
     */
    public boolean isWelcomeBackMessageEnabled() {
        return config.getBoolean("player_join.welcome_back_message.enabled", true);
    }
    
    /**
     * 復帰プレイヤーメッセージを取得
     */
    public String getWelcomeBackMessage() {
        return config.getString("player_join.welcome_back_message.message", "&aおかえりなさい、%player%さん!");
    }
    
    /**
     * 復帰判定日数を取得（この日数以上離れていたら復帰扱い）
     */
    public int getWelcomeBackDays() {
        return config.getInt("player_join.welcome_back_message.days_threshold", 7);
    }
    
    /**
     * スポーン座標機能の有効化状態を取得
     */
    public boolean isSpawnLocationEnabled() {
        return config.getBoolean("player_join.spawn_location.enabled", false);
    }
    
    /**
     * スポーン座標のワールド名を取得
     */
    public String getSpawnWorldName() {
        return config.getString("player_join.spawn_location.world", "world");
    }
    
    /**
     * スポーン座標のX座標を取得
     */
    public int getSpawnX() {
        return config.getInt("player_join.spawn_location.x", 0);
    }
    
    /**
     * スポーン座標のY座標を取得
     */
    public int getSpawnY() {
        return config.getInt("player_join.spawn_location.y", 64);
    }
    
    /**
     * スポーン座標のZ座標を取得
     */
    public int getSpawnZ() {
        return config.getInt("player_join.spawn_location.z", 0);
    }
    
    /**
     * スポーン座標テレポートの遅延時間を取得（tick）
     */
    public int getSpawnTeleportDelay() {
        return config.getInt("player_join.spawn_location.teleport_delay", 60);
    }
    
    // ==================== スコアボード設定関連メソッド ====================
    
    /**
     * スコアボード機能が有効かどうかを取得
     */
    public boolean isScoreboardEnabled() {
        return (Boolean) getCachedValue("scoreboard.enabled", true);
    }
    
    /**
     * スコアボードのデフォルト表示設定を取得
     */
    public boolean isScoreboardDefaultEnabled() {
        return (Boolean) getCachedValue("scoreboard.default_enabled", true);
    }
    
    /**
     * スコアボードタイトルを取得
     */
    public String getScoreboardTitle() {
        return (String) getCachedValue("scoreboard.title", "&6&l★ &eTofuNomics &6&l★");
    }
    
    /**
     * スコアボード更新間隔を取得（秒）
     */
    public int getScoreboardUpdateInterval() {
        return (Integer) getCachedValue("scoreboard.update_interval", 1);
    }
    
    /**
     * スコアボードでプレイヤー名を表示するかどうか
     */
    public boolean isScoreboardShowPlayerName() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_player_name", true);
    }
    
    /**
     * スコアボードで残高を表示するかどうか
     */
    public boolean isScoreboardShowBalance() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_balance", true);
    }
    
    /**
     * スコアボードで職業を表示するかどうか
     */
    public boolean isScoreboardShowJob() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_job", true);
    }
    
    /**
     * スコアボードで職業レベルを表示するかどうか
     */
    public boolean isScoreboardShowJobLevel() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_job_level", true);
    }
    
    /**
     * スコアボードで経験値を表示するかどうか
     */
    public boolean isScoreboardShowExperience() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_experience", true);
    }
    
    /**
     * スコアボードでオンライン時間を表示するかどうか
     */
    public boolean isScoreboardShowOnlineTime() {
        return (Boolean) getCachedValue("scoreboard.display_settings.show_online_time", true);
    }
    
    /**
     * スコアボード表示対象ワールド一覧を取得
     */
    public java.util.List<String> getScoreboardEnabledWorlds() {
        return config.getStringList("scoreboard.enabled_worlds");
    }
    
    /**
     * 指定されたワールドでスコアボードを表示するかどうか
     */
    public boolean isScoreboardEnabledInWorld(String worldName) {
        java.util.List<String> enabledWorlds = getScoreboardEnabledWorlds();
        return enabledWorlds.contains(worldName);
    }
    
    /**
     * 職業の最大レベルを取得
     */
    public int getMaxJobLevel() {
        // すべての職業で共通の最大レベル（75）を返す
        return 75;
    }
    
    // ========== 銀行・ATM場所制限設定 ==========
    
    /**
     * 場所制限機能の有効/無効
     */
    public boolean isLocationRestrictionsEnabled() {
        return config.getBoolean("economy.location_restrictions.enabled", true);
    }
    
    /**
     * 銀行・ATMアクセス可能範囲
     */
    public int getBankAccessRange() {
        return config.getInt("economy.location_restrictions.access_range", 5);
    }
    
    /**
     * 銀行の場所一覧
     */
    public List<Map<?, ?>> getBankLocations() {
        return config.getMapList("npc_system.bank_npc.locations");
    }
    
    /**
     * ATMの場所一覧
     */
    public List<Map<?, ?>> getAtmLocations() {
        return config.getMapList("economy.location_restrictions.atms");
    }
    
    // ========== NPCシステム設定 ==========
    
    /**
     * NPCシステムの有効化状態を取得
     */
    public boolean isNPCSystemEnabled() {
        return config.getBoolean("npc_system.enabled", true);
    }
    
    /**
     * NPCとの相互作用クールダウンを取得（ミリ秒）
     */
    public long getNPCInteractionCooldownMs() {
        return config.getLong("npc_system.interaction.cooldown_ms", 1000);
    }
    
    /**
     * NPCセッションのタイムアウトを取得（ミリ秒）
     */
    public long getNPCInteractionTimeoutMs() {
        return config.getLong("npc_system.interaction.session_timeout_ms", 300000);
    }
    
    /**
     * NPCへのアクセス範囲を取得
     */
    public int getNPCAccessRange() {
        return config.getInt("npc_system.interaction.access_range", 5);
    }
    
    // ========== 銀行NPC設定 ==========
    
    /**
     * 銀行NPCシステムの有効化状態を取得
     */
    public boolean isBankNPCEnabled() {
        return config.getBoolean("npc_system.bank_npcs.enabled", true);
    }
    
    /**
     * 銀行NPCの場所一覧を取得
     */
    public List<Map<?, ?>> getBankNPCs() {
        return config.getMapList("npc_system.bank_npc.locations");
    }
    
    /**
     * 銀行NPCの名前を取得
     */
    public String getBankNPCName(String locationType) {
        String defaultName = "§6銀行員";
        if (locationType != null) {
            switch (locationType.toLowerCase()) {
                case "main_bank":
                    return config.getString("npc_system.bank_npcs.locations.0.name", "§6中央銀行員");
                case "branch_bank":
                    return config.getString("npc_system.bank_npcs.locations.1.name", "§e支店銀行員");
                case "atm_assistant":
                    return config.getString("npc_system.bank_npcs.locations.2.name", "§bATM案内係");
                default:
                    return defaultName;
            }
        }
        return defaultName;
    }
    
    /**
     * ATM NPCの名前を取得
     */
    public String getATMNPCName(String locationName) {
        return config.getString("npc_system.bank_npcs.messages.atm_greeting", "§bATM案内係");
    }
    
    /**
     * 銀行NPCの挨拶メッセージを取得
     */
    public String getBankNPCGreeting(String npcName, String playerName) {
        String greeting = config.getString("npc_system.bank_npcs.messages.greeting", 
            "§6こんにちは、%player%さん！%npc_name%へようこそ。");
        return greeting.replace("%player%", playerName).replace("%npc_name%", npcName);
    }
    
    // ========== 取引NPC設定 ==========
    
    /**
     * 取引NPCシステムの有効化状態を取得
     */
    public boolean isTradingNPCEnabled() {
        return config.getBoolean("npc_system.trading_npcs.enabled", true);
    }
    
    /**
     * 取引時間制限の有効化状態を取得
     */
    public boolean isTradingHoursEnabled() {
        return config.getBoolean("npc_system.trading_npcs.trading_hours.enabled", true);
    }
    
    /**
     * 取引開始時刻を取得
     */
    public int getTradingStartHour() {
        return config.getInt("npc_system.trading_npcs.trading_hours.start", 6);
    }
    
    /**
     * 取引終了時刻を取得
     */
    public int getTradingEndHour() {
        return config.getInt("npc_system.trading_npcs.trading_hours.end", 22);
    }
    
    /**
     * 取引所の設定一覧を取得
     */
    public List<Map<?, ?>> getTradingPostConfigs() {
        return config.getMapList("npc_system.trading_posts");
    }
    
    /**
     * 新しい取引所をconfig.ymlに追加
     */
    public void addTradingPost(String npcName, org.bukkit.Location location, String jobType) {
        try {
            // 既存の取引所リスト取得
            java.util.List<java.util.Map<String, Object>> tradingPosts = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingPosts = config.getMapList("npc_system.trading_posts");
            for (java.util.Map<?, ?> post : existingPosts) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> castedPost = (java.util.Map<String, Object>) post;
                tradingPosts.add(castedPost);
            }
            
            // 新しい取引所データ作成
            java.util.Map<String, Object> newPost = new java.util.HashMap<>();
            newPost.put("id", npcName.toLowerCase().replace(" ", "_").replace("　", "_"));
            newPost.put("name", npcName);
            newPost.put("world", location.getWorld().getName());
            newPost.put("x", location.getBlockX());
            newPost.put("y", location.getBlockY());
            newPost.put("z", location.getBlockZ());
            newPost.put("accepted_jobs", java.util.Arrays.asList(jobType));
            newPost.put("description", jobType + "専用の取引所");
            
            // リストに追加
            tradingPosts.add(newPost);
            
            // 設定更新・保存
            updateConfigValue("npc_system.trading_posts", tradingPosts);
            plugin.saveConfig();
            
            plugin.getLogger().info("取引所データを追加しました: " + npcName + " (" + jobType + ")");
            
        } catch (Exception e) {
            plugin.getLogger().severe("取引所データの追加に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 新しい銀行NPCをconfig.ymlに追加
     */
    public void addBankNPC(String npcName, org.bukkit.Location location, String npcType) {
        try {
            // 既存の銀行NPCリスト取得
            java.util.List<java.util.Map<String, Object>> bankNPCs = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingNPCs = config.getMapList("npc_system.bank_npc.locations");
            
            // 新しい銀行NPCデータ作成
            java.util.Map<String, Object> newNPC = new java.util.HashMap<>();
            newNPC.put("world", location.getWorld().getName());
            newNPC.put("x", location.getBlockX());
            newNPC.put("y", location.getBlockY());
            newNPC.put("z", location.getBlockZ());
            newNPC.put("type", npcType);  // "bank" または "atm"
            newNPC.put("name", npcName);
            // 適切な挨拶メッセージを作成（nullチェック付き）
            String greeting = npcName != null && !npcName.trim().isEmpty() ? 
                "§6いらっしゃいませ、" + npcName + "へようこそ！" : 
                "§6いらっしゃいませ、銀行へようこそ！";
            newNPC.put("greeting", greeting);
            
            // 重複チェック: 同じ座標に既存NPCがないか確認
            int newX = location.getBlockX();
            int newY = location.getBlockY();
            int newZ = location.getBlockZ();
            String newWorld = location.getWorld().getName();
            boolean foundDuplicate = false;
            
            // 既存NPCをチェックして、重複しないものだけをリストに追加
            boolean alreadyAdded = false; // 新しいNPCを既に追加したかのフラグ
            
            for (java.util.Map<?, ?> npc : existingNPCs) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> existingNPC = (java.util.Map<String, Object>) npc;
                
                if (newWorld.equals(existingNPC.get("world")) &&
                    newX == ((Number) existingNPC.get("x")).intValue() &&
                    newY == ((Number) existingNPC.get("y")).intValue() &&
                    newZ == ((Number) existingNPC.get("z")).intValue()) {
                    // 同じ座標の既存NPCを発見
                    if (!alreadyAdded) {
                        // 初回の重複のみ新しいNPCで置換
                        bankNPCs.add(newNPC);
                        alreadyAdded = true;
                        plugin.getLogger().info("同じ座標の既存銀行NPCを更新しました: " + existingNPC.get("name") + " -> " + npcName);
                    } else {
                        // 2回目以降の重複は無視（ログのみ出力）
                        plugin.getLogger().info("重複エントリを除去しました: " + existingNPC.get("name"));
                    }
                    foundDuplicate = true;
                } else {
                    // 重複しないNPCはそのまま保持
                    bankNPCs.add(existingNPC);
                }
            }
            
            // 重複がなかった場合のみ新規追加
            if (!foundDuplicate) {
                bankNPCs.add(newNPC);
            }
            
            // 設定更新・保存
            updateConfigValue("npc_system.bank_npc.locations", bankNPCs);
            plugin.saveConfig();
            
            plugin.getLogger().info("銀行NPCデータを追加しました: " + npcName + " (" + npcType + ")");
            
        } catch (Exception e) {
            plugin.getLogger().severe("銀行NPCデータの追加に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * config.ymlから銀行NPCの位置情報を取得
     */
    public java.util.List<java.util.Map<?, ?>> getBankNPCLocations() {
        return config.getMapList("npc_system.bank_npc.locations");
    }

    /**
     * 新しい食料NPCをconfig.ymlに追加
     */
    public void addFoodNPC(String npcName, org.bukkit.Location location) {
        try {
            // 既存の食料NPCリスト取得
            java.util.List<java.util.Map<String, Object>> foodNPCs = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingNPCs = config.getMapList("npc_system.food_npc.locations");
            
            // 新しい食料NPCデータ作成
            java.util.Map<String, Object> newNPC = new java.util.HashMap<>();
            newNPC.put("world", location.getWorld().getName());
            newNPC.put("x", location.getBlockX());
            newNPC.put("y", location.getBlockY());
            newNPC.put("z", location.getBlockZ());
            newNPC.put("name", npcName);
            newNPC.put("description", "基本的な食料品を販売");
            
            // 重複チェック: 同じ座標に既存NPCがないか確認
            int newX = location.getBlockX();
            int newY = location.getBlockY();
            int newZ = location.getBlockZ();
            String newWorld = location.getWorld().getName();
            boolean foundDuplicate = false;
            
            // 既存NPCをチェックして、重複しないものだけをリストに追加
            boolean alreadyAdded = false; // 新しいNPCを既に追加したかのフラグ
            
            for (java.util.Map<?, ?> npc : existingNPCs) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> existingNPC = (java.util.Map<String, Object>) npc;
                
                if (newWorld.equals(existingNPC.get("world")) &&
                    newX == ((Number) existingNPC.get("x")).intValue() &&
                    newY == ((Number) existingNPC.get("y")).intValue() &&
                    newZ == ((Number) existingNPC.get("z")).intValue()) {
                    // 同じ座標の既存NPCを発見
                    if (!alreadyAdded) {
                        // 初回の重複のみ新しいNPCで置換
                        foodNPCs.add(newNPC);
                        alreadyAdded = true;
                        plugin.getLogger().info("同じ座標の既存食料NPCを更新しました: " + existingNPC.get("name") + " -> " + npcName);
                    } else {
                        // 2回目以降の重複は無視（ログのみ出力）
                        plugin.getLogger().info("重複エントリを除去しました: " + existingNPC.get("name"));
                    }
                    foundDuplicate = true;
                } else {
                    // 重複しないNPCはそのまま保持
                    foodNPCs.add(existingNPC);
                }
            }
            
            // 重複がなかった場合のみ新規追加
            if (!foundDuplicate) {
                foodNPCs.add(newNPC);
            }
            
            // 設定更新・保存
            updateConfigValue("npc_system.food_npc.locations", foodNPCs);
            plugin.saveConfig();
            
            plugin.getLogger().info("食料NPCデータを追加しました: " + npcName);
            
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPCデータの追加に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * NPCメッセージの存在を確認し、不足している場合は自動追加
     */
    public void ensureNPCMessagesExist() {
        try {
            plugin.getLogger().info("NPCメッセージ設定を確認・初期化しています...");
            
            // 基本メッセージを確保
            ensureMessagePath("messages.npc.unknown_type", "&cNPCの種類が不明です。");
            ensureMessagePath("messages.npc.service_error", 
                "&cサービス処理中にエラーが発生しました。しばらく経ってから再度お試しください。");
            
            // 取引NPC基本メッセージを確保
            ensureMessagePath("messages.npc.trading.no_job", 
                "&c職業に就いていません。/jobs join <職業名> で職業に就いてからご利用ください。");
            ensureMessagePath("messages.npc.trading.job_not_accepted", 
                "&c申し訳ありませんが、あなたの職業（%job%）では当店をご利用いただけません。");
            
            // central_market固有メッセージを確保
            ensureMessageListPath("messages.npc.trading.npc_specific.central_market.no_job", 
                java.util.Arrays.asList(
                    "&6「いらっしゃい、お客さん！」",
                    "&e「まずは何か職業に就いてからお越しください」",
                    "&7コマンド: &f/jobs join <職業名>"
                ));
            ensureMessageListPath("messages.npc.trading.npc_specific.central_market.greeting",
                java.util.Arrays.asList(
                    "&6「いらっしゃいませ！中央市場へようこそ！」",
                    "&e「何でも買い取りますよ～」"
                ));
            
            // mining_post固有メッセージを確保
            ensureMessageListPath("messages.npc.trading.npc_specific.mining_post.no_job",
                java.util.Arrays.asList(
                    "&8「ここは鉱夫の取引所だ」",
                    "&7「まずは /jobs join miner で鉱夫になってくれ」"
                ));
            ensureMessageListPath("messages.npc.trading.npc_specific.mining_post.greeting",
                java.util.Arrays.asList(
                    "&8「よう、同志よ。今日はどんな鉱石を掘ってきた？」"
                ));
            
            plugin.getLogger().info("NPCメッセージ設定の確認・初期化が完了しました");
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPCメッセージ初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 指定されたパスに文字列メッセージが存在しない場合は追加
     */
    private void ensureMessagePath(String path, String defaultMessage) {
        if (!config.contains(path)) {
            updateConfigValue(path, defaultMessage);
            plugin.getLogger().info("メッセージを追加しました: " + path);
        }
    }
    
    /**
     * 指定されたパスにリストメッセージが存在しない場合は追加
     */
    private void ensureMessageListPath(String path, java.util.List<String> defaultMessages) {
        if (!config.contains(path)) {
            updateConfigValue(path, defaultMessages);
            plugin.getLogger().info("メッセージリストを追加しました: " + path + " (" + defaultMessages.size() + "行)");
        }
    }
    
    /**
     * クラフト制限メッセージの存在を確認し、不足している場合は自動追加
     */
    public void ensureCraftRestrictionMessagesExist() {
        try {
            plugin.getLogger().info("クラフト制限メッセージ設定を確認・初期化しています...");
            
            // 基本クラフト制限メッセージを確保
            ensureMessagePath("messages.craft.no_job_required", 
                "&c職業に就いていないため、このアイテムをクラフトできません。");
            ensureMessagePath("messages.craft.wrong_job_required", 
                "&c{item}をクラフトするには{required_job}である必要があります。（現在: {current_job}）");
            ensureMessagePath("messages.craft.item_not_craftable", 
                "&cこのアイテムはクラフトできません。");
            
            plugin.getLogger().info("クラフト制限メッセージ設定の確認・初期化が完了しました");
            
        } catch (Exception e) {
            plugin.getLogger().severe("クラフト制限メッセージ初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * player_join設定の存在を確認し、不足している場合は自動追加
     */
    public void ensurePlayerJoinSettingsExist() {
        try {
            plugin.getLogger().info("player_join設定を確認・初期化しています...");
            
            // スポーン座標設定を確保
            ensureConfigPath("player_join.spawn_location.enabled", true);
            ensureConfigPath("player_join.spawn_location.world", "tofuNomics");
            ensureConfigPath("player_join.spawn_location.x", -97);
            ensureConfigPath("player_join.spawn_location.y", 75);
            ensureConfigPath("player_join.spawn_location.z", -247);
            ensureConfigPath("player_join.spawn_location.teleport_delay", 60);
            
            // ウェルカムメッセージ設定を確保
            ensureConfigListPath("player_join.welcome_messages", 
                java.util.Arrays.asList(
                    "&6▬▬▬▬▬▬▬▬ ようこそ TofuNomicsワールドへ! ▬▬▬▬▬▬▬▬",
                    "&e%player%さん、TofuNomicsワールドにようこそ！",
                    "&fこのワールドでは職業に就いて経済活動を楽しむことができます。",
                    "&a初期残高として &f%starting_balance%%currency% &aを受け取りました。",
                    "&b職業システムを活用して、豊かな経済生活を送りましょう！",
                    "&6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ));
            
            // ウェルカムタイトル設定を確保
            ensureConfigPath("player_join.welcome_title", "&6&lようこそ TofuNomicsワールドへ!");
            ensureConfigPath("player_join.welcome_subtitle", "&e%player%さん、経済活動をお楽しみください!");
            
            // 新規プレイヤーボーナス設定を確保
            ensureConfigPath("player_join.new_player_bonus.enabled", true);
            ensureConfigPath("player_join.new_player_bonus.amount", 100.0);
            ensureConfigListPath("player_join.new_player_bonus.messages",
                java.util.Arrays.asList(
                    "&a初めてのご参加ありがとうございます！",
                    "&f職業に就くことで収入を得ることができます。",
                    "&e&l/jobs join <職業名> &rで職業に就職してみましょう！",
                    "&b利用可能な職業: &f鉱夫, 木こり, 農家, 釣り人, 鍛冶屋, ポーション屋, エンチャンター, 建築家",
                    "&dチュートリアルクエストも用意されています。&f /quest &dで確認！"
                ));
            
            // 復帰プレイヤー設定を確保
            ensureConfigPath("player_join.welcome_back_message.enabled", true);
            ensureConfigPath("player_join.welcome_back_message.message", "&6★ おかえりなさい、%player%さん！");
            ensureConfigPath("player_join.welcome_back_message.days_threshold", 7);
            
            plugin.getLogger().info("player_join設定の確認・初期化が完了しました");
            
        } catch (Exception e) {
            plugin.getLogger().severe("player_join設定初期化中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 指定されたパスに設定が存在しない場合は追加
     */
    private void ensureConfigPath(String path, Object defaultValue) {
        if (!config.contains(path)) {
            updateConfigValue(path, defaultValue);
            plugin.getLogger().info("設定を追加しました: " + path + " = " + defaultValue);
        }
    }
    
    /**
     * 指定されたパスにリスト設定が存在しない場合は追加
     */
    private void ensureConfigListPath(String path, java.util.List<?> defaultList) {
        if (!config.contains(path)) {
            updateConfigValue(path, defaultList);
            plugin.getLogger().info("リスト設定を追加しました: " + path + " (" + defaultList.size() + "項目)");
        }
    }
    
    /**
     * 指定された名前の取引所データをconfig.ymlから削除
     */
    public void removeTradingPostByName(String npcName) {
        try {
            plugin.getLogger().info("取引所データ削除を開始: " + npcName);
            
            java.util.List<java.util.Map<String, Object>> tradingPosts = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingPosts = config.getMapList("npc_system.trading_posts");
            
            boolean removed = false;
            for (java.util.Map<?, ?> post : existingPosts) {
                String postName = (String) post.get("name");
                if (!npcName.equals(postName)) {
                    // 削除対象でないものは保持
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> castedPost = (java.util.Map<String, Object>) post;
                    tradingPosts.add(castedPost);
                } else {
                    removed = true;
                    plugin.getLogger().info("取引所データを削除: " + postName);
                }
            }
            
            if (removed) {
                updateConfigValue("npc_system.trading_posts", tradingPosts);
                plugin.saveConfig();
                plugin.getLogger().info("取引所データの削除が完了しました: " + npcName);
            } else {
                plugin.getLogger().warning("削除対象の取引所データが見つかりません: " + npcName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("取引所データ削除エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 指定された名前の銀行NPCをconfig.ymlから削除（物理削除）
     */
    public void removeBankNPCByName(String npcName) {
        try {
            plugin.getLogger().info("銀行NPCデータ削除を開始: " + npcName);
            
            java.util.List<java.util.Map<String, Object>> bankNPCs = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingNPCs = config.getMapList("npc_system.bank_npc.locations");
            
            plugin.getLogger().info("DEBUG: config.ymlから読み込んだ銀行NPC数: " + existingNPCs.size());
            for (int i = 0; i < existingNPCs.size(); i++) {
                java.util.Map<?, ?> npc = existingNPCs.get(i);
                String configName = (String) npc.get("name");
                plugin.getLogger().info("DEBUG: 銀行NPC[" + i + "] = " + configName + " (検索対象: " + npcName + ")");
            }
            
            boolean removed = false;
            for (java.util.Map<?, ?> npc : existingNPCs) {
                String locationName = (String) npc.get("name");
                String actualNPCName = getBankNPCName(locationName); // 実際のNPC名を取得
                
                plugin.getLogger().info("DEBUG: 場所=" + locationName + ", 実際のNPC名=" + actualNPCName);
                
                // 完全一致または表示名での一致をチェック
                boolean shouldDelete = npcName.equals(actualNPCName) || 
                                     npcName.equals(locationName) ||
                                     actualNPCName.contains(npcName) ||
                                     locationName.contains(npcName);
                
                if (!shouldDelete) {
                    // 削除対象でないものは保持
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> castedNPC = (java.util.Map<String, Object>) npc;
                    bankNPCs.add(castedNPC);
                } else {
                    removed = true;
                    plugin.getLogger().info("銀行NPCデータを削除: " + locationName + " (NPC名: " + actualNPCName + ")");
                }
            }
            
            if (removed) {
                updateConfigValue("npc_system.bank_npc.locations", bankNPCs);
                plugin.saveConfig();
                plugin.getLogger().info("銀行NPCデータの削除が完了しました: " + npcName);
            } else {
                plugin.getLogger().warning("削除対象の銀行NPCデータが見つかりません: " + npcName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("銀行NPCデータ削除エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 指定された名前の食料NPCをconfig.ymlから削除（物理削除）
     */
    public void removeFoodNPCByName(String npcName) {
        try {
            plugin.getLogger().info("食料NPCデータ削除を開始: " + npcName);
            
            java.util.List<java.util.Map<String, Object>> foodNPCs = new java.util.ArrayList<>();
            java.util.List<java.util.Map<?, ?>> existingNPCs = config.getMapList("npc_system.food_npc.locations");
            
            plugin.getLogger().info("DEBUG: config.ymlから読み込んだ食料NPC数: " + existingNPCs.size());
            for (int i = 0; i < existingNPCs.size(); i++) {
                java.util.Map<?, ?> npc = existingNPCs.get(i);
                String configName = (String) npc.get("name");
                plugin.getLogger().info("DEBUG: 食料NPC[" + i + "] = " + configName + " (検索対象: " + npcName + ")");
            }
            
            boolean removed = false;
            for (java.util.Map<?, ?> npc : existingNPCs) {
                String npcConfigName = (String) npc.get("name");
                
                plugin.getLogger().info("DEBUG: 食料NPC名=" + npcConfigName);
                
                // 完全一致または部分一致をチェック
                boolean shouldDelete = npcName.equals(npcConfigName) || 
                                     (npcConfigName != null && npcConfigName.contains(npcName)) ||
                                     npcName.contains(npcConfigName);
                
                if (!shouldDelete) {
                    // 削除対象でないものは保持
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> castedNPC = (java.util.Map<String, Object>) npc;
                    foodNPCs.add(castedNPC);
                } else {
                    removed = true;
                    plugin.getLogger().info("食料NPCデータを削除: " + npcConfigName);
                }
            }
            
            if (removed) {
                updateConfigValue("npc_system.food_npc.locations", foodNPCs);
                plugin.saveConfig();
                plugin.getLogger().info("食料NPCデータの削除が完了しました: " + npcName);
            } else {
                plugin.getLogger().warning("削除対象の食料NPCデータが見つかりません: " + npcName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPCデータ削除エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * すべての取引所データをconfig.ymlから削除
     */
    public void clearAllTradingPosts() {
        try {
            plugin.getLogger().info("すべての取引所データを削除しています...");
            updateConfigValue("npc_system.trading_npcs.trading_posts", new java.util.ArrayList<>());
            plugin.saveConfig();
            plugin.getLogger().info("すべての取引所データを削除しました");
        } catch (Exception e) {
            plugin.getLogger().severe("取引所データ全削除エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 指定されたNPCに削除フラグを設定（論理削除）
     */
    public void markNPCAsDeleted(String npcName, String npcType) {
        try {
            plugin.getLogger().info("NPC削除フラグ設定開始: " + npcName + " (" + npcType + ")");
            
            plugin.getLogger().info("=== 包括的NPC削除処理開始: " + npcName + " (タイプ: " + npcType + ") ===");
        
        boolean anyDeletionOccurred = false;
        
        // 1. 指定されたタイプでの物理削除
        if ("trader".equals(npcType)) {
            removeTradingPostByName(npcName);
            anyDeletionOccurred = true;
        } else if ("banker".equals(npcType)) {
            removeBankNPCByName(npcName);
            anyDeletionOccurred = true;
        }
        
        // 2. 他のカテゴリにも同名NPCが存在しないかチェックして削除
        // 取引NPCカテゴリをチェック（指定タイプがtrader以外の場合）
        if (!"trader".equals(npcType)) {
            boolean foundInTrading = getTradingPostConfigs().stream()
                .anyMatch(post -> npcName.equals(post.get("name")));
            if (foundInTrading) {
                plugin.getLogger().warning("同名NPCが取引NPCカテゴリにも存在するため削除します: " + npcName);
                removeTradingPostByName(npcName);
                anyDeletionOccurred = true;
            }
        }
        
        // 銀行NPCカテゴリをチェック（指定タイプがbanker以外の場合）
        if (!"banker".equals(npcType)) {
            boolean foundInBank = getBankNPCs().stream()
                .anyMatch(npc -> npcName.equals(npc.get("name")));
            if (foundInBank) {
                plugin.getLogger().warning("同名NPCが銀行NPCカテゴリにも存在するため削除します: " + npcName);
                removeBankNPCByName(npcName);
                anyDeletionOccurred = true;
            }
        }
        
        if (anyDeletionOccurred) {
            plugin.getLogger().info("設定ファイル保存を実行します...");
            plugin.saveConfig();
            plugin.getLogger().info("包括的NPC削除処理が完了しました: " + npcName);
        } else {
            plugin.getLogger().warning("削除対象のNPCが見つかりませんでした: " + npcName);
        }
            plugin.getLogger().info("NPC削除フラグ設定完了: " + npcName);
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPC削除フラグ設定エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    

    

    
    /**
     * 指定された名前の取引所データが既に存在するかチェック
     */
    public boolean hasTradingPostWithName(String npcName) {
        try {
            java.util.List<java.util.Map<?, ?>> existingPosts = config.getMapList("npc_system.trading_posts");
            for (java.util.Map<?, ?> post : existingPosts) {
                String postName = (String) post.get("name");
                if (npcName.equals(postName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("取引所重複チェック中にエラー: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 取引NPCの挨拶メッセージを取得
     */
    public String getTradingNPCGreeting(String npcName, String playerName) {
        String greeting = config.getString("npc_system.trading_npcs.messages.greeting",
            "§6いらっしゃいませ、%player%さん！%npc_name%へようこそ。");
        return greeting.replace("%player%", playerName).replace("%npc_name%", npcName);
    }
    
    /**
     * 取引詳細情報の表示設定を取得
     */
    public boolean showDetailedTradeInfo() {
        return config.getBoolean("npc_system.trading_npcs.messages.detailed_trade_info", true);
    }
    
    /**
     * アイテムの基本価格一覧を取得
     */
    public Map<String, Double> getItemBasePrices() {
        Map<String, Double> prices = new HashMap<>();
        if (config.getConfigurationSection("npc_system.item_prices") != null) {
            for (String key : config.getConfigurationSection("npc_system.item_prices").getKeys(false)) {
                prices.put(key, config.getDouble("npc_system.item_prices." + key));
            }
        }
        return prices;
    }
    
    /**
     * 特定アイテムの基本価格を取得
     */
    public double getItemBasePrice(String itemName) {
        return config.getDouble("npc_system.item_prices." + itemName.toLowerCase(), 0.0);
    }
    
    /**
     * CurrencyConverterの参照を取得（循環参照回避のため、まず設定から通貨情報を取得）
     */
    public Object getCurrencyConverter() {
        // 実際の実装では、TofuNomicsプラグインから取得する
        // このメソッドは銀行GUIで使用するため仮実装
        return null;
    }
    
    /**
     * NPCバンクでの1回あたりの最大引き出し金額を取得
     */
    public double getMaxWithdrawAmount() {
        return config.getDouble("npc_system.bank_npcs.limits.max_withdraw_amount", 10000.0);
    }
    
    /**
     * NPCバンクでの1回あたりの最大預金金額を取得
     */
    public double getMaxDepositAmount() {
        return config.getDouble("npc_system.bank_npcs.limits.max_deposit_amount", 10000.0);
    }
    
    /**
     * 食料NPCシステムが有効かどうか
     */
    public boolean isFoodNPCEnabled() {
        return config.getBoolean("npc_system.food_npc.enabled", false);
    }
    
    /**
     * 食料NPCの営業時間制限が有効かどうか
     */
    public boolean isFoodNPCOperatingHoursEnabled() {
        return config.getBoolean("npc_system.food_npc.operating_hours.enabled", false);
    }
    
    /**
     * 食料NPCの営業開始時間を取得
     */
    public int getFoodNPCStartHour() {
        return config.getInt("npc_system.food_npc.operating_hours.start_hour", 22);
    }
    
    /**
     * 食料NPCの営業終了時間を取得
     */
    public int getFoodNPCEndHour() {
        return config.getInt("npc_system.food_npc.operating_hours.end_hour", 8);
    }
    
    /**
     * 食料NPCの1日あたりアイテム購入上限を取得
     */
    public int getFoodNPCDailyLimitPerItem() {
        return config.getInt("npc_system.food_npc.purchase_limits.daily_limit_per_item", 32);
    }
    
    /**
     * 食料NPCの1日あたり在庫上限を取得
     */
    public int getFoodNPCDailyStockLimit() {
        return config.getInt("npc_system.food_npc.inventory_system.daily_stock_limit", 64);
    }
    
    /**
     * 食料NPCの設定リストを取得
     */
    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> getFoodNPCConfigs() {
        return (List<Map<?, ?>>) config.getList("npc_system.food_npc.locations", new ArrayList<>());
    }
    
    /**
     * 食料アイテムの価格マップを取得
     */
    @SuppressWarnings("unchecked")
    public Map<String, Double> getFoodItemPrices() {
        ConfigurationSection section = config.getConfigurationSection("npc_system.food_npc.food_items");
        Map<String, Double> prices = new HashMap<>();
        
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof Number) {
                    prices.put(key, ((Number) value).doubleValue());
                }
            }
        }
        
        return prices;
    }
    
    /**
     * 利用可能なNPCタイプ一覧を取得
     */
    public Set<String> getFoodNPCTypes() {
        ConfigurationSection section = config.getConfigurationSection("npc_system.food_npc.npc_types");
        if (section != null) {
            return section.getKeys(false);
        }
        return Collections.emptySet();
    }
    
    /**
     * NPCタイプの設定を取得
     */
    public ConfigurationSection getFoodNPCTypeConfig(String npcType) {
        String path = "npc_system.food_npc.npc_types." + npcType;
        return config.getConfigurationSection(path);
    }
    
    /**
     * NPCタイプの価格倍率を取得
     */
    public double getFoodNPCPriceMultiplier(String npcType) {
        ConfigurationSection section = getFoodNPCTypeConfig(npcType);
        if (section != null) {
            return section.getDouble("price_multiplier", 1.0);
        }
        return 1.0; // デフォルト倍率
    }
    
    /**
     * NPCタイプの表示名を取得
     */
    public String getFoodNPCTypeName(String npcType) {
        ConfigurationSection section = getFoodNPCTypeConfig(npcType);
        if (section != null) {
            return section.getString("name", "§6食料品店");
        }
        return "§6食料品店"; // デフォルト名
    }
    
    /**
     * NPCタイプの販売アイテム一覧を取得
     */
    public List<String> getFoodNPCTypeItems(String npcType) {
        ConfigurationSection section = getFoodNPCTypeConfig(npcType);
        
        if (section != null) {
            return section.getStringList("items");
        }
        return Collections.emptyList();
    }
    
    /**
     * NPCタイプが全商品を販売するかチェック
     */
    public boolean isFoodNPCTypeGeneral(String npcType) {
        List<String> items = getFoodNPCTypeItems(npcType);
        return items.contains("all");
    }
    
    /**
     * 設定ファイルをデフォルト設定で自動更新
     * 不足している設定項目を追加し、既存の設定は保持する
     */
    public void updateConfigWithDefaults() {
        try {
            
            // デフォルト設定をリソースから読み込み
            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream == null) {
                plugin.getLogger().warning("デフォルト設定ファイルが見つかりません");
                return;
            }
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            FileConfiguration currentConfig = plugin.getConfig();
            
            // 設定バックアップ
            createConfigBackup();
            
            // 不足している設定を追加
            boolean configUpdated = false;
            List<String> addedKeys = new ArrayList<>();
            
            for (String key : defaultConfig.getKeys(true)) {
                boolean shouldUpdate = false;
                
                // キーが存在しない場合
                if (!currentConfig.contains(key)) {
                    shouldUpdate = true;
                } 
                // ConfigurationSectionが空の場合も更新対象にする
                else if (currentConfig.isConfigurationSection(key)) {
                    ConfigurationSection currentSection = currentConfig.getConfigurationSection(key);
                    ConfigurationSection defaultSection = defaultConfig.getConfigurationSection(key);
                    
                    if (currentSection != null && defaultSection != null) {
                        // 現在のセクションが空で、デフォルトセクションにはデータがある場合
                        if (currentSection.getKeys(false).isEmpty() && 
                            !defaultSection.getKeys(false).isEmpty()) {
                            shouldUpdate = true;
                        }
                    }
                }
                
                if (shouldUpdate) {
                    Object defaultValue = defaultConfig.get(key);
                    currentConfig.set(key, defaultValue);
                    addedKeys.add(key);
                    configUpdated = true;
                }
            }
            
            // 設定バージョンを更新
            String currentVersion = currentConfig.getString("config_version", "1.0");
            String defaultVersion = defaultConfig.getString("config_version", "1.0");
            
            if (!currentVersion.equals(defaultVersion)) {
                currentConfig.set("config_version", defaultVersion);
                configUpdated = true;
            }
            
            // 設定を保存
            if (configUpdated) {
                plugin.saveConfig();
                plugin.getLogger().info("設定ファイルを自動更新しました。追加された設定: " + addedKeys.size() + "項目");
                
                if (!addedKeys.isEmpty()) {
                    plugin.getLogger().info("追加された設定項目:");
                    for (String key : addedKeys) {
                        plugin.getLogger().info("  - " + key);
                    }
                }
            }
            
            defaultConfigStream.close();
            
        } catch (Exception e) {
            plugin.getLogger().severe("設定ファイルの自動更新中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 完全なデフォルト設定ファイルを生成
     * 既存の設定ファイルを上書きしてクリーンな状態にする
     */
    public boolean generateDefaultConfig() {
        try {
            // 現在の設定をバックアップ
            createConfigBackup();
            
            // 既存の設定ファイルを削除
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                configFile.delete();
            }
            
            // デフォルト設定を強制的に保存
            plugin.saveDefaultConfig();
            
            // 設定をリロード
            plugin.reloadConfig();
            reloadConfig();
            
            plugin.getLogger().info("完全なデフォルト設定ファイルを生成しました。");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("デフォルト設定ファイルの生成中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 既存設定の問題を自動修正
     */
    public boolean fixConfigIssues() {
        try {
            createConfigBackup();
            
            FileConfiguration config = plugin.getConfig();
            boolean configChanged = false;
            List<String> fixedIssues = new ArrayList<>();
            
            // 1. 初期残高設定の修正
            if (!config.contains("economy.starting_balance")) {
                config.set("economy.starting_balance", 100.0);
                configChanged = true;
                fixedIssues.add("economy.starting_balance を追加");
            }
            
            // 2. 食料NPC営業時間の修正（深夜営業→通常営業）
            if (config.getInt("npc_system.food_npc.operating_hours.start_hour", 6) == 22 &&
                config.getInt("npc_system.food_npc.operating_hours.end_hour", 22) == 8) {
                config.set("npc_system.food_npc.operating_hours.start_hour", 6);
                config.set("npc_system.food_npc.operating_hours.end_hour", 22);
                configChanged = true;
                fixedIssues.add("食料NPC営業時間を通常営業（6:00-22:00）に修正");
            }
            
            // 3. 制限値の修正（0から適切な値へ）
            if (config.getInt("trade_system.limits.max_trades_per_day", 0) == 0) {
                config.set("trade_system.limits.max_trades_per_day", 50);
                configChanged = true;
                fixedIssues.add("1日の最大取引回数を50に設定");
            }
            
            if (config.getDouble("trade_system.limits.max_earnings_per_day", 0.0) == 0.0) {
                config.set("trade_system.limits.max_earnings_per_day", 10000.0);
                configChanged = true;
                fixedIssues.add("1日の最大収益を10000に設定");
            }
            
            if (config.getDouble("economy.pay.maximum_amount", 0.0) == 0.0) {
                config.set("economy.pay.maximum_amount", 5000.0);
                configChanged = true;
                fixedIssues.add("送金上限額を5000に設定");
            }
            
            // 4. 空の職業制限ブロックリストの修正
            fixEmptyJobBlocks(config, fixedIssues);
            if (!fixedIssues.isEmpty()) {
                configChanged = true;
            }
            
            // 5. 設定バージョンの更新
            config.set("config_version", "2.1");
            configChanged = true;
            
            if (configChanged) {
                plugin.saveConfig();
                reloadConfig();
                
                plugin.getLogger().info("設定の問題を自動修正しました。修正項目数: " + fixedIssues.size());
                for (String fix : fixedIssues) {
                    plugin.getLogger().info("  - " + fix);
                }
            } else {
                plugin.getLogger().info("修正が必要な問題は見つかりませんでした。");
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("設定の自動修正中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 空の職業制限ブロックリストを修正
     */
    private void fixEmptyJobBlocks(FileConfiguration config, List<String> fixedIssues) {
        // fishermanの制限ブロック
        if (config.getStringList("jobs.block_restrictions.job_restricted_blocks.fisherman").isEmpty()) {
            config.set("jobs.block_restrictions.job_restricted_blocks.fisherman", Arrays.asList(
                "WATER", "KELP", "SEAGRASS", "SEA_PICKLE", "TUBE_CORAL", "BRAIN_CORAL",
                "BUBBLE_CORAL", "FIRE_CORAL", "HORN_CORAL", "DEAD_TUBE_CORAL", 
                "DEAD_BRAIN_CORAL", "DEAD_BUBBLE_CORAL", "DEAD_FIRE_CORAL", "DEAD_HORN_CORAL"
            ));
            fixedIssues.add("fishermanの制限ブロックを追加");
        }
        
        // blacksmithの制限ブロック
        if (config.getStringList("jobs.block_restrictions.job_restricted_blocks.blacksmith").isEmpty()) {
            config.set("jobs.block_restrictions.job_restricted_blocks.blacksmith", Arrays.asList(
                "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "FURNACE", "BLAST_FURNACE",
                "SMITHING_TABLE", "GRINDSTONE", "CAULDRON", "LAVA_CAULDRON"
            ));
            fixedIssues.add("blacksmithの制限ブロックを追加");
        }
        
        // alchemistの制限ブロック
        if (config.getStringList("jobs.block_restrictions.job_restricted_blocks.alchemist").isEmpty()) {
            config.set("jobs.block_restrictions.job_restricted_blocks.alchemist", Arrays.asList(
                "BREWING_STAND", "CAULDRON", "WATER_CAULDRON", "POWDER_SNOW_CAULDRON",
                "NETHER_WART", "SOUL_SAND", "SOUL_SOIL", "MAGMA_BLOCK"
            ));
            fixedIssues.add("alchemistの制限ブロックを追加");
        }
        
        // enchanterの制限ブロック
        if (config.getStringList("jobs.block_restrictions.job_restricted_blocks.enchanter").isEmpty()) {
            config.set("jobs.block_restrictions.job_restricted_blocks.enchanter", Arrays.asList(
                "ENCHANTING_TABLE", "BOOKSHELF", "LECTERN", "LAPIS_LAZULI_BLOCK",
                "EXPERIENCE_BOTTLE"
            ));
            fixedIssues.add("enchanterの制限ブロックを追加");
        }
        
        // architectの制限ブロック
        if (config.getStringList("jobs.block_restrictions.job_restricted_blocks.architect").isEmpty()) {
            config.set("jobs.block_restrictions.job_restricted_blocks.architect", Arrays.asList(
                "SCAFFOLDING", "LADDER", "CHAIN", "LANTERN", "SOUL_LANTERN",
                "TORCH", "SOUL_TORCH", "REDSTONE_TORCH", "SEA_LANTERN",
                "GLOWSTONE", "JACK_O_LANTERN", "CAMPFIRE", "SOUL_CAMPFIRE"
            ));
            fixedIssues.add("architectの制限ブロックを追加");
        }
    }
    
    /**
     * 設定の妥当性をチェック
     */
    public ConfigValidationResult validateConfigSettings() {
        ConfigValidationResult result = new ConfigValidationResult();
        FileConfiguration config = plugin.getConfig();
        
        try {
            // 必須項目チェック
            checkRequiredPaths(config, result);
            
            // 数値範囲チェック
            checkNumericRanges(config, result);
            
            // 論理的整合性チェック
            checkLogicalConsistency(config, result);
            
            // 職業設定チェック
            checkJobSettings(config, result);
            
        } catch (Exception e) {
            result.addError("設定検証中にエラーが発生: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 必須項目の存在チェック
     */
    private void checkRequiredPaths(FileConfiguration config, ConfigValidationResult result) {
        String[] requiredPaths = {
            "economy.currency.name",
            "economy.currency.symbol", 
            "economy.starting_balance",
            "database.connection_pool.max_connections",
            "jobs.general.max_jobs_per_player"
        };
        
        for (String path : requiredPaths) {
            if (!config.contains(path)) {
                result.addError("必須設定項目が見つかりません: " + path);
            }
        }
    }
    
    /**
     * 数値範囲チェック
     */
    private void checkNumericRanges(FileConfiguration config, ConfigValidationResult result) {
        // 正の数であるべき項目
        checkPositiveNumber(config, "economy.starting_balance", result);
        checkPositiveNumber(config, "database.connection_pool.max_connections", result);
        checkPositiveNumber(config, "jobs.general.max_jobs_per_player", result);
        
        // 0-1の範囲であるべき項目
        checkProbability(config, "performance.memory_management.memory_monitoring.warning_threshold", result);
        checkProbability(config, "performance.memory_management.memory_monitoring.critical_threshold", result);
        
        // 時間範囲チェック（0-23）
        checkHourRange(config, "npc_system.food_npc.operating_hours.start_hour", result);
        checkHourRange(config, "npc_system.food_npc.operating_hours.end_hour", result);
    }
    
    /**
     * 論理的整合性チェック
     */
    private void checkLogicalConsistency(FileConfiguration config, ConfigValidationResult result) {
        // 営業時間の整合性
        int startHour = config.getInt("npc_system.food_npc.operating_hours.start_hour", 6);
        int endHour = config.getInt("npc_system.food_npc.operating_hours.end_hour", 22);
        
        if (startHour >= endHour) {
            result.addWarning("食料NPC営業時間: 開始時間(" + startHour + ")が終了時間(" + endHour + ")以降になっています");
        }
        
        // メモリ閾値の整合性
        double warningThreshold = config.getDouble("performance.memory_management.memory_monitoring.warning_threshold", 0.8);
        double criticalThreshold = config.getDouble("performance.memory_management.memory_monitoring.critical_threshold", 0.95);
        
        if (warningThreshold >= criticalThreshold) {
            result.addWarning("メモリ警告閾値(" + warningThreshold + ")が危険閾値(" + criticalThreshold + ")以上になっています");
        }
    }
    
    /**
     * 職業設定チェック
     */
    private void checkJobSettings(FileConfiguration config, ConfigValidationResult result) {
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs.job_settings");
        if (jobsSection != null) {
            for (String jobName : jobsSection.getKeys(false)) {
                String basePath = "jobs.job_settings." + jobName;
                
                // 最大レベルチェック
                int maxLevel = config.getInt(basePath + ".max_level", 75);
                if (maxLevel < 1 || maxLevel > 100) {
                    result.addWarning("職業 " + jobName + " の最大レベル(" + maxLevel + ")が推奨範囲(1-100)外です");
                }
                
                // 倍率チェック
                double incomeMultiplier = config.getDouble(basePath + ".base_income_multiplier", 1.0);
                if (incomeMultiplier < 0.1 || incomeMultiplier > 5.0) {
                    result.addWarning("職業 " + jobName + " の収入倍率(" + incomeMultiplier + ")が推奨範囲(0.1-5.0)外です");
                }
            }
        }
    }
    
    /**
     * 正の数チェック
     */
    private void checkPositiveNumber(FileConfiguration config, String path, ConfigValidationResult result) {
        if (config.contains(path)) {
            double value = config.getDouble(path);
            if (value <= 0) {
                result.addError(path + " は正の数である必要があります。現在値: " + value);
            }
        }
    }
    
    /**
     * 確率値チェック（0-1）
     */
    private void checkProbability(FileConfiguration config, String path, ConfigValidationResult result) {
        if (config.contains(path)) {
            double value = config.getDouble(path);
            if (value < 0.0 || value > 1.0) {
                result.addError(path + " は0.0-1.0の範囲である必要があります。現在値: " + value);
            }
        }
    }
    
    /**
     * 時間範囲チェック（0-23）
     */
    private void checkHourRange(FileConfiguration config, String path, ConfigValidationResult result) {
        if (config.contains(path)) {
            int value = config.getInt(path);
            if (value < 0 || value > 23) {
                result.addError(path + " は0-23の範囲である必要があります。現在値: " + value);
            }
        }
    }

    
    /**
     * NPCメッセージをリロードする
     */
    public boolean reloadMessages() {
        try {
            plugin.reloadConfig();
            reloadConfig();
            
            plugin.getLogger().info("NPCメッセージを再読み込みしました。");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPCメッセージの再読み込み中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * NPCメッセージの妥当性をチェック
     */
    public MessageValidationResult validateMessages() {
        MessageValidationResult result = new MessageValidationResult();
        FileConfiguration config = plugin.getConfig();
        
        try {
            // NPCメッセージの存在確認
            checkNPCMessages(config, result);
            
            // 色コードとプレースホルダーの検証
            checkMessageFormatting(config, result);
            
            // メッセージの整合性チェック
            checkMessageConsistency(config, result);
            
        } catch (Exception e) {
            result.addError("NPCメッセージ検証中にエラーが発生: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * NPCメッセージの存在確認
     */
    private void checkNPCMessages(FileConfiguration config, MessageValidationResult result) {
        String[] requiredPaths = {
            "messages.npc.trading.no_job",
            "messages.npc.trading.job_not_accepted",
            "messages.npc.bank.greeting",
            "npc_system.food_npc.messages.greeting",
            "npc_system.food_npc.messages.closed"
        };
        
        for (String path : requiredPaths) {
            if (!config.contains(path)) {
                result.addError("必須NPCメッセージが見つかりません: " + path);
            }
        }
    }
    
    /**
     * メッセージフォーマットの検証
     */
    private void checkMessageFormatting(FileConfiguration config, MessageValidationResult result) {
        // 主要なメッセージパスを検証
        checkMessagePath(config, "messages.npc.bank.greeting", result);
        checkMessagePath(config, "npc_system.food_npc.messages.greeting", result);
        checkMessagePath(config, "npc_system.food_npc.messages.closed", result);
        
        // リスト形式のメッセージも検証
        ConfigurationSection tradingNpcs = config.getConfigurationSection("messages.npc.trading.npc_specific");
        if (tradingNpcs != null) {
            for (String npcType : tradingNpcs.getKeys(false)) {
                checkNPCSpecificMessages(config, "messages.npc.trading.npc_specific." + npcType, result);
            }
        }
    }
    
    /**
     * 個別メッセージパスの検証
     */
    private void checkMessagePath(FileConfiguration config, String path, MessageValidationResult result) {
        if (config.contains(path)) {
            Object value = config.get(path);
            if (value instanceof String) {
                String message = (String) value;
                checkColorCodes(message, path, result);
                checkPlaceholders(message, path, result);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> messages = (List<String>) value;
                for (int i = 0; i < messages.size(); i++) {
                    String message = messages.get(i);
                    checkColorCodes(message, path + "[" + i + "]", result);
                    checkPlaceholders(message, path + "[" + i + "]", result);
                }
            }
        }
    }
    
    /**
     * NPC固有メッセージの検証
     */
    private void checkNPCSpecificMessages(FileConfiguration config, String basePath, MessageValidationResult result) {
        String[] messageTypes = {"greeting", "no_job", "job_not_accepted", "welcome"};
        
        for (String messageType : messageTypes) {
            String fullPath = basePath + "." + messageType;
            checkMessagePath(config, fullPath, result);
        }
    }
    
    /**
     * 色コードの検証
     */
    private void checkColorCodes(String message, String path, MessageValidationResult result) {
        // 不正な色コードパターンをチェック
        if (message.contains("§") && !message.matches(".*§[0-9a-fk-or].*")) {
            result.addWarning("不正な色コードが含まれている可能性があります: " + path);
        }
        
        // &記号で始まる色コードもチェック
        if (message.contains("&") && !message.matches(".*&[0-9a-fk-or].*")) {
            result.addWarning("&形式の色コードが含まれています（自動変換されます）: " + path);
        }
    }
    
    /**
     * プレースホルダーの検証
     */
    private void checkPlaceholders(String message, String path, MessageValidationResult result) {
        // 一般的なプレースホルダーパターン
        String[] validPlaceholders = {
            "%player%", "%npc_name%", "%job%", "%amount%", "%currency%", 
            "%item%", "%price%", "%start%", "%end%", "%required%"
        };
        
        // %で囲まれた文字列を探す
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%[^%]+%");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        while (matcher.find()) {
            String placeholder = matcher.group();
            boolean isValid = false;
            
            for (String valid : validPlaceholders) {
                if (placeholder.equals(valid)) {
                    isValid = true;
                    break;
                }
            }
            
            if (!isValid) {
                result.addWarning("未知のプレースホルダーが使用されています: " + placeholder + " in " + path);
            }
        }
    }
    
    /**
     * メッセージの整合性チェック
     */
    private void checkMessageConsistency(FileConfiguration config, MessageValidationResult result) {
        // 営業時間メッセージと実際の営業時間設定の整合性
        String closedMessage = config.getString("npc_system.food_npc.messages.closed", "");
        int startHour = config.getInt("npc_system.food_npc.operating_hours.start_hour", 6);
        int endHour = config.getInt("npc_system.food_npc.operating_hours.end_hour", 22);
        
        String expectedHours = startHour + ":00-" + endHour + ":00";
        if (!closedMessage.contains(expectedHours)) {
            result.addWarning("食料NPCの営業時間メッセージと設定値が一致していません。" +
                "設定: " + expectedHours + ", メッセージ: " + closedMessage);
        }
    }
    
    /**
     * NPCメッセージのテスト送信
     */
    public void testNPCMessage(org.bukkit.entity.Player player, String npcType, String messageType) {
        try {
            String message = getNPCSpecificMessage(npcType, messageType, player.getName());
            if (message != null && !message.isEmpty()) {
                player.sendMessage("§7[テスト] " + message);
            } else {
                player.sendMessage("§cメッセージが見つかりません: " + npcType + "." + messageType);
            }
        } catch (Exception e) {
            player.sendMessage("§cメッセージテスト中にエラーが発生: " + e.getMessage());
        }
    }
    
    /**
     * NPCメッセージ検証結果クラス
     */
    public static class MessageValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
    
    /**
     * 設定検証結果クラス
     */
    public static class ConfigValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
    
    /**
     * 取引所データを完全削除（物理削除・復元不可）
     */
    public void permanentlyDeleteTradingPost(String npcName) {
        removeTradingPostByName(npcName);
    }
    
    /**
     * 銀行NPCデータを完全削除（物理削除・復元不可）
     */
    public void permanentlyDeleteBankNPC(String npcName) {
        removeBankNPCByName(npcName);
    }
    
    /**
     * 食料NPCデータを完全削除（物理削除・復元不可）
     */
    public void permanentlyDeleteFoodNPC(String npcName) {
        removeFoodNPCByName(npcName);
    }
}