package org.tofu.tofunomics.resourcepack;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.tofu.tofunomics.config.ConfigManager;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 通貨（TofuCoin / TofuGold）の見た目を変えるリソースパックを、tofuNomics ワールドに入ったプレイヤーに送る。
 *
 * - 同じ接続のあいだは 1 回だけ送る（出入りのたびに読み込み画面を出さないため）。断られた場合も送り直さない
 * - ワールドを出てもパックは外さない。変わるのは CustomModelData 付きの金塊・金インゴットだけで、他のゲームの見た目には影響しない
 * - 受け取りは任意（断っても遊べる。通貨がバニラの金塊・金インゴットの見た目のままになるだけ）
 * - 読み込みに失敗したときは、次の入場で送り直す
 */
public class ResourcePackSender implements Listener {

    static final String TARGET_WORLD = "tofuNomics";

    /** このパックの識別子。固定にしておくと、更新したパックを送ったときに古いものと置き換わる */
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("TofuNomics:currency-resourcepack".getBytes(StandardCharsets.UTF_8));

    private final ConfigManager configManager;
    private final Logger logger;
    /** この接続のあいだにパックを送った人 */
    private final Set<UUID> sent = new HashSet<>();

    public ResourcePackSender(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sendIfNeeded(event.getPlayer());
    }

    /** 接続した時点で tofuNomics にいる場合（ワールド移動が起きない） */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendIfNeeded(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        sent.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        if (isFailure(event.getStatus())) {
            // 次の入場で送り直せるようにする
            sent.remove(event.getPlayer().getUniqueId());
            logger.warning("通貨のリソースパックを読み込めませんでした: " + event.getPlayer().getName()
                    + "（" + event.getStatus() + "）");
        }
    }

    private void sendIfNeeded(Player player) {
        String url = configManager.getResourcePackUrl();
        if (!shouldSend(player.getWorld().getName(), configManager.isResourcePackEnabled(), url,
                sent.contains(player.getUniqueId()))) {
            return;
        }
        byte[] hash = parseSha1(configManager.getResourcePackSha1());
        String prompt = ChatColor.translateAlternateColorCodes('&', configManager.getResourcePackPrompt());
        player.setResourcePack(PACK_ID, url, hash, prompt, false);
        sent.add(player.getUniqueId());
    }

    /** パックを送るかどうか */
    static boolean shouldSend(String worldName, boolean enabled, String url, boolean alreadySent) {
        return enabled
                && TARGET_WORLD.equals(worldName)
                && url != null && !url.trim().isEmpty()
                && !alreadySent;
    }

    /**
     * 40 桁の 16 進数の sha1 をバイト列にする。空や形が違うときは null（クライアントは確かめずに読み込む）
     */
    static byte[] parseSha1(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if (!s.matches("[0-9a-fA-F]{40}")) {
            return null;
        }
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static boolean isFailure(PlayerResourcePackStatusEvent.Status status) {
        switch (status) {
            case FAILED_DOWNLOAD:
            case INVALID_URL:
            case FAILED_RELOAD:
            case DISCARDED:
                return true;
            default:
                return false;
        }
    }
}
