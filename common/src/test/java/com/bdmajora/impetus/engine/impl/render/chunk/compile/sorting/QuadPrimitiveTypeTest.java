package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.testing.Quads;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class QuadPrimitiveTypeTest {
    private static ByteBuffer indices(int quads, QuadPrimitiveType type) {
        return MemoryUtil.memCalloc(type.getIndexBufferSize(quads)).order(ByteOrder.nativeOrder());
    }

    @Test
    void simpleBuffersEnumerateQuadsInOrder() {
        ByteBuffer tri = indices(2, QuadPrimitiveType.TRIANGULATED);
        QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(tri, 2);
        assertEquals(0, tri.getInt(0));
        assertEquals(2, tri.getInt(12));
        assertEquals(4, tri.getInt(24));
        assertEquals(6, QuadPrimitiveType.TRIANGULATED.getIndexBufferElementsPerPrimitive());
        assertEquals(GlPrimitiveType.TRIANGLES, QuadPrimitiveType.TRIANGULATED.getGlPrimitiveType());
        ByteBuffer direct = indices(1, QuadPrimitiveType.DIRECT);
        QuadPrimitiveType.DIRECT.generateSimpleIndexBuffer(direct, 1);
        assertEquals(3, direct.getInt(12));
        assertEquals(4, QuadPrimitiveType.DIRECT.getIndexBufferElementsPerPrimitive());
        assertEquals(4, QuadPrimitiveType.DIRECT.getVerticesPerPrimitive());
        assertEquals(GlPrimitiveType.QUADS, QuadPrimitiveType.DIRECT.getGlPrimitiveType());
        assertTrue(QuadPrimitiveType.DIRECT.getDefines().isEmpty());
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.DIRECT.generateSimpleIndexBuffer(direct, 5));
        MemoryUtil.memFree(tri);
        MemoryUtil.memFree(direct);
    }

    @Test
    void sortedBuffersFollowTheSortState() {
        ByteBuffer buffer = indices(2, QuadPrimitiveType.TRIANGULATED);
        // Checks nothing to sort by.
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, null, 0, 0, 0);
        assertEquals(0, buffer.getInt(0));
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, SortState.NONE, 0, 0, 0);
        assertEquals(4, buffer.getInt(24));

        // Checks stacked quads.
        SortState stacked = Quads.record(Quads.facingY(0, 0, 1, 0, 1), Quads.facingY(4, 0, 1, 0, 1)).getSortState();
        assertInstanceOf(SortState.Static.class, stacked);
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, stacked, 0, 100, 0);
        assertEquals(0, buffer.getInt(0));
        assertEquals(4, buffer.getInt(24));
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 1, stacked, 0, 0, 0));

        // A distance sort draws the farther quad's corners first
        SortState dynamic = new SortState.Dynamic(new float[]{0.5f, 1, 0.5f, 0, 0.5f, 0.5f});
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, dynamic, 0.5f, 10f, 0.5f);
        assertEquals(4, buffer.getInt(0));
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, dynamic, 0.5f, -10f, 0.5f);
        assertEquals(0, buffer.getInt(0));

        // Checks the tree output.
        PartitionTree tree = (PartitionTree) Quads.record(Quads.straddling(0)).getSortState();
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, tree, 0, 0, 0.5f);
        assertEquals(4, buffer.getInt(0));
        assertEquals(0, buffer.getInt(24));
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, tree, 0, 0, 5);
        assertEquals(0, buffer.getInt(0));
        ByteBuffer tooSmall = indices(1, QuadPrimitiveType.TRIANGULATED);
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(tooSmall, 2, tree, 0, 0, 0));
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(tooSmall, 1, tree, 0, 0, 0));
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(tooSmall, 1, dynamic, 0, 0, 0));
        // Direct quads write four indices per primitive in the same order
        ByteBuffer direct = indices(2, QuadPrimitiveType.DIRECT);
        QuadPrimitiveType.DIRECT.generateSortedIndexBuffer(direct, 2, tree, 0, 0, 0.5f);
        assertEquals(4, direct.getInt(0));
        assertEquals(7, direct.getInt(12));
        MemoryUtil.memFree(buffer);
        MemoryUtil.memFree(tooSmall);
        MemoryUtil.memFree(direct);
    }

    // Rejects a buffer whose position leaves too few bytes and writes from the position of a large enough one.
    @Test
    void simpleBuffersRespectThePosition() {
        ByteBuffer exact = indices(1, QuadPrimitiveType.TRIANGULATED);
        exact.position(4);
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(exact, 1));
        ByteBuffer larger = MemoryUtil.memCalloc(28).order(ByteOrder.nativeOrder());
        larger.putInt(0, -7).position(4);
        QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(larger, 1);
        assertEquals(-7, larger.getInt(0));
        assertEquals(0, larger.getInt(4));
        assertEquals(0, larger.getInt(24));
        QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(larger, 0);
        assertThrows(IllegalArgumentException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSimpleIndexBuffer(larger, -1));
        MemoryUtil.memFree(exact);
        MemoryUtil.memFree(larger);
    }
}
