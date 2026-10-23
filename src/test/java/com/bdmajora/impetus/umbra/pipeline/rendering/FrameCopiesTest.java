package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.TestGl;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrameCopiesTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
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
    }

    @AfterEach
    void close() {
        this.targets.destroy();
        this.gl.close();
    }

    // colortex2 keeps its contents, colortex4 is 64x32 like a bloom buffer, colortex0..5 exist
    private TargetDirectives directives() {
        this.targets.setColorSize(4, 64, 32, new boolean[]{false, false});
        for (int i = 0; i < 6; i++) {
            this.targets.getOrCreate(i);
        }
        TargetDirectives directives = new TargetDirectives();
        directives.apply(new PackSources(PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh",
                "const bool colortex2Clear = false;\nconst vec4 colortex5ClearColor = vec4(1.0);\nvoid main() {}").getProgramSet(),
                java.util.Map.of()), this.targets);
        return directives;
    }

    private static void verifyClears(int count) {
        verify(TestGl.gl(), times(count)).glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    @Test
    void targetsSharingAColourAndSizeClearInOneFramebuffer() {
        FrameCopies copies = new FrameCopies();
        TargetDirectives directives = directives();
        Mockito.clearInvocations(TestGl.gl());
        copies.build(this.targets, directives, new BufferFlipper(), 8);
        // fog {0}, white {1, 5}, black at render size {2, 3}, black at 64x32 {4}: four groups, both sides, for each of the two clear sets
        verify(TestGl.gl(), times(16)).glGenFramebuffers();
        Mockito.clearInvocations(TestGl.gl());
        copies.clear(true, 320, 240);
        verifyClears(8);
        // glClear ignores the viewport, so it is set once, after the clears, to the render size
        verify(TestGl.gl(), times(1)).glViewport(anyInt(), anyInt(), anyInt(), anyInt());
        verify(TestGl.gl()).glViewport(0, 0, 320, 240);
        Mockito.clearInvocations(TestGl.gl());
        // Every frame colortex2 keeps its contents, but black {3} still has a group
        copies.clear(false, 320, 240);
        verifyClears(8);
    }

    @Test
    void groupsAreCutToTheDriversDrawBufferLimit() {
        FrameCopies copies = new FrameCopies();
        TargetDirectives directives = directives();
        Mockito.clearInvocations(TestGl.gl());
        copies.build(this.targets, directives, new BufferFlipper(), 1);
        // Six targets on their own, both sides, for the full clear; colortex2 drops out of the per-frame one
        Mockito.clearInvocations(TestGl.gl());
        copies.clear(true, 320, 240);
        verifyClears(12);
        Mockito.clearInvocations(TestGl.gl());
        copies.clear(false, 320, 240);
        verifyClears(10);
        // A non-positive limit still makes progress
        FrameCopies unlimited = new FrameCopies();
        unlimited.build(this.targets, directives, new BufferFlipper(), 0);
        Mockito.clearInvocations(TestGl.gl());
        unlimited.clear(true, 320, 240);
        verifyClears(12);
    }

    @Test
    void theFogTargetClearsToTheCurrentFogColourAndTheOthersToTheirOwn() {
        FrameCopies copies = new FrameCopies();
        TargetDirectives directives = directives();
        copies.build(this.targets, directives, new BufferFlipper(), 8);
        Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
        Vector3f saved = new Vector3f(fog);
        fog.set(0.25f, 0.5f, 0.75f);
        try {
            copies.clear(true, 320, 240);
            this.gl.gl11.verify(() -> GL11.glClearColor(0.25f, 0.5f, 0.75f, 1.0f), Mockito.atLeastOnce());
            this.gl.gl11.verify(() -> GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f), Mockito.atLeastOnce());
            this.gl.gl11.verify(() -> GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f), Mockito.atLeastOnce());
        } finally {
            fog.set(saved);
        }
    }

    @Test
    void onlyFlippedBuffersThatKeepTheirContentsAreCopiedBackAtTheirCurrentSize() {
        FrameCopies copies = new FrameCopies();
        TargetDirectives directives = directives();
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(1);
        flipper.flip(2);
        flipper.flip(9);
        copies.build(this.targets, directives, flipper, 8);
        Mockito.clearInvocations(TestGl.gl());
        copies.swap(this.targets);
        // colortex1 clears every frame and colortex9 does not exist, so only colortex2 swaps
        verify(TestGl.gl(), times(1)).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 320, 240);
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, this.targets.get(2).getMainTexture());
        assertEquals(0, this.gl.activeUnit);
        // After the window shrinks the copy follows the new size; the size baked at build was an invalid region
        this.targets.resize(160, 120);
        Mockito.clearInvocations(TestGl.gl());
        copies.swap(this.targets);
        verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 160, 120);
        copies.destroy();
        verify(TestGl.gl(), times(1)).glDeleteFramebuffers(anyInt());
        Mockito.clearInvocations(TestGl.gl());
        copies.swap(this.targets);
        copies.clear(true, 160, 120);
        verify(TestGl.gl(), never()).glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verifyClears(0);
    }

    @Test
    void anIncompleteSwapFramebufferIsFreedBeforeTheFailureEscapes() {
        FrameCopies copies = new FrameCopies();
        TargetDirectives directives = directives();
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(2);
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        Mockito.clearInvocations(TestGl.gl());
        assertThrows(IllegalStateException.class, () -> copies.build(this.targets, directives, flipper, 8));
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
        // It failed before any clear framebuffer was made
        verify(TestGl.gl(), times(1)).glGenFramebuffers();
    }
}
