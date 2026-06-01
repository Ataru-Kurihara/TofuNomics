package org.tofu.tofunomics.tutorial;

/**
 * チュートリアルステップの列挙型
 * 各ステップは順番に進行し、プレイヤーの行動に応じて完了となる
 */
public enum TutorialStep {

    NOT_STARTED(0, "tutorial.not_started", "未開始"),
    JOB_SELECT(1, "tutorial.step1", "職業選択"),
    FIRST_INCOME(2, "tutorial.step2", "初収入"),
    BANK_NPC(3, "tutorial.step3", "銀行NPC"),
    TRADING_NPC(4, "tutorial.step4", "取引NPC"),
    COMPLETED(5, "tutorial.completed", "完了");

    private final int order;
    private final String configKey;
    private final String displayName;

    TutorialStep(int order, String configKey, String displayName) {
        this.order = order;
        this.configKey = configKey;
        this.displayName = displayName;
    }

    /**
     * ステップの順序を取得
     */
    public int getOrder() {
        return order;
    }

    /**
     * 設定キーを取得
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 表示名を取得
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 次のステップを取得
     * @return 次のステップ、既に完了の場合はCOMPLETEDを返す
     */
    public TutorialStep getNextStep() {
        if (this == COMPLETED) {
            return COMPLETED;
        }
        int nextOrder = this.order + 1;
        for (TutorialStep step : values()) {
            if (step.order == nextOrder) {
                return step;
            }
        }
        return COMPLETED;
    }

    /**
     * 順序からステップを取得
     * @param order 順序番号
     * @return 対応するステップ、見つからない場合はNOT_STARTED
     */
    public static TutorialStep fromOrder(int order) {
        for (TutorialStep step : values()) {
            if (step.order == order) {
                return step;
            }
        }
        return NOT_STARTED;
    }

    /**
     * このステップが完了済みかどうか
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * このステップが開始済みかどうか
     */
    public boolean isStarted() {
        return this != NOT_STARTED;
    }

    /**
     * 進行中のステップかどうか（開始済みかつ未完了）
     */
    public boolean isInProgress() {
        return isStarted() && !isCompleted();
    }
}
