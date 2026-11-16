package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.CachedBatch;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests the section mesh storage lifecycle and the render list limits.
class StorageEdgeTest {
    // Primitive type with four vertices and six indices.
    private static ChunkPrimitiveType quads() {
        ChunkPrimitiveType type = Mockito.mock(ChunkPrimitiveType.class);
        Mockito.when(type.getVerticesPerPrimitive()).thenReturn(4);
        Mockito.when(type.getIndexBufferElementsPerPrimitive()).thenReturn(6);
        return type;
    }

    // Segment at an offset.
    private static GlBufferSegment segment(int offset) {
        GlBufferSegment segment = Mockito.mock(GlBufferSegment.class);
        Mockito.when(segment.getOffset()).thenReturn(offset);
        return segment;
    }

    // Ranges with one facing.
    private static Map<ModelQuadFacing, VertexRange> ranges() {
        return Map.of(ModelQuadFacing.POS_X, new VertexRange(0, 4));
    }

    // Rejects bad constructor arguments.
    @Test
    void constructorRefusals() {
        assertThrows(NullPointerException.class, () -> new SectionRenderDataStorage(null, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new SectionRenderDataStorage(quads(), false, 0));
        assertThrows(IllegalArgumentException.class, () -> new SectionRenderDataStorage(quads(), true, -1));
    }

    // Replaces meshes and deletes the old allocations.
    @Test
    void setAndRemoveMeshes() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        assertTrue(storage.isEmpty());
        GlBufferSegment first = segment(2);
        GlBufferSegment second = segment(5);
        storage.setMeshes(7, first, null, ranges());
        assertFalse(storage.isEmpty());
        assertEquals(1, storage.getSliceMask(7));
        storage.setMeshes(7, second, null, ranges());
        Mockito.verify(first).delete();
        storage.removeMeshes(7);
        Mockito.verify(second).delete();
        assertTrue(storage.isEmpty());
        assertEquals(0, storage.getSliceMask(7));
        storage.removeMeshes(7);
        storage.delete();
    }

    // Rejects a null allocation and a bad index without changing state.
    @Test
    void setMeshesRefusals() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        assertThrows(NullPointerException.class, () -> storage.setMeshes(0, null, null, ranges()));
        assertThrows(NullPointerException.class, () -> storage.setMeshes(0, segment(0), null, null));
        assertThrows(IndexOutOfBoundsException.class, () -> storage.setMeshes(-1, segment(0), null, ranges()));
        assertThrows(IndexOutOfBoundsException.class, () -> storage.setMeshes(RenderRegion.REGION_SIZE, segment(0), null, ranges()));
        assertTrue(storage.isEmpty());
        assertThrows(ArithmeticException.class, () -> storage.setMeshes(0, segment(Integer.MAX_VALUE), null, ranges()));
        storage.delete();
    }

    // Keeps the old index allocation when the layout cannot store index offsets.
    @Test
    void unsortedStorageRejectsIndexReplacement() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        GlBufferSegment index = segment(1);
        assertThrows(UnsupportedOperationException.class, () -> storage.replaceIndexBuffer(0, index));
        Mockito.verify(index, Mockito.never()).delete();
        storage.delete();
    }

    // Replaces the index allocation of a sorted storage.
    @Test
    void sortedStorageReplacesIndexBuffer() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), true, 4);
        GlBufferSegment index = segment(3);
        GlBufferSegment other = segment(9);
        storage.setMeshes(1, segment(0), index, ranges());
        storage.replaceIndexBuffer(1, other);
        Mockito.verify(index).delete();
        long row = storage.getDataPointer(1);
        assertEquals(36, com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe.Strategy.FULL.getIndexOffset(row, 0));
        storage.removeIndexBuffer(1);
        Mockito.verify(other).delete();
        storage.replaceIndexBuffer(1, null);
        storage.onBufferResized();
        storage.delete();
    }

    // Keeps one cached batch per setting and deletes replaced batches.
    @Test
    void cachedBatches() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        SectionRenderDataStorage.BatchCacheParams on = new SectionRenderDataStorage.BatchCacheParams(true);
        SectionRenderDataStorage.BatchCacheParams off = new SectionRenderDataStorage.BatchCacheParams(false);
        assertNull(storage.getCachedMultiDrawBatch(on));
        CachedBatch a = Mockito.mock(CachedBatch.class);
        CachedBatch b = Mockito.mock(CachedBatch.class);
        CachedBatch c = Mockito.mock(CachedBatch.class);
        storage.storeCachedMultiDrawBatch(on, a);
        storage.storeCachedMultiDrawBatch(off, b);
        assertSame(a, storage.getCachedMultiDrawBatch(on));
        assertSame(b, storage.getCachedMultiDrawBatch(off));
        storage.storeCachedMultiDrawBatch(on, a);
        Mockito.verify(a, Mockito.never()).delete();
        assertSame(a, storage.getCachedMultiDrawBatch(on));
        storage.storeCachedMultiDrawBatch(on, c);
        Mockito.verify(a).delete();
        assertSame(c, storage.getCachedMultiDrawBatch(on));
        storage.invalidateCachedBatches();
        Mockito.verify(b).delete();
        Mockito.verify(c).delete();
        assertNull(storage.getCachedMultiDrawBatch(on));
        assertThrows(NullPointerException.class, () -> storage.getCachedMultiDrawBatch(null));
        assertThrows(NullPointerException.class, () -> storage.storeCachedMultiDrawBatch(null, a));
        assertThrows(NullPointerException.class, () -> storage.storeCachedMultiDrawBatch(on, null));
        storage.delete();
    }

    // Frees native memory once and rejects use afterwards.
    @Test
    void deleteIsIdempotentAndGuardsUse() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        GlBufferSegment segment = segment(0);
        storage.setMeshes(0, segment, null, ranges());
        storage.delete();
        storage.delete();
        Mockito.verify(segment, Mockito.times(1)).delete();
        assertThrows(IllegalStateException.class, () -> storage.getSliceMask(0));
        assertThrows(IllegalStateException.class, () -> storage.getDataPointer(0));
        assertThrows(IllegalStateException.class, storage::getRowBasePointer);
        assertThrows(IllegalStateException.class, () -> storage.setMeshes(0, segment(0), null, ranges()));
        assertThrows(IllegalStateException.class, () -> storage.removeMeshes(0));
        assertThrows(IllegalStateException.class, storage::onBufferResized);
    }

    // Frees the memory even when an allocation fails to delete.
    @Test
    void deleteSurvivesFailingAllocation() {
        SectionRenderDataStorage storage = new SectionRenderDataStorage(quads(), false, 4);
        GlBufferSegment segment = segment(0);
        Mockito.doThrow(new IllegalStateException("gl")).when(segment).delete();
        storage.setMeshes(0, segment, null, ranges());
        assertThrows(IllegalStateException.class, storage::delete);
        storage.delete();
        assertTrue(storage.isEmpty());
    }

    // Keeps the lists per content type and rejects bad indexes and a full list.
    @Test
    void renderListLimits() {
        RenderRegion region = Mockito.mock(RenderRegion.class);
        ChunkRenderList list = new ChunkRenderList(region);
        assertSame(region, list.getRegion());
        assertEquals("[]", list.toString());
        assertNull(list.sectionsWithGeometryIterator());
        assertNull(list.sectionsWithSpritesIterator());
        assertNull(list.sectionsWithEntitiesIterator());
        assertNull(list.sectionsNeedingDynamicSortIterator());
        list.add(0, 1 << RenderVisualsService.HAS_BLOCK_GEOMETRY);
        list.add(255, (1 << RenderVisualsService.HAS_SPRITES) | (1 << RenderVisualsService.NEEDS_DYNAMIC_SORT));
        list.add(3, 1 << RenderVisualsService.HAS_BLOCK_ENTITIES);
        list.add(4, 0);
        assertEquals(4, list.size());
        assertEquals(1, list.getSectionsWithGeometryCount());
        assertEquals(1, list.getSectionsWithSpritesCount());
        assertEquals(1, list.getSectionsWithEntitiesCount());
        assertEquals(255, list.sectionsWithSpritesIterator().nextByteAsInt());
        assertEquals(255, list.sectionsNeedingDynamicSortIterator().nextByteAsInt());
        assertEquals(3, list.sectionsWithEntitiesIterator().nextByteAsInt());
        assertEquals(0, list.getSectionsWithGeometry()[0]);
        assertThrows(IllegalArgumentException.class, () -> list.add(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> list.add(256, 0));
        assertThrows(IllegalArgumentException.class, () -> list.add(Integer.MIN_VALUE, 0));
        assertEquals(4, list.size());
        assertThrows(NullPointerException.class, () -> new ChunkRenderList(null));
        ChunkRenderList full = new ChunkRenderList(region);
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
            full.add(i, 1);
        }
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> full.add(0, 1));
        assertEquals(RenderRegion.REGION_SIZE, full.getSectionsWithGeometryCount());
    }
}
