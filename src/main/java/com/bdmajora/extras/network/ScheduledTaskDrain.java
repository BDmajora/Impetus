package com.bdmajora.extras.network;

import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.FutureTask;

// Drainer of the scheduled main thread tasks.
public final class ScheduledTaskDrain {
    // Prevents instantiation
    private ScheduledTaskDrain() {
    }

    // Runs the queued server tasks and logs any that fail.
    public static void drain(Queue<FutureTask<?>> queue, Logger logger) {
        int pending;
        synchronized (queue) {
            pending = queue.size();
        }
        for (int i = 0; i < pending; i++) {
            FutureTask<?> task;
            synchronized (queue) {
                task = queue.poll();
            }
            if (task == null) {
                return;
            }
            Util.runTask(task, logger);
        }
    }
}
