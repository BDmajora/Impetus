package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.mixin.core.terrain.ActiveRenderInfoAccessor;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The frame from renderWorld HEAD to RETURN: the gbuffer takes the world, the shadow map and prepare run after the camera capture, the deferred chain at the translucent anchor, composite and final at the end, then a clean GL state for vanilla
public final class FrameLifecycle {
    private final PipelineState state;
    private final PassRunner runner;
    private final PhaseController phases;
    // Reused for the per-frame matrix capture; the captured state copies out of it
    private final Matrix4f scratch = new Matrix4f();

    public FrameLifecycle(PipelineState state, PassRunner runner, PhaseController phases) {
        this.state = state;
        this.runner = runner;
        this.phases = phases;
    }

    // renderWorld HEAD: redirects the frame into the gbuffer, so vanilla's fog-coloured clear clears our attachments and every world draw lands in the render targets
    public void begin(float partialTicks) {
        PipelineState s = this.state;
        if (s.destroyed) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth != s.targets.getWidth() || mc.displayHeight != s.targets.getHeight()) {
            resize(mc.displayWidth, mc.displayHeight);
        }
        if (!s.storageBuffers.isEmpty()) {
            s.storageBuffers.bindAll();
        }
        SystemTimeUniforms.COUNTER.beginFrame(System.nanoTime());
        EyeBrightnessTracker.update();
        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        GlState.captureAtlasSize(mc);
        Entity camera = mc.getRenderViewEntity();
        if (camera != null) {
            CapturedRenderingState.INSTANCE.setCameraPosition(
                    camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks,
                    camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks,
                    camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks);
        }
        s.frameUpdateNotifier.onNewFrame();
        CommonUniforms.beginFrame();
        ActiveCustomUniforms.update();
        s.binder.bindFrameTextures();
        // Custom images: the pack-declared clears, then image units and paired samplers
        s.customImages.clearAll();
        s.binder.bindPackResources();
        s.activeGbufferFlips = s.schedule.preTranslucentFlips;
        s.binder.bindPbr();
        // colortex4..7 must be readable by gbuffer programs before any geometry (MakeUp's terrain fog samples gaux4), or distant terrain fogs toward garbage
        s.feedback.bindColorSamplers(s.activeGbufferFlips);
        UmbraFramebuffer gbuffer = s.schedule.gbuffer;
        s.gbuffers.current = gbuffer;
        s.copies.clear(s.fullClearRequired, s.targets.getWidth(), s.targets.getHeight());
        s.fullClearRequired = false;
        // Umbra beginLevelRendering: the setup computes run once on the first frame, then begin runs every frame on the freshly cleared targets
        if (!s.setupDispatched) {
            s.setupDispatched = true;
            if (!s.schedule.setup.isEmpty()) {
                s.binder.bindPackResources();
                for (FullscreenPass pass : s.schedule.setup) {
                    this.runner.bindImages(pass.flipsBefore);
                    this.runner.dispatch(pass.computes);
                }
                LWJGL.glUseProgram(0);
            }
        }
        this.runner.runFamily(s.schedule.begin, mc);
        gbuffer.bind();
        s.gbuffers.drawBuffers(gbuffer, GbufferTargets.FIXED_FUNCTION_MASK);
        GlStateManager.disableBlend();
        GlState.disableIndexedBlend(SamplerLayout.GBUFFER_ATTACHMENT_LIMIT, s.maxDrawBuffers);
        this.phases.setSkyAtFarPlane(false);
        s.active = true;
    }

    // Reallocates the screen-relative storage on the mipmap scratch unit, since the targets bind raw while reallocating and on a cached unit that desynced GlStateManager until the frame's end
    private void resize(int width, int height) {
        GlTextureUnits.selectScratch(SamplerLayout.MIPMAP_SCRATCH_UNIT);
        try {
            this.state.targets.resize(width, height);
            this.state.storageBuffers.onResize(width, height);
            this.state.customImages.onResize(width, height);
        } finally {
            GlTextureUnits.releaseScratch();
        }
        this.state.fullClearRequired = true;
    }

    // Right after setupCameraTransform, when vanilla has just read the camera matrices into ActiveRenderInfo's buffers
    public void captureRenderingState() {
        if (!this.state.active) {
            return;
        }
        CapturedRenderingState.INSTANCE.setGbufferModelView(this.scratch.set(ActiveRenderInfoAccessor.getModelViewMatrix()));
        CapturedRenderingState.INSTANCE.setGbufferProjection(this.scratch.set(ActiveRenderInfoAccessor.getProjectionMatrix()));
    }

    // The shadow map before anything draws into the gbuffer, then the gbuffer re-pointed and the real maps on shadowtex0/1; prepare runs here too like Umbra's renderShadows, so Photon's cloud shadow lands before any geometry
    public void renderShadowMap() {
        PipelineState s = this.state;
        if (!s.active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (s.switches.prepareBeforeShadow) {
            this.runner.runFamily(s.schedule.prepare, mc);
        }
        if (s.shadowRenderer != null) {
            s.shadowRenderer.render();
            // Iris binds the finished map to every shadowcomp program at use; the units still held the frame-start stubs
            s.binder.bindShadow();
            this.runner.runShadowComposites(mc);
            s.gbuffers.current.bind();
            LWJGL.glViewport(0, 0, s.targets.getWidth(), s.targets.getHeight());
            // Again, since the shadowcomp programs leave their own sampler flavours on the shadow units
            s.binder.bindShadow();
        }
        if (!s.switches.prepareBeforeShadow) {
            this.runner.runFamily(s.schedule.prepare, mc);
        }
    }

    // The translucent anchor after all opaque content: pre-translucent depth (depthtex1), the deferred chain, then the post-deferred gbuffer; ends in OptiFine's beginWater() state, blending AND depth writes on, re-enabled since the deferred chain runs blend-off
    public void beginTranslucents() {
        PipelineState s = this.state;
        if (!s.active) {
            return;
        }
        this.runner.copyDepth(s.targets.getDepthTextureNoTranslucents());
        if (!s.schedule.deferred.isEmpty()) {
            Minecraft mc = Minecraft.getMinecraft();
            PassRunner.enterFullscreenState();
            s.binder.bindDepth();
            // Umbra binds images at every program use; the shadow pass's out-of-band rendering may have disturbed the high units
            s.binder.bindPackResources();
            for (FullscreenPass pass : s.schedule.deferred) {
                this.runner.run(pass, mc);
            }
            LWJGL.glUseProgram(0);
            s.activeGbufferFlips = s.schedule.translucentFlips;
            this.runner.restoreGbufferBindings(true);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            s.gbuffers.current = s.schedule.translucentGbuffer;
            s.gbuffers.current.bind();
            LWJGL.glViewport(0, 0, s.targets.getWidth(), s.targets.getHeight());
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);
    }

    // Right before the solid hand draws: the pre-hand depth (depthtex2)
    public void beginHand() {
        if (this.state.active) {
            this.runner.copyDepth(this.state.targets.getDepthTextureNoHand());
        }
    }

    // renderWorld RETURN or the hand anchor, whichever comes first: composite and final, the fallback blit, the copy-back, then a clean GL state
    public void finish() {
        PipelineState s = this.state;
        if (!s.active) {
            return;
        }
        s.active = false;
        // An alphaTest.<program> override must not survive into the composite chain or the GUI
        this.phases.endFrame();
        Minecraft mc = Minecraft.getMinecraft();
        PassRunner.enterFullscreenState();
        s.binder.bindDepth();
        // centerDepthSmooth from depthtex0 at the screen centre now that all geometry is in it, before any composite reads it (OptiFine's readCenterDepth)
        s.centerDepth.sample(s.targets.getDepthTexture().getTextureId(), s.targets.getWidth(), s.targets.getHeight(),
                SystemTimeUniforms.COUNTER.getLastFrameTime(), s.scalars.centerDepthHalfLife());
        for (FullscreenPass pass : s.schedule.composite) {
            this.runner.run(pass, mc);
        }
        if (s.schedule.blitSource != null) {
            s.schedule.blitSource.bindAsReadBuffer();
            LWJGL.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    OpenGlHelper.isFramebufferEnabled() ? mc.getFramebuffer().framebufferObject : 0);
            LWJGL.glBlitFramebuffer(0, 0, s.targets.getWidth(), s.targets.getHeight(), 0, 0, mc.displayWidth,
                    mc.displayHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }
        // Wide-gamut conversion in place on the presentation target as the last colour op; free when the colorspace is sRGB
        if (s.colorSpace.isActive()) {
            GlState.bindMainRenderTarget(mc);
            GlState.restoreMainDrawReadBuffers();
            s.colorSpace.run(mc.displayWidth, mc.displayHeight, s.quad);
        }
        this.runner.resetMipmaps();
        // Odd-flipped buffers carry this frame's data on ALT; back to MAIN so next frame's baked FBOs read fresh data
        s.copies.swap(s.targets);
        LWJGL.glUseProgram(0);
        GlState.bindMainRenderTarget(mc);
        GlState.restoreMainDrawReadBuffers();
        s.binder.restoreUnits();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
    }
}
