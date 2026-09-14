package com.bdmajora.extras.async;

// Marker subclass so hot checks (profiler guard, Fulgor's wrong-thread warning) can answer "is this one of ours" with an instanceof rather than a set lookup
public final class ParallelWorkerThread extends Thread {
    public ParallelWorkerThread(Runnable task, String name) {
        super(task, name);
    }

    // FML answers getEffectiveSide() from the thread group, so a server-side pool must say so explicitly rather than inherit whichever thread happened to grow it
    public ParallelWorkerThread(ThreadGroup group, Runnable task, String name) {
        super(group, task, name);
    }

    // True on a pool thread; the main thread and every other caller read false
    public static boolean isCurrent() {
        return Thread.currentThread() instanceof ParallelWorkerThread;
    }
}
