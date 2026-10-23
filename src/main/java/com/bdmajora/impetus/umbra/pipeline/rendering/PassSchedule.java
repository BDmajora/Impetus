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

// ONE baked frame schedule like Umbra, in its family order (setup, begin, shadowcomp, prepare, gbuffers, deferred, translucents, composite, final); buffers the chain leaves odd-flipped are copied back at frame end, so every frame starts from "main = latest" and every baked FBO and snapshot stays valid
public final class PassSchedule {
    // Compute-only, dispatched once after the pipeline is built (Umbra runs it on dimension change, Photon seeds its LPV volumes with it)
    final List<FullscreenPass> setup = new ArrayList<>();
    // Runs at the very start of world rendering, before anything is drawn
    final List<FullscreenPass> begin = new ArrayList<>();
    // Dispatched right after the shadow map renders (Umbra's ShadowCompositeRenderer)
    final List<FullscreenPass> shadowComposite = new ArrayList<>();
    // After the shadow map and before the gbuffers; Photon builds its cloud shadow and coverage maps into colortex8 here
    final List<FullscreenPass> prepare = new ArrayList<>();
    final List<FullscreenPass> deferred = new ArrayList<>();
    // Composite then final
    final List<FullscreenPass> composite = new ArrayList<>();
    // Every colour target any pass mipmaps, so frame end only resets what was touched
    final BitSet mipmapped = new BitSet();
    UmbraFramebuffer gbuffer;
    UmbraFramebuffer translucentGbuffer;
    // The post-composite colortex0 shown when the pack has no working final program
    UmbraFramebuffer blitSource;
    // The colortex flip snapshots gbuffer programs read before and after the deferred chain
    BitSet preTranslucentFlips = new BitSet();
    BitSet translucentFlips = new BitSet();

    // Bakes the schedule from the flipper's reset state, advancing it as it goes
    public void build(ProgramSet programs, ShaderProperties properties, PassFactory factory, ComputeBuilder computes,
                      GbufferTargets gbuffers, BufferFlipper flipper) {
        // Setup is compute-only like Iris's; a stray setup.vsh/fsh used to be scheduled, flipping buffers for a draw that never ran
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
            // No working final program: show the post-composite colortex0 by blitting it to the screen
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

    // One numbered family in index order: a vertex+fragment pair draws, a .csh-only entry is compute-only (how Photon's deferred4_a.csh runs at all), and a draw that fails to build still runs its computes as Umbra would
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

    // The shadowcomp family (Complementary's floodfill computes); a raster entry draws into shadowcolor, and program.<name>.enabled now applies to it too
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

    // program.<name>.enabled
    static boolean enabled(ShaderProperties properties, ProgramSource source) {
        return properties.getProgramEnabled(source.getName()).orElse(Boolean.TRUE);
    }

    // Every family, in run order
    List<List<FullscreenPass>> families() {
        return List.of(this.setup, this.begin, this.shadowComposite, this.prepare, this.deferred, this.composite);
    }

    // Frees every compute, the raster shadowcomp framebuffers, the blit source and both gbuffer FBOs; the colour FBOs belong to the render targets and the draw programs to the program cache
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
