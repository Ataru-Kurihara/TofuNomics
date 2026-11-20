package org.tofu.tofunomics.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 借主の設置記録と預かり荷物の DAO のテスト（DatabaseManager と同じ DDL を SQLite インメモリに作る）。
 */
public class HousingPlacedBlockDAOTest {

    private Connection connection;
    private HousingPlacedBlockDAO placed;
    private HousingParcelDAO parcels;
    private final UUID tenant = UUID.randomUUID();

    @Before
    public void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS housing_placed_blocks (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    rental_id INTEGER NOT NULL," +
                    "    property_id INTEGER NOT NULL," +
                    "    tenant_uuid TEXT NOT NULL," +
                    "    world_name TEXT NOT NULL," +
                    "    x INTEGER NOT NULL," +
                    "    y INTEGER NOT NULL," +
                    "    z INTEGER NOT NULL," +
                    "    material TEXT NOT NULL," +
                    "    item_data TEXT," +
                    "    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    UNIQUE(world_name, x, y, z)" +
                    ");");
            st.execute("CREATE TABLE IF NOT EXISTS housing_parcels (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    tenant_uuid TEXT NOT NULL," +
                    "    property_id INTEGER NOT NULL," +
                    "    item_data TEXT NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");
        }
        placed = new HousingPlacedBlockDAO(connection);
        parcels = new HousingParcelDAO(connection);
    }

    @After
    public void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    public void 記録して座標で引ける_無い座標はnull() throws SQLException {
        placed.record(1, 92, tenant, "tofuNomics", -180, 76, -305, "CHEST", "item");
        HousingPlacedBlockDAO.PlacedBlock found = placed.find("tofuNomics", -180, 76, -305);
        assertNotNull(found);
        assertEquals(1, found.getRentalId());
        assertEquals(92, found.getPropertyId());
        assertEquals(tenant, found.getTenantUuid());
        assertEquals("CHEST", found.getMaterial());
        assertEquals("item", found.getItemData());
        assertNull(placed.find("tofuNomics", -180, 77, -305));
        assertNull(placed.find("world", -180, 76, -305));
    }

    @Test
    public void 同じ座標は置き換わり_契約ごとに引いて消せる() throws SQLException {
        placed.record(1, 92, tenant, "w", 0, 0, 0, "CHEST", "a");
        placed.record(2, 92, tenant, "w", 0, 0, 0, "BARREL", "b");
        placed.record(1, 92, tenant, "w", 1, 0, 0, "OAK_DOOR", null);

        assertEquals("BARREL", placed.find("w", 0, 0, 0).getMaterial());
        List<HousingPlacedBlockDAO.PlacedBlock> rental1 = placed.findByRental(1);
        assertEquals(1, rental1.size());
        assertNull(rental1.get(0).getItemData());

        placed.delete("w", 0, 0, 0);
        assertNull(placed.find("w", 0, 0, 0));
        placed.deleteByRental(1);
        assertTrue(placed.findByRental(1).isEmpty());
    }

    @Test
    public void 荷物は借主ごとに引けて_受け取りは一度だけ() throws SQLException {
        parcels.add(tenant, 92, "a");
        parcels.add(tenant, 92, "b");
        parcels.add(UUID.randomUUID(), 92, "c");

        List<HousingParcelDAO.Parcel> mine = parcels.findByTenant(tenant);
        assertEquals(2, mine.size());
        assertEquals(2, parcels.countByTenant(tenant));

        int id = mine.get(0).getId();
        // 他人の荷物は受け取れない
        assertFalse(parcels.delete(id, UUID.randomUUID()));
        assertTrue(parcels.delete(id, tenant));
        assertFalse(parcels.delete(id, tenant));

        parcels.updateItem(mine.get(1).getId(), "b2");
        assertEquals("b2", parcels.findByTenant(tenant).get(0).getItemData());
    }
}
