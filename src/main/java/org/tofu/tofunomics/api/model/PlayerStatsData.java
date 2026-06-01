package org.tofu.tofunomics.api.model;

/**
 * tohu-app API送信用のプレイヤー統計データモデル
 * PUT /api/mc/stats/upsert のリクエストボディに対応
 */
public class PlayerStatsData {
    // フィールド名はAPI仕様に合わせてスネークケース
    private String player_uuid;
    private String player_name;
    private int world_id;
    private String stat_type;
    private String stat_date;
    private long value;

    /**
     * デフォルトコンストラクタ
     */
    public PlayerStatsData() {
    }

    /**
     * 全フィールドを指定するコンストラクタ
     */
    public PlayerStatsData(String playerUuid, String playerName, int worldId,
                           String statType, String statDate, long value) {
        this.player_uuid = playerUuid;
        this.player_name = playerName;
        this.world_id = worldId;
        this.stat_type = statType;
        this.stat_date = statDate;
        this.value = value;
    }

    // ゲッター・セッター

    public String getPlayer_uuid() {
        return player_uuid;
    }

    public void setPlayer_uuid(String player_uuid) {
        this.player_uuid = player_uuid;
    }

    public String getPlayer_name() {
        return player_name;
    }

    public void setPlayer_name(String player_name) {
        this.player_name = player_name;
    }

    public int getWorld_id() {
        return world_id;
    }

    public void setWorld_id(int world_id) {
        this.world_id = world_id;
    }

    public String getStat_type() {
        return stat_type;
    }

    public void setStat_type(String stat_type) {
        this.stat_type = stat_type;
    }

    public String getStat_date() {
        return stat_date;
    }

    public void setStat_date(String stat_date) {
        this.stat_date = stat_date;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "PlayerStatsData{" +
                "player_uuid='" + player_uuid + '\'' +
                ", player_name='" + player_name + '\'' +
                ", world_id=" + world_id +
                ", stat_type='" + stat_type + '\'' +
                ", stat_date='" + stat_date + '\'' +
                ", value=" + value +
                '}';
    }
}
