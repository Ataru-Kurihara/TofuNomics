package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * プレイヤーが打った職業名を、内部名（miner など）に直すクラス。
 * 案内やGUIでは日本語の表示名（鉱夫 など）を見せているので、どちらで打っても通るようにする。
 */
public final class JobNameResolver {

    private JobNameResolver() {
    }

    /**
     * 入力を内部名に直す。
     *
     * @param input プレイヤーが打った職業名（内部名または表示名）
     * @param displayNamesByInternalName 内部名 → 表示名（色コード付きでもよい）
     * @return 内部名。該当が無ければ null
     */
    public static String resolve(String input, Map<String, String> displayNamesByInternalName) {
        if (input == null || displayNamesByInternalName == null) {
            return null;
        }
        String typed = input.trim();
        if (typed.isEmpty()) {
            return null;
        }

        // 内部名を先に見る（大文字小文字は区別しない）
        for (String internalName : displayNamesByInternalName.keySet()) {
            if (internalName.equalsIgnoreCase(typed)) {
                return internalName;
            }
        }
        for (Map.Entry<String, String> entry : displayNamesByInternalName.entrySet()) {
            if (plain(entry.getValue()).equalsIgnoreCase(typed)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Tab 補完の候補を返す。表示名（日本語）と内部名の両方を、入力の前方一致で絞り込む。
     */
    public static List<String> suggest(String typed, Map<String, String> displayNamesByInternalName) {
        List<String> candidates = new ArrayList<>();
        if (displayNamesByInternalName == null) {
            return candidates;
        }
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : displayNamesByInternalName.entrySet()) {
            String displayName = plain(entry.getValue());
            if (!displayName.isEmpty() && displayName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                candidates.add(displayName);
            }
        }
        for (String internalName : displayNamesByInternalName.keySet()) {
            if (internalName.toLowerCase(Locale.ROOT).startsWith(prefix) && !candidates.contains(internalName)) {
                candidates.add(internalName);
            }
        }
        return candidates;
    }

    /** 色コード（& と § の両方）を外した表示名 */
    static String plain(String displayName) {
        if (displayName == null) {
            return "";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName)).trim();
    }
}
