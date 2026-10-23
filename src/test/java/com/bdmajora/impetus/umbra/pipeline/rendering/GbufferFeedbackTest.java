package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.Mockito;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GbufferFeedbackTest {
    private static final int PROGRAM = 40;
    private PipelineGl gl;
    private UmbraRenderTargets targets;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(320, 240);
        for (int i = 0; i < 8; i++) {
            this.targets.getOrCreate(i);
        }
        Mockito.clearInvocations(TestGl.gl());
    }

    @AfterEach
    void close() {
        this.targets.destroy();
        this.gl.close();
    }

    private static void samples(String name) {
        when(TestGl.gl().glGetUniformLocation(eq(PROGRAM), argThat((CharSequence s) -> s != null && s.toString().equals(name)))).thenReturn(3);
    }

    private static void verifyCopies(int count) {
        verify(TestGl.gl(), times(count)).glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void onlyTargetsTheBoundProgramBothWritesAndSamplesAreCopied() {
        samples("gaux3");
        this.gl.program = PROGRAM;
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        BitSet active = new BitSet();
        active.set(5);
        // colortex6 is written and sampled (as gaux3), colortex5 and 0 only written
        BitSet flips = feedback.prepare(new int[]{0, 5, 6}, active);
        verifyCopies(1);
        UmbraRenderTarget six = this.targets.get(6);
        verify(TestGl.gl()).glFramebufferTexture2D(anyInt(), anyInt(), eq(GL11.GL_TEXTURE_2D), eq(six.getMainTexture()), eq(0));
        // Into the back side, bound on the scratch unit and put back afterwards
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, six.getAltTexture());
        assertEquals(0, this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT]);
        // The copy is read from: 6 flips, 5 keeps its flip, and the active snapshot itself is untouched
        assertTrue(flips.get(6));
        assertTrue(flips.get(5));
        assertNotSame(active, flips);
        assertFalse(active.get(6));
        // Nothing to copy returns the snapshot itself
        assertSame(active, feedback.prepare(new int[]{0, 5}, active));
        verifyCopies(1);
    }

    @Test
    void theSampledSetIsAskedOncePerProgram() {
        samples("colortex4");
        this.gl.program = PROGRAM;
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        feedback.prepare(new int[]{4}, new BitSet());
        feedback.prepare(new int[]{4}, new BitSet());
        assertEquals(1 << 4, feedback.sampledGaux());
        // Four targets by up to two names each (colortex4 answers before gaux1 is asked), on the first prepare only
        verify(TestGl.gl(), times(7)).glGetUniformLocation(eq(PROGRAM), any(CharSequence.class));
        verifyCopies(2);
    }

    @Test
    void withNoProgramBoundEveryWrittenGauxTargetIsCopied() {
        this.gl.program = 0;
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        assertEquals(~0, feedback.sampledGaux());
        BitSet flips = feedback.prepare(new int[]{0, 2, 4, 7, 9}, new BitSet());
        // colortex0/2 are not gbuffer-sampled targets, colortex9 has none of the aux units
        verifyCopies(2);
        assertTrue(flips.get(4));
        assertTrue(flips.get(7));
        assertFalse(flips.get(2));
        verify(TestGl.gl(), never()).glGetUniformLocation(anyInt(), any(CharSequence.class));
    }

    @Test
    void aMissingTargetIsNotCopiedAndASizedOneIsCopiedAtItsOwnSize() {
        UmbraRenderTargets sized = new UmbraRenderTargets(320, 240);
        try {
            sized.setColorSize(5, 64, 32, new boolean[]{false, false});
            sized.getOrCreate(5);
            this.gl.program = 0;
            GbufferFeedback feedback = new GbufferFeedback(sized);
            feedback.prepare(new int[]{4, 5}, new BitSet());
            // colortex4 was never created; colortex5 copies 64x32, the screen size used to be an invalid region
            verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 64, 32);
            verifyCopies(1);
            feedback.destroy();
        } finally {
            sized.destroy();
        }
    }

    @Test
    void gauxTargetsBindOnUnits7To10FollowingTheFlips() {
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        BitSet flips = new BitSet();
        flips.set(5);
        flips.set(7);
        feedback.bindColorSamplers(flips);
        assertEquals(this.targets.get(4).getMainTexture(), this.gl.textures[7]);
        assertEquals(this.targets.get(4).getMainTexture(), PipelineGl.cachedTexture(7));
        assertEquals(this.targets.get(5).getAltTexture(), this.gl.textures[8]);
        assertEquals(this.targets.get(6).getMainTexture(), this.gl.textures[9]);
        assertEquals(this.targets.get(7).getAltTexture(), this.gl.textures[10]);
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
        // A gaux target that does not exist is skipped
        UmbraRenderTargets empty = new UmbraRenderTargets(4, 4);
        try {
            Mockito.clearInvocations(TestGl.gl());
            new GbufferFeedback(empty).bindColorSamplers(flips);
            verify(TestGl.gl(), never()).glBindTexture(anyInt(), anyInt());
        } finally {
            empty.destroy();
        }
    }

    @Test
    void destroyFreesTheCopyFramebufferOnce() {
        this.gl.program = 0;
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        feedback.destroy();
        verify(TestGl.gl(), never()).glDeleteFramebuffers(anyInt());
        feedback.prepare(new int[]{4}, new BitSet());
        feedback.prepare(new int[]{5}, new BitSet());
        // One scratch framebuffer serves every copy
        verify(TestGl.gl(), times(1)).glGenFramebuffers();
        feedback.destroy();
        feedback.destroy();
        verify(TestGl.gl(), times(1)).glDeleteFramebuffers(anyInt());
    }

    @Test
    void aFlippedTargetIsCopiedFromItsAltSideIntoMain() {
        this.gl.program = 0;
        GbufferFeedback feedback = new GbufferFeedback(this.targets);
        BitSet active = new BitSet();
        active.set(7);
        BitSet flips = feedback.prepare(new int[]{7}, active);
        UmbraRenderTarget seven = this.targets.get(7);
        verify(TestGl.gl()).glFramebufferTexture2D(anyInt(), anyInt(), eq(GL11.GL_TEXTURE_2D), eq(seven.getAltTexture()), eq(0));
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, seven.getMainTexture());
        assertFalse(flips.get(7));
        feedback.destroy();
    }
}
