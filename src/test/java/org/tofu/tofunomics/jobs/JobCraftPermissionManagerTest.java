package org.tofu.tofunomics.jobs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobCraftPermissionManager の職業別クラフト制限機能テスト
 *
 * 検証観点:
 * - 農家専売（パン・料理・貴重食料）: 農家は可、無職・他職業は不可
 * - 木こり専売（板材・木材加工品）: 木こりは可、無職は不可
 * - 誤爆防止: 石・鉄の階段/ドア/感圧板は非制限（全員可）
 * - 非制限品: 作業台・棒・チェスト、精錬品（焼き肉）は全員可
 */
public class JobCraftPermissionManagerTest {

    @Mock
    private JavaPlugin plugin;

    @Mock
    private JobManager jobManager;

    @Mock
    private ConfigManager configManager;

    @Mock
    private Player player;

    private JobCraftPermissionManager permissionManager;

    private final UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // plugin.getLogger() は警告ログ用（正常系では呼ばれないが保険でstub）
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        lenient().when(player.getUniqueId()).thenReturn(playerUuid);

        permissionManager = new JobCraftPermissionManager(plugin, jobManager, configManager);
    }

    /** プレイヤーの職業を設定するヘルパー（null = 無職） */
    private void setPlayerJob(String job) {
        when(jobManager.getPlayerJob(playerUuid)).thenReturn(job);
    }

    // ===== farmer 専売（料理・貴重食料） =====

    @Test
    public void testFarmerCanCraftFood() {
        setPlayerJob("farmer");
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.BREAD));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.COOKIE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.CAKE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.PUMPKIN_PIE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.GOLDEN_APPLE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.GOLDEN_CARROT));
    }

    @Test
    public void testUnemployedCannotCraftBread() {
        setPlayerJob(null); // 無職
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.BREAD));
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.GOLDEN_APPLE));
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.GOLDEN_CARROT));
    }

    @Test
    public void testMinerCannotCraftBread() {
        setPlayerJob("miner"); // 他職業
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.BREAD));
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.CAKE));
    }

    // ===== woodcutter 専売（板材・木材加工品） =====

    @Test
    public void testWoodcutterCanCraftWoodProducts() {
        setPlayerJob("woodcutter");
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.OAK_PLANKS));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.OAK_STAIRS));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.OAK_DOOR));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.SPRUCE_FENCE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.OAK_BOAT));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.BAMBOO_RAFT));
    }

    @Test
    public void testUnemployedCannotCraftWoodProducts() {
        setPlayerJob(null); // 無職
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.OAK_PLANKS));
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.OAK_STAIRS));
        assertFalse(permissionManager.canPlayerCraftItem(player, Material.OAK_DOOR));
    }

    // ===== 誤爆防止（非木材の階段/ドア/感圧板は全員可） =====

    @Test
    public void testStoneAndIronProductsNotRestricted() {
        setPlayerJob(null); // 無職でも作れることを確認
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.STONE_STAIRS));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.IRON_DOOR));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.IRON_TRAPDOOR));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.POLISHED_BLACKSTONE_PRESSURE_PLATE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.STONE_BUTTON));
    }

    // ===== 基礎クラフト・精錬品は非制限 =====

    @Test
    public void testBasicCraftItemsNotRestricted() {
        setPlayerJob(null); // 無職
        // 作業台・棒・チェストは成果物としては専売にしていない（全員クラフト可）
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.CRAFTING_TABLE));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.STICK));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.CHEST));
    }

    @Test
    public void testSmeltedFoodNotRestricted() {
        setPlayerJob(null); // 無職
        // 焼き料理は精錬でありCraftItemEventを通らない → 専売リストに含めていない
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.COOKED_BEEF));
        assertTrue(permissionManager.canPlayerCraftItem(player, Material.BAKED_POTATO));
    }
}
