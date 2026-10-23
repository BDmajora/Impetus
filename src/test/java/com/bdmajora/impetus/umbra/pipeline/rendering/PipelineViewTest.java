package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class PipelineViewTest {
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PipelineView view;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.pipeline = PipelineGl.pipeline("/shaders.properties", "rain.depth = true");
        this.state = PipelineGl.state(this.pipeline);
        this.view = new PipelineView(this.state);
    }

    @AfterEach
    void close() {
        this.pipeline.destroy();
        this.gl.close();
    }

    @Test
    void theViewAnswersFromTheStateAndTheCameraPassExcludesTheShadowPass() {
        assertFalse(this.view.isActive());
        assertFalse(this.view.isPostDeferred());
        assertFalse(this.view.isCameraPassActive());
        assertTrue(this.view.switches().rainDepth);
        assertSame(this.state.dhCompat, this.view.dhCompat());
        assertNull(this.view.shadowRenderer());
        PipelineGl.enterFrame(this.state);
        assertTrue(this.view.isActive());
        assertTrue(this.view.isCameraPassActive());
        // No deferred chain: one gbuffer for the frame, never "post-deferred"
        assertFalse(this.view.isPostDeferred());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertFalse(this.view.isCameraPassActive());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
    }

    @Test
    void samplerKindsAndPackResourcesGoToTheirOwners() {
        ShadowSamplers samplers = Mockito.spy(this.state.shadowSamplers);
        Mixins.set(this.state, "shadowSamplers", samplers);
        TextureBinder binder = Mockito.spy(this.state.binder);
        Mixins.set(this.state, "binder", binder);
        this.view.applyShadowSamplerKinds(ShadowSamplerKinds.ALL_COMPARE, true);
        verify(samplers).apply(ShadowSamplerKinds.ALL_COMPARE, true);
        this.view.bindPackResources();
        verify(binder).bindPackResources();
    }
}
