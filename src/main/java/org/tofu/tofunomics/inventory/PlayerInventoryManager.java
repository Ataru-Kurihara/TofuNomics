package org.tofu.tofunomics.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * プレイヤーのインベントリを管理するクラス
 * データベースへの保存・復元を行う
 */
public class PlayerInventoryManager {

    private final JavaPlugin plugin;
    private final Connection connection;
    private final Logger logger;
    private BukkitTask autoSaveTask;

    public PlayerInventoryManager(JavaPlugin plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
        this.logger = plugin.getLogger();

        // 自動保存タスクを開始（5分ごと）
        startAutoSaveTask();
    }

    /**
     * プレイヤーのインベントリを保存
     */
    public boolean saveInventory(Player player) {
        try {
            UUID playerUuid = player.getUniqueId();

            // インベントリの内容を取得
            ItemStack[] inventory = player.getInventory().getContents();
            ItemStack[] armor = player.getInventory().getArmorContents();
            ItemStack offhand = player.getInventory().getItemInOffHand();

            // Base64エンコード
            String inventoryData = itemStackArrayToBase64(inventory);
            String armorData = itemStackArrayToBase64(armor);
            String offhandData = itemStackToBase64(offhand);

            // データベースに保存（REPLACE INTOで既存データを上書き）
            String sql = "REPLACE INTO player_inventories (player_uuid, inventory_data, armor_data, offhand_data, last_saved) VALUES (?, ?, ?, ?, datetime('now'))";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, inventoryData);
                stmt.setString(3, armorData);
                stmt.setString(4, offhandData);
                stmt.executeUpdate();
            }

            logger.info("プレイヤー " + player.getName() + " のインベントリを保存しました");
            return true;

        } catch (Exception e) {
            logger.severe("インベントリ保存中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * プレイヤーのインベントリを復元
     */
    public boolean loadInventory(Player player) {
        try {
            UUID playerUuid = player.getUniqueId();

            // データベースから取得
            String sql = "SELECT inventory_data, armor_data, offhand_data FROM player_inventories WHERE player_uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String inventoryData = rs.getString("inventory_data");
                    String armorData = rs.getString("armor_data");
                    String offhandData = rs.getString("offhand_data");

                    // Base64デコードしてインベントリに設定
                    if (inventoryData != null && !inventoryData.isEmpty()) {
                        ItemStack[] inventory = itemStackArrayFromBase64(inventoryData);
                        player.getInventory().setContents(inventory);
                    }

                    if (armorData != null && !armorData.isEmpty()) {
                        ItemStack[] armor = itemStackArrayFromBase64(armorData);
                        player.getInventory().setArmorContents(armor);
                    }

                    if (offhandData != null && !offhandData.isEmpty()) {
                        ItemStack offhand = itemStackFromBase64(offhandData);
                        if (offhand != null) {
                            player.getInventory().setItemInOffHand(offhand);
                        }
                    }

                    logger.info("プレイヤー " + player.getName() + " のインベントリを復元しました");
                    return true;
                } else {
                    logger.info("プレイヤー " + player.getName() + " の保存されたインベントリが見つかりません（初回参加の可能性）");
                    return false;
                }
            }

        } catch (Exception e) {
            logger.severe("インベントリ復元中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * インベントリをクリア（削除）
     */
    public boolean clearInventory(UUID playerUuid) {
        try {
            String sql = "DELETE FROM player_inventories WHERE player_uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.executeUpdate();
            }

            logger.info("プレイヤー " + playerUuid + " のインベントリデータを削除しました");
            return true;

        } catch (Exception e) {
            logger.severe("インベントリデータ削除中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 自動保存タスクを開始
     */
    private void startAutoSaveTask() {
        // 5分ごとにオンラインのプレイヤーのインベントリを保存
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                logger.info("インベントリ自動保存を実行中...");
                int savedCount = 0;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().getName().equals("tofuNomics")) {
                        // 同期タスクで保存（メインスレッドで実行）
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            saveInventory(player);
                        });
                        savedCount++;
                    }
                }

                if (savedCount > 0) {
                    logger.info("インベントリ自動保存完了: " + savedCount + "人のプレイヤー");
                }

            } catch (Exception e) {
                logger.warning("インベントリ自動保存中にエラーが発生しました: " + e.getMessage());
            }
        }, 6000L, 6000L); // 5分後に開始、5分ごとに実行（6000 ticks = 5分）

        logger.info("インベントリ自動保存タスクを開始しました（5分ごと）");
    }

    /**
     * 自動保存タスクを停止
     */
    public void stopAutoSaveTask() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
            logger.info("インベントリ自動保存タスクを停止しました");
        }
    }

    /**
     * 全オンラインプレイヤーのインベントリを保存
     */
    public void saveAllOnlineInventories() {
        logger.info("全オンラインプレイヤーのインベントリ保存を開始...");
        int savedCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals("tofuNomics")) {
                if (saveInventory(player)) {
                    savedCount++;
                }
            }
        }

        logger.info("全オンラインプレイヤーのインベントリ保存完了: " + savedCount + "人");
    }

    /**
     * ItemStack配列をBase64文字列に変換
     */
    private String itemStackArrayToBase64(ItemStack[] items) throws Exception {
        if (items == null || items.length == 0) {
            return "";
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        // 配列のサイズを書き込み
        dataOutput.writeInt(items.length);

        // 各アイテムを書き込み
        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64文字列からItemStack配列に変換
     */
    private ItemStack[] itemStackArrayFromBase64(String data) throws Exception {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        // 配列のサイズを読み込み
        int size = dataInput.readInt();
        ItemStack[] items = new ItemStack[size];

        // 各アイテムを読み込み
        for (int i = 0; i < size; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }

        dataInput.close();
        return items;
    }

    /**
     * ItemStackをBase64文字列に変換
     */
    private String itemStackToBase64(ItemStack item) throws Exception {
        if (item == null) {
            return "";
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeObject(item);
        dataOutput.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64文字列からItemStackに変換
     */
    private ItemStack itemStackFromBase64(String data) throws Exception {
        if (data == null || data.isEmpty()) {
            return null;
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();

        return item;
    }
}
