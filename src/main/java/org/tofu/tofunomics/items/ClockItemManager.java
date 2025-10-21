package org.tofu.tofunomics.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 時計アイテム管理システム
 * 時計アイテムの作成、配布、アクションバー表示を管理
 */
public class ClockItemManager implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private BukkitTask actionBarTask;
    
    // 時計アイテムの識別用NBTタグ代替（Lore内の特殊文字列で識別）
    private static final String CLOCK_IDENTIFIER = "§r§f§r";
    
    public ClockItemManager(TofuNomics plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * アクションバー表示タスクを開始
     */
    public void startActionBarTask() {
        if (!configManager.isClockItemEnabled() || !configManager.isClockItemActionBarEnabled()) {
            return;
        }
        
        // 既存のタスクがあればキャンセル
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        
        int interval = configManager.getClockItemActionBarUpdateInterval();
        
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasClockItem(player)) {
                    updateActionBar(player);
                }
            }
        }, 0L, interval);
        
        plugin.getLogger().info("時計アイテムのアクションバー表示タスクを開始しました");
    }
    
    /**
     * アクションバー表示タスクを停止
     */
    public void stopActionBarTask() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        plugin.getLogger().info("時計アイテムのアクションバー表示タスクを停止しました");
    }
    
    /**
     * TofuNomics時計アイテムを作成
     */
    public ItemStack createClockItem() {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        
        if (meta != null) {
            // 名前を設定
            meta.setDisplayName(configManager.getClockItemName());
            
            // Loreを設定（識別用文字列を含む）
            java.util.List<String> lore = new java.util.ArrayList<>(configManager.getClockItemLore());
            lore.add(CLOCK_IDENTIFIER); // 識別用の非表示文字列を追加
            meta.setLore(lore);
            
            // エンチャントグロウ効果
            if (configManager.isClockItemEnchanted()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            clock.setItemMeta(meta);
        }
        
        return clock;
    }
    
    /**
     * プレイヤーが時計アイテムを所持しているかチェック
     */
    public boolean hasClockItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isClockItem(item)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * アイテムが時計アイテムかどうか判定
     */
    public boolean isClockItem(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        
        // Loreに識別用文字列が含まれているかチェック
        return meta.getLore().contains(CLOCK_IDENTIFIER);
    }
    
    /**
     * プレイヤーに時計を付与
     */
    public boolean giveClockItem(Player player) {
        if (hasClockItem(player)) {
            player.sendMessage(configManager.getClockItemAlreadyOwnedMessage());
            return false;
        }
        
        ItemStack clock = createClockItem();
        player.getInventory().addItem(clock);
        player.sendMessage(configManager.getClockItemPurchaseSuccessMessage());
        return true;
    }
    
    /**
     * アクションバーを更新
     */
    private void updateActionBar(Player player) {
        long worldTime = player.getWorld().getTime();
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
        int currentMinute = (int) (((worldTime + 6000) % 1000) / 1000.0 * 60);
        String timeText = String.format("%02d:%02d", currentHour, currentMinute);
        
        // 取引ステータスと残り時間を計算
        String tradingStatus;
        String timeUntil = "";
        
        if (configManager.isTradingHoursEnabled()) {
            int startHour = configManager.getTradingStartHour();
            int endHour = configManager.getTradingEndHour();
            boolean isWithinTradingHours;
            
            if (startHour <= endHour) {
                isWithinTradingHours = currentHour >= startHour && currentHour < endHour;
            } else {
                isWithinTradingHours = currentHour >= startHour || currentHour < endHour;
            }
            
            if (isWithinTradingHours) {
                tradingStatus = configManager.getClockItemActionBarStatusOpen();
                // 閉店までの時間を計算
                int hoursUntilClose = (endHour - currentHour + 24) % 24;
                int minutesUntilClose = 60 - currentMinute;
                if (minutesUntilClose == 60) {
                    minutesUntilClose = 0;
                } else {
                    hoursUntilClose--;
                }
                timeUntil = String.format("(&e閉店まで%dh%02dm&7)", hoursUntilClose, minutesUntilClose);
            } else {
                tradingStatus = configManager.getClockItemActionBarStatusClosed();
                // 開店までの時間を計算
                int hoursUntilOpen = (startHour - currentHour + 24) % 24;
                int minutesUntilOpen = 60 - currentMinute;
                if (minutesUntilOpen == 60) {
                    minutesUntilOpen = 0;
                } else {
                    hoursUntilOpen--;
                }
                timeUntil = String.format("(&a開店まで%dh%02dm&7)", hoursUntilOpen, minutesUntilOpen);
            }
        } else {
            tradingStatus = configManager.getClockItemActionBarStatusOpen();
        }
        
        String message = configManager.getClockItemActionBarFormat()
            .replace("%time%", timeText)
            .replace("%trading_status%", tradingStatus)
            .replace("%time_until%", timeUntil);
        
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * 時計の右クリックイベント処理
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configManager.isClockItemEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 右クリックかつ時計アイテムの場合
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isClockItem(item)) {
            event.setCancelled(true);
            showClockDetails(player);
        }
    }
    
    /**
     * 時計の詳細情報を表示
     */
    private void showClockDetails(Player player) {
        long worldTime = player.getWorld().getTime();
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24);
        int currentMinute = (int) (((worldTime + 6000) % 1000) / 1000.0 * 60);
        String timeText = String.format("%02d:%02d", currentHour, currentMinute);
        
        // 取引ステータスと次のイベントまでの時間
        String tradingStatus;
        String nextEvent;
        String timeUntil;
        String tradingHours = String.format("%02d:00 - %02d:00", 
            configManager.getTradingStartHour(), 
            configManager.getTradingEndHour());
        
        if (configManager.isTradingHoursEnabled()) {
            int startHour = configManager.getTradingStartHour();
            int endHour = configManager.getTradingEndHour();
            boolean isWithinTradingHours;
            
            if (startHour <= endHour) {
                isWithinTradingHours = currentHour >= startHour && currentHour < endHour;
            } else {
                isWithinTradingHours = currentHour >= startHour || currentHour < endHour;
            }
            
            if (isWithinTradingHours) {
                tradingStatus = "&a営業中";
                nextEvent = "閉店まで";
                int hoursUntil = (endHour - currentHour + 24) % 24;
                int minutesUntil = 60 - currentMinute;
                if (minutesUntil == 60) {
                    minutesUntil = 0;
                } else {
                    hoursUntil--;
                }
                timeUntil = String.format("%d時間%02d分", hoursUntil, minutesUntil);
            } else {
                tradingStatus = "&c閉店中";
                nextEvent = "開店まで";
                int hoursUntil = (startHour - currentHour + 24) % 24;
                int minutesUntil = 60 - currentMinute;
                if (minutesUntil == 60) {
                    minutesUntil = 0;
                } else {
                    hoursUntil--;
                }
                timeUntil = String.format("%d時間%02d分", hoursUntil, minutesUntil);
            }
        } else {
            tradingStatus = "&a営業中";
            nextEvent = "次のイベント";
            timeUntil = "なし";
        }
        
        // メッセージを作成
        player.sendMessage(configManager.getClockItemDetailsTitle());
        for (String line : configManager.getClockItemDetailsContent()) {
            String formattedLine = org.bukkit.ChatColor.translateAlternateColorCodes('&', line)
                .replace("%time%", timeText)
                .replace("%trading_status%", org.bukkit.ChatColor.translateAlternateColorCodes('&', tradingStatus))
                .replace("%trading_hours%", tradingHours)
                .replace("%next_event%", nextEvent)
                .replace("%time_until%", timeUntil);
            player.sendMessage(formattedLine);
        }
        player.sendMessage(configManager.getClockItemDetailsFooter());
    }
    
    /**
     * システムを再読み込み
     */
    public void reload() {
        stopActionBarTask();
        startActionBarTask();
    }
}
