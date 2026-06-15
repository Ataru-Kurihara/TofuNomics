package org.tofu.tofunomics.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.ChatInputManager;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketMessages;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.market.ServiceProcessor;

import java.util.Arrays;
import java.util.Collections;

/**
 * マーケットの統合ハブ GUI。
 *
 * {@code /market}（無引数）で開く全操作の入口。閲覧・管理系は各既存 GUI の {@code open()} へ遷移し、
 * 登録系（出品・募集・修理依頼・エンチャント依頼）は GUI を閉じて {@link ChatInputManager} で
 * 価格・個数等をチャット入力させてから {@link MarketManager} のロジックを呼ぶ。
 */
public class MarketHubGUI {

    private static final String PERM_USE = "tofunomics.market.use";
    private static final String PERM_SELL = "tofunomics.market.sell";
    private static final String PERM_BUY = "tofunomics.market.buy";
    private static final String PERM_SERVICE = "tofunomics.market.service";

    private static final int SIZE = 27;
    private static final int SLOT_BROWSE = 0;
    private static final int SLOT_MY_LISTINGS = 1;
    private static final int SLOT_BUY_BROWSE = 2;
    private static final int SLOT_MY_BUY = 3;
    private static final int SLOT_SERVICE_BROWSE = 4;
    private static final int SLOT_MY_SERVICES = 5;
    private static final int SLOT_CREATE_LISTING = 11;
    private static final int SLOT_CREATE_BUY = 12;
    private static final int SLOT_CREATE_REPAIR = 14;
    private static final int SLOT_CREATE_ENCHANT = 15;
    private static final int SLOT_HELP = 22;
    private static final int SLOT_CLOSE = 26;

    private static final int INPUT_TIMEOUT_SECONDS = 60;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final ChatInputManager chatInput;
    private final MarketGUIListener listener;

    // 遷移先 GUI（循環依存回避のためセッターで後注入）
    private MarketBrowseGUI browseGUI;
    private MyListingsGUI myListingsGUI;
    private BuyOrderBrowseGUI buyOrderBrowseGUI;
    private MyBuyOrdersGUI myBuyOrdersGUI;
    private ServiceBrowseGUI serviceBrowseGUI;
    private MyServicesGUI myServicesGUI;

    public MarketHubGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                        ChatInputManager chatInput, MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.chatInput = chatInput;
        this.listener = listener;
    }

    /**
     * 遷移先 GUI 参照を注入する。
     */
    public void setGUIs(MarketBrowseGUI browseGUI, MyListingsGUI myListingsGUI,
                        BuyOrderBrowseGUI buyOrderBrowseGUI, MyBuyOrdersGUI myBuyOrdersGUI,
                        ServiceBrowseGUI serviceBrowseGUI, MyServicesGUI myServicesGUI) {
        this.browseGUI = browseGUI;
        this.myListingsGUI = myListingsGUI;
        this.buyOrderBrowseGUI = buyOrderBrowseGUI;
        this.myBuyOrdersGUI = myBuyOrdersGUI;
        this.serviceBrowseGUI = serviceBrowseGUI;
        this.myServicesGUI = myServicesGUI;
    }

    /**
     * ハブ GUI を開く。
     */
    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6マーケット");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.HUB, gui);
            render(player, gui);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("マーケットハブGUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(configManager.getMarketMessage("error"));
        }
    }

    private void render(Player player, Inventory gui) {
        boolean serviceEnabled = configManager.isMarketServiceEnabled();

        // 段1: 閲覧・管理（全て market.use）
        gui.setItem(SLOT_BROWSE, MarketGUIUtil.createButton(Material.CHEST, "§a§l出品一覧",
                Arrays.asList("§7出品されているアイテムを見て購入します", "§eクリックで開く")));
        gui.setItem(SLOT_MY_LISTINGS, MarketGUIUtil.createButton(Material.ENDER_CHEST, "§b§l自分の出品",
                Arrays.asList("§7自分の出品を確認・キャンセル・回収します", "§eクリックで開く")));
        gui.setItem(SLOT_BUY_BROWSE, MarketGUIUtil.createButton(Material.PAPER, "§a§l募集一覧",
                Arrays.asList("§7買い注文（募集）に応じて供給します", "§eクリックで開く")));
        gui.setItem(SLOT_MY_BUY, MarketGUIUtil.createButton(Material.WRITABLE_BOOK, "§b§l自分の募集",
                Arrays.asList("§7自分の募集を確認・キャンセル・回収します", "§eクリックで開く")));
        if (serviceEnabled) {
            gui.setItem(SLOT_SERVICE_BROWSE, MarketGUIUtil.createButton(Material.ANVIL, "§a§lサービス依頼一覧",
                    Arrays.asList("§7修理・エンチャント依頼を引き受けます", "§eクリックで開く")));
            gui.setItem(SLOT_MY_SERVICES, MarketGUIUtil.createButton(Material.DAMAGED_ANVIL, "§b§l自分の依頼",
                    Arrays.asList("§7自分の依頼を確認・キャンセル・回収します", "§eクリックで開く")));
        }

        // 段2: 新規登録（権限に応じて表示）
        if (player.hasPermission(PERM_SELL)) {
            gui.setItem(SLOT_CREATE_LISTING, MarketGUIUtil.createButton(Material.HOPPER, "§6§l出品する",
                    Arrays.asList("§7手に持ったアイテムを出品します", "§7価格はチャットで入力します", "§eクリックで開始")));
        }
        if (player.hasPermission(PERM_BUY)) {
            gui.setItem(SLOT_CREATE_BUY, MarketGUIUtil.createButton(Material.EMERALD, "§6§l募集する",
                    Arrays.asList("§7欲しいアイテムの買い注文を出します", "§7アイテム・個数・価格をチャットで入力", "§eクリックで開始")));
        }
        if (serviceEnabled && player.hasPermission(PERM_SERVICE)) {
            gui.setItem(SLOT_CREATE_REPAIR, MarketGUIUtil.createButton(Material.IRON_PICKAXE, "§6§l修理を依頼",
                    Arrays.asList("§7手に持った道具の修理を依頼します", "§7報酬はチャットで入力します", "§eクリックで開始")));
            gui.setItem(SLOT_CREATE_ENCHANT, MarketGUIUtil.createButton(Material.ENCHANTED_BOOK, "§6§lエンチャントを依頼",
                    Arrays.asList("§7手に持った道具へのエンチャントを依頼します", "§7内容・報酬はチャットで入力します", "§eクリックで開始")));
        }

        // 段3: ヘルプ・閉じる
        gui.setItem(SLOT_HELP, MarketGUIUtil.createButton(Material.BOOK, "§e§lヘルプ",
                Collections.singletonList("§7マーケットの使い方を表示します")));
        gui.setItem(SLOT_CLOSE, MarketGUIUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = MarketGUIUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = 0; i < SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    /**
     * クリック処理（{@link MarketGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, MarketGUISession session, int slot, ClickType clickType) {
        switch (slot) {
            case SLOT_BROWSE:
                if (browseGUI != null) browseGUI.open(player);
                break;
            case SLOT_MY_LISTINGS:
                if (myListingsGUI != null) myListingsGUI.open(player);
                break;
            case SLOT_BUY_BROWSE:
                if (buyOrderBrowseGUI != null) buyOrderBrowseGUI.open(player);
                break;
            case SLOT_MY_BUY:
                if (myBuyOrdersGUI != null) myBuyOrdersGUI.open(player);
                break;
            case SLOT_SERVICE_BROWSE:
                if (configManager.isMarketServiceEnabled() && serviceBrowseGUI != null) serviceBrowseGUI.open(player);
                break;
            case SLOT_MY_SERVICES:
                if (configManager.isMarketServiceEnabled() && myServicesGUI != null) myServicesGUI.open(player);
                break;
            case SLOT_CREATE_LISTING:
                if (player.hasPermission(PERM_SELL)) startCreateListing(player);
                break;
            case SLOT_CREATE_BUY:
                if (player.hasPermission(PERM_BUY)) startCreateBuyOrder(player);
                break;
            case SLOT_CREATE_REPAIR:
                if (configManager.isMarketServiceEnabled() && player.hasPermission(PERM_SERVICE)) startCreateRepair(player);
                break;
            case SLOT_CREATE_ENCHANT:
                if (configManager.isMarketServiceEnabled() && player.hasPermission(PERM_SERVICE)) startCreateEnchant(player);
                break;
            case SLOT_HELP:
                player.closeInventory();
                sendHelp(player);
                break;
            case SLOT_CLOSE:
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    // ====================================================================
    // 登録フロー（出品・募集・修理・エンチャント）
    // ====================================================================

    private void startCreateListing(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(configManager.getMarketMessage("invalid_item"));
            return; // GUI は開いたまま
        }
        String itemName = MarketGUIUtil.prettifyMaterial(hand.getType().name());
        player.closeInventory();
        chatInput.prompt(player,
                "§e「" + itemName + "」の出品価格を金塊（整数）で入力してください。",
                priceStr -> {
                    Double price = parsePrice(player, priceStr);
                    if (price == null) {
                        reopenHub(player);
                        return;
                    }
                    MarketResult result = marketManager.createListing(player, price);
                    player.sendMessage(MarketMessages.format(configManager, result, itemName, price));
                    reopenHub(player);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void startCreateBuyOrder(Player player) {
        player.closeInventory();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String example = (hand != null && hand.getType() != Material.AIR)
                ? hand.getType().name() : "DIAMOND";
        chatInput.prompt(player,
                "§e募集したいアイテム名を入力してください。§7（例: " + example + "）",
                materialStr -> {
                    Material material = Material.matchMaterial(materialStr.toUpperCase());
                    if (material == null || material == Material.AIR) {
                        player.sendMessage(configManager.getMarketMessage("invalid_item"));
                        reopenHub(player);
                        return;
                    }
                    promptBuyAmount(player, material);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void promptBuyAmount(Player player, Material material) {
        chatInput.prompt(player,
                "§e募集する個数を入力してください。",
                amountStr -> {
                    Integer amount = parsePositiveInt(player, amountStr);
                    if (amount == null) {
                        reopenHub(player);
                        return;
                    }
                    promptBuyPrice(player, material, amount);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void promptBuyPrice(Player player, Material material, int amount) {
        chatInput.prompt(player,
                "§e募集価格を金塊（整数）で入力してください。§7（前払い）",
                priceStr -> {
                    Double price = parsePrice(player, priceStr);
                    if (price == null) {
                        reopenHub(player);
                        return;
                    }
                    MarketResult result = marketManager.createBuyOrder(player, material, amount, price);
                    sendBuyOrderMessage(player, result, MarketGUIUtil.prettifyMaterial(material.name()), amount, price);
                    reopenHub(player);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void startCreateRepair(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || !ServiceProcessor.isRepairable(hand)) {
            player.sendMessage(configManager.getMarketMessage("invalid_service_item"));
            return;
        }
        String itemName = MarketGUIUtil.prettifyMaterial(hand.getType().name());
        player.closeInventory();
        chatInput.prompt(player,
                "§e「" + itemName + "」の修理報酬を金塊（整数）で入力してください。§7（前払い）",
                priceStr -> {
                    Double price = parsePrice(player, priceStr);
                    if (price == null) {
                        reopenHub(player);
                        return;
                    }
                    MarketResult result = marketManager.createRepairRequest(player, price);
                    sendServiceMessage(player, result, itemName, "修理", price);
                    reopenHub(player);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void startCreateEnchant(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(configManager.getMarketMessage("invalid_service_item"));
            return;
        }
        String itemName = MarketGUIUtil.prettifyMaterial(hand.getType().name());
        player.closeInventory();
        player.sendMessage("§7指定できるエンチャント: §f"
                + String.join(", ", configManager.getMarketServiceAllowedEnchantments()));
        chatInput.prompt(player,
                "§eエンチャント名を入力してください。",
                enchantKey -> promptEnchantLevel(player, itemName, enchantKey),
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void promptEnchantLevel(Player player, String itemName, String enchantKey) {
        chatInput.prompt(player,
                "§eエンチャントレベルを入力してください。§7（1〜"
                        + configManager.getMarketServiceMaxEnchantLevel() + "）",
                levelStr -> {
                    Integer level = parsePositiveInt(player, levelStr);
                    if (level == null) {
                        reopenHub(player);
                        return;
                    }
                    promptEnchantPrice(player, itemName, enchantKey, level);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    private void promptEnchantPrice(Player player, String itemName, String enchantKey, int level) {
        chatInput.prompt(player,
                "§e報酬を金塊（整数）で入力してください。§7（前払い）",
                priceStr -> {
                    Double price = parsePrice(player, priceStr);
                    if (price == null) {
                        reopenHub(player);
                        return;
                    }
                    MarketResult result = marketManager.createEnchantRequest(player, enchantKey, level, price);
                    sendServiceMessage(player, result, itemName, "エンチャント", price);
                    reopenHub(player);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
    }

    // ====================================================================
    // ヘルパー
    // ====================================================================

    /**
     * 1 tick 後にハブ GUI を再表示する（closeInventory 直後の再 open を避ける）。
     */
    private void reopenHub(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        }, 1L);
    }

    private Double parsePrice(Player player, String input) {
        double price;
        try {
            price = Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.getMarketMessage("input_invalid_number"));
            return null;
        }
        if (!marketManager.isValidPrice(price)) {
            player.sendMessage(MarketMessages.format(configManager, MarketResult.INVALID_PRICE, "", 0));
            return null;
        }
        return price;
    }

    private Integer parsePositiveInt(Player player, String input) {
        int value;
        try {
            value = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.getMarketMessage("input_invalid_number"));
            return null;
        }
        if (value <= 0) {
            player.sendMessage(configManager.getMarketMessage("input_invalid_number"));
            return null;
        }
        return value;
    }

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

    private void sendServiceMessage(Player player, MarketResult result, String item, String service, double price) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", item != null ? item : "",
                "service", service != null ? service : "",
                "price", MarketGUIUtil.formatPrice(price),
                "proceeds", String.valueOf(marketManager.calculateSellerProceeds(price)),
                "currency", configManager.getCurrencyName(),
                "min", String.valueOf((long) configManager.getMarketMinPrice()),
                "max", String.valueOf((long) configManager.getMarketMaxPrice()),
                "limit", String.valueOf(configManager.getMarketServiceMaxRequestsPerPlayer())));
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== マーケットの使い方 ===");
        player.sendMessage("§e/market §7- このGUIを開きます（全操作の入口）");
        player.sendMessage("§7・出品一覧/募集一覧/サービス一覧 から購入・供給・引受ができます");
        player.sendMessage("§7・出品する/募集する/修理を依頼/エンチャントを依頼 で新規登録できます");
        player.sendMessage("§7・登録時は価格などをチャットで入力します（cancel で中止）");
        player.sendMessage("§7従来のコマンド（/market sell 等）も引き続き使えます");
    }
}
