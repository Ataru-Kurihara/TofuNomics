package org.tofu.tofunomics.market;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * MarketItemSerializer 単体テスト
 *
 * ItemStack の往復（serialize → deserialize）は Bukkit サーバ実装（ItemFactory）に依存し、
 * MockBukkit を導入していない本プロジェクトのユニットテストでは再現できないため、
 * E2E 検証（実サーバでの出品→購入）に委ねる。
 * 本テストでは Bukkit 非依存のエッジケース（null・空・不正入力）の防御挙動を検証する。
 */
public class MarketItemSerializerTest {

    @Test
    public void testSerializeNullReturnsNull() {
        assertNull("null アイテムは null を返すべき", MarketItemSerializer.serialize(null));
    }

    @Test
    public void testDeserializeNullReturnsNull() {
        assertNull("null データは null を返すべき", MarketItemSerializer.deserialize(null));
    }

    @Test
    public void testDeserializeEmptyReturnsNull() {
        assertNull("空文字列は null を返すべき", MarketItemSerializer.deserialize(""));
    }

    @Test
    public void testDeserializeInvalidBase64ReturnsNull() {
        // Base64 として不正な文字列 → IllegalArgumentException を握りつぶして null
        assertNull("不正な Base64 は null を返すべき", MarketItemSerializer.deserialize("!!! not base64 !!!"));
    }

    @Test
    public void testDeserializeValidBase64ButNotItemStackReturnsNull() {
        // Base64 としては妥当だが ItemStack のバイナリではない → null
        assertNull("ItemStack でないデータは null を返すべき", MarketItemSerializer.deserialize("aGVsbG8gd29ybGQ="));
    }
}
