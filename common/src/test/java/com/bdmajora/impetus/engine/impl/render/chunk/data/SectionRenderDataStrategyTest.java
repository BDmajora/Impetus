package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests the native row layouts of section draw data.
class SectionRenderDataStrategyTest {
    // Primitive type with four vertices and six indices.
    private static ChunkPrimitiveType quads() {
        ChunkPrimitiveType type = Mockito.mock(ChunkPrimitiveType.class);
        Mockito.when(type.getVerticesPerPrimitive()).thenReturn(4);
        Mockito.when(type.getIndexBufferElementsPerPrimitive()).thenReturn(6);
        return type;
    }

    // Ranges with counts for the first and third facing.
    private static Map<ModelQuadFacing, VertexRange> ranges(int first, int third) {
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.VALUES[0], new VertexRange(0, first));
        ranges.put(ModelQuadFacing.VALUES[2], new VertexRange(first, third));
        return ranges;
    }

    // Converts between vertices and indices and drops partial primitives.
    @Test
    void conversions() {
        ChunkPrimitiveType type = quads();
        assertEquals(6, SectionRenderDataUnsafe.elementsForVertices(4, type));
        assertEquals(6, SectionRenderDataUnsafe.elementsForVertices(7, type));
        assertEquals(0, SectionRenderDataUnsafe.elementsForVertices(3, type));
        assertEquals(0, SectionRenderDataUnsafe.elementsForVertices(0, type));
        assertEquals(8, SectionRenderDataUnsafe.verticesForElements(12, type));
        assertEquals(4, SectionRenderDataUnsafe.verticesForElements(11, type));
        assertEquals(0, SectionRenderDataUnsafe.verticesForElements(5, type));
    }

    // Writes and reads rows in both layouts.
    @Test
    void writeAndReadBothLayouts() {
        ChunkPrimitiveType type = quads();
        for (SectionRenderDataUnsafe.Strategy strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = strategy.allocateHeap();
            assertNotEquals(0, heap);
            try {
                strategy.writeMeshesAndSliceMask(heap, 3, 100, 40, ranges(8, 4), type);
                assertEquals(0b101, strategy.getSliceMask(heap, 3));
                assertEquals(0, strategy.getSliceMask(heap, 2));
                long row = strategy.heapPointer(heap, 3);
                assertEquals(100, strategy.getVertexOffset(row, 0));
                assertEquals(12, strategy.getElementCount(row, 0, type));
                assertEquals(108, strategy.getVertexOffset(row, 2));
                assertEquals(6, strategy.getElementCount(row, 2, type));
                assertEquals(112, strategy.getRunVertexEnd(row, 2, type));
                assertEquals(0, strategy.getElementCount(row, 1, type));
                if (strategy == SectionRenderDataUnsafe.Strategy.FULL) {
                    assertEquals(40, strategy.getIndexOffset(row, 0));
                    assertEquals(40 + 48, strategy.getIndexOffset(row, 2));
                    strategy.writeIndexOffsets(row, 1000, type);
                    assertEquals(1000, strategy.getIndexOffset(row, 0));
                    assertEquals(1048, strategy.getIndexOffset(row, 2));
                } else {
                    assertEquals(0, strategy.getIndexOffset(row, 2));
                    assertThrows(UnsupportedOperationException.class, () -> strategy.writeIndexOffsets(row, 0, type));
                }
                strategy.rebase(row, 500, 64, type);
                assertEquals(500, strategy.getVertexOffset(row, 0));
                assertEquals(508, strategy.getVertexOffset(row, 2));
                assertEquals(12, strategy.getElementCount(row, 0, type));
                assertEquals(512, strategy.getRunVertexEnd(row, 2, type));
                strategy.rebase(row, 500, 64, type);
                assertEquals(500, strategy.getVertexOffset(row, 0));
                strategy.clearRow(heap, 3);
                assertEquals(0, strategy.getSliceMask(heap, 3));
                assertEquals(0, strategy.getVertexOffset(strategy.heapPointer(heap, 3), 0));
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }

    // Writes a mesh without ranges as an empty row.
    @Test
    void emptyRanges() {
        ChunkPrimitiveType type = quads();
        for (SectionRenderDataUnsafe.Strategy strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = strategy.allocateHeap();
            try {
                strategy.writeMeshesAndSliceMask(heap, 0, 7, 0, Map.of(), type);
                assertEquals(0, strategy.getSliceMask(heap, 0));
                assertEquals(0, strategy.getElementCount(strategy.heapPointer(heap, 0), 4, type));
                assertThrows(NullPointerException.class, () -> strategy.writeMeshesAndSliceMask(heap, 0, 0, 0, null, type));
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }

    // Rejects rows outside the region before touching memory.
    @Test
    void rowIndexIsChecked() {
        ChunkPrimitiveType type = quads();
        for (SectionRenderDataUnsafe.Strategy strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = strategy.allocateHeap();
            try {
                for (int bad : new int[]{-1, RenderRegion.REGION_SIZE, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                    assertThrows(IndexOutOfBoundsException.class, () -> strategy.clearRow(heap, bad));
                    assertThrows(IndexOutOfBoundsException.class, () -> strategy.writeMeshesAndSliceMask(heap, bad, 0, 0, Map.of(), type));
                }
                strategy.writeMeshesAndSliceMask(heap, RenderRegion.REGION_SIZE - 1, 1, 0, ranges(4, 4), type);
                assertEquals(0b101, strategy.getSliceMask(heap, RenderRegion.REGION_SIZE - 1));
                strategy.clearRow(heap, 0);
                strategy.freeHeap(0);
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }

    // Rejects offsets that overflow the unsigned fields.
    @Test
    void offsetOverflowIsRejected() {
        ChunkPrimitiveType type = quads();
        for (SectionRenderDataUnsafe.Strategy strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = strategy.allocateHeap();
            try {
                assertThrows(ArithmeticException.class, () -> strategy.writeMeshesAndSliceMask(heap, 0, Integer.MAX_VALUE, 0, ranges(4, 4), type));
            } finally {
                strategy.freeHeap(heap);
            }
        }
        long heap = SectionRenderDataUnsafe.Strategy.FULL.allocateHeap();
        try {
            assertThrows(ArithmeticException.class, () -> SectionRenderDataUnsafe.Strategy.FULL.writeMeshesAndSliceMask(heap, 0, 0, Integer.MAX_VALUE - 10, ranges(4, 4), type));
        } finally {
            SectionRenderDataUnsafe.Strategy.FULL.freeHeap(heap);
        }
    }

    // Reports the row sizes of the layouts.
    @Test
    void strides() {
        assertEquals(8 + 12 * 7, SectionRenderDataUnsafe.Strategy.FULL.getStride());
        assertEquals(4 * 8, SectionRenderDataUnsafe.Strategy.COMPACT.getStride());
        assertEquals(0, SectionRenderDataUnsafe.Strategy.FULL.getRowBasePointer(0));
        assertEquals(RenderRegion.REGION_SIZE, SectionRenderDataUnsafe.Strategy.COMPACT.getRowBasePointer(0));
    }
}
