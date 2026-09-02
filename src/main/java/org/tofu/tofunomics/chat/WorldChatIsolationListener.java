package org.tofu.tofunomics.chat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.Set;
import java.util.logging.Logger;

/**
 * ワールド単位のチャット分離
 *
 * <p>1つのサーバーにロビー・ミニゲーム・経済ワールドが同居しているため、
 * 既定では全ワールドの発言が混ざる。経済ワールドの会話にミニゲームの実況が
 * 流れ込むと、どちらの体験も損なわれる。
 *
 * <p>分離対象のワールドは「それぞれが独立した1つの部屋」として扱い、
 * 対象外のワールドはすべて従来どおり1つの部屋を共有する。
 *
 * <p>チャット入力待ちのメッセージ（{@code ChatInputManager} が LOWEST で
 * キャンセルする）は対象外にするため、{@code ignoreCancelled = true} で受ける。
 *
 * <p>コンソールのログには従来どおり全発言が残る。分離するのは配信先だけで、
 * 運営がログを追えなくなることはない。
 */
public class WorldChatIsolationListener implements Listener {

    private final ConfigManager configManager;
    private final Logger logger;

    /** getRecipients() が変更できない実装だった場合に、警告を一度だけ出すためのフラグ */
    private boolean warnedImmutableRecipients = false;

    public WorldChatIsolationListener(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    /**
     * 発言者のワールドから、受信者のワールドへ配信してよいかを判定する。
     *
     * <p>Bukkit に依存しない純粋な判定なので、そのままテストできる。
     *
     * @param senderWorld    発言者のワールド名
     * @param recipientWorld 受信者のワールド名
     * @param isolatedWorlds 分離対象のワールド名
     */
    public static boolean shouldDeliver(String senderWorld, String recipientWorld,
                                        Set<String> isolatedWorlds) {
        if (senderWorld == null || recipientWorld == null) {
            return true;
        }

        boolean senderIsolated = isolatedWorlds.contains(senderWorld);
        boolean recipientIsolated = isolatedWorlds.contains(recipientWorld);

        if (senderIsolated) {
            // 分離ワールドの発言は、同じワールドの相手にだけ届く
            return senderWorld.equals(recipientWorld);
        }

        // 分離対象外どうしは従来どおり共有する。分離ワールドへは流し込まない
        return !recipientIsolated;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!configManager.isChatWorldIsolationEnabled()) {
            return;
        }

        Set<String> isolatedWorlds = configManager.getChatIsolatedWorlds();
        if (isolatedWorlds.isEmpty()) {
            return;
        }

        String senderWorld = event.getPlayer().getWorld().getName();

        try {
            event.getRecipients().removeIf((Player recipient) ->
                !shouldDeliver(senderWorld, recipient.getWorld().getName(), isolatedWorlds));
        } catch (UnsupportedOperationException e) {
            // 他プラグインが受信者リストを変更不可にしている場合がある。
            // その場合は分離できないが、チャット自体は止めない。
            if (!warnedImmutableRecipients) {
                warnedImmutableRecipients = true;
                logger.warning("チャットの受信者リストを変更できないため、ワールド分離を適用できません。"
                    + " 他プラグインがチャットを制御している可能性があります。");
            }
        }
    }
}
