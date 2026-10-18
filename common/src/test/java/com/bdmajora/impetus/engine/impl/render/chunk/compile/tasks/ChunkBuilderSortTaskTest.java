package com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffersTest;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Quads;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkBuilderSortTaskTest {
    @Test
    void resortsEachTranslucentPassFromTheCameraRelativeToTheSection() {
        SortState.Resortable tree = (SortState.Resortable) Quads.record(Quads.straddling(0)).getSortState();
        RenderSection section = new RenderSection(null, 1, 1, 1);
        // The section starts at block 16, so a camera at z=16.5 is in front of the tree's cut at local z=1
        ChunkBuilderSortTask near = new ChunkBuilderSortTask(section, 16, 16, 16.5, 7, Map.of(Passes.TRANSLUCENT, tree), ChunkBuildBuffersTest.CONFIG);
        ChunkSortOutput output = near.execute(new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG), null);
        assertSame(section, output.render);
        assertEquals(7, output.buildTime);
        ByteBuffer indices = output.meshes.get(Passes.TRANSLUCENT).indexData().getDirectBuffer();
        assertEquals(12 * 4, indices.remaining());
        assertEquals(4, indices.getInt(0));
        output.delete();

        ChunkBuilderSortTask far = new ChunkBuilderSortTask(section, 16, 16, 30, 8, Map.of(Passes.TRANSLUCENT, tree), ChunkBuildBuffersTest.CONFIG);
        ChunkSortOutput behind = far.execute(new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG), null);
        assertEquals(0, behind.meshes.get(Passes.TRANSLUCENT).indexData().getDirectBuffer().getInt(0));
        behind.delete();
    }
}
