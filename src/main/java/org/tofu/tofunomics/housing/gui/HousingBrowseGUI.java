package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.models.HousingProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 賃貸可能物件の一覧 GUI（ページング）。物件をクリックすると詳細（{@link HousingDetailGUI}）へ遷移する。
 */
public class HousingBrowseGUI {

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
    private HousingDetailGUI detailGUI;

    public HousingBrowseGUI(TofuNomics plugin, ConfigManager configManager,
                            HousingRentalManager rentalManager, HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.listener = listener;
    }

    public void setGUIs(HousingHubGUI hubGUI, HousingDetailGUI detailGUI) {
        this.hubGUI = hubGUI;
        this.detailGUI = detailGUI;
    }

    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6住居賃貸 - 物件一覧");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.BROWSE, gui);
            render(session);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("住居物件一覧GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    public void render(HousingGUISession session) {
        Inventory gui = session.getInventory();
        gui.clear();
        session.clearSlots();

        List<HousingProperty> properties = rentalManager.getAvailableProperties();

        int totalPages = Math.max(1, (int) Math.ceil(properties.size() / (double) ITEMS_PER_PAGE));
        if (session.getPage() >= totalPages) {
            session.setPage(totalPages - 1);
        }
        if (session.getPage() < 0) {
            session.setPage(0);
        }

        int start = session.getPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, properties.size());

        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            HousingProperty property = properties.get(i);
            gui.setItem(slot, buildPropertyItem(property));
            session.putSlotProperty(slot, property);
            slot++;
        }

        setupNavigation(gui, session, properties.size(), totalPages);
    }

    private ItemStack buildPropertyItem(HousingProperty property) {
        return GuiUtil.createButton(Material.OAK_DOOR, "§f" + property.getPropertyName(),
                Arrays.asList(
                        "§7ID: §f" + property.getId(),
                        "§7ワールド: §f" + property.getWorldName(),
                        property.getDescription() != null ? "§7説明: §f" + property.getDescription() : "§7説明: §8なし",
                        "",
                        "§7日額: §a" + GuiUtil.formatPrice(property.getDailyRent()),
                        "§7週額: §a" + GuiUtil.formatPrice(property.getWeeklyRent()),
                        "§7月額: §a" + GuiUtil.formatPrice(property.getMonthlyRent()),
                        "",
                        "§eクリックで詳細・契約"
                ));
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

        gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.BOOK, "§6物件情報",
                Arrays.asList(
                        "§f賃貸可能: §a" + total + " 件",
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
            render(session);
            return;
        }
        if (slot == SLOT_NEXT) {
            session.setPage(session.getPage() + 1);
            render(session);
            return;
        }

        HousingProperty property = session.getPropertyAtSlot(slot);
        if (property == null) {
            return;
        }
        if (detailGUI != null) {
            detailGUI.open(player, property);
        }
    }
}
