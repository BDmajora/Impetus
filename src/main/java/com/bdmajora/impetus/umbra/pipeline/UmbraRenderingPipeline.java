package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.rendering.FrameLifecycle;
import com.bdmajora.impetus.umbra.pipeline.rendering.HandPass;
import com.bdmajora.impetus.umbra.pipeline.rendering.PassRunner;
import com.bdmajora.impetus.umbra.pipeline.rendering.PhaseController;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineState;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineView;
import com.bdmajora.impetus.umbra.pipeline.rendering.SkyHorizon;
import com.bdmajora.impetus.umbra.pipeline.rendering.TerrainHooks;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;

// Facade of a loaded shader pack that the render hooks call into.
public class UmbraRenderingPipeline {
    // Resources of the pack.
    private final PipelineState state;
    // Read only queries of the state.
    private final PipelineView view;
    // Program phase selection.
    private final PhaseController phases;
    // Begin and end of each frame.
    private final FrameLifecycle frame;
    // Hand pass.
    private final HandPass hand;
    // Terrain draw hooks.
    private final TerrainHooks terrain;
    // Sky horizon renderer.
    private final SkyHorizon horizon = new SkyHorizon();

    // Loads a pack and releases its resources when loading fails.
    public UmbraRenderingPipeline(ShaderPack pack) {
        this.state = new PipelineState(pack);
        // Releases the state when a later part fails
        try {
            this.view = new PipelineView(this.state);
            this.phases = new PhaseController(this.state);
            this.frame = new FrameLifecycle(this.state, new PassRunner(this.state), this.phases);
            this.hand = new HandPass(this.state, this.phases);
            this.terrain = new TerrainHooks(this.state, this.phases);
            // Last: the DH bridge calls createDhFramebuffer and getShadowRenderer, which read the parts above
            this.state.attachDistantHorizons(this, pack);
        } catch (RuntimeException | Error e) {
            try {
                this.state.destroy();
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    // Starts a frame.
    public void beginWorldRendering(float partialTicks) {
        this.frame.begin(partialTicks);
    }

    // Returns whether a phase has a gbuffer program or a fallback.
    public boolean hasGbufferProgram(ProgramId phase) {
        return this.phases.has(phase);
    }

    // Returns whether a phase has its own gbuffer program.
    public boolean hasDirectGbufferProgram(ProgramId phase) {
        return this.phases.hasDirect(phase);
    }

    // Returns whether translucents draw after the deferred passes.
    public boolean isRenderingPostDeferredTranslucents() {
        return this.view.isPostDeferred();
    }

    // Returns the phase of translucent entities.
    public ProgramId getTranslucentEntityPhase() {
        return this.phases.translucentEntityPhase();
    }

    // Returns the selected phase.
    public ProgramId getCurrentPhase() {
        return this.phases.current();
    }

    // Returns whether the camera pass is drawing.
    public boolean isCameraPassActive() {
        return this.view.isCameraPassActive();
    }

    // Selects a phase at its default render stage.
    public void setPhase(ProgramId phase) {
        setPhase(phase, PhaseController.defaultRenderStage(phase));
    }

    // Records a lightmap toggle.
    public void setLightmapEnabled(boolean enabled) {
        this.phases.lightmapToggled(enabled);
    }

    // Selects a phase at a render stage.
    public void setPhase(ProgramId phase, int renderStage) {
        this.phases.select(phase, renderStage);
    }

    // Uploads the dynamic uniforms of the bound program.
    public void refreshDynamicUniforms() {
        this.phases.refreshDynamicUniforms();
    }

    // Selects the spider eyes phase when entering it.
    public void beginEyes() {
        if (this.phases.enterEyes()) {
            setPhase(ProgramId.SpiderEyes);
        }
    }

    // Leaves the spider eyes phase.
    public void endEyes() {
        this.phases.exitEyes();
    }

    // Selects the armor glint phase when entering it.
    public void beginArmorGlint() {
        if (this.phases.enterGlint()) {
            setPhase(ProgramId.ArmorGlint);
        }
    }

    // Leaves the armor glint phase.
    public void endArmorGlint() {
        this.phases.exitGlint();
    }

    // Draws the sky horizon while the world renders.
    public void drawSkyHorizon() {
        if (this.view.isActive()) {
            this.horizon.draw();
        }
    }

    // Prepares a terrain draw without an alpha test override.
    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState) {
        onTerrainDraw(drawBuffers, blendState, ProgramAlphaTest.empty(), false);
    }

    // Prepares a terrain draw.
    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState, ProgramAlphaTest alphaTest,
                              boolean translucentPass) {
        this.terrain.onTerrainDraw(drawBuffers, blendState, alphaTest, translucentPass);
    }

    // Restores the state after a terrain draw.
    public void afterTerrainDraw(int drawBufferSlots) {
        this.terrain.afterTerrainDraw(drawBufferSlots);
    }

    // Returns the Distant Horizons bridge or null.
    public DhCompat getDhCompat() {
        return this.view.dhCompat();
    }

    // Returns the shadow renderer or null.
    public UmbraShadowRenderer getShadowRenderer() {
        return this.view.shadowRenderer();
    }

    // Creates a framebuffer of a Distant Horizons pass.
    public UmbraFramebuffer createDhFramebuffer(int[] drawBuffers, boolean translucent) {
        return this.terrain.createDhFramebuffer(drawBuffers, translucent);
    }

    // Prepares a Distant Horizons draw.
    public void onDhLodDraw(UmbraFramebuffer framebuffer, int[] drawBuffers, ProgramBlendState blendState,
                            boolean translucentPass) {
        this.terrain.onDhLodDraw(framebuffer, drawBuffers, blendState, translucentPass);
    }

    // Restores the state after a Distant Horizons draw.
    public void afterDhLodDraw(int drawBufferSlots) {
        this.terrain.afterDhLodDraw(drawBufferSlots);
    }

    // Records the camera and matrices of the frame.
    public void captureRenderingState() {
        this.frame.captureRenderingState();
    }

    // Draws the shadow pass.
    public void renderShadowMap() {
        this.frame.renderShadowMap();
    }

    // Runs the deferred passes before the translucents.
    public void beginTranslucents() {
        this.frame.beginTranslucents();
    }

    // Prepares the hand pass.
    public void beginHand() {
        this.frame.beginHand();
    }

    // Starts the solid hand pass and returns whether it runs.
    public boolean beginHandRendering() {
        return this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE);
    }

    // Starts the translucent hand pass and returns whether it runs.
    public boolean beginHandTranslucentRendering() {
        return this.hand.begin(ProgramId.HandWater, HandPass.TRANSLUCENT_STAGE);
    }

    // Ends a hand pass.
    public void endHandRendering() {
        this.hand.end();
    }

    // Runs the composite passes and ends the frame.
    public void finishWorldRendering() {
        this.frame.finish();
    }

    // Returns whether the world is rendering.
    public boolean isWorldRenderingActive() {
        return this.view.isActive();
    }

    // Returns whether weather writes depth.
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return this.view.switches().rainDepth;
    }

    // Returns whether the pack wants beacon beams written to the depth buffer.
    public boolean shouldWriteBeaconBeamToDepthBuffer() {
        return this.view.switches().beaconBeamDepth;
    }

    // Returns whether the pack disables frustum culling.
    public boolean shouldDisableFrustumCulling() {
        return !this.view.switches().frustumCulling;
    }

    // Returns whether the pack disables occlusion culling.
    public boolean shouldDisableOcclusionCulling() {
        return !this.view.switches().occlusionCulling;
    }

    // Returns whether the pack skips every vanilla draw.
    public boolean skipAllRendering() {
        return this.view.switches().skipAllRendering;
    }

    // Returns whether entities draw one at a time.
    public boolean shouldSeparateEntityDraws() {
        return this.view.switches().separateEntityDraws;
    }

    // Returns the particle ordering of the pack.
    public String getParticleOrdering() {
        return this.view.switches().particleOrdering;
    }

    // Returns whether a layer culls back faces.
    public boolean shouldCullBackFaces(int layerOrdinal) {
        return this.view.switches().cullsBackFaces(layerOrdinal);
    }

    // Returns whether the pack draws its own shadows.
    public boolean shouldDisableVanillaEntityShadows() {
        return this.view.shadowRenderer() != null;
    }

    // Binds the shadow samplers of a program.
    public void applyShadowSamplerKinds(ShadowSamplerKinds kinds, boolean gbufferStage) {
        this.view.applyShadowSamplerKinds(kinds, gbufferStage);
    }

    // Binds the pack textures and images.
    public void bindCustomImages() {
        this.view.bindPackResources();
    }

    // Releases every resource of the pack.
    public void destroy() {
        this.state.destroy();
    }
}
