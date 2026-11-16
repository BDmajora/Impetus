package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.OpenGlHelper;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Tracker of the program phase that draws are routed to.
public final class PhaseController {
    // Lightmap coordinate of full brightness.
    private static final float FULL_BRIGHT_LIGHTMAP_COORD = 240.0f;

    // Pipeline state shared by the passes.
    private final PipelineState state;
    // Phase in use or null.
    ProgramId current;
    // Alpha test in use.
    private ProgramAlphaTest activeAlphaTest;
    // Flag set when the sky draws at the far plane.
    private boolean skyAtFarPlane;
    // Phase before the eyes phase.
    private ProgramId beforeEyes;
    // Render stage before the eyes phase.
    private int beforeEyesStage;
    // Flag set during the eyes phase.
    private boolean eyesActive;
    // Phase before the glint phase.
    private ProgramId beforeGlint;
    // Render stage before the glint phase.
    private int beforeGlintStage;
    // Flag set during the glint phase.
    private boolean glintActive;

    // Creates a phase controller from its state.
    public PhaseController(PipelineState state) {
        this.state = state;
    }

    // Returns the default render stage of a phase.
    public static int defaultRenderStage(ProgramId phase) {
        if (phase == null) {
            return 0;
        }
        return switch (phase) {
            case SkyBasic -> 1;
            case SkyTextured -> 4;
            case Entities, SpiderEyes -> 11;
            case DamagedBlock -> 13;
            case Line -> 14;
            case Particles -> 19;
            case Clouds -> 20;
            case Weather -> 21;
            default -> 0;
        };
    }

    // Returns the current phase.
    public ProgramId current() {
        return this.current;
    }

    // Returns the has.
    public boolean has(ProgramId phase) {
        return this.state.active && this.state.gbufferPrograms.get(phase) != null;
    }

    // Returns whether a phase has its own program.
    public boolean hasDirect(ProgramId phase) {
        return this.state.active && this.state.gbufferPrograms.hasDirect(phase);
    }

    // Returns the phase of translucent entities.
    public ProgramId translucentEntityPhase() {
        return has(ProgramId.EntitiesTrans) ? ProgramId.EntitiesTrans : ProgramId.Entities;
    }

    // Switches to a phase and render stage.
    public void select(ProgramId phase, int renderStage) {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.current = phase;
        CapturedRenderingState.INSTANCE.setRenderStage(renderStage);
        GlState.resetVanillaVertexArrayState();
        setSkyAtFarPlane(phase == ProgramId.SkyBasic || phase == ProgramId.SkyTextured);
        GbufferPrograms.Entry entry = phase == null ? null : this.state.gbufferPrograms.get(phase);
        restoreAlphaTest();
        if (entry == null) {
            LWJGL.glUseProgram(0);
            this.state.gbuffers.drawBuffers(this.state.gbuffers.current, GbufferTargets.FIXED_FUNCTION_MASK);
            return;
        }
        entry.getProgram().bind();
        this.state.binder.bindPackResources();
        int[] drawBuffers = entry.drawBuffersReadOnly();
        this.state.feedback.bindColorSamplers(this.state.feedback.prepare(drawBuffers, this.state.activeGbufferFlips));
        entry.getUniforms().update();
        this.state.shadowSamplers.apply(entry.getShadowSamplerKinds(), true);
        this.state.gbuffers.drawBuffers(this.state.gbuffers.current, drawBuffers);
        entry.getBlendState().apply(drawBuffers);
        if (entry.getAlphaTest().hasDirectives()) {
            entry.getAlphaTest().apply();
            this.activeAlphaTest = entry.getAlphaTest();
        }
    }

    // Stores the sky at far plane.
    void setSkyAtFarPlane(boolean sky) {
        if (sky != this.skyAtFarPlane) {
            LWJGL.glDepthRange(sky ? 1.0 : 0.0, 1.0);
            this.skyAtFarPlane = sky;
        }
    }

    // Restores the full depth range.
    void resetDepthRange() {
        LWJGL.glDepthRange(0.0, 1.0);
        this.skyAtFarPlane = false;
    }

    // Applies an alpha test.
    void forceAlphaTest(ProgramAlphaTest alphaTest) {
        alphaTest.apply();
        this.activeAlphaTest = alphaTest;
    }

    // Restores the alpha test of the phase.
    void restoreAlphaTest() {
        if (this.activeAlphaTest != null) {
            this.activeAlphaTest.restore();
            this.activeAlphaTest = null;
        }
    }

    // Updates the lightmap state of the phase.
    public void lightmapToggled(boolean enabled) {
        if (!this.state.active) {
            return;
        }
        ProgramId from = enabled ? ProgramId.Textured : ProgramId.TexturedLit;
        if (this.current == from) {
            select(enabled ? ProgramId.TexturedLit : ProgramId.Textured, CapturedRenderingState.INSTANCE.getRenderStage());
        }
    }

    // Recomputes the uniforms that change each frame.
    public void refreshDynamicUniforms() {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass() || this.current == null) {
            return;
        }
        GbufferPrograms.Entry entry = this.state.gbufferPrograms.get(this.current);
        if (entry != null && LWJGL.glGetInteger(GbufferFeedback.GL_CURRENT_PROGRAM) == entry.getProgram().getProgram().getGlId()) {
            entry.getUniforms().updatePerObject();
        }
    }

    // Switches to the eyes phase and returns whether it did.
    public boolean enterEyes() {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return false;
        }
        if (!this.eyesActive) {
            this.beforeEyes = this.current;
            this.beforeEyesStage = CapturedRenderingState.INSTANCE.getRenderStage();
            this.eyesActive = true;
        }
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT_LIGHTMAP_COORD, FULL_BRIGHT_LIGHTMAP_COORD);
        return true;
    }

    // Returns from the eyes phase.
    public void exitEyes() {
        if (!this.eyesActive || !this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.eyesActive = false;
        select(this.beforeEyes, this.beforeEyesStage);
    }

    // Switches to the glint phase and returns whether it did.
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

    // Returns from the glint phase.
    public void exitGlint() {
        if (!this.glintActive) {
            return;
        }
        this.glintActive = false;
        select(this.beforeGlint, this.beforeGlintStage);
    }

    // Clears the phase at the end of a frame.
    void endFrame() {
        restoreAlphaTest();
        this.eyesActive = false;
        this.glintActive = false;
    }
}
