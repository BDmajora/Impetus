package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

public class SectionRenderDataStorage {
    private final GlBufferSegment[] allocations = new GlBufferSegment[RenderRegion.REGION_SIZE];
    private final GlBufferSegment[] indexAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];

    private final long pMeshDataArray;
    private final ChunkPrimitiveType primitiveType;

    private int numAllocations;

    public SectionRenderDataStorage(ChunkPrimitiveType primitiveType) {
        this.pMeshDataArray = SectionRenderDataUnsafe.allocateHeap(RenderRegion.REGION_SIZE);
        if (this.pMeshDataArray == 0) {
            throw new OutOfMemoryError("Failed to allocate mesh data array");
        }
        this.primitiveType = primitiveType;
    }

    // No section in this region has meshes
    public boolean isEmpty() {
        return this.numAllocations == 0;
    }

    // Records a section's fresh allocations and per-facing element counts, then lays the offsets out from them
    public void setMeshes(int localSectionIndex,
                          GlBufferSegment allocation, @Nullable GlBufferSegment indexAllocation, Map<ModelQuadFacing, VertexRange> ranges) {
        this.removeMeshes(localSectionIndex);

        this.allocations[localSectionIndex] = allocation;
        this.indexAllocations[localSectionIndex] = indexAllocation;
        this.numAllocations++;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int sliceMask = 0;
        int elementsPerPrimitive = primitiveType.getIndexBufferElementsPerPrimitive();
        int verticesPerPrimitive = primitiveType.getVerticesPerPrimitive();

        for (int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; facingIndex++) {
            VertexRange vertexRange = ranges.get(ModelQuadFacing.VALUES[facingIndex]);
            int vertexCount = vertexRange != null ? vertexRange.vertexCount() : 0;

            SectionRenderDataUnsafe.setElementCount(pMeshData, facingIndex, (vertexCount / verticesPerPrimitive) * elementsPerPrimitive);

            if (vertexCount > 0) {
                sliceMask |= 1 << facingIndex;
            }
        }

        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);

        // Vertex counts are whole primitives, so the offsets fall out of the element counts exactly as after a resize
        this.updateMeshes(localSectionIndex);
    }

    // Frees a section's vertex allocation and clears its data
    public void removeMeshes(int localSectionIndex) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;

            SectionRenderDataUnsafe.clear(this.getDataPointer(localSectionIndex));

            this.numAllocations--;
        }

        removeIndexBuffer(localSectionIndex);
    }

    // Frees a section's index allocation
    public void removeIndexBuffer(int localSectionIndex) {
        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }
    }

    // Swaps in a re-sorted index buffer and re-lays the offsets
    public void replaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation) {
        removeIndexBuffer(localSectionIndex);

        this.indexAllocations[localSectionIndex] = indexAllocation;
        this.updateMeshes(localSectionIndex);
    }

    // Rewrites every offset after the arena compacted
    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }
    }

    // Writes a section's per-facing offsets and counts into the native data
    private void updateMeshes(int sectionIndex) {
        var allocation = this.allocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        var indexAllocation = this.indexAllocations[sectionIndex];

        var vertexOffset = allocation.getOffset();
        var indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        var data = this.getDataPointer(sectionIndex);

        int elementsPerPrimitive = primitiveType.getIndexBufferElementsPerPrimitive();
        int verticesPerPrimitive = primitiveType.getVerticesPerPrimitive();

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            SectionRenderDataUnsafe.setVertexOffset(data, facing, vertexOffset);
            SectionRenderDataUnsafe.setIndexOffset(data, facing, indexOffset);

            var indexCount = SectionRenderDataUnsafe.getElementCount(data, facing);
            vertexOffset += (indexCount / elementsPerPrimitive) * verticesPerPrimitive; // convert elements back into vertices
            indexOffset += indexCount * 4;
        }
    }

    // Native pointer to a section's draw data
    public long getDataPointer(int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    // Frees every allocation and the native block
    public void delete() {
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

        Arrays.fill(this.allocations, null);
        Arrays.fill(this.indexAllocations, null);

        SectionRenderDataUnsafe.freeHeap(this.pMeshDataArray);

        this.numAllocations = 0;
    }
}