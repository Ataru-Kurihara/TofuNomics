package org.tofu.tofunomics.players;

import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.tofu.tofunomics.config.ConfigManager;

import java.util.logging.Logger;

/**
 * tofuNomics で死んだ人を、tofuNomics の中に復活させるリスナー。
 * ベッドの地点は全ワールドで 1 つなので、survival で寝た人は tofuNomics で死ぬと
 * survival に復活していた。死ぬだけで survival へ移れてしまい、ワールド移動の扱いで
 * tofuNomics の手持ちが空のまま保存されることもあった。
 * Multiverse（LOW）と TofuPlugin（NORMAL）が復活先を決めた後に、HIGHEST で直す。
 *
 * tofuNomics のベッドは、本体のベッド地点を更新せずにここで別に記録する。
 * 本体のベッド地点を上書きすると、survival で死んだときに tofuNomics へ復活してしまうため。
 */
public class TofuNomicsRespawnListener implements Listener {

    static final String WORLD_NAME = "tofuNomics";

    /** ベッドの上に立たせる高さ（ベッドの高さは 9/16 ブロック） */
    static final double BED_TOP_OFFSET = 0.5625;

    private final ConfigManager configManager;
    private final Logger logger;
    private final NamespacedKey bedKey;

    public TofuNomicsRespawnListener(Plugin plugin, ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        this.bedKey = new NamespacedKey(plugin, "tofunomics_bed");
    }

    /**
     * tofuNomics でベッドやリスポーンアンカーを使ったとき、本体のベッド地点を更新させない。
     * ベッドは tofuNomics 用として記録し、tofuNomics で死んだときだけ使う。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSetSpawn(PlayerSpawnChangeEvent event) {
        Location location = event.getNewSpawn();
        if (!WORLD_NAME.equals(worldNameOf(location))) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getCause() == PlayerSpawnChangeEvent.Cause.BED) {
            event.setCancelled(true);
            String encoded = encodeBed(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            PersistentDataContainer data = player.getPersistentDataContainer();
            if (!encoded.equals(data.get(bedKey, PersistentDataType.STRING))) {
                data.set(bedKey, PersistentDataType.STRING, encoded);
                player.sendMessage("§atofuNomics の復活地点をこのベッドにしました（survival のベッドはそのままです）");
                logger.info("プレイヤー " + player.getName() + " の tofuNomics のベッドを記録しました: " + encoded);
            }
        } else if (event.getCause() == PlayerSpawnChangeEvent.Cause.RESPAWN_ANCHOR) {
            event.setCancelled(true);
            player.sendMessage("§etofuNomics ではリスポーンアンカーを復活地点にできません");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }
        Player player = event.getPlayer();
        // 復活前なので、ここでのワールドは死んだワールド
        World deathWorld = player.getWorld();
        if (!WORLD_NAME.equals(deathWorld.getName())) {
            return;
        }

        // tofuNomics のベッドが残っていれば、そこに復活させる
        PersistentDataContainer data = player.getPersistentDataContainer();
        int[] bed = decodeBed(data.get(bedKey, PersistentDataType.STRING));
        if (bed != null) {
            if (Tag.BEDS.isTagged(deathWorld.getBlockAt(bed[0], bed[1], bed[2]).getType())) {
                event.setRespawnLocation(bedStandLocation(deathWorld, bed[0], bed[1], bed[2]));
                return;
            }
            data.remove(bedKey);
            player.sendMessage("§etofuNomics のベッドが無くなっていたため、スポーンに復活しました");
            logger.info("プレイヤー " + player.getName() + " の tofuNomics のベッドが無いため記録を消しました");
        }

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
     * tofuNomics の中への復活はそのままにする。
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

    static String encodeBed(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** 記録の形が壊れていれば null */
    static int[] decodeBed(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Location bedStandLocation(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y + BED_TOP_OFFSET, z + 0.5);
    }

    private static String worldNameOf(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName();
    }
}
