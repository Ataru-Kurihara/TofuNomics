package org.tofu.tofunomics.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketMessages;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.market.gui.MarketBrowseGUI;
import org.tofu.tofunomics.market.gui.MarketGUIUtil;
import org.tofu.tofunomics.market.gui.MyListingsGUI;
import org.tofu.tofunomics.models.MarketListing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * プレイヤー間マーケットのコマンド。
 *
 * <pre>
 * /market                 マーケット一覧GUIを開く
 * /market sell &lt;価格&gt;      手持ちのアイテムを出品する
 * /market mylistings      自分の出品管理GUIを開く
 * /market cancel &lt;id&gt;     出品中の出品をキャンセルしてアイテムを回収する
 * /market reclaim &lt;id&gt;    期限切れの出品からアイテムを回収する
 * /market reload          設定を再読み込みする（管理者）
 * /market help            ヘルプを表示する
 * </pre>
 */
public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "tofunomics.market.use";
    private static final String PERM_SELL = "tofunomics.market.sell";
    private static final String PERM_ADMIN = "tofunomics.market.admin";

    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketListingDAO listingDAO;
    private final MarketBrowseGUI browseGUI;
    private final MyListingsGUI myListingsGUI;

    public MarketCommand(ConfigManager configManager, MarketManager marketManager,
                         MarketListingDAO listingDAO, MarketBrowseGUI browseGUI, MyListingsGUI myListingsGUI) {
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.listingDAO = listingDAO;
        this.browseGUI = browseGUI;
        this.myListingsGUI = myListingsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!configManager.isMarketEnabled()) {
            sender.sendMessage(configManager.getMarketMessage("error"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            return handleBrowse(player);
        }

        switch (args[0].toLowerCase()) {
            case "sell":
                return handleSell(player, args);
            case "mylistings":
            case "my":
                return handleMyListings(player);
            case "cancel":
                return handleReclaim(player, args, true);
            case "reclaim":
                return handleReclaim(player, args, false);
            case "reload":
                return handleReload(player);
            case "help":
                sendUsage(player);
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    private boolean handleBrowse(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        browseGUI.open(player);
        return true;
    }

    private boolean handleMyListings(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        myListingsGUI.open(player);
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission(PERM_SELL)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/market sell <価格>");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(MarketMessages.format(configManager, MarketResult.INVALID_PRICE, "", 0));
            return true;
        }

        // 出品成功後はアイテムが手元から消えるため、メッセージ用の名前を先に取得しておく
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String itemName = (inHand != null && inHand.getType() != Material.AIR)
                ? MarketGUIUtil.prettifyMaterial(inHand.getType().name())
                : "";

        MarketResult result = marketManager.createListing(player, price);
        player.sendMessage(MarketMessages.format(configManager, result, itemName, price));
        return true;
    }

    private boolean handleReclaim(Player player, String[] args, boolean cancel) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/market " + (cancel ? "cancel" : "reclaim") + " <ID>");
            return true;
        }

        int listingId;
        try {
            listingId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cIDは数値で指定してください。");
            return true;
        }

        MarketResult result = cancel
                ? marketManager.cancelListing(player, listingId)
                : marketManager.reclaimListing(player, listingId);
        player.sendMessage(MarketMessages.format(configManager, result, "", 0));
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission(PERM_ADMIN)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        configManager.reloadConfig();
        player.sendMessage("§aマーケット設定を再読み込みしました。");
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== マーケットコマンド ===");
        player.sendMessage("§e/market §7- 出品一覧を開く");
        player.sendMessage("§e/market sell <価格> §7- 手持ちアイテムを出品");
        player.sendMessage("§e/market mylistings §7- 自分の出品を管理");
        player.sendMessage("§e/market cancel <ID> §7- 出品をキャンセルして回収");
        player.sendMessage("§e/market reclaim <ID> §7- 期限切れの出品を回収");
        if (player.hasPermission(PERM_ADMIN)) {
            player.sendMessage("§e/market reload §7- 設定を再読み込み（管理者）");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("sell", "mylistings", "cancel", "reclaim", "help"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.add("reload");
            }
            String prefix = args[0].toLowerCase();
            for (String sub : subs) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && sender instanceof Player) {
            String sub = args[0].toLowerCase();
            if (sub.equals("cancel") || sub.equals("reclaim")) {
                completions.addAll(suggestListingIds((Player) sender, sub.equals("cancel")));
            }
        }

        return completions;
    }

    /**
     * 自分の出品のうち、操作可能な status の ID を補完候補として返す。
     *
     * @param activeOnly true なら active（cancel 用）、false なら expired（reclaim 用）
     */
    private List<String> suggestListingIds(Player player, boolean activeOnly) {
        List<String> ids = new ArrayList<>();
        try {
            for (MarketListing listing : listingDAO.getListingsBySeller(player.getUniqueId())) {
                boolean match = activeOnly ? listing.isActive() : listing.isExpired();
                if (match) {
                    ids.add(String.valueOf(listing.getId()));
                }
            }
        } catch (SQLException ignored) {
            // 補完失敗時は候補なしで返す
        }
        return ids;
    }
}
