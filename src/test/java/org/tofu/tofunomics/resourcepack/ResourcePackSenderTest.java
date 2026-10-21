package org.tofu.tofunomics.resourcepack;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;

import java.io.File;

import static org.junit.Assert.*;

/**
 * 通貨のリソースパックを送る判定と、設定の既定値のテスト。
 */
public class ResourcePackSenderTest {

    private static final String URL = "https://example.com/pack.zip";

    @Test
    public void tofuNomicsに初めて入ったときだけ送る() {
        assertTrue(ResourcePackSender.shouldSend("tofuNomics", true, URL, false));
    }

    @Test
    public void ほかのワールドでは送らない() {
        assertFalse(ResourcePackSender.shouldSend("lobby", true, URL, false));
        assertFalse(ResourcePackSender.shouldSend("tofuNomics_nether", true, URL, false));
    }

    @Test
    public void 同じ接続で送り済みなら送らない() {
        assertFalse(ResourcePackSender.shouldSend("tofuNomics", true, URL, true));
    }

    @Test
    public void 無効または_url_が空なら送らない() {
        assertFalse(ResourcePackSender.shouldSend("tofuNomics", false, URL, false));
        assertFalse(ResourcePackSender.shouldSend("tofuNomics", true, "", false));
        assertFalse(ResourcePackSender.shouldSend("tofuNomics", true, "  ", false));
        assertFalse(ResourcePackSender.shouldSend("tofuNomics", true, null, false));
    }

    @Test
    public void sha1をバイト列にする() {
        byte[] bytes = ResourcePackSender.parseSha1(" 05aae30406a4bc1d9bcd89d227b71b2f519a2d9f ");
        assertNotNull(bytes);
        assertEquals(20, bytes.length);
        assertEquals((byte) 0x05, bytes[0]);
        assertEquals((byte) 0x9f, bytes[19]);
    }

    @Test
    public void 形の違うsha1はnull() {
        assertNull(ResourcePackSender.parseSha1(null));
        assertNull(ResourcePackSender.parseSha1(""));
        assertNull(ResourcePackSender.parseSha1("05aae304"));
        assertNull(ResourcePackSender.parseSha1("zzaae30406a4bc1d9bcd89d227b71b2f519a2d9f"));
    }

    @Test
    public void 読み込みの失敗だけを失敗と扱う() {
        assertTrue(ResourcePackSender.isFailure(Status.FAILED_DOWNLOAD));
        assertTrue(ResourcePackSender.isFailure(Status.INVALID_URL));
        assertTrue(ResourcePackSender.isFailure(Status.FAILED_RELOAD));
        assertTrue(ResourcePackSender.isFailure(Status.DISCARDED));
        assertFalse(ResourcePackSender.isFailure(Status.DECLINED));
        assertFalse(ResourcePackSender.isFailure(Status.SUCCESSFULLY_LOADED));
        assertFalse(ResourcePackSender.isFailure(Status.ACCEPTED));
    }

    /**
     * 既存サーバーの system.yml は jar で上書きされず、キーの無いサーバーでは ConfigManager の既定値が使われる。
     * 同梱の system.yml と既定値がずれると、サーバーによって違うパックを送ってしまう
     */
    @Test
    public void 同梱のsystem_ymlとConfigManagerの既定値がそろっている() {
        YamlConfiguration system = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config/system.yml"));
        assertEquals(ConfigManager.DEFAULT_RESOURCE_PACK_URL, system.getString("resource_pack.url"));
        assertEquals(ConfigManager.DEFAULT_RESOURCE_PACK_SHA1, system.getString("resource_pack.sha1"));
        assertNotNull("既定の sha1 が 40 桁の 16 進数ではない",
                ResourcePackSender.parseSha1(ConfigManager.DEFAULT_RESOURCE_PACK_SHA1));
    }
}
