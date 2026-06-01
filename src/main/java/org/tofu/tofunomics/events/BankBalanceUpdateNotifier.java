package org.tofu.tofunomics.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.api.client.TohuAppApiClient;
import org.tofu.tofunomics.api.model.PlayerStatsData;

import java.time.LocalDate;

/**
 * 銀行入出金時にtohu-app APIへ変化量を通知するクラス
 * イベント駆動型でAPI送信を行う（差分送信方式）
 */
public class BankBalanceUpdateNotifier {
    private final TofuNomics plugin;
    private final TohuAppApiClient apiClient;
    private final int worldId;
    private final boolean debugMode;

    public BankBalanceUpdateNotifier(TofuNomics plugin) {
        this.plugin = plugin;
        this.apiClient = new TohuAppApiClient(plugin);
        this.worldId = plugin.getConfig().getInt("api.tohu_app.world_id", 1);
        this.debugMode = plugin.getConfig().getBoolean("api.tohu_app.debug", false);
    }

    /**
     * 預金残高の変化量をtohu-app APIに通知（差分送信方式）
     *
     * @param player 入出金したプレイヤー
     * @param changeAmount 変化量（入金時は正、出金時は負）
     */
    public void notifyBankBalanceChange(Player player, double changeAmount) {
        // 機能が無効の場合はスキップ
        if (!plugin.getConfig().getBoolean("api.tohu_app.enabled", false)) {
            return;
        }

        // 変化量が0の場合は送信しない
        if (changeAmount == 0) {
            return;
        }

        // 今日の日付（YYYY-MM-DD形式）
        String today = LocalDate.now().toString();

        // 統計データを作成（変化量を送信）
        PlayerStatsData data = new PlayerStatsData(
                player.getUniqueId().toString(),
                player.getName(),
                worldId,
                "bank_balance",
                today,
                (long) changeAmount
        );

        // デバッグモード時はデータをログ出力
        if (debugMode) {
            String operation = changeAmount > 0 ? "入金" : "出金";
            plugin.getLogger().info(String.format(
                    "[tohu-app API] 預金残高%s送信: %s = %+d",
                    operation, player.getName(), (long) changeAmount
            ));
        }

        // 非同期でAPI送信（入出金処理をブロックしない）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = apiClient.submitStats(data);
                if (success && debugMode) {
                    plugin.getLogger().info(String.format(
                            "[tohu-app API] 預金残高送信成功: %s = %+d",
                            player.getName(), (long) changeAmount
                    ));
                } else if (!success) {
                    plugin.getLogger().warning(String.format(
                            "[tohu-app API] 預金残高送信失敗: %s",
                            player.getName()
                    ));
                }
            } catch (Exception e) {
                plugin.getLogger().warning(String.format(
                        "[tohu-app API] 預金残高送信中に例外が発生しました: %s - %s",
                        player.getName(), e.getMessage()
                ));
            }
        });
    }

    /**
     * APIクライアントのクリーンアップ
     */
    public void shutdown() {
        if (apiClient != null) {
            apiClient.shutdown();
        }
    }
}
