package com.bdmajora.extras.async;

// Thread of the parallel pool.
public final class ParallelWorkerThread extends Thread {
    // Creates a thread.
    public ParallelWorkerThread(Runnable task, String name) {
        super(task, name);
    }

    // Creates a thread in the group.
    public ParallelWorkerThread(ThreadGroup group, Runnable task, String name) {
        super(group, task, name);
    }

    // Returns whether the current thread is a worker.
    public static boolean isCurrent() {
        return Thread.currentThread() instanceof ParallelWorkerThread;
    }
}
