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

// The frame's target clears and the end-of-frame alt->main copy-back for buffers the chain leaves odd-flipped (Umbra's SwapPass)
public final class FrameCopies {
    // "from" reads the ALT texture, target is MAIN; the size is read per frame, a size baked at build copied the old region after a resize (GL_INVALID_VALUE once the window shrank)
    private record Swap(UmbraFramebuffer from, int index, int target) {
    }

    // One FBO over every target sharing a clear colour (null = fog) and size, so they clear in one glClear
    private record Clear(UmbraFramebuffer framebuffer, float[] color) {
    }

    private final List<Swap> swaps = new ArrayList<>();
    // Only buffers whose colortexNClear directive is true, both sides
    private final List<Clear> everyFrame = new ArrayList<>();
    // First frame or resized storage: every materialised buffer, both sides
    private final List<Clear> full = new ArrayList<>();

    // Swaps for every buffer the schedule left flipped and not cleared at frame start; gbuffer attachments are not special-cased since Complementary marks some clear=false for temporal contents
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
            // An FBO of mixed sizes clears only their intersection, so size is part of the group
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

    // Both sides of each group, cut into FBOs of at most maxGroup attachments (the driver's draw-buffer limit)
    private static void addClears(UmbraRenderTargets targets, Map<String, List<Integer>> groups, Map<String, float[]> colors,
                                  int maxGroup, List<Clear> into) {
        groups.forEach((key, indices) -> {
            for (int start = 0; start < indices.size(); start += maxGroup) {
                int[] chunk = indices.subList(start, Math.min(indices.size(), start + maxGroup)).stream()
                        .mapToInt(Integer::intValue).toArray();
                into.add(new Clear(targets.createClearFramebuffer(false, chunk), colors.get(key)));
                into.add(new Clear(targets.createClearFramebuffer(true, chunk), colors.get(key)));
            }
        });
    }

    // Runs the frame's clears; glClear ignores the viewport, so only the render-size viewport is set once afterwards rather than per clear
    public void clear(boolean everything, int renderWidth, int renderHeight) {
        for (Clear clear : everything ? this.full : this.everyFrame) {
            clear.framebuffer().bind();
            float[] color = clear.color();
            if (color == null) {
                // Umbra clears colortex0 to the current fog colour by default
                Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
                GlStateManager.clearColor(fog.x, fog.y, fog.z, 1.0f);
            } else {
                GlStateManager.clearColor(color[0], color[1], color[2], color[3]);
            }
            LWJGL.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        LWJGL.glViewport(0, 0, renderWidth, renderHeight);
    }

    // The copy-back at each target's own size (a size.buffer target must not be copied at the screen's); glCopyTexSubImage2D reads the GL_READ_BUFFER of GL_FRAMEBUFFER (bind(), not bindAsReadBuffer(), which broke TAA on many drivers for Umbra), and the destination is bound on a scratch unit no cached slot describes
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

    // Frees the swap FBOs; the clear FBOs belong to the render targets
    public void destroy() {
        for (Swap swap : this.swaps) {
            swap.from().destroy();
        }
        this.swaps.clear();
        this.everyFrame.clear();
        this.full.clear();
    }
}
