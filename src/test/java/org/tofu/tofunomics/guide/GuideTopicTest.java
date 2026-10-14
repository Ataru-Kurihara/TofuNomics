package org.tofu.tofunomics.guide;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * 遊び方ガイド（config/guide.yml）の読み込みと、本文が本の 1 ページに収まるかの検査。
 */
public class GuideTopicTest {

    // 本の 1 ページに見える行数と、1 行の幅（全角 1 文字 = 2、半角 = 1 として数える）
    private static final int MAX_LINES_PER_PAGE = 14;
    private static final int MAX_LINE_WIDTH = 24;

    private List<GuideTopic> loadBundled() {
        YamlConfiguration guide = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/guide.yml"));
        return GuideTopic.loadAll(guide.getConfigurationSection("guide.topics"), GuideGUI.SIZE);
    }

    @Test
    public void 同梱のガイドは全項目が読めてマスが重ならない() {
        YamlConfiguration guide = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/guide.yml"));
        int defined = guide.getConfigurationSection("guide.topics").getKeys(false).size();
        List<GuideTopic> topics = loadBundled();
        // 読み飛ばされた項目が無いこと（pages 抜け・slot の範囲外を検出する）
        assertEquals(defined, topics.size());
        assertTrue(topics.size() >= 6);

        Set<Integer> slots = new HashSet<>();
        for (GuideTopic topic : topics) {
            assertTrue("マスが重なっている: " + topic.getId(), slots.add(topic.getSlot()));
            // 職業一覧ボタン（22）と閉じる（26）とは重ねない
            assertNotEquals(22, topic.getSlot());
            assertNotEquals(26, topic.getSlot());
            assertNotEquals("アイコン名が誤っている: " + topic.getId(), Material.BOOK, topic.getIcon());
        }
    }

    @Test
    public void 本文が本の1ページに収まる() {
        for (GuideTopic topic : loadBundled()) {
            int pageNo = 0;
            for (String page : topic.getPages()) {
                pageNo++;
                String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', page));
                String[] lines = plain.replaceAll("\n+$", "").split("\n", -1);
                String where = topic.getId() + " の " + pageNo + " ページ目";
                assertTrue(where + " の行数が多すぎる: " + lines.length, lines.length <= MAX_LINES_PER_PAGE);
                for (String line : lines) {
                    assertTrue(where + " の行が長すぎる: " + line, width(line) <= MAX_LINE_WIDTH);
                }
            }
        }
    }

    @Test
    public void pagesが無い項目と範囲外のマスは読み飛ばす() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("topics.ok.slot", 10);
        yaml.set("topics.ok.icon", "EMERALD");
        yaml.set("topics.ok.pages", java.util.Collections.singletonList("本文"));
        yaml.set("topics.no_pages.slot", 11);
        yaml.set("topics.out.slot", 30);
        yaml.set("topics.out.pages", java.util.Collections.singletonList("本文"));
        yaml.set("topics.bad_icon.slot", 12);
        yaml.set("topics.bad_icon.icon", "NOT_A_MATERIAL");
        yaml.set("topics.bad_icon.pages", java.util.Collections.singletonList("本文"));

        List<GuideTopic> topics = GuideTopic.loadAll(yaml.getConfigurationSection("topics"), 27);
        assertEquals(2, topics.size());
        assertEquals("ok", topics.get(0).getId());
        assertEquals(Material.EMERALD, topics.get(0).getIcon());
        // 名前が無ければ id、アイコンが誤っていれば本
        assertEquals("bad_icon", topics.get(1).getName());
        assertEquals(Material.BOOK, topics.get(1).getIcon());
        assertTrue(GuideTopic.loadAll(null, 27).isEmpty());
    }

    private static int width(String line) {
        int w = 0;
        for (char c : line.toCharArray()) {
            w += c < 0x80 ? 1 : 2;
        }
        return w;
    }
}
