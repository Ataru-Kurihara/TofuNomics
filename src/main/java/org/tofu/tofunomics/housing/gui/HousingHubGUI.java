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

import java.util.Arrays;
import java.util.Collections;

/**
 * 住居賃貸の統合ハブ GUI。
 *
 * {@code /housing}（無引数）で開く全操作の入口。物件一覧・自分の契約へボタン遷移する。
 * 管理者操作（物件登録など）は従来コマンドのまま（ここには含めない）。
 */
public class HousingHubGUI {

    private static final String PERM_USE = "tofunomics.housing.use";

    private static final int SIZE = 27;
    private static final int SLOT_BROWSE = 11;
    private static final int SLOT_MY_RENTALS = 15;
    private static final int SLOT_HELP = 22;
    private static final int SLOT_CLOSE = 26;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final HousingGUIListener listener;

    private HousingBrowseGUI browseGUI;
    private MyRentalsGUI myRentalsGUI;

    public HousingHubGUI(TofuNomics plugin, ConfigManager configManager, HousingGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.listener = listener;
    }

    public void setGUIs(HousingBrowseGUI browseGUI, MyRentalsGUI myRentalsGUI) {
        this.browseGUI = browseGUI;
        this.myRentalsGUI = myRentalsGUI;
    }

    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6住居賃貸");
            HousingGUISession session = new HousingGUISession(
                    player.getUniqueId(), HousingGUISession.Type.HUB, gui);
            render(gui);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("住居ハブGUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    private void render(Inventory gui) {
        gui.setItem(SLOT_BROWSE, GuiUtil.createButton(Material.OAK_DOOR, "§a§l物件一覧",
                Arrays.asList("§7賃貸可能な物件を見て契約します", "§eクリックで開く")));
        gui.setItem(SLOT_MY_RENTALS, GuiUtil.createButton(Material.PAPER, "§b§l自分の契約",
                Arrays.asList("§7契約中の物件を確認・延長・解約します", "§eクリックで開く")));
        gui.setItem(SLOT_HELP, GuiUtil.createButton(Material.BOOK, "§e§lヘルプ",
                Collections.singletonList("§7住居賃貸の使い方を表示します")));
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
            case SLOT_BROWSE:
                if (browseGUI != null) browseGUI.open(player);
                break;
            case SLOT_MY_RENTALS:
                if (myRentalsGUI != null) myRentalsGUI.open(player);
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

    private void sendHelp(Player player) {
        player.sendMessage("§6=== 住居賃貸の使い方 ===");
        player.sendMessage("§e/housing §7- このGUIを開きます（全操作の入口）");
        player.sendMessage("§7・物件一覧 から物件を選び、日/週/月を選んで契約できます");
        player.sendMessage("§7・自分の契約 から延長・解約ができます");
        player.sendMessage("§7・契約時は期間（数）をチャットで入力します（cancel で中止）");
        player.sendMessage("§7従来のコマンド（/housing rent 等）も引き続き使えます");
    }

    // ハブが対象でないクラスからの再表示用ヘルパー
    void reopenLater(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        }, 1L);
    }
}
