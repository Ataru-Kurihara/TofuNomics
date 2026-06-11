package org.tofu.tofunomics.market;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * 単一の ItemStack を Base64 文字列に相互変換するユーティリティ。
 *
 * {@code PlayerInventoryManager} の単一アイテム変換と同一方式
 * （{@code BukkitObjectOutputStream}/{@code BukkitObjectInputStream} + Base64）。
 * YAML ではなくバイナリシリアライズを用いることで、エンチャント・カスタム名・
 * NBT を含む完全な ItemStack を保持できる。
 */
public final class MarketItemSerializer {

    private MarketItemSerializer() {
    }

    /**
     * ItemStack を Base64 文字列に変換する。
     *
     * @param item 変換対象（null 不可）
     * @return Base64 文字列。失敗時は null
     */
    public static String serialize(ItemStack item) {
        if (item == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Base64 文字列を ItemStack に変換する。
     *
     * @param data Base64 文字列
     * @return 復元した ItemStack。null/空文字列/不正データの場合は null
     */
    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }
}
