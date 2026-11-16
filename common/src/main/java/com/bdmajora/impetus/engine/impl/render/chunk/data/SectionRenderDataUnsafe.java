package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

import java.util.Map;
import java.util.Objects;
import org.lwjgl.system.MemoryUtil;

// Reads and writes the draw data of sections in native memory.
public class SectionRenderDataUnsafe {
    // Number of facings per section.
    private static final int NUM_FACINGS = ModelQuadFacing.COUNT;

    // Returns the number of indices that draw a span of vertices.
    public static int elementsForVertices(int vertexSpan, ChunkPrimitiveType primitiveType) {
        return (vertexSpan / primitiveType.getVerticesPerPrimitive()) * primitiveType.getIndexBufferElementsPerPrimitive();
    }

    // Returns the number of vertices that a number of indices draws.
    public static int verticesForElements(int elementCount, ChunkPrimitiveType primitiveType) {
        return (elementCount / primitiveType.getIndexBufferElementsPerPrimitive()) * primitiveType.getVerticesPerPrimitive();
    }

    // Memory layouts of the draw data of one section.
    public enum Strategy {
        // Layout with an offset and count and index offset for every facing.
        FULL {
            // Bytes before the facing data.
            private static final long OFFSET_SLICE_RANGES = 8;
            // Bytes of the data of one facing.
            private static final long DATA_PER_FACING_SIZE = 12;

            // Returns the address of the vertex offset of a facing.
            private static long pVertexOffset(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 0L;
            }

            // Returns the address of the element count of a facing.
            private static long pElementCount(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 4L;
            }

            // Returns the address of the index offset of a facing.
            private static long pIndexOffset(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 8L;
            }

            // Returns the bytes of one row.
            @Override
            public long getStride() {
                return OFFSET_SLICE_RANGES + (DATA_PER_FACING_SIZE * NUM_FACINGS);
            }

            // Returns the first vertex of a facing.
            @Override
            public int getVertexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pVertexOffset(ptr, facing));
            }

            // Returns the number of indices of a facing.
            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pElementCount(ptr, facing));
            }

            // Returns the byte offset of the indices of a facing.
            @Override
            public int getIndexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pIndexOffset(ptr, facing));
            }

            // Returns the end of the vertices of a facing.
            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pVertexOffset(ptr, lastFacing))
                        + verticesForElements(MemoryUtil.memGetInt(pElementCount(ptr, lastFacing)), primitiveType);
            }

            // Returns the slice mask of a section.
            @Override
            public int getSliceMask(long heap, int index) {
                return MemoryUtil.memGetInt(this.heapPointer(heap, index));
            }

            // Stores the slice mask of a section.
            @Override
            protected void setSliceMask(long heap, int index, int value) {
                MemoryUtil.memPutInt(this.heapPointer(heap, index), value);
            }

            // Writes a row from the ranges of a mesh and returns its slice mask.
            @Override
            protected int writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                      Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
                int sliceMask = 0;

                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    int vertexCount = vertexCountOf(ranges, facing);
                    int elementCount = elementsForVertices(vertexCount, primitiveType);

                    MemoryUtil.memPutInt(pVertexOffset(ptr, facing), vertexOffset);
                    MemoryUtil.memPutInt(pElementCount(ptr, facing), elementCount);
                    MemoryUtil.memPutInt(pIndexOffset(ptr, facing), indexOffset);

                    if (vertexCount > 0) {
                        sliceMask |= 1 << facing;
                    }

                    vertexOffset = Math.addExact(vertexOffset, vertexCount);
                    indexOffset = Math.addExact(indexOffset, Math.multiplyExact(elementCount, 4));
                }

                return sliceMask;
            }

            // Rewrites the index offsets of a row.
            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    MemoryUtil.memPutInt(pIndexOffset(ptr, facing), indexOffset);
                    indexOffset = Math.addExact(indexOffset, Math.multiplyExact(MemoryUtil.memGetInt(pElementCount(ptr, facing)), 4));
                }
            }

            // Moves a row to new vertex and index offsets.
            @Override
            public void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    MemoryUtil.memPutInt(pVertexOffset(ptr, facing), vertexOffset);
                    MemoryUtil.memPutInt(pIndexOffset(ptr, facing), indexOffset);

                    int elementCount = MemoryUtil.memGetInt(pElementCount(ptr, facing));
                    vertexOffset = Math.addExact(vertexOffset, verticesForElements(elementCount, primitiveType));
                    indexOffset = Math.addExact(indexOffset, Math.multiplyExact(elementCount, 4));
                }
            }
        },
        // Layout for unsorted passes that stores one vertex post per facing boundary.
        COMPACT {
            // Bytes of the slice masks before the rows.
            private static final long DATA_OFFSET = RenderRegion.REGION_SIZE;
            // Number of posts of a row.
            private static final int NUM_POSTS = NUM_FACINGS + 1;

            // Returns the address of a post.
            private static long pPost(long ptr, int post) {
                return ptr + ((long) post << 2);
            }

            // Returns the bytes of one row.
            @Override
            public long getStride() {
                return Integer.BYTES * (long) NUM_POSTS;
            }

            @Override
            // Returns the bytes before the rows.
        protected long getHeaderSize() {
                return DATA_OFFSET;
            }

            // Returns the slice mask of a section.
            @Override
            public int getSliceMask(long heap, int index) {
                return MemoryUtil.memGetByte(heap + index) & 0xFF;
            }

            // Stores the slice mask of a section.
            @Override
            protected void setSliceMask(long heap, int index, int value) {
                MemoryUtil.memPutByte(heap + index, (byte) value);
            }

            // Returns the first vertex of a facing.
            @Override
            public int getVertexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pPost(ptr, facing));
            }

            // Returns the number of indices of a facing.
            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                int start = MemoryUtil.memGetInt(pPost(ptr, facing));
                int end = MemoryUtil.memGetInt(pPost(ptr, facing + 1));

                return elementsForVertices(end - start, primitiveType);
            }

            // Returns the byte offset of the indices of a facing.
            @Override
            public int getIndexOffset(long ptr, int facing) {
                // Unsorted passes always draw through the shared index buffer, which starts at pointer zero.
                return 0;
            }

            // Returns the end of the vertices of a facing.
            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pPost(ptr, lastFacing + 1));
            }

            // Writes a row from the ranges of a mesh and returns its slice mask.
            @Override
            protected int writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                      Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
                int sliceMask = 0;

                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    int vertexCount = vertexCountOf(ranges, facing);

                    MemoryUtil.memPutInt(pPost(ptr, facing), vertexOffset);

                    if (vertexCount > 0) {
                        sliceMask |= 1 << facing;
                    }

                    vertexOffset = Math.addExact(vertexOffset, vertexCount);
                }

                MemoryUtil.memPutInt(pPost(ptr, NUM_FACINGS), vertexOffset);

                return sliceMask;
            }

            // Rejects the rewrite because the row stores no index offsets.
            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                throw new UnsupportedOperationException("Compact rows draw from the shared index buffer and store no index offsets");
            }

            // Moves a row to new vertex and index offsets.
            @Override
            public void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType) {
                int delta = vertexOffset - MemoryUtil.memGetInt(pPost(ptr, 0));

                if (delta == 0) {
                    return;
                }

                for (int post = 0; post < NUM_POSTS; post++) {
                    long pPost = pPost(ptr, post);
                    MemoryUtil.memPutInt(pPost, MemoryUtil.memGetInt(pPost) + delta);
                }
            }

        };

        // Returns the bytes of one row.
        public abstract long getStride();

        // Returns the first vertex of a facing.
        public abstract int getVertexOffset(long ptr, int facing);

        // Returns the number of indices of a facing.
        public abstract int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType);

        // Returns the byte offset of the indices of a facing or zero for the shared index buffer.
        public abstract int getIndexOffset(long ptr, int facing);

        // Returns the end of the vertices of a facing.
        public abstract int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType);

        // Writes a row from the ranges of a mesh and returns its slice mask.
        protected abstract int writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                           Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType);

        // Rewrites the index offsets of a row and rejects layouts without index offsets.
        public abstract void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType);

        // Moves a row to new vertex and index offsets and keeps its sizes.
        public abstract void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType);

        // Returns the slice mask of a section.
        public abstract int getSliceMask(long heap, int index);

        // Stores the slice mask of a section.
        protected abstract void setSliceMask(long heap, int index, int value);

        protected long getHeaderSize() {
            return 0;
        }

        // Returns the address of the first row.
        public final long getRowBasePointer(long heap) {
            return heap + this.getHeaderSize();
        }

        // Allocates and clears the memory of a region and returns zero on failure.
        public final long allocateHeap() {
            long size = this.getHeaderSize() + (RenderRegion.REGION_SIZE * this.getStride());
            long pointer = MemoryUtil.nmemAlignedAlloc(64, size);

            if (pointer != 0) {
                MemoryUtil.memSet(pointer, 0, size);
            }

            return pointer;
        }

        // Frees the memory of a region.
        public final void freeHeap(long pointer) {
            MemoryUtil.nmemAlignedFree(pointer);
        }

        // Returns the address of a row.
        public final long heapPointer(long ptr, int index) {
            return this.getRowBasePointer(ptr) + (index * this.getStride());
        }

        // Writes the row and the slice mask of a section and rejects an index outside the region.
        public final void writeMeshesAndSliceMask(long heap, int index, int vertexOffset, int indexOffset,
                                                  Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
            Objects.checkIndex(index, RenderRegion.REGION_SIZE);
            int sliceMask = this.writeMeshes(this.heapPointer(heap, index), vertexOffset, indexOffset, ranges, primitiveType);

            this.setSliceMask(heap, index, sliceMask);
        }

        // Clears the row and the slice mask of a section and rejects an index outside the region.
        public final void clearRow(long heap, int index) {
            Objects.checkIndex(index, RenderRegion.REGION_SIZE);
            this.setSliceMask(heap, index, 0);
            MemoryUtil.memSet(this.heapPointer(heap, index), 0x0, this.getStride());
        }

        // Returns the vertex count of a facing.
        private static int vertexCountOf(Map<ModelQuadFacing, VertexRange> ranges, int facing) {
            VertexRange range = ranges.get(ModelQuadFacing.VALUES[facing]);

            return range != null ? range.vertexCount() : 0;
        }
    }
}
