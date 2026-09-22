package com.bdmajora.extras.client;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import net.minecraft.client.Minecraft;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;

// Thread priorities and the render loop's yield, after wisecase2's StutterFix: every value defaults to what the game does on its own, so nothing here moves until the user asks
public final class ThreadTuning {
    // Read from the render loop every frame, so a plain static rather than a config walk
    public static volatile boolean removeRenderYield;

    private static volatile Thread clientThread;
    private static volatile Thread serverThread;
    private static volatile int renderPriority = Thread.NORM_PRIORITY;
    private static volatile int serverPriority = Thread.NORM_PRIORITY;

    private ThreadTuning() {
    }

    // Applied whenever the Extras config loads or saves; the config loads on the client thread, which is how that thread is found
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

    // The server thread announces itself when its run loop starts, and is dropped when it stops
    public static void onServerThreadStarted(Thread thread) {
        serverThread = thread;
        setPriority(thread, serverPriority);
    }

    public static void onServerThreadStopped() {
        serverThread = null;
    }

    private static void setPriority(Thread thread, int priority) {
        if (thread == null || !thread.isAlive() || thread.getPriority() == priority) {
            return;
        }
        try {
            thread.setPriority(priority);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // A priority the JVM refuses is not worth a crash; the thread keeps what it had
        }
    }

    private static int clampPriority(int priority) {
        return MathUtil.clamp(priority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
    }
}
