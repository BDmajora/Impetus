package com.bdmajora.impetus.engine.impl.render.frame;

import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

public class RenderAheadManager {
    private final LongArrayFIFOQueue fences = new LongArrayFIFOQueue();

    // Inserts a fence for this frame and drops any beyond the limit
    public void startFrame(int renderAheadLimit) {
        while (this.fences.size() > renderAheadLimit) {
            var fence = this.fences.dequeueLong();
            // ClientWaitSync rather than WaitSync keeps the CPU from running ahead of the GPU and protects the persistently-mapped staging buffers; with GL_SYNC_FLUSH_COMMANDS_BIT the flush effectively acts as a Finish for everything before the fence
            LWJGL.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
            LWJGL.glDeleteSync(fence);
        }
    }

    // Blocks on the oldest fence once the limit is reached, capping CPU run-ahead
    public void endFrame() {
        var fence = LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        if (fence == 0) {
            throw new RuntimeException("Failed to create fence object");
        }

        this.fences.enqueue(fence);
    }
}
