package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.lwjgl.GL21;
import com.bdmajora.impetus.lwjgl.GL30;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// OptiFine's centerDepthSmooth, the depth at screen centre smoothed by the pack's centerDepthHalflife. The read goes through two pixel-pack buffers: this frame's glReadPixels lands in one asynchronously and the value consumed is LAST frame's, mapped from the other, so the CPU never drains the GPU for it; the synchronous form this replaced sat between the gbuffers and the composites and made every frame wait for the whole geometry pass to finish. One frame of latency on a value the pack smooths over a half-life anyway is invisible. Nothing is read at all until some program of the pack actually declares the uniform
public final class CenterDepthSampler {
    // Latest smoothed value, read by uniform suppliers; starts at 1.0 (far plane) so the first frame reads "looking at nothing" and DoF does not blur the whole world for a frame
    private static float currentSmoothed = 1.0f;
    // Set by the uniform supplier the first time a program with centerDepthSmooth uploads it, so packs without the uniform never start the readback
    private static volatile boolean requested;

    private final ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
    private UmbraFramebuffer readFramebuffer;
    private int attachedDepthTexture = -1;
    private float smoothed = 1.0f;
    private boolean initialized;
    // The two pack buffers, or -1 while unallocated / when the context lacks them (GL 2.1); index selects which one this frame reads into, the other holds last frame's result
    private final int[] packBuffers = {-1, -1};
    private int packIndex;
    // How many frames have been issued into the ring, so the first mapped read waits for a buffer that was actually written
    private int issued;
    private boolean packUnavailable;

    // The smoothed centre depth for the centerDepthSmooth uniform; a program uploading it is what switches the sampling on
    public static float getCenterDepthSmooth() {
        requested = true;
        return currentSmoothed;
    }

    // Samples and smooths the centre depth; depthTexture is passed per call since it changes on resize, halfLife <= 0 takes the raw sample, and the READ framebuffer is deliberately left pointing at the internal FBO
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
            // Issue this frame's read into one buffer, then take last frame's out of the other; the map only waits on a transfer the GPU had a whole frame to finish
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
            sampled = this.pixel.asFloatBuffer().get(0);
            haveSample = true;
        }

        if (haveSample) {
            if (!this.initialized) {
                this.initialized = true;
                this.smoothed = sampled;
            } else if (halfLife <= 0.0f) {
                this.smoothed = sampled;
            } else {
                // Exponential approach with the given half-life: after `halfLife` seconds, half the gap is closed.
                float rate = 1.0f - (float) Math.pow(0.5, Math.max(frameTime, 1.0e-4f) / halfLife);
                this.smoothed += (sampled - this.smoothed) * rate;
            }
            currentSmoothed = this.smoothed;
        }

        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // Allocates the two 4-byte pack buffers on first use; false on a context without pixel-pack buffers or map-range, which keeps the synchronous read
    private boolean packBuffersReady() {
        if (this.packUnavailable) {
            return false;
        }
        if (this.packBuffers[0] != -1) {
            return true;
        }
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!(caps.OpenGL21 || caps.GL_ARB_pixel_buffer_object) || !(caps.OpenGL30 || caps.GL_ARB_map_buffer_range)) {
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

    // Frees the readback resources
    public void destroy() {
        if (this.readFramebuffer != null) {
            this.readFramebuffer.destroy();
            this.readFramebuffer = null;
        }
        for (int i = 0; i < 2; i++) {
            if (this.packBuffers[i] != -1) {
                LWJGL.glDeleteBuffers(this.packBuffers[i]);
                this.packBuffers[i] = -1;
            }
        }
        this.issued = 0;
        this.initialized = false;
        this.attachedDepthTexture = -1;
        currentSmoothed = 1.0f;
        requested = false;
    }
}
