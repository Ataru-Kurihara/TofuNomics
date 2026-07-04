package org.tofu.tofunomics.jobs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tofu.tofunomics.config.ConfigManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JobBlockPermissionManager の植え付け制限・採掘制限機能テスト
 */
public class JobBlockPermissionManagerTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private JobManager jobManager;

    @Mock
    private Player player;

    private JobBlockPermissionManager permissionManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // デフォルトで植え付け制限を有効化、管理者権限なし
        when(configManager.isJobPlantingRestrictionEnabled()).thenReturn(true);
        when(player.hasPermission("tofunomics.admin.place")).thenReturn(false);
        when(jobManager.getJobDisplayName("farmer")).thenReturn("農家");

        permissionManager = new JobBlockPermissionManager(configManager, jobManager);
    }

    @Test
    public void testFarmerCanPlantCrop() {
        // 農家は作物を植え付け可能
        when(jobManager.hasJob(player, "farmer")).thenReturn(true);

        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.WHEAT));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.CARROTS));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.SUGAR_CANE));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.BAMBOO));
    }

    @Test
    public void testNonFarmerCannotPlantCrop() {
        // 農家でないプレイヤーは作物を植え付け不可
        when(jobManager.hasJob(player, "farmer")).thenReturn(false);

        assertFalse(permissionManager.canPlayerPlantBlock(player, Material.WHEAT));
        assertFalse(permissionManager.canPlayerPlantBlock(player, Material.POTATOES));
        assertFalse(permissionManager.canPlayerPlantBlock(player, Material.PUMPKIN_STEM));
        assertFalse(permissionManager.canPlayerPlantBlock(player, Material.MELON_STEM));
    }

    @Test
    public void testDecorativeBlocksAllowedForEveryone() {
        // 装飾用フルブロックは農家でなくても設置可能（植え付け制限対象外）
        when(jobManager.hasJob(player, "farmer")).thenReturn(false);

        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.PUMPKIN));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.MELON));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.HAY_BLOCK));
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.DRIED_KELP_BLOCK));
        // 一般建材も当然許可
        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.STONE));
    }

    @Test
    public void testPlantingAllowedWhenRestrictionDisabled() {
        // 植え付け制限が無効なら誰でも植え付け可能
        when(configManager.isJobPlantingRestrictionEnabled()).thenReturn(false);
        when(jobManager.hasJob(player, "farmer")).thenReturn(false);

        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.WHEAT));
    }

    @Test
    public void testAdminBypassesPlantingRestriction() {
        // 管理者権限があれば農家でなくても植え付け可能
        when(player.hasPermission("tofunomics.admin.place")).thenReturn(true);
        when(jobManager.hasJob(player, "farmer")).thenReturn(false);

        assertTrue(permissionManager.canPlayerPlantBlock(player, Material.WHEAT));
    }

    @Test
    public void testSeedItemMapping() {
        // 種アイテムが対応する作物ブロックへ正しくマッピングされる
        assertEquals(Material.WHEAT, permissionManager.getCropBlockForSeed(Material.WHEAT_SEEDS));
        assertEquals(Material.CARROTS, permissionManager.getCropBlockForSeed(Material.CARROT));
        assertEquals(Material.POTATOES, permissionManager.getCropBlockForSeed(Material.POTATO));
        assertEquals(Material.BEETROOTS, permissionManager.getCropBlockForSeed(Material.BEETROOT_SEEDS));
        assertEquals(Material.PUMPKIN_STEM, permissionManager.getCropBlockForSeed(Material.PUMPKIN_SEEDS));
        assertEquals(Material.MELON_STEM, permissionManager.getCropBlockForSeed(Material.MELON_SEEDS));
        assertEquals(Material.NETHER_WART, permissionManager.getCropBlockForSeed(Material.NETHER_WART));
        // 種アイテムでないものはnull
        assertNull(permissionManager.getCropBlockForSeed(Material.STONE));
        assertNull(permissionManager.getCropBlockForSeed(Material.DIAMOND));
    }

    @Test
    public void testPlantingDeniedMessage() {
        // 拒否メッセージに必要職業名とブロック名が含まれる
        String message = permissionManager.getPlantingDeniedMessage(player, Material.WHEAT);

        assertTrue(message.contains("農家"));
        assertTrue(message.contains("WHEAT"));
        assertTrue(message.contains("植える"));
    }

    // ========== 採掘制限テスト（無職プレイヤーが鉱石を掘れる問題の回帰防止） ==========

    @Test
    public void testNonJobPlayerCannotBreakOre() {
        // 無職（鉱夫でない）プレイヤーは鉱石を採掘不可
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(true);
        when(jobManager.hasJob(player, "miner")).thenReturn(false);

        assertFalse("無職は石炭鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.COAL_ORE));
        assertFalse("無職は鉄鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.IRON_ORE));
        assertFalse("無職はダイヤ鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.DIAMOND_ORE));
    }

    @Test
    public void testDeepslateOreNormalizedForNonJob() {
        // 深層岩鉱石も通常鉱石へ正規化され、無職では採掘不可（抜け穴封じ）
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(true);
        when(jobManager.hasJob(player, "miner")).thenReturn(false);

        assertFalse("無職は深層岩石炭鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.DEEPSLATE_COAL_ORE));
        assertFalse("無職は深層岩鉄鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.DEEPSLATE_IRON_ORE));
        assertFalse("無職は深層岩ダイヤ鉱石を掘れない", permissionManager.canPlayerBreakBlock(player, Material.DEEPSLATE_DIAMOND_ORE));
    }

    @Test
    public void testMinerCanBreakOre() {
        // 鉱夫は鉱石を採掘可能
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(true);
        when(jobManager.hasJob(player, "miner")).thenReturn(true);

        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.COAL_ORE));
        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.DIAMOND_ORE));
        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.DEEPSLATE_COAL_ORE));
    }

    @Test
    public void testBasicBlocksBreakableByEveryone() {
        // 基本ブロック（石・丸石・原木等）は無職でも採掘可能
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(true);
        when(jobManager.hasJob(player, "miner")).thenReturn(false);

        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.STONE));
        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.COBBLESTONE));
        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.OAK_LOG));
    }

    @Test
    public void testBreakAllowedWhenRestrictionDisabled() {
        // 採掘制限が無効なら無職でも鉱石を採掘可能
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(false);
        when(jobManager.hasJob(player, "miner")).thenReturn(false);

        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.COAL_ORE));
    }

    @Test
    public void testAdminBypassesBreakRestriction() {
        // 管理者権限があれば無職でも鉱石を採掘可能
        when(configManager.isJobBlockRestrictionEnabled()).thenReturn(true);
        when(player.hasPermission("tofunomics.admin.break")).thenReturn(true);
        when(jobManager.hasJob(player, "miner")).thenReturn(false);

        assertTrue(permissionManager.canPlayerBreakBlock(player, Material.COAL_ORE));
    }

    @Test
    public void testMiningDeniedMessage() {
        // 拒否メッセージに必要職業名とブロック名が含まれる
        when(jobManager.getJobDisplayName("miner")).thenReturn("鉱夫");

        String message = permissionManager.getDeniedMessage(player, Material.COAL_ORE);

        assertTrue(message.contains("鉱夫"));
        assertTrue(message.contains("COAL_ORE"));
        assertTrue(message.contains("採掘"));
    }
}
