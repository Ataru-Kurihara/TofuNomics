package org.tofu.tofunomics.economy;

/**
 * お金の記録（transaction_log）に残す「理由」。
 * 表には記号（name()）を保存し、運営向けの表示には日本語の名前を使う。
 */
public enum TransactionType {
    PAY_SEND("送金"),
    PAY_RECEIVE("送金の受け取り"),
    PAY_FEE("送金手数料"),
    DEPOSIT("預け入れ"),
    WITHDRAW("引き出し"),
    ECO_GIVE("運営による付与"),
    ECO_TAKE("運営による取り上げ"),
    ECO_SET("運営による残高設定"),
    ECO_RESET("運営による残高リセット"),
    NPC_SELL("NPCへの売却"),
    NPC_BUY("NPCからの購入"),
    MARKET_PAY("マーケットの支払い"),
    MARKET_INCOME("マーケットの受け取り"),
    MARKET_REFUND("マーケットの返金"),
    QUEST_REWARD("クエスト報酬"),
    LEVEL_REWARD("レベルアップ報酬"),
    RENT("住居の家賃"),
    /** 理由を宣言していない呼び出し元からの増減 */
    OTHER("その他");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 表に保存された記号から表示名を得る。知らない記号はそのまま返す
     * （新しい版で増えた理由を古い版で読んでも落ちないようにする）。
     */
    public static String labelOf(String name) {
        if (name == null) {
            return OTHER.label;
        }
        try {
            return valueOf(name).label;
        } catch (IllegalArgumentException e) {
            return name;
        }
    }
}
