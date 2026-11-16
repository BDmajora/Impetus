package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;

// Read only view of the pipeline state.
public final class PipelineView {
    // Pipeline state being viewed.
    private final PipelineState state;

    // Creates a pipeline view from its state.
    public PipelineView(PipelineState state) {
        this.state = state;
    }

    // Returns whether active.
    public boolean isActive() {
        return this.state.active;
    }

    // Returns whether post deferred.
    public boolean isPostDeferred() {
        return this.state.active && this.state.isPostDeferred();
    }

    // Returns whether camera pass active.
    public boolean isCameraPassActive() {
        return this.state.active && !UmbraShadowRenderer.isShadowPass();
    }

    // Returns the pack switches.
    public PackSwitches switches() {
        return this.state.switches;
    }

    // Returns the Distant Horizons state.
    public DhCompat dhCompat() {
        return this.state.dhCompat;
    }

    // Returns the shadow renderer.
    public UmbraShadowRenderer shadowRenderer() {
        return this.state.shadowRenderer;
    }

    // Applies the shadow sampler kinds.
    public void applyShadowSamplerKinds(ShadowSamplerKinds kinds, boolean gbufferStage) {
        this.state.shadowSamplers.apply(kinds, gbufferStage);
    }

    // Binds the pack textures.
    public void bindPackResources() {
        this.state.binder.bindPackResources();
    }
}
