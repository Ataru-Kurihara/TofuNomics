package org.tofu.tofunomics.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 記入済みの本（WRITTEN_BOOK）を作るヘルパー。色コード（&）は変換する。
 *
 * 職業ガイドブックと遊び方ガイド（/guide）で共有する。
 * 作った本は配ってもよいし、Player#openBook で配らずに画面で開いてもよい。
 */
public final class BookUtil {

    private BookUtil() {
    }

    public static ItemStack createBook(String title, String author, List<String> pageContents) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(ChatColor.translateAlternateColorCodes('&', title));
            meta.setAuthor(ChatColor.translateAlternateColorCodes('&', author));
            List<String> pages = new ArrayList<>();
            for (String page : pageContents) {
                pages.add(ChatColor.translateAlternateColorCodes('&', page));
            }
            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        return book;
    }
}
