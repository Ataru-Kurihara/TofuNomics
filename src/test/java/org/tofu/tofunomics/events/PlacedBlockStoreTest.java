package org.tofu.tofunomics.events;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * 設置ブロックの記録が、再起動をまたいで残ることのテスト。
 * 以前はメモリだけに持っていたので、再起動の前に置いた石を掘ると経験値が入った。
 */
public class PlacedBlockStoreTest {

    private static final Logger LOGGER = Logger.getLogger("test");
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    private int rowCount() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM placed_blocks")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void 座標をまとめた数は元に戻せる() {
        int[][] samples = {{0, 0, 0}, {120, 64, -30}, {-29_999_999, -64, 29_999_999}, {-1, 319, -1}, {5, -2048, 7}, {5, 2047, 7}};
        for (int[] s : samples) {
            long packed = PlacedBlockStore.pack(s[0], s[1], s[2]);
            assertEquals(s[0], PlacedBlockStore.unpackX(packed));
            assertEquals(s[1], PlacedBlockStore.unpackY(packed));
            assertEquals(s[2], PlacedBlockStore.unpackZ(packed));
        }
    }

    @Test
    public void 隣の場所とは混ざらない() {
        assertNotEquals(PlacedBlockStore.pack(1, 64, 0), PlacedBlockStore.pack(0, 64, 1));
        assertNotEquals(PlacedBlockStore.pack(0, 64, 0), PlacedBlockStore.pack(0, 65, 0));
        assertNotEquals(PlacedBlockStore.pack(-1, 64, 0), PlacedBlockStore.pack(1, 64, 0));
    }

    @Test
    public void 置いた場所は再起動のあとも覚えている() {
        PlacedBlockStore before = new PlacedBlockStore(connection, LOGGER);
        before.add("tofuNomics", 120, 64, -30);
        before.flush();

        PlacedBlockStore afterRestart = new PlacedBlockStore(connection, LOGGER);
        assertTrue(afterRestart.contains("tofuNomics", 120, 64, -30));
        assertFalse("置いていない場所", afterRestart.contains("tofuNomics", 121, 64, -30));
        assertFalse("別のワールド", afterRestart.contains("lobby", 120, 64, -30));
    }

    @Test
    public void 壊した場所は再起動のあと忘れている() {
        PlacedBlockStore before = new PlacedBlockStore(connection, LOGGER);
        before.add("tofuNomics", 1, 2, 3);
        before.flush();
        assertTrue(before.remove("tofuNomics", 1, 2, 3));
        before.flush();

        assertFalse(new PlacedBlockStore(connection, LOGGER).contains("tofuNomics", 1, 2, 3));
    }

    @Test
    public void 置いてすぐ壊したら何も残らない() throws Exception {
        PlacedBlockStore store = new PlacedBlockStore(connection, LOGGER);
        store.add("tofuNomics", 1, 2, 3);
        store.remove("tofuNomics", 1, 2, 3);
        store.flush();
        assertEquals(0, rowCount());
        assertFalse(store.contains("tofuNomics", 1, 2, 3));
    }

    @Test
    public void 書き込みはまとめて行う() throws Exception {
        PlacedBlockStore store = new PlacedBlockStore(connection, LOGGER);
        for (int i = 0; i < 100; i++) {
            store.add("tofuNomics", i, 64, 0);
        }
        assertEquals("flush するまで DB には書かない", 0, rowCount());
        assertEquals(100, store.pendingCount());

        store.flush();
        assertEquals(100, rowCount());
        assertEquals(0, store.pendingCount());
        assertTrue("ほかの処理のために自動コミットを元に戻す", connection.getAutoCommit());
    }

    @Test
    public void 記録が無い場所を壊しても何も起きない() {
        PlacedBlockStore store = new PlacedBlockStore(connection, LOGGER);
        assertFalse(store.remove("tofuNomics", 9, 9, 9));
        assertEquals(0, store.pendingCount());
    }

    @Test
    public void 上限を超えたら起動時に古い順に消す() throws Exception {
        new PlacedBlockStore(connection, LOGGER);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO placed_blocks (world, x, y, z, placed_at) VALUES ('tofuNomics', ?, 64, 0, ?)")) {
            for (int i = 0; i < 10; i++) {
                stmt.setInt(1, i);
                stmt.setLong(2, 1000 + i);   // i が小さいほど古い
                stmt.executeUpdate();
            }
        }

        PlacedBlockStore store = new PlacedBlockStore(connection, LOGGER, 7);
        assertEquals(7, store.size());
        assertFalse("いちばん古い 3 件は消える", store.contains("tofuNomics", 2, 64, 0));
        assertTrue(store.contains("tofuNomics", 3, 64, 0));
        assertEquals(0, PlacedBlockStore.excessRows(7, 7));
        assertEquals(3, PlacedBlockStore.excessRows(10, 7));
    }

    @Test
    public void DBが無くてもメモリの中では動く() {
        PlacedBlockStore store = new PlacedBlockStore(null, LOGGER);
        store.add("tofuNomics", 1, 2, 3);
        store.flush();
        assertTrue(store.contains("tofuNomics", 1, 2, 3));
    }
}
