package org.tofu.tofunomics.rules;

import org.junit.Test;
import org.tofu.tofunomics.TofuNomics;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.jobs.JobManager;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * ルール同意強制フラグの回帰テスト。
 *
 * 背景: rules.enabled / rules.require_agreement が false でもコード側がフラグを読んでおらず、
 * ワールド参加した未同意プレイヤーが移動・コマンドを全てブロックされ、/rules で同意するまで
 * 動けなくなる不具合があった。設定が無効なら未同意リストに登録されないことを保証する。
 */
public class RulesAgreementEnforcementTest {

    private RulesManager createRulesManager(boolean enabled, boolean requireAgreement) {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isRulesEnabled()).thenReturn(enabled);
        when(configManager.isRulesAgreementRequired()).thenReturn(requireAgreement);

        return new RulesManager(
            mock(TofuNomics.class),
            configManager,
            mock(PlayerDAO.class),
            mock(JobManager.class)
        );
    }

    @Test
    public void 設定が無効なら同意強制されず未同意登録もされない() {
        RulesManager rulesManager = createRulesManager(false, false);
        UUID uuid = UUID.randomUUID();

        assertFalse("rules.enabled=false では同意強制しない", rulesManager.isAgreementEnforced());

        rulesManager.markAsUnagreed(uuid);
        assertFalse("同意強制が無効なら行動制限の対象にならない", rulesManager.isUnagreed(uuid));
    }

    @Test
    public void 外部サイト確認方式では同意強制されない() {
        // rules.enabled=true だが require_agreement=false（外部サイトでルール確認する運用）
        RulesManager rulesManager = createRulesManager(true, false);
        UUID uuid = UUID.randomUUID();

        assertFalse("require_agreement=false では同意強制しない", rulesManager.isAgreementEnforced());

        rulesManager.markAsUnagreed(uuid);
        assertFalse("行動制限の対象にならない", rulesManager.isUnagreed(uuid));
    }

    @Test
    public void 両フラグ有効なら未同意プレイヤーは制限対象になる() {
        RulesManager rulesManager = createRulesManager(true, true);
        UUID uuid = UUID.randomUUID();

        assertTrue("両フラグ true では同意強制する", rulesManager.isAgreementEnforced());

        rulesManager.markAsUnagreed(uuid);
        assertTrue("未同意プレイヤーは行動制限の対象", rulesManager.isUnagreed(uuid));
    }
}
