package org.tofu.tofunomics.market;

import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.market.gui.MarketGUIUtil;

/**
 * マーケットの結果メッセージ（messages.market.*）を組み立てるヘルパー。
 *
 * 各 {@link MarketResult} に対応するメッセージへ、必要なプレースホルダ
 * （%item% %price% %currency% %min% %max% %amount%）をまとめて渡す。
 * 未使用のプレースホルダは {@code getMessage} 側で無視されるため、常に全て渡してよい。
 */
public final class MarketMessages {

    private MarketMessages() {
    }

    /** 設定ファイルに文が無いときに ConfigManager が返す文の先頭 */
    public static final String MISSING_MESSAGE_PREFIX = "メッセージが見つかりません";

    /**
     * 件数の上限で断るときの、コード側の既定文。上限以外の結果では null。
     */
    public static String limitDefaultMessage(MarketResult result, int maxListings, int maxBuyOrders) {
        if (result == MarketResult.LISTING_LIMIT) {
            return "§c出品できるのは 1 人 " + maxListings + " 件までです。売れるのを待つか、取り下げてから出品してください。";
        }
        if (result == MarketResult.ORDER_LIMIT) {
            return "§c買い注文を出せるのは 1 人 " + maxBuyOrders + " 件までです。成立を待つか、取り下げてから出してください。";
        }
        return null;
    }

    /**
     * 結果コードに対応するメッセージを、関連プレースホルダを埋めて生成する。
     *
     * @param item  対象アイテムの表示名（不要な結果では無視される）
     * @param price 価格（不要な結果では無視される）
     */
    public static String format(ConfigManager configManager, MarketResult result, String item, double price) {
        String limitDefault = limitDefaultMessage(result,
                configManager.getMarketMaxListingsPerPlayer(), configManager.getMarketMaxBuyOrdersPerPlayer());
        if (limitDefault != null
                && configManager.getMarketMessage(result.getMessageKey()).startsWith(MISSING_MESSAGE_PREFIX)) {
            // 設定ファイルに文が無ければ、コード側の既定文を使う
            return limitDefault;
        }
        return configManager.getMarketMessage(result.getMessageKey(),
                "item", item != null ? item : "",
                "price", MarketGUIUtil.formatPrice(price),
                "currency", configManager.getCurrencyName(),
                "min", String.valueOf((long) configManager.getMarketMinPrice()),
                "max", String.valueOf((long) configManager.getMarketMaxPrice()),
                "amount", MarketGUIUtil.formatPrice(price));
    }
}
