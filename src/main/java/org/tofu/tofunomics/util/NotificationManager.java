package org.tofu.tofunomics.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Title / ActionBar / パーティクル演出をまとめた通知ヘルパー。
 *
 * 既存実装と同じ Bukkit 標準 API + BungeeCord Chat API を使用する:
 * - ActionBar: {@code items/ClockItemManager.java}
 * - Title: {@code area/AreaListener.java}
 * - パーティクル: {@code housing/SelectionManager.java}
 *
 * 演出のON/OFF判定は呼び出し側で {@code ConfigManager} を見て行う。
 * このクラスは純粋な送信ヘルパーに徹する。
 */
public class NotificationManager {

    private static String color(String legacy) {
        return ChatColor.translateAlternateColorCodes('&', legacy);
    }

    /**
     * ActionBar（画面下部のホットバー上）にメッセージを表示する。
     */
    public void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(color(message)));
    }

    /**
     * 画面中央に Title / Subtitle を表示する（時間はtick単位）。
     */
    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    /**
     * レベルアップ等の祝福パーティクルをプレイヤー周囲に表示する。
     */
    public void playCelebrationEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 40, 0.5, 1.0, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.4, 0.8, 0.4, 0.3);
    }
}
