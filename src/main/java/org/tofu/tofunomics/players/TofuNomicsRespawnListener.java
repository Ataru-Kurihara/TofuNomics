package org.tofu.tofunomics.players;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.logging.Logger;

/**
 * tofuNomics で死んだ人を、tofuNomics の中に復活させるリスナー。
 * ベッドの地点は全ワールドで 1 つなので、survival で寝た人は tofuNomics で死ぬと
 * survival に復活していた。死ぬだけで survival へ移れてしまい、ワールド移動の扱いで
 * tofuNomics の手持ちが空のまま保存されることもあった。
 * Multiverse（LOW）と TofuPlugin（NORMAL）が復活先を決めた後に、HIGHEST で直す。
 */
public class TofuNomicsRespawnListener implements Listener {

    static final String WORLD_NAME = "tofuNomics";

    private final ConfigManager configManager;
    private final Logger logger;

    public TofuNomicsRespawnListener(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }
        Player player = event.getPlayer();
        // 復活前なので、ここでのワールドは死んだワールド
        World deathWorld = player.getWorld();
        String originalWorldName = worldNameOf(event.getRespawnLocation());
        if (!needsRedirect(deathWorld.getName(), originalWorldName)) {
            return;
        }

        Location spawn = resolveSpawn(deathWorld, configManager.isSpawnLocationEnabled(),
                configManager.getSpawnWorldName(),
                configManager.getSpawnX(), configManager.getSpawnY(), configManager.getSpawnZ(),
                configManager.getSpawnYaw(), configManager.getSpawnPitch());
        event.setRespawnLocation(spawn);
        logger.info("プレイヤー " + player.getName() + " は tofuNomics で死亡したため、復活先を "
                + originalWorldName + " から tofuNomics のスポーンに変えました");
    }

    /**
     * 復活先を tofuNomics に戻すかどうか。
     * tofuNomics の中のベッド・アンカーへの復活はそのままにする。
     */
    static boolean needsRedirect(String deathWorldName, String respawnWorldName) {
        if (!WORLD_NAME.equals(deathWorldName)) {
            return false;
        }
        return !WORLD_NAME.equals(respawnWorldName);
    }

    /**
     * 復活させる地点。player_join.spawn_location が tofuNomics を指していればそこ、
     * そうでなければ tofuNomics ワールドのスポーン地点。
     */
    static Location resolveSpawn(World tofuNomicsWorld, boolean spawnEnabled, String spawnWorldName,
                                 int x, int y, int z, float yaw, float pitch) {
        if (spawnEnabled && WORLD_NAME.equals(spawnWorldName)) {
            return new Location(tofuNomicsWorld, x + 0.5, y, z + 0.5, yaw, pitch);
        }
        return tofuNomicsWorld.getSpawnLocation();
    }

    private static String worldNameOf(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName();
    }
}
