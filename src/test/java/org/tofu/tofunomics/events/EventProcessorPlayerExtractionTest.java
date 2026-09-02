package org.tofu.tofunomics.events;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.Before;
import org.junit.Test;
import org.tofu.tofunomics.config.ConfigManager;
import org.tofu.tofunomics.jobs.JobManager;
import org.tofu.tofunomics.models.PlayerJob;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * イベントから行為者プレイヤーを取り出せるかの回帰テスト
 *
 * shouldProcessEvent はプレイヤーを特定できないと必ず false を返し、
 * そのイベントのハンドラが一度も呼ばれない。
 *
 * 修正前は EntityDeathEvent / EntityBreedEvent で
 * 「EntityEvent#getEntity() が Player か」しか見ていなかったため、
 * 倒されたモブ・生まれた子を見てしまい常に null になっていた。
 * その結果、モブ討伐と繁殖の経験値がまるごと入っていなかった。
 */
public class EventProcessorPlayerExtractionTest {

    private static final String WORLD_NAME = "tofuNomics";

    private EventProcessor processor;
    private World world;
    private Player player;

    @Before
    public void setUp() {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getExcludedWorlds()).thenReturn(Collections.emptyList());
        when(configManager.getEconomyEnabledWorlds()).thenReturn(Collections.singletonList(WORLD_NAME));
        when(configManager.getExcludedGameModes()).thenReturn(Collections.emptyList());

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD_NAME);

        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasMetadata("NPC")).thenReturn(false);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission(anyString())).thenReturn(true);

        PlayerJob job = new PlayerJob();
        job.setLevel(5);

        JobManager jobManager = mock(JobManager.class);
        when(jobManager.getPlayerJobs(player)).thenReturn(Collections.singletonList(job));

        processor = new EventProcessor(configManager, jobManager);
    }

    @Test
    public void モブ討伐は倒したプレイヤーで判定される() {
        LivingEntity killed = mock(LivingEntity.class);
        when(killed.getKiller()).thenReturn(player);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(killed);

        assertTrue("倒したプレイヤーを行為者として処理すること", processor.shouldProcessEvent(event));
    }

    @Test
    public void 誰にも倒されていないモブの死亡は処理されない() {
        LivingEntity killed = mock(LivingEntity.class);
        when(killed.getKiller()).thenReturn(null);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(killed);

        assertFalse("落下死などプレイヤー起因でない死亡は対象外", processor.shouldProcessEvent(event));
    }

    @Test
    public void 繁殖は繁殖させたプレイヤーで判定される() {
        EntityBreedEvent event = mock(EntityBreedEvent.class);
        when(event.getBreeder()).thenReturn(player);

        assertTrue("繁殖させたプレイヤーを行為者として処理すること", processor.shouldProcessEvent(event));
    }

    @Test
    public void 村人同士の繁殖は処理されない() {
        LivingEntity breeder = mock(LivingEntity.class);

        EntityBreedEvent event = mock(EntityBreedEvent.class);
        when(event.getBreeder()).thenReturn(breeder);

        assertFalse("プレイヤー以外が起点の繁殖は対象外", processor.shouldProcessEvent(event));
    }

    @Test
    public void 対象ワールドならプレイヤー不在イベントも処理される() {
        assertTrue("醸造や作物成長はワールドだけで判定する", processor.shouldProcessInWorld(world));
    }

    @Test
    public void 対象外ワールドのプレイヤー不在イベントは処理されない() {
        World other = mock(World.class);
        when(other.getName()).thenReturn("lobby");

        assertFalse("ホワイトリスト外のワールドは対象外", processor.shouldProcessInWorld(other));
    }
}
