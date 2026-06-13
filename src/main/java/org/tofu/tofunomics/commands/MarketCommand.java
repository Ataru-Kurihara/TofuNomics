package org.tofu.tofunomics.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketBuyOrderDAO;
import org.tofu.tofunomics.dao.MarketListingDAO;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketMessages;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.market.gui.BuyOrderBrowseGUI;
import org.tofu.tofunomics.market.gui.MarketBrowseGUI;
import org.tofu.tofunomics.market.gui.MarketGUIUtil;
import org.tofu.tofunomics.market.gui.MyBuyOrdersGUI;
import org.tofu.tofunomics.market.gui.MyListingsGUI;
import org.tofu.tofunomics.models.MarketBuyOrder;
import org.tofu.tofunomics.models.MarketListing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * プレイヤー間マーケットのコマンド。
 *
 * <pre>
 * /market                       マーケット一覧GUIを開く
 * /market sell &lt;価格&gt;            手持ちのアイテムを出品する
 * /market mylistings            自分の出品管理GUIを開く
 * /market cancel &lt;id&gt;           出品中の出品をキャンセルしてアイテムを回収する
 * /market reclaim &lt;id&gt;          期限切れの出品からアイテムを回収する
 * /market buy &lt;material&gt; &lt;個数&gt; &lt;価格&gt;  募集（買い注文）を登録する（前払い）
 * /market requests              募集一覧GUIを開く
 * /market myrequests            自分の募集管理GUIを開く
 * /market cancelrequest &lt;id&gt;    募集をキャンセルして前払いを返金する
 * /market reclaimrequest &lt;id&gt;   成立した募集から供給アイテムを回収する
 * /market reload                設定を再読み込みする（管理者）
 * /market help                  ヘルプを表示する
 * </pre>
 */
public class MarketCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "tofunomics.market.use";
    private static final String PERM_SELL = "tofunomics.market.sell";
    private static final String PERM_BUY = "tofunomics.market.buy";
    private static final String PERM_ADMIN = "tofunomics.market.admin";

    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketListingDAO listingDAO;
    private final MarketBuyOrderDAO buyOrderDAO;
    private final MarketBrowseGUI browseGUI;
    private final MyListingsGUI myListingsGUI;
    private final BuyOrderBrowseGUI buyOrderBrowseGUI;
    private final MyBuyOrdersGUI myBuyOrdersGUI;

    public MarketCommand(ConfigManager configManager, MarketManager marketManager,
                         MarketListingDAO listingDAO, MarketBuyOrderDAO buyOrderDAO,
                         MarketBrowseGUI browseGUI, MyListingsGUI myListingsGUI,
                         BuyOrderBrowseGUI buyOrderBrowseGUI, MyBuyOrdersGUI myBuyOrdersGUI) {
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.listingDAO = listingDAO;
        this.buyOrderDAO = buyOrderDAO;
        this.browseGUI = browseGUI;
        this.myListingsGUI = myListingsGUI;
        this.buyOrderBrowseGUI = buyOrderBrowseGUI;
        this.myBuyOrdersGUI = myBuyOrdersGUI;
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
            case "buy":
                return handleBuy(player, args);
            case "requests":
                return handleRequests(player);
            case "myrequests":
                return handleMyRequests(player);
            case "cancelrequest":
                return handleRequestAction(player, args, true);
            case "reclaimrequest":
                return handleRequestAction(player, args, false);
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

    // ====================================================================
    // 買い注文（募集）サブコマンド
    // ====================================================================

    private boolean handleRequests(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        buyOrderBrowseGUI.open(player);
        return true;
    }

    private boolean handleMyRequests(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        myBuyOrdersGUI.open(player);
        return true;
    }

    private boolean handleBuy(Player player, String[] args) {
        if (!player.hasPermission(PERM_BUY)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        if (args.length < 4) {
            player.sendMessage("§e使い方: §f/market buy <material> <個数> <価格>");
            return true;
        }

        Material material = Material.matchMaterial(args[1].toUpperCase());
        if (material == null || material == Material.AIR) {
            player.sendMessage(configManager.getMarketMessage("invalid_item"));
            return true;
        }

        int amount;
        double price;
        try {
            amount = Integer.parseInt(args[2]);
            price = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c個数と価格は数値で指定してください。");
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(configManager.getMarketMessage("invalid_item"));
            return true;
        }

        MarketResult result = marketManager.createBuyOrder(player, material, amount, price);
        sendBuyOrderMessage(player, result, MarketGUIUtil.prettifyMaterial(material.name()), amount, price);
        return true;
    }

    private boolean handleRequestAction(Player player, String[] args, boolean cancel) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/market " + (cancel ? "cancelrequest" : "reclaimrequest") + " <ID>");
            return true;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cIDは数値で指定してください。");
            return true;
        }

        // メッセージのプレースホルダ用に注文情報を先に取得（無くても続行）
        String item = "";
        int amount = 0;
        double price = 0;
        try {
            MarketBuyOrder order = buyOrderDAO.getById(orderId);
            if (order != null) {
                item = MarketGUIUtil.prettifyMaterial(order.getMaterial());
                amount = order.getAmount();
                price = order.getPrice();
            }
        } catch (SQLException ignored) {
            // 取得失敗時はプレースホルダ空のまま続行
        }

        MarketResult result = cancel
                ? marketManager.cancelBuyOrder(player, orderId)
                : marketManager.reclaimBuyOrder(player, orderId);
        sendBuyOrderMessage(player, result, item, amount, price);
        return true;
    }

    /**
     * 買い注文（募集）系の結果メッセージを、関連プレースホルダを埋めて送信する。
     * 未使用のプレースホルダはメッセージ側で無視される。
     */
    private void sendBuyOrderMessage(Player player, MarketResult result, String item, int amount, double price) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", item != null ? item : "",
                "amount", String.valueOf(amount),
                "price", MarketGUIUtil.formatPrice(price),
                "proceeds", String.valueOf(marketManager.calculateSellerProceeds(price)),
                "currency", configManager.getCurrencyName(),
                "min", String.valueOf((long) configManager.getMarketMinPrice()),
                "max", String.valueOf((long) configManager.getMarketMaxPrice()),
                "limit", String.valueOf(configManager.getMarketMaxBuyOrdersPerPlayer())));
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
        player.sendMessage("§b▼ 売り");
        player.sendMessage("§e/market §7- 出品一覧を開く");
        player.sendMessage("§e/market sell <価格> §7- 手持ちアイテムを出品");
        player.sendMessage("§e/market mylistings §7- 自分の出品を管理");
        player.sendMessage("§e/market cancel <ID> §7- 出品をキャンセルして回収");
        player.sendMessage("§e/market reclaim <ID> §7- 期限切れの出品を回収");
        player.sendMessage("§b▼ 募集（買い注文）");
        player.sendMessage("§e/market buy <material> <個数> <価格> §7- 募集を登録（前払い）");
        player.sendMessage("§e/market requests §7- 募集一覧を開く");
        player.sendMessage("§e/market myrequests §7- 自分の募集を管理");
        player.sendMessage("§e/market cancelrequest <ID> §7- 募集をキャンセルして返金");
        player.sendMessage("§e/market reclaimrequest <ID> §7- 成立した募集のアイテムを回収");
        if (player.hasPermission(PERM_ADMIN)) {
            player.sendMessage("§e/market reload §7- 設定を再読み込み（管理者）");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "sell", "mylistings", "cancel", "reclaim",
                    "buy", "requests", "myrequests", "cancelrequest", "reclaimrequest", "help"));
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
            Player player = (Player) sender;
            String sub = args[0].toLowerCase();
            if (sub.equals("cancel") || sub.equals("reclaim")) {
                completions.addAll(suggestListingIds(player, sub.equals("cancel")));
            } else if (sub.equals("cancelrequest") || sub.equals("reclaimrequest")) {
                completions.addAll(suggestBuyOrderIds(player, sub.equals("cancelrequest")));
            } else if (sub.equals("buy")) {
                // 第2引数（material）は手持ちアイテムの Material 名を候補として提示
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand != null && inHand.getType() != Material.AIR) {
                    completions.add(inHand.getType().name());
                }
            }
        }

        return completions;
    }

    /**
     * 自分の募集のうち、操作可能な status の ID を補完候補として返す。
     *
     * @param openOnly true なら open（cancelrequest 用）、false なら fulfilled（reclaimrequest 用）
     */
    private List<String> suggestBuyOrderIds(Player player, boolean openOnly) {
        List<String> ids = new ArrayList<>();
        try {
            for (MarketBuyOrder order : buyOrderDAO.getOrdersByRequester(player.getUniqueId())) {
                boolean match = openOnly ? order.isOpen() : order.isFulfilled();
                if (match) {
                    ids.add(String.valueOf(order.getId()));
                }
            }
        } catch (SQLException ignored) {
            // 補完失敗時は候補なしで返す
        }
        return ids;
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
