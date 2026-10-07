package org.tofu.tofunomics.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.tofu.tofunomics.inventory.PlayerInventoryManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 現金（手持ちの TofuCoin / TofuGold）が動いた人の手持ちを、次の tick にまとめて保存する。
 *
 * 銀行残高は操作のたびに DB へ保存されるが、手持ちは退出・5 分ごと・停止時にしか保存されない。
 * そのあいだにサーバーが異常終了すると、預け入れ直後なら「残高は増えたまま手持ちの現金も戻る」、
 * 引き出し・売却直後なら現金が消える。現金と残高が同時に動く操作のあとに手持ちも保存して、
 * 食い違う時間をほぼ無くす。
 *
 * 1 回の操作の中で現金は何度も動く（支払い→お釣り、全売却など）。そのたびに DB へ書くと重いので、
 * 同じ tick の予約は 1 人 1 回にまとめ、次の tick に保存する。
 */
public class CashSaveScheduler implements Consumer<Player> {

    /**
     * 手持ちを保存・復元する対象のワールド。
     * PlayerInventoryManager / PlayerJoinHandler の保存条件（このワールドにいる人だけ保存する）に合わせる。
     * ほかのワールドの手持ちを保存すると、このワールド用の保存データを上書きしてしまう。
     */
    static final String MANAGED_WORLD = "tofuNomics";

    private final Plugin plugin;
    private final PlayerInventoryManager inventoryManager;
    private final Set<UUID> pending = new LinkedHashSet<>();
    private boolean flushScheduled;

    public CashSaveScheduler(Plugin plugin, PlayerInventoryManager inventoryManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
    }

    @Override
    public void accept(Player player) {
        requestSave(player);
    }

    /**
     * 次の tick に手持ちを保存するよう予約する。同じ tick に何度呼んでも保存は 1 回。
     */
    public void requestSave(Player player) {
        if (player == null || !enqueue(pending, player.getUniqueId())) {
            return;
        }
        if (!flushScheduled) {
            flushScheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::flush);
        }
    }

    /**
     * 予約の一覧に足す。
     *
     * @return 新しく足したら true。すでに予約済みなら false
     */
    static boolean enqueue(Set<UUID> pending, UUID playerId) {
        return playerId != null && pending.add(playerId);
    }

    /** 保存してよいワールドかどうか */
    static boolean isManagedWorld(String worldName) {
        return MANAGED_WORLD.equals(worldName);
    }

    private void flush() {
        flushScheduled = false;
        List<UUID> targets = new ArrayList<>(pending);
        pending.clear();
        for (UUID playerId : targets) {
            try {
                Player player = Bukkit.getPlayer(playerId);
                // 退出した人は退出時の保存に任せる。復元前の人は saveInventory が見送る
                if (player != null && player.isOnline() && isManagedWorld(player.getWorld().getName())) {
                    inventoryManager.saveInventory(player);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("現金操作後の手持ち保存に失敗しました: " + playerId + " " + e.getMessage());
            }
        }
    }
}
