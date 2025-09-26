package org.tofu.tofunomics.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * 設定ファイル検証システム
 * 設定値の妥当性をチェックし、不正な値をデフォルト値で修正
 */
public class ConfigValidator {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final List<ValidationError> validationErrors;
    
    public ConfigValidator(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.validationErrors = new ArrayList<>();
    }
    
    /**
     * 設定ファイル全体の検証を実行
     */
    public boolean validateConfiguration() {
        validationErrors.clear();
        
        boolean isValid = true;
        
        // 基本設定の検証
        isValid &= validateBasicSettings();
        
        // 職業設定の検証
        isValid &= validateJobSettings();
        
        // 職業別スキル設定の検証
        isValid &= validateJobSkillSettings();
        
        // レベリング設定の検証
        isValid &= validateLevelingSettings();
        
        // イベント報酬設定の検証
        isValid &= validateEventRewardSettings();
        
        // パフォーマンス設定の検証
        isValid &= validatePerformanceSettings();
        
        // エラーレポートの出力
        reportValidationErrors();
        
        return isValid;
    }
    
    /**
     * 基本設定の検証
     */
    private boolean validateBasicSettings() {
        boolean isValid = true;
        
        // 通貨設定の検証
        if (configManager.getStartingBalance() < 0) {
            addError("economy.starting_balance", "初期残高は0以上である必要があります", "100.0");
            isValid = false;
        }
        
        // 送金設定の検証
        if (configManager.getMinimumPayAmount() <= 0) {
            addError("economy.pay.minimum_amount", "最低送金額は0より大きい必要があります", "1.0");
            isValid = false;
        }
        
        if (configManager.getPayFeePercentage() < 0 || configManager.getPayFeePercentage() > 1) {
            addError("economy.pay.fee_percentage", "送金手数料は0-1の範囲である必要があります", "0.0");
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * 職業設定の検証
     */
    private boolean validateJobSettings() {
        boolean isValid = true;
        
        String[] jobNames = {"miner", "woodcutter", "farmer", "fisherman", 
                           "blacksmith", "alchemist", "enchanter", "architect"};
        
        for (String jobName : jobNames) {
            // 最大レベルの検証
            int maxLevel = configManager.getJobMaxLevel(jobName);
            if (maxLevel <= 0 || maxLevel > 100) {
                addError("jobs.job_settings." + jobName + ".max_level", 
                        "最大レベルは1-100の範囲である必要があります", "75");
                isValid = false;
            }
            
            // 収入倍率の検証
            double incomeMultiplier = configManager.getJobIncomeMultiplier(jobName);
            if (incomeMultiplier <= 0) {
                addError("jobs.job_settings." + jobName + ".base_income_multiplier", 
                        "収入倍率は0より大きい必要があります", "1.0");
                isValid = false;
            }
            
            // 経験値倍率の検証
            double expMultiplier = configManager.getJobExpMultiplier(jobName);
            if (expMultiplier <= 0) {
                addError("jobs.job_settings." + jobName + ".exp_multiplier", 
                        "経験値倍率は0より大きい必要があります", "1.0");
                isValid = false;
            }
        }
        
        return isValid;
    }
    
    /**
     * 職業別スキル設定の検証
     */
    private boolean validateJobSkillSettings() {
        boolean isValid = true;
        
        String[] jobNames = {"miner", "woodcutter", "farmer", "fisherman", 
                           "blacksmith", "alchemist", "enchanter", "architect"};
        
        // 職業別スキル名の定義
        String[][] skillNames = {
            {"fortune_strike", "vein_discovery", "mining_mastery"}, // miner
            {"tree_feller", "sapling_blessing", "forest_guardian"}, // woodcutter
            {"harvest_blessing", "twin_miracle", "growth_acceleration", "selective_breeding"}, // farmer
            {"big_catch", "treasure_hunter", "sea_blessing"}, // fisherman
            {"perfect_repair", "master_craftsmanship", "artifact_creation"}, // blacksmith
            {"ingredient_conservation", "double_brewing", "alchemy_mastery"}, // alchemist
            {"experience_conservation", "bonus_enchantment", "mystical_arts"}, // enchanter
            {"material_efficiency", "architectural_aesthetics", "master_architect"} // architect
        };
        
        for (int i = 0; i < jobNames.length; i++) {
            String jobName = jobNames[i];
            for (String skillName : skillNames[i]) {
                String skillPath = "job_skills." + jobName + "." + skillName;
                
                // 基本発動確率の検証
                double baseProbability = configManager.getJobSkillBaseProbability(jobName, skillName);
                if (!configManager.isValidProbability(baseProbability)) {
                    addError(skillPath + ".base_probability", 
                            "基本発動確率は0.0-1.0の範囲である必要があります", "0.05");
                    isValid = false;
                }
                
                // レベルボーナスの検証
                double levelBonus = configManager.getJobSkillLevelBonus(jobName, skillName);
                if (levelBonus < 0) {
                    addError(skillPath + ".level_bonus", 
                            "レベルボーナスは0以上である必要があります", "0.002");
                    isValid = false;
                }
                
                // 最大発動確率の検証
                double maxProbability = configManager.getJobSkillMaxProbability(jobName, skillName);
                if (!configManager.isValidProbability(maxProbability)) {
                    addError(skillPath + ".max_probability", 
                            "最大発動確率は0.0-1.0の範囲である必要があります", "0.25");
                    isValid = false;
                }
                
                // クールダウンの検証
                int cooldown = configManager.getJobSkillCooldown(jobName, skillName);
                if (cooldown < 0) {
                    addError(skillPath + ".cooldown_seconds", 
                            "クールダウンは0以上である必要があります", "0");
                    isValid = false;
                }
            }
        }
        
        return isValid;
    }
    
    /**
     * レベリング設定の検証
     */
    private boolean validateLevelingSettings() {
        boolean isValid = true;
        
        // 基本経験値倍率の検証
        int baseMultiplier = configManager.getExperienceBaseMultiplier();
        if (baseMultiplier <= 0) {
            addError("leveling.experience.base_multiplier", 
                    "基本経験値倍率は0より大きい必要があります", "100");
            isValid = false;
        }
        
        // 経験値計算指数の検証
        double exponent = configManager.getExperienceExponent();
        if (exponent <= 0) {
            addError("leveling.experience.exponent", 
                    "経験値計算指数は0より大きい必要があります", "2.0");
            isValid = false;
        }
        
        // レベル報酬の検証
        double baseAmount = configManager.getLevelRewardBaseAmount();
        if (baseAmount < 0) {
            addError("leveling.rewards.base_rewards.money.base_amount", 
                    "基本報酬額は0以上である必要があります", "50.0");
            isValid = false;
        }
        
        double maxAmount = configManager.getLevelRewardMaxAmount();
        if (maxAmount < baseAmount) {
            addError("leveling.rewards.base_rewards.money.max_amount", 
                    "最大報酬額は基本報酬額以上である必要があります", "500.0");
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * イベント報酬設定の検証
     */
    private boolean validateEventRewardSettings() {
        boolean isValid = true;
        
        // 全体倍率の検証
        double expMultiplier = configManager.getGlobalExperienceMultiplier();
        if (expMultiplier <= 0) {
            addError("event_rewards.global_multipliers.experience_multiplier", 
                    "経験値倍率は0より大きい必要があります", "1.0");
            isValid = false;
        }
        
        double incomeMultiplier = configManager.getGlobalIncomeMultiplier();
        if (incomeMultiplier <= 0) {
            addError("event_rewards.global_multipliers.income_multiplier", 
                    "収入倍率は0より大きい必要があります", "1.0");
            isValid = false;
        }
        
        // 時間帯ボーナスの検証
        String[] timeTypes = {"morning", "day", "evening", "night"};
        String[] bonusTypes = {"experience_bonus", "income_bonus"};
        
        for (String timeType : timeTypes) {
            for (String bonusType : bonusTypes) {
                double bonus = configManager.getTimeBonus(timeType, bonusType);
                if (bonus < 0 || bonus > 1) {
                    addError("event_rewards.global_multipliers.time_bonuses." + timeType + "." + bonusType, 
                            "時間帯ボーナスは0-1の範囲である必要があります", "0.0");
                    isValid = false;
                }
            }
        }
        
        return isValid;
    }
    
    /**
     * パフォーマンス設定の検証
     */
    private boolean validatePerformanceSettings() {
        boolean isValid = true;
        
        // コネクションプール設定の検証
        int maxPoolSize = configManager.getMaximumPoolSize();
        if (maxPoolSize <= 0) {
            addError("performance.database.connection_pool.maximum_pool_size", 
                    "最大プールサイズは0より大きい必要があります", "15");
            isValid = false;
        }
        
        int minIdle = configManager.getMinimumIdle();
        if (minIdle < 0 || minIdle > maxPoolSize) {
            addError("performance.database.connection_pool.minimum_idle", 
                    "最小アイドル数は0以上かつ最大プールサイズ以下である必要があります", "3");
            isValid = false;
        }
        
        // キャッシュ設定の検証
        int playerCacheSize = configManager.getPlayerCacheMaxSize();
        if (playerCacheSize <= 0) {
            addError("performance.caching.player_cache.max_size", 
                    "プレイヤーキャッシュサイズは0より大きい必要があります", "1000");
            isValid = false;
        }
        
        // バッチサイズの検証
        int batchSize = configManager.getBatchSize();
        if (batchSize <= 0) {
            addError("performance.database.batch_processing.batch_size", 
                    "バッチサイズは0より大きい必要があります", "100");
            isValid = false;
        }
        
        // メモリ監視閾値の検証
        double memoryThreshold = configManager.getMemoryWarningThreshold();
        if (memoryThreshold <= 0 || memoryThreshold >= 1) {
            addError("performance.memory_management.memory_monitoring.warning_threshold", 
                    "メモリ警告閾値は0より大きく1より小さい必要があります", "0.8");
            isValid = false;
        }
        
        // TPS閾値の検証
        double tpsThreshold = configManager.getTpsWarningThreshold();
        if (tpsThreshold <= 0 || tpsThreshold > 20) {
            addError("performance.monitoring.realtime_monitoring.tps_threshold", 
                    "TPS警告閾値は0より大きく20以下である必要があります", "18.0");
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * エラーを追加
     */
    private void addError(String configPath, String message, String suggestedValue) {
        validationErrors.add(new ValidationError(configPath, message, suggestedValue));
    }
    
    /**
     * 検証エラーレポートを出力
     */
    private void reportValidationErrors() {
        if (validationErrors.isEmpty()) {
            plugin.getLogger().info("設定ファイルの検証が完了しました。エラーはありませんでした。");
            return;
        }
        
        plugin.getLogger().warning("設定ファイルに " + validationErrors.size() + " 個の問題が見つかりました:");
        
        for (ValidationError error : validationErrors) {
            plugin.getLogger().log(Level.WARNING, String.format(
                "設定項目: %s | エラー: %s | 推奨値: %s",
                error.getConfigPath(),
                error.getMessage(),
                error.getSuggestedValue()
            ));
        }
        
        plugin.getLogger().warning("上記の設定を修正してサーバーを再起動することを推奨します。");
    }
    
    /**
     * 特定の設定パスの値をデフォルト値で修正
     */
    public void fixConfigValue(String configPath, Object defaultValue) {
        plugin.getConfig().set(configPath, defaultValue);
        plugin.saveConfig();
        configManager.reloadConfig();
        
        plugin.getLogger().info("設定項目 '" + configPath + "' をデフォルト値 '" + defaultValue + "' で修正しました。");
    }
    
    /**
     * 全ての検証エラーを自動修正
     */
    public void autoFixAllErrors() {
        if (validationErrors.isEmpty()) {
            plugin.getLogger().info("修正すべきエラーはありません。");
            return;
        }
        
        int fixedCount = 0;
        for (ValidationError error : validationErrors) {
            try {
                // 推奨値の型を判定して適切な型で設定
                String suggestedValue = error.getSuggestedValue();
                Object value = parseValue(suggestedValue);
                
                fixConfigValue(error.getConfigPath(), value);
                fixedCount++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "設定項目 '" + error.getConfigPath() + "' の自動修正に失敗しました: " + e.getMessage());
            }
        }
        
        plugin.getLogger().info(fixedCount + " 個の設定項目を自動修正しました。");
        
        // 修正後に再検証
        validationErrors.clear();
        validateConfiguration();
    }
    
    /**
     * 文字列値を適切な型に変換
     */
    private Object parseValue(String value) {
        // Boolean型の判定
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        
        // Integer型の判定
        try {
            if (!value.contains(".")) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException ignored) {}
        
        // Double型の判定
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        
        // 文字列として返す
        return value;
    }
    
    /**
     * 検証エラー情報クラス
     */
    public static class ValidationError {
        private final String configPath;
        private final String message;
        private final String suggestedValue;
        
        public ValidationError(String configPath, String message, String suggestedValue) {
            this.configPath = configPath;
            this.message = message;
            this.suggestedValue = suggestedValue;
        }
        
        public String getConfigPath() { return configPath; }
        public String getMessage() { return message; }
        public String getSuggestedValue() { return suggestedValue; }
    }
    
    /**
     * 検証エラー一覧を取得
     */
    public List<ValidationError> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }
}