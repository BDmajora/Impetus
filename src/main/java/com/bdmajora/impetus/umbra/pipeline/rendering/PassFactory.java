package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Builds the drawing passes of the schedule: a family entry's fullscreen pass, the final pass and a raster shadowcomp; each reads the front sides of the flip state it is built under and writes the back sides
public final class PassFactory {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final ShaderProperties properties;
    private final PackSources sources;
    private final ProgramCache programs;
    private final UmbraRenderTargets targets;
    private final UmbraShadowRenderer shadow;
    // Colour targets flipped by an earlier pass; a custom-texture override on a colortex deactivates once a pass has written it (Umbra parity)
    private final BitSet flippedAtLeastOnce;

    public PassFactory(ShaderProperties properties, PackSources sources, ProgramCache programs, UmbraRenderTargets targets,
                       UmbraShadowRenderer shadow, BitSet flippedAtLeastOnce) {
        this.properties = properties;
        this.sources = sources;
        this.programs = programs;
        this.targets = targets;
        this.shadow = shadow;
        this.flippedAtLeastOnce = flippedAtLeastOnce;
    }

    // A family pass into its own colour framebuffer, flipping what it writes; null when its program cannot be built, leaving the flip state untouched
    FullscreenPass composite(ProgramSource source, BufferFlipper flipper, List<ComputePass> computes) {
        String name = source.getName();
        try {
            ProgramCache.Compiled program = this.programs.get(source);
            if (program == null) {
                return null;
            }
            int[] drawBuffers = DrawBuffers.sanitize(program.program().getDrawBuffers(), UmbraRenderTargets.MAX_COLOR_BUFFERS);
            Map<Integer, Boolean> explicitFlips = validFlips(this.properties.getExplicitFlips(name), name);
            BitSet flipsBefore = flipper.snapshot();
            int[] colorSamplers = snapshotFrontTextures(flipper);
            UmbraFramebuffer framebuffer = this.targets.createColorFramebuffer(drawBuffers);
            FullscreenPass pass = new FullscreenPass(name, program.program(), program.uniforms(), framebuffer, colorSamplers,
                    drawBuffers, ProgramBlendState.from(this.properties, name), flipsBefore,
                    TargetDirectives.mipmappedBuffers(source, this.sources), computes, program.shadowSamplerKinds(),
                    program.modern(), viewportSize(name, drawBuffers), this.properties.getViewportScale(name));
            // Flips last, once nothing can throw, so a skipped pass leaves the schedule's flip state as it was; a buffer both written and explicitly flipped flips twice, as Iris does
            for (int buffer : drawBuffers) {
                if (!Boolean.FALSE.equals(explicitFlips.get(buffer))) {
                    flipper.flip(buffer);
                    this.flippedAtLeastOnce.set(buffer);
                }
            }
            explicitFlips.forEach((buffer, flip) -> {
                if (flip) {
                    flipper.flip(buffer);
                    this.flippedAtLeastOnce.set(buffer);
                }
            });
            return pass;
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build pass '{}'; it will be skipped: {}", name, e.getMessage());
            return null;
        }
    }

    // The final program, which writes the main framebuffer rather than a colortex; null falls back to the colortex0 blit
    FullscreenPass finalPass(ProgramSource source, BufferFlipper flipper) {
        String name = source.getName();
        try {
            ProgramCache.Compiled program = this.programs.get(source);
            if (program == null) {
                return null;
            }
            return new FullscreenPass(name, program.program(), program.uniforms(), null, snapshotFrontTextures(flipper),
                    DrawBuffers.DEFAULT.clone(), ProgramBlendState.from(this.properties, name), flipper.snapshot(),
                    TargetDirectives.mipmappedBuffers(source, this.sources), Collections.emptyList(),
                    program.shadowSamplerKinds(), program.modern(), new int[2], null);
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build final pass; falling back to colortex0 blit: {}", e.getMessage());
            return null;
        }
    }

    // A raster shadowcomp: a full-screen quad into the shadow pass's own colour attachments at shadow-map resolution, which do not ping-pong; the pass owns its framebuffer
    FullscreenPass shadowComposite(ProgramSource source, BufferFlipper flipper, List<ComputePass> computes) {
        String name = source.getName();
        if (this.shadow == null) {
            LOGGER.warn("[Umbra] '{}' draws into shadowcolor but the pack declares no shadow program; skipping", name);
            return null;
        }
        try {
            ProgramCache.Compiled program = this.programs.get(source);
            if (program == null) {
                return null;
            }
            int[] drawBuffers = shadowDrawBuffers(name, program.program().getDrawBuffers());
            if (drawBuffers.length == 0) {
                LOGGER.warn("[Umbra] '{}' writes no shadowcolor buffer this pack has; skipping", name);
                return null;
            }
            UmbraFramebuffer framebuffer = new UmbraFramebuffer();
            try {
                for (int i = 0; i < drawBuffers.length; i++) {
                    framebuffer.addColorAttachment(drawBuffers[i], i, this.shadow.getColorTextureId(drawBuffers[i]));
                }
                GlState.checkComplete(framebuffer, "shadowcomp", drawBuffers);
            } catch (RuntimeException e) {
                framebuffer.destroy();
                throw e;
            }
            int resolution = this.shadow.getResolution();
            // The flip snapshot of its place in the chain, so its images and samplers hit the same sides as everything else
            return new FullscreenPass(name, program.program(), program.uniforms(), framebuffer,
                    snapshotFrontTextures(flipper), drawBuffers, ProgramBlendState.from(this.properties, name),
                    flipper.snapshot(), new BitSet(), computes, program.shadowSamplerKinds(), program.modern(),
                    new int[]{resolution, resolution}, null);
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build shadowcomp pass '{}'; it will be skipped: {}", name, e.getMessage());
            return null;
        }
    }

    // A shadowcomp DRAWBUFFERS index past the shadowcolor buffers that exist has nothing to attach to
    private int[] shadowDrawBuffers(String name, int[] drawBuffers) {
        int[] kept = new int[drawBuffers.length];
        int count = 0;
        for (int buffer : drawBuffers) {
            if (this.shadow.getColorTextureId(buffer) == 0) {
                LOGGER.warn("[Umbra] '{}' writes shadowcolor{}, which this pack does not have; dropping it", name, buffer);
                continue;
            }
            kept[count++] = buffer;
        }
        return Arrays.copyOf(kept, count);
    }

    // The front texture of every existing colour target under the flip state
    int[] snapshotFrontTextures(BufferFlipper flipper) {
        int[] samplers = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
        for (int i = 0; i < samplers.length; i++) {
            UmbraRenderTarget target = this.targets.get(i);
            samplers[i] = target == null ? 0 : flipper.isFlipped(i) ? target.getAltTexture() : target.getMainTexture();
        }
        return samplers;
    }

    // The front texture of one colour target under the flip state, materialising it
    int front(BufferFlipper flipper, int index) {
        return GbufferTargets.front(this.targets, flipper.isFlipped(index), index);
    }

    // The size of the buffers a pass writes, zero for the render size; Umbra throws on mixed sizes, here the first size wins and the mismatch is logged rather than losing a stage
    int[] viewportSize(String name, int[] drawBuffers) {
        int[] size = new int[2];
        for (int buffer : drawBuffers) {
            if (!this.targets.hasCustomSize(buffer)) {
                continue;
            }
            int width = this.targets.getWidth(buffer);
            int height = this.targets.getHeight(buffer);
            if (size[0] == 0) {
                size[0] = width;
                size[1] = height;
            } else if (size[0] != width || size[1] != height) {
                LOGGER.warn("[Umbra] Pass '{}' writes buffers of different sizes ({}x{} vs colortex{} at {}x{}); "
                        + "using the first", name, size[0], size[1], buffer, width, height);
            }
        }
        return size;
    }

    // flip.<pass>.colortexN keys outside the colour targets are dropped; colortex-1 used to throw out of the BitSet and skip the pass, or fail the whole pipeline for deferred_pre/composite_pre
    static Map<Integer, Boolean> validFlips(Map<Integer, Boolean> flips, String name) {
        boolean valid = true;
        for (Integer buffer : flips.keySet()) {
            valid &= buffer >= 0 && buffer < UmbraRenderTargets.MAX_COLOR_BUFFERS;
        }
        if (valid) {
            return flips;
        }
        Map<Integer, Boolean> kept = new LinkedHashMap<>();
        flips.forEach((buffer, flip) -> {
            if (buffer >= 0 && buffer < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                kept.put(buffer, flip);
            } else {
                LOGGER.warn("[Umbra] Ignoring flip.{}.colortex{}: there is no such colour target", name, buffer);
            }
        });
        return kept;
    }

    // Honours a <name>_pre flip set (deferred_pre, composite_pre) before a family runs; these do not count as writes
    static void applyPreFlips(Map<Integer, Boolean> flips, BufferFlipper flipper, String name) {
        validFlips(flips, name).forEach((buffer, flip) -> {
            if (flip) {
                flipper.flip(buffer);
            }
        });
    }
}
