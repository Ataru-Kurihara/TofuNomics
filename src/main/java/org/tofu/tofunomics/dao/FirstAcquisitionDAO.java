package org.tofu.tofunomics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 各職業で「初めて入手したアイテム」を記録・判定するDAO。
 *
 * <p>採掘・伐採などの高頻度イベントから呼ばれるため、UUIDごとの取得済みキー集合を
 * メモリにキャッシュし、DBアクセスはプレイヤー単位の初回ロードと記録時のみに限定する。</p>
 *
 * <p>判定キー（itemKey）は職業ごとに意味が異なる：
 * 大半の職業は Material 名（例: COAL_ORE）、釣り人のみ固定キー "ANY_FISH"（最初の1匹のみ）。
 * キャッシュ内部では {@code jobName + ":" + itemKey} の形で保持する。</p>
 */
public class FirstAcquisitionDAO {
    private static final Logger logger = Logger.getLogger(FirstAcquisitionDAO.class.getName());

    private final Connection connection;

    // UUID -> 取得済み（jobName:itemKey）集合。null値は「DB未ロード」を表す。
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public FirstAcquisitionDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 指定プレイヤー・職業・アイテムキーの組が「初めての入手」かどうかを返す。
     * 記録は行わない（記録は {@link #recordAcquisition} を別途呼ぶ）。
     *
     * @return まだ記録が無ければ true（初回）、既に記録済みなら false
     */
    public boolean isFirstAcquisition(UUID uuid, String jobName, String itemKey) {
        Set<String> acquired = loadIfAbsent(uuid);
        return !acquired.contains(cacheKey(jobName, itemKey));
    }

    /**
     * 指定プレイヤー・職業・アイテムキーの組を「入手済み」として記録する。
     * 既に記録済みの場合は INSERT OR IGNORE により無視される（重複登録しない）。
     */
    public void recordAcquisition(UUID uuid, String jobName, String itemKey) {
        Set<String> acquired = loadIfAbsent(uuid);
        // 既にキャッシュ上で記録済みなら何もしない（DBアクセス削減）
        if (!acquired.add(cacheKey(jobName, itemKey))) {
            return;
        }

        String query = "INSERT OR IGNORE INTO player_first_acquisitions (uuid, job_name, item_key) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, jobName);
            statement.setString(3, itemKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "初回入手記録の保存に失敗しました: " + uuid + " / " + jobName + " / " + itemKey, e);
        }
    }

    /**
     * 指定プレイヤーのキャッシュを解放する（ログアウト時のメモリ最適化用）。
     */
    public void unloadCache(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * 指定プレイヤーのキャッシュが未ロードなら、DBから取得済みキーを一括ロードする。
     */
    private Set<String> loadIfAbsent(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDatabase);
    }

    private Set<String> loadFromDatabase(UUID uuid) {
        Set<String> acquired = new HashSet<>();
        String query = "SELECT job_name, item_key FROM player_first_acquisitions WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    acquired.add(cacheKey(resultSet.getString("job_name"), resultSet.getString("item_key")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "初回入手記録の読み込みに失敗しました: " + uuid, e);
        }
        return acquired;
    }

    private String cacheKey(String jobName, String itemKey) {
        return jobName + ":" + itemKey;
    }
}
