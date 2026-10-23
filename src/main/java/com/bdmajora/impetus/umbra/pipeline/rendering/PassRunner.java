package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;

import java.util.BitSet;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Executes the baked schedule: fullscreen passes, their computes, the mid-world families and the shadowcomp chain
public final class PassRunner {
    private final PipelineState state;

    public PassRunner(PipelineState state) {
        this.state = state;
    }

    // Full-screen passes draw with depth, blend and alpha test off; through GlStateManager so vanilla's later toggles are not skipped
    static void enterFullscreenState() {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
    }

    // One pass into its framebuffer, or Minecraft's for the final pass; Umbra's CompositeRenderer.renderAll order: the pass's computes dispatch first under its flip state, samplers and images in place, then the draw (deferred4 texelFetches what deferred4_a imageStored)
    void run(FullscreenPass pass, Minecraft mc) {
        if (!pass.computes.isEmpty()) {
            TextureBinder.bindColorSamplers(pass.colorSamplers);
            bindImages(pass.flipsBefore);
            dispatch(pass.computes);
            LWJGL.glUseProgram(0);
        }
        if (pass.program == null) {
            return;
        }
        if (pass.framebuffer != null) {
            pass.framebuffer.bind();
            pass.applyViewport(this.state.targets.getWidth(), this.state.targets.getHeight());
        } else {
            GlState.bindMainRenderTarget(mc);
            GlState.restoreMainDrawReadBuffers();
        }
        GlStateManager.disableBlend();
        GlState.disableIndexedBlend(pass.drawBuffers.length, this.state.maxDrawBuffers);
        pass.blendState.apply(pass.drawBuffers);
        generateMipmaps(pass);
        TextureBinder.bindColorSamplers(pass.colorSamplers);
        pass.program.bind();
        this.state.binder.bindPackResources();
        pass.uniforms.update();
        // Last, since nothing above touches the shadow units but the previous pass may have left the other flavour there
        this.state.shadowSamplers.apply(pass.shadowSamplerKinds, false);
        if (pass.modern) {
            // ftransform() = ortho * [0,1] = NDC and gl_TextureMatrix[0] passes the texcoord through; saved and restored so the hand and GUI after the chain are unaffected
            GlState.pushFullscreenMatrices();
            this.state.quad.draw();
            GlState.popFixedFunctionMatrices();
        } else {
            this.state.quad.draw();
        }
    }

    // colorimgN and shadowcolorimgN under a pass's flip state
    void bindImages(BitSet flips) {
        this.state.images.bind(flips, this.state.targets, this.state.shadowRenderer);
    }

    // A family that runs mid world rendering (begin, prepare), then the gbuffer state put back
    void runFamily(List<FullscreenPass> family, Minecraft mc) {
        if (family.isEmpty()) {
            return;
        }
        enterFullscreenState();
        this.state.binder.bindDepth();
        this.state.binder.bindPackResources();
        for (FullscreenPass pass : family) {
            run(pass, mc);
        }
        LWJGL.glUseProgram(0);
        restoreGbufferBindings(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        if (this.state.gbuffers.current != null) {
            this.state.gbuffers.current.bind();
            LWJGL.glViewport(0, 0, this.state.targets.getWidth(), this.state.targets.getHeight());
        }
    }

    // After fullscreen work mid-frame: units cleaned, then every gbuffer-stage sampler bound again for the geometry that follows
    void restoreGbufferBindings(boolean shadow) {
        TextureBinder binder = this.state.binder;
        binder.restoreUnits();
        binder.bindDepth();
        if (shadow) {
            binder.bindShadow();
        }
        binder.bindNoise();
        binder.bindPackResources();
        binder.bindPbr();
        this.state.feedback.bindColorSamplers(this.state.activeGbufferFlips);
    }

    // Dispatches in order with a full barrier after each, since Complementary's floodfill iterations read the previous output; allowConcurrentCompute is the pack asserting independence, and then one barrier publishes the whole group
    void dispatch(List<ComputePass> computes) {
        boolean concurrent = this.state.switches.allowConcurrentCompute;
        for (ComputePass compute : computes) {
            compute.program.bind();
            this.state.binder.bindPackResources();
            compute.uniforms.update();
            this.state.shadowSamplers.apply(compute.shadowSamplerKinds, false);
            compute.dispatch(this.state.targets.getWidth(), this.state.targets.getHeight());
            if (!concurrent) {
                LWJGL.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
            }
        }
        if (concurrent && !computes.isEmpty()) {
            LWJGL.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        }
    }

    // The shadowcomp chain: a full barrier makes the shadow pass's voxelization visible, then each pass; compute-only entries bind only images, since colortex0..7 would land on the atlas and lightmap units the world is about to use
    void runShadowComposites(Minecraft mc) {
        List<FullscreenPass> passes = this.state.schedule.shadowComposite;
        if (passes.isEmpty()) {
            return;
        }
        // The shadow-terrain draw that just voxelized can reset units, so the voxel/floodfill bindings are re-established rather than trusted
        this.state.binder.bindPackResources();
        LWJGL.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        boolean drewRaster = false;
        for (FullscreenPass pass : passes) {
            if (pass.program == null) {
                bindImages(pass.flipsBefore);
                dispatch(pass.computes);
            } else {
                // run() dispatches the raster pass's own computes first, so they must not run again here
                run(pass, mc);
                drewRaster = true;
            }
        }
        LWJGL.glUseProgram(0);
        if (drewRaster) {
            // The caller binds the finished shadow maps right after, so only the rest is restored here
            restoreGbufferBindings(false);
        }
    }

    // Generates mips on the targets the pass declared via mipmapEnabled, on a scratch unit above every sampler allocation
    private void generateMipmaps(FullscreenPass pass) {
        BitSet mipmapped = pass.mipmappedBuffers;
        if (mipmapped.isEmpty()) {
            return;
        }
        GlTextureUnits.selectScratch(SamplerLayout.MIPMAP_SCRATCH_UNIT);
        for (int index = mipmapped.nextSetBit(0); index >= 0; index = mipmapped.nextSetBit(index + 1)) {
            UmbraRenderTarget target = this.state.targets.get(index);
            if (target != null) {
                target.generateMipmaps(pass.flipsBefore.get(index));
            }
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Returns the targets passes mipmapped to their plain filter at frame end; nothing else mipmaps a colour target, so the others are skipped
    void resetMipmaps() {
        BitSet mipmapped = this.state.schedule.mipmapped;
        if (mipmapped.isEmpty()) {
            return;
        }
        GlTextureUnits.selectScratch(SamplerLayout.MIPMAP_SCRATCH_UNIT);
        for (int index = mipmapped.nextSetBit(0); index >= 0; index = mipmapped.nextSetBit(index + 1)) {
            UmbraRenderTarget target = this.state.targets.get(index);
            if (target != null) {
                target.resetMipmaps();
            }
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Snapshots the active gbuffer's depth (how OptiFine makes depthtex1/2), from the shader framebuffer's own depth rather than whatever a previous hook left bound
    void copyDepth(DepthTexture destination) {
        if (this.state.gbuffers.current != null) {
            this.state.gbuffers.current.bind();
        }
        int previous = GlState.bindScratchTexture2D(destination.getTextureId());
        try {
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    this.state.targets.getWidth(), this.state.targets.getHeight());
        } finally {
            GlState.restoreScratchTexture2D(previous);
        }
        this.state.binder.bindPackResources();
    }
}
