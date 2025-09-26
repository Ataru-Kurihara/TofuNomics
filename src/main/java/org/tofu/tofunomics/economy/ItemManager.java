package org.tofu.tofunomics.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.Arrays;
import java.util.List;

public class ItemManager {
    
    private static final String GOLD_NUGGET_DISPLAY_NAME = ChatColor.GOLD + "豆腐コイン";
    private List<String> createGoldNuggetLore() {
        double coinValue = configManager.getCoinValue();
        return Arrays.asList(
            ChatColor.YELLOW + "TofuNomics公式通貨",
            ChatColor.GREEN + "豆腐銀行発行 v2.0",
            ChatColor.AQUA + String.format("1コイン = $%.1f", coinValue),
            ChatColor.GRAY + "採掘不可・偽造防止機能付き",
            ChatColor.BLUE + "銀行で預け入れ・引き出し可能"
        );
    }
    
    // セキュリティ強化のための定数
    private static final int CURRENCY_CUSTOM_MODEL_DATA = 1001; // 豆腐コイン識別用
    private static final String CURRENCY_VERSION = "v2.0";
    private static final String SECURITY_HASH = "TOFU2024"; // 簡易セキュリティハッシュ
    
    private final org.tofu.tofunomics.config.ConfigManager configManager;
    
    public ItemManager(org.tofu.tofunomics.config.ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public ItemStack createGoldNugget(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金塊の数量は1以上である必要があります");
        }
        
        ItemStack goldNugget = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = goldNugget.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(GOLD_NUGGET_DISPLAY_NAME);
            meta.setLore(createGoldNuggetLore()); // 動的Lore使用
            
            // CustomModelDataによる識別機能
            meta.setCustomModelData(CURRENCY_CUSTOM_MODEL_DATA);
            
            // エンチャントグロー効果（偽のエンチャント）
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            goldNugget.setItemMeta(meta);
        }
        
        return goldNugget;
    }
    
    public boolean isValidGoldNugget(ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return false;
        }
        
        // 基本的なチェック
        if (!GOLD_NUGGET_DISPLAY_NAME.equals(meta.getDisplayName())) {
            return false;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null || lore.size() != 5) { // 新形式は5行
            return false;
        }
        
        // 必須のLore行をチェック（通貨価値は動的なのでスキップ）
        if (!lore.get(0).equals(ChatColor.YELLOW + "TofuNomics公式通貨") ||
            !lore.get(1).equals(ChatColor.GREEN + "豆腐銀行発行 v2.0") ||
            !lore.get(3).equals(ChatColor.GRAY + "採掘不可・偽造防止機能付き") ||
            !lore.get(4).equals(ChatColor.BLUE + "銀行で預け入れ・引き出し可能")) {
            return false;
        }
        
        // 通貨価値行の形式チェック（行3）
        String valueLine = lore.get(2);
        if (!valueLine.startsWith(ChatColor.AQUA + "1コイン = $")) {
            return false;
        }
        
        // CustomModelDataチェック
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != CURRENCY_CUSTOM_MODEL_DATA) {
            return false;
        }
        
        // エンチャントチェック（偽造防止）
        if (!meta.hasEnchant(Enchantment.LURE) || meta.getEnchantLevel(Enchantment.LURE) != 1) {
            return false;
        }
        
        // ItemFlagsチェック
        if (!meta.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS)) {
            return false;
        }
        
        return true;
    }

    /**
     * 旧形式の豆腐コインかどうかを判定（互換性のため）
     */
    public boolean isLegacyGoldNugget(ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return false;
        }
        
        // 旧形式チェック（表示名が「金塊」でLoreが2行）
        boolean isOldDisplayName = (ChatColor.GOLD + "金塊").equals(meta.getDisplayName());
        boolean isOldLore = meta.getLore().size() == 2 && 
                           meta.getLore().get(0).equals(ChatColor.YELLOW + "TofuNomicsの通貨アイテム") &&
                           meta.getLore().get(1).equals(ChatColor.GRAY + "銀行で預け入れできます");
        
        return isOldDisplayName && isOldLore;
    }
    
    /**
     * 旧形式の豆腐コインを新形式に変換
     */
    public ItemStack convertLegacyToNewFormat(ItemStack legacyItem) {
        if (!isLegacyGoldNugget(legacyItem)) {
            return null;
        }
        
        int amount = legacyItem.getAmount();
        return createGoldNugget(amount);
    }
    
    /**
     * 拡張バリデーション：新形式または旧形式の豆腐コインかチェック
     */
    public boolean isAnyValidGoldNugget(ItemStack item) {
        return isValidGoldNugget(item) || isLegacyGoldNugget(item);
    }
    
    public int countGoldNuggetsInInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        int totalAmount = 0;
        
        for (ItemStack item : inventory.getContents()) {
            if (isAnyValidGoldNugget(item)) {
                totalAmount += item.getAmount();
            }
        }
        
        return totalAmount;
    }
    
    public boolean removeGoldNuggetsFromInventory(Player player, int amount) {
        if (amount <= 0) {
            return false;
        }
        
        PlayerInventory inventory = player.getInventory();
        int totalAvailable = countGoldNuggetsInInventory(player);
        
        if (totalAvailable < amount) {
            return false;
        }
        
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isAnyValidGoldNugget(item) && remaining > 0) {
                int itemAmount = item.getAmount();
                
                if (itemAmount <= remaining) {
                    inventory.setItem(slot, null);
                    remaining -= itemAmount;
                } else {
                    // 旧形式の場合は新形式に変換して残りを設定
                    if (isLegacyGoldNugget(item)) {
                        ItemStack newFormatItem = createGoldNugget(itemAmount - remaining);
                        inventory.setItem(slot, newFormatItem);
                    } else {
                        item.setAmount(itemAmount - remaining);
                    }
                    remaining = 0;
                }
            }
        }
        
        return remaining == 0;
    }
    
    public boolean addGoldNuggetsToInventory(Player player, int amount) {
        if (amount <= 0) {
            return false;
        }
        
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        
        while (remaining > 0) {
            int stackSize = Math.min(remaining, Material.GOLD_NUGGET.getMaxStackSize());
            ItemStack goldNugget = createGoldNugget(stackSize);
            
            if (inventory.firstEmpty() == -1) {
                return false;
            }
            
            inventory.addItem(goldNugget);
            remaining -= stackSize;
        }
        
        return true;
    }
    
    public boolean hasInventorySpace(Player player, int amount) {
        PlayerInventory inventory = player.getInventory();
        int emptySlots = 0;
        int maxStackSize = Material.GOLD_NUGGET.getMaxStackSize();
        
        for (ItemStack item : inventory.getContents()) {
            if (item == null) {
                emptySlots++;
            } else if (isValidGoldNugget(item)) {
                int availableSpace = maxStackSize - item.getAmount();
                amount -= availableSpace;
            }
        }
        
        int requiredSlots = (int) Math.ceil((double) amount / maxStackSize);
        return emptySlots >= requiredSlots;
    }
    
    public void dropGoldNuggetsAtLocation(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, Material.GOLD_NUGGET.getMaxStackSize());
            ItemStack goldNugget = createGoldNugget(stackSize);
            
            player.getWorld().dropItemNaturally(player.getLocation(), goldNugget);
            remaining -= stackSize;
        }
    }
    
    /**
     * アイテムが金塊かどうかを判定（NPCシステム用）
     */
    public boolean isGoldIngot(ItemStack item) {
        return item != null && item.getType() == Material.GOLD_INGOT;
    }
    
    /**
     * 指定数の金塊アイテムスタックを生成（NPCシステム用）
     */
    public ItemStack createGoldIngots(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金塊の数量は1以上である必要があります");
        }
        
        return new ItemStack(Material.GOLD_INGOT, amount);
    }

    
    /**
     * プレイヤーインベントリ内の旧形式豆腐コインを新形式に一括変換
     */
    public int convertAllLegacyCoinsInInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        int convertedCount = 0;
        
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isLegacyGoldNugget(item)) {
                ItemStack newFormatItem = convertLegacyToNewFormat(item);
                if (newFormatItem != null) {
                    inventory.setItem(slot, newFormatItem);
                    convertedCount += item.getAmount();
                }
            }
        }
        
        return convertedCount;
    }
    
    /**
     * 管理者用：サーバー内全プレイヤーの旧形式コインを新形式に変換
     */
    public void convertAllLegacyCoinsForAllPlayers() {
        // この機能は管理者コマンドとして実装される予定
        // 大量のデータ処理のため、非同期処理が推奨される
    }
}