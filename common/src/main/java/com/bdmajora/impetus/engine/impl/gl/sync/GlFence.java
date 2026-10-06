package com.bdmajora.impetus.engine.impl.gl.sync;

import org.lwjgl.opengl.GL32;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


import java.nio.IntBuffer;

public class GlFence {
    private final long id;
    private boolean disposed;

    public GlFence(long id) {
        this.id = id;
    }

    // Polls without blocking
    public boolean isCompleted() {
        this.checkDisposed();

        int result;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            result = LWJGL.glGetSynci(this.id, GL32.GL_SYNC_STATUS, count);

            if (count.get(0) != 1) {
                throw new RuntimeException("glGetSync returned more than one value");
            }
        }

        return result == GL32.GL_SIGNALED;
    }

    // Blocks until signalled
    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    // Blocks up to the timeout
    public void sync(long timeout) {
        this.checkDisposed();
        LWJGL.glWaitSync(this.id, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
    }

    // glDeleteSync
    public void delete() {
        LWJGL.glDeleteSync(this.id);
        this.disposed = true;
    }

    // Throws on use after delete
    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}
