package org.tofu.tofunomics.housing.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.tofu.tofunomics.housing.SelectionManager;
import org.tofu.tofunomics.integration.WorldGuardIntegration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理者向け物件登録 GUI。
 *
 * 事前に木の斧で範囲選択した状態で開く。物件名・日額賃料の既定値（連番自動命名・config値）を表示し、
 * クリックでチャット入力による上書きができる。WorldGuard領域の自動作成トグルと、
 * 選択範囲に重なる既存リージョンの確認も提供する。登録ボタンで {@code quickregister} と同じ中核処理を呼ぶ。
 *
 * チャット入力でGUIを開き直す際も入力中の値を保持するため、編集中の状態はプレイヤーごとに
 * {@link #pendingStates} で保持する（GUIセッションはクローズ時に破棄されるため）。
 */
public class HousingAdminRegisterGUI {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_NAME = 10;
    private static final int SLOT_RENT = 12;
    private static final int SLOT_WG_TOGGLE = 14;
    private static final int SLOT_WG_CHECK = 16;
    private static final int SLOT_REGISTER = 22;
    private static final int SLOT_CLOSE = 26;

    private static final int INPUT_TIMEOUT_SECONDS = 60;

    /** 編集中の入力値（プレイヤーごと） */
    private static class PendingState {
        String propertyName;
        double dailyRent;
        boolean createWg;
    }

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingRentalManager rentalManager;
    private final SelectionManager selectionManager;
    private final ChatInputManager chatInput;
    private final HousingGUIListener listener;

    private final Map<UUID, PendingState> pendingStates = new ConcurrentHashMap<>();

    public HousingAdminRegisterGUI(TofuNomics plugin, ConfigManager configManager,
                                   HousingRentalManager rentalManager, SelectionManager selectionManager,
                                   ChatInputManager chatInput, HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rentalManager = rentalManager;
        this.selectionManager = selectionManager;
        this.chatInput = chatInput;
        this.listener = listener;
    }

    /**
     * 登録GUIを新規に開く（既定値で初期化）。コマンドからの入口。
     */
    public void open(Player player) {
        if (!selectionManager.hasCompleteSelection(player)) {
            player.sendMessage("§c範囲選択が完了していません");
            player.sendMessage("§7木の斧で2点を選択してから開いてください");
            return;
        }

        PendingState state = new PendingState();
        state.propertyName = rentalManager.generateNextPropertyName();
        state.dailyRent = configManager.getHousingDefaultDailyRent();
        state.createWg = configManager.isHousingAutoCreateWgRegion();
        pendingStates.put(player.getUniqueId(), state);

        render(player, state);
    }

    /**
     * 編集中の状態を保ったままGUIを開き直す（チャット入力後の戻り用）。
     */
    private void reopen(Player player) {
        PendingState state = pendingStates.get(player.getUniqueId());
        if (state == null) {
            open(player);
            return;
        }
        render(player, state);
    }

    private void render(Player player, PendingState state) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6物件登録");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.ADMIN_REGISTER, gui);
            renderItems(gui, player, state);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("物件登録GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void renderItems(Inventory gui, Player player, PendingState state) {
        int volume = selectionManager.getSelectionVolume(player);
        int area = selectionManager.getSelectionArea(player);

        gui.setItem(SLOT_INFO, GuiUtil.createButton(Material.MAP, "§f§l選択範囲",
                Arrays.asList(
                        "§7ワールド: §f" + player.getWorld().getName(),
                        "§7体積: §f" + volume + " ブロック",
                        "§7面積(XZ): §f" + area + " ブロック"
                )));

        gui.setItem(SLOT_NAME, GuiUtil.createButton(Material.NAME_TAG, "§e§l物件名",
                Arrays.asList(
                        "§7現在: §f" + state.propertyName,
                        "§7クリックでチャット入力して変更"
                )));

        gui.setItem(SLOT_RENT, GuiUtil.createButton(Material.GOLD_INGOT, "§e§l日額賃料",
                Arrays.asList(
                        "§7現在: §a" + GuiUtil.formatPrice(state.dailyRent),
                        "§7クリックでチャット入力して変更"
                )));

        gui.setItem(SLOT_WG_TOGGLE, GuiUtil.createButton(
                state.createWg ? Material.LIME_DYE : Material.GRAY_DYE,
                "§e§lWorldGuard領域の自動作成",
                Arrays.asList(
                        "§7現在: " + (state.createWg ? "§a有効" : "§c無効"),
                        "§7クリックで切り替え",
                        "§7有効時は物件名と同名の領域を作成します"
                )));

        gui.setItem(SLOT_WG_CHECK, GuiUtil.createButton(Material.SPYGLASS, "§b§lWGリージョン確認",
                buildWgCheckLore(player)));

        gui.setItem(SLOT_REGISTER, GuiUtil.createButton(Material.EMERALD_BLOCK, "§a§l登録する",
                Collections.singletonList("§7この内容で物件を登録します")));
        gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§cキャンセル",
                Collections.singletonList("§7登録せずに閉じます")));

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

    private List<String> buildWgCheckLore(Player player) {
        List<String> lore = new ArrayList<>();
        WorldGuardIntegration wg = plugin.getWorldGuardIntegration();
        if (wg == null || !wg.isEnabled()) {
            lore.add("§8WorldGuard統合が無効です");
            return lore;
        }

        Location pos1 = selectionManager.getFirstPosition(player.getUniqueId());
        Location pos2 = selectionManager.getSecondPosition(player.getUniqueId());
        if (pos1 == null || pos2 == null) {
            lore.add("§8選択範囲が未設定です");
            return lore;
        }
        List<String> overlapping = wg.getOverlappingRegionNames(player.getWorld(), pos1, pos2);

        if (overlapping.isEmpty()) {
            lore.add("§a選択範囲に重なる既存リージョンはありません");
        } else {
            lore.add("§e重なる既存リージョン (" + overlapping.size() + "件):");
            int shown = 0;
            for (String name : overlapping) {
                if (shown >= 8) {
                    lore.add("§7...他 " + (overlapping.size() - shown) + " 件");
                    break;
                }
                lore.add("§7- §f" + name);
                shown++;
            }
        }
        return lore;
    }

    public void handleClick(Player player, HousingGUISession session, int slot, ClickType clickType) {
        PendingState state = pendingStates.get(player.getUniqueId());
        if (state == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case SLOT_NAME:
                startNameInput(player, state);
                break;
            case SLOT_RENT:
                startRentInput(player, state);
                break;
            case SLOT_WG_TOGGLE:
                state.createWg = !state.createWg;
                reopen(player);
                break;
            case SLOT_REGISTER:
                doRegister(player, state);
                break;
            case SLOT_CLOSE:
                pendingStates.remove(player.getUniqueId());
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    private void startNameInput(Player player, PendingState state) {
        player.closeInventory();
        chatInput.prompt(player,
                "§e物件名をチャットで入力してください。",
                input -> {
                    String name = input.trim();
                    if (!name.isEmpty()) {
                        state.propertyName = name;
                    }
                    reopen(player);
                },
                () -> reopen(player), INPUT_TIMEOUT_SECONDS);
    }

    private void startRentInput(Player player, PendingState state) {
        player.closeInventory();
        chatInput.prompt(player,
                "§e日額賃料を数値で入力してください。",
                input -> {
                    Double rent = parsePositiveDouble(player, input);
                    if (rent != null) {
                        state.dailyRent = rent;
                    }
                    reopen(player);
                },
                () -> reopen(player), INPUT_TIMEOUT_SECONDS);
    }

    private void doRegister(Player player, PendingState state) {
        player.closeInventory();
        pendingStates.remove(player.getUniqueId());

        if (!selectionManager.hasCompleteSelection(player)) {
            player.sendMessage("§c範囲選択が解除されています。再度選択してください。");
            return;
        }

        Location pos1 = selectionManager.getFirstPosition(player.getUniqueId());
        Location pos2 = selectionManager.getSecondPosition(player.getUniqueId());

        HousingRentalManager.RentalResult result = rentalManager.registerPropertyFromSelection(
                state.propertyName, player.getWorld(), pos1, pos2, state.dailyRent, state.createWg);

        if (result.isSuccess()) {
            player.sendMessage("§a物件 '" + state.propertyName + "' を登録しました（日額: " + state.dailyRent + "）");
            player.sendMessage("§7" + result.getMessage());
            selectionManager.clearSelection(player);
        } else {
            player.sendMessage("§c" + result.getMessage());
        }
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
}
