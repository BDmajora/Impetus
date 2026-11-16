package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Reads the depth at the screen center with a two buffer readback ring and smooths it.
public final class CenterDepthSampler {
    // Smoothed depth published to the uniforms.
    private static float currentSmoothed = 1.0f;
    // Requested state of the depth since the last destroy.
    private static volatile boolean requested;

    // Scratch of a synchronous read.
    private final ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
    // Framebuffer that reads the depth texture or null.
    private UmbraFramebuffer readFramebuffer;
    // GL name of the attached depth texture or minus one.
    private int attachedDepthTexture = -1;
    // Smoothed depth of this sampler.
    private float smoothed = 1.0f;
    // First sample state.
    private boolean initialized;
    // Pixel pack buffers of the ring or minus one.
    private final int[] packBuffers = {-1, -1};
    // Ring slot written next.
    private int packIndex;
    // Number of reads issued into the ring.
    private int issued;
    // Missing pixel pack support state.
    private boolean packUnavailable;

    // Marks the depth as requested and returns the smoothed value.
    public static float getCenterDepthSmooth() {
        requested = true;
        return currentSmoothed;
    }

    // Reads the center depth of a texture when requested and smooths it by a half life.
    public void sample(int depthTexture, int width, int height, float frameTime, float halfLife) {
        if (!requested || width <= 0 || height <= 0) {
            return;
        }

        if (this.readFramebuffer == null || this.attachedDepthTexture != depthTexture) {
            if (this.readFramebuffer != null) {
                this.readFramebuffer.destroy();
            }
            this.readFramebuffer = new UmbraFramebuffer();
            this.readFramebuffer.bind();
            this.readFramebuffer.addDepthAttachment(depthTexture);
            this.readFramebuffer.noDrawBuffers();
            this.attachedDepthTexture = depthTexture;
        }

        this.readFramebuffer.bindAsReadBuffer();

        float sampled;
        boolean haveSample;
        if (this.packBuffersReady()) {
            int write = this.packBuffers[this.packIndex];
            int read = this.packBuffers[this.packIndex ^ 1];
            LWJGL.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, write);
            LWJGL.glReadPixels(width / 2, height / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0L);
            this.issued++;
            haveSample = this.issued >= 2;
            sampled = 1.0f;
            if (haveSample) {
                LWJGL.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, read);
                ByteBuffer mapped = LWJGL.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, 4L, GL30.GL_MAP_READ_BIT);
                if (mapped != null) {
                    sampled = mapped.order(ByteOrder.nativeOrder()).getFloat(0);
                    LWJGL.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                } else {
                    haveSample = false;
                }
            }
            LWJGL.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            this.packIndex ^= 1;
        } else {
            this.pixel.clear();
            LWJGL.glReadPixels(width / 2, height / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, this.pixel);
            sampled = this.pixel.getFloat(0);
            haveSample = true;
        }

        if (haveSample) {
            if (!this.initialized) {
                this.initialized = true;
                this.smoothed = sampled;
            } else if (halfLife <= 0.0f) {
                this.smoothed = sampled;
            } else {
                float rate = 1.0f - (float) Math.pow(0.5, Math.max(frameTime, 1.0e-4f) / halfLife);
                this.smoothed += (sampled - this.smoothed) * rate;
            }
            currentSmoothed = this.smoothed;
        }

        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // Creates the ring on first use and returns false without pixel pack support.
    private boolean packBuffersReady() {
        if (this.packUnavailable) {
            return false;
        }
        if (this.packBuffers[0] != -1) {
            return true;
        }
        if (!(LWJGL.isOpenGLVersionSupported(2, 1) || LWJGL.isExtensionSupported(GLExtension.ARB_pixel_buffer_object))
                || !(LWJGL.isOpenGLVersionSupported(3, 0) || LWJGL.isExtensionSupported(GLExtension.ARB_map_buffer_range))) {
            this.packUnavailable = true;
            return false;
        }
        for (int i = 0; i < 2; i++) {
            int buffer = LWJGL.glGenBuffers();
            LWJGL.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
            LWJGL.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 4L, GL15.GL_STREAM_READ);
            this.packBuffers[i] = buffer;
        }
        LWJGL.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        this.issued = 0;
        this.packIndex = 0;
        return true;
    }

    // Releases the framebuffer and the ring and resets the published depth.
    public void destroy() {
        UmbraFramebuffer framebuffer = this.readFramebuffer;
        this.readFramebuffer = null;
        try {
            if (framebuffer != null) {
                framebuffer.destroy();
            }
        } finally {
            for (int i = 0; i < 2; i++) {
                int buffer = this.packBuffers[i];
                this.packBuffers[i] = -1;
                if (buffer != -1) {
                    LWJGL.glDeleteBuffers(buffer);
                }
            }
        }
        this.issued = 0;
        this.initialized = false;
        this.attachedDepthTexture = -1;
        currentSmoothed = 1.0f;
        requested = false;
    }
}
