package org.tofu.tofunomics.experience;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.ChatColor;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerJobDAO;
import org.tofu.tofunomics.dao.JobDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.jobs.ExperienceManager;
import org.tofu.tofunomics.models.PlayerJob;
import org.tofu.tofunomics.models.Job;
import org.tofu.tofunomics.tools.JobToolManager;
import org.tofu.tofunomics.util.NotificationManager;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 職業別経験値獲得システム
 */
public class JobExperienceManager implements Listener {

    // 収穫経験値の付与に「完全成長」を必要とする作物（成長段階を持つ作物）
    private static final Set<Material> MATURITY_REQUIRED_CROPS = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART,
        Material.COCOA
    );

    private final ConfigManager configManager;
    private final PlayerJobDAO playerJobDAO;
    private final JobDAO jobDAO;
    private final JobManager jobManager;
    private final JobToolManager jobToolManager;
    private final ExperienceManager experienceManager;
    private final NotificationManager notificationManager = new NotificationManager();

    // 職業レベルBossBarへの即時反映用（setter注入。null許容）
    private org.tofu.tofunomics.scoreboard.JobLevelBossBarManager jobLevelBossBarManager;

    // 食事による経験値ブーストバフ用（setter注入。null許容）
    private org.tofu.tofunomics.food.FoodBuffManager foodBuffManager;

    // レベルアップ時のお金報酬用（setter注入。null許容）
    private org.tofu.tofunomics.rewards.JobLevelRewardManager jobLevelRewardManager;

    // 各職業で初めて入手したアイテムへの経験値ボーナス用（setter注入。null許容）
    private org.tofu.tofunomics.dao.FirstAcquisitionDAO firstAcquisitionDAO;

    // 釣り人の初回ボーナス判定キー（魚種別を問わず「最初の1匹だけ」ボーナスにするための固定キー）
    private static final String FISHERMAN_FIRST_KEY = "ANY_FISH";

    // 経験値テーブル
    private final Map<Material, Double> miningExperience;
    private final Map<Material, Double> loggingExperience;
    private final Map<Material, Double> farmingExperience;
    // 右クリック採取で得る作物の経験値テーブル（スイートベリー・グローベリー・はちみつ）。
    // BlockBreakEventでは発火しないため farmingExperience とは分離し、onPlayerInteractHarvestで付与する。
    private final Map<Material, Double> interactHarvestExperience;
    private final Map<PlayerFishEvent.State, Double> fishingExperience;
    private final Map<Material, Double> craftingExperience;
    private final Map<Material, Double> brewingExperience;
    private final Map<Integer, Double> enchantingExperience;
    private final Map<Material, Double> buildingExperience;

    // マーケット売り出品成約時に付与する職業経験値テーブル（出品される実アイテムに対応）
    private final Map<Material, MarketSellEntry> marketSellExperience;

    /**
     * マーケット出品アイテムに対応する職業と基準経験値の組
     */
    static final class MarketSellEntry {
        final String jobName;
        final double baseExp;

        MarketSellEntry(String jobName, double baseExp) {
            this.jobName = jobName;
            this.baseExp = baseExp;
        }
    }

    public JobExperienceManager(ConfigManager configManager, PlayerJobDAO playerJobDAO,
                               JobDAO jobDAO, JobManager jobManager, JobToolManager jobToolManager,
                               ExperienceManager experienceManager) {
        this.configManager = configManager;
        this.playerJobDAO = playerJobDAO;
        this.jobDAO = jobDAO;
        this.jobManager = jobManager;
        this.jobToolManager = jobToolManager;
        this.experienceManager = experienceManager;
        
        this.miningExperience = new HashMap<>();
        this.loggingExperience = new HashMap<>();
        this.farmingExperience = new HashMap<>();
        this.interactHarvestExperience = new HashMap<>();
        this.fishingExperience = new HashMap<>();
        this.craftingExperience = new HashMap<>();
        this.brewingExperience = new HashMap<>();
        this.enchantingExperience = new HashMap<>();
        this.buildingExperience = new HashMap<>();
        this.marketSellExperience = new HashMap<>();

        initializeExperienceTables();
        initializeMarketSellExperience();
    }

    /**
     * 職業レベルBossBar即時反映用のJobLevelBossBarManagerを注入する
     */
    public void setJobLevelBossBarManager(org.tofu.tofunomics.scoreboard.JobLevelBossBarManager jobLevelBossBarManager) {
        this.jobLevelBossBarManager = jobLevelBossBarManager;
    }

    /**
     * 食事による経験値ブーストバフ管理を注入する
     */
    public void setFoodBuffManager(org.tofu.tofunomics.food.FoodBuffManager foodBuffManager) {
        this.foodBuffManager = foodBuffManager;
    }

    /**
     * レベルアップ時のお金報酬管理を注入する
     */
    public void setJobLevelRewardManager(org.tofu.tofunomics.rewards.JobLevelRewardManager jobLevelRewardManager) {
        this.jobLevelRewardManager = jobLevelRewardManager;
    }

    /**
     * 初回入手経験値ボーナス用のFirstAcquisitionDAOを注入する
     */
    public void setFirstAcquisitionDAO(org.tofu.tofunomics.dao.FirstAcquisitionDAO firstAcquisitionDAO) {
        this.firstAcquisitionDAO = firstAcquisitionDAO;
    }

    /**
     * 各職業で初めて入手したアイテムの場合、経験値に初回ボーナス倍率を掛けて返す。
     * 初回でない・機能無効・DAO未注入の場合は exp をそのまま返す。
     * 初回入手時は「初めてだからボーナスが付いた」旨のメッセージをプレイヤーに表示する。
     *
     * @param itemKey 初回判定キー（多くの職業は Material 名、釣り人のみ {@link #FISHERMAN_FIRST_KEY}）
     */
    double applyFirstAcquisitionBonus(Player player, String jobName, String itemKey, double exp) {
        if (firstAcquisitionDAO == null || !configManager.isFirstAcquisitionBonusEnabled()) {
            return exp;
        }
        UUID uuid = player.getUniqueId();
        if (!firstAcquisitionDAO.isFirstAcquisition(uuid, jobName, itemKey)) {
            return exp;
        }
        firstAcquisitionDAO.recordAcquisition(uuid, jobName, itemKey);

        double multiplier = configManager.getFirstAcquisitionMultiplier();
        sendFirstAcquisitionMessage(player, multiplier);
        return exp * multiplier;
    }

    /**
     * 初回入手ボーナスのメッセージをプレイヤーに表示する。
     * 文言は config（gameplay.yml の first_acquisition_bonus.message）で変更可能。
     * プレースホルダ %multiplier% に倍率（小数が無ければ整数表記）を埋め込む。
     */
    private void sendFirstAcquisitionMessage(Player player, double multiplier) {
        String template = configManager.getFirstAcquisitionMessage();
        if (template == null || template.isEmpty()) {
            return;
        }
        // 倍率は 5.0 → "5"、2.5 → "2.5" のように余分な小数を省いて表示
        String multiplierText = (multiplier == Math.floor(multiplier))
            ? String.valueOf((long) multiplier)
            : String.valueOf(multiplier);
        String message = ChatColor.translateAlternateColorCodes('&',
            template.replace("%multiplier%", multiplierText));
        player.sendMessage(message);
    }

    private void initializeExperienceTables() {
        // 採掘経験値テーブル（レア度順。全鉱石が必ず STONE を上回るよう設計）
        miningExperience.put(Material.COAL_ORE, 2.5);
        miningExperience.put(Material.COPPER_ORE, 3.0);  // 1.17追加。安価な鉱石
        miningExperience.put(Material.GLOWSTONE, 3.0);  // ネザーの採掘対象。容易だが採取手間あり
        miningExperience.put(Material.LAPIS_ORE, 4.0);
        miningExperience.put(Material.REDSTONE_ORE, 4.0);
        miningExperience.put(Material.AMETHYST_CLUSTER, 4.0);  // ジオード収穫。シャードドロップ
        miningExperience.put(Material.IRON_ORE, 6.0);
        miningExperience.put(Material.NETHER_QUARTZ_ORE, 7.0);
        miningExperience.put(Material.NETHER_GOLD_ORE, 7.0);  // 1.16追加。金鉱石相当
        miningExperience.put(Material.OBSIDIAN, 8.0);  // ダイヤピッケル必須・採掘約9.4秒の高労力
        miningExperience.put(Material.CRYING_OBSIDIAN, 8.0);  // 黒曜石相当の労力
        miningExperience.put(Material.GOLD_ORE, 9.0);
        miningExperience.put(Material.EMERALD_ORE, 20.0);
        miningExperience.put(Material.DIAMOND_ORE, 25.0);
        miningExperience.put(Material.ANCIENT_DEBRIS, 50.0);
        // STONE: 自然生成のみ経験値獲得（プレイヤー設置はUnifiedEventHandlerで除外）
        miningExperience.put(Material.STONE, 1.0);  // 地グラインドの最低基準。鉱石は必ずこれを上回る
        // COBBLESTONE: 経験値なし（設置→採掘での無限経験値防止）
        
        // 伐採経験値テーブル
        loggingExperience.put(Material.OAK_LOG, 2.0);
        loggingExperience.put(Material.BIRCH_LOG, 2.0);
        loggingExperience.put(Material.SPRUCE_LOG, 2.0);
        loggingExperience.put(Material.JUNGLE_LOG, 3.0);
        loggingExperience.put(Material.ACACIA_LOG, 3.0);
        loggingExperience.put(Material.DARK_OAK_LOG, 3.0);
        loggingExperience.put(Material.WARPED_STEM, 4.0);
        loggingExperience.put(Material.CRIMSON_STEM, 4.0);
        loggingExperience.put(Material.MANGROVE_LOG, 3.0);  // 1.19追加。jungle/acacia相当
        loggingExperience.put(Material.CHERRY_LOG, 3.0);    // 1.20追加。jungle/acacia相当
        
        // 農業経験値テーブル（キーは破壊されるブロックのMaterial）
        // 小麦=基準（鉱夫の石ポジション。草から無限に種が得られる最も基本の作物）。
        // 入手・栽培に手間のかかるニンジン・ジャガイモは小麦を上回るよう設定する（逆転防止）。
        farmingExperience.put(Material.WHEAT, 1.5);
        farmingExperience.put(Material.POTATOES, 2.0);
        farmingExperience.put(Material.CARROTS, 2.0);
        farmingExperience.put(Material.BEETROOTS, 2.0);
        farmingExperience.put(Material.PUMPKIN, 3.0);
        farmingExperience.put(Material.MELON, 2.5);
        farmingExperience.put(Material.SUGAR_CANE, 1.0);
        farmingExperience.put(Material.COCOA, 2.5);
        farmingExperience.put(Material.NETHER_WART, 3.0);
        farmingExperience.put(Material.BAMBOO, 0.5);
        farmingExperience.put(Material.CACTUS, 1.0);
        farmingExperience.put(Material.BROWN_MUSHROOM, 1.5);
        farmingExperience.put(Material.RED_MUSHROOM, 1.5);

        // 右クリック採取経験値テーブル（キーは右クリックされるブロックのMaterial）
        // スイートベリー・グローベリーは骨粉で量産可能なため控えめ、はちみつは養蜂の手間を考慮しやや高め。
        interactHarvestExperience.put(Material.SWEET_BERRY_BUSH, 1.0);
        interactHarvestExperience.put(Material.CAVE_VINES, 1.0);
        interactHarvestExperience.put(Material.CAVE_VINES_PLANT, 1.0);
        interactHarvestExperience.put(Material.BEEHIVE, 1.5);
        interactHarvestExperience.put(Material.BEE_NEST, 1.5);

        // 釣り経験値テーブル
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_FISH, 5.0);
        fishingExperience.put(PlayerFishEvent.State.CAUGHT_ENTITY, 8.0);
        fishingExperience.put(PlayerFishEvent.State.IN_GROUND, 1.0);
        
        // 製作経験値テーブル（鍛冶屋）
        // インゴット精錬
        craftingExperience.put(Material.IRON_INGOT, 3.0);
        craftingExperience.put(Material.GOLD_INGOT, 5.0);
        craftingExperience.put(Material.NETHERITE_INGOT, 50.0);
        craftingExperience.put(Material.NETHERITE_SCRAP, 15.0);
        // 剣
        craftingExperience.put(Material.WOODEN_SWORD, 2.0);
        craftingExperience.put(Material.STONE_SWORD, 3.0);
        craftingExperience.put(Material.IRON_SWORD, 8.0);
        craftingExperience.put(Material.GOLDEN_SWORD, 8.0);
        craftingExperience.put(Material.DIAMOND_SWORD, 20.0);
        craftingExperience.put(Material.NETHERITE_SWORD, 60.0);
        // ツルハシ
        craftingExperience.put(Material.WOODEN_PICKAXE, 2.0);
        craftingExperience.put(Material.STONE_PICKAXE, 3.0);
        craftingExperience.put(Material.IRON_PICKAXE, 10.0);
        craftingExperience.put(Material.GOLDEN_PICKAXE, 10.0);
        craftingExperience.put(Material.DIAMOND_PICKAXE, 25.0);
        craftingExperience.put(Material.NETHERITE_PICKAXE, 70.0);
        // 斧
        craftingExperience.put(Material.WOODEN_AXE, 2.0);
        craftingExperience.put(Material.STONE_AXE, 3.0);
        craftingExperience.put(Material.IRON_AXE, 10.0);
        craftingExperience.put(Material.GOLDEN_AXE, 10.0);
        craftingExperience.put(Material.DIAMOND_AXE, 25.0);
        craftingExperience.put(Material.NETHERITE_AXE, 70.0);
        // シャベル
        craftingExperience.put(Material.WOODEN_SHOVEL, 1.5);
        craftingExperience.put(Material.STONE_SHOVEL, 2.0);
        craftingExperience.put(Material.IRON_SHOVEL, 6.0);
        craftingExperience.put(Material.GOLDEN_SHOVEL, 6.0);
        craftingExperience.put(Material.DIAMOND_SHOVEL, 15.0);
        craftingExperience.put(Material.NETHERITE_SHOVEL, 45.0);
        // クワ
        craftingExperience.put(Material.WOODEN_HOE, 1.5);
        craftingExperience.put(Material.STONE_HOE, 2.0);
        craftingExperience.put(Material.IRON_HOE, 6.0);
        craftingExperience.put(Material.GOLDEN_HOE, 6.0);
        craftingExperience.put(Material.DIAMOND_HOE, 15.0);
        craftingExperience.put(Material.NETHERITE_HOE, 45.0);
        // ヘルメット
        craftingExperience.put(Material.LEATHER_HELMET, 2.0);
        craftingExperience.put(Material.CHAINMAIL_HELMET, 12.0);
        craftingExperience.put(Material.IRON_HELMET, 9.0);
        craftingExperience.put(Material.GOLDEN_HELMET, 9.0);
        craftingExperience.put(Material.DIAMOND_HELMET, 22.0);
        craftingExperience.put(Material.NETHERITE_HELMET, 65.0);
        // チェストプレート
        craftingExperience.put(Material.LEATHER_CHESTPLATE, 3.0);
        craftingExperience.put(Material.CHAINMAIL_CHESTPLATE, 18.0);
        craftingExperience.put(Material.IRON_CHESTPLATE, 14.0);
        craftingExperience.put(Material.GOLDEN_CHESTPLATE, 14.0);
        craftingExperience.put(Material.DIAMOND_CHESTPLATE, 35.0);
        craftingExperience.put(Material.NETHERITE_CHESTPLATE, 100.0);
        // レギンス
        craftingExperience.put(Material.LEATHER_LEGGINGS, 2.5);
        craftingExperience.put(Material.CHAINMAIL_LEGGINGS, 16.0);
        craftingExperience.put(Material.IRON_LEGGINGS, 12.0);
        craftingExperience.put(Material.GOLDEN_LEGGINGS, 12.0);
        craftingExperience.put(Material.DIAMOND_LEGGINGS, 30.0);
        craftingExperience.put(Material.NETHERITE_LEGGINGS, 85.0);
        // ブーツ
        craftingExperience.put(Material.LEATHER_BOOTS, 2.0);
        craftingExperience.put(Material.CHAINMAIL_BOOTS, 12.0);
        craftingExperience.put(Material.IRON_BOOTS, 9.0);
        craftingExperience.put(Material.GOLDEN_BOOTS, 9.0);
        craftingExperience.put(Material.DIAMOND_BOOTS, 22.0);
        craftingExperience.put(Material.NETHERITE_BOOTS, 65.0);
        // その他装備
        craftingExperience.put(Material.SHIELD, 10.0);
        craftingExperience.put(Material.BOW, 8.0);
        craftingExperience.put(Material.CROSSBOW, 10.0);
        craftingExperience.put(Material.SHEARS, 5.0);
        craftingExperience.put(Material.FLINT_AND_STEEL, 4.0);
        craftingExperience.put(Material.FISHING_ROD, 6.0);
        
        // 醸造経験値テーブル
        brewingExperience.put(Material.POTION, 8.0);
        brewingExperience.put(Material.SPLASH_POTION, 8.0);
        brewingExperience.put(Material.LINGERING_POTION, 12.0);
        
        // エンチャント経験値テーブル（エンチャントレベル別）
        enchantingExperience.put(1, 10.0);
        enchantingExperience.put(2, 15.0);
        enchantingExperience.put(3, 25.0);
        enchantingExperience.put(4, 35.0);
        enchantingExperience.put(5, 50.0);
        
        // 建築経験値テーブル
        // 石材・加工系（建築の主役）
        buildingExperience.put(Material.STONE, 0.5);
        buildingExperience.put(Material.SMOOTH_STONE, 1.0);
        buildingExperience.put(Material.STONE_BRICKS, 1.0);
        buildingExperience.put(Material.CHISELED_STONE_BRICKS, 1.5);
        buildingExperience.put(Material.QUARTZ_BLOCK, 1.5);
        buildingExperience.put(Material.QUARTZ_PILLAR, 1.5);
        buildingExperience.put(Material.CHISELED_QUARTZ_BLOCK, 2.0);
        buildingExperience.put(Material.PRISMARINE, 1.5);
        buildingExperience.put(Material.PRISMARINE_BRICKS, 2.0);
        buildingExperience.put(Material.DARK_PRISMARINE, 2.0);
        buildingExperience.put(Material.PURPUR_BLOCK, 2.0);
        buildingExperience.put(Material.PURPUR_PILLAR, 2.0);
        buildingExperience.put(Material.END_STONE_BRICKS, 2.5);
        // レンガ・テラコッタ系
        buildingExperience.put(Material.BRICKS, 1.5);
        buildingExperience.put(Material.TERRACOTTA, 1.0);
        buildingExperience.put(Material.WHITE_TERRACOTTA, 1.2);
        buildingExperience.put(Material.NETHER_BRICKS, 1.5);
        buildingExperience.put(Material.RED_NETHER_BRICKS, 2.0);
        // 銅系
        buildingExperience.put(Material.COPPER_BLOCK, 1.5);
        // 木材・装飾系
        buildingExperience.put(Material.OAK_PLANKS, 0.5);
        buildingExperience.put(Material.SPRUCE_PLANKS, 0.5);
        buildingExperience.put(Material.BIRCH_PLANKS, 0.5);
        buildingExperience.put(Material.BOOKSHELF, 2.0);
        // ガラス系
        buildingExperience.put(Material.GLASS, 0.8);
        buildingExperience.put(Material.WHITE_STAINED_GLASS, 1.0);
        // 高級建材
        buildingExperience.put(Material.SEA_LANTERN, 3.0);
        buildingExperience.put(Material.GLOWSTONE, 2.5);
        // 安価ブロック（設置→破壊ループ濫用防止のため低値据え置き）
        buildingExperience.put(Material.COBBLESTONE, 0.1);
    }

    /**
     * マーケット売り出品成約時の職業経験値テーブルを初期化する。
     * キーは「実際に出品されるアイテム」のMaterial。採取テーブルがブロックベースなのに対し、
     * こちらは加工品（RAW_IRON, IRON_INGOT等）も含めて職業へ対応付ける。
     */
    private void initializeMarketSellExperience() {
        // 鉱夫（miner）— 採掘で得られる素材
        marketSellExperience.put(Material.COAL, new MarketSellEntry("miner", 2.0));
        marketSellExperience.put(Material.RAW_IRON, new MarketSellEntry("miner", 5.0));
        marketSellExperience.put(Material.RAW_GOLD, new MarketSellEntry("miner", 8.0));
        marketSellExperience.put(Material.RAW_COPPER, new MarketSellEntry("miner", 5.0));
        marketSellExperience.put(Material.DIAMOND, new MarketSellEntry("miner", 25.0));
        marketSellExperience.put(Material.EMERALD, new MarketSellEntry("miner", 20.0));
        marketSellExperience.put(Material.LAPIS_LAZULI, new MarketSellEntry("miner", 4.0));
        marketSellExperience.put(Material.REDSTONE, new MarketSellEntry("miner", 3.0));
        marketSellExperience.put(Material.QUARTZ, new MarketSellEntry("miner", 6.0));
        marketSellExperience.put(Material.ANCIENT_DEBRIS, new MarketSellEntry("miner", 50.0));

        // 木こり（woodcutter）— 各種原木
        marketSellExperience.put(Material.OAK_LOG, new MarketSellEntry("woodcutter", 2.0));
        marketSellExperience.put(Material.BIRCH_LOG, new MarketSellEntry("woodcutter", 2.0));
        marketSellExperience.put(Material.SPRUCE_LOG, new MarketSellEntry("woodcutter", 2.0));
        marketSellExperience.put(Material.JUNGLE_LOG, new MarketSellEntry("woodcutter", 3.0));
        marketSellExperience.put(Material.ACACIA_LOG, new MarketSellEntry("woodcutter", 3.0));
        marketSellExperience.put(Material.DARK_OAK_LOG, new MarketSellEntry("woodcutter", 3.0));
        marketSellExperience.put(Material.MANGROVE_LOG, new MarketSellEntry("woodcutter", 3.0));
        marketSellExperience.put(Material.CHERRY_LOG, new MarketSellEntry("woodcutter", 3.0));
        marketSellExperience.put(Material.WARPED_STEM, new MarketSellEntry("woodcutter", 4.0));
        marketSellExperience.put(Material.CRIMSON_STEM, new MarketSellEntry("woodcutter", 4.0));

        // 農家（farmer）— 収穫物
        marketSellExperience.put(Material.WHEAT, new MarketSellEntry("farmer", 1.5));
        marketSellExperience.put(Material.POTATO, new MarketSellEntry("farmer", 2.0));
        marketSellExperience.put(Material.CARROT, new MarketSellEntry("farmer", 2.0));
        marketSellExperience.put(Material.BEETROOT, new MarketSellEntry("farmer", 2.0));
        marketSellExperience.put(Material.PUMPKIN, new MarketSellEntry("farmer", 3.0));
        marketSellExperience.put(Material.MELON_SLICE, new MarketSellEntry("farmer", 2.5));
        marketSellExperience.put(Material.SUGAR_CANE, new MarketSellEntry("farmer", 1.0));
        marketSellExperience.put(Material.COCOA_BEANS, new MarketSellEntry("farmer", 2.5));
        marketSellExperience.put(Material.NETHER_WART, new MarketSellEntry("farmer", 3.0));
        // 農産加工品（パン類）— 農家
        marketSellExperience.put(Material.BREAD, new MarketSellEntry("farmer", 4.0));
        marketSellExperience.put(Material.BAKED_POTATO, new MarketSellEntry("farmer", 1.5));
        marketSellExperience.put(Material.COOKIE, new MarketSellEntry("farmer", 0.8));
        marketSellExperience.put(Material.PUMPKIN_PIE, new MarketSellEntry("farmer", 4.0));
        marketSellExperience.put(Material.CAKE, new MarketSellEntry("farmer", 8.0));
        marketSellExperience.put(Material.HAY_BLOCK, new MarketSellEntry("farmer", 12.0));
        marketSellExperience.put(Material.SUGAR, new MarketSellEntry("farmer", 0.5));
        marketSellExperience.put(Material.HONEY_BOTTLE, new MarketSellEntry("farmer", 1.5));
        // 収穫素材の補完 — 農家
        marketSellExperience.put(Material.MELON, new MarketSellEntry("farmer", 2.5));
        marketSellExperience.put(Material.SWEET_BERRIES, new MarketSellEntry("farmer", 1.0));
        marketSellExperience.put(Material.GLOW_BERRIES, new MarketSellEntry("farmer", 1.0));
        marketSellExperience.put(Material.RED_MUSHROOM, new MarketSellEntry("farmer", 1.5));
        marketSellExperience.put(Material.BROWN_MUSHROOM, new MarketSellEntry("farmer", 1.5));

        // 鍛冶屋（blacksmith）— 精錬・製作品
        marketSellExperience.put(Material.IRON_INGOT, new MarketSellEntry("blacksmith", 3.0));
        marketSellExperience.put(Material.GOLD_INGOT, new MarketSellEntry("blacksmith", 5.0));
        marketSellExperience.put(Material.COPPER_INGOT, new MarketSellEntry("blacksmith", 3.0));
        marketSellExperience.put(Material.NETHERITE_INGOT, new MarketSellEntry("blacksmith", 50.0));
        marketSellExperience.put(Material.IRON_SWORD, new MarketSellEntry("blacksmith", 8.0));
        marketSellExperience.put(Material.DIAMOND_SWORD, new MarketSellEntry("blacksmith", 20.0));
        marketSellExperience.put(Material.NETHERITE_SWORD, new MarketSellEntry("blacksmith", 60.0));
        marketSellExperience.put(Material.IRON_PICKAXE, new MarketSellEntry("blacksmith", 10.0));
        marketSellExperience.put(Material.DIAMOND_PICKAXE, new MarketSellEntry("blacksmith", 25.0));
        marketSellExperience.put(Material.NETHERITE_PICKAXE, new MarketSellEntry("blacksmith", 70.0));
        marketSellExperience.put(Material.IRON_CHESTPLATE, new MarketSellEntry("blacksmith", 14.0));
        marketSellExperience.put(Material.DIAMOND_CHESTPLATE, new MarketSellEntry("blacksmith", 35.0));
        marketSellExperience.put(Material.NETHERITE_CHESTPLATE, new MarketSellEntry("blacksmith", 100.0));
    }

    /**
     * マーケットの売り出品が成約した時、出品者へ職業経験値を付与する。
     * 出品されたアイテムが対応職業のマッピングに無い場合は何もしない。
     * 出品者がオンラインなら通常の経験値付与経路（レベルアップ演出・報酬込み）に委譲し、
     * オフラインなら経験値をDBへ直接加算する（レベルアップ演出・報酬は次回オンライン時に繰り越し）。
     *
     * @param sellerUuid 出品者UUID
     * @param material   成約したアイテムのMaterial
     * @param amount     成約した個数
     */
    public void giveMarketSellExperience(UUID sellerUuid, Material material, int amount) {
        if (sellerUuid == null || material == null || amount <= 0) {
            return;
        }
        MarketSellEntry entry = marketSellExperience.get(material);
        if (entry == null) {
            return;
        }
        double exp = calculateMarketSellExp(entry.baseExp, amount);
        if (exp <= 0) {
            return;
        }

        Player onlineSeller = Bukkit.getPlayer(sellerUuid);
        if (onlineSeller != null && onlineSeller.isOnline()) {
            // オンライン時は通常経路に委譲（就業判定・レベルアップ・BossBar・報酬が連動）
            if (jobManager.hasJob(onlineSeller, entry.jobName)) {
                giveJobExperience(onlineSeller, entry.jobName, exp);
            }
            return;
        }

        // オフライン時はDBへ直接加算（就業中＝該当PlayerJobが存在する場合のみ）
        try {
            Job job = jobDAO.getJobByNameSafe(entry.jobName);
            if (job == null) {
                return;
            }
            PlayerJob playerJob = playerJobDAO.getPlayerJob(sellerUuid, job.getId());
            if (playerJob == null) {
                // 未就業のため付与しない
                return;
            }
            playerJobDAO.addExperience(sellerUuid, job.getId(), exp);
        } catch (Exception e) {
            // 経験値付与の失敗は取引結果に影響させない
        }
    }

    /**
     * マーケット売り出品成約時の付与経験値を算出する（基準値 × 数量 × config係数）。
     * テスト容易化のため副作用を持たない純粋計算として切り出す。
     */
    double calculateMarketSellExp(double baseExp, int amount) {
        if (baseExp <= 0 || amount <= 0) {
            return 0.0;
        }
        return baseExp * amount * configManager.getMarketSellExpMultiplier();
    }

    /**
     * 指定Materialがマーケット売り出品経験値の対象なら対応職業名を返す（無ければnull）。
     * テスト・参照用。
     */
    String getMarketSellJobName(Material material) {
        MarketSellEntry entry = (material == null) ? null : marketSellExperience.get(material);
        return entry == null ? null : entry.jobName;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // 深層岩鉱石・深層岩石材を通常版へ正規化してから経験値マップを引く
        Material blockType = org.tofu.tofunomics.util.BlockNormalizer.normalizeForJob(event.getBlock().getType());

        // 鉱夫の採掘経験値
        if (jobManager.hasJob(player, "miner") && miningExperience.containsKey(blockType)) {
            double baseExp = miningExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "miner", baseExp);
            multipliedExp = applyFirstAcquisitionBonus(player, "miner", blockType.name(), multipliedExp);
            giveJobExperience(player, "miner", multipliedExp);
        }

        // 木こりの伐採経験値
        if (jobManager.hasJob(player, "woodcutter") && loggingExperience.containsKey(blockType)) {
            double baseExp = loggingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "woodcutter", baseExp);
            multipliedExp = applyFirstAcquisitionBonus(player, "woodcutter", blockType.name(), multipliedExp);
            giveJobExperience(player, "woodcutter", multipliedExp);
        }

        // 農家の収穫経験値（完全に成長した作物のみ付与）
        if (jobManager.hasJob(player, "farmer") && farmingExperience.containsKey(blockType)
                && isCropFullyGrown(event.getBlock())) {
            double baseExp = farmingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "farmer", baseExp);
            multipliedExp = applyFirstAcquisitionBonus(player, "farmer", blockType.name(), multipliedExp);
            giveJobExperience(player, "farmer", multipliedExp);
        }
    }

    /**
     * 骨粉（ボーンミール）による作物成長で農家経験値を付与する。
     * 骨粉成長は BlockGrowEvent ではなく BlockFertilizeEvent を発火するため、
     * 収穫経路（onBlockBreak）と同じ farmingExperience / 付与処理を再利用して経験値を与える。
     * 完全成長に達した作物のみを対象とし、半端な骨粉連打による無限ファーミングと
     * 収穫経験値との過度な二重取りを抑える（おおむね作物1サイクルあたり1回の付与）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        // ディスペンサー等による自動骨粉（プレイヤー不在）は対象外
        if (player == null || !jobManager.hasJob(player, "farmer")) {
            return;
        }

        for (BlockState state : event.getBlocks()) {
            // 深層岩鉱石等の正規化と同じ前処理（作物は通常そのまま）
            Material material = org.tofu.tofunomics.util.BlockNormalizer.normalizeForJob(state.getType());
            if (farmingExperience.containsKey(material) && isCropDataFullyGrown(material, state.getBlockData())) {
                double baseExp = farmingExperience.get(material);
                double multipliedExp = applyJobMultiplier(player, "farmer", baseExp);
                multipliedExp = applyFirstAcquisitionBonus(player, "farmer", material.name(), multipliedExp);
                giveJobExperience(player, "farmer", multipliedExp);
            }
        }
    }

    /**
     * 作物が「完全に成長した状態」かを判定する。
     * 成長段階を持つ作物（小麦・ニンジン・ジャガイモ・ビートルート・ネザーウォート・ココア）は
     * 最大成熟度に達している場合のみtrue。
     * 成熟概念のない作物（カボチャ・スイカ・サトウキビ・サボテン・竹・キノコ）は常にtrue。
     *
     * @param block 破壊された作物ブロック
     * @return 収穫経験値の付与対象ならtrue
     */
    static boolean isCropFullyGrown(Block block) {
        if (block == null) {
            return true;
        }
        return isCropDataFullyGrown(block.getType(), block.getBlockData());
    }

    /**
     * 作物の種別と BlockData から「完全に成長した状態」かを判定する。
     * BlockState（成長後の状態）からも判定できるよう、Block 依存を分離したヘルパー。
     *
     * @param material  作物の種別
     * @param data      対象ブロックの BlockData
     * @return 経験値付与対象（完全成長または成熟概念なし）ならtrue
     */
    static boolean isCropDataFullyGrown(Material material, BlockData data) {
        if (material == null || !MATURITY_REQUIRED_CROPS.contains(material)) {
            return true;
        }
        if (data instanceof Ageable) {
            Ageable ageable = (Ageable) data;
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }

    /**
     * 右クリック採取系作物（スイートベリー・グローベリー・はちみつ）の収穫経験値を付与する。
     * これらは BlockBreakEvent では発火しないため PlayerInteractEvent で個別に処理する。
     * 実際に採取が成立する状態（実が成っている／巣が満杯かつガラス瓶所持）のときのみ付与し、
     * 空の茂み・空の巣への空振りクリックでは付与しない（濫用防止）。
     */
    @EventHandler
    public void onPlayerInteractHarvest(PlayerInteractEvent event) {
        // 右クリック、かつメインハンドのみ処理（両手による二重発火を防止）
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (!jobManager.hasJob(player, "farmer")) return;

        Block block = event.getClickedBlock();
        Material blockType = block.getType();
        if (!interactHarvestExperience.containsKey(blockType)) return;

        // 採取が成立する状態のときのみ付与する
        if (!isInteractHarvestable(player, block)) return;

        double baseExp = interactHarvestExperience.get(blockType);
        double multipliedExp = applyJobMultiplier(player, "farmer", baseExp);
        multipliedExp = applyFirstAcquisitionBonus(player, "farmer", blockType.name(), multipliedExp);
        giveJobExperience(player, "farmer", multipliedExp);
    }

    /**
     * 右クリック採取で実際に収穫物が得られる状態かを判定する。
     * - スイートベリーの茂み: 実がドロップする成熟段階（age >= 2）
     * - 洞窟ツタ: 実が成っている（CaveVinesPlant#isBerries()）
     * - ハチの巣/ハチの巣箱: 満杯（honey_level == 最大）かつ手にガラス瓶またはハサミを所持
     */
    static boolean isInteractHarvestable(Player player, Block block) {
        BlockData data = block.getBlockData();
        Material type = block.getType();

        if (type == Material.SWEET_BERRY_BUSH) {
            return data instanceof Ageable && ((Ageable) data).getAge() >= 2;
        }
        if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            return data instanceof CaveVinesPlant && ((CaveVinesPlant) data).isBerries();
        }
        if (type == Material.BEEHIVE || type == Material.BEE_NEST) {
            if (!(data instanceof Beehive)) {
                return false;
            }
            Beehive hive = (Beehive) data;
            boolean full = hive.getHoneyLevel() >= hive.getMaximumHoneyLevel();
            // ガラス瓶（はちみつ）・ハサミ（ハニカム）のどちらの採取も対象にする
            Material mainHand = player.getInventory().getItemInMainHand().getType();
            boolean harvestTool = mainHand == Material.GLASS_BOTTLE || mainHand == Material.SHEARS;
            return full && harvestTool;
        }
        return false;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || 
            event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            
            Player player = event.getPlayer();
            if (jobManager.hasJob(player, "fisherman")) {
                double baseExp = fishingExperience.getOrDefault(event.getState(), 5.0);
                double multipliedExp = applyJobMultiplier(player, "fisherman", baseExp);
                // 釣り人は魚種別を問わず「最初の1匹だけ」初回ボーナス（固定キー）
                multipliedExp = applyFirstAcquisitionBonus(player, "fisherman", FISHERMAN_FIRST_KEY, multipliedExp);
                giveJobExperience(player, "fisherman", multipliedExp);
            }
        }
    }
    
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();
        
        if (jobManager.hasJob(player, "blacksmith") && craftingExperience.containsKey(craftedItem)) {
            double baseExp = craftingExperience.get(craftedItem);
            double multipliedExp = applyJobMultiplier(player, "blacksmith", baseExp);
            multipliedExp = applyFirstAcquisitionBonus(player, "blacksmith", craftedItem.name(), multipliedExp);
            giveJobExperience(player, "blacksmith", multipliedExp);
        }
    }
    
    @EventHandler
    public void onSmithItem(SmithItemEvent event) {
        // 鍛冶テーブルでの製作（ネザライト装備のアップグレード等）に対応
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null) return;
        Material smithedItem = event.getCurrentItem().getType();

        if (jobManager.hasJob(player, "blacksmith") && craftingExperience.containsKey(smithedItem)) {
            double baseExp = craftingExperience.get(smithedItem);
            double multipliedExp = applyJobMultiplier(player, "blacksmith", baseExp);
            multipliedExp = applyFirstAcquisitionBonus(player, "blacksmith", smithedItem.name(), multipliedExp);
            giveJobExperience(player, "blacksmith", multipliedExp);
        }
    }

    @EventHandler
    public void onBrew(BrewEvent event) {
        // 醸造台の使用者を特定する必要がある（近くのプレイヤーをチェック）
        event.getBlock().getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distance(event.getBlock().getLocation()) <= 5.0)
            .forEach(player -> {
            if (jobManager.hasJob(player, "alchemist")) {
                double baseExp = brewingExperience.getOrDefault(Material.POTION, 5.0);
                double multipliedExp = applyJobMultiplier(player, "alchemist", baseExp);
                giveJobExperience(player, "alchemist", multipliedExp);
            }
        });
    }
    
    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (jobManager.hasJob(player, "enchanter")) {
            int totalLevels = event.getEnchantsToAdd().values().stream()
                .mapToInt(Integer::intValue).sum();

            double baseExp = enchantingExperience.getOrDefault(totalLevels, 10.0);
            double multipliedExp = applyJobMultiplier(player, "enchanter", baseExp);
            // エンチャント対象アイテムの種類ごとに初回ボーナス
            multipliedExp = applyFirstAcquisitionBonus(player, "enchanter",
                event.getItem().getType().name(), multipliedExp);
            giveJobExperience(player, "enchanter", multipliedExp);
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        if (jobManager.hasJob(player, "architect") && buildingExperience.containsKey(blockType)) {
            double baseExp = buildingExperience.get(blockType);
            double multipliedExp = applyJobMultiplier(player, "architect", baseExp);
            giveJobExperience(player, "architect", multipliedExp);
        }
    }
    
    /**
     * プレイヤーに職業経験値を付与
     */
    private void giveJobExperience(Player player, String jobName, double experience) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) return;
        
        double currentExp = playerJob.getExperience();
        int currentLevel = playerJob.getLevel();
        
        playerJob.addExperience(experience);
        
        // レベルアップチェック
        checkLevelUp(player, playerJob, jobName, currentLevel);
        
        // データベース更新
        playerJobDAO.updatePlayerJobData(playerJob);

        // 職業レベルBossBarへ即時反映（経験値獲得・レベルアップを即座に表示）
        if (jobLevelBossBarManager != null) {
            jobLevelBossBarManager.refresh(player);
        }

        // 経験値獲得メッセージ（5経験値以上の場合のみ表示）
        if (experience >= 5.0) {
            player.sendMessage(ChatColor.GREEN + String.format("+ %.1f %s経験値", 
                experience, configManager.getJobDisplayName(jobName)));
        }
    }
    
    /**
     * レベルアップチェックと処理
     */
    private void checkLevelUp(Player player, PlayerJob playerJob, String jobName, int previousLevel) {
        while (canPlayerLevelUp(playerJob)) {
            playerJob.levelUp();
            int newLevel = playerJob.getLevel();
            String displayName = configManager.getJobDisplayName(jobName);

            // レベルアップメッセージ（ログとしてチャットにも残す）
            player.sendMessage(ChatColor.GOLD + "★ レベルアップ！ " +
                displayName + " レベル " + newLevel + " に到達！");

            // Title演出（画面中央に大きく表示）
            if (configManager.isLevelUpTitleEnabled()) {
                notificationManager.sendTitle(player,
                    "&6&l★ LEVEL UP ★",
                    "&e" + displayName + " &fLv." + newLevel,
                    10, 50, 20);
            }

            // パーティクル演出
            if (configManager.isLevelUpParticleEnabled()) {
                notificationManager.playCelebrationEffect(player);
            }

            // レベルアップ報酬（お金）の付与。5レベルおき（Lv5,10,…）に付与される。
            // while文で1レベルずつ処理されるため、複数レベル同時アップ時も各レベルで判定される。
            if (jobLevelRewardManager != null) {
                jobLevelRewardManager.giveJobLevelReward(player, jobName, newLevel);
            }

            // 職業レベル最大値チェック
            Job job = jobDAO.getJobByNameSafe(jobName);
            if (job != null && newLevel >= job.getMaxLevel()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "★ おめでとうございます！ " +
                    displayName + " の最大レベルに到達しました！");
                if (configManager.isLevelUpTitleEnabled()) {
                    notificationManager.sendTitle(player,
                        "&d&l★ MAX LEVEL ★",
                        "&d" + displayName + " &f最大レベル到達！",
                        10, 60, 20);
                }
                break;
            }
        }
    }
    
    /**
     * 職業レベルによる経験値倍率を適用
     */
    private double applyJobMultiplier(Player player, String jobName, double baseExperience) {
        PlayerJob playerJob = jobManager.getPlayerJob(player, jobName);
        if (playerJob == null) return baseExperience;
        
        // レベルが高いほど経験値獲得効率が下がる（現実的な成長曲線）
        double levelPenalty = Math.max(0.1, 1.0 - (playerJob.getLevel() * 0.01));
        
        // 設定ファイルの経験値倍率を適用
        Job job = jobDAO.getJobByNameSafe(jobName);
        double configMultiplier = job != null ?
            configManager.getJobExpMultiplier(jobName) : 1.0;

        // 食事バフ倍率を適用（職業マッチ制: 対応食材を食べた職業のみ。非マッチ・バフ無し・失効時は1.0）
        double foodBuffMultiplier = (foodBuffManager != null) ?
            foodBuffManager.getMultiplier(player, jobName) : 1.0;

        return baseExperience * levelPenalty * configMultiplier * foodBuffMultiplier;
    }
    
    /**
     * プレイヤーがレベルアップ可能かチェック
     */
    private boolean canPlayerLevelUp(PlayerJob playerJob) {
        double currentExp = playerJob.getExperience();
        double requiredExp = experienceManager.calculateRequiredExperience(playerJob.getLevel() + 1);
        return currentExp >= requiredExp;
    }
    
    /**
     * 手動で経験値を付与（管理者用）
     */
    public boolean giveExperienceManual(Player player, String jobName, double amount) {
        if (!jobManager.hasJob(player, jobName)) {
            return false;
        }

        // 食事バフ倍率を適用（職業マッチ制。Mob討伐経験値などの経路でも一貫して反映。非マッチ・バフ無し時は1.0）
        double foodBuffMultiplier = (foodBuffManager != null) ?
            foodBuffManager.getMultiplier(player, jobName) : 1.0;

        giveJobExperience(player, jobName, amount * foodBuffMultiplier);
        return true;
    }
}