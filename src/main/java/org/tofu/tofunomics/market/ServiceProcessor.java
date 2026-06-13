package org.tofu.tofunomics.market;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/**
 * 修理・エンチャント依頼の「仮想加工」を担うクラス（システム自動代行）。
 *
 * アイテムは常にシステム保管され、worker は実際の金床操作を行わない。
 * 本クラスが預かりアイテム（ItemStack）に対して修理（耐久全回復）または
 * エンチャント付与を施し、加工後の ItemStack を返す。元のエンチャント・
 * カスタム名・NBT は ItemMeta を加工するだけなので保持される。
 *
 * シリアライズ/デシリアライズは呼び出し側（MarketManager）が担当する。
 */
public class ServiceProcessor {

    /**
     * エンチャント名（minecraft key の小文字、例 "sharpness"）から Enchantment を解決する。
     * 1.21 では Enchantment.getByName/getByKey が非推奨のため Registry 経由で解決する。
     *
     * @return 解決できなければ null
     */
    public static Enchantment resolveEnchantment(String enchantKey) {
        if (enchantKey == null || enchantKey.isEmpty()) {
            return null;
        }
        String normalized = enchantKey.toLowerCase(Locale.ROOT).trim();
        // "minecraft:sharpness" のように名前空間付きで来た場合も許容
        if (normalized.contains(":")) {
            normalized = normalized.substring(normalized.indexOf(':') + 1);
        }
        try {
            NamespacedKey key = NamespacedKey.minecraft(normalized);
            return Registry.ENCHANTMENT.get(key);
        } catch (IllegalArgumentException e) {
            // 不正な key 文字（大文字・記号等）
            return null;
        }
    }

    /**
     * 指定エンチャントが許可リストに含まれるか。
     * allowed が空（または null）の場合は全エンチャント許可とみなす。
     */
    public static boolean isEnchantAllowed(String enchantKey, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (enchantKey == null) {
            return false;
        }
        String normalized = enchantKey.toLowerCase(Locale.ROOT).trim();
        if (normalized.contains(":")) {
            normalized = normalized.substring(normalized.indexOf(':') + 1);
        }
        for (String a : allowed) {
            if (a != null && a.toLowerCase(Locale.ROOT).trim().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 修理対象にできるアイテムか（耐久度を持つ＝Damageable かつ最大耐久 > 0）。
     */
    public static boolean isRepairable(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable;
    }

    /**
     * 修理を適用する（耐久を全回復＝damage を 0 に）。元のメタ情報は保持される。
     *
     * @return 加工後の ItemStack。修理不可なら null
     */
    public static ItemStack applyRepair(ItemStack item) {
        if (!isRepairable(item)) {
            return null;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return null;
        }
        Damageable damageable = (Damageable) meta;
        damageable.setDamage(0);
        result.setItemMeta(meta);
        return result;
    }

    /**
     * エンチャントを付与する（addEnchant(..., true) でレベル・互換制限を無視）。
     * 元のエンチャント・カスタム名・NBT は保持される。
     *
     * @return 加工後の ItemStack。メタ取得不可・エンチャント null なら null
     */
    public static ItemStack applyEnchant(ItemStack item, Enchantment enchantment, int level) {
        if (item == null || enchantment == null || level <= 0) {
            return null;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.addEnchant(enchantment, level, true);
        result.setItemMeta(meta);
        return result;
    }
}
