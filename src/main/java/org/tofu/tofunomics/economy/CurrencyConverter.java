package org.tofu.tofunomics.economy;

import org.bukkit.entity.Player;
import org.tofu.tofunomics.dao.PlayerDAO;

import java.text.DecimalFormat;

public class CurrencyConverter {
    
    private final PlayerDAO playerDAO;
    private final ItemManager itemManager;
    private final org.tofu.tofunomics.config.ConfigManager configManager;
    private final int decimalPlaces;
    private final DecimalFormat formatter;
    
    public CurrencyConverter(PlayerDAO playerDAO, ItemManager itemManager, 
                           org.tofu.tofunomics.config.ConfigManager configManager, int decimalPlaces) {
        this.playerDAO = playerDAO;
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.decimalPlaces = Math.max(0, Math.min(2, decimalPlaces));
        
        StringBuilder pattern = new StringBuilder("#,##0");
        if (this.decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < this.decimalPlaces; i++) {
                pattern.append("0");
            }
        }
        this.formatter = new DecimalFormat(pattern.toString());
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    // 現金（手持ち）が動いたことを知らせる先。手持ちの保存の予約に使う。未設定なら何もしない
    private java.util.function.Consumer<Player> cashChangeListener;

    public void setCashChangeListener(java.util.function.Consumer<Player> cashChangeListener) {
        this.cashChangeListener = cashChangeListener;
    }

    /**
     * 現金（手持ちの TofuCoin / TofuGold）が動いたことを知らせる。
     * 銀行残高はすぐ DB に保存されるので、手持ちも近いうちに保存して食い違いを防ぐ。
     * この入口を通らずに手持ちの通貨を動かした所（銀行 GUI の換金など）からも呼ぶ。
     */
    public void notifyCashChanged(Player player) {
        if (player == null) {
            return;
        }
        // 残高の表示（スコアボード）にも知らせる
        BalanceDisplayRefresher.notifyChanged(player.getUniqueId());
        if (cashChangeListener == null) {
            return;
        }
        try {
            cashChangeListener.accept(player);
        } catch (RuntimeException e) {
            // 保存の予約に失敗しても、お金の操作は止めない
        }
    }

    /** 手持ちの現金の増減を記録し、手持ちの保存を予約する */
    private void cashMoved(Player player, int nuggets) {
        if (nuggets != 0) {
            TransactionRecorder.recordCashChange(player.getUniqueId(), convertSignedNuggets(nuggets));
        }
        notifyCashChanged(player);
    }

    private double convertSignedNuggets(int nuggets) {
        return nuggets < 0 ? -convertNuggetsToBalance(-nuggets) : convertNuggetsToBalance(nuggets);
    }
    
    public String formatCurrency(double amount) {
        // 価格を四捨五入で丸める
        double roundedAmount = Math.round(amount);
        return formatter.format(roundedAmount);
    }
    
    public boolean depositGoldNuggets(Player player, int nuggetAmount) {
        if (nuggetAmount <= 0) {
            return false;
        }
        
        int availableNuggets = itemManager.countGoldNuggetsInInventory(player);
        if (availableNuggets < nuggetAmount) {
            return false;
        }
        
        int unplacedChange = itemManager.removeGoldNuggetsReturningUnplacedChange(player, nuggetAmount);
        if (unplacedChange < 0) {
            return false;
        }
        
        // TofuGold を崩したお釣りが手持ちに入りきらなかった分は、一緒に預け入れる
        double bankAmount = convertNuggetsToBalance(nuggetAmount + unplacedChange);
        notifyCashChanged(player);
        
        try (TransactionContext.Scope scope =
                 TransactionContext.openIfUndeclared(TransactionType.DEPOSIT, null, null)) {
            org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
            if (tofuPlayer == null) {
                tofuPlayer = new org.tofu.tofunomics.models.Player();
                tofuPlayer.setUuid(player.getUniqueId().toString());
                tofuPlayer.setBalance(0.0); // 現金は0
                tofuPlayer.setBankBalance(bankAmount); // 銀行預金に追加
                return playerDAO.insertPlayer(tofuPlayer);
            } else {
                tofuPlayer.addBankBalance(bankAmount); // 銀行預金に追加
                return playerDAO.updatePlayerData(tofuPlayer);
            }
        }
    }
    
    public boolean depositAllGoldNuggets(Player player) {
        int availableNuggets = itemManager.countGoldNuggetsInInventory(player);
        return availableNuggets > 0 && depositGoldNuggets(player, availableNuggets);
    }
    
    public enum WithdrawResult {
        SUCCESS,
        INSUFFICIENT_BALANCE,
        INSUFFICIENT_INVENTORY_SPACE,
        INVALID_AMOUNT,
        DATABASE_ERROR
    }
    
    public WithdrawResult withdrawToGoldNuggets(Player player, double amount) {
        if (amount <= 0) {
            return WithdrawResult.INVALID_AMOUNT;
        }
        
        int nuggetAmount = convertBalanceToNuggets(amount);
        double exactAmount = convertNuggetsToBalance(nuggetAmount);
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        if (tofuPlayer == null || tofuPlayer.getBankBalance() < exactAmount) {
            return WithdrawResult.INSUFFICIENT_BALANCE;
        }
        
        if (!itemManager.hasInventorySpace(player, nuggetAmount)) {
            return WithdrawResult.INSUFFICIENT_INVENTORY_SPACE;
        }
        
        try (TransactionContext.Scope scope =
                 TransactionContext.openIfUndeclared(TransactionType.WITHDRAW, null, null)) {
            tofuPlayer.removeBankBalance(exactAmount); // 銀行預金から引き出し
            if (!playerDAO.updatePlayerData(tofuPlayer)) {
                return WithdrawResult.DATABASE_ERROR;
            }
            
            if (!itemManager.addGoldNuggetsToInventory(player, nuggetAmount)) {
                tofuPlayer.addBankBalance(exactAmount); // ロールバック
                playerDAO.updatePlayerData(tofuPlayer);
                return WithdrawResult.INSUFFICIENT_INVENTORY_SPACE;
            }
        }
        
        notifyCashChanged(player);
        return WithdrawResult.SUCCESS;
    }
    
    public int convertBalanceToNuggets(double balance) {
        if (balance <= 0) {
            return 0;
        }
        // 1:1の単純な換算（coin_value = 1）
        return (int) Math.round(balance);
    }
    
    public double convertNuggetsToBalance(int nuggets) {
        if (nuggets <= 0) {
            return 0.0;
        }
        // 1:1の単純な換算（coin_value = 1）
        return (double) nuggets;
    }

    
    /**
     * 現在の通貨価値を取得
     */
    public double getCurrentCoinValue() {
        return configManager.getCoinValue();
    }
    
    /**
     * 通貨価値の説明文を取得
     */
    public String getCoinValueDescription() {
        double coinValue = getCurrentCoinValue();
        return String.format("TofuCoin 1 枚 = $%.1f", coinValue);
    }
    
    public double roundToDecimalPlaces(double amount) {
        if (decimalPlaces == 0) {
            return Math.floor(amount);
        }
        
        double multiplier = Math.pow(10, decimalPlaces);
        return Math.round(amount * multiplier) / multiplier;
    }
    
    public boolean canAfford(Player player, double amount) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null && tofuPlayer.getBankBalance() >= amount;
    }
    
    public double getBalance(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    // 現金（金塊）残高を取得
    public double getCashBalance(Player player) {
        int goldNuggets = itemManager.countGoldNuggetsInInventory(player);
        return convertNuggetsToBalance(goldNuggets);
    }
    
    // 銀行預金残高を取得
    public double getBankBalance(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    // 総資産（現金+預金）を取得
    public double getTotalBalance(Player player) {
        return getCashBalance(player) + getBankBalance(player);
    }
    
    public boolean transfer(Player fromPlayer, Player toPlayer, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        String fromUuid = fromPlayer.getUniqueId().toString();
        String toUuid = toPlayer.getUniqueId().toString();
        
        return playerDAO.transferBalance(fromUuid, toUuid, amount);
    }
    
    public boolean hasEnoughGoldNuggets(Player player, int requiredAmount) {
        return itemManager.countGoldNuggetsInInventory(player) >= requiredAmount;
    }
    
    public int getMaxWithdrawableNuggets(Player player) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(player.getUniqueId().toString());
        if (tofuPlayer == null) {
            return 0;
        }
        return convertBalanceToNuggets(tofuPlayer.getBankBalance());
    }
    
    // UUIDベースのメソッド（NPCシステム用）
    public double getBalance(java.util.UUID uuid) {
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        return tofuPlayer != null ? tofuPlayer.getBankBalance() : 0.0;
    }
    
    public boolean addBalance(java.util.UUID uuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        if (tofuPlayer == null) {
            return false;
        }
        
        double newBalance = tofuPlayer.getBankBalance() + amount;
        tofuPlayer.setBankBalance(newBalance);
        return playerDAO.updatePlayerData(tofuPlayer);
    }
    
    public boolean subtractBalance(java.util.UUID uuid, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(uuid.toString());
        if (tofuPlayer == null || tofuPlayer.getBankBalance() < amount) {
            return false;
        }
        
        double newBalance = tofuPlayer.getBankBalance() - amount;
        tofuPlayer.setBankBalance(newBalance);
        return playerDAO.updatePlayerData(tofuPlayer);
    }
    
    // 所持金での支払い処理（金塊をインベントリから削除）
    public boolean payWithCash(Player player, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        int requiredNuggets = convertBalanceToNuggets(amount);
        double exactAmount = convertNuggetsToBalance(requiredNuggets);
        
        // 必要な金塊数をチェック
        if (!hasEnoughGoldNuggets(player, requiredNuggets)) {
            return false;
        }
        
        // 金塊をインベントリから削除
        int unplacedChange = itemManager.removeGoldNuggetsReturningUnplacedChange(player, requiredNuggets);
        if (unplacedChange < 0) {
            return false;
        }
        // TofuGold を崩したお釣りが手持ちに入りきらなかった分は口座へ入金する
        bankUnplacedNuggets(player, unplacedChange);
        cashMoved(player, -(requiredNuggets + unplacedChange));
        return true;
    }

    /**
     * 手持ちに入りきらなかったコインを口座へ入金する。口座に入れられなければ足元へ落とす。
     */
    public void bankUnplacedNuggets(Player player, int nuggets) {
        if (nuggets <= 0) {
            return;
        }
        if (!addBalance(player.getUniqueId(), convertNuggetsToBalance(nuggets))) {
            java.util.logging.Logger.getLogger(CurrencyConverter.class.getName()).warning(
                "[CurrencyConverter] 口座へ入金できなかったため足元に落としました: "
                + player.getName() + " 金塊" + nuggets + "枚");
            itemManager.dropGoldNuggetsAtLocation(player, nuggets);
        }
    }
    
    // 所持金での受取り処理（金塊をインベントリに追加）
    public boolean receiveCash(Player player, double amount) {
        if (amount <= 0) {
            return false;
        }
        
        int nuggetAmount = convertBalanceToNuggets(amount);
        
        // インベントリ容量チェック
        if (!itemManager.hasInventorySpace(player, nuggetAmount)) {
            return false;
        }
        
        // 金塊をインベントリに追加
        boolean added = itemManager.addGoldNuggetsToInventory(player, nuggetAmount);
        if (added) {
            cashMoved(player, nuggetAmount);
        }
        return added;
    }

    // 所持金での受取り処理（金塊をインベントリに追加）- スペースチェックスキップオプション付き
    public boolean receiveCash(Player player, double amount, boolean skipSpaceCheck) {
        org.bukkit.Bukkit.getLogger().info("[CurrencyConverter] receiveCash called with amount: " + amount + ", skipSpaceCheck: " + skipSpaceCheck);
        
        if (amount <= 0) {
            return false;
        }
        
        int nuggetAmount = convertBalanceToNuggets(amount);
        
        // スペースチェックをスキップしない場合のみチェックを実行
        if (!skipSpaceCheck) {
            boolean hasSpace = itemManager.hasInventorySpace(player, nuggetAmount);
            org.bukkit.Bukkit.getLogger().info("[CurrencyConverter] Space check result: " + hasSpace + " (nuggetAmount: " + nuggetAmount + ")");
            if (!hasSpace) {
                return false;
            }
        } else {
            org.bukkit.Bukkit.getLogger().info("[CurrencyConverter] Skipping space check as requested");
        }
        
        // 金塊をインベントリに追加
        boolean result = itemManager.addGoldNuggetsToInventory(player, nuggetAmount);
        org.bukkit.Bukkit.getLogger().info("[CurrencyConverter] addGoldNuggetsToInventory result: " + result);
        if (result) {
            cashMoved(player, nuggetAmount);
        }
        return result;
    }
    
    // 入る分は金塊で渡し、入りきらない分は銀行口座へ入金。口座へ回した枚数を返す
    public int receiveCashWithBankFallback(Player player, int nuggetAmount) {
        if (nuggetAmount <= 0) {
            return 0;
        }

        // インベントリに入る分だけ金塊を付与し、入りきらなかった枚数を取得
        int leftover = itemManager.addGoldNuggetsWithLeftover(player, nuggetAmount);
        cashMoved(player, nuggetAmount - leftover);

        // 入りきらなかった分を銀行口座へ入金（金塊1枚=残高1の1:1換算）
        if (leftover > 0) {
            boolean banked = addBalance(player.getUniqueId(), convertNuggetsToBalance(leftover));
            if (!banked) {
                // 口座未登録などで入金に失敗すると代金消失となるため警告ログを残す
                org.bukkit.Bukkit.getLogger().warning(
                    "[CurrencyConverter] 口座フォールバック入金に失敗しました（口座未登録の可能性）: "
                    + player.getName() + " 金塊" + leftover + "枚");
            }
        }

        return leftover;
    }

    /**
     * 返金・報酬など「必ず渡しきる」支払い。入る分は金塊で渡し、入りきらない分は口座へ入金する。
     *
     * @return 口座へ回した枚数
     */
    public int receiveCashWithBankFallback(Player player, double amount) {
        return receiveCashWithBankFallback(player, convertBalanceToNuggets(amount));
    }

    // 所持金で支払い可能かチェック
    public boolean canAffordWithCash(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        
        int requiredNuggets = convertBalanceToNuggets(amount);
        return hasEnoughGoldNuggets(player, requiredNuggets);
    }
}