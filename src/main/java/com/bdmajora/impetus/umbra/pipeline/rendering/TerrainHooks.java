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

// The chunk renderer's and Distant Horizons' draw hooks: the pack samplers are Umbra-owned dynamic bindings re-asserted at program use, so gbuffers_water and the LOD programs see the current depthtex1, shadow, noise and a feedback-safe gaux snapshot
public final class TerrainHooks {
    private final PipelineState state;
    private final PhaseController phases;
    // The alpha-test state a LOD pass found, recorded once per pass since DH's generic objects re-enter mid-pass and must not overwrite it
    private boolean dhAlphaTestRecorded;
    private boolean dhAlphaTestWasEnabled;

    public TerrainHooks(PipelineState state, PhaseController phases) {
        this.state = state;
        this.phases = phases;
    }

    // The terrain or water override program just bound: its DRAWBUFFERS on the gbuffer, the pass's blend, and the pack's alpha-test override recorded so the next phase restores vanilla's; a null state counts as no directives
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

    private void bindDrawSamplers(int[] buffers) {
        TextureBinder binder = this.state.binder;
        binder.bindDepth();
        binder.bindShadow();
        binder.bindNoise();
        binder.bindPbr();
        this.state.feedback.bindColorSamplers(this.state.feedback.prepare(buffers, this.state.activeGbufferFlips));
    }

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

    // After a terrain pass: vanilla's framebuffer and buffers back, indexed blend off
    public void afterTerrainDraw(int drawBufferSlots) {
        if (!this.state.active || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
        GlState.restoreMainDrawReadBuffers();
        GlState.disableIndexedBlend(drawBufferSlots, this.state.maxDrawBuffers);
        GlTextureUnits.resetToUnit0();
    }

    // A LOD framebuffer over the gbuffer targets at the front side of the phase it runs in (before or after the deferred chain); DH's own depth is attached by the compat, so LOD depth lands in dhDepthTex0. Owned by the caller
    public UmbraFramebuffer createDhFramebuffer(int[] drawBuffers, boolean translucent) {
        return this.state.gbuffers.createDh(drawBuffers,
                translucent ? this.state.schedule.translucentFlips : this.state.schedule.preTranslucentFlips);
    }

    // Right before DH draws a LOD pass (Iris's LodRendererEvents binds its framebuffer here): a terrain draw's setup on the LOD framebuffer, whose draw buffers were fixed at creation; Iris runs DH programs with no fixed-function alpha test (core profile), so it goes off or a pack's low-alpha dithered fade is culled
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

    // After a LOD pass: indexed blend off and the alpha test as it was; the gbuffer is rebound by whatever draws next
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
