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

// Builds composite and final and shadow composite passes and advances the flip state.
public final class PassFactory {
    // Logger of passes that cannot be built.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Properties of the pack.
    private final ShaderProperties properties;
    // Program sources of the pack.
    private final PackSources sources;
    // Cache of compiled programs.
    private final ProgramCache programs;
    // Color targets of the pack.
    private final UmbraRenderTargets targets;
    // Shadow renderer or null.
    private final UmbraShadowRenderer shadow;
    // Targets flipped by at least one pass.
    private final BitSet flippedAtLeastOnce;

    // Creates a factory.
    public PassFactory(ShaderProperties properties, PackSources sources, ProgramCache programs, UmbraRenderTargets targets,
                       UmbraShadowRenderer shadow, BitSet flippedAtLeastOnce) {
        this.properties = properties;
        this.sources = sources;
        this.programs = programs;
        this.targets = targets;
        this.shadow = shadow;
        this.flippedAtLeastOnce = flippedAtLeastOnce;
    }

    // Builds a composite pass and flips its draw buffers or returns null and logs a failure.
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

    // Builds the final pass or returns null for the fallback blit.
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

    // Builds a shadow composite pass on the shadow color textures or returns null.
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
            return new FullscreenPass(name, program.program(), program.uniforms(), framebuffer,
                    snapshotFrontTextures(flipper), drawBuffers, ProgramBlendState.from(this.properties, name),
                    flipper.snapshot(), new BitSet(), computes, program.shadowSamplerKinds(), program.modern(),
                    new int[]{resolution, resolution}, null);
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build shadowcomp pass '{}'; it will be skipped: {}", name, e.getMessage());
            return null;
        }
    }

    // Returns the draw buffers that have a shadow color texture.
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

    // Returns the texture each target reads from under the current flips.
    int[] snapshotFrontTextures(BufferFlipper flipper) {
        int[] samplers = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
        for (int i = 0; i < samplers.length; i++) {
            UmbraRenderTarget target = this.targets.get(i);
            samplers[i] = target == null ? 0 : flipper.isFlipped(i) ? target.getAltTexture() : target.getMainTexture();
        }
        return samplers;
    }

    // Returns the texture a target reads from under the current flips.
    int front(BufferFlipper flipper, int index) {
        return GbufferTargets.front(this.targets, flipper.isFlipped(index), index);
    }

    // Returns the size of the first custom sized draw buffer or zeros.
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

    // Returns the flips of existing targets and reports the rest.
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

    // Applies the explicit flips that run before a pass family.
    static void applyPreFlips(Map<Integer, Boolean> flips, BufferFlipper flipper, String name) {
        validFlips(flips, name).forEach((buffer, flip) -> {
            if (flip) {
                flipper.flip(buffer);
            }
        });
    }
}
