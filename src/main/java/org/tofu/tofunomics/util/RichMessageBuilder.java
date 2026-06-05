package org.tofu.tofunomics.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * クリック実行・ホバー説明付きのリッチテキストメッセージを組み立てるヘルパー。
 *
 * 既存の {@code commands/RulesCommand.java} と同じ BungeeCord Chat API
 * （{@code net.md_5.bungee.api.chat.*}）を使用する。
 * カラーコードは Minecraft 標準の {@code &} 記法で指定する。
 */
public class RichMessageBuilder {

    private final TextComponent root = new TextComponent("");

    public static RichMessageBuilder create() {
        return new RichMessageBuilder();
    }

    private static String color(String legacy) {
        return ChatColor.translateAlternateColorCodes('&', legacy);
    }

    /**
     * 通常テキストを追加する（{@code &} カラーコード対応）。
     */
    public RichMessageBuilder text(String legacy) {
        for (BaseComponent component : TextComponent.fromLegacyText(color(legacy))) {
            root.addExtra(component);
        }
        return this;
    }

    /**
     * クリックでコマンドを実行するボタンを追加する。
     *
     * @param label   表示テキスト（{@code &} カラーコード対応）
     * @param command 実行するコマンド（先頭の {@code /} を含む）
     * @param hover   ホバー時のツールチップ（null可、{@code &} カラーコード対応）
     */
    public RichMessageBuilder runButton(String label, String command, String hover) {
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(color(label)));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        if (hover != null) {
            button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(color(hover)))));
        }
        root.addExtra(button);
        return this;
    }

    /**
     * ホバー時に詳細を表示するテキストを追加する（クリック動作なし）。
     *
     * @param label 表示テキスト（{@code &} カラーコード対応）
     * @param hover ホバー時のツールチップ（{@code &} カラーコード対応）
     */
    public RichMessageBuilder hoverText(String label, String hover) {
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(color(label)));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new Text(TextComponent.fromLegacyText(color(hover)))));
        root.addExtra(component);
        return this;
    }

    /**
     * 組み立てたコンポーネントを返す。
     */
    public TextComponent build() {
        return root;
    }

    /**
     * 組み立てたメッセージをプレイヤーへ送信する。
     */
    public void sendTo(Player player) {
        player.spigot().sendMessage(root);
    }
}
