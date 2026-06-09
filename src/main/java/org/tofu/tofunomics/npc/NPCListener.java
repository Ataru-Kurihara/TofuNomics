package org.tofu.tofunomics.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NPCListener implements Listener {
    
    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final NPCManager npcManager;
    private final BankNPCManager bankNPCManager;
    private final TradingNPCManager tradingNPCManager;
    private final FoodNPCManager foodNPCManager;
    private final ProcessingNPCManager processingNPCManager;
    
    // プレイヤーの取引状態を管理
    private final Map<UUID, TradingSession> activeTradingSessions = new ConcurrentHashMap<>();
    
    public NPCListener(TofuNomics plugin, ConfigManager configManager, NPCManager npcManager,
                     BankNPCManager bankNPCManager, TradingNPCManager tradingNPCManager, 
                     FoodNPCManager foodNPCManager, ProcessingNPCManager processingNPCManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcManager = npcManager;
        this.bankNPCManager = bankNPCManager;
        this.tradingNPCManager = tradingNPCManager;
        this.foodNPCManager = foodNPCManager;
        this.processingNPCManager = processingNPCManager;
    }
    
    private static class TradingSession {
        private final UUID npcId;
        private final long lastInteractionTime;
        private final String sessionType;
        
        public TradingSession(UUID npcId, String sessionType) {
            this.npcId = npcId;
            this.sessionType = sessionType;
            this.lastInteractionTime = System.currentTimeMillis();
        }
        
        public UUID getNpcId() { return npcId; }
        public String getSessionType() { return sessionType; }
        public long getLastInteractionTime() { return lastInteractionTime; }
        
        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - lastInteractionTime > timeoutMs;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.VILLAGER) {
            return;
        }
        
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        UUID npcId = villager.getUniqueId();
        
        // システムのNPCかチェック
        if (!npcManager.isNPCEntity(npcId)) {
            return;
        }

        event.setCancelled(true); // デフォルトの村人取引を無効化

        // ワールド制限チェック（経済機能が無効なワールドではNPC機能を提供しない）
        if (!configManager.isEconomyEnabledInWorld(player.getWorld().getName())) {
            return;
        }

        try {
            NPCManager.NPCData npcData = npcManager.getNPCData(npcId);
            if (npcData == null) {
                plugin.getLogger().warning("NPCデータが見つかりません: " + npcId);
                return;
            }

            // プレイヤーの方を向く機能（設定で有効な場合）
            if (configManager.isLookAtPlayerEnabled()) {
                makeNPCLookAtPlayer(villager, player);
            }
            
            switch (npcData.getNpcType()) {
                case "banker":
                    handleBankNPCInteraction(player, npcId);
                    break;

                case "trader":
                    handleTradingNPCInteraction(player, npcId);
                    break;

                case "food_merchant":
                    handleFoodNPCInteraction(player, npcId);
                    break;

                case "processing":
                    handleProcessingNPCInteraction(player, npcId);
                    break;

                default:
                    plugin.getLogger().warning("不明なNPCタイプ: " + npcData.getNpcType());
                    player.sendMessage(configManager.getMessage("npc.unknown_type"));
                    break;
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("NPC取引処理中にエラーが発生しました: " + e.getMessage());
            player.sendMessage(configManager.getMessage("npc.service_error"));
        }
    }
    
    private void handleBankNPCInteraction(Player player, UUID npcId) {
        // 取引時間制限チェック
        if (!isWithinTradingHours(player)) {
            player.sendMessage(configManager.getMessage("npc.bank.outside_hours"));
            return;
        }
        
        // クールダウンチェック
        if (hasRecentInteraction(player.getUniqueId(), "banker")) {
            player.sendMessage(configManager.getMessage("npc.bank.cooldown_active"));
            return;
        }
        
        boolean handled = bankNPCManager.handleBankNPCInteraction(player, npcId);
        if (handled) {
            // 取引セッションを記録
            activeTradingSessions.put(player.getUniqueId(), new TradingSession(npcId, "banker"));
        }
    }
    
    private void handleTradingNPCInteraction(Player player, UUID npcId) {
        // クールダウンチェック
        if (hasRecentInteraction(player.getUniqueId(), "trader")) {
            player.sendMessage(configManager.getMessage("npc.trading.cooldown_active"));
            return;
        }
        
        // 取引NPCインターフェース（モード選択GUI）を表示
        // ※ 自動全売却は廃止。売却はGUI内のアイテム選択／全売却ボタンからのみ実行する
        //   （右クリック連打による意図しない全アイテム売却バグを防止するため）
        boolean handled = tradingNPCManager.handleTradingNPCInteraction(player, npcId);
        if (handled) {
            // クールダウン管理用にセッションを記録
            activeTradingSessions.put(player.getUniqueId(), new TradingSession(npcId, "trader"));
        }
    }
    
    /**
     * 加工NPC相互作用の処理
     */
    private void handleProcessingNPCInteraction(Player player, UUID npcId) {
        // クールダウンチェック
        if (hasRecentInteraction(player.getUniqueId(), "processing")) {
            player.sendMessage("§c少し待ってからもう一度お試しください。");
            return;
        }
        
        try {
            if (processingNPCManager != null) {
                boolean handled = processingNPCManager.handleProcessingNPCInteraction(player, npcId);
                if (handled) {
                    // 取引セッションを記録
                    activeTradingSessions.put(player.getUniqueId(), new TradingSession(npcId, "processing"));
                } else {
                    plugin.getLogger().warning("加工NPC相互作用の処理に失敗: " + npcId);
                    player.sendMessage("§c加工NPCとの処理中にエラーが発生しました。");
                }
            } else {
                plugin.getLogger().severe("ProcessingNPCManagerが初期化されていません");
                player.sendMessage("§c加工NPCシステムが利用できません。管理者にお知らせください。");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("加工NPC相互作用処理中に例外が発生しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。管理者にお知らせください。");
        }
    }
    
    /**
     * 食料NPC相互作用の処理
     */
    private void handleFoodNPCInteraction(Player player, UUID npcId) {
        // クールダウンチェック
        if (hasRecentInteraction(player.getUniqueId(), "food_merchant")) {
            player.sendMessage("§c少し待ってからもう一度お試しください。");
            return;
        }
        
        try {
            if (foodNPCManager != null) {
                boolean handled = foodNPCManager.handleFoodNPCInteraction(player, npcId);
                if (handled) {
                    // 取引セッションを記録
                    activeTradingSessions.put(player.getUniqueId(), new TradingSession(npcId, "food_merchant"));
                } else {
                    plugin.getLogger().warning("食料NPC相互作用の処理に失敗: " + npcId);
                    plugin.getLogger().warning("NPCがFoodNPCManagerに登録されていない可能性があります");
                    player.sendMessage("§c食料NPCとの取引中にエラーが発生しました。");
                    player.sendMessage("§7管理者に報告してください。NPC ID: " + npcId);
                }
            } else {
                plugin.getLogger().severe("FoodNPCManagerが初期化されていません");
                player.sendMessage("§c食料NPCシステムが利用できません。管理者にお知らせください。");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("食料NPC相互作用処理中に例外が発生しました: " + e.getMessage());
            player.sendMessage("§c処理中にエラーが発生しました。管理者にお知らせください。");
        }
    }
    
    private boolean isWithinTradingHours(Player player) {
        if (!configManager.isTradingHoursEnabled()) {
            return true;
        }
        
        // 現在の時間を取得（プレイヤーがいるワールドのMinecraft時間）
        long worldTime = player.getWorld().getTime();
        int currentHour = (int) (((worldTime + 6000) / 1000) % 24); // 6:00を基準とした24時間制
        
        int startHour = configManager.getTradingStartHour();
        int endHour = configManager.getTradingEndHour();
        
        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour < endHour;
        } else {
            // 日をまたぐ場合
            return currentHour >= startHour || currentHour < endHour;
        }
    }
    
    private boolean hasRecentInteraction(UUID playerId, String npcType) {
        TradingSession session = activeTradingSessions.get(playerId);
        if (session == null) {
            return false;
        }
        
        long cooldownMs = configManager.getNPCInteractionCooldownMs();
        return session.getSessionType().equals(npcType) && !session.isExpired(cooldownMs);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getType() != EntityType.VILLAGER) {
            return;
        }
        
        UUID entityId = event.getEntity().getUniqueId();
        if (npcManager.isNPCEntity(entityId)) {
            event.setCancelled(true); // システムNPCへのダメージを無効化
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getType() != EntityType.VILLAGER) {
            return;
        }
        
        UUID entityId = event.getEntity().getUniqueId();
        if (npcManager.isNPCEntity(entityId)) {
            event.setCancelled(true); // システムNPCへの攻撃を無効化
            
            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getDamager();
                if (player.isOp()) {
                    // 管理者にはNPC情報を表示
                    NPCManager.NPCData npcData = npcManager.getNPCData(entityId);
                    if (npcData != null) {
                        player.sendMessage("§6[管理者] NPC情報: " + npcData.getName() + 
                                         " (" + npcData.getNpcType() + ")");
                    }
                }
            }
        }
    }
    
    /**
     * NPCをプレイヤーの方に向ける
     */
    private void makeNPCLookAtPlayer(Villager npc, Player player) {
        Location npcLoc = npc.getLocation();
        Location playerLoc = player.getLocation();
        
        // プレイヤーの方向を計算
        double dx = playerLoc.getX() - npcLoc.getX();
        double dz = playerLoc.getZ() - npcLoc.getZ();
        double dy = playerLoc.getY() - npcLoc.getY();
        
        // yaw（水平方向の向き）を計算
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        
        // pitch（垂直方向の向き）を計算
        double distance = Math.sqrt(dx * dx + dz * dz);
        double pitch = Math.toDegrees(Math.atan(-dy / distance));
        
        // NPCの向きを設定
        Location newLoc = npcLoc.clone();
        newLoc.setYaw((float) yaw);
        newLoc.setPitch((float) pitch);
        npc.teleport(newLoc);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤーがログアウトしたら取引セッションを削除
        UUID playerId = event.getPlayer().getUniqueId();
        activeTradingSessions.remove(playerId);
    }
    
    /**
     * チャンクロード時にNPC設定を再適用（サーバー再起動後の復元）
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // チャンク内のシステムNPCを「タイプ+整数座標」でグルーピング
        // （サーバー再起動でNBT復元された旧NPCと新規NPCが重複するため、ここで排除する）
        Map<String, List<Villager>> npcsByLocation = new HashMap<>();
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                if (npcManager.isSystemNPC(villager)) {
                    String key = getNPCLocationKey(villager);
                    npcsByLocation.computeIfAbsent(key, k -> new ArrayList<>()).add(villager);
                }
            }
        }

        // 同一キーに複数体ある場合は1体だけ残して残りを削除
        for (Map.Entry<String, List<Villager>> entry : npcsByLocation.entrySet()) {
            List<Villager> duplicates = entry.getValue();
            Villager keep = selectNPCToKeep(duplicates);

            for (Villager villager : duplicates) {
                if (villager == keep) {
                    continue;
                }
                plugin.getLogger().info("重複NPCを削除: " + villager.getCustomName() +
                    " at " + entry.getKey());
                villager.remove();
            }

            // 残った1体にのみ設定を再適用
            if (keep != null) {
                npcManager.reapplyNPCSettings(keep);
            }
        }
    }

    /**
     * NPCの重複判定用キーを生成（タイプ + 整数化したブロック座標）
     */
    private String getNPCLocationKey(Villager villager) {
        Location loc = villager.getLocation();
        String npcType = "unknown";
        org.bukkit.persistence.PersistentDataContainer pdc = villager.getPersistentDataContainer();
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "npc_type");
        if (pdc.has(typeKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String type = pdc.get(typeKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (type != null) {
                npcType = type;
            }
        } else if (villager.hasMetadata("tofunomics_npc_type")) {
            npcType = villager.getMetadata("tofunomics_npc_type").get(0).asString();
        }
        return npcType + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * 重複NPC群から残す1体を選定する
     * 内部マップ（npcs）に登録済み=今回の起動で正規生成された個体を最優先で残す
     */
    private Villager selectNPCToKeep(List<Villager> duplicates) {
        for (Villager villager : duplicates) {
            if (npcManager.getNPCData(villager.getUniqueId()) != null) {
                return villager;
            }
        }
        // 正規個体が見つからなければ最初の1体を残す
        return duplicates.isEmpty() ? null : duplicates.get(0);
    }

    /**
     * 村人インベントリが開くのを防ぐ（システムNPCの場合）
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        // MERCHANTタイプ（村人取引）のインベントリをチェック
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            // インベントリのホルダーが村人かチェック
            if (event.getInventory().getHolder() instanceof Villager) {
                Villager villager = (Villager) event.getInventory().getHolder();
                
                // システムNPCかチェック
                if (npcManager.isNPCEntity(villager.getUniqueId())) {
                    event.setCancelled(true);
                    plugin.getLogger().fine("システムNPCの村人取引インベントリをブロックしました: " + villager.getUniqueId());
                }
            }
        }
    }

    /**
     * カスタムGUIが閉じられた時にセッションをクリーンアップ
     */
    @EventHandler
    public void onCustomGUIClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        
        // カスタムGUI（取引、食料品店、加工所、銀行）が閉じられた場合、セッションをクリア
        if (title != null && (title.contains("取引") || title.contains("食料品店") || 
            title.contains("加工所") || title.contains("銀行"))) {
            
            TradingSession session = activeTradingSessions.remove(player.getUniqueId());
            if (session != null) {
                plugin.getLogger().fine("NPCセッションをクリーンアップしました: " + player.getName() + " (GUI: " + title + ")");
            }
        }
    }
    
    // 定期的にタイムアウトしたセッションをクリーンアップ
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        long timeoutMs = configManager.getNPCInteractionTimeoutMs();
        
        activeTradingSessions.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(timeoutMs));
    }
    
    public int getActiveSessionsCount() {
        return activeTradingSessions.size();
    }
    
    public Collection<UUID> getActiveSessionPlayerIds() {
        return activeTradingSessions.keySet();
    }
}