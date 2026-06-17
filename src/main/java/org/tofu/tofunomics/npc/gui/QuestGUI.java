package org.tofu.tofunomics.npc.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.npc.QuestNPCManager;
import org.tofu.tofunomics.quests.QuestDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クエスト受注NPCのGUI
 *
 * クエスト一覧を1スロット1クエストで表示し、未受注クエストのクリックで受注、
 * 受注中クエストのクリックで納品を行う。BankGUI と同型に自身が Listener を実装する。
 */
public class QuestGUI implements Listener {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final QuestNPCManager questNPCManager;

    private final Map<UUID, QuestGUISession> activeSessions = new ConcurrentHashMap<>();

    // クエストアイコンを並べる先頭スロット（27スロットインベントリの中央付近）
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int CLOSE_SLOT = 26;

    public QuestGUI(TofuNomics plugin, ConfigManager configManager, QuestNPCManager questNPCManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questNPCManager = questNPCManager;
    }

    private static class QuestGUISession {
        private final UUID playerId;
        private final Inventory inventory;
        // スロット番号 -> questId のマッピング
        private final Map<Integer, String> slotToQuestId = new HashMap<>();

        public QuestGUISession(UUID playerId, Inventory inventory) {
            this.playerId = playerId;
            this.inventory = inventory;
        }

        public UUID getPlayerId() { return playerId; }
        public Inventory getInventory() { return inventory; }
        public Map<Integer, String> getSlotToQuestId() { return slotToQuestId; }
    }

    /**
     * クエストGUIを開く
     */
    public void openQuestGUI(Player player) {
        try {
            String title = "§6討伐クエスト受付";
            Inventory gui = Bukkit.createInventory(null, 27, title);

            QuestGUISession session = new QuestGUISession(player.getUniqueId(), gui);
            activeSessions.put(player.getUniqueId(), session);

            setupQuestGUIItems(session, player);
            player.openInventory(gui);

        } catch (Exception e) {
            plugin.getLogger().severe("クエストGUI作成中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§cクエストメニューの表示に失敗しました。");
        }
    }

    /**
     * GUIアイテムを配置（受注状態・所持数を反映して再描画にも使う）
     */
    private void setupQuestGUIItems(QuestGUISession session, Player player) {
        Inventory gui = session.getInventory();
        gui.clear();
        session.getSlotToQuestId().clear();

        // 受注枠（同時受注上限）の使用状況
        int maxQuests = questNPCManager.getMaxConcurrentQuests();
        int acceptedCount = questNPCManager.getAcceptedCount(player);
        int remaining = Math.max(0, maxQuests - acceptedCount);

        // ヘッダー
        gui.setItem(4, createGUIItem(Material.WRITABLE_BOOK, "§6討伐クエスト",
            Arrays.asList(
                "§7敵モブのドロップを集めて納品しよう",
                "§7クリックで受注 / 受注中はクリックで納品",
                "",
                "§f受注枠: §e" + acceptedCount + " / " + maxQuests + " §7（残り " + remaining + "）"
            )));

        Collection<QuestDefinition> quests = questNPCManager.getAllQuestDefinitions();
        int slotIndex = 0;
        for (QuestDefinition def : quests) {
            if (slotIndex >= QUEST_SLOTS.length) {
                // スロット数を超えるクエストは表示しきれない（運用上は QUEST_SLOTS を拡張）
                plugin.getLogger().warning("表示可能なクエスト数（" + QUEST_SLOTS.length + "）を超えています。一部のクエストは表示されません。");
                break;
            }
            int slot = QUEST_SLOTS[slotIndex];
            boolean accepted = questNPCManager.isQuestAccepted(player, def.getQuestId());
            int held = questNPCManager.getHeldAmount(player, def.getTargetMaterial());

            gui.setItem(slot, createQuestItem(def, accepted, held, acceptedCount, maxQuests));
            session.getSlotToQuestId().put(slot, def.getQuestId());
            slotIndex++;
        }

        // 閉じるボタン
        gui.setItem(CLOSE_SLOT, createGUIItem(Material.BARRIER, "§c閉じる",
            Collections.singletonList("§7GUIを閉じます")));

        // 装飾
        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glassPane = createGUIItem(Material.YELLOW_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int slot = 0; slot < gui.getSize(); slot++) {
                if (gui.getItem(slot) == null) {
                    gui.setItem(slot, glassPane);
                }
            }
        }
    }

    /**
     * 1クエスト分の表示アイテムを生成
     */
    private ItemStack createQuestItem(QuestDefinition def, boolean accepted, int held, int acceptedCount, int maxQuests) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.getDescription());
        lore.add("");
        lore.add("§f対象: §e" + def.getTargetMaterial().name());
        lore.add("§f必要数: §e" + def.getRequiredAmount() + " 個");
        lore.add("§f報酬: §6" + def.getRewardNuggets() + " 金塊");
        lore.add("§f所持数: " + (held >= def.getRequiredAmount() ? "§a" : "§c") + held + " / " + def.getRequiredAmount());
        lore.add("§f受注枠: §e" + acceptedCount + " / " + maxQuests);
        lore.add("");
        if (accepted) {
            if (held >= def.getRequiredAmount()) {
                lore.add("§a▶ 受注中 - クリックで納品！");
            } else {
                lore.add("§e▶ 受注中 - アイテムを集めよう");
            }
        } else if (acceptedCount >= maxQuests) {
            lore.add("§c✖ 受注枠が上限です（他のクエストを納品/整理してください）");
        } else {
            lore.add("§b▶ クリックで受注");
        }

        String prefix = accepted ? "§a[受注中] " : "§f";
        return createGUIItem(def.getTargetMaterial(), prefix + def.getDisplayName(), lore);
    }

    private ItemStack createGUIItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        QuestGUISession session = activeSessions.get(playerId);
        if (session == null || !session.getInventory().equals(event.getInventory())) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String questId = session.getSlotToQuestId().get(slot);
        if (questId == null) {
            return;
        }

        try {
            if (questNPCManager.isQuestAccepted(player, questId)) {
                // 受注中 → 納品を試行
                questNPCManager.deliverQuest(player, questId);
            } else {
                // 未受注 → 受注
                questNPCManager.acceptQuest(player, questId);
            }
            // 状態が変わったので再描画
            setupQuestGUIItems(session, player);
        } catch (Exception e) {
            plugin.getLogger().severe("クエストGUIクリック処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        activeSessions.remove(player.getUniqueId());
    }

    public void closeAllGUIs() {
        for (QuestGUISession session : activeSessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    public int getActiveSessionsCount() {
        return activeSessions.size();
    }
}
