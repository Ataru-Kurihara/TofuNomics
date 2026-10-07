package org.tofu.tofunomics.events;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * プレイヤーが置いたブロックの場所を覚えておくクラス。
 * 自分で置いた石や原木を掘って経験値を得る、という抜け道をふさぐために使う。
 *
 * 以前はメモリの中だけに持っていたので、サーバーを再起動すると忘れてしまい、
 * 再起動の前に置いたブロックを掘ると経験値が入っていた。
 * ここでは placed_blocks 表に残す。置く・壊すのたびに DB へ書くと重いので、
 * メモリを書き換えるだけにして、たまった分を {@link #flush()} でまとめて書く
 * （数秒ごとと、プラグインの停止時に呼ぶ）。
 *
 * メモリ上の記録はメインスレッドからだけ使う。
 * DB の接続はほかの処理（別スレッドで動くものを含む）と共有しているので、DB に触る所は
 * マーケットなどと同じく {@code synchronized (connection)} で囲む。囲まないと、まとめ書きの
 * 取引の途中に別の処理の書き込みが割り込み、取り消しのときに一緒に消える恐れがある。
 */
public class PlacedBlockStore {

    /** 覚えておく件数の上限。起動時にこれを超えていたら、古い順に消す */
    public static final int DEFAULT_MAX_ROWS = 500_000;

    private final Connection connection;
    private final Logger logger;
    private final int maxRows;

    /** ワールド名 → 置かれた場所（座標を 1 つの数にまとめたもの） */
    private final Map<String, Set<Long>> placed = new HashMap<>();
    /** まだ DB に書いていない変更。値が null でなければ「追加（置いた時刻）」、null なら「削除」 */
    private final Map<BlockKey, Long> pending = new LinkedHashMap<>();

    /**
     * @param connection DB の接続。null のときはメモリの中だけで動く（再起動で忘れる）
     */
    public PlacedBlockStore(Connection connection, Logger logger) {
        this(connection, logger, DEFAULT_MAX_ROWS);
    }

    PlacedBlockStore(Connection connection, Logger logger, int maxRows) {
        this.connection = connection;
        this.logger = logger;
        this.maxRows = maxRows;
        if (connection != null) {
            try {
                synchronized (connection) {
                    createTable();
                    trimToLimit();
                    load();
                }
            } catch (Exception e) {
                // 読み込みに失敗しても、プラグインの起動は止めない。途中まで読めた分は捨てて空で始める
                placed.clear();
                logger.severe("設置ブロックの記録を読み込めませんでした（空の状態で始めます）: " + e.getMessage());
            }
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS placed_blocks ("
                    + "world TEXT NOT NULL, "
                    + "x INTEGER NOT NULL, "
                    + "y INTEGER NOT NULL, "
                    + "z INTEGER NOT NULL, "
                    + "placed_at INTEGER NOT NULL, "
                    + "PRIMARY KEY (world, x, y, z))");
        }
    }

    /** 上限を超えた分を、古い順に消す */
    private void trimToLimit() throws SQLException {
        int count;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM placed_blocks")) {
            count = rs.next() ? rs.getInt(1) : 0;
        }
        int excess = excessRows(count, maxRows);
        if (excess <= 0) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM placed_blocks WHERE rowid IN "
                        + "(SELECT rowid FROM placed_blocks ORDER BY placed_at ASC LIMIT ?)")) {
            stmt.setInt(1, excess);
            stmt.executeUpdate();
        }
        logger.info("設置ブロックの記録が上限(" + maxRows + "件)を超えていたため、古い " + excess + " 件を消しました");
    }

    /** 上限を超えている件数（超えていなければ 0） */
    static int excessRows(int count, int maxRows) {
        return Math.max(0, count - maxRows);
    }

    private void load() throws SQLException {
        int count = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT world, x, y, z FROM placed_blocks")) {
            while (rs.next()) {
                placed.computeIfAbsent(rs.getString(1), w -> new HashSet<>())
                        .add(pack(rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                count++;
            }
        }
        logger.info("設置ブロックの記録を読み込みました: " + count + "件");
    }

    /** その場所のブロックは、プレイヤーが置いたものか */
    public boolean contains(String world, int x, int y, int z) {
        Set<Long> blocks = placed.get(world);
        return blocks != null && blocks.contains(pack(x, y, z));
    }

    /** プレイヤーがブロックを置いた */
    public void add(String world, int x, int y, int z) {
        long packed = pack(x, y, z);
        if (placed.computeIfAbsent(world, w -> new HashSet<>()).add(packed)) {
            pending.put(new BlockKey(world, packed), System.currentTimeMillis());
        }
    }

    /**
     * その場所のブロックが無くなった（壊された・動かされた）。
     *
     * @return 記録があったら true
     */
    public boolean remove(String world, int x, int y, int z) {
        long packed = pack(x, y, z);
        Set<Long> blocks = placed.get(world);
        if (blocks == null || !blocks.remove(packed)) {
            return false;
        }
        pending.put(new BlockKey(world, packed), null);
        return true;
    }

    /** まだ DB に書いていない変更の件数 */
    public int pendingCount() {
        return pending.size();
    }

    /** 覚えている件数 */
    public int size() {
        int total = 0;
        for (Set<Long> blocks : placed.values()) {
            total += blocks.size();
        }
        return total;
    }

    /**
     * たまった変更を、1 回の取引でまとめて DB へ書く。
     * 失敗したときは変更を捨てずに持っておき、次の呼び出しでやり直す。
     */
    public void flush() {
        if (connection == null || pending.isEmpty()) {
            return;
        }
        // 取引の始めから終わりまで、ほかの処理に同じ接続を使わせない
        synchronized (connection) {
            flushLocked();
        }
    }

    private void flushLocked() {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR REPLACE INTO placed_blocks (world, x, y, z, placed_at) VALUES (?, ?, ?, ?, ?)");
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                for (Map.Entry<BlockKey, Long> change : pending.entrySet()) {
                    BlockKey key = change.getKey();
                    PreparedStatement stmt = change.getValue() != null ? insert : delete;
                    stmt.setString(1, key.world);
                    stmt.setInt(2, unpackX(key.packed));
                    stmt.setInt(3, unpackY(key.packed));
                    stmt.setInt(4, unpackZ(key.packed));
                    if (change.getValue() != null) {
                        stmt.setLong(5, change.getValue());
                    }
                    stmt.addBatch();
                }
                insert.executeBatch();
                delete.executeBatch();
            }
            connection.commit();
            pending.clear();
        } catch (SQLException e) {
            logger.warning("設置ブロックの記録を保存できませんでした（次回にやり直します）: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // 取り消しにも失敗した場合は、次回の保存に任せる
            }
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // 接続が閉じられている場合など。次の DB 操作で分かる
            }
        }
    }

    // ===== 座標を 1 つの数にまとめる（メモリを節約するため） =====
    // x・z は 26 ビット（±3300 万）、y は 12 ビット（-2048〜2047）。Minecraft の世界の広さに足りる。

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    /** ワールドと場所の組（まだ書いていない変更の見出し用） */
    private static final class BlockKey {
        final String world;
        final long packed;

        BlockKey(String world, long packed) {
            this.world = world;
            this.packed = packed;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BlockKey)) {
                return false;
            }
            BlockKey that = (BlockKey) other;
            return packed == that.packed && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return world.hashCode() * 31 + Long.hashCode(packed);
        }
    }
}
