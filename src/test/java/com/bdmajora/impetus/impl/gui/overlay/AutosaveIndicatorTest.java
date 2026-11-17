package com.bdmajora.impetus.impl.gui.overlay;

import com.bdmajora.impetus.impl.world.WorldSaveTracker;
import com.bdmajora.impetus.mixin.core.ServerSaveIndicatorMixin;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests the save tracker and the indicator fade and the save mixin.
class AutosaveIndicatorTest {
    // Clears the fade and syncs the seen save count.
    @BeforeEach
    void hidden() {
        AutosaveIndicator.tick(false);
    }

    // Returns the full alpha after the latest tick
    private static int alpha() {
        return AutosaveIndicator.alpha(1F);
    }

    // Runs ticks in a world and returns the alpha after each
    private static List<Integer> ticks(int count) {
        List<Integer> alphas = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AutosaveIndicator.tick(true);
            alphas.add(alpha());
        }
        return alphas;
    }

    @Test
    void theTrackerRaisesTheFlagOnlyWhileTheSaveRuns() {
        int before = WorldSaveTracker.started();
        assertFalse(WorldSaveTracker.saving());
        boolean[] inside = new boolean[1];
        WorldSaveTracker.duringSave(() -> inside[0] = WorldSaveTracker.saving());
        assertTrue(inside[0]);
        assertFalse(WorldSaveTracker.saving());
        assertEquals(before + 1, WorldSaveTracker.started());
    }

    @Test
    void aSaveThatThrowsStillLowersTheFlagAndKeepsItsError() {
        int before = WorldSaveTracker.started();
        IllegalStateException failure = new IllegalStateException("disk full");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> WorldSaveTracker.duringSave(() -> {
            throw failure;
        })));
        assertFalse(WorldSaveTracker.saving());
        assertEquals(before + 1, WorldSaveTracker.started());
        assertThrows(NullPointerException.class, () -> WorldSaveTracker.duringSave(null));
        assertEquals(before + 1, WorldSaveTracker.started());
    }

    @Test
    void nothingShowsWithoutASave() {
        assertTrue(ticks(20).stream().allMatch(a -> a == 0));
    }

    @Test
    void aSaveBetweenTwoTicksRisesForTheHoldThenFadesOut() {
        WorldSaveTracker.duringSave(() -> {});
        List<Integer> rise = ticks(AutosaveIndicator.MIN_TICKS);
        for (int i = 1; i < rise.size(); i++) {
            assertTrue(rise.get(i) > rise.get(i - 1), rise::toString);
        }
        // Checks the alpha after the rise.
        assertEquals(227, rise.get(rise.size() - 1));
        List<Integer> fall = ticks(30);
        for (int i = 1; i < fall.size(); i++) {
            assertTrue(fall.get(i) <= fall.get(i - 1), fall::toString);
        }
        assertTrue(fall.get(0) < 227);
        assertTrue(fall.get(fall.size() - 1) <= AutosaveIndicator.MIN_ALPHA, fall::toString);
    }

    @Test
    void aLongSaveKeepsRisingPastTheHold() {
        List<Integer> during = new ArrayList<>();
        WorldSaveTracker.duringSave(() -> during.addAll(ticks(30)));
        assertTrue(during.get(during.size() - 1) >= 254, during::toString);
        assertTrue(ticks(1).get(0) < 254);
    }

    @Test
    void leavingTheWorldHidesAtOnceAndForgetsSavesMadeAway() {
        WorldSaveTracker.duringSave(() -> {});
        ticks(5);
        assertTrue(alpha() > 0);
        AutosaveIndicator.tick(false);
        assertEquals(0, alpha());
        assertEquals(0, AutosaveIndicator.alpha(0F));
        // The server's quit save is counted while away and must not flash on the next world
        WorldSaveTracker.duringSave(() -> {});
        AutosaveIndicator.tick(false);
        assertTrue(ticks(5).stream().allMatch(a -> a == 0));
    }

    @Test
    void partialTicksBlendTheLastTwoTicksAndAlphaIsClamped() {
        WorldSaveTracker.duringSave(() -> {});
        ticks(2);
        int previous = AutosaveIndicator.alpha(0F);
        int current = AutosaveIndicator.alpha(1F);
        int middle = AutosaveIndicator.alpha(0.5F);
        assertTrue(previous < middle && middle < current, previous + " " + middle + " " + current);
        Statics.set(AutosaveIndicator.class, "value", 2F);
        Statics.set(AutosaveIndicator.class, "last", 2F);
        assertEquals(255, alpha());
        Statics.set(AutosaveIndicator.class, "value", -1F);
        Statics.set(AutosaveIndicator.class, "last", -1F);
        assertEquals(0, alpha());
    }

    @Test
    void theSaveCountWrapsWithoutLosingASave() {
        int real = WorldSaveTracker.started();
        try {
            Statics.set(WorldSaveTracker.class, "started", Integer.MAX_VALUE);
            AutosaveIndicator.tick(false);
            WorldSaveTracker.duringSave(() -> {});
            assertEquals(Integer.MIN_VALUE, WorldSaveTracker.started());
            assertTrue(ticks(1).get(0) > 0);
        } finally {
            Statics.set(WorldSaveTracker.class, "started", real);
            AutosaveIndicator.tick(false);
        }
    }

    @Test
    void theMixinRunsTheOriginalSaveInsideTheTracker() {
        int before = WorldSaveTracker.started();
        List<Object[]> calls = new ArrayList<>();
        Operation<Void> original = args -> {
            assertTrue(WorldSaveTracker.saving());
            calls.add(args);
            return null;
        };
        Mixins.call(Mixins.instance(ServerSaveIndicatorMixin.class), "impetus$trackSave", true, original);
        assertEquals(1, calls.size());
        assertArrayEquals(new Object[] {true}, calls.get(0));
        assertFalse(WorldSaveTracker.saving());
        assertEquals(before + 1, WorldSaveTracker.started());
    }
}
