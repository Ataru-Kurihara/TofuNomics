package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.QuestProgressDAO;
import org.tofu.tofunomics.economy.CurrencyConverter;
import org.tofu.tofunomics.models.QuestProgress;
import org.tofu.tofunomics.quests.QuestDefinition;

import java.sql.SQLException;
import java.util.*;

/**
 * クエスト受注NPCマネージャー
 *
 * 敵モブのドロップを集めて納品すると金塊報酬を受け取れる常設クエストを提供する。
 * クエスト定義は config.yml の npc_system.quest_npc.quests を駆動とし、
 * 受注状態は {@link QuestProgressDAO} で永続化する（進捗は持たず、納品時にインベントリ実数で判定）。
 *
 * 既存の {@code ProcessingNPCManager} / {@code FoodNPCManager} と同型の構成。
 */
public class QuestNPCManager {

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final CurrencyConverter currencyConverter;
    private final QuestProgressDAO questProgressDAO;

    // クエスト受付所のデータを保持（id -> ステーション）
    private final Map<String, QuestStation> questStations = new HashMap<>();

    // クエスト定義（questId -> 定義）。config.yml 駆動・挿入順を保持
    private final Map<String, QuestDefinition> questDefinitions = new LinkedHashMap<>();

    // 同時に受注できるクエストの最大数（config駆動）
    private int maxConcurrentQuests;

    public QuestNPCManager(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager,
                           CurrencyConverter currencyConverter, QuestProgressDAO questProgressDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.currencyConverter = currencyConverter;
        this.questProgressDAO = questProgressDAO;

        loadQuestDefinitions();
    }

    /**
     * config.yml からクエスト定義と同時受注上限を読み込む
     */
    private void loadQuestDefinitions() {
        questDefinitions.clear();
        this.maxConcurrentQuests = configManager.getMaxConcurrentQuests();

        for (QuestDefinition def : configManager.getQuestDefinitions()) {
            questDefinitions.put(def.getQuestId(), def);
        }

        plugin.getLogger().info("クエスト定義を読み込みました: " + questDefinitions.size() + "件（同時受注上限: " + maxConcurrentQuests + "）");
    }

    /**
     * クエスト受付所データクラス
     */
    public static class QuestStation {
        private final String id;
        private final String name;
        private final Location location;
        private UUID npcId;

        public QuestStation(String id, String name, Location location, UUID npcId) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.npcId = npcId;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public UUID getNpcId() { return npcId; }
        public void setNpcId(UUID npcId) { this.npcId = npcId; }
    }

    // ===== NPCライフサイクル =====

    /**
     * クエストNPCシステムを初期化
     */
    public void initializeQuestNPCs() {
        try {
            if (!configManager.isQuestNPCEnabled()) {
                plugin.getLogger().info("クエストNPCシステムは無効化されています");
                return;
            }

            spawnQuestNPCs();
            plugin.getLogger().info("クエストNPCシステムを初期化しました");
        } catch (Exception e) {
            plugin.getLogger().severe("クエストNPCシステムの初期化中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * config.ymlの配置情報に基づいてクエストNPCを生成（既存NPCは座標一致で再利用）
     */
    private void spawnQuestNPCs() {
        questStations.clear();

        // 現在スポーン中のクエストNPCを座標キーで索引（重複生成防止）
        Map<String, UUID> existingNPCsByLocation = new HashMap<>();
        for (NPCManager.NPCData npc : npcManager.getAllNPCs()) {
            if ("quest".equals(npc.getNpcType())) {
                existingNPCsByLocation.put(locationKey(npc.getLocation()), npc.getEntityId());
            }
        }

        List<Map<?, ?>> questConfigs = configManager.getQuestNPCConfigs();

        for (Map<?, ?> config : questConfigs) {
            try {
                String id = (String) config.get("id");
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                if (id == null || name == null || world == null) {
                    plugin.getLogger().warning("クエストNPCの設定が不完全です: " + config);
                    continue;
                }

                int x = ((Number) config.get("x")).intValue();
                int y = ((Number) config.get("y")).intValue();
                int z = ((Number) config.get("z")).intValue();

                float yaw = config.get("yaw") != null ? ((Number) config.get("yaw")).floatValue() : 0.0f;
                float pitch = config.get("pitch") != null ? ((Number) config.get("pitch")).floatValue() : 0.0f;

                Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5, yaw, pitch);
                String key = world + "," + x + "," + y + "," + z;

                UUID npcId;
                if (existingNPCsByLocation.containsKey(key)) {
                    // 既存NPCを再利用
                    npcId = existingNPCsByLocation.get(key);
                    NPCManager.NPCData existing = npcManager.getNPCData(npcId);
                    if (existing != null && existing.getEntity() != null && !name.equals(existing.getName())) {
                        existing.getEntity().setCustomName(name);
                    }
                } else {
                    // 新規生成
                    Villager questNPC = npcManager.createNPC(location, "quest", name);
                    if (questNPC == null) {
                        plugin.getLogger().severe("クエストNPCの生成に失敗しました: " + name);
                        continue;
                    }
                    setupQuestNPC(questNPC);
                    npcId = questNPC.getUniqueId();
                }

                questStations.put(id, new QuestStation(id, name, location, npcId));
                plugin.getLogger().info("クエスト受付所を登録しました: " + name + " [" + x + ", " + y + ", " + z + "]");

            } catch (Exception e) {
                plugin.getLogger().warning("クエストNPC処理中にエラーが発生しました: " + e.getMessage());
            }
        }
    }

    /**
     * クエストNPCの外観設定
     */
    private void setupQuestNPC(Villager npc) {
        // 基本設定は NPCManager.createNPC() で完了済み（職業はNITWIT維持）
        npc.setVillagerLevel(5);
    }

    /**
     * コマンドで生成されたNPCを即座に登録（リロードなし）。NPCCommandからの直接呼び出し用。
     */
    public void registerQuestNPC(UUID npcId, String id, String name, Location location) {
        try {
            questStations.put(id, new QuestStation(id, name, location, npcId));
            plugin.getLogger().info("クエストNPCを登録しました: " + name);
        } catch (Exception e) {
            plugin.getLogger().severe("クエストNPC即座登録中にエラーが発生: " + e.getMessage());
        }
    }

    /**
     * クエストNPCを削除
     */
    public void removeQuestNPCs() {
        for (NPCManager.NPCData npcData : npcManager.getNPCsByType("quest")) {
            npcManager.removeNPC(npcData.getEntityId());
        }
        questStations.clear();
        plugin.getLogger().info("全てのクエストNPCを削除しました");
    }

    /**
     * クエストNPCをリロード（定義の再読込 + NPC再生成）
     */
    public void reloadQuestNPCs() {
        loadQuestDefinitions();
        removeQuestNPCs();
        spawnQuestNPCs();
        plugin.getLogger().info("クエストNPCのリロードが完了しました");
    }

    /**
     * 既存NPCを保持したままクエスト定義と受付所データのみ再読込する。
     * /npc reload（既存NPC保持方針）から呼び出される。
     */
    public void reloadQuestData() {
        loadQuestDefinitions();

        // config.ymlの配置情報と現在スポーン中のNPCを座標で突き合わせ、stationsを再構築
        questStations.clear();
        Map<String, UUID> existingNPCsByLocation = new HashMap<>();
        for (NPCManager.NPCData npc : npcManager.getNPCsByType("quest")) {
            existingNPCsByLocation.put(locationKey(npc.getLocation()), npc.getEntityId());
        }

        for (Map<?, ?> config : configManager.getQuestNPCConfigs()) {
            try {
                String id = (String) config.get("id");
                String name = (String) config.get("name");
                String world = (String) config.get("world");
                if (id == null || name == null || world == null) {
                    continue;
                }
                int x = ((Number) config.get("x")).intValue();
                int y = ((Number) config.get("y")).intValue();
                int z = ((Number) config.get("z")).intValue();
                String key = world + "," + x + "," + y + "," + z;

                UUID npcId = existingNPCsByLocation.get(key);
                if (npcId != null) {
                    Location location = new Location(plugin.getServer().getWorld(world), x + 0.5, y, z + 0.5);
                    questStations.put(id, new QuestStation(id, name, location, npcId));
                } else {
                    plugin.getLogger().warning("クエスト受付所 " + name + " に対応するNPCが見つかりません");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("クエスト受付所データ更新中にエラーが発生しました: " + e.getMessage());
            }
        }

        plugin.getLogger().info("クエストデータの再読み込みが完了しました");
    }

    // ===== インタラクション =====

    /**
     * クエストNPCとのインタラクション処理。GUIを開く。
     */
    public boolean handleQuestNPCInteraction(Player player, UUID npcId) {
        try {
            if (!npcManager.isNPCEntity(npcId)) {
                plugin.getLogger().warning("NPCエンティティが見つかりません: " + npcId);
                return false;
            }

            NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
            if (npcData == null || !"quest".equals(npcData.getNpcType())) {
                plugin.getLogger().warning("クエストNPCデータが見つかりません: " + npcId);
                return false;
            }

            // 挨拶
            player.sendMessage(configManager.getQuestNPCMessage("greeting"));

            // GUIを少し遅延して開く（クリック誤爆防止・既存NPCと同挙動）
            org.tofu.tofunomics.npc.gui.QuestGUI questGUI = plugin.getQuestGUI();
            if (questGUI == null) {
                plugin.getLogger().severe("QuestGUIが初期化されていません");
                player.sendMessage("§cクエストメニューの表示に失敗しました。");
                return false;
            }

            int delayTicks = configManager.getNPCGUIDelayTicks();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    questGUI.openQuestGUI(player);
                }
            }, delayTicks);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("クエストNPC処理中に例外発生: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。");
            return true;
        }
    }

    // ===== GUI向けAPI =====

    /**
     * 全クエスト定義を取得（GUI表示用）
     */
    public Collection<QuestDefinition> getAllQuestDefinitions() {
        return questDefinitions.values();
    }

    /**
     * 同時受注できるクエストの最大数（受注枠の上限）
     */
    public int getMaxConcurrentQuests() {
        return maxConcurrentQuests;
    }

    /**
     * 指定プレイヤーが現在受注中のクエスト数（使用中の受注枠）
     */
    public int getAcceptedCount(Player player) {
        try {
            return questProgressDAO.countAcceptedByPlayer(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("受注中クエスト数の取得に失敗しました: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 指定プレイヤーが指定クエストを受注中かどうか
     */
    public boolean isQuestAccepted(Player player, String questId) {
        try {
            return questProgressDAO.isAccepted(player.getUniqueId(), questId);
        } catch (SQLException e) {
            plugin.getLogger().warning("クエスト受注状態の取得に失敗しました: " + e.getMessage());
            return false;
        }
    }

    /**
     * プレイヤーのインベントリ内（収納欄）の指定アイテム所持数を取得
     */
    public int getHeldAmount(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * クエストを受注する。同時受注上限・重複受注をチェックする。
     */
    public void acceptQuest(Player player, String questId) {
        QuestDefinition def = questDefinitions.get(questId);
        if (def == null) {
            player.sendMessage("§cそのクエストは存在しません。");
            return;
        }

        try {
            if (questProgressDAO.isAccepted(player.getUniqueId(), questId)) {
                player.sendMessage("§e既にこのクエストを受注しています。");
                return;
            }

            int active = questProgressDAO.countAcceptedByPlayer(player.getUniqueId());
            if (active >= maxConcurrentQuests) {
                player.sendMessage("§c同時に受注できるクエストは最大 " + maxConcurrentQuests + " 件までです。");
                return;
            }

            // 過去の完了行などが残っていれば削除してから新規受注（リピート受注を許可）
            questProgressDAO.deleteByPlayerAndQuest(player.getUniqueId(), questId);
            questProgressDAO.insertProgress(new QuestProgress(player.getUniqueId(), questId));

            player.sendMessage(configManager.getQuestNPCMessage("quest_accepted").replace("%quest%", def.getDisplayName()));
        } catch (SQLException e) {
            plugin.getLogger().severe("クエスト受注処理に失敗しました: " + e.getMessage());
            player.sendMessage("§cクエストの受注に失敗しました。");
        }
    }

    /**
     * クエストを納品する。
     * 1) 受注中か確認 → 2) 所持数確認 → 3) 完了化を原子的に確定（二重納品防止）
     * → 4) アイテム消費 → 5) 報酬付与 → 6) 受注行削除（リピート受注を許可）
     */
    public void deliverQuest(Player player, String questId) {
        QuestDefinition def = questDefinitions.get(questId);
        if (def == null) {
            player.sendMessage("§cそのクエストは存在しません。");
            return;
        }

        try {
            if (!questProgressDAO.isAccepted(player.getUniqueId(), questId)) {
                player.sendMessage("§cこのクエストを受注していません。");
                return;
            }

            int held = getHeldAmount(player, def.getTargetMaterial());
            if (held < def.getRequiredAmount()) {
                player.sendMessage(configManager.getQuestNPCMessage("not_enough_items")
                    .replace("%held%", String.valueOf(held))
                    .replace("%required%", String.valueOf(def.getRequiredAmount())));
                return;
            }

            // 原子的に完了化（accepted行のみ対象・影響1件のみ成功）。同時納品による二重報酬を防止
            if (!questProgressDAO.markAsCompleted(player.getUniqueId(), questId, System.currentTimeMillis())) {
                player.sendMessage("§c納品処理に失敗しました（既に処理済みの可能性があります）。");
                return;
            }

            // 納品アイテムを消費
            player.getInventory().removeItem(new ItemStack(def.getTargetMaterial(), def.getRequiredAmount()));

            // 報酬付与（金塊1:1）。インベントリ満杯時は足元にドロップ
            boolean given = currencyConverter.receiveCash(player, def.getRewardNuggets());
            if (!given) {
                player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.GOLD_NUGGET, def.getRewardNuggets()));
                player.sendMessage("§eインベントリに空きがなかったため、報酬を足元にドロップしました。");
            }

            // リピート受注を許可するため受注行を削除
            questProgressDAO.deleteByPlayerAndQuest(player.getUniqueId(), questId);

            player.sendMessage(configManager.getQuestNPCMessage("quest_completed")
                .replace("%quest%", def.getDisplayName())
                .replace("%reward%", String.valueOf(def.getRewardNuggets())));

        } catch (SQLException e) {
            plugin.getLogger().severe("クエスト納品処理に失敗しました: " + e.getMessage());
            player.sendMessage("§cクエストの納品に失敗しました。");
        }
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + (int) loc.getX() + "," + (int) loc.getY() + "," + (int) loc.getZ();
    }
}
