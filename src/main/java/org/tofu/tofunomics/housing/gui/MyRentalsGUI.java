package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.models.HousingProperty;
import org.tofu.tofunomics.models.HousingRental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自分の契約一覧 GUI（ページング）。契約をクリックすると管理画面（{@link RentalManageGUI}）へ遷移する。
 */
public class MyRentalsGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEM_START_SLOT = 9;
    private static final int ITEM_END_SLOT = 44;
    private static final int ITEMS_PER_PAGE = ITEM_END_SLOT - ITEM_START_SLOT + 1; // 36
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 53;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingRentalManager rentalManager;
    private final HousingGUIListener listener;

    private HousingHubGUI hubGUI;
    private RentalManageGUI manageGUI;

    public MyRentalsGUI(TofuNomics plugin, ConfigManager configManager,
                        HousingRentalManager rentalManager, HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.listener = listener;
    }

    public void setGUIs(HousingHubGUI hubGUI, RentalManageGUI manageGUI) {
        this.hubGUI = hubGUI;
        this.manageGUI = manageGUI;
    }

    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6住居賃貸 - 自分の契約");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.MY_RENTALS, gui);
            render(player, session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("自分の契約GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    public void render(Player player, HousingGUISession session) {
        Inventory gui = session.getInventory();
        gui.clear();
        session.clearSlots();

        World world = resolveWorld();
        List<HousingRental> rentals = rentalManager.getPlayerRentals(player.getUniqueId());

        int totalPages = Math.max(1, (int) Math.ceil(rentals.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, rentals.size());

        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            HousingRental rental = rentals.get(i);
            gui.setItem(slot, buildRentalItem(rental, world));
            session.putSlotRental(slot, rental);
            slot++;
        }

        setupNavigation(gui, session, rentals.size(), totalPages);
    }

    private ItemStack buildRentalItem(HousingRental rental, World world) {
        HousingProperty property = rentalManager.getProperty(rental.getPropertyId());
        String name = property != null ? property.getPropertyName() : "物件 #" + rental.getPropertyId();
        String remaining = world != null ? rental.getFormattedRemainingTime(world) : "不明";

        List<String> lore = new ArrayList<>();
        lore.add("§7物件ID: §f" + rental.getPropertyId());
        lore.add("§7残り: §e" + remaining);
        lore.add("§7支払総額: §a" + GuiUtil.formatPrice(rental.getTotalCost()));
        lore.add("");
        lore.add("§eクリックで延長・解約");
        return GuiUtil.createButton(Material.PAPER, "§f" + name, lore);
    }

    private void setupNavigation(Inventory gui, HousingGUISession session, int total, int totalPages) {
        if (session.getPage() > 0) {
            gui.setItem(SLOT_PREV, GuiUtil.createButton(Material.ARROW, "§a前のページ",
                    Collections.singletonList("§7ページ " + session.getPage() + " へ")));
        }
        if (session.getPage() < totalPages - 1) {
            gui.setItem(SLOT_NEXT, GuiUtil.createButton(Material.ARROW, "§a次のページ",
                    Collections.singletonList("§7ページ " + (session.getPage() + 2) + " へ")));
        }

        gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.BOOK, "§6契約情報",
                java.util.Arrays.asList(
                        "§f契約中: §a" + total + " 件",
                        "§fページ: §e" + (session.getPage() + 1) + " / " + totalPages
                )));

        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.BARRIER, "§e戻る",
                Collections.singletonList("§7ハブメニューへ戻ります")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = GuiUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = 45; i < INVENTORY_SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    public void handleClick(Player player, HousingGUISession session, int slot, ClickType clickType) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            if (hubGUI != null) hubGUI.open(player);
            return;
        }
        if (slot == SLOT_PREV) {
            session.setPage(session.getPage() - 1);
            render(player, session);
            return;
        }
        if (slot == SLOT_NEXT) {
            session.setPage(session.getPage() + 1);
            render(player, session);
            return;
        }

        HousingRental rental = session.getRentalAtSlot(slot);
        if (rental == null) {
            return;
        }
        if (manageGUI != null) {
            manageGUI.open(player, rental);
        }
    }

    private World resolveWorld() {
        String worldName = plugin.getConfig().getString("housing_rental.world_name", "world");
        return Bukkit.getWorld(worldName);
    }
}
