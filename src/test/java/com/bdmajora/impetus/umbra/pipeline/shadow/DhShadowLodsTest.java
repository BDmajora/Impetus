package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the Distant Horizons shadow hook
class DhShadowLodsTest {
    // Distant Horizons calls made by the stand in handles in order
    private static final List<String> CALLS = new ArrayList<>();
    // Flag that makes the stand in handles throw
    private static boolean failing;

    // GL model of the test
    private PipelineGl gl;
    // Presence flag of Distant Horizons before the test
    private boolean present;
    // Rendering switch handle before the test
    private MethodHandle enabled;
    // Opaque draw handle before the test
    private MethodHandle solid;
    // Translucent draw handle before the test
    private MethodHandle deferred;

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Records an opaque draw
    static void recordSolid() {
        CALLS.add("solid");
        if (failing) {
            throw new IllegalStateException("solid");
        }
    }

    // Records a translucent draw
    static void recordDeferred() {
        CALLS.add("deferred");
    }

    // Marks Distant Horizons present with recording handles
    @BeforeEach
    void open() throws ReflectiveOperationException {
        this.gl = new PipelineGl();
        GlCache.reset();
        CALLS.clear();
        failing = false;
        this.present = Statics.get(DhCompat.class, "dhPresent");
        this.enabled = Statics.get(DhCompat.class, "isRenderingEnabled");
        this.solid = Statics.get(DhCompat.class, "renderShadowSolid");
        this.deferred = Statics.get(DhCompat.class, "renderDeferredLods");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Statics.set(DhCompat.class, "dhPresent", true);
        Statics.set(DhCompat.class, "isRenderingEnabled", MethodHandles.constant(boolean.class, true));
        Statics.set(DhCompat.class, "renderShadowSolid",
                lookup.findStatic(DhShadowLodsTest.class, "recordSolid", MethodType.methodType(void.class)));
        Statics.set(DhCompat.class, "renderDeferredLods",
                lookup.findStatic(DhShadowLodsTest.class, "recordDeferred", MethodType.methodType(void.class)));
    }

    // Restores the Distant Horizons state and the pipeline
    @AfterEach
    void close() {
        Statics.set(DhCompat.class, "dhPresent", this.present);
        Statics.set(DhCompat.class, "isRenderingEnabled", this.enabled);
        Statics.set(DhCompat.class, "renderShadowSolid", this.solid);
        Statics.set(DhCompat.class, "renderDeferredLods", this.deferred);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        GlCache.reset();
        this.gl.close();
    }

    // Installs a pipeline whose compat answers the shadow switch
    private DhCompat install(boolean shadows) {
        UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
        DhCompat compat = mock(DhCompat.class);
        when(compat.shouldRenderShadows()).thenReturn(shadows);
        when(pipeline.getDhCompat()).thenReturn(compat);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        return compat;
    }

    // Checks every condition that keeps the hook inactive and the one combination that activates it
    @Test
    void theHookIsActiveOnlyWithAPipelineACompatShadowsAndRendering() {
        assertFalse(DhShadowLods.active());
        UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        assertFalse(DhShadowLods.active());
        install(false);
        assertFalse(DhShadowLods.active());
        install(true);
        assertTrue(DhShadowLods.active());
        Statics.set(DhCompat.class, "isRenderingEnabled", MethodHandles.constant(boolean.class, false));
        assertFalse(DhShadowLods.active());
        Statics.set(DhCompat.class, "dhPresent", false);
        assertFalse(DhShadowLods.active());
    }

    // Checks that the opaque and translucent draws reach their handles and the pass state comes back
    @Test
    void eachLayerDrawsAndThePassStateComesBack() {
        ShadowTargets targets = mock(ShadowTargets.class);
        GlCache.seed(true, true, true, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        this.gl.activeUnit = 4;
        DhShadowLods.render(false, targets);
        DhShadowLods.render(true, targets);
        assertEquals(List.of("solid", "deferred"), CALLS);
        InOrder order = Mockito.inOrder(TestGl.gl(), targets);
        order.verify(TestGl.gl()).glUseProgram(0);
        order.verify(TestGl.gl()).glBindVertexArray(0);
        order.verify(TestGl.gl()).glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        order.verify(TestGl.gl()).glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        order.verify(targets).bind();
        verify(targets, Mockito.times(2)).bind();
        assertFalse(GlCache.cull());
        assertFalse(GlCache.blend());
        assertTrue(GlCache.depthMask());
        assertEquals(GL11.GL_LEQUAL, GlCache.depthFunc());
        assertEquals(0, this.gl.activeUnit);
    }

    // Checks that a throwing draw still restores the pass state and propagates
    @Test
    void aThrowingDrawStillRestoresThePassState() {
        failing = true;
        ShadowTargets targets = mock(ShadowTargets.class);
        GlCache.seed(true, true, true, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> DhShadowLods.render(false, targets));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        verify(targets).bind();
        assertFalse(GlCache.cull());
        assertEquals(GL11.GL_LEQUAL, GlCache.depthFunc());
    }

    // Checks that without Distant Horizons the draw is skipped and the pass state still comes back
    @Test
    void withoutDistantHorizonsOnlyThePassStateIsRestored() {
        Statics.set(DhCompat.class, "dhPresent", false);
        ShadowTargets targets = mock(ShadowTargets.class);
        DhShadowLods.render(false, targets);
        DhShadowLods.render(true, targets);
        assertTrue(CALLS.isEmpty());
        verify(targets, Mockito.times(2)).bind();
    }

    // Checks that null targets fail after the draw
    @Test
    void nullTargetsFailAfterTheDraw() {
        assertThrows(NullPointerException.class, () -> DhShadowLods.render(false, null));
        assertEquals(List.of("solid"), CALLS);
    }

    // Checks that the private constructor runs
    @Test
    void theConstructorIsPrivate() {
        assertNotNull(Mixins.construct(DhShadowLods.class));
    }
}
