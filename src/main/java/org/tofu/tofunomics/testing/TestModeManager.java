package org.tofu.tofunomics.testing;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * テストモードを管理するクラス
 * OP権限を持つ管理者が、権限のないユーザーの挙動をテストするための機能を提供
 */
public class TestModeManager {
    private final Plugin plugin;
    private final Logger logger;
    private final Set<UUID> testModePlayers;

    public TestModeManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.testModePlayers = new HashSet<>();
    }

    /**
     * プレイヤーのテストモードを有効化
     * 
     * @param player テストモードを有効化するプレイヤー
     */
    public void enableTestMode(Player player) {
        testModePlayers.add(player.getUniqueId());
        logger.info(player.getName() + " がテストモードを有効化しました");
    }

    /**
     * プレイヤーのテストモードを無効化
     * 
     * @param player テストモードを無効化するプレイヤー
     */
    public void disableTestMode(Player player) {
        testModePlayers.remove(player.getUniqueId());
        logger.info(player.getName() + " がテストモードを無効化しました");
    }

    /**
     * プレイヤーがテストモード中かをチェック
     * 
     * @param player チェックするプレイヤー
     * @return テストモード中の場合true
     */
    public boolean isInTestMode(Player player) {
        return testModePlayers.contains(player.getUniqueId());
    }

    /**
     * プレイヤーがテストモード中かをUUIDでチェック
     * 
     * @param playerUuid チェックするプレイヤーのUUID
     * @return テストモード中の場合true
     */
    public boolean isInTestMode(UUID playerUuid) {
        return testModePlayers.contains(playerUuid);
    }

    /**
     * テストモードを考慮した権限チェック
     * テストモード中のプレイヤーはすべての権限が無効化される
     * 
     * @param player チェックするプレイヤー
     * @param permission チェックする権限
     * @return 権限を持っている場合true（テストモード中は常にfalse）
     */
    public boolean hasEffectivePermission(Player player, String permission) {
        // テストモード中は全ての権限を無効化
        if (isInTestMode(player)) {
            return false;
        }
        
        // 通常時は本来の権限チェック
        return player.hasPermission(permission);
    }

    /**
     * 現在テストモード中のプレイヤー数を取得
     * 
     * @return テストモード中のプレイヤー数
     */
    public int getTestModePlayerCount() {
        return testModePlayers.size();
    }

    /**
     * 全てのテストモードをクリア（プラグイン無効化時に使用）
     */
    public void clearAll() {
        testModePlayers.clear();
        logger.info("全てのテストモードをクリアしました");
    }
}
