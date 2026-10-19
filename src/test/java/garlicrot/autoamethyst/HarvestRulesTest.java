package garlicrot.autoamethyst;

import org.junit.jupiter.api.Test;

import static garlicrot.autoamethyst.HarvestRules.HarvestMode.*;
import static garlicrot.autoamethyst.HarvestRules.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

class HarvestRulesTest {
    @Test
    void defaultOnlyBreaksMatureClusters() {
        assertTrue(MATURE_ONLY.accepts(CLUSTER));
        assertFalse(MATURE_ONLY.accepts(LARGE));
        assertFalse(MATURE_ONLY.accepts(MEDIUM));
        assertFalse(MATURE_ONLY.accepts(SMALL));
        assertFalse(MATURE_ONLY.accepts(null));
    }

    @Test
    void optionalAllGrowthModeAllowsBuds() {
        for (HarvestRules.Stage stage : HarvestRules.Stage.values()) {
            assertTrue(ALL_GROWTH.accepts(stage));
        }
        assertFalse(ALL_GROWTH.accepts(null));
    }

    @Test
    void reachNeverExceedsVanillaForActiveGameMode() {
        assertEquals(4.5, HarvestRules.vanillaReach(4.5, false));
        assertEquals(4.5, HarvestRules.vanillaReach(10, false));
        assertEquals(5.0, HarvestRules.vanillaReach(5, true));
        assertEquals(5.0, HarvestRules.vanillaReach(10, true));
        assertEquals(3.0, HarvestRules.vanillaReach(3, false));
        assertEquals(0.0, HarvestRules.vanillaReach(-1, false));
        assertEquals(0.0, HarvestRules.vanillaReach(Double.NaN, false));
    }

    @Test
    void confirmedBreakContinuesImmediately() {
        assertEquals(HarvestRules.Verification.CONFIRMED,
                HarvestRules.verification(true, 1, 16));
        assertEquals(HarvestRules.Verification.WAITING,
                HarvestRules.verification(false, 15, 16));
        assertEquals(HarvestRules.Verification.TIMED_OUT,
                HarvestRules.verification(false, 16, 16));
    }

    @Test
    void rejectedTargetsUseBoundedBackoff() {
        assertEquals(10, HarvestRules.retryDelayTicks(10, 1));
        assertEquals(20, HarvestRules.retryDelayTicks(10, 2));
        assertEquals(40, HarvestRules.retryDelayTicks(10, 3));
        assertEquals(80, HarvestRules.retryDelayTicks(10, 4));
        assertEquals(160, HarvestRules.retryDelayTicks(10, 5));
        assertEquals(160, HarvestRules.retryDelayTicks(80, 100));
    }
}
