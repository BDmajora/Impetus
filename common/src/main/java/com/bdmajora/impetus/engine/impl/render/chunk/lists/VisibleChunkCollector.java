package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongHeaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.joml.Vector3ic;

// Visitor that collects the visible sections into render lists and the sections to rebuild into queues.
public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    // Render lists in draw order.
    @Getter(AccessLevel.PACKAGE)
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    // Rebuild queues by update type.
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;
    // Sections dropped from each queue by update type.
    private final int[] rebuildQueueOverflowCounts;
    // Render list of each region.
    private final ChunkRenderList[] renderListsByRegion;

    // Candidates for rebuilding by update type.
    private final LongArrayList[] rebuildCandidates;

    // Frame of the search.
    private final int frame;

    // Number of first builds queued per frame.
    private final int targetQueueSize;

    // Lattice of the sections.
    private final SectionLattice lattice;

    // Camera position in blocks.
    private final int cameraX, cameraY, cameraZ;

    // Dropped candidate state of the queues.
    private boolean hasAdditionalUpdates;
    // Finished state of the queues.
    private boolean rebuildListsFinished;

    // Creates a collector.
    public VisibleChunkCollector(SectionLattice lattice, int frame, int regionIdsLength, int targetQueueSize, Vector3ic cameraBlockPos) {
        this.lattice = lattice;
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);
        this.rebuildQueueOverflowCounts = new int[ChunkUpdateType.VALUES.length];
        this.rebuildCandidates = new LongArrayList[ChunkUpdateType.VALUES.length];
        this.targetQueueSize = targetQueueSize;
        this.renderListsByRegion = new ChunkRenderList[regionIdsLength];
        this.cameraX = cameraBlockPos.x();
        this.cameraY = cameraBlockPos.y();
        this.cameraZ = cameraBlockPos.z();

        for (var type : ChunkUpdateType.VALUES) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    // Creates and registers the render list of a region.
    private ChunkRenderList createRenderList(RenderRegion region) {
        ChunkRenderList renderList = new ChunkRenderList(region);
        this.sortedRenderLists.add(renderList);
        this.renderListsByRegion[region.getId()] = renderList;
        return renderList;
    }

    // Adds a visited section to its render list and the rebuild candidates.
    @Override
    public void visit(int latticeIndex, int regionId, int sectionIndex, int chunkX, int chunkY, int chunkZ, int meta, boolean visible) {
        // Creates the list of the region even for an empty section.
        ChunkRenderList renderList = this.renderListsByRegion[regionId];

        if (renderList == null) {
            renderList = this.createRenderList(this.lattice.sectionAt(latticeIndex).getRegion());
        }

        if (visible) {
            int visualsFlags = PackedSectionMetadata.getCompactVisualsFlags(meta);
            if (visualsFlags != 0) {
                renderList.add(sectionIndex, visualsFlags);
            }

            ChunkUpdateType type = PackedSectionMetadata.getCompactPendingUpdate(meta);

            // Skips sections with a build in flight
            if (type != null && !PackedSectionMetadata.isCompactBuildInFlight(meta)) {
                this.addRebuildCandidate(latticeIndex, chunkX, chunkY, chunkZ, type);
            }
        }
    }

    // Adds a section to the candidates ordered by distance to the camera.
    private void addRebuildCandidate(int latticeIndex, int chunkX, int chunkY, int chunkZ, ChunkUpdateType type) {
        long dx = ((long) chunkX << 4) + 8 - this.cameraX;
        long dy = ((long) chunkY << 4) + 8 - this.cameraY;
        long dz = ((long) chunkZ << 4) + 8 - this.cameraZ;
        long distanceSq = Math.min(dx * dx + dy * dy + dz * dz, Integer.MAX_VALUE);

        var candidates = this.rebuildCandidates[type.ordinal()];

        if (candidates == null) {
            candidates = this.rebuildCandidates[type.ordinal()] = new LongArrayList();
        }

        candidates.add((distanceSq << 32) | (latticeIndex & 0xFFFFFFFFL));
    }

    // Fills the rebuild queues from the nearest candidates and rejects a second call.
    public void finishRebuildLists() {
        if (this.rebuildListsFinished) {
            throw new IllegalStateException("Rebuild lists already finished");
        }

        this.rebuildListsFinished = true;

        var types = ChunkUpdateType.VALUES;

        for (int i = 0; i < types.length; i++) {
            var candidates = this.rebuildCandidates[i];

            if (candidates == null) {
                continue;
            }

            var type = types[i];
            long[] keys = candidates.elements();
            int count = candidates.size();

            // Limits only the first builds
            int limit = type == ChunkUpdateType.INITIAL_BUILD ? Math.max(0, Math.min(count, this.targetQueueSize)) : count;

            // Selects with a heap when at most a third is needed
            if ((long) limit * 3 < count) {
                keys = selectSmallest(keys, count, limit);
            } else {
                Arrays.sort(keys, 0, count);
            }

            var queue = this.sortedRebuildLists.get(type);

            for (int j = 0; j < limit; j++) {
                queue.add(this.lattice.sectionAt((int) keys[j]));
            }

            if (limit < count) {
                this.rebuildQueueOverflowCounts[i] += count - limit;
                this.hasAdditionalUpdates = true;
            }

            this.rebuildCandidates[i] = null;
        }
    }

    // Returns the smallest keys in ascending order.
    private static long[] selectSmallest(long[] keys, int count, int k) {
        if (k <= 0) {
            return new long[0];
        }

        var comparator = LongComparators.OPPOSITE_COMPARATOR;

        long[] heap = Arrays.copyOf(keys, k);
        LongHeaps.makeHeap(heap, k, comparator);

        for (int i = k; i < count; i++) {
            long key = keys[i];

            if (key < heap[0]) {
                heap[0] = key;
                LongHeaps.downHeap(heap, k, 0, comparator);
            }
        }

        Arrays.sort(heap);

        return heap;
    }

    // Orders the region and geometry lists independently of the order of the occlusion walk.
    void sortGeometry() {
        int cameraSectionX = this.cameraX >> 4;
        int cameraSectionY = this.cameraY >> 4;
        int cameraSectionZ = this.cameraZ >> 4;
        int regionX = cameraSectionX >> RenderRegion.REGION_WIDTH_SH;
        int regionY = cameraSectionY >> RenderRegion.REGION_HEIGHT_SH;
        int regionZ = cameraSectionZ >> RenderRegion.REGION_LENGTH_SH;
        this.sortedRenderLists.sort(Comparator.comparingInt(list -> {
            var region = list.getRegion();
            return Math.abs((region.getChunkX() >> RenderRegion.REGION_WIDTH_SH) - regionX)
                    + Math.abs((region.getChunkY() >> RenderRegion.REGION_HEIGHT_SH) - regionY)
                    + Math.abs((region.getChunkZ() >> RenderRegion.REGION_LENGTH_SH) - regionZ);
        }));

        int[] keys = new int[RenderRegion.REGION_SIZE];
        for (var list : this.sortedRenderLists) {
            list.sortGeometry(cameraSectionX, cameraSectionY, cameraSectionZ, keys);
        }
    }

    // Creates the sorted render lists.
    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    // Returns the rebuild queues and rejects a call before they were finished.
    public ChunkRebuildLists getRebuildLists() {
        if (!this.rebuildListsFinished) {
            throw new IllegalStateException("finishRebuildLists() must be called on the search thread first");
        }

        EnumMap<ChunkUpdateType, Integer> overflowCounts = new EnumMap<>(ChunkUpdateType.class);
        if (this.hasAdditionalUpdates) {
            var values = ChunkUpdateType.VALUES;
            for (int i = 0; i < values.length; i++) {
                if (this.rebuildQueueOverflowCounts[i] != 0) {
                    overflowCounts.put(values[i], this.rebuildQueueOverflowCounts[i]);
                }
            }
        }
        return new ChunkRebuildLists(this.sortedRebuildLists, this.hasAdditionalUpdates, overflowCounts);
    }
}
