package org.tofu.tofunomics.quests;

import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.dao.PlayerDAO;
import org.tofu.tofunomics.jobs.JobManager;

/**
 * コマンドのクエスト（/quest）の管理クラスだったもの。今は何もしない。
 *
 * コマンドのクエストは廃止した。クエストは街の「依頼受付所」（クエスト NPC。
 * {@code npc.QuestNPCManager}）で受ける。そちらにはクールダウンと DB 保存がある。
 *
 * 廃止の理由: 完了直後に何度でも受け直せた／進捗が再起動で消えた／報酬が使えない列に入っていた／
 * 付与していない経験値を「報酬」と表示していた／中身が 5 件の決め打ちだった。
 *
 * このクラスは、生成している側（TofuNomics、UnifiedEventHandler）の引数を変えないために
 * 空の入れ物として残している。イベントは受け取らず、報酬も付与しない。
 */
public class JobQuestManager {

    public JobQuestManager(ConfigManager configManager, PlayerDAO playerDAO, JobManager jobManager) {
        // 何も持たない（上の説明を参照）
    }
}
