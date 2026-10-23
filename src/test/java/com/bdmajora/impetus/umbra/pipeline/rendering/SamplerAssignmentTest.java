package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SamplerAssignmentTest {
    private static final int PROGRAM = 9;

    @AfterEach
    void withdrawEverything() {
        SamplerAssignment.publish(this, Map.of());
        SamplerAssignment.withdraw(this);
    }

    // The program declares exactly these names, each at its own location (1000 + index)
    private static void declares(String... names) {
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            when(TestGl.gl().glGetUniformLocation(Mockito.eq(PROGRAM), argThat((CharSequence s) -> s != null && s.toString().equals(name))))
                    .thenReturn(1000 + i);
        }
    }

    private static Map<String, CustomTextureManager.Override> overrides(Object... nameUnitTarget) {
        Map<String, CustomTextureManager.Override> overrides = new LinkedHashMap<>();
        for (int i = 0; i < nameUnitTarget.length; i += 3) {
            overrides.put((String) nameUnitTarget[i], new CustomTextureManager.Override((Integer) nameUnitTarget[i + 1], (Integer) nameUnitTarget[i + 2]));
        }
        return overrides;
    }

    @Test
    void onlyDeclaredSamplersAreAssignedTheirLayoutUnit() {
        declares("colortex3", "depthtex0");
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), Map.of(), new BitSet());
        verify(TestGl.gl()).glUniform1i(1000, 3);
        verify(TestGl.gl()).glUniform1i(1001, 16);
        verify(TestGl.gl(), Mockito.times(2)).glUniform1i(anyInt(), anyInt());
    }

    @Test
    void withWatershadowTheLegacyShadowAliasReadsThePreTranslucentMapOfItsLayout() {
        declares("shadow", "watershadow");
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), Map.of(), new BitSet());
        verify(TestGl.gl()).glUniform1i(1000, SamplerLayout.SHADOW_TEX_1_UNIT);
        verify(TestGl.gl()).glUniform1i(1001, SamplerLayout.SHADOW_TEX_0_UNIT);
        Mockito.clearInvocations(TestGl.gl());
        SamplerAssignment.assign(PROGRAM, SamplerLayout.gbuffer(), Map.of(), new BitSet());
        verify(TestGl.gl()).glUniform1i(1000, SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT);
        verify(TestGl.gl()).glUniform1i(1001, SamplerLayout.GBUFFER_SHADOW_TEX_0_UNIT);
    }

    @Test
    void withoutWatershadowTheShadowAliasKeepsShadowtex0() {
        declares("shadow");
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), Map.of(), new BitSet());
        verify(TestGl.gl()).glUniform1i(1000, SamplerLayout.SHADOW_TEX_0_UNIT);
    }

    @Test
    void aColortexOverrideDeactivatesOnceAPassFlippedThatBufferButOtherOverridesNever() {
        declares("colortex5", "noisetex", "extra");
        Map<String, CustomTextureManager.Override> overrides = overrides("colortex5", 26, 5, "noisetex", 27, -1, "extra", 28, -1);
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), overrides, new BitSet());
        verify(TestGl.gl()).glUniform1i(1000, 26);
        verify(TestGl.gl()).glUniform1i(1001, 27);
        // A pack-only name with no layout unit is assigned from the second loop
        verify(TestGl.gl()).glUniform1i(1002, 28);
        Mockito.clearInvocations(TestGl.gl());
        BitSet flipped = new BitSet();
        flipped.set(5);
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), overrides, flipped);
        verify(TestGl.gl()).glUniform1i(1000, 5);
        verify(TestGl.gl()).glUniform1i(1001, 27);
        // An override target past the BitSet's size reads as never flipped
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), overrides("colortex5", 26, 9000), flipped);
        verify(TestGl.gl()).glUniform1i(1000, 26);
    }

    @Test
    void anUndeclaredPackOnlyNameIsNotAssigned() {
        declares();
        SamplerAssignment.assign(PROGRAM, SamplerLayout.fullscreen(), overrides("extra", 28, -1), new BitSet());
        verify(TestGl.gl(), never()).glUniform1i(anyInt(), anyInt());
    }

    @Test
    void theBoundProgramGetsTheGbufferLayoutWithTheLivePipelinesOverrides() {
        declares("gaux1", "extra");
        Object pipeline = new Object();
        SamplerAssignment.publish(pipeline, overrides("extra", 29, -1));
        SamplerAssignment.assignToBoundProgram(PROGRAM);
        verify(TestGl.gl()).glUniform1i(1000, 7);
        verify(TestGl.gl()).glUniform1i(1001, 29);
        assertEquals(Set.of("extra"), SamplerAssignment.activeOverrides().keySet());
        SamplerAssignment.withdraw(pipeline);
        assertTrue(SamplerAssignment.activeOverrides().isEmpty());
    }

    @Test
    void aStalePipelinesWithdrawLeavesItsSuccessorsOverrides() {
        Object old = new Object();
        Object live = new Object();
        SamplerAssignment.publish(old, overrides("a", 26, -1));
        SamplerAssignment.publish(live, overrides("b", 27, -1));
        SamplerAssignment.withdraw(old);
        assertEquals(Set.of("b"), SamplerAssignment.activeOverrides().keySet());
        SamplerAssignment.withdraw(live);
        assertTrue(SamplerAssignment.activeOverrides().isEmpty());
        // Withdrawing with nothing published is harmless
        SamplerAssignment.withdraw(live);
        assertTrue(SamplerAssignment.activeOverrides().isEmpty());
    }
}
