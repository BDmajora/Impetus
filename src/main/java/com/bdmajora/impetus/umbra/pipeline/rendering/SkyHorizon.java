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

// Fog colored octagonal wall that hides the void below the horizon.
public final class SkyHorizon {
    // Logger of draw failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Largest radius of the wall in blocks.
    private static final float MAX_RADIUS = 256.0f;
    // Height of the wall top above the camera.
    private static final double TOP = 16.0;
    // Corner index pairs of the octagon in order.
    private static final int[][] RING = {{2, 3}, {3, 2}, {3, 1}, {2, 1}, {1, 0}, {0, 1}, {0, 2}, {1, 3}};

    // Drawing state that blocks reentry.
    private boolean drawing;
    // Corner offsets of the octagon.
    private final double[] corners = new double[4];

    // Draws the wall once at a time and finishes the buffer when drawing fails.
    public void draw() {
        if (this.drawing) {
            return;
        }
        this.drawing = true;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        boolean begun = false;
        try {
            float radius = Math.min(Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0f, MAX_RADIUS);
            double[] corners = this.corners;
            corners[0] = radius * 0.9238D;
            corners[1] = radius * 0.3826D;
            corners[2] = -corners[1];
            corners[3] = -corners[0];
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
            if (begun) {
                buffer.finishDrawing();
            }
            LOGGER.warn("[Umbra] drawSkyHorizon failed: {}", t.toString());
        } finally {
            this.drawing = false;
        }
    }
}
