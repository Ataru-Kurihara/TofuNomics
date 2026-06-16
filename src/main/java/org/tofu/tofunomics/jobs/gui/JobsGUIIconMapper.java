package org.tofu.tofunomics.jobs.gui;

import org.bukkit.Material;

/**
 * 職業名 → 表示アイコン（Material）のマッピング。
 *
 * Jobs GUI で各職業を表すアイコンを決定する。職業の種類は固定的なため
 * {@code JobManager#getJobTitle} と同様にハードコードの switch で対応する。
 */
public final class JobsGUIIconMapper {

    private JobsGUIIconMapper() {
    }

    /**
     * 職業名に対応するアイコン Material を返す。未知の職業は {@link Material#BOOK} を返す。
     */
    public static Material getIcon(String jobName) {
        if (jobName == null) {
            return Material.BOOK;
        }
        switch (jobName.toLowerCase()) {
            case "miner":      return Material.DIAMOND_PICKAXE;
            case "woodcutter": return Material.IRON_AXE;
            case "farmer":     return Material.WHEAT;
            case "fisherman":  return Material.FISHING_ROD;
            case "blacksmith": return Material.ANVIL;
            case "alchemist":  return Material.BREWING_STAND;
            case "enchanter":  return Material.ENCHANTING_TABLE;
            case "architect":  return Material.BRICKS;
            default:           return Material.BOOK;
        }
    }
}
