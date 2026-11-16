package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Buffer swaps and clears that run around each frame of a shader pack.
public final class FrameCopies {
    // Copy of the alternate texture of a flipped target back into its main texture.
    private record Swap(UmbraFramebuffer from, int index, int target) {
    }

    // Framebuffer cleared to a color or to the fog color when the color is null.
    private record Clear(UmbraFramebuffer framebuffer, float[] color) {
    }

    // Swaps of the flipped targets.
    private final List<Swap> swaps = new ArrayList<>();
    // Clears that run every frame.
    private final List<Clear> everyFrame = new ArrayList<>();
    // Clears that run on a full reset.
    private final List<Clear> full = new ArrayList<>();

    // Creates the swaps and clears of the targets and copies each flipped target once.
    public void build(UmbraRenderTargets targets, TargetDirectives directives, BufferFlipper flipper, int maxGroup) {
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            UmbraRenderTarget target = targets.get(i);
            if (target == null || !flipper.isFlipped(i) || directives.clears(i)) {
                continue;
            }
            UmbraFramebuffer from = new UmbraFramebuffer();
            try {
                from.addColorAttachment(0, target.getAltTexture());
                from.readBuffer(0);
                GlState.checkComplete(from, "swap colortex" + i, new int[]{i});
            } catch (RuntimeException e) {
                from.destroy();
                throw e;
            }
            this.swaps.add(new Swap(from, i, target.getMainTexture()));
        }
        Map<String, List<Integer>> fullGroups = new LinkedHashMap<>();
        Map<String, List<Integer>> frameGroups = new LinkedHashMap<>();
        Map<String, float[]> colors = new LinkedHashMap<>();
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            if (targets.get(i) == null) {
                continue;
            }
            float[] color = directives.clearColor(i);
            String key = (color == null ? "fog" : Arrays.toString(color)) + "@"
                    + (targets.hasCustomSize(i) ? targets.getWidth(i) + "x" + targets.getHeight(i) : "render");
            colors.put(key, color);
            fullGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            if (directives.clears(i)) {
                frameGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }
        addClears(targets, fullGroups, colors, Math.max(1, maxGroup), this.full);
        addClears(targets, frameGroups, colors, Math.max(1, maxGroup), this.everyFrame);
    }

    // Adds two clears for each group of at most the group size of targets that share a color.
    private static void addClears(UmbraRenderTargets targets, Map<String, List<Integer>> groups, Map<String, float[]> colors,
                                  int maxGroup, List<Clear> into) {
        groups.forEach((key, indices) -> {
            for (int start = 0; start < indices.size(); start += maxGroup) {
                int end = Math.min(indices.size(), start + maxGroup);
                int[] chunk = new int[end - start];
                for (int i = start; i < end; i++) {
                    chunk[i - start] = indices.get(i);
                }
                into.add(new Clear(targets.createClearFramebuffer(false, chunk), colors.get(key)));
                into.add(new Clear(targets.createClearFramebuffer(true, chunk), colors.get(key)));
            }
        });
    }

    // Clears every target or only those that clear each frame and restores the viewport.
    public void clear(boolean everything, int renderWidth, int renderHeight) {
        for (Clear clear : everything ? this.full : this.everyFrame) {
            clear.framebuffer().bind();
            float[] color = clear.color();
            if (color == null) {
                Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
                GlStateManager.clearColor(fog.x, fog.y, fog.z, 1.0f);
            } else {
                GlStateManager.clearColor(color[0], color[1], color[2], color[3]);
            }
            LWJGL.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        LWJGL.glViewport(0, 0, renderWidth, renderHeight);
    }

    // Copies each flipped target back into its main texture.
    public void swap(UmbraRenderTargets targets) {
        if (this.swaps.isEmpty()) {
            return;
        }
        GlTextureUnits.selectScratch(SamplerLayout.DEPTH_COPY_SCRATCH_UNIT);
        try {
            for (Swap swap : this.swaps) {
                swap.from().bind();
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, swap.target());
                LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                        targets.getWidth(swap.index()), targets.getHeight(swap.index()));
            }
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GlTextureUnits.releaseScratch();
        }
    }

    // Destroys the swap framebuffers and forgets every clear.
    public void destroy() {
        for (Swap swap : this.swaps) {
            swap.from().destroy();
        }
        this.swaps.clear();
        this.everyFrame.clear();
        this.full.clear();
    }
}
