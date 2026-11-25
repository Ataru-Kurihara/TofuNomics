package org.tofu.tofunomics.farming;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.FarmPlotDAO;
import org.tofu.tofunomics.integration.WorldGuardIntegration;
import org.tofu.tofunomics.models.FarmPlot;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 農家畑区画(プロット)の管理マネージャ。
 * - 管理者のコマンドによる区画作成/削除
 * - 農家就職時の自動割り当て / 離職時の解放（1人1区画）
 * 窃盗防止はWorldGuardのメンバー機構（所有者をmember登録）で実現する。
 */
public class FarmPlotManager {

    // 区画のWGリージョンを登録範囲から広げる段数（耕す地面の段と、作物・足場の高さを含めるため）
    static final int REGION_EXPAND_DOWN = 3;
    static final int REGION_EXPAND_UP = 10;

    // 区画リージョンに付けるフラグ（住居と同じ形。3つともメンバーだけ許可し、非メンバーは親(city)の deny に従う）
    private static final String[] PLOT_FLAGS = {"build", "block-break", "interact"};

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final FarmPlotDAO farmPlotDAO;

    // 管理者のコマンドによる角選択（足元ブロック位置）
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    // 全区画のインメモリキャッシュ（フェンスゲート/ドア開閉判定で高頻度に参照されるためDBアクセスを避ける）
    // 区画やオーナーが変わるたびに invalidateCache() で破棄する
    private List<FarmPlot> plotCache = null;

    public FarmPlotManager(TofuNomics plugin, ConfigManager configManager, FarmPlotDAO farmPlotDAO) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.farmPlotDAO = farmPlotDAO;
    }

    // WorldGuard連携は初期化順序の都合で遅延取得する
    private WorldGuardIntegration wg() {
        return plugin.getWorldGuardIntegration();
    }

    // ========== 角選択（コマンド） ==========

    public void setPos1(Player player, Location location) {
        pos1.put(player.getUniqueId(), location);
    }

    public void setPos2(Player player, Location location) {
        pos2.put(player.getUniqueId(), location);
    }

    public boolean hasSelection(Player player) {
        return pos1.containsKey(player.getUniqueId()) && pos2.containsKey(player.getUniqueId());
    }

    // ========== 区画の作成/削除（管理者） ==========

    /**
     * 選択範囲から区画を作成（WGリージョン作成 + DB登録）
     */
    public boolean createPlot(Player admin, String name) {
        WorldGuardIntegration wg = wg();
        if (wg == null || !wg.isEnabled()) {
            admin.sendMessage(ChatColor.RED + "WorldGuardが無効なため区画を作成できません。");
            return false;
        }
        if (!hasSelection(admin)) {
            admin.sendMessage(ChatColor.RED + "範囲が未選択です。/farmplot pos1 と /farmplot pos2 で角を指定してください。");
            return false;
        }
        Location p1 = pos1.get(admin.getUniqueId());
        Location p2 = pos2.get(admin.getUniqueId());
        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().getName().equals(p2.getWorld().getName())) {
            admin.sendMessage(ChatColor.RED + "pos1とpos2は同じワールドで指定してください。");
            return false;
        }

        try {
            if (farmPlotDAO.getPlotByName(name) != null) {
                admin.sendMessage(ChatColor.RED + "区画名 '" + name + "' は既に存在します。");
                return false;
            }
            String regionId = ("farmplot_" + name).toLowerCase();
            World world = p1.getWorld();

            // 選択範囲の Y を上下に広げてから作る（WG と DB を同じ範囲にそろえる）
            int[] y = expandedYRange(p1.getBlockY(), p2.getBlockY());
            Location c1 = new Location(world, p1.getBlockX(), y[0], p1.getBlockZ());
            Location c2 = new Location(world, p2.getBlockX(), y[1], p2.getBlockZ());

            if (!wg.createRegion(regionId, world, c1, c2)) {
                admin.sendMessage(ChatColor.RED + "WorldGuardリージョンの作成に失敗しました（同名リージョンが存在する可能性）。");
                return false;
            }

            FarmPlot plot = new FarmPlot(name, world.getName(),
                    p1.getBlockX(), y[0], p1.getBlockZ(),
                    p2.getBlockX(), y[1], p2.getBlockZ(), regionId);
            int id = farmPlotDAO.createPlot(plot);
            if (id <= 0) {
                // DB登録失敗時はWGリージョンをロールバック
                wg.removeRegion(regionId, world);
                admin.sendMessage(ChatColor.RED + "区画のDB登録に失敗しました。");
                return false;
            }
            plot.setId(id);

            // 親リージョンとフラグを付ける（範囲は広げ済みなので、ここでは広げない）
            String problem = applyPlotRegionSettings(plot, world);
            if (problem != null) {
                admin.sendMessage(ChatColor.YELLOW + "区画は作成しましたが、保護の設定に失敗しました: " + problem
                        + "（/farmplot fixregions で付け直せます）");
            }

            // 選択をクリア
            pos1.remove(admin.getUniqueId());
            pos2.remove(admin.getUniqueId());

            invalidateCache();
            admin.sendMessage(ChatColor.GREEN + "畑区画 '" + name + "' を作成しました（リージョン: " + regionId + "）。");
            return true;
        } catch (SQLException e) {
            admin.sendMessage(ChatColor.RED + "区画作成中にエラーが発生しました。");
            plugin.getLogger().severe("farm plot 作成エラー: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePlot(Player admin, String name) {
        try {
            FarmPlot plot = farmPlotDAO.getPlotByName(name);
            if (plot == null) {
                admin.sendMessage(ChatColor.RED + "区画 '" + name + "' は存在しません。");
                return false;
            }
            WorldGuardIntegration wg = wg();
            World world = Bukkit.getWorld(plot.getWorldName());
            if (wg != null && wg.isEnabled() && world != null && plot.getWorldguardRegionId() != null) {
                wg.removeRegion(plot.getWorldguardRegionId(), world);
            }
            farmPlotDAO.deletePlot(plot.getId());
            invalidateCache();
            admin.sendMessage(ChatColor.GREEN + "畑区画 '" + name + "' を削除しました。");
            return true;
        } catch (SQLException e) {
            admin.sendMessage(ChatColor.RED + "区画削除中にエラーが発生しました。");
            plugin.getLogger().severe("farm plot 削除エラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * 選択範囲で既存区画の座標範囲を更新する。
     * 所有者(owner_uuid)・WGメンバー登録・リージョンID・各種フラグは保持し、範囲のみ変更する。
     */
    public boolean resizePlot(Player admin, String name) {
        WorldGuardIntegration wg = wg();
        if (wg == null || !wg.isEnabled()) {
            admin.sendMessage(ChatColor.RED + "WorldGuardが無効なため区画をリサイズできません。");
            return false;
        }
        if (!hasSelection(admin)) {
            admin.sendMessage(ChatColor.RED + "範囲が未選択です。/farmplot pos1 と /farmplot pos2 で角を指定してください。");
            return false;
        }
        Location p1 = pos1.get(admin.getUniqueId());
        Location p2 = pos2.get(admin.getUniqueId());
        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().getName().equals(p2.getWorld().getName())) {
            admin.sendMessage(ChatColor.RED + "pos1とpos2は同じワールドで指定してください。");
            return false;
        }

        try {
            FarmPlot plot = farmPlotDAO.getPlotByName(name);
            if (plot == null) {
                admin.sendMessage(ChatColor.RED + "区画 '" + name + "' は存在しません。");
                return false;
            }
            World world = p1.getWorld();
            // 別ワールドへのリサイズは禁止（リージョンはワールド固有）
            if (!world.getName().equals(plot.getWorldName())) {
                admin.sendMessage(ChatColor.RED + "区画 '" + name + "' は別ワールド("
                        + plot.getWorldName() + ")にあります。同じワールドで範囲を選択してください。");
                return false;
            }

            String regionId = plot.getWorldguardRegionId();

            // 旧座標を控える（DB更新失敗時のロールバック用）
            Location oldP1 = new Location(world, plot.getX1(), plot.getY1(), plot.getZ1());
            Location oldP2 = new Location(world, plot.getX2(), plot.getY2(), plot.getZ2());

            // WGリージョンを新範囲で再構築（メンバー/オーナー/フラグを保持）
            if (!wg.redefineRegion(regionId, world, p1, p2)) {
                admin.sendMessage(ChatColor.RED + "WorldGuardリージョンの再構築に失敗しました。");
                return false;
            }

            // DBの座標を更新
            boolean dbUpdated = farmPlotDAO.updateCoordinates(plot.getId(),
                    p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
            if (!dbUpdated) {
                // DB更新失敗時はWGリージョンを旧範囲に戻してロールバック
                wg.redefineRegion(regionId, world, oldP1, oldP2);
                admin.sendMessage(ChatColor.RED + "区画のDB更新に失敗しました。リージョンを元の範囲に戻しました。");
                return false;
            }

            // 選択をクリア
            pos1.remove(admin.getUniqueId());
            pos2.remove(admin.getUniqueId());

            invalidateCache();
            admin.sendMessage(ChatColor.GREEN + "畑区画 '" + name + "' の範囲を更新しました。");
            return true;
        } catch (SQLException e) {
            admin.sendMessage(ChatColor.RED + "区画リサイズ中にエラーが発生しました。");
            plugin.getLogger().severe("farm plot リサイズエラー: " + e.getMessage());
            return false;
        }
    }

    public List<FarmPlot> listPlots() {
        try {
            return farmPlotDAO.getAllPlots();
        } catch (SQLException e) {
            plugin.getLogger().severe("farm plot 一覧取得エラー: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public FarmPlot getPlotByName(String name) {
        try {
            return farmPlotDAO.getPlotByName(name);
        } catch (SQLException e) {
            plugin.getLogger().severe("farm plot 取得エラー: " + e.getMessage());
            return null;
        }
    }

    /**
     * 指定座標を含む畑区画を返す（無ければnull）。
     * フェンスゲート/ドアの開閉判定など高頻度呼び出しを想定し、全区画をキャッシュして走査する。
     */
    public FarmPlot getPlotAt(Location location) {
        if (location == null) {
            return null;
        }
        List<FarmPlot> plots = plotCache;
        if (plots == null) {
            try {
                plots = farmPlotDAO.getAllPlots();
            } catch (SQLException e) {
                plugin.getLogger().severe("farm plot キャッシュ取得エラー: " + e.getMessage());
                return null;
            }
            plotCache = plots;
        }
        for (FarmPlot plot : plots) {
            if (plot.contains(location)) {
                return plot;
            }
        }
        return null;
    }

    /**
     * 区画キャッシュを破棄する。区画の作成/削除/リサイズや所有者の割り当て/解放の成功後に呼ぶ。
     */
    private void invalidateCache() {
        plotCache = null;
    }

    // ========== 割り当て / 解放 ==========

    /**
     * 農家就職時の自動割り当て（1人1区画）。職業就職自体は妨げない。
     */
    public void assignPlot(Player player) {
        WorldGuardIntegration wg = wg();
        if (wg == null || !wg.isEnabled()) {
            return;
        }
        String uuid = player.getUniqueId().toString();
        try {
            // 既に所有していれば何もしない
            if (farmPlotDAO.getPlotByOwner(uuid) != null) {
                return;
            }
            FarmPlot plot = farmPlotDAO.findAvailablePlot();
            if (plot == null) {
                // 区画が1つも登録されていない場合は機能未導入とみなし無言でスキップ
                if (farmPlotDAO.getAllPlots().isEmpty()) {
                    return;
                }
                player.sendMessage(ChatColor.YELLOW + "現在割り当て可能な畑区画がありません。管理者にお問い合わせください。");
                plugin.getLogger().warning("農家区画の空きがありません: " + player.getName());
                return;
            }
            World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) {
                plugin.getLogger().warning("農家区画のワールドが見つかりません: " + plot.getWorldName());
                return;
            }
            boolean memberAdded = wg.addMember(plot.getWorldguardRegionId(), world, player.getUniqueId());
            if (!memberAdded) {
                plugin.getLogger().warning("農家区画のWGメンバー追加に失敗: " + plot.getPlotName());
                return;
            }
            farmPlotDAO.updateOwner(plot.getId(), uuid);
            invalidateCache();

            int cx = (plot.getX1() + plot.getX2()) / 2;
            int cy = Math.max(plot.getY1(), plot.getY2());
            int cz = (plot.getZ1() + plot.getZ2()) / 2;
            player.sendMessage(ChatColor.GREEN + "農家の畑区画 '" + plot.getPlotName()
                    + "' が割り当てられました！ 場所: " + ChatColor.AQUA
                    + plot.getWorldName() + " (" + cx + ", " + cy + ", " + cz + ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("農家区画の割り当てエラー: " + e.getMessage());
        }
    }

    /**
     * 離職/転職時の区画解放（UUIDベース＝オフライン対応）
     */
    public void releasePlot(UUID playerUuid) {
        WorldGuardIntegration wg = wg();
        String uuid = playerUuid.toString();
        try {
            FarmPlot plot = farmPlotDAO.getPlotByOwner(uuid);
            if (plot == null) {
                return;
            }
            World world = Bukkit.getWorld(plot.getWorldName());
            if (wg != null && wg.isEnabled() && world != null && plot.getWorldguardRegionId() != null) {
                wg.removeMember(plot.getWorldguardRegionId(), world, playerUuid);
            }
            farmPlotDAO.updateOwner(plot.getId(), null);
            invalidateCache();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "農家の畑区画 '" + plot.getPlotName() + "' が解放されました。");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("農家区画の解放エラー: " + e.getMessage());
        }
    }

    /**
     * 管理者による手動割り当て（既存農家向け）。plotName が null の場合は空き区画から自動選択。
     */
    public boolean assignPlotManually(Player admin, Player target, String plotName) {
        WorldGuardIntegration wg = wg();
        if (wg == null || !wg.isEnabled()) {
            admin.sendMessage(ChatColor.RED + "WorldGuardが無効です。");
            return false;
        }
        String uuid = target.getUniqueId().toString();
        try {
            if (farmPlotDAO.getPlotByOwner(uuid) != null) {
                admin.sendMessage(ChatColor.RED + target.getName() + " は既に区画を所有しています。");
                return false;
            }
            FarmPlot plot = (plotName != null) ? farmPlotDAO.getPlotByName(plotName) : farmPlotDAO.findAvailablePlot();
            if (plot == null) {
                admin.sendMessage(ChatColor.RED + "割り当て可能な区画が見つかりません。");
                return false;
            }
            if (plot.isAssigned()) {
                admin.sendMessage(ChatColor.RED + "区画 '" + plot.getPlotName() + "' は既に割り当て済みです。");
                return false;
            }
            World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) {
                admin.sendMessage(ChatColor.RED + "区画のワールドが見つかりません。");
                return false;
            }
            if (!wg.addMember(plot.getWorldguardRegionId(), world, target.getUniqueId())) {
                // メンバー登録に失敗したまま所有者だけ書くと「割り当て済みなのに耕せない」状態になるため止める
                admin.sendMessage(ChatColor.RED + "区画 '" + plot.getPlotName()
                        + "' のWorldGuardメンバー登録に失敗しました。割り当ては行っていません。");
                plugin.getLogger().warning("農家区画の手動割り当てでWGメンバー追加に失敗: " + plot.getPlotName());
                return false;
            }
            farmPlotDAO.updateOwner(plot.getId(), uuid);
            invalidateCache();
            admin.sendMessage(ChatColor.GREEN + target.getName() + " に区画 '" + plot.getPlotName() + "' を割り当てました。");
            target.sendMessage(ChatColor.GREEN + "農家の畑区画 '" + plot.getPlotName() + "' が割り当てられました。");
            return true;
        } catch (SQLException e) {
            admin.sendMessage(ChatColor.RED + "割り当て中にエラーが発生しました。");
            plugin.getLogger().severe("農家区画の手動割り当てエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * 管理者による手動解放
     */
    public boolean releasePlotManually(Player admin, Player target) {
        try {
            if (farmPlotDAO.getPlotByOwner(target.getUniqueId().toString()) == null) {
                admin.sendMessage(ChatColor.RED + target.getName() + " は区画を所有していません。");
                return false;
            }
            releasePlot(target.getUniqueId());
            admin.sendMessage(ChatColor.GREEN + target.getName() + " の区画を解放しました。");
            return true;
        } catch (SQLException e) {
            admin.sendMessage(ChatColor.RED + "解放中にエラーが発生しました。");
            plugin.getLogger().severe("農家区画の手動解放エラー: " + e.getMessage());
            return false;
        }
    }

    // ========== リージョンの保護設定（作成時・付け直しの共通処理） ==========

    /**
     * 登録範囲の Y を下に REGION_EXPAND_DOWN 段・上に REGION_EXPAND_UP 段広げた範囲を返す。
     *
     * @return {広げた後の下端, 広げた後の上端}
     */
    static int[] expandedYRange(int y1, int y2) {
        return new int[]{Math.min(y1, y2) - REGION_EXPAND_DOWN, Math.max(y1, y2) + REGION_EXPAND_UP};
    }

    /**
     * 範囲がもう広げてあるかを判定する。
     * 広げた範囲は必ず (上端 - 下端) が REGION_EXPAND_DOWN + REGION_EXPAND_UP 以上になるので、それで見分ける。
     * これで付け直しを何度流しても、範囲が広がり続けない。
     */
    static boolean isYRangeExpanded(int y1, int y2) {
        return Math.abs(y2 - y1) >= REGION_EXPAND_DOWN + REGION_EXPAND_UP;
    }

    /**
     * 親にするリージョン名を返す。都市保護が無効、または名前が空なら null（親を付けない）。
     */
    static String resolveParentRegionName(boolean cityProtectionEnabled, String regionName) {
        if (!cityProtectionEnabled || regionName == null || regionName.trim().isEmpty()) {
            return null;
        }
        return regionName.trim();
    }

    /**
     * 区画の WG リージョンを「耕せて植えられる形」にそろえる。何度呼んでも同じ結果になる。
     * 1. Y がまだ広げてなければ、上下に広げる（WG と DB の両方。plot の値も書き換える）
     * 2. 親リージョンを housing_rental.city_protection.region_name にする（無効・空なら警告ログだけ）
     * 3. build / block-break / interact を allow、3つとも対象をメンバーに限る
     *
     * @return 問題が無ければ null。失敗したときはその理由
     */
    String applyPlotRegionSettings(FarmPlot plot, World world) {
        WorldGuardIntegration wg = wg();
        if (wg == null || !wg.isEnabled()) {
            return "WorldGuardが無効です";
        }
        String regionId = plot.getWorldguardRegionId();
        if (regionId == null || !wg.hasRegion(regionId, world)) {
            return "リージョン " + regionId + " が見つかりません";
        }

        // 1. 高さを広げる
        if (!isYRangeExpanded(plot.getY1(), plot.getY2())) {
            int[] y = expandedYRange(plot.getY1(), plot.getY2());
            Location c1 = new Location(world, plot.getX1(), y[0], plot.getZ1());
            Location c2 = new Location(world, plot.getX2(), y[1], plot.getZ2());
            if (!wg.redefineRegion(regionId, world, c1, c2)) {
                return "リージョンの範囲を広げられませんでした";
            }
            boolean dbUpdated;
            try {
                dbUpdated = farmPlotDAO.updateCoordinates(plot.getId(),
                        plot.getX1(), y[0], plot.getZ1(), plot.getX2(), y[1], plot.getZ2());
            } catch (SQLException e) {
                plugin.getLogger().severe("farm plot 座標更新エラー: " + e.getMessage());
                dbUpdated = false;
            }
            if (!dbUpdated) {
                // DB を直せなければ WG も元の範囲に戻し、WG と DB がずれないようにする
                wg.redefineRegion(regionId, world,
                        new Location(world, plot.getX1(), plot.getY1(), plot.getZ1()),
                        new Location(world, plot.getX2(), plot.getY2(), plot.getZ2()));
                return "DBの座標を更新できませんでした（範囲は元に戻しました）";
            }
            plot.setY1(y[0]);
            plot.setY2(y[1]);
            invalidateCache();
        }

        // 2. 親リージョン
        String parent = resolveParentRegionName(configManager.isCityProtectionEnabled(),
                configManager.getCityProtectionRegionName());
        if (parent == null) {
            plugin.getLogger().warning("都市保護が無効か親リージョン名が空のため、区画 " + regionId
                    + " に親リージョンを付けません（housing_rental.city_protection を確認してください）");
        } else if (!parent.equals(wg.getParentRegionName(regionId, world))
                && !wg.setParentRegion(regionId, parent, world)) {
            return "親リージョン '" + parent + "' を設定できませんでした";
        }

        // 3. フラグ（メンバーだけ許可）
        Map<String, String> flags = new LinkedHashMap<>();
        Map<String, String> groups = new LinkedHashMap<>();
        for (String flag : PLOT_FLAGS) {
            flags.put(flag, "allow");
            groups.put(flag, "members");
        }
        int applied = wg.applyRegionFlags(regionId, world, flags, groups);
        if (applied != flags.size()) {
            return "フラグを " + flags.size() + " 個中 " + applied + " 個しか設定できませんでした";
        }
        return null;
    }

    /**
     * 既存の全区画に保護設定を付け直す（/farmplot fixregions）。
     *
     * @return 区画名 → 失敗理由（成功した区画は値が null）。登録順
     */
    public Map<String, String> fixAllRegions() {
        Map<String, String> results = new LinkedHashMap<>();
        List<FarmPlot> plots;
        try {
            plots = farmPlotDAO.getAllPlots();
        } catch (SQLException e) {
            plugin.getLogger().severe("farm plot 一覧取得エラー: " + e.getMessage());
            return results;
        }
        for (FarmPlot plot : plots) {
            World world = Bukkit.getWorld(plot.getWorldName());
            String problem = (world == null)
                    ? "ワールド " + plot.getWorldName() + " が見つかりません"
                    : applyPlotRegionSettings(plot, world);
            results.put(plot.getPlotName(), problem);
            if (problem != null) {
                plugin.getLogger().warning("区画 " + plot.getPlotName() + " の付け直しに失敗: " + problem);
            }
        }
        invalidateCache();
        return results;
    }
}
