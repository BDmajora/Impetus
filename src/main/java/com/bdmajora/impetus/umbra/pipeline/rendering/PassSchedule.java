package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSet;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

// Fullscreen and compute passes of a pack in draw order with the gbuffers between them.
public final class PassSchedule {
    // Passes that run once after a load.
    final List<FullscreenPass> setup = new ArrayList<>();
    // Passes that run at the start of each frame.
    final List<FullscreenPass> begin = new ArrayList<>();
    // Passes that run after the shadow pass.
    final List<FullscreenPass> shadowComposite = new ArrayList<>();
    // Passes that run before the gbuffers.
    final List<FullscreenPass> prepare = new ArrayList<>();
    // Passes that run between the opaque and translucent gbuffers.
    final List<FullscreenPass> deferred = new ArrayList<>();
    // Passes that run after the gbuffers with the final pass last.
    final List<FullscreenPass> composite = new ArrayList<>();
    // Targets that some pass mipmaps.
    final BitSet mipmapped = new BitSet();
    // Framebuffer of the opaque gbuffers.
    UmbraFramebuffer gbuffer;
    // Framebuffer of the translucent gbuffers.
    UmbraFramebuffer translucentGbuffer;
    // Framebuffer read by the fallback blit or null with a final pass.
    UmbraFramebuffer blitSource;
    // Flip state before the deferred passes.
    BitSet preTranslucentFlips = new BitSet();
    // Flip state before the translucent gbuffers.
    BitSet translucentFlips = new BitSet();

    // Creates every pass in draw order and the gbuffers and the fallback blit.
    public void build(ProgramSet programs, ShaderProperties properties, PassFactory factory, ComputeBuilder computes,
                      GbufferTargets gbuffers, BufferFlipper flipper) {
        family(programs, properties, ProgramArrayId.Setup, TextureStage.SETUP, false, factory, computes, flipper, this.setup);
        family(programs, properties, ProgramArrayId.Begin, TextureStage.BEGIN, true, factory, computes, flipper, this.begin);
        shadowComposites(programs, properties, factory, computes, flipper);
        family(programs, properties, ProgramArrayId.Prepare, TextureStage.PREPARE, true, factory, computes, flipper, this.prepare);
        this.gbuffer = gbuffers.create(flipper);
        this.preTranslucentFlips = flipper.snapshot();
        PassFactory.applyPreFlips(properties.getExplicitFlips("deferred_pre"), flipper, "deferred_pre");
        family(programs, properties, ProgramArrayId.Deferred, TextureStage.DEFERRED, true, factory, computes, flipper, this.deferred);
        this.translucentFlips = flipper.snapshot();
        this.translucentGbuffer = this.deferred.isEmpty() ? this.gbuffer : gbuffers.create(flipper);
        PassFactory.applyPreFlips(properties.getExplicitFlips("composite_pre"), flipper, "composite_pre");
        family(programs, properties, ProgramArrayId.Composite, TextureStage.COMPOSITE_AND_FINAL, true, factory, computes,
                flipper, this.composite);
        Optional<ProgramSource> finalSource = programs.get(ProgramId.Final);
        FullscreenPass finalPass = finalSource.isPresent() && enabled(properties, finalSource.get())
                ? factory.finalPass(finalSource.get(), flipper) : null;
        if (finalPass != null) {
            this.composite.add(finalPass);
        } else {
            UmbraFramebuffer blit = new UmbraFramebuffer();
            this.blitSource = blit;
            blit.addColorAttachment(0, factory.front(flipper, 0));
            blit.readBuffer(0);
            GlState.checkComplete(blit, "fallback blit", new int[]{0});
        }
        for (List<FullscreenPass> family : families()) {
            for (FullscreenPass pass : family) {
                this.mipmapped.or(pass.mipmappedBuffers);
            }
        }
    }

    // Adds the enabled programs of an array as raster or compute only passes.
    private static void family(ProgramSet programs, ShaderProperties properties, ProgramArrayId id, TextureStage stage,
                               boolean draws, PassFactory factory, ComputeBuilder computes, BufferFlipper flipper,
                               List<FullscreenPass> target) {
        for (int i = 0; i < id.getNumPrograms(); i++) {
            Optional<ProgramSource> source = programs.get(id, i);
            if (source.isEmpty() || !enabled(properties, source.get())) {
                continue;
            }
            List<ComputePass> built = computes.build(source.get(), stage);
            FullscreenPass pass = draws && source.get().hasRasterStages()
                    ? factory.composite(source.get(), flipper, built) : null;
            if (pass != null) {
                target.add(pass);
            } else if (!built.isEmpty()) {
                target.add(FullscreenPass.computeOnly(source.get().getName(), factory.snapshotFrontTextures(flipper),
                        flipper.snapshot(), built));
            }
        }
    }

    // Adds the enabled shadow composite programs as raster or compute only passes.
    private void shadowComposites(ProgramSet programs, ShaderProperties properties, PassFactory factory,
                                  ComputeBuilder computes, BufferFlipper flipper) {
        for (int i = 0; i < ProgramArrayId.ShadowComposite.getNumPrograms(); i++) {
            Optional<ProgramSource> source = programs.get(ProgramArrayId.ShadowComposite, i);
            if (source.isEmpty() || !enabled(properties, source.get())) {
                continue;
            }
            List<ComputePass> built = computes.build(source.get(), TextureStage.SHADOWCOMP);
            FullscreenPass raster = source.get().hasRasterStages()
                    ? factory.shadowComposite(source.get(), flipper, built) : null;
            if (raster != null) {
                this.shadowComposite.add(raster);
            } else if (!built.isEmpty()) {
                this.shadowComposite.add(FullscreenPass.computeOnly(source.get().getName(),
                        factory.snapshotFrontTextures(flipper), flipper.snapshot(), built));
            }
        }
    }

    // Returns whether the pack properties enable a program.
    static boolean enabled(ShaderProperties properties, ProgramSource source) {
        return properties.getProgramEnabled(source.getName()).orElse(Boolean.TRUE);
    }

    // Returns every pass list in draw order.
    List<List<FullscreenPass>> families() {
        return List.of(this.setup, this.begin, this.shadowComposite, this.prepare, this.deferred, this.composite);
    }

    // Destroys the compute programs and the owned framebuffers and forgets every pass.
    public void destroy() {
        for (List<FullscreenPass> family : families()) {
            for (FullscreenPass pass : family) {
                pass.destroyComputes();
            }
        }
        for (FullscreenPass pass : this.shadowComposite) {
            if (pass.framebuffer != null) {
                pass.framebuffer.destroy();
            }
        }
        for (List<FullscreenPass> family : families()) {
            family.clear();
        }
        if (this.blitSource != null) {
            this.blitSource.destroy();
            this.blitSource = null;
        }
        if (this.translucentGbuffer != null && this.translucentGbuffer != this.gbuffer) {
            this.translucentGbuffer.destroy();
        }
        this.translucentGbuffer = null;
        if (this.gbuffer != null) {
            this.gbuffer.destroy();
            this.gbuffer = null;
        }
    }
}
