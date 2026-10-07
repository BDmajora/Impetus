package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

import java.util.Map;
import org.lwjgl.system.MemoryUtil;


// Section draw data in raw native memory (the render path is memory-bound and HotSpot scatters objects): u64 slice_mask, then per facing u32 vertex_offset, u32 element_count, u32 index_offset
public class SectionRenderDataUnsafe {
    private static final int NUM_FACINGS = ModelQuadFacing.COUNT; // 6 directions + UNASSIGNED

    /**
     * {@return the number of index buffer elements needed to draw a span of {@code vertexSpan} vertices}
     */
    public static int elementsForVertices(int vertexSpan, ChunkPrimitiveType primitiveType) {
        return (vertexSpan / primitiveType.getVerticesPerPrimitive()) * primitiveType.getIndexBufferElementsPerPrimitive();
    }

    /**
     * The inverse of {@link #elementsForVertices}.
     */
    public static int verticesForElements(int elementCount, ChunkPrimitiveType primitiveType) {
        return (elementCount / primitiveType.getIndexBufferElementsPerPrimitive()) * primitiveType.getVerticesPerPrimitive();
    }

    /**
     * Memory layouts for a single section's mesh data. Which one a {@link SectionRenderDataStorage} uses is fixed for
     * its lifetime and determined by whether its pass is translucency-sorted.
     * <p>
     * The write side of this interface is expressed as whole operations rather than per-field setters, because the two
     * layouts do not store the same fields. {@link #COMPACT} derives element counts and has no index offsets at all.
     * Operations that a layout genuinely cannot express throw.
     */
    public enum Strategy {
        /**
         * The general layout. Stores, for each facing, an explicit vertex offset, element count and index offset.
         * <pre>
         * u64 slice_mask;
         * struct { u32 vertex_offset; u32 element_count; u32 index_offset; } facings[7];
         * </pre>
         * Required for sorted passes, which have per-facing index offsets into a per-section sorted index buffer.
         */
        FULL {
            private static final long OFFSET_SLICE_RANGES = 8;
            private static final long DATA_PER_FACING_SIZE = 12;

            private static long pVertexOffset(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 0L;
            }

            private static long pElementCount(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 4L;
            }

            private static long pIndexOffset(long ptr, int facing) {
                return ptr + OFFSET_SLICE_RANGES + (facing * DATA_PER_FACING_SIZE) + 8L;
            }

            @Override
            public long getStride() {
                return OFFSET_SLICE_RANGES + (DATA_PER_FACING_SIZE * NUM_FACINGS);
            }

            @Override
            public int getVertexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pVertexOffset(ptr, facing));
            }

            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pElementCount(ptr, facing));
            }

            @Override
            public int getIndexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pIndexOffset(ptr, facing));
            }

            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pVertexOffset(ptr, lastFacing))
                        + verticesForElements(MemoryUtil.memGetInt(pElementCount(ptr, lastFacing)), primitiveType);
            }

            @Override
            public int getSliceMask(long heap, int index) {
                return MemoryUtil.memGetInt(this.heapPointer(heap, index));
            }

            @Override
            protected void setSliceMask(long heap, int index, int value) {
                MemoryUtil.memPutInt(this.heapPointer(heap, index), value);
            }

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

                    vertexOffset += vertexCount;
                    indexOffset += elementCount * 4;
                }

                return sliceMask;
            }

            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    MemoryUtil.memPutInt(pIndexOffset(ptr, facing), indexOffset);
                    indexOffset += MemoryUtil.memGetInt(pElementCount(ptr, facing)) * 4;
                }
            }

            @Override
            public void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    MemoryUtil.memPutInt(pVertexOffset(ptr, facing), vertexOffset);
                    MemoryUtil.memPutInt(pIndexOffset(ptr, facing), indexOffset);

                    int elementCount = MemoryUtil.memGetInt(pElementCount(ptr, facing));
                    vertexOffset += verticesForElements(elementCount, primitiveType);
                    indexOffset += elementCount * 4;
                }
            }
        },
        /**
         * The "fence post" layout, valid only for unsorted passes.
         * <pre>
         * u32 posts[8];
         * </pre>
         * Slice masks are stored separately in one byte per section, followed by the aligned post rows.
         * It drops index offsets entirely (unnecessary for unsorted passes) and compresses vertex info by exploiting
         * the fact that facing vertex ranges are always contiguous in memory (in facing order), with empty facings having
         * zero-length spans.
         */
        COMPACT {
            private static final long DATA_OFFSET = RenderRegion.REGION_SIZE;
            private static final int NUM_POSTS = NUM_FACINGS + 1;

            private static long pPost(long ptr, int post) {
                return ptr + ((long) post << 2);
            }

            @Override
            public long getStride() {
                return Integer.BYTES * (long) NUM_POSTS;
            }

            @Override
            protected long getHeaderSize() {
                return DATA_OFFSET;
            }

            @Override
            public int getSliceMask(long heap, int index) {
                return MemoryUtil.memGetByte(heap + index) & 0xFF;
            }

            @Override
            protected void setSliceMask(long heap, int index, int value) {
                MemoryUtil.memPutByte(heap + index, (byte) value);
            }

            @Override
            public int getVertexOffset(long ptr, int facing) {
                return MemoryUtil.memGetInt(pPost(ptr, facing));
            }

            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                int start = MemoryUtil.memGetInt(pPost(ptr, facing));
                int end = MemoryUtil.memGetInt(pPost(ptr, facing + 1));

                return elementsForVertices(end - start, primitiveType);
            }

            @Override
            public int getIndexOffset(long ptr, int facing) {
                // Unsorted passes always draw through the shared index buffer, which starts at pointer zero.
                return 0;
            }

            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return MemoryUtil.memGetInt(pPost(ptr, lastFacing + 1));
            }

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

                    vertexOffset += vertexCount;
                }

                MemoryUtil.memPutInt(pPost(ptr, NUM_FACINGS), vertexOffset);

                return sliceMask;
            }

            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                throw new UnsupportedOperationException();
            }

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

        public abstract long getStride();

        public abstract int getVertexOffset(long ptr, int facing);

        /**
         * @param primitiveType ignored by layouts which store explicit element counts
         */
        public abstract int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType);

        /**
         * {@return the byte offset of this facing's indices, or zero if the layout draws from the shared index buffer}
         */
        public abstract int getIndexOffset(long ptr, int facing);

        /**
         * {@return the exclusive end of facing {@code lastFacing}'s vertex range}
         * <p>
         * Paired with {@link #getVertexOffset} on a run's first facing, this yields the run's whole vertex span in two
         * loads, and leaves the caller holding the start it needs as the command's base vertex. Merging a run into one
         * command is only valid under {@link #COMPACT}, whose facings all draw from the shared index buffer.
         */
        public abstract int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType);

        /**
         * Populates an entire row from a freshly uploaded mesh and returns its slice mask. {@code ranges} must be
         * contiguous in facing order starting at zero, as produced by the chunk build buffers.
         *
         * @param vertexOffset the section's base offset into the vertex arena, in vertices
         * @param indexOffset  the section's base offset into its index buffer, in bytes; layouts which draw from the
         *                     shared index buffer ignore this
         */
        protected abstract int writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                           Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType);

        /**
         * Rewrites every facing's index offset against a newly allocated index buffer, preserving element counts.
         *
         * @throws UnsupportedOperationException if the layout does not store index offsets
         */
        public abstract void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType);

        /**
         * Rewrites the row against new vertex and index arena offsets, preserving the per-facing sizes. Used when an
         * arena grows and existing allocations move.
         */
        public abstract void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType);

        public abstract int getSliceMask(long heap, int index);

        protected abstract void setSliceMask(long heap, int index, int value);

        /**
         * {@return the number of leading bytes reserved before the row array, or zero if rows start at the heap base}
         */
        protected long getHeaderSize() {
            return 0;
        }

        public final long getRowBasePointer(long heap) {
            return heap + this.getHeaderSize();
        }

        public final long allocateHeap() {
            long size = this.getHeaderSize() + (RenderRegion.REGION_SIZE * this.getStride());
            long pointer = MemoryUtil.nmemAlignedAlloc(64, size);

            if (pointer != 0) {
                MemoryUtil.memSet(pointer, 0, size);
            }

            return pointer;
        }

        public final void freeHeap(long pointer) {
            MemoryUtil.nmemAlignedFree(pointer);
        }

        public final long heapPointer(long ptr, int index) {
            return this.getRowBasePointer(ptr) + (index * this.getStride());
        }

        /**
         * Populates section {@code index}'s row from a freshly uploaded mesh and records its slice mask, keeping the
         * two in sync so callers can't forget the latter. {@code ranges} must be contiguous in facing order starting
         * at zero, as produced by the chunk build buffers.
         *
         * @param vertexOffset the section's base offset into the vertex arena, in vertices
         * @param indexOffset  the section's base offset into its index buffer, in bytes; layouts which draw from the
         *                     shared index buffer ignore this
         */
        public final void writeMeshesAndSliceMask(long heap, int index, int vertexOffset, int indexOffset,
                                                  Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
            int sliceMask = this.writeMeshes(this.heapPointer(heap, index), vertexOffset, indexOffset, ranges, primitiveType);

            this.setSliceMask(heap, index, sliceMask);
        }

        /**
         * Clears section {@code index}'s row and resets its slice mask to zero, keeping the two in sync.
         */
        public final void clearRow(long heap, int index) {
            this.setSliceMask(heap, index, 0);
            MemoryUtil.memSet(this.heapPointer(heap, index), 0x0, this.getStride());
        }

        private static int vertexCountOf(Map<ModelQuadFacing, VertexRange> ranges, int facing) {
            VertexRange range = ranges.get(ModelQuadFacing.VALUES[facing]);

            return range != null ? range.vertexCount() : 0;
        }
    }
}
