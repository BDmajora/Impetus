package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;

// Krypton's flush consolidation on 1.12.2: every sendPacket on the server thread ends in channel.writeAndFlush, and a flush is a syscall, so a tick that sends forty entity updates to a player makes forty syscalls where one would do. While the server thread is inside MinecraftServer.tick the write is queued without a flush and every connection is flushed once at the end of the tick. Only the calling thread's own state is consulted, so a send from a worker or the client thread keeps vanilla's immediate flush
public final class FlushBatch {
    private static final ThreadLocal<Boolean> IN_TICK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FlushBatch() {
    }

    public static boolean shouldDefer() {
        return IN_TICK.get() && Extras.options().network.flushConsolidation;
    }

    public static void beginTick() {
        IN_TICK.set(Boolean.TRUE);
    }

    public static void endTick() {
        IN_TICK.set(Boolean.FALSE);
    }

    // Implemented on NetworkManager: flush() only if a deferred write happened since the last flush
    public interface Deferrable {
        void impetus$flushDeferred();
    }
}
