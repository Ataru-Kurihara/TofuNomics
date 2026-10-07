package org.tofu.tofunomics.economy;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * チャット文の中にアイテム名を入れて送るためのヘルパー。
 *
 * アイテム名は内部名（oak_planks / WOODEN_PICKAXE）ではなく、クライアントの翻訳名で表示する。
 * 翻訳はプレイヤーのクライアントが行うので、日本語設定なら「オークの板材」と表示される。
 * サーバー側に日本語名の対応表を持たずに済み、新しいバージョンのアイテムにも自動で追従する。
 */
public final class ItemNameText {

    private ItemNameText() {
    }

    /**
     * template の中の placeholder をアイテムの翻訳名に置き換えて送信する。
     * placeholder が無い場合は template をそのまま送る。
     */
    public static void send(Player player, String template, String placeholder, Material material) {
        String[] parts = splitAtPlaceholder(template, placeholder);
        if (parts == null || material == null) {
            player.sendMessage(template);
            return;
        }

        String before = parts[0];
        // アイテム名の後ろの文は、前の文の色を引き継ぐ
        String after = ChatColor.getLastColors(before) + parts[1];

        TextComponent root = new TextComponent();
        BaseComponent[] head = TextComponent.fromLegacyText(before);
        for (BaseComponent component : head) {
            root.addExtra(component);
        }

        TranslatableComponent name = new TranslatableComponent(material.getTranslationKey());
        if (head.length > 0) {
            name.copyFormatting(head[head.length - 1], ComponentBuilder.FormatRetention.FORMATTING, true);
        }
        root.addExtra(name);

        for (BaseComponent component : TextComponent.fromLegacyText(after)) {
            root.addExtra(component);
        }

        player.spigot().sendMessage(root);
    }

    /**
     * template を最初の placeholder の前後に分ける。
     *
     * @return {前, 後}。placeholder が含まれない場合は null
     */
    public static String[] splitAtPlaceholder(String template, String placeholder) {
        if (template == null || placeholder == null || placeholder.isEmpty()) {
            return null;
        }
        int index = template.indexOf(placeholder);
        if (index < 0) {
            return null;
        }
        return new String[] {
            template.substring(0, index),
            template.substring(index + placeholder.length())
        };
    }
}
