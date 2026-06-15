package org.tofu.tofunomics.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * GUI からチャット入力を受け付けるための共通プロンプト基盤。
 *
 * GUI を閉じてプレイヤーにチャット入力を促し、次のチャットメッセージをコールバックで受け取る。
 * 「cancel」入力・タイムアウト・ログアウトで保留中のプロンプトは破棄される。
 * 複数値の入力は {@code onInput} 内で次の {@link #prompt} を呼ぶことでステップ式にチェーンできる。
 *
 * market / housing など複数機能の GUI から共有する（インスタンスは1つでよい）。
 *
 * 注意: {@link AsyncPlayerChatEvent} は非同期スレッドで発火するため、
 * コールバックは必ず {@link org.bukkit.scheduler.BukkitScheduler#runTask} でメインスレッドへ同期してから実行する。
 */
public class ChatInputManager implements Listener {

    /** 保留中のプロンプト1件 */
    private static class PendingPrompt {
        final Consumer<String> onInput;
        final Runnable onCancel;
        final BukkitTask timeoutTask;

        PendingPrompt(Consumer<String> onInput, Runnable onCancel, BukkitTask timeoutTask) {
            this.onInput = onInput;
            this.onCancel = onCancel;
            this.timeoutTask = timeoutTask;
        }
    }

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();

    public ChatInputManager(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * チャット入力プロンプトを開始する。GUI は呼び出し側で閉じておくこと。
     *
     * @param player         対象プレイヤー
     * @param promptMessage  入力を促すメッセージ（色コード適用済み）
     * @param onInput        入力受領時のコールバック（メインスレッドで実行）
     * @param onCancel       中止/タイムアウト時のコールバック（メインスレッドで実行、null 可）
     * @param timeoutSeconds タイムアウト秒数（0 以下で無効）
     */
    public void prompt(Player player, String promptMessage,
                       Consumer<String> onInput, Runnable onCancel, int timeoutSeconds) {
        cancel(player); // 既存プロンプトがあれば破棄
        player.sendMessage(promptMessage);
        player.sendMessage("§7（中止する場合は §fcancel §7と入力）");

        BukkitTask timeoutTask = null;
        if (timeoutSeconds > 0) {
            final UUID id = player.getUniqueId();
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PendingPrompt p = pending.remove(id);
                if (p != null) {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null && online.isOnline()) {
                        online.sendMessage(configManager.getMarketMessage("input_timeout"));
                    }
                    if (p.onCancel != null) {
                        p.onCancel.run();
                    }
                }
            }, timeoutSeconds * 20L);
        }

        pending.put(player.getUniqueId(), new PendingPrompt(onInput, onCancel, timeoutTask));
    }

    /**
     * 保留中のプロンプトを破棄する（コールバックは呼ばない）。
     */
    public void cancel(Player player) {
        PendingPrompt p = pending.remove(player.getUniqueId());
        if (p != null && p.timeoutTask != null) {
            p.timeoutTask.cancel();
        }
    }

    public boolean hasPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        PendingPrompt p = pending.remove(id);
        if (p == null) {
            return;
        }
        // 通常チャットに流さない
        event.setCancelled(true);
        if (p.timeoutTask != null) {
            p.timeoutTask.cancel();
        }

        final String message = event.getMessage().trim();
        final Player player = event.getPlayer();
        // 非同期スレッドなので必ずメインスレッドへ同期してからコールバック実行
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(configManager.getMarketMessage("input_cancelled"));
                if (p.onCancel != null) {
                    p.onCancel.run();
                }
            } else {
                p.onInput.accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }
}
