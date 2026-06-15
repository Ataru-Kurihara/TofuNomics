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
import org.tofu.tofunomics.gui.ChatInputManager;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.models.HousingProperty;
import org.tofu.tofunomics.models.HousingRental;

import java.util.Arrays;
import java.util.Collections;

/**
 * 契約管理 GUI（延長・解約）と、解約の確認画面を扱う。
 *
 * 延長は追加日数をチャット入力。解約は不可逆のため確認画面（はい/いいえ）を挟む。
 */
public class RentalManageGUI {

    private static final int SIZE = 27;
    private static final int SLOT_ICON = 4;
    private static final int SLOT_EXTEND = 11;
    private static final int SLOT_CANCEL = 15;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    // 確認画面
    private static final int SLOT_CONFIRM_YES = 11;
    private static final int SLOT_CONFIRM_NO = 15;

    private static final int INPUT_TIMEOUT_SECONDS = 60;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingRentalManager rentalManager;
    private final ChatInputManager chatInput;
    private final HousingGUIListener listener;

    private HousingHubGUI hubGUI;
    private MyRentalsGUI myRentalsGUI;

    public RentalManageGUI(TofuNomics plugin, ConfigManager configManager,
                           HousingRentalManager rentalManager, ChatInputManager chatInput,
                           HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.chatInput = chatInput;
        this.listener = listener;
    }

    public void setGUIs(HousingHubGUI hubGUI, MyRentalsGUI myRentalsGUI) {
        this.hubGUI = hubGUI;
        this.myRentalsGUI = myRentalsGUI;
    }

    public void open(Player player, HousingRental rental) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6契約管理");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.MANAGE, gui);
            session.setSelectedRentalId(rental.getId());
            session.setSelectedPropertyId(rental.getPropertyId());
            renderManage(gui, rental);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("契約管理GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void openCancelConfirm(Player player, int propertyId) {
        HousingProperty property = rentalManager.getProperty(propertyId);
        String name = property != null ? property.getPropertyName() : "物件 #" + propertyId;
        Inventory gui = Bukkit.createInventory(null, SIZE, "§c解約の確認");
        HousingGUISession session = new HousingGUISession(
                player.getUniqueId(), HousingGUISession.Type.CANCEL_CONFIRM, gui);
        session.setSelectedPropertyId(propertyId);
        renderConfirm(gui, name);
        listener.registerSession(player.getUniqueId(), session);
        player.openInventory(gui);
    }

    private void renderManage(Inventory gui, HousingRental rental) {
        World world = resolveWorld();
        HousingProperty property = rentalManager.getProperty(rental.getPropertyId());
        String name = property != null ? property.getPropertyName() : "物件 #" + rental.getPropertyId();
        String remaining = world != null ? rental.getFormattedRemainingTime(world) : "不明";

        gui.setItem(SLOT_ICON, GuiUtil.createButton(Material.PAPER, "§f§l" + name,
                Arrays.asList(
                        "§7物件ID: §f" + rental.getPropertyId(),
                        "§7残り: §e" + remaining,
                        "§7支払総額: §a" + GuiUtil.formatPrice(rental.getTotalCost())
                )));
        gui.setItem(SLOT_EXTEND, GuiUtil.createButton(Material.CLOCK, "§e§l延長する",
                Arrays.asList("§7追加日数をチャットで入力します", "§7（日額 × 日数を追加で支払い）")));
        gui.setItem(SLOT_CANCEL, GuiUtil.createButton(Material.RED_CONCRETE, "§c§l解約する",
                Collections.singletonList("§7契約を解約します（確認あり）")));
        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.BARRIER, "§e戻る",
                Collections.singletonList("§7自分の契約一覧へ戻ります")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        decorate(gui);
    }

    private void renderConfirm(Inventory gui, String name) {
        gui.setItem(SLOT_ICON, GuiUtil.createButton(Material.RED_CONCRETE, "§c§l本当に解約しますか？",
                Arrays.asList("§7対象: §f" + name, "§7解約すると物件は他の人が借りられます")));
        gui.setItem(SLOT_CONFIRM_YES, GuiUtil.createButton(Material.LIME_CONCRETE, "§a§lはい、解約する",
                Collections.singletonList("§7契約を解約します")));
        gui.setItem(SLOT_CONFIRM_NO, GuiUtil.createButton(Material.RED_CONCRETE, "§c§lいいえ",
                Collections.singletonList("§7解約をやめて契約一覧へ戻ります")));
        decorate(gui);
    }

    private void decorate(Inventory gui) {
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
        if (session.getType() == HousingGUISession.Type.CANCEL_CONFIRM) {
            handleConfirmClick(player, session, slot);
            return;
        }
        // MANAGE
        switch (slot) {
            case SLOT_EXTEND:
                startExtend(player, session.getSelectedPropertyId());
                break;
            case SLOT_CANCEL:
                openCancelConfirm(player, session.getSelectedPropertyId());
                break;
            case SLOT_BACK:
                if (myRentalsGUI != null) myRentalsGUI.open(player);
                break;
            case SLOT_CLOSE:
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    private void handleConfirmClick(Player player, HousingGUISession session, int slot) {
        if (slot == SLOT_CONFIRM_YES) {
            HousingRentalManager.RentalResult result =
                    rentalManager.cancelRental(player.getUniqueId(), session.getSelectedPropertyId());
            player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.getMessage());
            reopenHub(player);
        } else if (slot == SLOT_CONFIRM_NO) {
            if (myRentalsGUI != null) myRentalsGUI.open(player);
        }
    }

    private void startExtend(Player player, int propertyId) {
        player.closeInventory();
        chatInput.prompt(player,
                "§e延長する日数を入力してください。",
                input -> {
                    Integer days = parsePositiveInt(player, input);
                    if (days == null) {
                        reopenHub(player);
                        return;
                    }
                    HousingRentalManager.RentalResult result =
                            rentalManager.extendRental(player.getUniqueId(), propertyId, days);
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

    private World resolveWorld() {
        String worldName = plugin.getConfig().getString("housing_rental.world_name", "world");
        return Bukkit.getWorld(worldName);
    }
}
