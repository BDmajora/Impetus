package com.bdmajora.impetus.engine.impl.render.chunk.compile;

import org.jetbrains.annotations.Nullable;

// Lookup of the build context of the current thread.
public final class GlobalChunkBuildContext {
    // Context of the main thread.
    private static volatile ChunkBuildContext mainThreadContext;
    // Thread treated as the main thread.
    private static volatile Thread mainThread;

    // Prevents instantiation
    private GlobalChunkBuildContext() {}

    // Records the caller as the main thread.
    public static void setMainThread() {
        mainThread = Thread.currentThread();
    }

    // Returns the context of the current thread or null.
    @Nullable
    public static ChunkBuildContext get() {
        var thread = Thread.currentThread();
        // Checks the main thread first
        if(thread == mainThread) {
            return mainThreadContext;
        } else if(thread instanceof Holder holder) {
            return holder.impetus$getGlobalContext();
        } else {
            return null;
        }
    }

    // Stores the context of the main thread.
    public static void bindMainThread(ChunkBuildContext context) {
        mainThreadContext = context;
    }

    // Thread that owns a build context.
    public interface Holder {
        // Returns the context of the thread.
        ChunkBuildContext impetus$getGlobalContext();
    }
}
