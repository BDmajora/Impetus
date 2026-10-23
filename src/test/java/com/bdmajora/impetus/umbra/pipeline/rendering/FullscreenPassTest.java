package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FullscreenPassTest {
    private static FullscreenPass pass(int[] size, float[] scale) {
        return new FullscreenPass("composite", null, null, null, new int[16], new int[]{0}, ProgramBlendState.empty(),
                new BitSet(), new BitSet(), List.of(), ShadowSamplerKinds.ALL_COMPARE, false, size, scale);
    }

    @Test
    void aPassDrawsAtTheRenderSizeUnlessItsBuffersHaveTheirOwn() {
        pass(new int[2], null).applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(0, 0, 320, 240);
        pass(new int[]{64, 32}, null).applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(0, 0, 64, 32);
    }

    @Test
    void scaleShrinksTheRectangleWithFractionalOffsetsAndNeverGoesNegative() {
        pass(new int[2], new float[]{0.5f, 0.25f, 0.5f}).applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(80, 120, 160, 120);
        // Truncated, not rounded, as Umbra does it
        pass(new int[]{101, 51}, new float[]{0.5f, 0.0f, 0.0f}).applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(0, 0, 50, 25);
        // A negative scale became a negative glViewport, which GL rejects and so kept the last pass's viewport
        pass(new int[2], new float[]{-1.0f, 0.0f, 0.0f}).applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(0, 0, 0, 0);
    }

    @Test
    void aComputeOnlyEntryDrawsNothingButKeepsItsChainPosition() {
        BitSet flips = new BitSet();
        flips.set(3);
        int[] samplers = new int[16];
        FullscreenPass pass = FullscreenPass.computeOnly("deferred4", samplers, flips, List.of());
        assertNull(pass.program);
        assertNull(pass.uniforms);
        assertNull(pass.framebuffer);
        assertNull(pass.blendState);
        assertSame(samplers, pass.colorSamplers);
        assertSame(flips, pass.flipsBefore);
        assertArrayEquals(new int[]{0}, pass.drawBuffers);
        assertTrue(pass.mipmappedBuffers.isEmpty());
        assertSame(ShadowSamplerKinds.ALL_COMPARE, pass.shadowSamplerKinds);
        assertFalse(pass.modern);
        assertEquals("deferred4", pass.name);
    }

    @Test
    void thePassFreesItsComputesButNotItsDrawProgram() {
        GlProgram first = mock(GlProgram.class);
        GlProgram second = mock(GlProgram.class);
        List<ComputePass> computes = List.of(
                new ComputePass(first, mock(ProgramUniforms.class), ShadowSamplerKinds.ALL_COMPARE, new int[]{1, 1, 1}, null, null, -1, 0),
                new ComputePass(second, mock(ProgramUniforms.class), ShadowSamplerKinds.ALL_COMPARE, new int[]{1, 1, 1}, null, null, -1, 0));
        FullscreenPass.computeOnly("c", new int[16], new BitSet(), computes).destroyComputes();
        verify(first).destroy();
        verify(second).destroy();
    }
}
