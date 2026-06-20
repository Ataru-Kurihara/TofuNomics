package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理者向けの物件編集 GUI。
 *
 * 物件の賃料変更（チャット入力）・テレポート・WorldGuard情報表示・削除（確認画面あり）を提供する。
 * チャット入力でGUIを開き直しても対象物件を保持するため、編集中の物件IDをプレイヤーごとに
 * {@link #editing} で保持する（GUIセッションはクローズ時に破棄されるため）。
 */
public class HousingAdminEditGUI {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_RENT = 10;
    private static final int SLOT_TELEPORT = 12;
    private static final int SLOT_WG = 14;
    private static final int SLOT_DELETE = 16;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_CLOSE = 26;

    // 削除確認画面
    private static final int SLOT_CONFIRM_YES = 11;
    private static final int SLOT_CONFIRM_NO = 15;

    private static final int INPUT_TIMEOUT_SECONDS = 60;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingRentalManager rentalManager;
    private final ChatInputManager chatInput;
    private final HousingGUIListener listener;

    private HousingAdminListGUI listGUI;

    /** 編集中の物件ID（プレイヤーごと、reopen時の保持用） */
    private final Map<UUID, Integer> editing = new ConcurrentHashMap<>();

    public HousingAdminEditGUI(TofuNomics plugin, ConfigManager configManager,
                               HousingRentalManager rentalManager, ChatInputManager chatInput,
                               HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.chatInput = chatInput;
        this.listener = listener;
    }

    public void setGUIs(HousingAdminListGUI listGUI) {
        this.listGUI = listGUI;
    }

    public void open(Player player, int propertyId) {
        editing.put(player.getUniqueId(), propertyId);
        render(player);
    }

    private void render(Player player) {
        Integer propertyId = editing.get(player.getUniqueId());
        if (propertyId == null) {
            backToList(player);
            return;
        }
        HousingProperty property = rentalManager.getProperty(propertyId);
        if (property == null) {
            player.sendMessage("§c物件が見つかりません（削除された可能性があります）");
            editing.remove(player.getUniqueId());
            backToList(player);
            return;
        }

        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6物件編集 - " + property.getPropertyName());
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.ADMIN_EDIT, gui);
            session.setSelectedPropertyId(propertyId);
            renderItems(gui, property);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("物件編集GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void renderItems(Inventory gui, HousingProperty property) {
        gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.PAPER, "§f§l" + property.getPropertyName(),
                Arrays.asList(
                        "§7ID: §f" + property.getId(),
                        "§7ワールド: §f" + property.getWorldName(),
                        "§7状態: " + (property.isAvailable() ? "§a利用可能" : "§c賃貸中"),
                        "",
                        "§7日額: §a" + GuiUtil.formatPrice(property.getDailyRent()),
                        "§7週額: §a" + GuiUtil.formatPrice(property.getWeeklyRent()),
                        "§7月額: §a" + GuiUtil.formatPrice(property.getMonthlyRent())
                )));

        gui.setItem(SLOT_RENT, GuiUtil.createButton(Material.GOLD_INGOT, "§e§l賃料変更",
                Arrays.asList(
                        "§7現在の日額: §a" + GuiUtil.formatPrice(property.getDailyRent()),
                        "§7クリックで新しい日額をチャット入力",
                        "§7（週額・月額は自動再計算）"
                )));

        gui.setItem(SLOT_TELEPORT, GuiUtil.createButton(Material.ENDER_PEARL, "§b§lテレポート",
                property.hasCoordinates()
                        ? Collections.singletonList("§7物件の位置へ移動します")
                        : Collections.singletonList("§8座標情報がないため移動できません")));

        gui.setItem(SLOT_WG, GuiUtil.createButton(Material.SPYGLASS, "§b§lWorldGuard情報",
                buildWgInfoLore(property)));

        gui.setItem(SLOT_DELETE, GuiUtil.createButton(Material.RED_CONCRETE, "§c§l物件を削除",
                Arrays.asList(
                        "§7この物件を削除します（確認あり）",
                        property.isAvailable() ? "§7WG領域も併せて削除されます" : "§c賃貸中の物件は削除できません"
                )));

        gui.setItem(SLOT_BACK, GuiUtil.createButton(Material.BARRIER, "§e戻る",
                Collections.singletonList("§7物件一覧へ戻ります")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                Collections.singletonList("§7GUIを閉じます")));

        decorate(gui);
    }

    private List<String> buildWgInfoLore(HousingProperty property) {
        List<String> lore = new ArrayList<>();
        if (property.hasWorldGuardRegion()) {
            lore.add("§7WG領域ID: §f" + property.getWorldguardRegionId());
        } else {
            lore.add("§7WG領域: §8未設定");
        }
        if (property.hasCoordinates()) {
            lore.add("§7座標1: §f" + property.getX1() + ", " + property.getY1() + ", " + property.getZ1());
            lore.add("§7座標2: §f" + property.getX2() + ", " + property.getY2() + ", " + property.getZ2());
            lore.add("§7面積(XZ): §f" + property.getArea() + " ブロック");
        } else {
            lore.add("§7座標範囲: §8未設定");
        }
        lore.add("§7状態: " + (property.isAvailable() ? "§a利用可能" : "§c賃貸中"));
        return lore;
    }

    private void openDeleteConfirm(Player player, HousingProperty property) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§c物件削除の確認");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.ADMIN_EDIT_DELETE_CONFIRM, gui);
            session.setSelectedPropertyId(property.getId());

            gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.RED_CONCRETE, "§c§l本当に削除しますか？",
                    Arrays.asList("§7対象: §f" + property.getPropertyName() + " (ID:" + property.getId() + ")",
                            "§7WG領域がある場合は併せて削除されます")));
            gui.setItem(SLOT_CONFIRM_YES, GuiUtil.createButton(Material.LIME_CONCRETE, "§a§lはい、削除する",
                    Collections.singletonList("§7物件を削除します")));
            gui.setItem(SLOT_CONFIRM_NO, GuiUtil.createButton(Material.RED_CONCRETE, "§c§lいいえ",
                    Collections.singletonList("§7削除をやめて編集画面へ戻ります")));
            decorate(gui);

            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("物件削除確認GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
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
        if (session.getType() == HousingGUISession.Type.ADMIN_EDIT_DELETE_CONFIRM) {
            handleConfirmClick(player, session, slot);
            return;
        }

        switch (slot) {
            case SLOT_RENT:
                startRentInput(player, session.getSelectedPropertyId());
                break;
            case SLOT_TELEPORT:
                teleport(player, session.getSelectedPropertyId());
                break;
            case SLOT_DELETE:
                HousingProperty property = rentalManager.getProperty(session.getSelectedPropertyId());
                if (property == null) {
                    player.sendMessage("§c物件が見つかりません");
                    backToList(player);
                    break;
                }
                if (!property.isAvailable()) {
                    player.sendMessage("§c賃貸中の物件は削除できません。先に契約をキャンセルしてください。");
                    break;
                }
                openDeleteConfirm(player, property);
                break;
            case SLOT_BACK:
                editing.remove(player.getUniqueId());
                backToList(player);
                break;
            case SLOT_WG:
                // 情報表示のみ（クリック操作なし）
                break;
            case SLOT_CLOSE:
                editing.remove(player.getUniqueId());
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    private void handleConfirmClick(Player player, HousingGUISession session, int slot) {
        if (slot == SLOT_CONFIRM_YES) {
            HousingRentalManager.RentalResult result =
                    rentalManager.removeProperty(session.getSelectedPropertyId());
            player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.getMessage());
            editing.remove(player.getUniqueId());
            backToList(player);
        } else if (slot == SLOT_CONFIRM_NO) {
            render(player);
        }
    }

    private void startRentInput(Player player, int propertyId) {
        player.closeInventory();
        chatInput.prompt(player,
                "§e新しい日額賃料を数値で入力してください。",
                input -> {
                    Double rent = parsePositiveDouble(player, input);
                    if (rent != null) {
                        HousingRentalManager.RentalResult result =
                                rentalManager.updatePropertyRent(propertyId, rent);
                        player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.getMessage());
                    }
                    reopenLater(player);
                },
                () -> reopenLater(player), INPUT_TIMEOUT_SECONDS);
    }

    private void teleport(Player player, int propertyId) {
        HousingProperty property = rentalManager.getProperty(propertyId);
        if (property == null) {
            player.sendMessage("§c物件が見つかりません");
            return;
        }
        if (!property.hasCoordinates()) {
            player.sendMessage("§c座標情報がないためテレポートできません");
            return;
        }
        World world = Bukkit.getWorld(property.getWorldName());
        if (world == null) {
            player.sendMessage("§cワールド '" + property.getWorldName() + "' が見つかりません");
            return;
        }

        // 座標範囲の中心(XZ)・低い方のYへ移動
        double cx = (property.getX1() + property.getX2()) / 2.0 + 0.5;
        double cz = (property.getZ1() + property.getZ2()) / 2.0 + 0.5;
        double y = Math.min(property.getY1(), property.getY2());
        Location target = new Location(world, cx, y, cz);

        player.closeInventory();
        player.teleport(target);
        player.sendMessage("§a物件 '" + property.getPropertyName() + "' へテレポートしました");
    }

    private Double parsePositiveDouble(Player player, String input) {
        double value;
        try {
            value = Double.parseDouble(input.trim());
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

    private void reopenLater(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                render(player);
            }
        }, 1L);
    }

    private void backToList(Player player) {
        if (listGUI != null) {
            listGUI.open(player);
        } else {
            player.closeInventory();
        }
    }
}
