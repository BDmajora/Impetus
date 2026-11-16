package com.bdmajora.impetus.engine.impl.util.task;

// Flag that signals a cancelled task.
public interface CancellationToken {
    // Returns whether the task was cancelled.
    boolean isCancelled();

    // Marks the task as cancelled.
    void setCancelled();
}
