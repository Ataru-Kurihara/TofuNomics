package org.tofu.tofunomics.economy;

/**
 * 「いまから行うお金の操作の理由」を宣言するための入れ物。
 *
 * 残高を書き換える所は多く、保存処理（PlayerDAO）まで理由を引数で運ぶと呼び出し元を
 * すべて書き換えることになる。そこで、操作する側が理由を宣言し、保存処理の側が
 * 「いま宣言されている理由」を読んで記録する。宣言の無い操作は「その他」として記録される。
 *
 * 使い方（try-with-resources。抜けるときに元の宣言へ戻る）:
 * <pre>
 * try (TransactionContext.Scope scope = TransactionContext.open(TransactionType.RENT, null, "物件A 3日")) {
 *     // 残高を動かす既存の処理
 * }
 * </pre>
 */
public final class TransactionContext {

    private static final TransactionContext DEFAULT = new TransactionContext(TransactionType.OTHER, null, null);
    private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();

    private final TransactionType type;
    private final String counterparty;
    private final String detail;

    private TransactionContext(TransactionType type, String counterparty, String detail) {
        this.type = type;
        this.counterparty = counterparty;
        this.detail = detail;
    }

    public TransactionType getType() {
        return type;
    }

    /** 相手（プレイヤーの UUID、NPC、運営の実行者名など）。無ければ null */
    public String getCounterparty() {
        return counterparty;
    }

    /** 補足（品名×個数など）。無ければ null */
    public String getDetail() {
        return detail;
    }

    /** いま宣言されている理由。宣言が無ければ「その他」 */
    public static TransactionContext current() {
        TransactionContext context = CURRENT.get();
        return context != null ? context : DEFAULT;
    }

    /** 理由が宣言されているか */
    public static boolean isDeclared() {
        return CURRENT.get() != null;
    }

    /**
     * 理由を宣言する。返り値を close すると、宣言する前の状態に戻る。
     */
    public static Scope open(TransactionType type, String counterparty, String detail) {
        TransactionContext previous = CURRENT.get();
        CURRENT.set(new TransactionContext(type != null ? type : TransactionType.OTHER, counterparty, detail));
        return new Scope(previous, true);
    }

    /**
     * まだ理由が宣言されていないときだけ宣言する。
     * 共通の入口（預け入れ・引き出しなど）が既定の理由を付けるために使う。
     * 呼び出し元がすでに宣言していれば、そちらを優先して何もしない。
     */
    public static Scope openIfUndeclared(TransactionType type, String counterparty, String detail) {
        if (isDeclared()) {
            return new Scope(null, false);
        }
        return open(type, counterparty, detail);
    }

    /** 宣言の範囲。close で元に戻す */
    public static final class Scope implements AutoCloseable {
        private final TransactionContext previous;
        private final boolean active;

        private Scope(TransactionContext previous, boolean active) {
            this.previous = previous;
            this.active = active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
