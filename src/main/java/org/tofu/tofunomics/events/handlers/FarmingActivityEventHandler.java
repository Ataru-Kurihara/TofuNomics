package org.tofu.tofunomics.events.handlers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.events.AsyncEventUpdater;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

/**
 * 農家の補助アクション経験値ハンドラ
 * 羊毛の毛刈り・卵の孵化・コンポスターでの骨粉生成を処理する。
 * 既存の BreedingEventHandler / GrowthEventHandler と同じく
 * AsyncEventUpdater 経由で農家の経験値を非同期付与する。
 */
public class FarmingActivityEventHandler {

    private final ConfigManager configManager;
    private final PlayerDAO playerDAO;
    private final JobManager jobManager;
    private final AsyncEventUpdater asyncUpdater;

    // 各アクションの基準経験値（farmingExperience テーブルと同じ流儀のハードコード）
    private static final double SHEAR_SHEEP_EXP = 1.5;       // 羊毛の毛刈り
    private static final double SHEAR_MUSHROOM_COW_EXP = 1.5; // キノコ牛のキノコ刈り
    private static final double EGG_HATCH_EXP = 2.0;          // 卵の孵化（1匹あたり）
    private static final double COMPOSTER_EXP = 2.0;          // コンポスター骨粉生成

    // コンポスター満杯（骨粉が取り出せる状態）のレベル
    private static final int COMPOSTER_READY_LEVEL = 8;

    public FarmingActivityEventHandler(ConfigManager configManager, PlayerDAO playerDAO,
                                       JobManager jobManager, AsyncEventUpdater asyncUpdater) {
        this.configManager = configManager;
        this.playerDAO = playerDAO;
        this.jobManager = jobManager;
        this.asyncUpdater = asyncUpdater;
    }

    /**
     * 毛刈りイベントの処理（羊毛・キノコ牛）
     */
    public void handleShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getEntity();

        double baseExp;
        String displayName;

        if (target instanceof Sheep) {
            Sheep sheep = (Sheep) target;
            // 既に毛が刈られている羊は対象外（スパム防止。草を食べて再生後に再取得可能）
            if (sheep.isSheared()) {
                return;
            }
            baseExp = SHEAR_SHEEP_EXP;
            displayName = "羊毛の刈り取り";
        } else if (target instanceof MushroomCow) {
            baseExp = SHEAR_MUSHROOM_COW_EXP;
            displayName = "キノコの刈り取り";
        } else {
            // 羊・キノコ牛以外（雪ゴーレム等）は対象外
            return;
        }

        PlayerJob farmerJob = getActiveFarmerJob(player);
        if (farmerJob == null) {
            return;
        }

        double finalExp = baseExp * getLevelMultiplier(farmerJob);
        giveFarmerExperience(player, finalExp, displayName);
    }

    /**
     * 卵の孵化イベントの処理
     */
    public void handleEggHatch(PlayerEggThrowEvent event) {
        // 投げた卵が孵化しなかった場合は対象外
        if (!event.isHatching() || event.getNumHatches() <= 0) {
            return;
        }

        Player player = event.getPlayer();
        PlayerJob farmerJob = getActiveFarmerJob(player);
        if (farmerJob == null) {
            return;
        }

        // 孵化した匹数を加味（getNumHatches は byte）
        double baseExp = EGG_HATCH_EXP * event.getNumHatches();
        double finalExp = baseExp * getLevelMultiplier(farmerJob);
        giveFarmerExperience(player, finalExp, "卵の孵化");
    }

    /**
     * コンポスターの骨粉取り出しイベントの処理
     * 満杯（LEVEL 8）のコンポスターを右クリックした時を「骨粉生成」とみなす。
     */
    public void handleComposterHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // 両手分の二重発火を防ぐためメインハンドのみ処理
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COMPOSTER) {
            return;
        }

        // 満杯（骨粉が取り出せる）状態のみ対象
        BlockData data = block.getBlockData();
        if (!(data instanceof Levelled)) {
            return;
        }
        if (((Levelled) data).getLevel() != COMPOSTER_READY_LEVEL) {
            return;
        }

        Player player = event.getPlayer();
        PlayerJob farmerJob = getActiveFarmerJob(player);
        if (farmerJob == null) {
            return;
        }

        double finalExp = COMPOSTER_EXP * getLevelMultiplier(farmerJob);
        giveFarmerExperience(player, finalExp, "堆肥づくり");
    }

    /**
     * アクティブな農家職業を取得（非農家・非アクティブは null）
     */
    private PlayerJob getActiveFarmerJob(Player player) {
        PlayerJob farmerJob = jobManager.getPlayerJob(player, "farmer");
        if (farmerJob == null || !farmerJob.isActive()) {
            return null;
        }
        return farmerJob;
    }

    /**
     * レベルボーナス倍率（レベル毎に2%。BreedingEventHandler の流儀に準拠）
     */
    private double getLevelMultiplier(PlayerJob farmerJob) {
        return 1.0 + (farmerJob.getLevel() * 0.02);
    }

    /**
     * 農家経験値を非同期付与し、一定量以上で通知する
     */
    private void giveFarmerExperience(Player player, double experience, String displayName) {
        if (experience <= 0) {
            return;
        }

        asyncUpdater.updateJobExperience(player.getUniqueId().toString(), "farmer", experience);

        // 毛刈り・卵孵化・コンポスターは頻度の低い意図的アクションのため、
        // 獲得量に関わらず必ず通知する（BreedingEventHandler と同じ挙動）。
        player.sendMessage(String.format(
            "%s%s成功！ §a+%.1f経験値",
            ChatColor.GREEN, displayName, experience
        ));
    }
}
