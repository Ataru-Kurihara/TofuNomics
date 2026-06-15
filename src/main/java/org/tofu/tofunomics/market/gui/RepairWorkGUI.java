package org.tofu.tofunomics.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.MarketServiceRequestDAO;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.market.MarketItemSerializer;
import org.tofu.tofunomics.market.MarketManager;
import org.tofu.tofunomics.market.MarketResult;
import org.tofu.tofunomics.market.ServiceEligibility;
import org.tofu.tofunomics.models.MarketServiceRequest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 修理・エンチャント依頼の「作業GUI」（金床風の確認画面）。
 *
 * 鍛冶屋・エンチャンターが金床を所持した状態で依頼を引き受けると本GUIが開く。
 * 対象アイテムは常にシステム保持され（GUIに表示されるだけで worker の手には渡らない）、
 * 確認ボタンを押すとシステムが加工を実行する（窃盗・すり替えは原理的に不可能）。
 */
public class RepairWorkGUI {

    private static final int INVENTORY_SIZE = 27;
    private static final int SLOT_ITEM = 13;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    private final TofuNomics plugin;
    private final ConfigManager configManager;
    private final MarketManager marketManager;
    private final MarketServiceRequestDAO serviceRequestDAO;
    private final JobManager jobManager;
    private final MarketGUIListener listener;

    public RepairWorkGUI(TofuNomics plugin, ConfigManager configManager, MarketManager marketManager,
                         MarketServiceRequestDAO serviceRequestDAO, JobManager jobManager,
                         MarketGUIListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.marketManager = marketManager;
        this.serviceRequestDAO = serviceRequestDAO;
        this.jobManager = jobManager;
        this.listener = listener;
    }

    /**
     * 指定依頼の作業GUIを開く。
     */
    public void open(Player player, MarketServiceRequest req) {
        try {
            String titleType = req.isRepair() ? "修理" : "エンチャント";
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, "§6" + titleType + "工房 - 確認");
            MarketGUISession session = new MarketGUISession(
                    player.getUniqueId(), MarketGUISession.Type.SERVICE_WORK, gui);
            session.setWorkingRequestId(req.getId());
            render(gui, req);
            listener.registerSession(player.getUniqueId(), session);
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.getLogger().severe("修理作業GUIの表示に失敗しました: " + e.getMessage());
            player.sendMessage(configManager.getMarketMessage("error"));
        }
    }

    private void render(Inventory gui, MarketServiceRequest req) {
        // 対象アイテム（預かり品）を復元して中央に表示
        ItemStack item = MarketItemSerializer.deserialize(req.getItemData());
        if (item == null) {
            Material material = Material.matchMaterial(req.getMaterial());
            item = new ItemStack(material != null ? material : Material.BARRIER);
        } else {
            item = item.clone();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7依頼者: §f" + req.getRequesterName());
            if (req.isRepair()) {
                lore.add("§7内容: §b修理（耐久全回復）");
                lore.add("§7消費: §f経験値 " + configManager.getMarketServiceRepairExpCost() + " レベル");
            } else {
                int level = req.getEnchantLevel() != null ? req.getEnchantLevel() : 1;
                lore.add("§7内容: §dエンチャント §f" + req.getEnchantType() + " " + level);
                lore.add("§7消費: §f経験値 " + (configManager.getMarketServiceEnchantExpCostPerLevel() * level)
                        + " レベル ＋ ラピス " + configManager.getMarketServiceEnchantLapisCost() + " 個");
            }
            lore.add("§7引受時受取: §a" + marketManager.calculateSellerProceeds(req.getPrice())
                    + " " + configManager.getCurrencyName());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        gui.setItem(SLOT_ITEM, item);

        gui.setItem(SLOT_CONFIRM, MarketGUIUtil.createButton(Material.LIME_CONCRETE, "§a§l作業を実行する",
                Collections.singletonList("§7消費リソースを支払って加工します")));
        gui.setItem(SLOT_CANCEL, MarketGUIUtil.createButton(Material.RED_CONCRETE, "§c§lやめる",
                Collections.singletonList("§7引き受けをキャンセルします")));

        if (configManager.isGuiDecorationEnabled()) {
            ItemStack glass = MarketGUIUtil.createButton(
                    Material.GRAY_STAINED_GLASS_PANE, "§r", Collections.emptyList());
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    /**
     * クリック処理（{@link MarketGUIListener} から呼ばれる）。
     */
    public void handleClick(Player player, MarketGUISession session, int slot, ClickType clickType) {
        if (slot == SLOT_CANCEL) {
            player.closeInventory();
            return;
        }
        if (slot != SLOT_CONFIRM) {
            return;
        }

        int requestId = session.getWorkingRequestId();
        if (requestId < 0) {
            player.closeInventory();
            return;
        }

        // 依頼情報を取得（メッセージ用・存在確認）
        MarketServiceRequest req;
        try {
            req = serviceRequestDAO.getById(requestId);
        } catch (SQLException e) {
            plugin.getLogger().warning("作業対象のサービス依頼取得に失敗しました: " + e.getMessage());
            player.sendMessage(configManager.getMarketMessage("error"));
            player.closeInventory();
            return;
        }
        if (req == null || !MarketServiceRequest.STATUS_OPEN.equals(req.getStatus())) {
            player.sendMessage(configManager.getMarketMessage("service_not_available"));
            player.closeInventory();
            return;
        }

        // 資格を再確認（GUIを開いた後に職業変更・金床手放しが起きた場合の防御）
        ServiceEligibility.Result eligibility = ServiceEligibility.evaluate(player, jobManager, configManager);
        if (eligibility == ServiceEligibility.Result.WRONG_JOB) {
            player.sendMessage(configManager.getMarketMessage("service_requires_job"));
            player.closeInventory();
            return;
        }
        if (eligibility == ServiceEligibility.Result.NO_ANVIL) {
            player.sendMessage(configManager.getMarketMessage("service_requires_anvil"));
            player.closeInventory();
            return;
        }

        MarketResult result = marketManager.fulfillServiceRequest(player, requestId);
        sendResultMessage(player, result, req);
        player.closeInventory();
    }

    private void sendResultMessage(Player player, MarketResult result, MarketServiceRequest req) {
        player.sendMessage(configManager.getMarketMessage(result.getMessageKey(),
                "item", MarketGUIUtil.prettifyMaterial(req.getMaterial()),
                "service", req.isRepair() ? "修理" : "エンチャント",
                "price", MarketGUIUtil.formatPrice(req.getPrice()),
                "proceeds", String.valueOf(marketManager.calculateSellerProceeds(req.getPrice())),
                "currency", configManager.getCurrencyName()));
    }
}
