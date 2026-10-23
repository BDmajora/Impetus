package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

// Port of OptiFine's Shaders.drawHorizon: an octagonal ring at the render-distance edge from ground level to y = 16 through gbuffers_skybasic, right before the sky disc; vanilla leaves a band at the horizon uncovered, which kept last frame's non-cleared colortex1 and self-perpetuated into a blown band
public final class SkyHorizon {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // At high render distances the ring reaches the far plane and clips, letting the band return; 256 blocks is beyond the terrain fog yet inside the sky projection (Umbra HorizonRenderer)
    private static final float MAX_RADIUS = 256.0f;
    private static final double TOP = 16.0;
    // OptiFine's ring corners as indices into {d0, d1, d2, d3} = {0.9238r, 0.3826r, -0.3826r, -0.9238r}, x then z; each quad joins one corner to the next
    private static final int[][] RING = {{2, 3}, {3, 2}, {3, 1}, {2, 1}, {1, 0}, {0, 1}, {0, 2}, {1, 3}};

    private boolean drawing;

    // Draws the ring; re-entry (a mod drawing the sky from inside this) is ignored, and a failure leaves the shared BufferBuilder usable for vanilla's next begin
    public void draw() {
        if (this.drawing) {
            return;
        }
        this.drawing = true;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        boolean begun = false;
        try {
            float radius = Math.min(Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0f, MAX_RADIUS);
            double[] corners = {radius * 0.9238D, radius * 0.3826D, -(radius * 0.3826D), -(radius * 0.9238D)};
            double bottom = -CapturedRenderingState.INSTANCE.getCameraPosition().y;
            Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
            GlStateManager.color(fog.x, fog.y, fog.z);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            begun = true;
            for (int i = 0; i < RING.length; i++) {
                int[] from = RING[i];
                int[] to = RING[(i + 1) % RING.length];
                buffer.pos(corners[from[0]], bottom, corners[from[1]]).endVertex();
                buffer.pos(corners[from[0]], TOP, corners[from[1]]).endVertex();
                buffer.pos(corners[to[0]], TOP, corners[to[1]]).endVertex();
                buffer.pos(corners[to[0]], bottom, corners[to[1]]).endVertex();
            }
            begun = false;
            Tessellator.getInstance().draw();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            // A half-built ring left the buffer "already building" and vanilla's next begin crashed the client
            if (begun) {
                buffer.finishDrawing();
            }
            LOGGER.warn("[Umbra] drawSkyHorizon failed: {}", t.toString());
        } finally {
            this.drawing = false;
        }
    }
}
