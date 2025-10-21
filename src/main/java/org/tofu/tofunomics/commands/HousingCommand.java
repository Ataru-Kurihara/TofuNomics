package org.tofu.tofunomics.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.housing.HousingRentalManager;
import org.tofu.tofunomics.housing.SelectionManager;
import org.tofu.tofunomics.models.HousingProperty;
import org.tofu.tofunomics.models.HousingRental;
import org.tofu.tofunomics.testing.TestModeManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 住居賃貸システムのコマンド
 */
public class HousingCommand implements CommandExecutor, TabCompleter {
    private final TofuNomics plugin;
    private final HousingRentalManager rentalManager;
    private final SelectionManager selectionManager;
    private final TestModeManager testModeManager;

    public HousingCommand(TofuNomics plugin, HousingRentalManager rentalManager, SelectionManager selectionManager, TestModeManager testModeManager) {
        this.plugin = plugin;
        this.rentalManager = rentalManager;
        this.selectionManager = selectionManager;
        this.testModeManager = testModeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // 管理者コマンド
        if (subCommand.equals("admin")) {
            // テストモードを考慮した権限チェック
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!testModeManager.hasEffectivePermission(player, "tofunomics.housing.admin")) {
                    sender.sendMessage("§c権限がありません");
                    return true;
                }
            } else if (!sender.hasPermission("tofunomics.housing.admin")) {
                sender.sendMessage("§c権限がありません");
                return true;
            }
            return handleAdminCommand(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        // プレイヤーコマンド
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }

        Player player = (Player) sender;

        switch (subCommand) {
            case "list":
                return handleListCommand(player);
            case "info":
                return handleInfoCommand(player, args);
            case "rent":
                return handleRentCommand(player, args);
            case "myrentals":
                return handleMyRentalsCommand(player);
            case "extend":
                return handleExtendCommand(player, args);
            case "cancel":
                return handleCancelCommand(player, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    /**
     * 賃貸可能物件一覧
     */
    private boolean handleListCommand(Player player) {
        List<HousingProperty> properties = rentalManager.getAvailableProperties();
        
        if (properties.isEmpty()) {
            player.sendMessage("§e現在賃貸可能な物件はありません");
            return true;
        }

        player.sendMessage("§6========= 賃貸可能物件一覧 =========");
        for (HousingProperty property : properties) {
            player.sendMessage(String.format(
                "§e#%d §f%s §7- 日額: §a%.1f §7/ 週額: §a%.1f §7/ 月額: §a%.1f",
                property.getId(),
                property.getPropertyName(),
                property.getDailyRent(),
                property.getWeeklyRent(),
                property.getMonthlyRent()
            ));
        }
        player.sendMessage("§6=====================================");
        player.sendMessage("§7詳細: /housing info <物件ID>");
        
        return true;
    }

    /**
     * 物件詳細情報
     */
    private boolean handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c使用法: /housing info <物件ID>");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);
            HousingProperty property = rentalManager.getProperty(propertyId);
            
            if (property == null) {
                player.sendMessage("§c物件が見つかりません");
                return true;
            }

            player.sendMessage("§6========= 物件情報 =========");
            player.sendMessage("§eID: §f" + property.getId());
            player.sendMessage("§e名前: §f" + property.getPropertyName());
            player.sendMessage("§eワールド: §f" + property.getWorldName());
            if (property.getDescription() != null) {
                player.sendMessage("§e説明: §f" + property.getDescription());
            }
            player.sendMessage("§e賃料: ");
            player.sendMessage("  §7日額: §a" + property.getDailyRent());
            player.sendMessage("  §7週額: §a" + property.getWeeklyRent());
            player.sendMessage("  §7月額: §a" + property.getMonthlyRent());
            player.sendMessage("§e状態: " + (property.isAvailable() ? "§a利用可能" : "§c賃貸中"));
            player.sendMessage("§6============================");
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c物件IDは数字で指定してください");
        }
        
        return true;
    }

    /**
     * 物件を借りる
     */
    private boolean handleRentCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c使用法: /housing rent <物件ID> <日数|週数w|月数m>");
            player.sendMessage("§7例: /housing rent 1 7  (7日間)");
            player.sendMessage("§7例: /housing rent 1 2w (2週間)");
            player.sendMessage("§7例: /housing rent 1 1m (1ヶ月)");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);
            String periodInput = args[2].toLowerCase();
            
            // 期間のパース
            String period;
            int units;
            
            if (periodInput.endsWith("w")) {
                period = "weekly";
                units = Integer.parseInt(periodInput.substring(0, periodInput.length() - 1));
            } else if (periodInput.endsWith("m")) {
                period = "monthly";
                units = Integer.parseInt(periodInput.substring(0, periodInput.length() - 1));
            } else {
                period = "daily";
                units = Integer.parseInt(periodInput);
            }

            HousingRentalManager.RentalResult result = rentalManager.rentProperty(
                player.getUniqueId(), propertyId, period, units
            );

            if (result.isSuccess()) {
                player.sendMessage("§a" + result.getMessage());
            } else {
                player.sendMessage("§c" + result.getMessage());
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c数値の形式が正しくありません");
        }
        
        return true;
    }

    /**
     * 自分の賃貸契約一覧
     */
    private boolean handleMyRentalsCommand(Player player) {
        List<HousingRental> rentals = rentalManager.getPlayerRentals(player.getUniqueId());
        
        if (rentals.isEmpty()) {
            player.sendMessage("§e現在賃貸中の物件はありません");
            return true;
        }

        // TofuNomicsワールドを取得
        String worldName = plugin.getConfig().getString("housing_rental.world_name", "world");
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        
        player.sendMessage("§6========= 賃貸契約一覧 =========");
        for (HousingRental rental : rentals) {
            HousingProperty property = rentalManager.getProperty(rental.getPropertyId());
            if (property != null) {
                player.sendMessage(String.format(
                    "§e物件: §f%s §7(#%d)",
                    property.getPropertyName(),
                    property.getId()
                ));
                
                if (world != null) {
                    String formattedTime = rental.getFormattedRemainingTime(world);
                    player.sendMessage(String.format(
                        "  §7残り: §e%s §7| 費用: §a%.1f",
                        formattedTime,
                        rental.getTotalCost()
                    ));
                } else {
                    player.sendMessage(String.format(
                        "  §7費用: §a%.1f",
                        rental.getTotalCost()
                    ));
                }
            }
        }
        player.sendMessage("§6================================");
        
        return true;
    }

    /**
     * 契約延長
     */
    private boolean handleExtendCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c使用法: /housing extend <物件ID> <追加日数>");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);
            int additionalDays = Integer.parseInt(args[2]);

            HousingRentalManager.RentalResult result = rentalManager.extendRental(
                player.getUniqueId(), propertyId, additionalDays
            );

            if (result.isSuccess()) {
                player.sendMessage("§a" + result.getMessage());
            } else {
                player.sendMessage("§c" + result.getMessage());
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c数値の形式が正しくありません");
        }
        
        return true;
    }

    /**
     * 契約キャンセル
     */
    private boolean handleCancelCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c使用法: /housing cancel <物件ID>");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);

            HousingRentalManager.RentalResult result = rentalManager.cancelRental(
                player.getUniqueId(), propertyId
            );

            if (result.isSuccess()) {
                player.sendMessage("§a" + result.getMessage());
            } else {
                player.sendMessage("§c" + result.getMessage());
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c物件IDは数字で指定してください");
        }
        
        return true;
    }

    /**
     * 管理者コマンド処理
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendAdminUsage(sender);
            return true;
        }

        String adminSubCommand = args[0].toLowerCase();

        switch (adminSubCommand) {
            case "register":
                return handleAdminRegister(sender, args);
            case "list":
                return handleAdminList(sender);
            case "setrent":
                return handleAdminSetRent(sender, args);
            case "remove":
                return handleAdminRemove(sender, args);
            case "createcityregion":
                return handleAdminCreateCityRegion(sender, args);
            case "setparent":
                return handleAdminSetParent(sender, args);
            default:
                sendAdminUsage(sender);
                return true;
        }
    }

    /**
     * 管理者: 物件登録
     */
    private boolean handleAdminRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 3) {
            player.sendMessage("§c使用法: /housing admin register <物件名> <日額賃料> [--wg <領域名>]");
            player.sendMessage("§7事前に木の斧で範囲選択してください");
            player.sendMessage("§7--wgオプションで自動的にWorldGuard領域を作成できます");
            return true;
        }

        if (!selectionManager.hasCompleteSelection(player)) {
            player.sendMessage("§c範囲選択が完了していません");
            player.sendMessage("§7木の斧で2点を選択してください");
            return true;
        }

        try {
            String propertyName = args[1];
            double dailyRent = Double.parseDouble(args[2]);
            
            // --wgオプションをチェック
            String worldGuardRegionId = null;
            for (int i = 3; i < args.length - 1; i++) {
                if (args[i].equalsIgnoreCase("--wg")) {
                    worldGuardRegionId = args[i + 1];
                    break;
                }
            }

            org.bukkit.Location pos1 = selectionManager.getFirstPosition(player.getUniqueId());
            org.bukkit.Location pos2 = selectionManager.getSecondPosition(player.getUniqueId());

            // HousingPropertyを作成
            HousingProperty property = new HousingProperty(
                propertyName,
                pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ(),
                null,
                dailyRent
            );

            // WorldGuard領域を作成（オプション指定時）
            if (worldGuardRegionId != null) {
                boolean regionCreated = rentalManager.createWorldGuardRegion(
                    worldGuardRegionId, 
                    player.getWorld(), 
                    pos1, 
                    pos2
                );
                
                if (regionCreated) {
                    property.setWorldguardRegionId(worldGuardRegionId);
                    player.sendMessage("§aWorldGuard領域 '" + worldGuardRegionId + "' を作成しました");
                } else {
                    player.sendMessage("§cWorldGuard領域の作成に失敗しました");
                    player.sendMessage("§7座標範囲のみで物件を登録しますか？ (Y/N)");
                    // 簡易的に続行
                }
            }

            HousingRentalManager.RentalResult result = rentalManager.registerProperty(property);

            if (result.isSuccess()) {
                player.sendMessage("§a" + result.getMessage());
                if (worldGuardRegionId != null) {
                    player.sendMessage("§aWorldGuard統合完了: プレイヤーが借りると自動的にドアアクセス権が付与されます");
                }
                selectionManager.clearSelection(player);
            } else {
                player.sendMessage("§c" + result.getMessage());
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§c賃料は数値で指定してください");
        }

        return true;
    }

    /**
     * 管理者: 物件一覧
     */
    private boolean handleAdminList(CommandSender sender) {
        List<HousingProperty> properties = rentalManager.getAvailableProperties();
        
        sender.sendMessage("§6========= 全物件一覧 =========");
        for (HousingProperty property : properties) {
            sender.sendMessage(String.format(
                "§e#%d §f%s §7- 日額: §a%.1f §7(%s)",
                property.getId(),
                property.getPropertyName(),
                property.getDailyRent(),
                property.isAvailable() ? "利用可能" : "賃貸中"
            ));
        }
        sender.sendMessage("§6==============================");
        
        return true;
    }

    /**
     * 管理者: 賃料変更
     */
    private boolean handleAdminSetRent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c使用法: /housing admin setrent <物件ID> <新日額>");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);
            double newDailyRent = Double.parseDouble(args[2]);

            HousingProperty property = rentalManager.getProperty(propertyId);
            if (property == null) {
                sender.sendMessage("§c物件が見つかりません");
                return true;
            }

            property.setDailyRent(newDailyRent);
            // TODO: propertyDAO.updateProperty(property) を呼び出す処理をHousingRentalManagerに追加

            sender.sendMessage("§a物件#" + propertyId + "の日額賃料を" + newDailyRent + "に変更しました");

        } catch (NumberFormatException e) {
            sender.sendMessage("§c数値の形式が正しくありません");
        }

        return true;
    }

    /**
     * 管理者: 物件削除
     */
    private boolean handleAdminRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /housing admin remove <物件ID>");
            return true;
        }

        try {
            int propertyId = Integer.parseInt(args[1]);
            
            HousingRentalManager.RentalResult result = rentalManager.removeProperty(propertyId);
            
            if (result.isSuccess()) {
                sender.sendMessage("§a" + result.getMessage());
            } else {
                sender.sendMessage("§c" + result.getMessage());
            }

        } catch (NumberFormatException e) {
            sender.sendMessage("§c物件IDは数字で指定してください");
        }

        return true;
    }


    /**
     * 管理者: 都市保護リージョン作成
     */
    private boolean handleAdminCreateCityRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§c使用法: /housing admin createcityregion <リージョン名>");
            player.sendMessage("§7事前に木の斧で範囲選択してください");
            player.sendMessage("§7このリージョンが都市全体を保護します（ブロック破壊・設置禁止）");
            return true;
        }

        if (!selectionManager.hasCompleteSelection(player)) {
            player.sendMessage("§c範囲選択が完了していません");
            player.sendMessage("§7木の斧で2点を選択してください");
            return true;
        }

        String regionName = args[1];
        org.bukkit.Location pos1 = selectionManager.getFirstPosition(player.getUniqueId());
        org.bukkit.Location pos2 = selectionManager.getSecondPosition(player.getUniqueId());

        // WorldGuardリージョンを作成
        boolean regionCreated = rentalManager.createWorldGuardRegion(
            regionName,
            player.getWorld(),
            pos1,
            pos2
        );

        if (!regionCreated) {
            player.sendMessage("§cWorldGuardリージョンの作成に失敗しました");
            return true;
        }

        // BUILDフラグとUSEフラグをDENYに設定
        org.tofu.tofunomics.integration.WorldGuardIntegration wgIntegration = 
            plugin.getWorldGuardIntegration();
        
        if (wgIntegration != null && wgIntegration.isEnabled()) {
            boolean buildFlagSet = wgIntegration.setRegionFlag(regionName, player.getWorld(), "build", "deny");
            boolean useFlagSet = wgIntegration.setRegionFlag(regionName, player.getWorld(), "use", "deny");
            
            if (buildFlagSet && useFlagSet) {
                player.sendMessage("§a都市保護リージョン '" + regionName + "' を作成しました");
                player.sendMessage("§aBUILDフラグとUSEフラグをDENYに設定しました");
                player.sendMessage("§7次に '/housing admin setparent " + regionName + "' で既存物件を子リージョンに設定してください");
                selectionManager.clearSelection(player);
            } else {
                player.sendMessage("§cリージョンは作成されましたが、フラグの設定に失敗しました");
            }
        } else {
            player.sendMessage("§cWorldGuard統合が無効です");
        }

        return true;
    }

    /**
     * 管理者: 既存物件を親リージョンの子に設定
     */
    private boolean handleAdminSetParent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c使用法: /housing admin setparent <親リージョン名>");
            sender.sendMessage("§7全ての既存賃貸物件のリージョンを指定した親リージョンの子に設定します");
            return true;
        }

        String parentRegionName = args[1];

        // config.ymlから親リージョン設定を確認
        String configParent = plugin.getConfig().getString("housing_rental.city_protection.region_name", "");
        if (!configParent.isEmpty() && !configParent.equals(parentRegionName)) {
            sender.sendMessage("§e警告: config.ymlの設定と異なる親リージョン名です");
            sender.sendMessage("§7config.yml: " + configParent);
            sender.sendMessage("§7指定値: " + parentRegionName);
        }

        // 全物件を取得
        List<HousingProperty> properties = rentalManager.getAvailableProperties();
        
        if (properties.isEmpty()) {
            sender.sendMessage("§e設定可能な物件がありません");
            return true;
        }

        org.tofu.tofunomics.integration.WorldGuardIntegration wgIntegration = 
            plugin.getWorldGuardIntegration();

        if (wgIntegration == null || !wgIntegration.isEnabled()) {
            sender.sendMessage("§cWorldGuard統合が無効です");
            return true;
        }

        int successCount = 0;
        int skipCount = 0;

        for (HousingProperty property : properties) {
            if (!property.hasWorldGuardRegion()) {
                skipCount++;
                continue;
            }

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(property.getWorldName());
            if (world == null) {
                skipCount++;
                continue;
            }

            boolean result = wgIntegration.setParentRegion(
                property.getWorldguardRegionId(),
                parentRegionName,
                world
            );

            if (result) {
                successCount++;
            } else {
                skipCount++;
            }
        }

        sender.sendMessage("§a親リージョン設定完了");
        sender.sendMessage("§7成功: " + successCount + " 件");
        if (skipCount > 0) {
            sender.sendMessage("§7スキップ: " + skipCount + " 件 (WorldGuardリージョン未設定またはエラー)");
        }

        return true;
    }

    /**
     * 使用法を表示
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6===== 住居賃貸コマンド =====");
        sender.sendMessage("§e/housing list §7- 賃貸可能物件一覧");
        sender.sendMessage("§e/housing info <ID> §7- 物件詳細");
        sender.sendMessage("§e/housing rent <ID> <期間> §7- 物件を借りる");
        sender.sendMessage("§e/housing myrentals §7- 自分の契約一覧");
        sender.sendMessage("§e/housing extend <ID> <日数> §7- 契約延長");
        sender.sendMessage("§e/housing cancel <ID> §7- 契約キャンセル");
        // テストモードを考慮した権限チェック
        boolean hasAdminPerm = false;
        if (sender instanceof Player) {
            hasAdminPerm = testModeManager.hasEffectivePermission((Player) sender, "tofunomics.housing.admin");
        } else {
            hasAdminPerm = sender.hasPermission("tofunomics.housing.admin");
        }
        if (hasAdminPerm) {
            sender.sendMessage("§e/housing admin §7- 管理者コマンド");
        }
        sender.sendMessage("§6===========================");
    }

    /**
     * 管理者コマンド使用法を表示
     */
    private void sendAdminUsage(CommandSender sender) {
        sender.sendMessage("§6===== 住居管理コマンド =====");
        sender.sendMessage("§e/housing admin register <名前> <日額> [--wg <領域名>] §7- 物件登録");
        sender.sendMessage("§e/housing admin list §7- 全物件一覧");
        sender.sendMessage("§e/housing admin setrent <ID> <日額> §7- 賃料変更");
        sender.sendMessage("§e/housing admin remove <ID> §7- 物件削除");
        sender.sendMessage("§e/housing admin createcityregion <名前> §7- 都市保護リージョン作成");
        sender.sendMessage("§e/housing admin setparent <親リージョン名> §7- 全物件を子リージョンに設定");
        sender.sendMessage("§6===========================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("list", "info", "rent", "myrentals", "extend", "cancel"));
            // テストモードを考慮した権限チェック
            boolean hasAdminPerm = false;
            if (sender instanceof Player) {
                hasAdminPerm = testModeManager.hasEffectivePermission((Player) sender, "tofunomics.housing.admin");
            } else {
                hasAdminPerm = sender.hasPermission("tofunomics.housing.admin");
            }
            if (hasAdminPerm) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            completions.addAll(Arrays.asList("register", "list", "setrent", "remove"));
        }

        return completions;
    }
}
