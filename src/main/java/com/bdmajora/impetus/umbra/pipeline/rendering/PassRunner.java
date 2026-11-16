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

// Runner of one fullscreen pass.
public final class PassRunner {
    // Pipeline state shared by the passes.
    private final PipelineState state;

    // Creates a pass runner from its state.
    public PassRunner(PipelineState state) {
        this.state = state;
    }

    // Sets the GL state of a fullscreen pass.
    static void enterFullscreenState() {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
    }

    // Runs a fullscreen pass.
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
        this.state.shadowSamplers.apply(pass.shadowSamplerKinds, false);
        if (pass.modern) {
            GlState.pushFullscreenMatrices();
            this.state.quad.draw();
            GlState.popFixedFunctionMatrices();
        } else {
            this.state.quad.draw();
        }
    }

    // Binds the images for a flip state.
    void bindImages(BitSet flips) {
        this.state.images.bind(flips, this.state.targets, this.state.shadowRenderer);
    }

    // Runs a list of fullscreen passes.
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

    // Restores the gbuffer bindings.
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

    // Runs a list of compute passes.
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

    // Runs the shadow composite passes.
    void runShadowComposites(Minecraft mc) {
        List<FullscreenPass> passes = this.state.schedule.shadowComposite;
        if (passes.isEmpty()) {
            return;
        }
        this.state.binder.bindPackResources();
        LWJGL.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        boolean drewRaster = false;
        for (FullscreenPass pass : passes) {
            if (pass.program == null) {
                bindImages(pass.flipsBefore);
                dispatch(pass.computes);
            } else {
                run(pass, mc);
                drewRaster = true;
            }
        }
        LWJGL.glUseProgram(0);
        if (drewRaster) {
            restoreGbufferBindings(false);
        }
    }

    // Generates the mipmaps that a pass asks for.
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

    // Clears the mipmap flags.
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

    // Copies the depth.
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
