package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// Section lattice and search thread shared by the graph searches of a world renderer.
public final class SectionGraph {
    // Lattice of the sections.
    private final SectionLattice lattice;

    // Thread that runs asynchronous searches or null.
    @Nullable
    private final ExecutorService executor;

    // Number of searches that were submitted and not joined.
    private int inFlight;

    // Lattice updates that wait for the searches to finish.
    private final ArrayDeque<Runnable> updateTasks = new ArrayDeque<>();

    // Creates the lattice and a search thread unless searches are inline.
    public SectionGraph(int minSectionY, int maxSectionY, AsyncOcclusionMode asyncMode, boolean hasShadowPass, boolean rasterOcclusion) {
        this.lattice = new SectionLattice(minSectionY, maxSectionY, hasShadowPass, rasterOcclusion);

        if (asyncMode != AsyncOcclusionMode.NONE) {
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("Impetus chunk graph search thread");
                thread.setDaemon(true);
                return thread;
            });
        } else {
            this.executor = null;
        }
    }

    // Returns the lattice.
    SectionLattice getLattice() {
        return this.lattice;
    }

    // Runs a search on the search thread or inline and counts it until it is joined.
    <T> CompletableFuture<T> submit(Supplier<T> task, boolean async) {
        java.util.Objects.requireNonNull(task, "task");
        this.inFlight++;

        try {
            if (async && this.executor != null) {
                return CompletableFuture.supplyAsync(task, this.executor);
            } else {
                return CompletableFuture.completedFuture(task.get());
            }
        } catch (Throwable failure) {
            this.inFlight--;
            throw failure;
        }
    }

    // Marks a search as joined and rejects a join without a submission.
    void onSearchJoined() {
        if (this.inFlight <= 0) {
            throw new IllegalStateException("No search in flight");
        }
        this.inFlight--;
    }

    // Runs the deferred lattice updates when no search is in flight.
    void runDeferredTasks() {
        if (this.inFlight > 0) {
            return;
        }

        Runnable task;

        while ((task = this.updateTasks.poll()) != null) {
            task.run();
        }
    }

    // Rejects a structural change while a search runs.
    private void assertSearchNotRunning() {
        if (this.inFlight > 0) {
            throw new IllegalStateException("Attempted to update occlusion graph during occlusion!");
        }
    }

    // Adds a section to the lattice.
    public void attachRenderSection(RenderSection section) {
        this.assertSearchNotRunning();
        this.lattice.attach(section);
    }

    // Removes a section from the lattice.
    public void detachRenderSection(RenderSection section) {
        this.assertSearchNotRunning();
        this.lattice.detach(section);
    }

    // Applies the metadata of a section now or after the searches and calls the callback when the search input changed.
    public void updateSectionMetadata(int x, int y, int z, long packedMetadata, Runnable onGraphInputChanged) {
        java.util.Objects.requireNonNull(onGraphInputChanged, "onGraphInputChanged");
        this.submitUpdateTask(() -> {
            if (this.lattice.updateSectionMetadata(x, y, z, packedMetadata)) {
                onGraphInputChanged.run();
            }
        });
    }

    // Runs a task now or defers it while a search runs.
    private void submitUpdateTask(Runnable runnable) {
        if (this.inFlight == 0) {
            runnable.run();
        } else {
            this.updateTasks.add(runnable);
        }
    }

    // Stops the search thread and waits for it.
    public void destroy() {
        if (this.executor != null) {
            this.executor.shutdown();

            try {
                if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.executor.shutdownNow();
                    throw new IllegalStateException("Async graph executor has not shut down");
                }
            } catch (InterruptedException e) {
                this.executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping the async graph executor", e);
            }
        }
    }
}
