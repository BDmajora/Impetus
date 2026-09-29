package com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkBuilderSortTaskTest {
    @Test
    void resortsEachTranslucentPass() {
        TranslucentQuadAnalyzer analyzer = new TranslucentQuadAnalyzer();
        for (ChunkVertexEncoder.Vertex v : ChunkBuildBuffersTest.quad(0)) analyzer.capture(v);
        for (ChunkVertexEncoder.Vertex v : ChunkBuildBuffersTest.quad(5)) analyzer.capture(v);
        TranslucentQuadAnalyzer.SortState state = analyzer.getSortState();
        RenderSection section = new RenderSection(null, 1, 1, 1);
        ChunkBuilderSortTask task = new ChunkBuilderSortTask(section, 8, 100, 8, 7, Map.of(Passes.TRANSLUCENT, state));
        ChunkSortOutput output = task.execute(new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG), null);
        assertSame(section, output.render);
        assertEquals(7, output.buildTime);
        assertEquals(12 * 4, output.meshes.get(Passes.TRANSLUCENT).indexData().getLength());
        output.delete();
    }
}
