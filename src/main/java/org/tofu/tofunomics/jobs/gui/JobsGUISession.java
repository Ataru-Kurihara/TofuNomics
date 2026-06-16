package org.tofu.tofunomics.jobs.gui;

import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jobs GUI（職業ハブ・詳細・ステータス・確認）の開いている状態を保持するセッション。
 *
 * どの種類の GUI が開いているか、各スロットに対応する職業名、確認/詳細対象の職業名を保持し、
 * クリック時にスロットから職業を解決できるようにする。
 */
public class JobsGUISession {

    /** GUI の種別 */
    public enum Type {
        HUB,            // 職業ハブ（職業アイコングリッド＋ステータス）
        JOB_DETAIL,     // 個別職業の詳細（就職/辞職ボタン付き）
        STATS,          // 自分の職業ステータス（レベル・経験値・進捗）
        CONFIRM_JOIN,   // 就職確認ダイアログ
        CONFIRM_LEAVE   // 辞職確認ダイアログ
    }

    private final UUID playerId;
    private final Type type;
    private final Inventory inventory;

    // スロット番号 → そのスロットに表示している職業名（HUB/STATS で使用）
    private final Map<Integer, String> slotJobNames = new HashMap<>();

    // 詳細/確認画面で対象としている職業名
    private String targetJobName;

    public JobsGUISession(UUID playerId, Type type, Inventory inventory) {
        this.playerId = playerId;
        this.type = type;
        this.inventory = inventory;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Type getType() {
        return type;
    }

    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 描画時にスロット→職業名のマッピングをクリアする。
     */
    public void clearSlotJobNames() {
        slotJobNames.clear();
    }

    public void putSlotJobName(int slot, String jobName) {
        slotJobNames.put(slot, jobName);
    }

    /**
     * クリックされたスロットに対応する職業名を取得する（無ければ null）。
     */
    public String getJobNameAtSlot(int slot) {
        return slotJobNames.get(slot);
    }

    public String getTargetJobName() {
        return targetJobName;
    }

    public void setTargetJobName(String targetJobName) {
        this.targetJobName = targetJobName;
    }
}
