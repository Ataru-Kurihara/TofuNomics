package org.tofu.tofunomics.commands;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 本番の messages.yml にまだキーが無いときでも、上限に達したときの文が読める形で出ることのテスト。
 */
public class MarketFallbackMessageTest {

    @Test
    public void 上限の2キーには既定文がある() {
        assertNotNull(MarketCommand.fallbackMarketMessage("listing_limit"));
        assertNotNull(MarketCommand.fallbackMarketMessage("buy_order_limit"));
        assertNull(MarketCommand.fallbackMarketMessage("listed"));
    }

    @Test
    public void キーが無いときの文を見分ける() {
        assertTrue(MarketCommand.isMissingMessage("メッセージが見つかりません: market.listing_limit"));
        assertTrue(MarketCommand.isMissingMessage(null));
        assertFalse(MarketCommand.isMissingMessage("§c出品数の上限に達しています。"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void 既定文はymlの文と同じ() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config/messages.yml")) {
            assertNotNull(in);
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> market = (Map<String, Object>) ((Map<String, Object>) root.get("messages")).get("market");
            assertEquals(market.get("listing_limit"), MarketCommand.fallbackMarketMessage("listing_limit"));
            assertEquals(market.get("buy_order_limit"), MarketCommand.fallbackMarketMessage("buy_order_limit"));
        }
    }
}
