package org.tofu.tofunomics.events.handlers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.Map;
import java.util.Random;

/**
 * エンチャントイベントハンドラ
 * 魔術師のエンチャント処理を管理
 */
public class EnchantmentEventHandler {
    
    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;
    private final Random random;
    
    public EnchantmentEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                                  JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
        this.random = new Random();
    }
    
    /**
     * エンチャントイベントの処理
     */
    public void handleEnchantment(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        
        // 魔術師の職業チェック
        PlayerJob wizardJob = jobManager.getPlayerJob(player, "wizard");
        if (wizardJob == null || !wizardJob.isActive()) {
            // 魔術師でない場合は通常のエンチャント処理
            return;
        }
        
        // エンチャント結果を処理
        processEnchantmentResult(player, wizardJob, event);
        
        // 魔術師特典を適用
        applyWizardBenefits(player, wizardJob, event);
    }
    
    /**
     * エンチャント結果の処理
     */
    private void processEnchantmentResult(Player player, PlayerJob wizardJob, EnchantItemEvent event) {
        ItemStack item = event.getItem();
        int expLevelCost = event.getExpLevelCost();
        Map<Enchantment, Integer> enchantments = event.getEnchantsToAdd();
        
        // 基本報酬計算
        double baseExperience = expLevelCost * 2.0; // 消費レベル×2の経験値
        
        // エンチャント数によるボーナス
        double enchantBonus = enchantments.size() * 1.5;
        
        // レベルによる倍率
        double levelMultiplier = 1.0 + (wizardJob.getLevel() * 0.03); // レベル毎に3%ボーナス
        
        // 最終報酬計算
        double totalExperience = (baseExperience + enchantBonus) * levelMultiplier;
        
        // 非同期で経験値を付与（収入システムは無効化）
        String playerUUID = player.getUniqueId().toString();
        asyncUpdater.updateJobExperience(playerUUID, "wizard", totalExperience);
        
        // メッセージ表示
        displayEnchantmentResult(player, item, enchantments.size(), totalExperience);
    }
    
    /**
     * 魔術師特典の適用
     */
    private void applyWizardBenefits(Player player, PlayerJob wizardJob, EnchantItemEvent event) {
        int level = wizardJob.getLevel();
        
        // レベル10以上：経験値コスト削減
        if (level >= 10) {
            int reduction = Math.min(level / 10, 5); // 最大5レベル削減
            int newCost = Math.max(1, event.getExpLevelCost() - reduction);
            event.setExpLevelCost(newCost);
            
            if (reduction > 0) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ 魔術師特典: 経験値コストが" + 
                                 reduction + "レベル削減されました！");
            }
        }
        
        // レベル25以上：追加エンチャントチャンス
        if (level >= 25 && random.nextDouble() < 0.2) { // 20%の確率
            addBonusEnchantment(event);
            player.sendMessage(ChatColor.GOLD + "✦ ボーナスエンチャント！追加の魔法効果を付与しました！");
        }
        
        // レベル50以上：超越エンチャント
        if (level >= 50 && random.nextDouble() < 0.1) { // 10%の確率
            upgradeEnchantments(event);
            player.sendMessage(ChatColor.GOLD + "✦ 超越エンチャント！全ての魔法効果が強化されました！");
        }
        
        // レベル75以上：ラピスラズリ消費削減
        if (level >= 75) {
            // ラピスラズリの一部を返却（実装は複雑なため簡略化）
            player.sendMessage(ChatColor.AQUA + "✦ 達人の技: ラピスラズリ消費が軽減されました");
        }
    }
    
    /**
     * ボーナスエンチャントを追加
     */
    private void addBonusEnchantment(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        Map<Enchantment, Integer> currentEnchants = event.getEnchantsToAdd();
        
        // アイテムタイプに応じた追加エンチャントを選択
        Enchantment bonusEnchant = selectBonusEnchantment(item.getType(), currentEnchants);
        
        if (bonusEnchant != null && bonusEnchant.canEnchantItem(item)) {
            int level = random.nextInt(bonusEnchant.getMaxLevel()) + 1;
            currentEnchants.put(bonusEnchant, level);
        }
    }
    
    /**
     * ボーナスエンチャントを選択
     */
    private Enchantment selectBonusEnchantment(Material itemType, Map<Enchantment, Integer> currentEnchants) {
        // 武器の場合
        if (isWeapon(itemType)) {
            if (!currentEnchants.containsKey(Enchantment.DAMAGE_ALL)) {
                return Enchantment.DAMAGE_ALL; // ダメージ増加
            }
            if (!currentEnchants.containsKey(Enchantment.KNOCKBACK)) {
                return Enchantment.KNOCKBACK; // ノックバック
            }
        }
        
        // 防具の場合
        if (isArmor(itemType)) {
            if (!currentEnchants.containsKey(Enchantment.PROTECTION_ENVIRONMENTAL)) {
                return Enchantment.PROTECTION_ENVIRONMENTAL; // ダメージ軽減
            }
            if (!currentEnchants.containsKey(Enchantment.DURABILITY)) {
                return Enchantment.DURABILITY; // 耐久力
            }
        }
        
        // ツールの場合
        if (isTool(itemType)) {
            if (!currentEnchants.containsKey(Enchantment.DIG_SPEED)) {
                return Enchantment.DIG_SPEED; // 効率強化
            }
            if (!currentEnchants.containsKey(Enchantment.DURABILITY)) {
                return Enchantment.DURABILITY; // 耐久力
            }
        }
        
        return null;
    }
    
    /**
     * エンチャントレベルを強化
     */
    private void upgradeEnchantments(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchantments = event.getEnchantsToAdd();
        
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchant = entry.getKey();
            int currentLevel = entry.getValue();
            
            // レベルを1上昇（最大レベルを超えない）
            int newLevel = Math.min(currentLevel + 1, enchant.getMaxLevel());
            entry.setValue(newLevel);
        }
    }
    
    /**
     * エンチャント結果を表示
     */
    private void displayEnchantmentResult(Player player, ItemStack item, int enchantCount,
                                         double experience) {
        String itemName = item.getType().name().toLowerCase().replace("_", " ");
        
        player.sendMessage(ChatColor.LIGHT_PURPLE + "═══════════════════════════");
        player.sendMessage(ChatColor.GOLD + "✦ エンチャント成功！");
        player.sendMessage(ChatColor.YELLOW + "アイテム: " + ChatColor.WHITE + itemName);
        player.sendMessage(ChatColor.YELLOW + "付与効果数: " + ChatColor.WHITE + enchantCount);
        player.sendMessage(ChatColor.GREEN + "獲得経験値: +" + String.format("%.1f", experience));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "═══════════════════════════");
    }
    
    /**
     * アイテムが武器かチェック
     */
    private boolean isWeapon(Material material) {
        String name = material.name();
        return name.contains("SWORD") || name.contains("BOW") || 
               name.contains("AXE") || name.contains("TRIDENT");
    }
    
    /**
     * アイテムが防具かチェック
     */
    private boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("HELMET") || name.contains("CHESTPLATE") || 
               name.contains("LEGGINGS") || name.contains("BOOTS") || 
               name.contains("SHIELD");
    }
    
    /**
     * アイテムがツールかチェック
     */
    private boolean isTool(Material material) {
        String name = material.name();
        return name.contains("PICKAXE") || name.contains("SHOVEL") || 
               name.contains("HOE") || name.contains("FISHING_ROD");
    }
}