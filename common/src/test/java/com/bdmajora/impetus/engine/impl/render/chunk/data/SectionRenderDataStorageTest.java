package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferArena;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.MultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.CachedBatch;
import com.bdmajora.testing.Devices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class SectionRenderDataStorageTest {
    private static final QuadPrimitiveType QUADS = QuadPrimitiveType.TRIANGULATED;
    private static final SectionRenderDataUnsafe.Strategy FULL = SectionRenderDataUnsafe.Strategy.FULL;
    private static final SectionRenderDataUnsafe.Strategy COMPACT = SectionRenderDataUnsafe.Strategy.COMPACT;

    private GLRenderDevice device;
    private CommandList commands;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
    }

    @AfterEach
    void deactivate() {
        device.makeInactive();
    }

    private GlBufferSegment segment(GlBufferArena arena, int bytes) {
        PendingUpload upload = PendingUpload.of(new NativeBuffer(bytes));
        arena.upload(commands, new ArrayList<>(List.of(upload)));
        return upload.getResult();
    }

    private static Map<ModelQuadFacing, VertexRange> quads(ModelQuadFacing... facings) {
        Map<ModelQuadFacing, VertexRange> ranges = new java.util.EnumMap<>(ModelQuadFacing.class);
        int start = 0;
        for (ModelQuadFacing facing : facings) {
            ranges.put(facing, new VertexRange(start, 4));
            start += 4;
        }
        return ranges;
    }

    @Test
    void sortedPassesKeepEveryFacingsOffsetsInTheFullLayout() {
        GlBufferArena vertices = new GlBufferArena(commands, 20, 4, new FallbackStagingBuffer(commands));
        GlBufferArena indices = new GlBufferArena(commands, 4, 4, new FallbackStagingBuffer(commands));
        SectionRenderDataStorage storage = new SectionRenderDataStorage(QUADS, true, 1);
        assertTrue(storage.isEmpty());
        assertSame(QUADS, storage.getPrimitiveType());
        // The row base is row zero, which hot loops index from themselves
        assertEquals(storage.getDataPointer(0), storage.getRowBasePointer());
        segment(vertices, 16 * 20);
        GlBufferSegment vertexSeg = segment(vertices, 8 * 20);
        GlBufferSegment indexSeg = segment(indices, 12 * 4);
        storage.setMeshes(5, vertexSeg, indexSeg, quads(ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y));
        assertFalse(storage.isEmpty());
        long row = storage.getDataPointer(5);
        int base = vertexSeg.getOffset();
        int indexBase = indexSeg.getOffset() * 4;
        assertEquals((1 << ModelQuadFacing.POS_Y.ordinal()) | (1 << ModelQuadFacing.NEG_Y.ordinal()), storage.getSliceMask(5));
        // Each facing starts where the previous ones end, in vertices and in index bytes
        assertEquals(base, FULL.getVertexOffset(row, ModelQuadFacing.POS_X.ordinal()));
        assertEquals(0, FULL.getElementCount(row, ModelQuadFacing.POS_X.ordinal(), QUADS));
        assertEquals(6, FULL.getElementCount(row, ModelQuadFacing.POS_Y.ordinal(), QUADS));
        assertEquals(base + 4, FULL.getVertexOffset(row, ModelQuadFacing.POS_Z.ordinal()));
        assertEquals(indexBase + 24, FULL.getIndexOffset(row, ModelQuadFacing.POS_Z.ordinal()));
        assertEquals(base + 8, FULL.getVertexOffset(row, ModelQuadFacing.UNASSIGNED.ordinal()));
        assertEquals(indexBase + 48, FULL.getIndexOffset(row, ModelQuadFacing.UNASSIGNED.ordinal()));
        assertEquals(base + 8, FULL.getRunVertexEnd(row, ModelQuadFacing.NEG_Y.ordinal(), QUADS));

        // A resize rewrites the same offsets from the allocations, and a moved allocation shifts the whole row
        storage.onBufferResized();
        assertEquals(base + 4, FULL.getVertexOffset(row, ModelQuadFacing.NEG_Y.ordinal()));
        FULL.rebase(row, base + 100, 400, QUADS);
        assertEquals(base + 104, FULL.getVertexOffset(row, ModelQuadFacing.NEG_Y.ordinal()));
        assertEquals(424, FULL.getIndexOffset(row, ModelQuadFacing.NEG_Y.ordinal()));
        storage.onBufferResized();
        assertEquals(base + 4, FULL.getVertexOffset(row, ModelQuadFacing.NEG_Y.ordinal()));

        // A re-sort swaps the index buffer and keeps the element counts
        GlBufferSegment resorted = segment(indices, 12 * 4);
        storage.replaceIndexBuffer(5, resorted);
        assertEquals(resorted.getOffset() * 4, FULL.getIndexOffset(row, 0));
        assertEquals(resorted.getOffset() * 4 + 24, FULL.getIndexOffset(row, ModelQuadFacing.NEG_Y.ordinal()));
        assertEquals(6, FULL.getElementCount(row, ModelQuadFacing.NEG_Y.ordinal(), QUADS));
        storage.replaceIndexBuffer(5, null);
        assertEquals(0, FULL.getIndexOffset(row, 0));
        storage.replaceIndexBuffer(2, segment(indices, 4));

        storage.removeMeshes(5);
        assertTrue(storage.isEmpty());
        assertEquals(0, storage.getSliceMask(5));
        assertEquals(0, FULL.getElementCount(row, ModelQuadFacing.POS_Y.ordinal(), QUADS));
        storage.removeMeshes(5);
        storage.removeMeshes(2);
        // A second upload over an existing one frees the old allocations first
        storage.setMeshes(1, segment(vertices, 4 * 20), segment(indices, 6 * 4), quads(ModelQuadFacing.UNASSIGNED));
        storage.setMeshes(1, segment(vertices, 4 * 20), segment(indices, 6 * 4), quads(ModelQuadFacing.UNASSIGNED));
        assertFalse(storage.isEmpty());
        storage.delete();
        assertTrue(storage.isEmpty());
        vertices.delete(commands);
        indices.delete(commands);
    }

    @Test
    void unsortedPassesStoreFencePostsAndAByteMaskHeader() {
        GlBufferArena vertices = new GlBufferArena(commands, 40, 4, new FallbackStagingBuffer(commands));
        // The arena allocates in 40-byte elements, two 20-byte vertices each
        SectionRenderDataStorage storage = new SectionRenderDataStorage(QUADS, false, 2);
        assertEquals(storage.getDataPointer(0), storage.getRowBasePointer());
        segment(vertices, 3 * 40);
        GlBufferSegment seg = segment(vertices, 6 * 40);
        storage.setMeshes(7, seg, null, quads(ModelQuadFacing.POS_X, ModelQuadFacing.POS_Y, ModelQuadFacing.UNASSIGNED));
        long row = storage.getDataPointer(7);
        assertEquals(storage.getRowBasePointer() + 7 * COMPACT.getStride(), row);
        int base = seg.getOffset() * 2;
        assertEquals(0b1000011, storage.getSliceMask(7));
        assertEquals(base + 4, COMPACT.getVertexOffset(row, ModelQuadFacing.POS_Y.ordinal()));
        assertEquals(6, COMPACT.getElementCount(row, ModelQuadFacing.POS_Y.ordinal(), QUADS));
        assertEquals(0, COMPACT.getElementCount(row, ModelQuadFacing.POS_Z.ordinal(), QUADS));
        // Unsorted passes draw from the shared index buffer, so there is no per-facing offset
        assertEquals(0, COMPACT.getIndexOffset(row, ModelQuadFacing.POS_Y.ordinal()));
        assertEquals(base + 8, COMPACT.getRunVertexEnd(row, ModelQuadFacing.POS_Y.ordinal(), QUADS));
        assertEquals(base + 12, COMPACT.getRunVertexEnd(row, ModelQuadFacing.UNASSIGNED.ordinal(), QUADS));
        assertThrows(UnsupportedOperationException.class, () -> COMPACT.writeIndexOffsets(row, 0, QUADS));

        // Rebasing moves every post by the same amount, and is free when nothing moved
        storage.onBufferResized();
        assertEquals(base, COMPACT.getVertexOffset(row, 0));
        COMPACT.rebase(row, base + 10, 0, QUADS);
        assertEquals(base + 14, COMPACT.getVertexOffset(row, ModelQuadFacing.POS_Y.ordinal()));
        assertEquals(base + 22, COMPACT.getRunVertexEnd(row, ModelQuadFacing.UNASSIGNED.ordinal(), QUADS));
        storage.onBufferResized();
        assertEquals(base + 4, COMPACT.getVertexOffset(row, ModelQuadFacing.POS_Y.ordinal()));

        storage.removeMeshes(7);
        assertEquals(0, storage.getSliceMask(7));
        assertEquals(0, COMPACT.getVertexOffset(row, ModelQuadFacing.POS_Y.ordinal()));
        storage.delete();
        vertices.delete(commands);
    }

    @Test
    void elementAndVertexCountsConvertThroughThePrimitive() {
        assertEquals(12, SectionRenderDataUnsafe.elementsForVertices(8, QUADS));
        assertEquals(8, SectionRenderDataUnsafe.verticesForElements(12, QUADS));
        assertEquals(8, SectionRenderDataUnsafe.elementsForVertices(8, QuadPrimitiveType.DIRECT));
        assertEquals(8L + 12L * ModelQuadFacing.COUNT, FULL.getStride());
        assertEquals(4L * (ModelQuadFacing.COUNT + 1), COMPACT.getStride());
        assertNotNull(new SectionRenderDataUnsafe());
    }

    @Test
    void twoBatchesAreCachedOnePerCullingMode() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(QUADS, false, 1);
        SectionRenderDataStorage.BatchCacheParams culled = new SectionRenderDataStorage.BatchCacheParams(true);
        SectionRenderDataStorage.BatchCacheParams unculled = new SectionRenderDataStorage.BatchCacheParams(false);
        assertNull(storage.getCachedMultiDrawBatch(culled));
        MultiDrawBatch firstBatch = Mockito.mock(MultiDrawBatch.class);
        MultiDrawBatch secondBatch = Mockito.mock(MultiDrawBatch.class);
        MultiDrawBatch thirdBatch = Mockito.mock(MultiDrawBatch.class);
        MultiDrawBatch fourthBatch = Mockito.mock(MultiDrawBatch.class);
        CachedBatch first = new CachedBatch(firstBatch, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
        CachedBatch second = new CachedBatch(secondBatch, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
        storage.storeCachedMultiDrawBatch(culled, first);
        storage.storeCachedMultiDrawBatch(unculled, second);
        assertSame(first, storage.getCachedMultiDrawBatch(culled));
        assertSame(second, storage.getCachedMultiDrawBatch(new SectionRenderDataStorage.BatchCacheParams(false)));
        // Replacing either slot frees the batch it held
        CachedBatch third = new CachedBatch(thirdBatch, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
        CachedBatch fourth = new CachedBatch(fourthBatch, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
        storage.storeCachedMultiDrawBatch(culled, third);
        Mockito.verify(firstBatch).delete();
        storage.storeCachedMultiDrawBatch(unculled, fourth);
        Mockito.verify(secondBatch).delete();
        assertSame(third, storage.getCachedMultiDrawBatch(culled));
        assertSame(fourth, storage.getCachedMultiDrawBatch(unculled));
        // Any mesh change drops both
        storage.invalidateCachedBatches();
        Mockito.verify(thirdBatch).delete();
        Mockito.verify(fourthBatch).delete();
        assertNull(storage.getCachedMultiDrawBatch(culled));
        assertNull(storage.getCachedMultiDrawBatch(unculled));
        storage.delete();
    }

    @Test
    void aFailedHeapAllocationIsReported() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(0L);
            assertThrows(OutOfMemoryError.class, () -> new SectionRenderDataStorage(QUADS, false, 1));
            assertThrows(OutOfMemoryError.class, () -> new SectionRenderDataStorage(QUADS, true, 1));
        }
    }
}
