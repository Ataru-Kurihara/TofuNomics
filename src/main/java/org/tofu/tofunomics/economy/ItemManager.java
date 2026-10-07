package org.tofu.tofunomics.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ItemManager {
    
    private static final String GOLD_NUGGET_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "TofuCoin";
    private List<String> createGoldNuggetLore() {
        return Arrays.asList(
            ChatColor.YELLOW + "TofuNomics公式通貨",
            ChatColor.GREEN + "豆腐銀行発行 v2.0",
            ChatColor.AQUA + "TofuNomics基本通貨単位",
            ChatColor.GRAY + "採掘不可・偽造防止機能付き",
            ChatColor.BLUE + "銀行で預け入れ・引き出し可能"
        );
    }
    
    // セキュリティ強化のための定数
    private static final int CURRENCY_CUSTOM_MODEL_DATA = 1001; // 豆腐コイン識別用
    private static final int INGOT_CUSTOM_MODEL_DATA = 1002; // 金インゴット通貨識別用
    private static final String CURRENCY_VERSION = "v2.0";
    private static final String SECURITY_HASH = "TOFU2024"; // 簡易セキュリティハッシュ
    private static final String GOLD_INGOT_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "TofuGold";
    private static final int NUGGETS_PER_INGOT = 9; // 9金塊 = 1金インゴット（Minecraftバニラ準拠）
    private static final int NUGGET_MAX_STACK_SIZE = 64; // 金塊 1 マスの最大数（バニラ準拠）
    
    private List<String> createGoldIngotLore() {
        return Arrays.asList(
            ChatColor.YELLOW + "TofuNomics公式上位通貨",
            ChatColor.GREEN + "豆腐銀行発行 v2.0",
            ChatColor.AQUA + "1金貨 = 9コイン",
            ChatColor.GRAY + "採掘不可・偽造防止機能付き",
            ChatColor.BLUE + "銀行で金塊通貨と交換可能"
        );
    }
    
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

    /**
     * 通貨版金インゴット（豆腐金貨）を作成
     */
    public ItemStack createCurrencyGoldIngot(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金インゴットの数量は1以上である必要があります");
        }
        
        ItemStack goldIngot = new ItemStack(Material.GOLD_INGOT, amount);
        ItemMeta meta = goldIngot.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(GOLD_INGOT_DISPLAY_NAME);
            meta.setLore(createGoldIngotLore());
            
            // CustomModelDataによる識別機能
            meta.setCustomModelData(INGOT_CUSTOM_MODEL_DATA);
            
            // エンチャントグロー効果（偽のエンチャント）
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            goldIngot.setItemMeta(meta);
        }
        
        return goldIngot;
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
        
        // 必須のLore行をチェック
        if (!lore.get(0).equals(ChatColor.YELLOW + "TofuNomics公式通貨") ||
            !lore.get(1).equals(ChatColor.GREEN + "豆腐銀行発行 v2.0") ||
            !lore.get(2).equals(ChatColor.AQUA + "TofuNomics基本通貨単位") ||
            !lore.get(3).equals(ChatColor.GRAY + "採掘不可・偽造防止機能付き") ||
            !lore.get(4).equals(ChatColor.BLUE + "銀行で預け入れ・引き出し可能")) {
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
     * 通貨版金インゴット（豆腐金貨）かどうかを検証
     */
    public boolean isValidCurrencyGoldIngot(ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_INGOT) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return false;
        }
        
        // 基本的なチェック
        if (!GOLD_INGOT_DISPLAY_NAME.equals(meta.getDisplayName())) {
            return false;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null || lore.size() != 5) {
            return false;
        }
        
        // 必須のLore行をチェック
        if (!lore.get(0).equals(ChatColor.YELLOW + "TofuNomics公式上位通貨") ||
            !lore.get(1).equals(ChatColor.GREEN + "豆腐銀行発行 v2.0") ||
            !lore.get(2).equals(ChatColor.AQUA + "1金貨 = 9コイン") ||
            !lore.get(3).equals(ChatColor.GRAY + "採掘不可・偽造防止機能付き") ||
            !lore.get(4).equals(ChatColor.BLUE + "銀行で金塊通貨と交換可能")) {
            return false;
        }
        
        // CustomModelDataチェック
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != INGOT_CUSTOM_MODEL_DATA) {
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
     * 旧形式のTofuGold（金インゴット）かどうかを判定（互換性のため）
     * バリデーションが厳格すぎて預け入れできない問題への対応
     */
    public boolean isLegacyGoldIngot(ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_INGOT) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // 表示名だけでは通貨と認めない。表示名は金床で誰でも付けられるため、
        // 「TofuGold」と名付けただけの金インゴットが 9 コインの通貨として通ってしまう。
        // Lore と CustomModelData はサバイバルでは付けられず、銀行が発行した TofuGold は
        // 新旧どちらの形式でも必ず持っているので、既存の手持ちは通貨のまま扱える。

        // Loreチェック（TofuNomicsまたは豆腐銀行が含まれていればOK）
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String loreLine : lore) {
                String strippedLore = ChatColor.stripColor(loreLine);
                if (strippedLore.contains("TofuNomics") || strippedLore.contains("豆腐銀行") ||
                    strippedLore.contains("金貨") || strippedLore.contains("コイン")) {
                    return true;
                }
            }
        }

        // CustomModelDataがあればOK（通貨として作成されたもの）
        if (meta.hasCustomModelData() && meta.getCustomModelData() == INGOT_CUSTOM_MODEL_DATA) {
            return true;
        }

        return false;
    }

    /**
     * 旧形式のTofuGold（金インゴット）を新形式に変換
     */
    public ItemStack convertLegacyGoldIngotToNewFormat(ItemStack legacyItem) {
        if (!isLegacyGoldIngot(legacyItem)) {
            return null;
        }

        int amount = legacyItem.getAmount();
        return createCurrencyGoldIngot(amount);
    }

    /**
     * 拡張バリデーション：新形式または旧形式のTofuGold（金インゴット）かチェック
     */
    public boolean isAnyValidGoldIngot(ItemStack item) {
        return isValidCurrencyGoldIngot(item) || isLegacyGoldIngot(item);
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
    
    /**
     * 通貨（TofuCoin / TofuGold。旧形式を含む）かどうかを判定する。
     * クラフト・売却・買い注文への供給など「通貨を別の物として扱ってはいけない」場面で使う。
     */
    public boolean isCurrencyItem(ItemStack item) {
        return isAnyValidGoldNugget(item) || isAnyValidGoldIngot(item);
    }

    public int countGoldNuggetsInInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        int totalAmount = 0;
        
        for (ItemStack item : inventory.getContents()) {
            // 金塊のカウント
            if (isAnyValidGoldNugget(item)) {
                totalAmount += item.getAmount();
            }
            // 金のインゴットのカウント（1インゴット = 9金塊）
            else if (isAnyValidGoldIngot(item)) {
                totalAmount += item.getAmount() * NUGGETS_PER_INGOT;
            }
        }
        
        return totalAmount;
    }
    
    public boolean removeGoldNuggetsFromInventory(Player player, int amount) {
        int unplacedChange = removeGoldNuggetsReturningUnplacedChange(player, amount);
        if (unplacedChange < 0) {
            return false;
        }
        if (unplacedChange > 0) {
            // 口座へ回せない呼び出し元向けの最後の受け皿。消さずに通貨として足元へ落とす
            dropGoldNuggetsAtLocation(player, unplacedChange);
        }
        return true;
    }

    /**
     * 通貨を amount コイン分だけ手持ちから引き落とす。
     * TofuGold を崩したお釣りが手持ちに入りきらなかった場合、その枚数を返す
     * （呼び出し元が口座へ入金するなどして、お釣りを消さないようにする）。
     *
     * @return 入りきらなかったお釣りの枚数（0 以上）。引き落とせなかった場合は -1
     */
    public int removeGoldNuggetsReturningUnplacedChange(Player player, int amount) {
        if (amount <= 0) {
            return -1;
        }
        int unplacedChange = 0;
        
        PlayerInventory inventory = player.getInventory();
        int totalAvailable = countGoldNuggetsInInventory(player);
        
        if (totalAvailable < amount) {
            return -1;
        }
        
        int remaining = amount;
        
        // ステップ1: まず金塊から引き落とす
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
        
        // ステップ2: まだ不足している場合、金のインゴットから引き落とす
        if (remaining > 0) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (isAnyValidGoldIngot(item) && remaining > 0) {
                    int ingotAmount = item.getAmount();
                    int nuggetsFromThisStack = ingotAmount * NUGGETS_PER_INGOT;

                    if (nuggetsFromThisStack <= remaining) {
                        // このインゴットスタック全体を使う
                        inventory.setItem(slot, null);
                        remaining -= nuggetsFromThisStack;
                    } else {
                        // このインゴットスタックから一部を使う
                        int ingotsNeeded = (remaining + NUGGETS_PER_INGOT - 1) / NUGGETS_PER_INGOT; // 切り上げ
                        int changeNuggets = (ingotsNeeded * NUGGETS_PER_INGOT) - remaining;

                        // インゴットを減らす
                        if (ingotAmount == ingotsNeeded) {
                            inventory.setItem(slot, null);
                        } else {
                            // 旧形式の場合は新形式に変換して残りを設定
                            if (isLegacyGoldIngot(item)) {
                                ItemStack newFormatItem = createCurrencyGoldIngot(ingotAmount - ingotsNeeded);
                                inventory.setItem(slot, newFormatItem);
                            } else {
                                item.setAmount(ingotAmount - ingotsNeeded);
                            }
                        }

                        // お釣りの金塊を追加（入りきらない分は呼び出し元へ返す）
                        if (changeNuggets > 0) {
                            unplacedChange += addGoldNuggetsWithLeftover(player, changeNuggets);
                        }

                        remaining = 0;
                    }
                }
            }
        }
        
        return remaining == 0 ? unplacedChange : -1;
    }
    
    public boolean addGoldNuggetsToInventory(Player player, int amount) {
        if (amount <= 0) {
            return false;
        }

        // 事前にスペースをチェック（既存の金塊スタックの空きも考慮）
        if (!hasInventorySpace(player, amount)) {
            return false;
        }

        // 全部入るか、まったく入らないかのどちらかにする。
        // 途中まで入った分（既存スタックに入った端数を含む）を残すと、
        // 呼び出し元の返金と合わせてコインが増えてしまう。
        int leftover = addGoldNuggetsWithLeftover(player, amount);
        if (leftover > 0) {
            removeExactGoldNuggets(player, amount - leftover);
            return false;
        }
        return true;
    }

    /**
     * addGoldNuggetsWithLeftover で入れた新形式コインを、入れた枚数だけ取り除く（巻き戻し用）。
     */
    private void removeExactGoldNuggets(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, NUGGET_MAX_STACK_SIZE);
            player.getInventory().removeItem(createGoldNugget(stackSize));
            remaining -= stackSize;
        }
    }

    // インベントリに入る分だけ金塊を追加し、入りきらなかった枚数を返す（ロールバックしない）
    public int addGoldNuggetsWithLeftover(Player player, int amount) {
        if (amount <= 0) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        int remaining = amount;

        try {
            while (remaining > 0) {
                int stackSize = Math.min(remaining, Material.GOLD_NUGGET.getMaxStackSize());
                ItemStack goldNugget = createGoldNugget(stackSize);

                HashMap<Integer, ItemStack> leftover = inventory.addItem(goldNugget);

                // このスタックで入りきらなかった枚数を集計
                int notAdded = 0;
                for (ItemStack item : leftover.values()) {
                    notAdded += item.getAmount();
                }

                // 実際にインベントリへ入った枚数を残数から減算
                remaining -= (stackSize - notAdded);

                // 入りきらない分が出た時点でインベントリは満杯。残りはすべて口座行き
                if (notAdded > 0) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            // 金塊付与中の予期せぬ例外。インベントリへ入った分はそのまま維持し、未付与の
            // 残数は呼び出し元（receiveCashWithBankFallback）で口座へ回し代金の取りこぼしを防ぐ。
            // 障害調査のためスタックトレースも記録する。
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                "[ItemManager] addGoldNuggetsWithLeftover failed with exception", e);
        }

        return remaining;
    }

    public boolean hasInventorySpace(Player player, int amount) {
        PlayerInventory inventory = player.getInventory();
        int emptySlots = 0;
        int partialSpace = 0;
        int maxStackSize = NUGGET_MAX_STACK_SIZE;

        // addItem が入れるのは収納 36 枠だけ。防具・オフハンドの枠は空きに数えない
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            } else if (isValidGoldNugget(item)) {
                partialSpace += Math.max(0, maxStackSize - item.getAmount());
            }
        }

        return fitsInStorage(amount, emptySlots, partialSpace, maxStackSize);
    }

    /**
     * 空き枠の数と、同じ品の既存スタックの空きから、amount 個が入りきるかを判定する。
     */
    public static boolean fitsInStorage(int amount, int emptySlots, int partialSpace, int maxStackSize) {
        if (amount <= 0) {
            return true;
        }
        long capacity = (long) emptySlots * maxStackSize + partialSpace;
        return capacity >= amount;
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

    /**
     * 金塊数を金インゴット数に換算（9金塊 = 1金インゴット）
     * @param nuggets 金塊数
     * @return 金インゴット数（切り捨て）
     */
    public int convertNuggetsToIngots(int nuggets) {
        if (nuggets < NUGGETS_PER_INGOT) {
            return 0;
        }
        return nuggets / NUGGETS_PER_INGOT;
    }
    
    /**
     * 金塊数から余りを計算（金インゴットに換算できなかった分）
     * @param nuggets 金塊数
     * @return 余りの金塊数
     */
    public int getRemainingNuggets(int nuggets) {
        return nuggets % NUGGETS_PER_INGOT;
    }
    
    /**
     * 金インゴット数を金塊数に換算（1金インゴット = 9金塊）
     * @param ingots 金インゴット数
     * @return 金塊数
     */
    public int convertIngotsToNuggets(int ingots) {
        return ingots * NUGGETS_PER_INGOT;
    }
    
    /**
     * 換算比率を取得
     * @return 9（9金塊 = 1金インゴット）
     */
    public int getNuggetsPerIngot() {
        return NUGGETS_PER_INGOT;
    }
}