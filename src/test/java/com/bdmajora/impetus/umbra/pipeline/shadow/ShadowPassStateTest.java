package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Covers the culling and depth state saved around the shadow pass
class ShadowPassStateTest {
    // GL model of the test
    private PipelineGl gl;

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Opens a fresh GL model with vanilla caches
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        GlCache.reset();
    }

    // Closes the GL model and resets the caches
    @AfterEach
    void close() {
        GlCache.reset();
        this.gl.close();
    }

    // Answers the culling switch and depth function queries
    private static void live(boolean cull, int depthFunc) {
        when(TestGl.gl().glGetBoolean(GL11.GL_CULL_FACE)).thenReturn(cull);
        when(TestGl.gl().glGetInteger(GL11.GL_DEPTH_FUNC)).thenReturn(depthFunc);
    }

    // Checks that an enabled culling switch and a custom depth function come back after the pass
    @Test
    void enabledCullingAndTheDepthFunctionComeBack() {
        live(true, GL11.GL_GREATER);
        ShadowPassState state = new ShadowPassState();
        state.save();
        GlCache.seed(false, false, true, false, GL11.GL_LEQUAL, 1.0, 0, 0, 0, 0);
        state.restore();
        assertTrue(GlCache.cull());
        assertEquals(GL11.GL_GREATER, GlCache.depthFunc());
        assertTrue(GlCache.depthMask());
    }

    // Checks that a disabled culling switch comes back disabled
    @Test
    void disabledCullingComesBackDisabled() {
        live(false, GL11.GL_LEQUAL);
        ShadowPassState state = new ShadowPassState();
        state.save();
        GlCache.seed(true, false, true, true, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        state.restore();
        assertFalse(GlCache.cull());
        assertEquals(GL11.GL_LEQUAL, GlCache.depthFunc());
    }

    // Checks that restore without a save and a second restore change nothing
    @Test
    void restoreRunsOnlyOncePerSave() {
        ShadowPassState state = new ShadowPassState();
        GlCache.seed(true, false, true, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        state.restore();
        assertTrue(GlCache.cull());
        assertFalse(GlCache.depthMask());
        assertEquals(GL11.GL_ALWAYS, GlCache.depthFunc());
        live(false, GL11.GL_LESS);
        state.save();
        state.restore();
        GlCache.seed(true, false, true, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        state.restore();
        assertTrue(GlCache.cull());
        assertEquals(GL11.GL_ALWAYS, GlCache.depthFunc());
    }

    // Checks that the latest save wins and an unexpected depth value is restored as read
    @Test
    void theLatestSaveWinsAndValuesPassThrough() {
        ShadowPassState state = new ShadowPassState();
        live(true, GL11.GL_GREATER);
        state.save();
        live(false, 0);
        state.save();
        GlCache.seed(true, false, true, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        state.restore();
        assertFalse(GlCache.cull());
        assertEquals(0, GlCache.depthFunc());
        live(true, Integer.MIN_VALUE);
        state.save();
        state.restore();
        assertEquals(Integer.MIN_VALUE, GlCache.depthFunc());
    }

    // Checks the depth and culling state that prepare leaves
    @Test
    void prepareEnablesDepthWritesAndDisablesCulling() {
        GlCache.seed(true, true, false, false, GL11.GL_ALWAYS, 0.25, 0, 0, 0, 0);
        ShadowPassState.prepare();
        assertTrue(GlCache.depthTest());
        assertTrue(GlCache.depthMask());
        assertEquals(GL11.GL_LEQUAL, GlCache.depthFunc());
        assertEquals(1.0, GlCache.clearDepth());
        assertFalse(GlCache.cull());
        assertTrue(GlCache.blend());
    }
}
