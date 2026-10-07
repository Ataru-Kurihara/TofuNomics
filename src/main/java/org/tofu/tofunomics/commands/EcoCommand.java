package org.tofu.tofunomics.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.dao.TransactionLogDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.economy.TransactionContext;
import org.tofu.tofunomics.economy.TransactionRecorder;
import org.tofu.tofunomics.economy.TransactionType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class EcoCommand implements CommandExecutor {
    
    private final ConfigManager configManager;
    private final CurrencyConverter currencyConverter;
    private final PlayerDAO playerDAO;
    
    public EcoCommand(ConfigManager configManager, CurrencyConverter currencyConverter, PlayerDAO playerDAO) {
        this.configManager = configManager;
        this.currencyConverter = currencyConverter;
        this.playerDAO = playerDAO;
    }
    
    /** /eco を使うのに必要な権限（plugin.yml の設定と同じ。設定が外れても使えないよう、ここでも確かめる） */
    static final String ADMIN_PERMISSION = "tofunomics.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "give":
                return handleGive(sender, args);
            case "take":
                return handleTake(sender, args);
            case "set":
                return handleSet(sender, args);
            case "setcoinvalue":
                return handleSetCoinValue(sender, args);
            case "getcoinvalue":
                return handleGetCoinValue(sender);
            case "resetcoinvalue":
                return handleResetCoinValue(sender);
            case "reset":
                return handleReset(sender, args);
            case "log":
                return handleLog(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }
    
    /**
     * 操作の対象。オンラインでなくても、UUID が分かれば操作できる。
     */
    static final class Target {
        final UUID uuid;
        final String name;
        /** オンラインのときだけ入る（本人への通知に使う） */
        final Player online;

        Target(UUID uuid, String name, Player online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }

    /**
     * 名前（または UUID）から対象を決める。
     * オンライン → サーバーが覚えているオフラインのプレイヤー → お金の記録に残っている名前、の順に探す。
     *
     * @return 見つからなければ null
     */
    private Target resolveTarget(String nameOrUuid) {
        Player online = Bukkit.getPlayer(nameOrUuid);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName(), online);
        }

        UUID uuid = parseUuidOrNull(nameOrUuid);
        String name = nameOrUuid;
        if (uuid == null) {
            OfflinePlayer[] known = Bukkit.getOfflinePlayers();
            if (known != null) {
                for (OfflinePlayer offline : known) {
                    if (offline != null && nameOrUuid.equalsIgnoreCase(offline.getName())) {
                        uuid = offline.getUniqueId();
                        name = offline.getName();
                        break;
                    }
                }
            }
        }
        if (uuid == null) {
            TransactionRecorder recorder = TransactionRecorder.global();
            if (recorder != null) {
                uuid = recorder.findUuidByName(nameOrUuid);
            }
        }
        if (uuid == null) {
            return null;
        }
        // UUID で指定されたオンラインの相手にも通知できるようにする
        Player onlineByUuid = Bukkit.getPlayer(uuid);
        if (onlineByUuid != null) {
            return new Target(uuid, onlineByUuid.getName(), onlineByUuid);
        }
        return new Target(uuid, name, null);
    }

    /** UUID の形の文字列なら UUID にする。違えば null */
    static UUID parseUuidOrNull(String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sendPlayerNotFound(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            configManager.getMessagePrefix() + configManager.getMessage("player_not_found")));
    }

    private void sendInvalidAmount(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            configManager.getMessagePrefix() + configManager.getMessage("invalid_amount")));
    }

    /** 金額の文字列を数にする。数でなければ null */
    static Double parseAmountOrNull(String text) {
        try {
            double amount = Double.parseDouble(text);
            return (Double.isNaN(amount) || Double.isInfinite(amount)) ? null : amount;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** 金額の表示（数値＋設定の通貨記号） */
    private String money(double amount) {
        return currencyConverter.formatCurrency(amount) + " " + configManager.getCurrencySymbol();
    }

    private void notifyTarget(Target target, String message) {
        if (target.online != null) {
            target.online.sendMessage(message);
        }
    }

    private String offlineNote(Target target) {
        return target.online == null ? ChatColor.GRAY + "（オフライン）" : "";
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco give <プレイヤー名> <金額>");
            return true;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender);
            return true;
        }

        Double amount = parseAmountOrNull(args[2]);
        if (amount == null || amount <= 0) {
            sendInvalidAmount(sender);
            return true;
        }

        boolean saved;
        try (TransactionContext.Scope scope =
                 TransactionContext.open(TransactionType.ECO_GIVE, sender.getName(), null)) {
            org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(target.uuid.toString());
            if (tofuPlayer == null) {
                tofuPlayer = new org.tofu.tofunomics.models.Player();
                tofuPlayer.setUuid(target.uuid.toString());
                tofuPlayer.setBalance(0.0); // 持ち歩き現金は0に設定
                tofuPlayer.setBankBalance(amount); // 銀行預金を設定
                saved = playerDAO.insertPlayer(tofuPlayer);
            } else {
                tofuPlayer.addBankBalance(amount); // 銀行預金に追加
                saved = playerDAO.updatePlayerData(tofuPlayer);
            }
        }

        if (!saved) {
            sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + target.name + " に " + money(amount) + " を付与しました。" + offlineNote(target));
        notifyTarget(target, ChatColor.GREEN + "管理者により " + money(amount) + " が付与されました。");
        return true;
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco take <プレイヤー名> <金額>");
            return true;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender);
            return true;
        }

        Double amount = parseAmountOrNull(args[2]);
        if (amount == null || amount <= 0) {
            sendInvalidAmount(sender);
            return true;
        }

        org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(target.uuid.toString());
        if (tofuPlayer == null) {
            sender.sendMessage(ChatColor.RED + "対象プレイヤーのデータが見つかりません。");
            return true;
        }

        // 残高が足りなければ何も引かれない。引けなかったのに「取り上げました」と出さない
        if (!tofuPlayer.removeBankBalance(amount)) {
            sender.sendMessage(ChatColor.RED + target.name + " の残高が足りないため、取り上げできませんでした。（残高: "
                + money(tofuPlayer.getBankBalance()) + "）");
            return true;
        }

        boolean saved;
        try (TransactionContext.Scope scope =
                 TransactionContext.open(TransactionType.ECO_TAKE, sender.getName(), null)) {
            saved = playerDAO.updatePlayerData(tofuPlayer);
        }

        if (!saved) {
            sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + target.name + " から " + money(amount) + " を取り上げました。" + offlineNote(target));
        notifyTarget(target, ChatColor.YELLOW + "管理者により " + money(amount) + " が取り上げられました。");
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco set <プレイヤー名> <金額>");
            return true;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender);
            return true;
        }

        Double amount = parseAmountOrNull(args[2]);
        if (amount == null) {
            sendInvalidAmount(sender);
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "残高は負の値にできません。");
            return true;
        }

        boolean saved;
        try (TransactionContext.Scope scope =
                 TransactionContext.open(TransactionType.ECO_SET, sender.getName(), null)) {
            org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(target.uuid.toString());
            if (tofuPlayer == null) {
                tofuPlayer = new org.tofu.tofunomics.models.Player();
                tofuPlayer.setUuid(target.uuid.toString());
                tofuPlayer.setBalance(0.0); // 持ち歩き現金は0に設定
                tofuPlayer.setBankBalance(amount); // 銀行預金を設定
                saved = playerDAO.insertPlayer(tofuPlayer);
            } else {
                tofuPlayer.setBankBalance(amount); // 銀行預金を設定
                saved = playerDAO.updatePlayerData(tofuPlayer);
            }
        }

        if (!saved) {
            sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + target.name + " の残高を " + money(amount) + " に設定しました。" + offlineNote(target));
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco reset <プレイヤー名>");
            return true;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender);
            return true;
        }

        boolean saved;
        try (TransactionContext.Scope scope =
                 TransactionContext.open(TransactionType.ECO_RESET, sender.getName(), null)) {
            org.tofu.tofunomics.models.Player tofuPlayer = playerDAO.getPlayerByUUID(target.uuid.toString());
            if (tofuPlayer == null) {
                tofuPlayer = new org.tofu.tofunomics.models.Player();
                tofuPlayer.setUuid(target.uuid.toString());
                tofuPlayer.setBalance(0.0);
                tofuPlayer.setBankBalance(0.0);
                saved = playerDAO.insertPlayer(tofuPlayer);
            } else {
                tofuPlayer.setBalance(0.0);
                tofuPlayer.setBankBalance(0.0);
                saved = playerDAO.updatePlayerData(tofuPlayer);
            }
        }

        if (!saved) {
            sender.sendMessage(ChatColor.RED + "データベースエラーが発生しました。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + target.name + " の所持金と預金をリセットしました。（残高: "
            + money(0.0) + "）" + offlineNote(target));
        notifyTarget(target, ChatColor.YELLOW + "管理者により所持金と預金がリセットされました。");
        return true;
    }

    /** /eco log で 1 回に出す件数の既定値と上限 */
    static final int LOG_DEFAULT_COUNT = 20;
    static final int LOG_MAX_COUNT = 100;

    /** 件数の指定を、1 以上・上限以下に収める。指定が無い・数でないときは既定値 */
    static int clampLogCount(String text) {
        if (text == null) {
            return LOG_DEFAULT_COUNT;
        }
        try {
            return Math.max(1, Math.min(LOG_MAX_COUNT, Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            return LOG_DEFAULT_COUNT;
        }
    }

    /**
     * お金の記録を表示する（運営用。オフラインの相手も指定できる）。
     */
    private boolean handleLog(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(ChatColor.RED + "使用法: /eco log <プレイヤー名> [件数]");
            return true;
        }

        TransactionRecorder recorder = TransactionRecorder.global();
        if (recorder == null) {
            sender.sendMessage(ChatColor.RED + "お金の記録が利用できません。");
            return true;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            sendPlayerNotFound(sender);
            return true;
        }

        int count = clampLogCount(args.length == 3 ? args[2] : null);
        List<TransactionLogDAO.Entry> entries = recorder.findRecent(target.uuid, count);

        sender.sendMessage("§6=== " + target.name + " のお金の記録（新しい順・最大 " + count + " 件） ===");
        if (entries.isEmpty()) {
            sender.sendMessage("§7記録はありません。");
            return true;
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat("MM/dd HH:mm");
        for (TransactionLogDAO.Entry entry : entries) {
            sender.sendMessage(formatLogLine(
                timeFormat.format(new Date(entry.getCreatedAt())),
                TransactionType.labelOf(entry.getType()),
                entry.getAmount(),
                money(Math.abs(entry.getAmount())),
                TransactionLogDAO.ACCOUNT_CASH.equals(entry.getAccount()),
                entry.getBalanceAfter() != null ? money(entry.getBalanceAfter()) : null,
                describeCounterparty(entry.getCounterparty()),
                entry.getDetail()));
        }
        return true;
    }

    /** 相手が UUID なら名前に直す。名前が分からなければそのまま出す */
    private String describeCounterparty(String counterparty) {
        UUID uuid = parseUuidOrNull(counterparty);
        if (uuid == null) {
            return counterparty;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return (offline != null && offline.getName() != null) ? offline.getName() : counterparty;
    }

    /**
     * 記録 1 件を 1 行の文にする。
     *
     * @param amountText  増減の大きさ（符号なし、通貨記号付き）
     * @param cash        手持ちの現金の増減なら true、預金なら false
     * @param balanceText 操作後の預金残高。無ければ null
     */
    static String formatLogLine(String timeText, String typeLabel, double amount, String amountText,
                                boolean cash, String balanceText, String counterparty, String detail) {
        StringBuilder line = new StringBuilder();
        line.append("§7").append(timeText).append(" §f").append(typeLabel).append(' ');
        line.append(amount >= 0 ? "§a+" : "§c-").append(amountText);
        line.append(cash ? " §7(手持ち)" : " §7(預金)");
        if (balanceText != null) {
            line.append(" §7残高 §f").append(balanceText);
        }
        if (counterparty != null && !counterparty.isEmpty()) {
            line.append(" §7相手 §f").append(counterparty);
        }
        if (detail != null && !detail.isEmpty()) {
            line.append(" §8").append(detail);
        }
        return line.toString();
    }

    private boolean handleReload(CommandSender sender) {
        try {
            configManager.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "設定ファイルをリロードしました。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "設定ファイルのリロードに失敗しました: " + e.getMessage());
        }
        
        return true;
    }

    
    private boolean handleSetCoinValue(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c使用法: /eco setcoinvalue <価値>");
            return true;
        }
        
        try {
            double newValue = Double.parseDouble(args[1]);
            
            if (!configManager.isDynamicValueEnabled()) {
                sender.sendMessage("§c通貨価値の変更は無効になっています。");
                return true;
            }
            
            if (newValue < configManager.getMinCoinValue() || newValue > configManager.getMaxCoinValue()) {
                sender.sendMessage(String.format("§c通貨価値は %.1f から %.1f の範囲内である必要があります。", 
                    configManager.getMinCoinValue(), configManager.getMaxCoinValue()));
                return true;
            }
            
            configManager.setCoinValue(newValue);
            sender.sendMessage(String.format("§a通貨価値を %.1f に設定しました。（TofuCoin 1 枚 = $%.1f）", 
                newValue, newValue));
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§c無効な数値です。");
        } catch (Exception e) {
            sender.sendMessage("§c設定の更新に失敗しました: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleGetCoinValue(CommandSender sender) {
        double currentValue = configManager.getCoinValue();
        sender.sendMessage("§6=== 通貨価値情報 ===");
        sender.sendMessage(String.format("§e現在の価値: §fTofuCoin 1 枚 = $%.1f", currentValue));
        sender.sendMessage(String.format("§e最小価値: §f$%.1f", configManager.getMinCoinValue()));
        sender.sendMessage(String.format("§e最大価値: §f$%.1f", configManager.getMaxCoinValue()));
        sender.sendMessage(String.format("§e変更可能: §f%s", 
            configManager.isDynamicValueEnabled() ? "有効" : "無効"));
        return true;
    }
    
    private boolean handleResetCoinValue(CommandSender sender) {
        if (!configManager.isDynamicValueEnabled()) {
            sender.sendMessage("§c通貨価値の変更は無効になっています。");
            return true;
        }
        
        try {
            configManager.setCoinValue(10.0); // デフォルト値
            sender.sendMessage("§a通貨価値をデフォルト値（TofuCoin 1 枚 = $10.0）にリセットしました。");
        } catch (Exception e) {
            sender.sendMessage("§c設定のリセットに失敗しました: " + e.getMessage());
        }
        
        return true;
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== TofuNomics 経済コマンド ===");
        sender.sendMessage("§e/eco give <プレイヤー> <金額> §7- プレイヤーに金額を付与");
        sender.sendMessage("§e/eco take <プレイヤー> <金額> §7- プレイヤーから金額を減額");
        sender.sendMessage("§e/eco set <プレイヤー> <金額> §7- プレイヤーの残高を設定");
        sender.sendMessage("§e/eco setcoinvalue <価値> §7- 通貨価値を設定");
        sender.sendMessage("§e/eco getcoinvalue §7- 現在の通貨価値を表示");
        sender.sendMessage("§e/eco resetcoinvalue §7- 通貨価値をデフォルトに戻す");
        sender.sendMessage("§e/eco reset <プレイヤー> §7- プレイヤーの所持金と預金をリセット");
        sender.sendMessage("§e/eco log <プレイヤー> [件数] §7- お金の記録を表示（オフラインの相手も可）");
        sender.sendMessage("§e/eco reload §7- 設定ファイルを再読み込み");
    }
}