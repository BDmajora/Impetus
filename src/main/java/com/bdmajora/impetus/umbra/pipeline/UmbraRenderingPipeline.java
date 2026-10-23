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

// The frame pipeline's entry points for the render mixins: beginWorldRendering binds the gbuffer, captureRenderingState copies camera matrices, finishWorldRendering runs the passes ping-ponging targets like OptiFine then final into vanilla's framebuffer; the work lives in the rendering package, render thread only
public class UmbraRenderingPipeline {
    private final PipelineState state;
    private final PipelineView view;
    private final PhaseController phases;
    private final FrameLifecycle frame;
    private final HandPass hand;
    private final TerrainHooks terrain;
    private final SkyHorizon horizon = new SkyHorizon();

    public UmbraRenderingPipeline(ShaderPack pack) {
        this.state = new PipelineState(this, pack);
        this.view = new PipelineView(this.state);
        this.phases = new PhaseController(this.state);
        this.frame = new FrameLifecycle(this.state, new PassRunner(this.state), this.phases);
        this.hand = new HandPass(this.state, this.phases);
        this.terrain = new TerrainHooks(this.state, this.phases);
    }

    // renderWorld HEAD: the frame goes into the gbuffer
    public void beginWorldRendering(float partialTicks) {
        this.frame.begin(partialTicks);
    }

    // True when the pack supplies a program for this phase; callers suppressing vanilla GL state use it to leave the fixed-function path alone
    public boolean hasGbufferProgram(ProgramId phase) {
        return this.phases.has(phase);
    }

    // Whether the pack ships this phase's program itself rather than through the fallback chain
    public boolean hasDirectGbufferProgram(ProgramId phase) {
        return this.phases.hasDirect(phase);
    }

    // Whether the frame is between the deferred and composite stages
    public boolean isRenderingPostDeferredTranslucents() {
        return this.view.isPostDeferred();
    }

    // gbuffers_entities_translucent, else gbuffers_entities
    public ProgramId getTranslucentEntityPhase() {
        return this.phases.translucentEntityPhase();
    }

    // The gbuffer phase currently bound, or null when none is
    public ProgramId getCurrentPhase() {
        return this.phases.current();
    }

    // Whether the camera pass is drawing right now; narrower than isWorldRenderingActive(), since the shadow pass renders entities through the same vanilla renderers and runs first
    public boolean isCameraPassActive() {
        return this.view.isCameraPassActive();
    }

    // Switches the gbuffer program for a fixed-function phase with its default render stage
    public void setPhase(ProgramId phase) {
        setPhase(phase, PhaseController.defaultRenderStage(phase));
    }

    // Mirrors OptiFine's enableLightmap()/disableLightmap() swap of gbuffers_textured and gbuffers_textured_lit
    public void setLightmapEnabled(boolean enabled) {
        this.phases.lightmapToggled(enabled);
    }

    // For callers that know a finer phase than ProgramId can express (sky basic covers sky/stars/void)
    public void setPhase(ProgramId phase, int renderStage) {
        this.phases.select(phase, renderStage);
    }

    // Re-uploads the bound phase program's per-object uniforms between draws of a batch
    public void refreshDynamicUniforms() {
        this.phases.refreshDynamicUniforms();
    }

    // The glowing-eyes layers through gbuffers_spidereyes, selected through setPhase(ProgramId) so its hooks see the switch
    public void beginEyes() {
        if (this.phases.enterEyes()) {
            setPhase(ProgramId.SpiderEyes);
        }
    }

    // Back to the phase bound before the eyes
    public void endEyes() {
        this.phases.exitEyes();
    }

    // The enchantment glint through gbuffers_armor_glint
    public void beginArmorGlint() {
        if (this.phases.enterGlint()) {
            setPhase(ProgramId.ArmorGlint);
        }
    }

    // Back to the phase bound before the glint
    public void endArmorGlint() {
        this.phases.exitGlint();
    }

    // OptiFine's horizon ring, right before the sky disc
    public void drawSkyHorizon() {
        if (this.view.isActive()) {
            this.horizon.draw();
        }
    }

    // The Impetus terrain override program just bound (UmbraTerrainShaderInterface.setupState)
    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState) {
        onTerrainDraw(drawBuffers, blendState, ProgramAlphaTest.empty(), false);
    }

    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState, ProgramAlphaTest alphaTest,
                              boolean translucentPass) {
        this.terrain.onTerrainDraw(drawBuffers, blendState, alphaTest, translucentPass);
    }

    // After a terrain pass
    public void afterTerrainDraw(int drawBufferSlots) {
        this.terrain.afterTerrainDraw(drawBufferSlots);
    }

    // The Distant Horizons compat for this pipeline
    public DhCompat getDhCompat() {
        return this.view.dhCompat();
    }

    // The shadow renderer, or null when the pack draws no shadow map
    public UmbraShadowRenderer getShadowRenderer() {
        return this.view.shadowRenderer();
    }

    // A framebuffer for a Distant Horizons LOD pass, owned by the caller
    public UmbraFramebuffer createDhFramebuffer(int[] drawBuffers, boolean translucent) {
        return this.terrain.createDhFramebuffer(drawBuffers, translucent);
    }

    // Right before DH draws a LOD pass
    public void onDhLodDraw(UmbraFramebuffer framebuffer, int[] drawBuffers, ProgramBlendState blendState,
                            boolean translucentPass) {
        this.terrain.onDhLodDraw(framebuffer, drawBuffers, blendState, translucentPass);
    }

    // After a LOD pass
    public void afterDhLodDraw(int drawBufferSlots) {
        this.terrain.afterDhLodDraw(drawBufferSlots);
    }

    // Right after setupCameraTransform: the camera matrices for the uniform providers
    public void captureRenderingState() {
        this.frame.captureRenderingState();
    }

    // The shadow map and the prepare family, before any gbuffer geometry
    public void renderShadowMap() {
        this.frame.renderShadowMap();
    }

    // The translucent anchor: depthtex1, the deferred chain, then the post-deferred gbuffer
    public void beginTranslucents() {
        this.frame.beginTranslucents();
    }

    // Right before the solid hand draws: depthtex2
    public void beginHand() {
        this.frame.beginHand();
    }

    // The solid first-person hand into the pre-deferred gbuffer; true means call endHandRendering()
    public boolean beginHandRendering() {
        return this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE);
    }

    // OptiFine's late hand pass, after translucent geometry and before the composite chain
    public boolean beginHandTranslucentRendering() {
        return this.hand.begin(ProgramId.HandWater, HandPass.TRANSLUCENT_STAGE);
    }

    // Restores state after the hand
    public void endHandRendering() {
        this.hand.end();
    }

    // renderWorld RETURN: the composite chain and final pass, then a clean GL state for vanilla
    public void finishWorldRendering() {
        this.frame.finish();
    }

    // Whether the pipeline is between beginWorldRendering and its end
    public boolean isWorldRenderingActive() {
        return this.view.isActive();
    }

    // rain.depth
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return this.view.switches().rainDepth;
    }

    // beacon.beam.depth
    public boolean shouldWriteBeaconBeamToDepthBuffer() {
        return this.view.switches().beaconBeamDepth;
    }

    // frustum.culling = false: the pack needs off-screen geometry drawn
    public boolean shouldDisableFrustumCulling() {
        return !this.view.switches().frustumCulling;
    }

    // occlusion.culling = false: the pack needs occluded geometry drawn
    public boolean shouldDisableOcclusionCulling() {
        return !this.view.switches().occlusionCulling;
    }

    // skipAllRendering: no world geometry; the composite chain still runs
    public boolean skipAllRendering() {
        return this.view.switches().skipAllRendering;
    }

    // separateEntityDraws: entities in their own pass after the deferred chain
    public boolean shouldSeparateEntityDraws() {
        return this.view.switches().separateEntityDraws;
    }

    // particles.ordering: mixed, after or before the deferred chain
    public String getParticleOrdering() {
        return this.view.switches().particleOrdering;
    }

    // backFace.<layer>: false when the pack wants that layer's back faces drawn; layerOrdinal is BlockRenderLayer#ordinal()
    public boolean shouldCullBackFaces(int layerOrdinal) {
        return this.view.switches().cullsBackFaces(layerOrdinal);
    }

    // Packs with a shadow pass replace vanilla's blob shadows
    public boolean shouldDisableVanillaEntityShadows() {
        return this.view.shadowRenderer() != null;
    }

    // Puts the raw-depth or comparison sampler on the shadow units to match how the program about to draw declared them
    public void applyShadowSamplerKinds(ShadowSamplerKinds kinds, boolean gbufferStage) {
        this.view.applyShadowSamplerKinds(kinds, gbufferStage);
    }

    // The pack's custom textures and images for the current program
    public void bindCustomImages() {
        this.view.bindPackResources();
    }

    // Frees all GL resources; render thread only, safe more than once
    public void destroy() {
        this.state.destroy();
    }
}
