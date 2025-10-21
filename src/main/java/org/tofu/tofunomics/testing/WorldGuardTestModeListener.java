package org.tofu.tofunomics.testing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * テストモード中のプレイヤーに対してWorldGuard保護をシミュレートするリスナー
 * LOWEST優先度で実行されるため、WorldGuardの処理より先に動作する
 */
public class WorldGuardTestModeListener implements Listener {
    private final Plugin plugin;
    private final Logger logger;
    private final TestModeManager testModeManager;
    private final org.tofu.tofunomics.integration.WorldGuardIntegration worldGuardIntegration;
    
    // テストモード中に操作を制限するブロックタイプ
    private static final Set<Material> PROTECTED_BLOCKS = EnumSet.of(
        // ドア類
        Material.OAK_DOOR,
        Material.SPRUCE_DOOR,
        Material.BIRCH_DOOR,
        Material.JUNGLE_DOOR,
        Material.ACACIA_DOOR,
        Material.DARK_OAK_DOOR,
        Material.CRIMSON_DOOR,
        Material.WARPED_DOOR,
        Material.IRON_DOOR,
        
        // トラップドア類
        Material.OAK_TRAPDOOR,
        Material.SPRUCE_TRAPDOOR,
        Material.BIRCH_TRAPDOOR,
        Material.JUNGLE_TRAPDOOR,
        Material.ACACIA_TRAPDOOR,
        Material.DARK_OAK_TRAPDOOR,
        Material.CRIMSON_TRAPDOOR,
        Material.WARPED_TRAPDOOR,
        Material.IRON_TRAPDOOR,
        
        // ボタン・レバー類
        Material.OAK_BUTTON,
        Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON,
        Material.ACACIA_BUTTON,
        Material.DARK_OAK_BUTTON,
        Material.CRIMSON_BUTTON,
        Material.WARPED_BUTTON,
        Material.STONE_BUTTON,
        Material.POLISHED_BLACKSTONE_BUTTON,
        Material.LEVER,
        
        // フェンスゲート類
        Material.OAK_FENCE_GATE,
        Material.SPRUCE_FENCE_GATE,
        Material.BIRCH_FENCE_GATE,
        Material.JUNGLE_FENCE_GATE,
        Material.ACACIA_FENCE_GATE,
        Material.DARK_OAK_FENCE_GATE,
        Material.CRIMSON_FENCE_GATE,
        Material.WARPED_FENCE_GATE,
        
        // コンテナ類
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL,
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.BREWING_STAND,
        Material.HOPPER,
        Material.DROPPER,
        Material.DISPENSER,
        Material.SHULKER_BOX,
        
        // その他のインタラクティブブロック
        Material.CRAFTING_TABLE,
        Material.ENCHANTING_TABLE,
        Material.ANVIL,
        Material.CHIPPED_ANVIL,
        Material.DAMAGED_ANVIL,
        Material.LOOM,
        Material.CARTOGRAPHY_TABLE,
        Material.GRINDSTONE,
        Material.STONECUTTER,
        Material.SMITHING_TABLE,
        Material.BEEHIVE,
        Material.BEE_NEST,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.RESPAWN_ANCHOR,
        Material.BELL,
        Material.LECTERN
    );
    
    public WorldGuardTestModeListener(Plugin plugin, TestModeManager testModeManager, 
                                      org.tofu.tofunomics.integration.WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.testModeManager = testModeManager;
        this.worldGuardIntegration = worldGuardIntegration;
    }
    
    /**
     * プレイヤーのブロックインタラクションをテストモードで制限
     * LOWEST優先度でWorldGuardより先に処理される
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // テストモード無効時は何もしない
        Player player = event.getPlayer();
        
        // デバッグ: イベントハンドラーが呼ばれたことをログ
        logger.info("WorldGuardTestModeListener: PlayerInteractEvent発生 - プレイヤー: " + player.getName());
        
        if (!testModeManager.isInTestMode(player)) {
            logger.info("WorldGuardTestModeListener: " + player.getName() + " はテストモード中ではありません");
            return;
        }
        
        logger.info("WorldGuardTestModeListener: " + player.getName() + " はテストモード中です");
        
        // 右クリックまたは左クリックのブロックインタラクションのみチェック
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        Material blockType = clickedBlock.getType();
        
        logger.info("WorldGuardTestModeListener: クリックされたブロック: " + blockType);
        
        // 保護対象ブロックタイプかチェック
        if (!PROTECTED_BLOCKS.contains(blockType)) {
            logger.info("WorldGuardTestModeListener: " + blockType + " は保護対象ブロックではありません");
            return;
        }
        
        logger.info("WorldGuardTestModeListener: " + blockType + " は保護対象ブロックです");
        
        // テストモード中は、WorldGuardの権限チェックを厳密に実行
        // OP権限を持っていてもWorldGuardの保護をバイパスさせない
        // canInteractメソッドはプレイヤーのメンバー権限も考慮する
        boolean canInteract = worldGuardIntegration.canInteract(player, clickedBlock.getLocation());
        logger.info("WorldGuardTestModeListener: 操作可否チェック結果: " + (canInteract ? "許可" : "拒否"));
        
        if (!canInteract) {
            // WorldGuardの権限チェックで操作不可の場合は拒否
            event.setCancelled(true);
            player.sendMessage("§c[テストモード] このブロックは保護されています");
            logger.info("テストモード: " + player.getName() + " がブロック " + blockType + " の操作を試みました（権限なしで拒否）");
        } else {
            // WorldGuardの権限チェックで操作可能な場合は許可
            logger.info("テストモード: " + player.getName() + " がブロック " + blockType + " の操作を許可（メンバー権限あり）");
        }
    }
}
