package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import com.bdmajora.impetus.engine.impl.render.chunk.sorting.trigger.NormalPlanes;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslucentQuadAnalyzerTest {
    private static void quad(TranslucentQuadAnalyzer analyzer, float[][] corners) {
        ChunkVertexEncoder.Vertex v = new ChunkVertexEncoder.Vertex();
        for (float[] c : corners) {
            v.x = c[0];
            v.y = c[1];
            v.z = c[2];
            analyzer.capture(v);
        }
    }

    private static float[][] horizontal(float y, boolean flipped) {
        return flipped
                ? new float[][]{{1, y, 0}, {1, y, 1}, {0, y, 1}, {0, y, 0}}
                : new float[][]{{0, y, 0}, {0, y, 1}, {1, y, 1}, {1, y, 0}};
    }

    @Test
    void classifiesSectionsByTheirNormals() {
        TranslucentQuadAnalyzer analyzer = new TranslucentQuadAnalyzer();
        assertSame(TranslucentQuadAnalyzer.SortState.NONE, analyzer.getSortState());
        quad(analyzer, horizontal(0, false));
        quad(analyzer, horizontal(0, true));
        TranslucentQuadAnalyzer.SortState same = analyzer.getSortState();
        assertSame(TranslucentQuadAnalyzer.SortState.NONE, same);
        assertFalse(same.requiresDynamicSorting());
        assertSame(same, same.compactForStorage());

        quad(analyzer, horizontal(3, false));
        TranslucentQuadAnalyzer.SortState statik = analyzer.getSortState();
        assertEquals(TranslucentQuadAnalyzer.Level.STATIC, statik.level());
        assertTrue(statik.normalSigns().get(1));
        assertEquals(9, statik.centersLength());
        TranslucentQuadAnalyzer.SortState compact = statik.compactForStorage();
        assertEquals(TranslucentQuadAnalyzer.Level.STATIC, compact.level());
        assertNull(compact.centers());
        assertNull(TranslucentQuadAnalyzer.SortState.compacted(null));
        assertNotNull(TranslucentQuadAnalyzer.SortState.compacted(statik));

        quad(analyzer, new float[][]{{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}});
        TranslucentQuadAnalyzer.SortState dynamic = analyzer.getSortState();
        assertEquals(TranslucentQuadAnalyzer.Level.DYNAMIC, dynamic.level());
        assertTrue(dynamic.requiresDynamicSorting());
        assertSame(dynamic, dynamic.compactForStorage());
        assertNotNull(dynamic.triggerPlanes());
        // +Y, the flipped -Y and the vertical quad's -X each get a plane group
        assertEquals(3, dynamic.triggerPlanes().length);
        NormalPlanes planes = dynamic.triggerPlanes()[0];
        assertEquals(2, planes.distances().length);
        assertEquals(planes.distances()[0], planes.minDistance());
        assertEquals(planes.distances()[1], planes.maxDistance());
        assertTrue(TranslucentQuadAnalyzer.Level.DYNAMIC.requiresDynamicSorting());
        assertFalse(TranslucentQuadAnalyzer.Level.STATIC.requiresDynamicSorting());
        analyzer.clear();
        assertSame(TranslucentQuadAnalyzer.SortState.NONE, analyzer.getSortState());
    }

    @Test
    void tooManyNormalsDropsTriggerPlanes() {
        TranslucentQuadAnalyzer analyzer = new TranslucentQuadAnalyzer();
        for (int i = 0; i < 20; i++) {
            float angle = (float) (i * Math.PI / 40);
            float dx = (float) Math.cos(angle), dz = (float) Math.sin(angle);
            quad(analyzer, new float[][]{{0, 0, 0}, {0, 1, 0}, {dx, 1, dz}, {dx, 0, dz}});
        }
        TranslucentQuadAnalyzer.SortState state = analyzer.getSortState();
        assertEquals(TranslucentQuadAnalyzer.Level.DYNAMIC, state.level());
        assertNull(state.triggerPlanes());
        assertEquals(NormalPlanes.quantize(0, 1, 0), NormalPlanes.quantize(0.001f, 0.999f, 0));
        assertNotEquals(NormalPlanes.quantize(0, 1, 0), NormalPlanes.quantize(1, 0, 0));
    }
}
