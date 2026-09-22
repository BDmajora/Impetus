package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import com.bdmajora.impetus.engine.impl.render.chunk.async.ChunkCullTask;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.GraphDirection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionNode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RenderListManager {
    @Getter
    @NotNull
    private SortedRenderLists renderLists;
    @Getter
    @NotNull
    private ChunkRebuildLists rebuildLists;

    private final OcclusionCuller occlusionCuller;

    private final Long2ReferenceMap<OcclusionNode> occlusionNodes = new Long2ReferenceOpenHashMap<>();
    private final SectionTree sectionTree = new SectionTree();

    // Non-null while an async graph search runs; structural mutations to occlusionNodes are forbidden and visibilityData updates are deferred to updateTasks while set
    private CompletableFuture<VisibleChunkCollector> currentOcclusionFuture;

    @Getter
    @Setter
    private boolean needsUpdate = true;

    @Getter
    private int lastUpdatedFrame;

    private int pendingLastUpdatedFrame;

    // Tasks deferred by submitUpdateTask() during an async search, drained on the render thread in finishPreviousGraphUpdate() after join() establishes happens-before
    private final ArrayDeque<Runnable> updateTasks = new ArrayDeque<>();

    private final ExecutorService asyncGraphExecutor;

    @Nullable
    private final SectionTicker sectionTicker;

    // Per-pass and per-sort-type counts for the debug screen
    public record RenderListDebugStatistics(Object2IntOpenHashMap<TerrainRenderPass> renderPassCounts, int[] sortingSectionCounts) {
        // Sort counts formatted
        public String getSortingString() {
            StringBuilder sb = new StringBuilder();

            sb.append("Sorting: ");
            TranslucentQuadAnalyzer.Level[] values = TranslucentQuadAnalyzer.Level.VALUES;
            for (int i = 0; i < values.length; i++) {
                TranslucentQuadAnalyzer.Level level = values[i];
                sb.append(level.name());
                sb.append('=');
                sb.append(sortingSectionCounts[level.ordinal()]);
                if((i + 1) < values.length) {
                    sb.append(", ");
                }
            }

            return sb.toString();
        }
    }

    private RenderListDebugStatistics debugStatistics;

    public RenderListManager(int minSectionY, int maxSectionY, boolean useAsyncGraphSearch, @Nullable SectionTicker sectionTicker) {
        this.sectionTicker = sectionTicker;

        if (useAsyncGraphSearch) {
            this.asyncGraphExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("Impetus chunk graph search thread");
                thread.setDaemon(true);
                return thread;
            });
        } else {
            this.asyncGraphExecutor = null;
        }
        this.occlusionCuller = new OcclusionCuller(this.occlusionNodes, this.sectionTree, minSectionY, maxSectionY);
        this.renderLists = SortedRenderLists.empty();
        this.rebuildLists = ChunkRebuildLists.EMPTY;
    }

    // Kicks off the occlusion walk, async when enabled
    public void startGraphUpdate(Viewport viewport, int frame, int regionIdsLength, float searchDistance, boolean useOcclusionCulling, int targetQueueSize) {
        if (this.currentOcclusionFuture != null) {
            throw new IllegalStateException("Occlusion work in progress while trying to submit next task");
        }

        var visitor = new VisibleChunkCollector(frame, regionIdsLength, targetQueueSize);

        var occlusionTask = new ChunkCullTask(this.occlusionCuller, visitor, viewport, searchDistance,
                useOcclusionCulling, frame, this.sectionTicker);

        this.pendingLastUpdatedFrame = frame;

        if (this.asyncGraphExecutor != null) {
            this.currentOcclusionFuture = CompletableFuture.supplyAsync(occlusionTask, this.asyncGraphExecutor);
        } else {
            this.currentOcclusionFuture = CompletableFuture.completedFuture(occlusionTask.get());
            this.finishPreviousGraphUpdate();
        }

        this.needsUpdate = false;
    }

    // Collects the walk's result into the current render lists
    public void finishPreviousGraphUpdate() {
        if (currentOcclusionFuture != null) {
            VisibleChunkCollector visitor = currentOcclusionFuture.join();

            this.renderLists = visitor.createRenderLists();
            this.rebuildLists = visitor.getRebuildLists();

            this.currentOcclusionFuture = null;
            this.lastUpdatedFrame = this.pendingLastUpdatedFrame;

            this.debugStatistics = null;
        }

        // Run tasks deferred during the async search; the join() above establishes happens-before for the async thread's OcclusionNode writes
        Runnable task;

        while ((task = updateTasks.poll()) != null) {
            task.run();
        }
    }

    // Stops the async walker
    public void destroy() {
        if (currentOcclusionFuture != null) {
            currentOcclusionFuture.join();
            currentOcclusionFuture = null;
        }

        if (asyncGraphExecutor != null) {
            asyncGraphExecutor.shutdown();

            try {
                if (!asyncGraphExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new InterruptedException();
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Async graph executor has somehow not shut down");
            }
        }
    }

    // Node by section coordinates, or null
    private OcclusionNode getOcclusionNode(int x, int y, int z) {
        return this.occlusionNodes.get(PositionUtil.packSection(x, y, z));
    }

    // Links a new node to its six neighbours
    private void connectNeighborNodes(OcclusionNode render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            OcclusionNode adj = this.getOcclusionNode(render.getChunkX() + GraphDirection.x(direction),
                    render.getChunkY() + GraphDirection.y(direction),
                    render.getChunkZ() + GraphDirection.z(direction));

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), render);
                render.setAdjacentNode(direction, adj);
            }
        }
    }

    // Unlinks a removed node
    private void disconnectNeighborNodes(OcclusionNode render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            OcclusionNode adj = render.getAdjacent(direction);

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), null);
                render.setAdjacentNode(direction, null);
            }
        }
    }

    // Structural mutations to occlusionNodes are unsafe while the async thread holds the map via OcclusionCuller
    private void assertOcclusionNotRunning() {
        if (this.currentOcclusionFuture != null) {
            throw new IllegalStateException("Attempted to update occlusion graph during occlusion!");
        }
    }

    // Adds a section to the graph
    public void attachRenderSection(RenderSection section) {
        this.assertOcclusionNotRunning();

        var key = section.positionAsLong();

        OcclusionNode occlusionNode = this.occlusionNodes.get(key);

        if (occlusionNode != null) {
            throw new IllegalStateException("Occlusion node already exists for section " + section);
        }

        var node = new OcclusionNode(section);
        this.occlusionNodes.put(key, node);
        this.sectionTree.add(node);
        this.connectNeighborNodes(node);
        this.needsUpdate = true;
    }

    // Removes a section from the graph
    public void detachRenderSection(RenderSection section) {
        this.assertOcclusionNotRunning();

        var key = section.positionAsLong();

        OcclusionNode occlusionNode = this.occlusionNodes.remove(key);

        if (occlusionNode == null) {
            throw new IllegalStateException("Occlusion node does not exist for section " + section);
        }

        this.disconnectNeighborNodes(occlusionNode);
        this.sectionTree.remove(occlusionNode);
        this.needsUpdate = true;
    }

    // Runs immediately if no async search is active, otherwise defers to finishPreviousGraphUpdate() to avoid concurrent OcclusionNode writes
    private void submitUpdateTask(Runnable runnable) {
        if (this.currentOcclusionFuture == null) {
            runnable.run();
        } else {
            this.updateTasks.add(runnable);
        }
    }

    // Installs a section's face-to-face visibility after a build
    public void updateVisibilityData(int x, int y, int z, long visibilityData) {
        this.submitUpdateTask(() -> {
            var node = this.getOcclusionNode(x, y, z);
            if (node != null) {
                node.setVisibilityData(visibilityData);
                this.needsUpdate = true;
            }
        });
    }

    // Whether the last walk reached the section
    public boolean isSectionVisible(int x, int y, int z) {
        OcclusionNode render = this.getOcclusionNode(x, y, z);

        if (render == null) {
            return false;
        }

        // lastUpdatedFrame only advances in finishPreviousGraphUpdate(), so mid-search this reflects the previous committed frame: a section may appear visible slightly early but never invisible too early
        return render.getLastVisibleFrame() >= this.lastUpdatedFrame;
    }

    // Advances sprite animation for visible sections
    public void tickVisibleRenders() {
        if (this.sectionTicker != null) {
            this.sectionTicker.tickVisibleRenders();
        }
    }

    // Computed lazily per frame
    public RenderListDebugStatistics getDebugStatistics() {
        if (this.debugStatistics == null) {
            this.debugStatistics = computeDebugStatistics();
        }
        return this.debugStatistics;
    }

    // From the section ticker
    public String getTickerDebugString() {
        if (this.sectionTicker == null) {
            return "";
        }
        return this.sectionTicker.getDebugString();
    }

    // Walks the render lists once
    private RenderListDebugStatistics computeDebugStatistics() {
        Object2IntOpenHashMap<TerrainRenderPass> renderPassCounts = new Object2IntOpenHashMap<>();

        var iterator = renderLists.iterator();

        int[] sectionCounts = new int[TranslucentQuadAnalyzer.Level.VALUES.length];

        boolean isSorting = renderLists.hasSortedPass();

        while (iterator.hasNext()) {
            var renderList = iterator.next();

            if (renderList.getSectionsWithGeometryCount() == 0) {
                continue;
            }

            var region = renderList.getRegion();

            for (TerrainRenderPass pass : region.getPasses()) {
                int numToAdd = 0;
                var storage = region.getStorage(pass);
                var iter = Objects.requireNonNull(renderList.sectionsWithGeometryIterator(false));

                while (iter.hasNext()) {
                    int sectionIndex = iter.nextByteAsInt();
                    var pMeshData = storage.getDataPointer(sectionIndex);

                    if (SectionRenderDataUnsafe.getSliceMask(pMeshData) != 0) {
                        numToAdd++;
                    }
                }

                if (numToAdd > 0) {
                    renderPassCounts.addTo(pass, numToAdd);
                }
            }

            if (isSorting) {
                var iter = Objects.requireNonNull(renderList.sectionsWithGeometryIterator(false));

                while (iter.hasNext()) {
                    int sectionIndex = iter.nextByteAsInt();
                    var section = region.getSection(sectionIndex);

                    // Do not count sections without translucent data
                    if(section == null || section.getTranslucencySortStates().isEmpty()) {
                        continue;
                    }

                    sectionCounts[section.getHighestSortingLevel().ordinal()]++;
                }
            }
        }

        return new RenderListDebugStatistics(renderPassCounts, sectionCounts);
    }
}
