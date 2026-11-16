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

// Steps of a pack frame from its begin passes to its composite passes.
public final class FrameLifecycle {
    // Resources of the pack.
    private final PipelineState state;
    // Runner of fullscreen passes.
    private final PassRunner runner;
    // Program phase selection.
    private final PhaseController phases;
    // Scratch of a captured matrix.
    private final Matrix4f scratch = new Matrix4f();

    // Creates the lifecycle of a pipeline.
    public FrameLifecycle(PipelineState state, PassRunner runner, PhaseController phases) {
        this.state = state;
        this.runner = runner;
        this.phases = phases;
    }

    // Resizes on a new screen size and updates the uniforms and runs the setup and begin passes and binds the gbuffer.
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
        s.customImages.clearAll();
        s.binder.bindPackResources();
        s.activeGbufferFlips = s.schedule.preTranslucentFlips;
        s.binder.bindPbr();
        s.feedback.bindColorSamplers(s.activeGbufferFlips);
        UmbraFramebuffer gbuffer = s.schedule.gbuffer;
        s.gbuffers.current = gbuffer;
        s.copies.clear(s.fullClearRequired, s.targets.getWidth(), s.targets.getHeight());
        s.fullClearRequired = false;
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

    // Resizes the targets and buffers and images and requests a full clear.
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

    // Records the camera model view and projection matrices.
    public void captureRenderingState() {
        if (!this.state.active) {
            return;
        }
        CapturedRenderingState.INSTANCE.setGbufferModelView(this.scratch.set(ActiveRenderInfoAccessor.getModelViewMatrix()));
        CapturedRenderingState.INSTANCE.setGbufferProjection(this.scratch.set(ActiveRenderInfoAccessor.getProjectionMatrix()));
    }

    // Runs the prepare passes and the shadow pass in the order the pack asks for.
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
            s.binder.bindShadow();
            this.runner.runShadowComposites(mc);
            s.gbuffers.current.bind();
            LWJGL.glViewport(0, 0, s.targets.getWidth(), s.targets.getHeight());
            s.binder.bindShadow();
        }
        if (!s.switches.prepareBeforeShadow) {
            this.runner.runFamily(s.schedule.prepare, mc);
        }
    }

    // Copies the opaque depth and runs the deferred passes and switches to the translucent gbuffer.
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

    // Copies the depth before the hand draws.
    public void beginHand() {
        if (this.state.active) {
            this.runner.copyDepth(this.state.targets.getDepthTextureNoHand());
        }
    }

    // Runs the composite passes or the fallback blit and the color conversion and restores the vanilla state.
    public void finish() {
        PipelineState s = this.state;
        if (!s.active) {
            return;
        }
        s.active = false;
        this.phases.endFrame();
        Minecraft mc = Minecraft.getMinecraft();
        PassRunner.enterFullscreenState();
        s.binder.bindDepth();
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
        if (s.colorSpace.isActive()) {
            GlState.bindMainRenderTarget(mc);
            GlState.restoreMainDrawReadBuffers();
            s.colorSpace.run(mc.displayWidth, mc.displayHeight, s.quad);
        }
        this.runner.resetMipmaps();
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
