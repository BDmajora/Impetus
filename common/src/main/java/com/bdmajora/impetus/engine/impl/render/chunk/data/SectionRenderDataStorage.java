package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.CachedBatch;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

// Mesh data of the sections of one region and pass in native memory.
public class SectionRenderDataStorage {
    // Vertex allocations by section.
    private final GlBufferSegment[] allocations = new GlBufferSegment[RenderRegion.REGION_SIZE];
    // Index allocations by section.
    private final GlBufferSegment[] indexAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];

    // Address of the mesh data.
    private final long pMeshDataArray;
    // Primitive type of the pass.
    private final ChunkPrimitiveType primitiveType;
    // Layout of the mesh data.
    private final SectionRenderDataUnsafe.Strategy storageStrategy;

    // Vertices in one arena element.
    private final int verticesPerArenaElement;

    // Number of sections with a vertex allocation.
    private int numAllocations;
    // Whether the storage was deleted.
    private boolean deleted;

    // Settings that decide which batch is cached.
    public record BatchCacheParams(boolean useBlockFaceCulling) {}

    // Settings of the first cached batch.
    private BatchCacheParams firstBatchParams;
    // First cached batch.
    private CachedBatch firstCachedBatch;
    // Settings of the second cached batch.
    private BatchCacheParams secondBatchParams;
    // Second cached batch.
    private CachedBatch secondCachedBatch;

    // Creates a storage and rejects a null primitive type or a vertex count that is not positive.
    public SectionRenderDataStorage(ChunkPrimitiveType primitiveType, boolean sorted, int verticesPerArenaElement) {
        java.util.Objects.requireNonNull(primitiveType, "primitiveType");
        if (verticesPerArenaElement <= 0) {
            throw new IllegalArgumentException("verticesPerArenaElement must be positive");
        }
        this.storageStrategy = sorted ? SectionRenderDataUnsafe.Strategy.FULL : SectionRenderDataUnsafe.Strategy.COMPACT;
        this.pMeshDataArray = this.storageStrategy.allocateHeap();
        if (this.pMeshDataArray == 0) {
            throw new OutOfMemoryError("Failed to allocate mesh data array");
        }
        this.primitiveType = primitiveType;
        this.verticesPerArenaElement = verticesPerArenaElement;
    }

    // Returns whether no section has a mesh.
    public boolean isEmpty() {
        return this.numAllocations == 0;
    }

    // Returns the primitive type.
    public ChunkPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    // Replaces the mesh of a section and deletes its old allocations and rejects a null allocation.
    public void setMeshes(int localSectionIndex,
                          GlBufferSegment allocation, @Nullable GlBufferSegment indexAllocation, Map<ModelQuadFacing, VertexRange> ranges) {
        this.checkLive();
        java.util.Objects.checkIndex(localSectionIndex, RenderRegion.REGION_SIZE);
        java.util.Objects.requireNonNull(allocation, "allocation");
        java.util.Objects.requireNonNull(ranges, "ranges");
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;
            this.numAllocations--;
        }

        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }

        this.allocations[localSectionIndex] = allocation;
        this.indexAllocations[localSectionIndex] = indexAllocation;
        this.numAllocations++;

        int vertexOffset = Math.multiplyExact(allocation.getOffset(), this.verticesPerArenaElement);
        int indexOffset = indexAllocation != null ? Math.multiplyExact(indexAllocation.getOffset(), 4) : 0;

        this.storageStrategy.writeMeshesAndSliceMask(this.pMeshDataArray, localSectionIndex, vertexOffset, indexOffset, ranges, this.primitiveType);

        this.invalidateCachedBatches();
    }

    // Deletes the mesh of a section.
    public void removeMeshes(int localSectionIndex) {
        this.checkLive();
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;

            this.storageStrategy.clearRow(this.pMeshDataArray, localSectionIndex);

            this.numAllocations--;

            this.invalidateCachedBatches();
        }

        removeIndexBuffer(localSectionIndex);
    }

    // Deletes the index allocation of a section.
    public void removeIndexBuffer(int localSectionIndex) {
        this.checkLive();
        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;

            this.invalidateCachedBatches();
        }
    }

    // Replaces the index allocation of a section and keeps the old state when the layout has no index offsets.
    public void replaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation) {
        this.checkLive();
        java.util.Objects.checkIndex(localSectionIndex, RenderRegion.REGION_SIZE);

        int indexOffset = indexAllocation != null ? Math.multiplyExact(indexAllocation.getOffset(), 4) : 0;
        var pMeshData = this.getDataPointer(localSectionIndex);

        this.storageStrategy.writeIndexOffsets(pMeshData, indexOffset, this.primitiveType);

        removeIndexBuffer(localSectionIndex);

        this.indexAllocations[localSectionIndex] = indexAllocation;

        this.invalidateCachedBatches();
    }

    // Rewrites every row after the buffers moved.
    public void onBufferResized() {
        this.checkLive();
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }

        this.invalidateCachedBatches();
    }

    // Moves the row of a section to its current allocations.
    private void updateMeshes(int sectionIndex) {
        var allocation = this.allocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        var indexAllocation = this.indexAllocations[sectionIndex];

        var vertexOffset = Math.multiplyExact(allocation.getOffset(), this.verticesPerArenaElement);
        var indexOffset = indexAllocation != null ? Math.multiplyExact(indexAllocation.getOffset(), 4) : 0;

        var data = this.getDataPointer(sectionIndex);

        this.storageStrategy.rebase(data, vertexOffset, indexOffset, this.primitiveType);
    }

    // Returns the address of the row of a section.
    public long getDataPointer(int sectionIndex) {
        this.checkLive();
        return this.storageStrategy.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    // Returns the address of the first row.
    public long getRowBasePointer() {
        this.checkLive();
        return this.storageStrategy.getRowBasePointer(this.pMeshDataArray);
    }

    // Returns the slice mask of a section.
    public int getSliceMask(int sectionIndex) {
        this.checkLive();
        return this.storageStrategy.getSliceMask(this.pMeshDataArray, sectionIndex);
    }

    // Returns the cached batch for the settings or null.
    public @Nullable CachedBatch getCachedMultiDrawBatch(BatchCacheParams params) {
        java.util.Objects.requireNonNull(params, "params");
        if (params.equals(firstBatchParams)) {
            return firstCachedBatch;
        } else if (params.equals(secondBatchParams)) {
            return secondCachedBatch;
        } else {
            return null;
        }
    }

    // Caches a batch for the settings and deletes the batch it replaces.
    public void storeCachedMultiDrawBatch(BatchCacheParams params, CachedBatch batch) {
        java.util.Objects.requireNonNull(params, "params");
        java.util.Objects.requireNonNull(batch, "batch");
        if (batch == this.firstCachedBatch || batch == this.secondCachedBatch) {
            return;
        }
        if (params.equals(firstBatchParams) || firstBatchParams == null) {
            if (firstCachedBatch != null) {
                firstCachedBatch.delete();
            }
            firstBatchParams = params;
            firstCachedBatch = batch;
        } else {
            if (secondCachedBatch != null) {
                secondCachedBatch.delete();
            }
            secondBatchParams = params;
            secondCachedBatch = batch;
        }
    }

    // Deletes the cached batches.
    public void invalidateCachedBatches() {
        if (firstCachedBatch != null) {
            firstCachedBatch.delete();
            firstCachedBatch = null;
        }
        if (secondCachedBatch != null) {
            secondCachedBatch.delete();
            secondCachedBatch = null;
        }
        firstBatchParams = null;
        secondBatchParams = null;
    }

    // Deletes every allocation and frees the native memory once.
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;

        try {
            this.invalidateCachedBatches();

            for (var allocation : this.allocations) {
                if (allocation != null) {
                    allocation.delete();
                }
            }

            for (var allocation : this.indexAllocations) {
                if (allocation != null) {
                    allocation.delete();
                }
            }
        } finally {
            Arrays.fill(this.allocations, null);
            Arrays.fill(this.indexAllocations, null);

            this.storageStrategy.freeHeap(this.pMeshDataArray);

            this.numAllocations = 0;
        }
    }

    // Rejects use after delete.
    private void checkLive() {
        if (this.deleted) {
            throw new IllegalStateException("Storage was deleted");
        }
    }
}
