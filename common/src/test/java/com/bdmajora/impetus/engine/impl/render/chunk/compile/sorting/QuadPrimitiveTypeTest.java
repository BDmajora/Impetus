package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.testing.TestGl;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class QuadPrimitiveTypeTest {
    @AfterEach
    void restore() {
        ImpetusRuntimeOptions.quadSplittingEnabled = true;
    }

    private static ByteBuffer indices(int quads, QuadPrimitiveType type) {
        return MemoryUtil.memCalloc(type.getIndexBufferSize(quads)).order(ByteOrder.nativeOrder());
    }

    // Two horizontal quads stacked at the given heights, captured through the analyzer
    private static TranslucentQuadAnalyzer.SortState stacked(float... heights) {
        TranslucentQuadAnalyzer analyzer = new TranslucentQuadAnalyzer();
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        for (float h : heights) {
            float[][] corners = {{0, h, 0}, {0, h, 1}, {1, h, 1}, {1, h, 0}};
            for (float[] c : corners) {
                v.x = c[0];
                v.y = c[1];
                v.z = c[2];
                analyzer.capture(v);
            }
        }
        return analyzer.getSortState();
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
    void sortedBuffersOrderBackToFront() {
        ByteBuffer buffer = indices(2, QuadPrimitiveType.TRIANGULATED);
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, null, 0, 0, 0);
        assertEquals(0, buffer.getInt(0));
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, TranslucentQuadAnalyzer.SortState.NONE, 0, 0, 0);

        TranslucentQuadAnalyzer.SortState stacked = stacked(0f, 4f);
        assertEquals(TranslucentQuadAnalyzer.Level.STATIC, stacked.level());
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, stacked, 0, 0, 0);
        assertEquals(0, buffer.getInt(0));
        assertEquals(4, buffer.getInt(24));
        assertThrows(IllegalStateException.class, () -> QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 1, stacked, 0, 0, 0));

        // A dynamic state: two quads whose normals differ, drawn far-to-near from a camera above
        TranslucentQuadAnalyzer analyzer = new TranslucentQuadAnalyzer();
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        float[][] top = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        float[][] side = {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}};
        for (float[][] quad : new float[][][]{top, side}) {
            for (float[] c : quad) {
                v.x = c[0];
                v.y = c[1];
                v.z = c[2];
                analyzer.capture(v);
            }
        }
        TranslucentQuadAnalyzer.SortState dynamic = analyzer.getSortState();
        assertEquals(TranslucentQuadAnalyzer.Level.DYNAMIC, dynamic.level());
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, dynamic, 0.5f, 10f, 0.5f);
        assertEquals(4, buffer.getInt(0));
        ImpetusRuntimeOptions.quadSplittingEnabled = false;
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, dynamic, 0.5f, 10f, 0.5f);
        assertEquals(4, buffer.getInt(0));
        ImpetusRuntimeOptions.quadSplittingEnabled = true;
        TranslucentQuadAnalyzer.SortState degenerate = new TranslucentQuadAnalyzer.SortState(TranslucentQuadAnalyzer.Level.DYNAMIC, dynamic.centers(), new float[6], 6, new BitSet(), new Vector3f(), null);
        QuadPrimitiveType.TRIANGULATED.generateSortedIndexBuffer(buffer, 2, degenerate, 0.5f, 10f, 0.5f);
        MemoryUtil.memFree(buffer);
    }

    @Test
    void bspSorterRejectsBadInput() {
        assertNull(BspTranslucencySorter.sort(new float[3], new float[3], 0, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(new float[3], new float[3], 3000, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(null, new float[3], 1, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(new float[3], null, 1, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(new float[2], new float[3], 1, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(new float[3], new float[2], 1, 0, 0, 0));
        assertNull(BspTranslucencySorter.sort(new float[3], new float[3], 1, 0, 0, 0));
        float[] centers = {0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 2, 0};
        float[] normals = {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, -1, 0};
        int[] above = BspTranslucencySorter.sort(centers, normals, 4, 0, 10, 0);
        assertNotNull(above);
        assertEquals(2, above[0]);
        int[] below = BspTranslucencySorter.sort(centers, normals, 4, 0, -10, 0);
        assertEquals(3, below[0]);
    }
}
