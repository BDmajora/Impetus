package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.OpenGlHelper;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The fixed-function gbuffer phases (sky, entities, particles, weather, clouds, hand) anchored on vanilla's profiler sections: binds the phase's program and points the draw-buffer mask at its DRAWBUFFERS, colortex0 only with no program
public final class PhaseController {
    // 1.12 signals full-bright eyes with the raw 61680 sentinel, which is arithmetically 240.97 and overflows HDR targets into a halo, so vec4(240, 240, 0, 1) is substituted like Umbra's VanillaTransformer
    private static final float FULL_BRIGHT_LIGHTMAP_COORD = 240.0f;

    private final PipelineState state;
    // The phase last selected, null when none is
    ProgramId current;
    // The alphaTest.<program> override currently forced on the GL state, so it can be undone
    private ProgramAlphaTest activeAlphaTest;
    // Sky phases render at the far plane: LIGHT writes BLACK from gbuffers_skybasic and repaints the sky in composite, which only works if the vanilla dome never reaches depthtex0; glDepthRange(1,1) emulates OptiFine and survives vanilla's depthMask toggling
    private boolean skyAtFarPlane;
    private ProgramId beforeEyes;
    private int beforeEyesStage;
    private boolean eyesActive;
    private ProgramId beforeGlint;
    private int beforeGlintStage;
    private boolean glintActive;

    public PhaseController(PipelineState state) {
        this.state = state;
    }

    // Umbra's WorldRenderingPhase ordinals for identifiable phases, published as renderStage (Clarity's stars gate on MC_RENDER_STAGE_STARS); ambiguous phases report NONE
    public static int defaultRenderStage(ProgramId phase) {
        if (phase == null) {
            return 0;
        }
        return switch (phase) {
            case SkyBasic -> 1;
            // Vanilla draws sun then moon under one anchor, reported as SUN
            case SkyTextured -> 4;
            // The eyes overlay draws inside the entity pass, which Umbra gives no phase of its own
            case Entities, SpiderEyes -> 11;
            case DamagedBlock -> 13;
            case Line -> 14;
            case Particles -> 19;
            case Clouds -> 20;
            case Weather -> 21;
            default -> 0;
        };
    }

    // The phase last selected, null when none is
    public ProgramId current() {
        return this.current;
    }

    // Whether the pack supplies a program for this phase, so a draw lands in its DRAWBUFFERS
    public boolean has(ProgramId phase) {
        return this.state.active && this.state.gbufferPrograms.get(phase) != null;
    }

    // Whether the pack ships this phase's program itself, rather than through OptiFine's fallback chain onto a program never written for this geometry
    public boolean hasDirect(ProgramId phase) {
        return this.state.active && this.state.gbufferPrograms.hasDirect(phase);
    }

    // Umbra's chain gbuffers_entities_translucent -> gbuffers_entities -> gbuffers_textured_lit; skipping the middle link lost entityColor, normals and diffuse lighting
    public ProgramId translucentEntityPhase() {
        return has(ProgramId.EntitiesTrans) ? ProgramId.EntitiesTrans : ProgramId.Entities;
    }

    // Selects a phase; the shadow pass owns its GL state and renders entities through the same vanilla renderers, so a gbuffer phase there would re-point the draw buffers at the gbuffer and shred the shadow map
    public void select(ProgramId phase, int renderStage) {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.current = phase;
        CapturedRenderingState.INSTANCE.setRenderStage(renderStage);
        // Every phase here is fixed-function geometry through client arrays aliasing generic slots, and a leftover generic array flattens the attribute
        GlState.resetVanillaVertexArrayState();
        setSkyAtFarPlane(phase == ProgramId.SkyBasic || phase == ProgramId.SkyTextured);
        GbufferPrograms.Entry entry = phase == null ? null : this.state.gbufferPrograms.get(phase);
        // The previous phase may have overridden the alpha test; vanilla's goes back before this phase decides
        restoreAlphaTest();
        if (entry == null) {
            LWJGL.glUseProgram(0);
            this.state.gbuffers.drawBuffers(this.state.gbuffers.current, GbufferTargets.FIXED_FUNCTION_MASK);
            return;
        }
        entry.getProgram().bind();
        this.state.binder.bindPackResources();
        int[] drawBuffers = entry.drawBuffersReadOnly();
        // Sky/entity/hand phases sample gaux buffers and the prior phase may have disturbed the units; a program writing a target it samples reads a copied side
        this.state.feedback.bindColorSamplers(this.state.feedback.prepare(drawBuffers, this.state.activeGbufferFlips));
        entry.getUniforms().update();
        this.state.shadowSamplers.apply(entry.getShadowSamplerKinds(), true);
        this.state.gbuffers.drawBuffers(this.state.gbuffers.current, drawBuffers);
        entry.getBlendState().apply(drawBuffers);
        // alphaTest.<program>: Photon turns the fixed-function test off, Complementary tightens it to GREATER 0.0001; held until the next phase
        if (entry.getAlphaTest().hasDirectives()) {
            entry.getAlphaTest().apply();
            this.activeAlphaTest = entry.getAlphaTest();
        }
    }

    // glDepthRange only on a change of sky/not-sky
    void setSkyAtFarPlane(boolean sky) {
        if (sky != this.skyAtFarPlane) {
            LWJGL.glDepthRange(sky ? 1.0 : 0.0, 1.0);
            this.skyAtFarPlane = sky;
        }
    }

    // The full depth range again, unconditionally, for a pass that must not inherit whatever another hook left
    void resetDepthRange() {
        LWJGL.glDepthRange(0.0, 1.0);
        this.skyAtFarPlane = false;
    }

    // Records an alpha-test override a terrain draw forced, so the next phase restores vanilla's
    void forceAlphaTest(ProgramAlphaTest alphaTest) {
        alphaTest.apply();
        this.activeAlphaTest = alphaTest;
    }

    // Puts vanilla's alpha test back if an override is in force
    void restoreAlphaTest() {
        if (this.activeAlphaTest != null) {
            this.activeAlphaTest.restore();
            this.activeAlphaTest = null;
        }
    }

    // OptiFine's enableLightmap()/disableLightmap() swap of gbuffers_textured and gbuffers_textured_lit when vanilla toggles unit 1, or items (no lightmap element in ITEM format) render fullbright; the render stage carries over since only the call site knows it
    public void lightmapToggled(boolean enabled) {
        if (!this.state.active) {
            return;
        }
        ProgramId from = enabled ? ProgramId.Textured : ProgramId.TexturedLit;
        if (this.current == from) {
            select(enabled ? ProgramId.TexturedLit : ProgramId.Textured, CapturedRenderingState.INSTANCE.getRenderStage());
        }
    }

    // Re-uploads only the per-object uniforms (entityColor, entityId, blockEntityId, currentRenderedItemId) between draws of a batch, since 1.12's vertex formats cannot carry Umbra's per-draw ids; glUniform writes into whatever program is bound NOW, and these hooks fire from renderers unaware of the phase, so GL is asked rather than the bookkeeping
    public void refreshDynamicUniforms() {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass() || this.current == null) {
            return;
        }
        GbufferPrograms.Entry entry = this.state.gbufferPrograms.get(this.current);
        if (entry != null && LWJGL.glGetInteger(GbufferFeedback.GL_CURRENT_PROGRAM) == entry.getProgram().getProgram().getGlId()) {
            entry.getUniforms().updatePerObject();
        }
    }

    // The eyes bracket (OptiFine's beginSpiderEyes): true when the caller should now select gbuffers_spidereyes through the hooked entry point
    public boolean enterEyes() {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        // A nested begin keeps the outer phase, so the end lands back on the entity program rather than on the eyes
        if (!this.eyesActive) {
            this.beforeEyes = this.current;
            this.beforeEyesStage = CapturedRenderingState.INSTANCE.getRenderStage();
            this.eyesActive = true;
        }
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT_LIGHTMAP_COORD, FULL_BRIGHT_LIGHTMAP_COORD);
        return true;
    }

    // Back to what was bound before the eyes, since they also run in the translucent-entity batch; an end with no begin (one that bailed) used to restore a stale phase and unbind the entity program
    public void exitEyes() {
        if (!this.eyesActive || !this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.eyesActive = false;
        select(this.beforeEyes, this.beforeEyesStage);
    }

    // The glint bracket (OptiFine's renderEnchantedGlintBegin), gated on the world pass so GUI items skip it; true when the caller should select gbuffers_armor_glint
    public boolean enterGlint() {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        if (!this.glintActive) {
            this.beforeGlint = this.current;
            this.beforeGlintStage = CapturedRenderingState.INSTANCE.getRenderStage();
            this.glintActive = true;
        }
        return true;
    }

    // Restores whatever was bound before the glint, which runs in both the entity and the hand batch
    public void exitGlint() {
        if (!this.glintActive) {
            return;
        }
        this.glintActive = false;
        select(this.beforeGlint, this.beforeGlintStage);
    }

    // Forgets the brackets at frame end, so a bracket left open by a crashed renderer cannot restore into the next frame
    void endFrame() {
        restoreAlphaTest();
        this.eyesActive = false;
        this.glintActive = false;
    }
}
