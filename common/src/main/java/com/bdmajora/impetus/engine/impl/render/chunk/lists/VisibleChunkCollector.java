package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Queue;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionNode;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    @Getter(AccessLevel.PACKAGE)
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;
    private final int[] rebuildQueueOverflowCounts;
    private final ChunkRenderList[] renderListsByRegion;

    private final int frame;

    private final int targetQueueSize;

    private boolean hasAdditionalUpdates;

    public VisibleChunkCollector(int frame, int regionIdsLength, int targetQueueSize) {
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);
        this.rebuildQueueOverflowCounts = new int[ChunkUpdateType.VALUES.length];
        this.targetQueueSize = targetQueueSize;
        this.renderListsByRegion = new ChunkRenderList[regionIdsLength];

        for (var type : ChunkUpdateType.VALUES) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    // One list per region, created on first visible section
    private ChunkRenderList createRenderList(RenderRegion region) {
        ChunkRenderList renderList = new ChunkRenderList(region);
        this.sortedRenderLists.add(renderList);
        this.renderListsByRegion[region.getId()] = renderList;
        return renderList;
    }

    // Called by the walk for every reached section
    @Override
    public void visit(OcclusionNode node, boolean visible) {
        var section = node.getRenderSection();

        // Even a section without render objects must initialise its render list and enter the sorted queue, to keep draw call order correct
        int regionId = node.getRenderRegionId();
        ChunkRenderList renderList = this.renderListsByRegion[regionId];

        if (renderList == null) {
            renderList = this.createRenderList(section.getRegion());
        }

        if (visible) {
            if (section.hasAnythingToRender()) {
                renderList.add(section);
            }

            this.addToRebuildLists(section);
        }
    }

    // Queues sections needing a rebuild, by importance
    private void addToRebuildLists(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        // Skip sections with an in-flight build; advisory only, since submitRebuildTasks() re-validates getPendingUpdate() so a stale null token cannot double-submit
        if (type != null && section.getBuildCancellationToken() == null) {
            Queue<RenderSection> queue = this.sortedRebuildLists.get(type);

            // Do not limit the queue size for rebuilds
            if (type != ChunkUpdateType.INITIAL_BUILD || queue.size() < this.targetQueueSize) {
                queue.add(section);
            } else {
                this.rebuildQueueOverflowCounts[type.ordinal()]++;
                this.hasAdditionalUpdates = true;
            }
        }
    }

    // Finalises the lists in walk order
    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    // The lists so far
    public List<ChunkRenderList> getCollectedRenderLists() {
        return this.sortedRenderLists;
    }

    // The rebuild queues
    public ChunkRebuildLists getRebuildLists() {
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
