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

    /**
     * 結果コードに対応するメッセージを、関連プレースホルダを埋めて生成する。
     *
     * @param item  対象アイテムの表示名（不要な結果では無視される）
     * @param price 価格（不要な結果では無視される）
     */
    public static String format(ConfigManager configManager, MarketResult result, String item, double price) {
        return configManager.getMarketMessage(result.getMessageKey(),
                "item", item != null ? item : "",
                "price", MarketGUIUtil.formatPrice(price),
                "currency", configManager.getCurrencyName(),
                "min", String.valueOf((long) configManager.getMarketMinPrice()),
                "max", String.valueOf((long) configManager.getMarketMaxPrice()),
                "amount", MarketGUIUtil.formatPrice(price));
    }
}
