package org.tofu.tofunomics.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.farming.FarmPlotManager;
import org.tofu.tofunomics.models.FarmPlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 畑区画(プロット)管理コマンド /farmplot
 * 管理者(tofunomics.farmplot.admin)向け。
 */
public class FarmPlotCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "tofunomics.farmplot.admin";

    private final TofuNomics plugin;
    private final FarmPlotManager farmPlotManager;

    public FarmPlotCommand(TofuNomics plugin, FarmPlotManager farmPlotManager) {
        this.plugin = plugin;
        this.farmPlotManager = farmPlotManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1":
                farmPlotManager.setPos1(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(ChatColor.GREEN + "pos1 を設定しました: " + formatLoc(player));
                return true;
            case "pos2":
                farmPlotManager.setPos2(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(ChatColor.GREEN + "pos2 を設定しました: " + formatLoc(player));
                return true;
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot create <名前>");
                    return true;
                }
                farmPlotManager.createPlot(player, args[1]);
                return true;
            case "delete":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot delete <名前>");
                    return true;
                }
                farmPlotManager.deletePlot(player, args[1]);
                return true;
            case "resize":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot resize <名前>");
                    return true;
                }
                farmPlotManager.resizePlot(player, args[1]);
                return true;
            case "list":
                handleList(player);
                return true;
            case "info":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot info <名前>");
                    return true;
                }
                handleInfo(player, args[1]);
                return true;
            case "assign":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot assign <プレイヤー> [区画名]");
                    return true;
                }
                handleAssign(player, args);
                return true;
            case "release":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用法: /farmplot release <プレイヤー>");
                    return true;
                }
                handleRelease(player, args[1]);
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    private void handleList(Player player) {
        List<FarmPlot> plots = farmPlotManager.listPlots();
        if (plots.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "登録されている畑区画はありません。");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "===== 畑区画一覧 (" + plots.size() + ") =====");
        for (FarmPlot p : plots) {
            String owner = p.isAssigned() ? ownerName(p.getOwnerUuid()) : ChatColor.GRAY + "空き";
            player.sendMessage(ChatColor.YELLOW + "・" + p.getPlotName() + ChatColor.WHITE
                    + " [" + p.getWorldName() + "] " + ChatColor.WHITE + "所有: " + owner);
        }
    }

    private void handleInfo(Player player, String name) {
        FarmPlot p = farmPlotManager.getPlotByName(name);
        if (p == null) {
            player.sendMessage(ChatColor.RED + "区画 '" + name + "' は存在しません。");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "===== 区画情報: " + p.getPlotName() + " =====");
        player.sendMessage(ChatColor.YELLOW + "ワールド: " + ChatColor.WHITE + p.getWorldName());
        player.sendMessage(ChatColor.YELLOW + "範囲: " + ChatColor.WHITE
                + "(" + p.getX1() + "," + p.getY1() + "," + p.getZ1() + ") - ("
                + p.getX2() + "," + p.getY2() + "," + p.getZ2() + ")");
        player.sendMessage(ChatColor.YELLOW + "リージョン: " + ChatColor.WHITE + p.getWorldguardRegionId());
        player.sendMessage(ChatColor.YELLOW + "所有者: " + ChatColor.WHITE
                + (p.isAssigned() ? ownerName(p.getOwnerUuid()) : "空き"));
    }

    private void handleAssign(Player admin, String[] args) {
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "オンラインのプレイヤーが見つかりません: " + args[1]);
            return;
        }
        String plotName = args.length >= 3 ? args[2] : null;
        farmPlotManager.assignPlotManually(admin, target, plotName);
    }

    private void handleRelease(Player admin, String targetName) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "オンラインのプレイヤーが見つかりません: " + targetName);
            return;
        }
        farmPlotManager.releasePlotManually(admin, target);
    }

    private String ownerName(String uuid) {
        try {
            String name = plugin.getServer().getOfflinePlayer(java.util.UUID.fromString(uuid)).getName();
            return name != null ? name : uuid;
        } catch (Exception e) {
            return uuid;
        }
    }

    private String formatLoc(Player player) {
        return player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY()
                + ", " + player.getLocation().getBlockZ();
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== /farmplot 使用法 =====");
        player.sendMessage(ChatColor.YELLOW + "/farmplot pos1|pos2" + ChatColor.GRAY + " - 立っている位置を角に設定");
        player.sendMessage(ChatColor.YELLOW + "/farmplot create <名前>" + ChatColor.GRAY + " - 選択範囲で区画を作成");
        player.sendMessage(ChatColor.YELLOW + "/farmplot delete <名前>" + ChatColor.GRAY + " - 区画を削除");
        player.sendMessage(ChatColor.YELLOW + "/farmplot resize <名前>" + ChatColor.GRAY + " - 選択範囲で区画の範囲を更新（所有者保持）");
        player.sendMessage(ChatColor.YELLOW + "/farmplot list" + ChatColor.GRAY + " - 区画一覧");
        player.sendMessage(ChatColor.YELLOW + "/farmplot info <名前>" + ChatColor.GRAY + " - 区画詳細");
        player.sendMessage(ChatColor.YELLOW + "/farmplot assign <プレイヤー> [区画名]" + ChatColor.GRAY + " - 手動割り当て");
        player.sendMessage(ChatColor.YELLOW + "/farmplot release <プレイヤー>" + ChatColor.GRAY + " - 手動解放");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            List<String> subs = Arrays.asList("pos1", "pos2", "create", "delete", "resize", "list", "info", "assign", "release");
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("info") || sub.equals("resize")) {
                List<String> names = new ArrayList<>();
                for (FarmPlot p : farmPlotManager.listPlots()) {
                    if (p.getPlotName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        names.add(p.getPlotName());
                    }
                }
                return names;
            }
            if (sub.equals("assign") || sub.equals("release")) {
                List<String> players = new ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        players.add(p.getName());
                    }
                }
                return players;
            }
        }
        return new ArrayList<>();
    }
}
