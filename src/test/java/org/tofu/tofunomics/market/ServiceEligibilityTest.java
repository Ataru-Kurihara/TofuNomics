package org.tofu.tofunomics.market;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * ServiceEligibility.check（職業・金床ゲートの純粋ロジック）の単体テスト。
 */
public class ServiceEligibilityTest {

    private static final List<String> REQUIRED = Arrays.asList("blacksmith", "enchanter");

    @Test
    public void testBlacksmithWithAnvilIsOk() {
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check("blacksmith", true, REQUIRED, true));
    }

    @Test
    public void testEnchanterWithAnvilIsOk() {
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check("enchanter", true, REQUIRED, true));
    }

    @Test
    public void testJobNameIsCaseInsensitive() {
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check("BlackSmith", true, REQUIRED, true));
    }

    @Test
    public void testWrongJobIsRejected() {
        assertEquals(ServiceEligibility.Result.WRONG_JOB,
                ServiceEligibility.check("miner", true, REQUIRED, true));
    }

    @Test
    public void testNoJobIsRejected() {
        assertEquals(ServiceEligibility.Result.WRONG_JOB,
                ServiceEligibility.check(null, true, REQUIRED, true));
    }

    @Test
    public void testCorrectJobWithoutAnvilIsRejectedWhenAnvilRequired() {
        assertEquals(ServiceEligibility.Result.NO_ANVIL,
                ServiceEligibility.check("blacksmith", false, REQUIRED, true));
    }

    @Test
    public void testCorrectJobWithoutAnvilIsOkWhenAnvilNotRequired() {
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check("blacksmith", false, REQUIRED, false));
    }

    @Test
    public void testNoJobRestrictionAllowsAnyJob() {
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check("miner", true, Collections.emptyList(), true));
        assertEquals(ServiceEligibility.Result.OK,
                ServiceEligibility.check(null, true, null, true));
    }

    @Test
    public void testJobCheckedBeforeAnvil() {
        // 職業不適合が優先して返る（金床が無くても WRONG_JOB）
        assertEquals(ServiceEligibility.Result.WRONG_JOB,
                ServiceEligibility.check("miner", false, REQUIRED, true));
    }
}
