package com.bdmajora.impetus.engine.impl.gl.sync;

import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

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
