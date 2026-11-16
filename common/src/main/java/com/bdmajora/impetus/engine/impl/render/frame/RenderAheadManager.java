package com.bdmajora.impetus.engine.impl.render.frame;

import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

// Limits how many frames the CPU may run ahead of the GPU.
public class RenderAheadManager {
    // Fences of the frames in flight.
    private final LongArrayFIFOQueue fences = new LongArrayFIFOQueue();

    // Waits for the oldest fences until no more than the limit remain.
    public void startFrame(int renderAheadLimit) {
        int limit = Math.max(0, renderAheadLimit);

        while (this.fences.size() > limit) {
            var fence = this.fences.dequeueLong();
            LWJGL.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
            LWJGL.glDeleteSync(fence);
        }
    }

    // Adds a fence for the frame and rejects a failed fence.
    public void endFrame() {
        var fence = LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        if (fence == 0) {
            throw new RuntimeException("Failed to create fence object");
        }

        this.fences.enqueue(fence);
    }
}
