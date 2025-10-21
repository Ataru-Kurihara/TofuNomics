package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 住居賃貸契約を表すモデルクラス
 */
public class HousingRental {
    private int id;
    private int propertyId;
    private UUID tenantUuid;
    private String rentalPeriod;  // "daily", "weekly", "monthly"
    private int rentalDays;
    private double totalCost;
    private Timestamp startDate;  // 後方互換性のため残す
    private Timestamp endDate;    // 後方互換性のため残す
    private long startTick;       // ゲーム内時間（tick数）
    private long endTick;         // ゲーム内時間（tick数）
    private String status;  // "active", "expired", "cancelled"
    private boolean autoRenew;
    private Timestamp createdAt;

    // 定数: 1ゲーム内日 = 24000 ticks
    private static final long TICKS_PER_DAY = 24000L;
    // 定数: 1ゲーム内日 = 20分（1200秒）
    private static final long SECONDS_PER_GAME_DAY = 1200L;

    public HousingRental() {}

    public HousingRental(int propertyId, UUID tenantUuid, String rentalPeriod,
                        int rentalDays, double totalCost, long startTick) {
        this.propertyId = propertyId;
        this.tenantUuid = tenantUuid;
        this.rentalPeriod = rentalPeriod;
        this.rentalDays = rentalDays;
        this.totalCost = totalCost;
        this.startTick = startTick;
        this.endTick = calculateEndTick(startTick, rentalDays);
        // 後方互換性のため現実時間も設定
        this.startDate = new Timestamp(System.currentTimeMillis());
        this.endDate = new Timestamp(System.currentTimeMillis() + (rentalDays * SECONDS_PER_GAME_DAY * 1000L));
        this.status = "active";
        this.autoRenew = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * 終了tick数を計算
     */
    private long calculateEndTick(long start, int days) {
        return start + (days * TICKS_PER_DAY);
    }

    /**
     * 終了日時を計算（後方互換性）
     */
    @Deprecated
    private Timestamp calculateEndDate(Timestamp start, int days) {
        long endMillis = start.getTime() + TimeUnit.DAYS.toMillis(days);
        return new Timestamp(endMillis);
    }

    /**
     * 契約が有効期限内かチェック
     * @param world TofuNomicsワールド
     */
    public boolean isActive(org.bukkit.World world) {
        if (!"active".equals(status)) {
            return false;
        }
        return world.getFullTime() < endTick;
    }

    /**
     * 契約が期限切れかチェック
     * @param world TofuNomicsワールド
     */
    public boolean isExpired(org.bukkit.World world) {
        return world.getFullTime() >= endTick;
    }

    /**
     * 残りゲーム内日数を取得
     * @param world TofuNomicsワールド
     */
    public long getRemainingGameDays(org.bukkit.World world) {
        long remainingTicks = endTick - world.getFullTime();
        return Math.max(0, remainingTicks / TICKS_PER_DAY);
    }

    /**
     * 残り時間を現実時間（分）で取得
     * @param world TofuNomicsワールド
     */
    public long getRemainingRealMinutes(org.bukkit.World world) {
        long remainingTicks = endTick - world.getFullTime();
        if (remainingTicks <= 0) {
            return 0;
        }
        // ticks → 秒 → 分に変換
        // 1ゲーム内日(24000 ticks) = 20分(1200秒)
        // 1 tick = 1200秒 / 24000 = 0.05秒
        long remainingSeconds = remainingTicks * SECONDS_PER_GAME_DAY / TICKS_PER_DAY;
        return remainingSeconds / 60;
    }

    /**
     * 残りゲーム内時間（時間単位）を取得
     * @param world TofuNomicsワールド
     * @return 残りゲーム内時間（0〜23時間）
     */
    public long getRemainingGameHours(org.bukkit.World world) {
        long remainingTicks = endTick - world.getFullTime();
        if (remainingTicks <= 0) {
            return 0;
        }
        // 1ゲーム内時間 = 1000 ticks
        // 全体の時間から日数を除いた残りの時間を計算
        long totalHours = remainingTicks / 1000;
        return totalHours % 24;
    }

    /**
     * 残りゲーム内時間（分単位）を取得
     * @param world TofuNomicsワールド
     * @return 残りゲーム内分（0〜59分）
     */
    public long getRemainingGameMinutes(org.bukkit.World world) {
        long remainingTicks = endTick - world.getFullTime();
        if (remainingTicks <= 0) {
            return 0;
        }
        // 1ゲーム内分 ≈ 16.67 ticks (1000 ticks / 60 minutes)
        // より正確には: remainingTicks * 60 / 1000
        long totalMinutes = (remainingTicks * 60) / 1000;
        return totalMinutes % 60;
    }

    /**
     * フォーマット済み残り時間を取得
     * @param world TofuNomicsワールド
     * @return フォーマット済みの残り時間文字列
     */
    public String getFormattedRemainingTime(org.bukkit.World world) {
        long days = getRemainingGameDays(world);
        long hours = getRemainingGameHours(world);
        long minutes = getRemainingGameMinutes(world);
        long realMinutes = getRemainingRealMinutes(world);
        
        StringBuilder sb = new StringBuilder();
        
        // ゲーム内時間の表示
        if (days > 0) {
            sb.append(days).append("日");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("時間");
        }
        if (minutes > 0 || (days == 0 && hours == 0)) {
            sb.append(minutes).append("分");
        }
        
        sb.append(" (ゲーム内) | 現実: 約").append(realMinutes).append("分");
        
        return sb.toString();
    }

    /**
     * 残り日数を取得（後方互換性）
     */
    @Deprecated
    public long getRemainingDays() {
        long diff = endDate.getTime() - System.currentTimeMillis();
        return Math.max(0, TimeUnit.MILLISECONDS.toDays(diff));
    }

    /**
     * 残り時間（ミリ秒）を取得（後方互換性）
     */
    @Deprecated
    public long getRemainingMillis() {
        return Math.max(0, endDate.getTime() - System.currentTimeMillis());
    }

    /**
     * 契約を期限切れにする
     */
    public void expire() {
        this.status = "expired";
    }

    /**
     * 契約をキャンセルする
     */
    public void cancel() {
        this.status = "cancelled";
    }

    /**
     * 契約を延長する
     * @param additionalDays 追加日数
     * @param additionalCost 追加費用
     */
    public void extend(int additionalDays, double additionalCost) {
        this.rentalDays += additionalDays;
        this.totalCost += additionalCost;
        this.endTick += (additionalDays * TICKS_PER_DAY);
        // 後方互換性のため現実時間も更新
        long newEndMillis = endDate.getTime() + (additionalDays * SECONDS_PER_GAME_DAY * 1000L);
        this.endDate = new Timestamp(newEndMillis);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(int propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getTenantUuid() {
        return tenantUuid;
    }

    public void setTenantUuid(UUID tenantUuid) {
        this.tenantUuid = tenantUuid;
    }

    public String getRentalPeriod() {
        return rentalPeriod;
    }

    public void setRentalPeriod(String rentalPeriod) {
        this.rentalPeriod = rentalPeriod;
    }

    public int getRentalDays() {
        return rentalDays;
    }

    public void setRentalDays(int rentalDays) {
        this.rentalDays = rentalDays;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public long getStartTick() {
        return startTick;
    }

    public void setStartTick(long startTick) {
        this.startTick = startTick;
    }

    public long getEndTick() {
        return endTick;
    }

    public void setEndTick(long endTick) {
        this.endTick = endTick;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
