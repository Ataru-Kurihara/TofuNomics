package org.tofu.tofunomics.integration;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.util.NormativeOrders;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 賃貸リージョンのフラグ設定のテスト。
 * フラグ名の引き方と、設定した結果「誰がチェストを開けられるか」を WorldGuard の判定で確かめる。
 */
public class WorldGuardRegionFlagTest {

    private static final Logger LOGGER = Logger.getLogger("WorldGuardRegionFlagTest");

    private FlagRegistry registry;
    private ProtectedRegion city;
    private ProtectedRegion house;
    private final UUID tenant = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        Flags.registerAll();
        registry = WorldGuard.getInstance().getFlagRegistry();

        // 本番と同じ構成: 親 city（build/interact 禁止・use 許可）の子に物件のリージョンがある
        city = new ProtectedCuboidRegion("city", BlockVector3.at(0, 0, 0), BlockVector3.at(100, 100, 100));
        city.setFlag(Flags.BUILD, State.DENY);
        city.setFlag(Flags.INTERACT, State.DENY);
        city.setFlag(Flags.USE, State.ALLOW);

        house = new ProtectedCuboidRegion("house-1", BlockVector3.at(10, 0, 10), BlockVector3.at(20, 20, 20));
        house.setParent(city);
        house.getMembers().addPlayer(tenant);
    }

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** WorldGuard の RegionQuery.testBuild(location, subject, flags...) と同じ式 */
    private boolean test(boolean isTenant, StateFlag... flags) {
        FlagValueCalculator calculator = new FlagValueCalculator(
            NormativeOrders.fromSet(new HashSet<>(Arrays.asList(house, city))), null);
        RegionAssociable subject = regions -> {
            if (isTenant) {
                for (ProtectedRegion region : regions) {
                    if (region.getMembers().contains(tenant)) {
                        return Association.MEMBER;
                    }
                }
            }
            return Association.NON_MEMBER;
        };
        return StateFlag.test(StateFlag.combine(
            StateFlag.denyToNone(calculator.queryState(subject, Flags.BUILD)),
            calculator.queryState(subject, flags)));
    }

    private boolean canOpenChest(boolean isTenant) {
        return test(isTenant, Flags.INTERACT, Flags.CHEST_ACCESS);
    }

    private boolean canUseDoor(boolean isTenant) {
        return test(isTenant, Flags.INTERACT, Flags.USE);
    }

    @Test
    public void chest_accessを登録簿から引いて設定できる() {
        int applied = WorldGuardIntegration.applyFlagsToRegion(house,
            map("chest-access", "allow"), Collections.<String, String>emptyMap(), registry, LOGGER);

        assertEquals(1, applied);
        assertEquals(State.ALLOW, house.getFlag(Flags.CHEST_ACCESS));
    }

    @Test
    public void 知らないフラグや状態は飛ばして残りを設定する() {
        int applied = WorldGuardIntegration.applyFlagsToRegion(house,
            map("no-such-flag", "allow", "use", "maybe", "greeting", "allow", "build", "deny"),
            map("build", "everyone"), registry, LOGGER);

        // greeting は状態フラグではない。build は対象グループ名が不正
        assertEquals(0, applied);
        assertNull(house.getFlag(Flags.BUILD));

        applied = WorldGuardIntegration.applyFlagsToRegion(house,
            map("no-such-flag", "allow", "build", "deny"), null, registry, LOGGER);
        assertEquals(1, applied);
        assertEquals(State.DENY, house.getFlag(Flags.BUILD));
    }

    @Test
    public void 対象グループを指定でき_指定を外すと全員に戻る() {
        WorldGuardIntegration.applyFlagsToRegion(house,
            map("chest-access", "deny"), map("chest-access", "nonmembers"), registry, LOGGER);
        assertEquals(RegionGroup.NON_MEMBERS, house.getFlag(Flags.CHEST_ACCESS.getRegionGroupFlag()));

        WorldGuardIntegration.applyFlagsToRegion(house,
            map("chest-access", "deny"), Collections.<String, String>emptyMap(), registry, LOGGER);
        assertNull(house.getFlag(Flags.CHEST_ACCESS.getRegionGroupFlag()));
    }

    @Test
    public void 対象グループ名の書き方のゆれを受け付ける() {
        assertEquals(RegionGroup.NON_MEMBERS, WorldGuardIntegration.parseRegionGroup("nonmembers"));
        assertEquals(RegionGroup.NON_MEMBERS, WorldGuardIntegration.parseRegionGroup("NON_MEMBERS"));
        assertEquals(RegionGroup.NON_MEMBERS, WorldGuardIntegration.parseRegionGroup("non-members"));
        assertEquals(RegionGroup.MEMBERS, WorldGuardIntegration.parseRegionGroup(" members "));
        assertNull(WorldGuardIntegration.parseRegionGroup("everyone"));
    }

    @Test
    public void 以前の設定では借りていない人もチェストを開けられる() {
        // chest-access が付かなかった以前の状態（build/use/interact のみ）
        WorldGuardIntegration.applyFlagsToRegion(house,
            map("use", "allow", "build", "deny", "interact", "allow"), null, registry, LOGGER);
        assertTrue(canOpenChest(true));
        assertTrue(canOpenChest(false));

        // chest-access: allow を足しても、全員に適用されるので変わらない
        WorldGuardIntegration.applyFlagsToRegion(house, map("chest-access", "allow"), null, registry, LOGGER);
        assertTrue(canOpenChest(true));
        assertTrue(canOpenChest(false));
    }

    @Test
    public void 既定の設定では借主だけがチェストを開けられ_ドアは全員が使える() {
        WorldGuardIntegration.applyFlagsToRegion(house,
            map("use", "allow", "chest-access", "deny", "build", "deny", "interact", "allow"),
            map("chest-access", "nonmembers"), registry, LOGGER);

        assertTrue(canOpenChest(true));
        assertFalse(canOpenChest(false));
        assertTrue(canUseDoor(true));
        assertTrue(canUseDoor(false));
        // 建築は借主もできない
        assertFalse(test(true, Flags.BLOCK_PLACE));
    }

    @Test
    public void interactを借主だけにすると_ドアもチェストも借主だけになる() {
        // 親 city の interact: deny が借主以外に効く
        WorldGuardIntegration.applyFlagsToRegion(house,
            map("use", "allow", "chest-access", "allow", "build", "deny", "interact", "allow"),
            map("interact", "members", "chest-access", "members"), registry, LOGGER);

        assertTrue(canOpenChest(true));
        assertTrue(canUseDoor(true));
        assertFalse(canOpenChest(false));
        assertFalse(canUseDoor(false));
    }
}
