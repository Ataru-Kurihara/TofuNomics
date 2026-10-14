package org.tofu.tofunomics.guide;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.gui.BookUtil;
import org.tofu.tofunomics.gui.GuiUtil;
import org.tofu.tofunomics.jobs.gui.JobsHubGUI;
import org.tofu.tofunomics.npc.gui.GuiSafety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遊び方ガイドのメニュー（/guide）。
 *
 * 項目（はじめに・ルール・NPC・お金・職業・家と土地・よくある質問）を並べ、
 * 押すとその項目の本を画面に開く（本は配らない）。本文は config/guide.yml にある。
 * 「職業一覧」ボタンは /jobs と同じ職業GUIを開く。
 */
public class GuideGUI implements Listener {

    public static final int SIZE = 27;
    private static final int SLOT_JOBS = 22;
    private static final int SLOT_CLOSE = 26;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private JobsHubGUI jobsHubGUI;

    // プレイヤー → 開いているメニュー
    private final Map<UUID, Inventory> sessions = new ConcurrentHashMap<>();

    public GuideGUI(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setJobsHubGUI(JobsHubGUI jobsHubGUI) {
        this.jobsHubGUI = jobsHubGUI;
    }

    public List<GuideTopic> getTopics() {
        return GuideTopic.loadAll(configManager.getGuideTopicsSection(), SIZE);
    }

    public GuideTopic findTopic(String id) {
        for (GuideTopic topic : getTopics()) {
            if (topic.getId().equalsIgnoreCase(id)) {
                return topic;
            }
        }
        return null;
    }

    public void open(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, SIZE, "§6§l遊び方ガイド");
            for (GuideTopic topic : getTopics()) {
                List<String> lore = new ArrayList<>();
                for (String line : topic.getDescription()) {
                    lore.add(color("&7" + line));
                }
                lore.add("");
                lore.add("§eクリックで読む");
                gui.setItem(topic.getSlot(), GuiUtil.createButton(topic.getIcon(), color(topic.getName()), lore));
            }
            if (jobsHubGUI != null) {
                gui.setItem(SLOT_JOBS, GuiUtil.createButton(Material.IRON_PICKAXE, "§b§l職業一覧を開く",
                        java.util.Arrays.asList(
                                "§7職業ごとの稼ぎ方を見て、",
                                "§7就職・ガイドブックの入手ができます",
                                "§8（/jobs と同じ画面）",
                                "",
                                "§eクリックで開く")));
            }
            gui.setItem(SLOT_CLOSE, GuiUtil.createButton(Material.BARRIER, "§c閉じる",
                    Collections.singletonList("§7ガイドを閉じます")));
            if (configManager.isGuiDecorationEnabled()) {
                ItemStack glass = GuiUtil.createButton(Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
                for (int i = 0; i < SIZE; i++) {
                    if (gui.getItem(i) == null) {
                        gui.setItem(i, glass);
                    }
                }
            }
            sessions.put(player.getUniqueId(), gui);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("遊び方ガイドの表示に失敗しました: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "ガイドの表示に失敗しました。");
        }
    }

    /**
     * 項目の本を画面に開く（本は配らない）。ルールの項目では、ルールのページの URL も出す。
     */
    public void openTopic(Player player, GuideTopic topic) {
        player.openBook(BookUtil.createBook(topic.getName(), configManager.getGuideAuthor(), topic.getPages()));
        if ("rules".equals(topic.getId())) {
            player.sendMessage("§e詳しいルールは §b/rules §eで開けるページにもあります。");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory gui = sessions.get(player.getUniqueId());
        if (gui == null || !gui.equals(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!GuiSafety.isTopSlot(slot, SIZE)) {
            return;
        }
        try {
            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_JOBS && jobsHubGUI != null) {
                jobsHubGUI.open(player);
                return;
            }
            for (GuideTopic topic : getTopics()) {
                if (topic.getSlot() == slot) {
                    // 本はインベントリの画面を閉じてから、次の tick で開く
                    //（クリック処理の中で別の画面を開くと表示が崩れることがあるため）
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            openTopic(player, topic);
                        }
                    });
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("遊び方ガイドのクリック処理中にエラーが発生しました: " + e.getMessage());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory gui = sessions.get(event.getWhoClicked().getUniqueId());
        if (gui != null && gui.equals(event.getInventory())
                && GuiSafety.dragTouchesTop(event.getRawSlots(), SIZE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Inventory gui = sessions.get(playerId);
        if (GuiSafety.isSessionInventory(gui, event.getInventory())) {
            sessions.remove(playerId);
        }
    }

    public void closeAll() {
        for (UUID playerId : sessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
