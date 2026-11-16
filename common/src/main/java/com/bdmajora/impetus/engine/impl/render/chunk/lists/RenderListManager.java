package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

// Render lists of one pass and the search that produces them.
public class RenderListManager {
    // Lists of the visible sections.
    @Getter
    @NotNull
    private SortedRenderLists renderLists;
    // Sections that need a rebuild.
    @Getter
    @NotNull
    private ChunkRebuildLists rebuildLists;

    // Graph shared with the other pass.
    private final SectionGraph graph;
    // Lattice of the graph.
    private final SectionLattice lattice;
    // Whether this manager serves the shadow pass.
    private final boolean shadow;
    // Whether the searches run on the search thread.
    private final boolean async;

    // Search in progress or null.
    private CompletableFuture<VisibleChunkCollector> currentOcclusionFuture;

    // Whether a new search is needed.
    @Getter
    @Setter
    private boolean needsUpdate = true;

    // Frame of the lists in use.
    @Getter
    private int lastUpdatedFrame;

    // Frame of the search in progress.
    private int pendingLastUpdatedFrame;

    // Visibility of the lists in use.
    @NotNull
    private SectionLattice.VisibilitySnapshot visibilitySnapshot = SectionLattice.VisibilitySnapshot.EMPTY;

    // Visibility produced by the search in progress.
    @Nullable
    private SectionLattice.VisibilitySnapshot pendingVisibilitySnapshot;

    // Ticker of the visible sections or null.
    @Nullable
    private final SectionTicker sectionTicker;

    // Counts shown on the debug screen.
    public record RenderListDebugStatistics(Object2IntOpenHashMap<TerrainRenderPass> renderPassCounts, int[] sortingSectionCounts) {
        // Returns the sorting counts as text.
        public String getSortingString() {
            StringBuilder sb = new StringBuilder();

            sb.append("Sorting: ");
            String[] names = SortState.DEBUG_NAMES;
            for (int i = 0; i < names.length; i++) {
                sb.append(names[i]);
                sb.append('=');
                sb.append(sortingSectionCounts[i]);
                if((i + 1) < names.length) {
                    sb.append(", ");
                }
            }

            return sb.toString();
        }
    }

    // Cached debug counts.
    private RenderListDebugStatistics debugStatistics;

    // Creates a manager and rejects a null graph.
    public RenderListManager(SectionGraph graph, boolean shadow, AsyncOcclusionMode mode, @Nullable SectionTicker sectionTicker) {
        this.graph = java.util.Objects.requireNonNull(graph, "graph");
        this.lattice = graph.getLattice();
        this.shadow = shadow;
        this.async = mode == AsyncOcclusionMode.EVERYTHING || (shadow && mode == AsyncOcclusionMode.ONLY_SHADOW);
        this.sectionTicker = sectionTicker;
        this.renderLists = SortedRenderLists.empty();
        this.rebuildLists = ChunkRebuildLists.empty();
    }

    // Starts the search from the camera and rejects use on the shadow manager.
    public void startGraphUpdate(Viewport viewport, int frame, int regionIdsLength, float searchDistance, boolean useOcclusionCulling, int targetQueueSize) {
        if (this.shadow) {
            throw new IllegalStateException("startGraphUpdate is for the terrain pass; use startShadowGraphUpdate");
        }

        this.lattice.ensureWindowCovers(viewport.getChunkCoord(), searchDistance);

        this.submitSearch(frame, regionIdsLength, targetQueueSize, viewport, visitor ->
                this.lattice.findVisible(visitor, viewport, searchDistance, regionIdsLength, useOcclusionCulling, true, frame));
    }

    // Starts the shadow search and rejects use on the terrain manager.
    public void startShadowGraphUpdate(Viewport shadowViewport, int frame, int regionIdsLength, float searchDistance, @Nullable Vector3fc lightVector, int targetQueueSize) {
        if (!this.shadow) {
            throw new IllegalStateException("startShadowGraphUpdate is for the shadow pass; use startGraphUpdate");
        }

        this.lattice.ensureWindowCovers(shadowViewport.getChunkCoord(), searchDistance);

        this.submitSearch(frame, regionIdsLength, targetQueueSize, shadowViewport, visitor ->
                this.lattice.findShadowVisible(visitor, shadowViewport, searchDistance, regionIdsLength, lightVector, frame));
    }

    // Submits a search and joins it at once when it runs inline.
    private void submitSearch(int frame, int regionIdsLength, int targetQueueSize, Viewport viewport,
                              Function<VisibleChunkCollector, SectionLattice.VisibilitySnapshot> search) {
        if (this.currentOcclusionFuture != null) {
            throw new IllegalStateException("Occlusion work in progress while trying to submit next task");
        }

        var visitor = new VisibleChunkCollector(this.lattice, frame, regionIdsLength, targetQueueSize, viewport.getBlockCoord());

        Supplier<VisibleChunkCollector> occlusionTask = () -> {
            this.pendingVisibilitySnapshot = search.apply(visitor);

            // Sorts the rebuild lists on the search thread
            visitor.finishRebuildLists();

            // Notifies the ticker from the search thread
            if (this.sectionTicker != null) {
                this.sectionTicker.onRenderListUpdated(visitor.getSortedRenderLists());
            }

            return visitor;
        };

        this.pendingLastUpdatedFrame = frame;
        this.currentOcclusionFuture = this.graph.submit(occlusionTask, this.async);

        if (!this.async) {
            this.finishPreviousGraphUpdate();
        }

        this.needsUpdate = false;
    }

    // Joins the search in progress and publishes its lists and runs the deferred updates.
    public void finishPreviousGraphUpdate() {
        if (currentOcclusionFuture != null) {
            try {
                VisibleChunkCollector visitor = currentOcclusionFuture.join();

                this.renderLists = visitor.createRenderLists();
                this.rebuildLists = visitor.getRebuildLists();

                // Publishes the visibility before the frame
                this.visibilitySnapshot = this.pendingVisibilitySnapshot;
                this.lastUpdatedFrame = this.pendingLastUpdatedFrame;

                this.debugStatistics = null;
            } finally {
                this.pendingVisibilitySnapshot = null;
                this.currentOcclusionFuture = null;
                this.graph.onSearchJoined();
            }
        }

        // Runs the updates that waited for the searches
        this.graph.runDeferredTasks();
    }

    // Joins the search in progress and forgets it even when it failed.
    public void destroy() {
        if (currentOcclusionFuture != null) {
            try {
                currentOcclusionFuture.join();
            } finally {
                currentOcclusionFuture = null;
                this.pendingVisibilitySnapshot = null;
                this.graph.onSearchJoined();
            }
        }
    }

    // Returns whether a section is visible in the lists in use.
    public boolean isSectionVisible(int x, int y, int z) {
        return this.visibilitySnapshot.isSectionVisible(x, y, z, this.lastUpdatedFrame);
    }

    // Ticks the visible sections.
    public void tickVisibleRenders() {
        if (this.sectionTicker != null) {
            this.sectionTicker.tickVisibleRenders();
        }
    }


    // Returns the debug counts and calculates them once per search.
    public RenderListDebugStatistics getDebugStatistics() {
        if (this.debugStatistics == null) {
            this.debugStatistics = computeDebugStatistics();
        }
        return this.debugStatistics;
    }

    // Returns the debug text of the ticker.
    public String getTickerDebugString() {
        if (this.sectionTicker == null) {
            return "";
        }
        return this.sectionTicker.getDebugString();
    }

    // Counts the sections per pass and per sorting level.
    private RenderListDebugStatistics computeDebugStatistics() {
        Object2IntOpenHashMap<TerrainRenderPass> renderPassCounts = new Object2IntOpenHashMap<>();

        var iterator = renderLists.iterator();

        int[] sectionCounts = new int[SortState.DEBUG_NAMES.length];

        boolean isSorting = renderLists.getPasses().stream().anyMatch(TerrainRenderPass::isSorted);

        while (iterator.hasNext()) {
            var renderList = iterator.next();

            if (renderList.getSectionsWithGeometryCount() == 0) {
                continue;
            }

            var region = renderList.getRegion();

            for (TerrainRenderPass pass : region.getPasses()) {
                int numToAdd = 0;
                var storage = region.getStorage(pass);
                var iter = Objects.requireNonNull(renderList.sectionsWithGeometryIterator());

                while (iter.hasNext()) {
                    int sectionIndex = iter.nextByteAsInt();

                    if (storage.getSliceMask(sectionIndex) != 0) {
                        numToAdd++;
                    }
                }

                if (numToAdd > 0) {
                    renderPassCounts.addTo(pass, numToAdd);
                }
            }

            if (isSorting) {
                var iter = Objects.requireNonNull(renderList.sectionsWithGeometryIterator());

                while (iter.hasNext()) {
                    int sectionIndex = iter.nextByteAsInt();
                    var section = region.getSection(sectionIndex);

                    // Skips sections without translucent data
                    if(section == null || section.getHighestSortingIndex() == RenderSection.NO_TRANSLUCENT_GEOMETRY) {
                        continue;
                    }

                    sectionCounts[section.getHighestSortingIndex()]++;
                }
            }
        }

        return new RenderListDebugStatistics(renderPassCounts, sectionCounts);
    }
}
