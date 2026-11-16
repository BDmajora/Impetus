package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Hooks that route terrain draws through the pack programs.
public final class TerrainHooks {
    // Pipeline state shared by the passes.
    private final PipelineState state;
    // Controller of the program phase.
    private final PhaseController phases;
    // Flag set once the Distant Horizons alpha test is recorded.
    private boolean dhAlphaTestRecorded;
    // Alpha test state before the LOD draw.
    private boolean dhAlphaTestWasEnabled;

    // Creates a terrain hooks from its state and phases.
    public TerrainHooks(PipelineState state, PhaseController phases) {
        this.state = state;
        this.phases = phases;
    }

    // Handles the terrain draw.
    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState, ProgramAlphaTest alphaTest,
                              boolean translucentPass) {
        if (!this.state.active) {
            return;
        }
        int[] buffers = DrawBuffers.sanitize(drawBuffers, SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
        bindDrawSamplers(buffers);
        this.state.gbuffers.drawBuffers(this.state.gbuffers.current, buffers);
        applyBlend(buffers, blendState, translucentPass);
        if (alphaTest != null && alphaTest.hasDirectives()) {
            this.phases.forceAlphaTest(alphaTest);
        }
    }

    // Binds the samplers of the draw buffers.
    private void bindDrawSamplers(int[] buffers) {
        TextureBinder binder = this.state.binder;
        binder.bindDepth();
        binder.bindShadow();
        binder.bindNoise();
        binder.bindPbr();
        this.state.feedback.bindColorSamplers(this.state.feedback.prepare(buffers, this.state.activeGbufferFlips));
    }

    // Applies the blend.
    private void applyBlend(int[] buffers, ProgramBlendState blendState, boolean translucentPass) {
        if (translucentPass) {
            GlState.translucentGbufferBlend(buffers.length, this.state.maxDrawBuffers);
        } else {
            GlState.opaqueGbufferBlend(buffers.length, this.state.maxDrawBuffers);
        }
        if (blendState != null) {
            blendState.apply(buffers);
        }
    }

    // Restores the state after a terrain draw.
    public void afterTerrainDraw(int drawBufferSlots) {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
        GlState.restoreMainDrawReadBuffers();
        GlState.disableIndexedBlend(drawBufferSlots, this.state.maxDrawBuffers);
        GlTextureUnits.resetToUnit0();
    }

    // Creates a framebuffer for LOD draws.
    public UmbraFramebuffer createDhFramebuffer(int[] drawBuffers, boolean translucent) {
        return this.state.gbuffers.createDh(drawBuffers,
                translucent ? this.state.schedule.translucentFlips : this.state.schedule.preTranslucentFlips);
    }

    // Handles the dh lod draw.
    public void onDhLodDraw(UmbraFramebuffer framebuffer, int[] drawBuffers, ProgramBlendState blendState,
                            boolean translucentPass) {
        if (!this.state.active) {
            return;
        }
        int[] buffers = DrawBuffers.sanitize(drawBuffers, SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
        bindDrawSamplers(buffers);
        framebuffer.bind();
        applyBlend(buffers, blendState, translucentPass);
        this.state.binder.bindPackResources();
        if (!this.dhAlphaTestRecorded) {
            this.dhAlphaTestRecorded = true;
            this.dhAlphaTestWasEnabled = LWJGL.glGetBoolean(GL11.GL_ALPHA_TEST);
        }
        GlStateManager.disableAlpha();
        LWJGL.glDisable(GL11.GL_ALPHA_TEST);
    }

    // Restores the state after a LOD draw.
    public void afterDhLodDraw(int drawBufferSlots) {
        if (!this.state.active) {
            return;
        }
        GlState.disableIndexedBlend(drawBufferSlots, this.state.maxDrawBuffers);
        if (this.dhAlphaTestRecorded && this.dhAlphaTestWasEnabled) {
            GlStateManager.enableAlpha();
            LWJGL.glEnable(GL11.GL_ALPHA_TEST);
        }
        this.dhAlphaTestRecorded = false;
        GlTextureUnits.resetToUnit0();
    }
}
