package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.ChatInputManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.models.HousingProperty;

import java.util.Arrays;
import java.util.Collections;

/**
 * 物件詳細 GUI。日/週/月のいずれかを選び、個数（何日/何週/何ヶ月）をチャット入力して契約する。
 */
public class HousingDetailGUI {

    private static final int SIZE = 27;
    private static final int SLOT_ICON = 4;
    private static final int SLOT_RENT_DAILY = 11;
    private static final int SLOT_RENT_WEEKLY = 13;
    private static final int SLOT_RENT_MONTHLY = 15;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    private static final int INPUT_TIMEOUT_SECONDS = 60;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingRentalManager rentalManager;
    private final ChatInputManager chatInput;
    private final HousingGUIListener listener;

    private HousingHubGUI hubGUI;
    private HousingBrowseGUI browseGUI;

    public HousingDetailGUI(TofuNomics plugin, ConfigManager configManager,
                            HousingRentalManager rentalManager, ChatInputManager chatInput,
                            HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.chatInput = chatInput;
        this.listener = listener;
    }

    public void setGUIs(HousingHubGUI hubGUI, HousingBrowseGUI browseGUI) {
        this.hubGUI = hubGUI;
        this.browseGUI = browseGUI;
    }

    public void open(Player player, HousingProperty property) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6物件詳細 - " + property.getPropertyName());
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.DETAIL, gui);
            session.setSelectedPropertyId(property.getId());
            render(gui, property);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("住居物件詳細GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void render(Inventory gui, HousingProperty property) {
        gui.setItem(SLOT_ICON, GuiUtil.createButton(Material.OAK_DOOR, "§f§l" + property.getPropertyName(),
                Arrays.asList(
                        "§7ID: §f" + property.getId(),
                        "§7ワールド: §f" + property.getWorldName(),
                        property.getDescription() != null ? "§7説明: §f" + property.getDescription() : "§7説明: §8なし",
                        "§7状態: " + (property.isAvailable() ? "§a利用可能" : "§c賃貸中")
                )));

        gui.setItem(SLOT_RENT_DAILY, GuiUtil.createButton(Material.LIME_CONCRETE, "§a§l日で借りる",
                Arrays.asList("§7日額: §a" + GuiUtil.formatPrice(property.getDailyRent()),
                        "§7クリック後、日数をチャット入力")));
        gui.setItem(SLOT_RENT_WEEKLY, GuiUtil.createButton(Material.LIME_CONCRETE, "§a§l週で借りる",
                Arrays.asList("§7週額: §a" + GuiUtil.formatPrice(property.getWeeklyRent()),
                        "§7クリック後、週数をチャット入力")));
        gui.setItem(SLOT_RENT_MONTHLY, GuiUtil.createButton(Material.LIME_CONCRETE, "§a§l月で借りる",
                Arrays.asList("§7月額: §a" + GuiUtil.formatPrice(property.getMonthlyRent()),
                        "§7クリック後、月数をチャット入力")));

        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.BARRIER, "§e戻る",
                Collections.singletonList("§7物件一覧へ戻ります")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = GuiUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = 0; i < SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    public void handleClick(Player player, HousingGUISession session, int slot, ClickType clickType) {
        switch (slot) {
            case SLOT_RENT_DAILY:
                startRent(player, session.getSelectedPropertyId(), "daily", "日");
                break;
            case SLOT_RENT_WEEKLY:
                startRent(player, session.getSelectedPropertyId(), "weekly", "週");
                break;
            case SLOT_RENT_MONTHLY:
                startRent(player, session.getSelectedPropertyId(), "monthly", "ヶ月");
                break;
            case SLOT_BACK:
                if (browseGUI != null) browseGUI.open(player);
                break;
            case SLOT_CLOSE:
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    private void startRent(Player player, int propertyId, String period, String unitName) {
        HousingProperty property = rentalManager.getProperty(propertyId);
        if (property == null || !property.isAvailable()) {
            player.sendMessage("§cこの物件は現在契約できません。");
            reopenHub(player);
            player.closeInventory();
            return;
        }
        player.closeInventory();
        chatInput.prompt(player,
                "§e契約期間を入力してください（何" + unitName + "分か、数で入力）。",
                input -> {
                    Integer units = parsePositiveInt(player, input);
                    if (units == null) {
                        reopenHub(player);
                        return;
                    }
                    HousingRentalManager.RentalResult result =
                            rentalManager.rentProperty(player.getUniqueId(), propertyId, period, units);
                    player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.getMessage());
                    reopenHub(player);
                },
                () -> reopenHub(player), INPUT_TIMEOUT_SECONDS);
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

    private void reopenHub(Player player) {
        if (hubGUI != null) {
            hubGUI.reopenLater(player);
        }
    }
}
