package org.tofu.tofunomics.tutorial;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.tofu.tofunomics.TofuNomics;

import java.util.UUID;

/**
 * チュートリアル進捗を検知するためのイベントリスナー
 * 各種イベントでチュートリアルステップの完了を検知する
 */
public class TutorialEventListener implements Listener {

    private final TofuNomics plugin;
    private final TutorialManager tutorialManager;

    public TutorialEventListener(TofuNomics plugin, TutorialManager tutorialManager) {
        this.plugin = plugin;
        this.tutorialManager = tutorialManager;
    }

    /**
     * プレイヤー退出時にキャッシュをクリア
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        tutorialManager.clearPlayerCache(event.getPlayer().getUniqueId());
    }

    /**
     * ステップ1完了: 職業就職時に呼び出される
     * JobsCommandから呼び出される
     */
    public void onJobJoined(Player player) {
        if (!tutorialManager.isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = tutorialManager.getCurrentStep(uuid);

        if (currentStep == TutorialStep.JOB_SELECT) {
            tutorialManager.advanceStep(player);
        }
    }

    /**
     * ステップ2完了: 初収入時に呼び出される
     * UnifiedEventHandlerから呼び出される
     */
    public void onFirstIncome(Player player) {
        if (!tutorialManager.isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = tutorialManager.getCurrentStep(uuid);

        if (currentStep == TutorialStep.FIRST_INCOME) {
            tutorialManager.advanceStep(player);
        }
    }

    /**
     * ステップ3完了: 銀行NPC使用時に呼び出される
     * NPCListenerから呼び出される
     */
    public void onBankNPCUsed(Player player) {
        if (!tutorialManager.isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = tutorialManager.getCurrentStep(uuid);

        if (currentStep == TutorialStep.BANK_NPC) {
            tutorialManager.advanceStep(player);
        }
    }

    /**
     * ステップ4完了: 取引NPC使用時に呼び出される
     * NPCListenerから呼び出される
     */
    public void onTradingNPCUsed(Player player) {
        if (!tutorialManager.isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        TutorialStep currentStep = tutorialManager.getCurrentStep(uuid);

        if (currentStep == TutorialStep.TRADING_NPC) {
            tutorialManager.advanceStep(player);
        }
    }
}
