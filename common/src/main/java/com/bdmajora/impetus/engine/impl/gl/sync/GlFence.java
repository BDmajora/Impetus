package com.bdmajora.impetus.engine.impl.gl.sync;

import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Wrapper of a GL sync object that reports when the GPU reached it.
public class GlFence {
    // GL name of the sync object.
    private final long id;
    // Deleted state of the sync object.
    private boolean disposed;

    // Creates a fence for a sync object and rejects a zero sync name.
    public GlFence(long id) {
        if (id == 0L) {
            throw new IllegalArgumentException("Sync object name is zero");
        }
        this.id = id;
    }

    // Returns whether the GPU signaled the fence without waiting and rejects a deleted fence.
    public boolean isCompleted() {
        this.checkDisposed();

        int result;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            result = LWJGL.glGetSynci(this.id, GL32.GL_SYNC_STATUS, count);

            int written = count.get(0);
            if (written != 1) {
                throw new IllegalStateException("glGetSynci returned " + written + " values instead of one");
            }
        }

        return result == GL32.GL_SIGNALED;
    }

    // Deletes the sync object once and ignores later calls.
    public void delete() {
        if (this.disposed) {
            return;
        }

        LWJGL.glDeleteSync(this.id);
        this.disposed = true;
    }

    // Rejects a deleted fence.
    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}
