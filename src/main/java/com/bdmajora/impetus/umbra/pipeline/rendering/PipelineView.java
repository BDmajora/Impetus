package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;

// What the pipeline's entry points read from its state, so the state's fields stay package-private
public final class PipelineView {
    private final PipelineState state;

    public PipelineView(PipelineState state) {
        this.state = state;
    }

    // Between beginWorldRendering and its end
    public boolean isActive() {
        return this.state.active;
    }

    // Between the deferred and composite stages
    public boolean isPostDeferred() {
        return this.state.active && this.state.isPostDeferred();
    }

    // The camera pass, not the shadow pass that renders entities through the same vanilla renderers first
    public boolean isCameraPassActive() {
        return this.state.active && !UmbraShadowRenderer.isShadowPass();
    }

    // The shaders.properties switches
    public PackSwitches switches() {
        return this.state.switches;
    }

    // The Distant Horizons compat, null once destroyed
    public DhCompat dhCompat() {
        return this.state.dhCompat;
    }

    // The shadow pass, or null when the pack draws no shadow map
    public UmbraShadowRenderer shadowRenderer() {
        return this.state.shadowRenderer;
    }

    // The sampler flavour on the shadow units for the program about to draw
    public void applyShadowSamplerKinds(ShadowSamplerKinds kinds, boolean gbufferStage) {
        this.state.shadowSamplers.apply(kinds, gbufferStage);
    }

    // The pack's custom textures and images; nothing once destroyed
    public void bindPackResources() {
        this.state.binder.bindPackResources();
    }
}
