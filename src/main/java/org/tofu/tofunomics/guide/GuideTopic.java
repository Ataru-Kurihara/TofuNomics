package org.tofu.tofunomics.guide;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 遊び方ガイド（/guide）の 1 項目。config/guide.yml の guide.topics.<id> を読む。
 *
 * サーバーが無くても読めるよう、ConfigurationSection からの読み込みだけを持つ。
 */
public final class GuideTopic {

    private final String id;
    private final String name;
    private final Material icon;
    private final int slot;
    private final List<String> description;
    private final List<String> pages;

    GuideTopic(String id, String name, Material icon, int slot, List<String> description, List<String> pages) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.slot = slot;
        this.description = description;
        this.pages = pages;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getIcon() { return icon; }
    public int getSlot() { return slot; }
    public List<String> getDescription() { return description; }
    public List<String> getPages() { return pages; }

    /**
     * guide.topics セクションから項目を設定の順に読む。
     * 本文（pages）が無い項目と、メニューの枠（0〜menuSize-1）から外れる項目は読み飛ばす。
     */
    public static List<GuideTopic> loadAll(ConfigurationSection topics, int menuSize) {
        if (topics == null) {
            return Collections.emptyList();
        }
        List<GuideTopic> result = new ArrayList<>();
        for (String id : topics.getKeys(false)) {
            ConfigurationSection s = topics.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            List<String> pages = s.getStringList("pages");
            int slot = s.getInt("slot", -1);
            if (pages.isEmpty() || slot < 0 || slot >= menuSize) {
                continue;
            }
            Material icon = parseIcon(s.getString("icon", "BOOK"));
            result.add(new GuideTopic(id, s.getString("name", id), icon, slot,
                    s.getStringList("description"), pages));
        }
        return result;
    }

    /**
     * アイコン名を Material にする。誤った名前なら本にする。
     * Material.matchMaterial はサーバーの登録情報を使うため、テストでも動く名前引きにしている。
     */
    static Material parseIcon(String name) {
        if (name == null) {
            return Material.BOOK;
        }
        try {
            Material icon = Material.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            return icon == Material.AIR ? Material.BOOK : icon;
        } catch (IllegalArgumentException e) {
            return Material.BOOK;
        }
    }
}
