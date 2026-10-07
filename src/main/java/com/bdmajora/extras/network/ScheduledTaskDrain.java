package com.bdmajora.extras.network;

import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.FutureTask;

// Vanilla's runGameLoop runs every queued main-thread task while holding the queue's monitor, and addScheduledTask takes the same monitor, so one long task (a server resource pack reload, 48 s on a large park server) parks the netty thread on its next packet: nothing is read, keep-alives go unanswered and the server drops the client mid-join, after which every packet that piled up meanwhile runs against the unloaded world. Here the monitor is held only to take each task off the queue; keep-alives are answered on the netty thread itself, so they get through while the task runs
public final class ScheduledTaskDrain {
    private ScheduledTaskDrain() {
    }

    public static void drain(Queue<FutureTask<?>> queue, Logger logger) {
        // Only the client thread polls, so the first `pending` entries are exactly the ones queued before the drain began; anything the network thread adds meanwhile waits for the next frame, as it did behind vanilla's lock, and a flooding server cannot keep the loop from ending
        int pending;
        synchronized (queue) {
            pending = queue.size();
        }
        for (int i = 0; i < pending; i++) {
            FutureTask<?> task;
            synchronized (queue) {
                task = queue.poll();
            }
            // Nothing else in vanilla polls, but a mod that drains the queue itself must not crash the frame
            if (task == null) {
                return;
            }
            Util.runTask(task, logger);
        }
    }
}
