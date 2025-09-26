package org.tofu.tofunomics.tools;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業別専用ツール・アイテムの管理
 */
public class JobToolManager {
    
    private final ConfigManager configManager;
    private final Map<String, JobToolSet> jobToolSets;
    
    public JobToolManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.jobToolSets = new HashMap<>();
        initializeJobToolSets();
    }
    
    private void initializeJobToolSets() {
        // 鉱夫のツールセット
        jobToolSets.put("miner", new JobToolSet()
            .addTool(1, createEnchantedTool(Material.IRON_PICKAXE, "見習い鉱夫のツルハシ", 
                Arrays.asList(ChatColor.GRAY + "鉱夫レベル1で使用可能", ChatColor.YELLOW + "採掘効率が少し向上"), 
                Enchantment.DIG_SPEED, 1))
            .addTool(10, createEnchantedTool(Material.DIAMOND_PICKAXE, "熟練鉱夫のツルハシ",
                Arrays.asList(ChatColor.GRAY + "鉱夫レベル10で使用可能", ChatColor.YELLOW + "採掘効率が大幅向上"),
                Enchantment.DIG_SPEED, 3))
            .addTool(25, createEnchantedTool(Material.NETHERITE_PICKAXE, "マスター鉱夫のツルハシ",
                Arrays.asList(ChatColor.GRAY + "鉱夫レベル25で使用可能", ChatColor.GOLD + "最高の採掘効率", ChatColor.LIGHT_PURPLE + "幸運付与"),
                Enchantment.DIG_SPEED, 5, Enchantment.LOOT_BONUS_BLOCKS, 3)));
        
        // 木こりのツールセット
        jobToolSets.put("woodcutter", new JobToolSet()
            .addTool(1, createEnchantedTool(Material.IRON_AXE, "見習い木こりの斧",
                Arrays.asList(ChatColor.GRAY + "木こりレベル1で使用可能", ChatColor.YELLOW + "伐採効率が少し向上"),
                Enchantment.DIG_SPEED, 1))
            .addTool(10, createEnchantedTool(Material.DIAMOND_AXE, "熟練木こりの斧",
                Arrays.asList(ChatColor.GRAY + "木こりレベル10で使用可能", ChatColor.YELLOW + "伐採効率が大幅向上"),
                Enchantment.DIG_SPEED, 3))
            .addTool(25, createEnchantedTool(Material.NETHERITE_AXE, "マスター木こりの斧",
                Arrays.asList(ChatColor.GRAY + "木こりレベル25で使用可能", ChatColor.GOLD + "最高の伐採効率", ChatColor.LIGHT_PURPLE + "幸運付与"),
                Enchantment.DIG_SPEED, 5, Enchantment.LOOT_BONUS_BLOCKS, 2)));
        
        // 農家のツールセット
        jobToolSets.put("farmer", new JobToolSet()
            .addTool(1, createEnchantedTool(Material.IRON_HOE, "見習い農家のクワ",
                Arrays.asList(ChatColor.GRAY + "農家レベル1で使用可能", ChatColor.GREEN + "作物の成長を促進"),
                Enchantment.DIG_SPEED, 1))
            .addTool(5, createSpecialItem(Material.BONE_MEAL, "特製肥料", 16,
                Arrays.asList(ChatColor.GRAY + "農家レベル5で使用可能", ChatColor.GREEN + "作物の成長を大幅促進")))
            .addTool(15, createEnchantedTool(Material.DIAMOND_HOE, "熟練農家のクワ",
                Arrays.asList(ChatColor.GRAY + "農家レベル15で使用可能", ChatColor.GREEN + "作物の成長を大幅促進", ChatColor.YELLOW + "収穫量増加"),
                Enchantment.LOOT_BONUS_BLOCKS, 2)));
        
        // 釣り人のツールセット  
        jobToolSets.put("fisherman", new JobToolSet()
            .addTool(1, createEnchantedTool(Material.FISHING_ROD, "見習い釣り人の竿",
                Arrays.asList(ChatColor.GRAY + "釣り人レベル1で使用可能", ChatColor.AQUA + "釣り効率が少し向上"),
                Enchantment.LURE, 1))
            .addTool(10, createEnchantedTool(Material.FISHING_ROD, "熟練釣り人の竿",
                Arrays.asList(ChatColor.GRAY + "釣り人レベル10で使用可能", ChatColor.AQUA + "釣り効率が大幅向上", ChatColor.YELLOW + "宝物発見確率UP"),
                Enchantment.LURE, 3, Enchantment.LUCK, 2))
            .addTool(20, createEnchantedTool(Material.FISHING_ROD, "マスター釣り人の竿",
                Arrays.asList(ChatColor.GRAY + "釣り人レベル20で使用可能", ChatColor.GOLD + "最高の釣り効率", ChatColor.LIGHT_PURPLE + "レア宝物確率大幅UP"),
                Enchantment.LURE, 5, Enchantment.LUCK, 3, Enchantment.MENDING, 1)));
        
        // 鍛冶屋のツールセット
        jobToolSets.put("blacksmith", new JobToolSet()
            .addTool(1, createSpecialItem(Material.IRON_INGOT, "精錬された鉄", 8,
                Arrays.asList(ChatColor.GRAY + "鍛冶屋レベル1で使用可能", ChatColor.YELLOW + "高品質な製作材料")))
            .addTool(10, createEnchantedTool(Material.SMITHING_TABLE, "熟練鍛冶台",
                Arrays.asList(ChatColor.GRAY + "鍛冶屋レベル10で使用可能", ChatColor.YELLOW + "製作効率が向上"),
                Enchantment.DIG_SPEED, 1))
            .addTool(15, createSpecialItem(Material.DIAMOND, "精錬されたダイヤモンド", 4,
                Arrays.asList(ChatColor.GRAY + "鍛冶屋レベル15で使用可能", ChatColor.LIGHT_PURPLE + "最高品質の製作材料"))));
        
        // ポーション屋のツールセット
        jobToolSets.put("alchemist", new JobToolSet()
            .addTool(1, createSpecialItem(Material.BREWING_STAND, "見習いポーション台", 1,
                Arrays.asList(ChatColor.GRAY + "ポーション屋レベル1で使用可能", ChatColor.LIGHT_PURPLE + "ポーション製作効率向上")))
            .addTool(5, createSpecialItem(Material.BLAZE_POWDER, "精製ブレイズパウダー", 16,
                Arrays.asList(ChatColor.GRAY + "ポーション屋レベル5で使用可能", ChatColor.YELLOW + "高品質燃料")))
            .addTool(20, createSpecialItem(Material.DRAGON_BREATH, "濃縮ドラゴンブレス", 8,
                Arrays.asList(ChatColor.GRAY + "ポーション屋レベル20で使用可能", ChatColor.LIGHT_PURPLE + "最高級ポーション材料"))));
        
        // エンチャンターのツールセット
        jobToolSets.put("enchanter", new JobToolSet()
            .addTool(1, createSpecialItem(Material.EXPERIENCE_BOTTLE, "凝縮された経験", 8,
                Arrays.asList(ChatColor.GRAY + "エンチャンターレベル1で使用可能", ChatColor.GREEN + "経験値効率向上")))
            .addTool(10, createEnchantedTool(Material.ENCHANTING_TABLE, "熟練エンチャント台",
                Arrays.asList(ChatColor.GRAY + "エンチャンターレベル10で使用可能", ChatColor.LIGHT_PURPLE + "エンチャント効率向上"),
                Enchantment.MENDING, 1))
            .addTool(25, createSpecialItem(Material.NETHER_STAR, "マスターオーブ", 1,
                Arrays.asList(ChatColor.GRAY + "エンチャンターレベル25で使用可能", ChatColor.GOLD + "最高レベルエンチャント可能"))));
        
        // 建築家のツールセット
        jobToolSets.put("architect", new JobToolSet()
            .addTool(1, createSpecialItem(Material.PAPER, "設計図", 16,
                Arrays.asList(ChatColor.GRAY + "建築家レベル1で使用可能", ChatColor.YELLOW + "建築効率向上")))
            .addTool(5, createEnchantedTool(Material.GOLDEN_SHOVEL, "建築家のスコップ",
                Arrays.asList(ChatColor.GRAY + "建築家レベル5で使用可能", ChatColor.YELLOW + "整地効率大幅向上"),
                Enchantment.DIG_SPEED, 3))
            .addTool(15, createSpecialItem(Material.STRUCTURE_BLOCK, "マスター設計ブロック", 4,
                Arrays.asList(ChatColor.GRAY + "建築家レベル15で使用可能", ChatColor.GOLD + "高度な建築機能解放"))));
    }
    
    private ItemStack createEnchantedTool(Material material, String name, List<String> lore, 
                                         Enchantment enchant1, int level1) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(lore);
            meta.addEnchant(enchant1, level1, true);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createEnchantedTool(Material material, String name, List<String> lore, 
                                         Enchantment enchant1, int level1, 
                                         Enchantment enchant2, int level2) {
        ItemStack item = createEnchantedTool(material, name, lore, enchant1, level1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchant2, level2, true);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createEnchantedTool(Material material, String name, List<String> lore, 
                                         Enchantment enchant1, int level1, 
                                         Enchantment enchant2, int level2,
                                         Enchantment enchant3, int level3) {
        ItemStack item = createEnchantedTool(material, name, lore, enchant1, level1, enchant2, level2);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchant3, level3, true);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createSpecialItem(Material material, String name, int amount, List<String> lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * プレイヤーが指定レベルでアンロックできるツールを付与
     */
    public boolean giveJobTool(Player player, String jobName, int playerJobLevel) {
        JobToolSet toolSet = jobToolSets.get(jobName.toLowerCase());
        if (toolSet == null) {
            return false;
        }
        
        ItemStack tool = toolSet.getToolForLevel(playerJobLevel);
        if (tool == null) {
            return false;
        }
        
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), tool);
            player.sendMessage(ChatColor.YELLOW + "インベントリが満杯のため、ツールを足元に落としました。");
        } else {
            player.getInventory().addItem(tool);
        }
        
        player.sendMessage(ChatColor.GREEN + "職業レベル" + playerJobLevel + "のツールを獲得しました！");
        return true;
    }
    
    /**
     * 指定された職業とレベルで使用可能なツール一覧を取得
     */
    public List<ItemStack> getAvailableTools(String jobName, int maxLevel) {
        JobToolSet toolSet = jobToolSets.get(jobName.toLowerCase());
        if (toolSet == null) {
            return Arrays.asList();
        }
        
        return toolSet.getAvailableTools(maxLevel);
    }
    
    /**
     * プレイヤーが新しいレベルに達した時にツールを自動付与
     */
    public void checkAndGiveNewTools(Player player, String jobName, int newLevel) {
        JobToolSet toolSet = jobToolSets.get(jobName.toLowerCase());
        if (toolSet == null) {
            return;
        }
        
        if (toolSet.hasToolForLevel(newLevel)) {
            giveJobTool(player, jobName, newLevel);
        }
    }
    
    /**
     * ツールが職業専用かチェック
     */
    public boolean isJobTool(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        
        String displayName = item.getItemMeta().getDisplayName();
        return displayName.contains("見習い") || displayName.contains("熟練") || displayName.contains("マスター")
            || displayName.contains("特製") || displayName.contains("精錬された") || displayName.contains("濃縮");
    }
}