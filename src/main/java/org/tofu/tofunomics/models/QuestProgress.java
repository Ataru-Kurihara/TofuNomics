package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * クエスト受注NPCの受注状態を表すモデルクラス
 *
 * 「集めて納品」形式のため進捗カラムは持たず、納品時のインベントリ実数で都度判定する。
 * 保持するのは「どのクエストを受注中か」という受注状態のみ。
 * 完了後の再受注は completed 行を削除して accepted を再 insert する方式で許可する。
 */
public class QuestProgress {

    // 受注ステータス定数
    public static final String STATUS_ACCEPTED = "accepted";    // 受注中（納品待ち）
    public static final String STATUS_COMPLETED = "completed";  // 納品完了（履歴用途）

    private int id;
    private UUID playerUuid;
    private String questId;          // config の quests キー
    private String status;
    private Timestamp acceptedAt;
    private Timestamp completedAt;   // 完了時刻（nullable）

    public QuestProgress() {
    }

    /**
     * 新規受注用コンストラクタ（id・acceptedAt は INSERT 時/DB側で確定）
     */
    public QuestProgress(UUID playerUuid, String questId) {
        this.playerUuid = playerUuid;
        this.questId = questId;
        this.status = STATUS_ACCEPTED;
    }

    /**
     * 受注中（納品可能）かどうか
     */
    public boolean isAccepted() {
        return STATUS_ACCEPTED.equals(status);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Timestamp acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }
}
