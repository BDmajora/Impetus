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

    @Test
    void aCancelledOrFailingSortFreesWhatItAlreadyBuilt() {
        SortState.Resortable tree = (SortState.Resortable) Quads.record(Quads.straddling(0)).getSortState();
        RenderSection section = new RenderSection(null, 1, 1, 1);
        // Two passes, so the second one finds a buffer from the first to free
        java.util.LinkedHashMap<com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass, SortState.Resortable> passes =
                new java.util.LinkedHashMap<>();
        passes.put(Passes.TRANSLUCENT, tree);
        passes.put(Passes.SOLID, tree);
        int[] checks = {0};
        com.bdmajora.impetus.engine.impl.util.task.CancellationToken cancelAfterFirst =
                new com.bdmajora.impetus.engine.impl.util.task.CancellationToken() {
                    @Override
                    public boolean isCancelled() {
                        return checks[0]++ > 0;
                    }

                    @Override
                    public void setCancelled() {
                    }
                };
        ChunkBuilderSortTask cancelled = new ChunkBuilderSortTask(section, 16, 16, 16.5, 7, passes, ChunkBuildBuffersTest.CONFIG);
        long before = com.bdmajora.impetus.engine.impl.common.util.NativeBuffer.getTotalAllocated();
        assertNull(cancelled.execute(new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG), cancelAfterFirst));
        assertEquals(2, checks[0]);
        assertEquals(before, com.bdmajora.impetus.engine.impl.common.util.NativeBuffer.getTotalAllocated());

        // A state that fails when asked its quad count makes the sort throw partway, freeing the finished pass's buffer
        SortState.Dynamic broken = org.mockito.Mockito.mock(SortState.Dynamic.class);
        org.mockito.Mockito.when(broken.quadCount()).thenThrow(new IllegalStateException("broken state"));
        java.util.LinkedHashMap<com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass, SortState.Resortable> failing =
                new java.util.LinkedHashMap<>();
        failing.put(Passes.TRANSLUCENT, tree);
        failing.put(Passes.SOLID, broken);
        ChunkBuilderSortTask throwing = new ChunkBuilderSortTask(section, 16, 16, 16.5, 7, failing, ChunkBuildBuffersTest.CONFIG);
        assertThrows(IllegalStateException.class, () -> throwing.execute(new ChunkBuildContext(ChunkBuildBuffersTest.CONFIG), null));
        assertEquals(before, com.bdmajora.impetus.engine.impl.common.util.NativeBuffer.getTotalAllocated());
    }
}
