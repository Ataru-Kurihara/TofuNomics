package org.tofu.tofunomics.quests;

import org.bukkit.Material;

/**
 * クエスト受注NPCで提供する1件のクエスト定義（不変オブジェクト）
 *
 * config.yml の npc_system.quest_npc.quests 配下の各エントリに対応する。
 * 敵モブの基本ドロップ（腐肉・骨・糸など）を required_amount だけ納品すると
 * reward_nuggets の金塊を受け取れる、という常設クエストを表す。
 *
 * 既存の {@link JobQuest}（職業クエスト・メモリ管理）とは別系統のため、別名のクラスとして定義する。
 */
public final class QuestDefinition {

    private final String questId;          // config の quests キー（例: rotten_flesh_1）
    private final String displayName;      // GUI 表示名
    private final String description;      // 説明文
    private final Material targetMaterial; // 納品対象アイテム
    private final int requiredAmount;      // 必要納品数
    private final int rewardNuggets;       // 報酬金塊数（1以上の整数）

    public QuestDefinition(String questId, String displayName, String description,
                           Material targetMaterial, int requiredAmount, int rewardNuggets) {
        this.questId = questId;
        this.displayName = displayName;
        this.description = description;
        this.targetMaterial = targetMaterial;
        this.requiredAmount = requiredAmount;
        this.rewardNuggets = rewardNuggets;
    }

    public String getQuestId() {
        return questId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getTargetMaterial() {
        return targetMaterial;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getRewardNuggets() {
        return rewardNuggets;
    }
}
