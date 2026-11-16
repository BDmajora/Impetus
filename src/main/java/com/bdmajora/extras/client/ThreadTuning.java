package com.bdmajora.extras.client;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import net.minecraft.client.Minecraft;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;

// Thread priorities of the game.
public final class ThreadTuning {
    // Whether the render thread skips its yield.
    public static volatile boolean removeRenderYield;

    // Client thread.
    private static volatile Thread clientThread;
    // Server thread.
    private static volatile Thread serverThread;
    // Priority of the client thread.
    private static volatile int renderPriority = Thread.NORM_PRIORITY;
    // Priority of the server thread.
    private static volatile int serverPriority = Thread.NORM_PRIORITY;

    // Prevents instantiation
    private ThreadTuning() {
    }

    // Applies the thread settings.
    public static void apply(ExtrasConfig.ThreadSettings settings) {
        removeRenderYield = settings.removeRenderYield;
        renderPriority = clampPriority(settings.renderThreadPriority);
        serverPriority = clampPriority(settings.serverThreadPriority);
        ChunkBuilder.WORKER_PRIORITY = clampPriority(settings.chunkBuilderPriority);

        if (clientThread == null && Minecraft.getMinecraft() != null && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            clientThread = Thread.currentThread();
        }
        setPriority(clientThread, renderPriority);
        setPriority(serverThread, serverPriority);
    }

    // Records the server thread and sets its priority.
    public static void onServerThreadStarted(Thread thread) {
        serverThread = thread;
        setPriority(thread, serverPriority);
    }

    // Forgets the server thread.
    public static void onServerThreadStopped() {
        serverThread = null;
    }

    // Sets the priority of a live thread and ignores a refusal.
    private static void setPriority(Thread thread, int priority) {
        if (thread == null || !thread.isAlive() || thread.getPriority() == priority) {
            return;
        }
        try {
            thread.setPriority(priority);
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    // Returns the priority limited to the valid range.
    private static int clampPriority(int priority) {
        return MathUtil.clamp(priority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
    }
}
