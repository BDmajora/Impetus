package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Tests search bookkeeping and rebuild queue limits.
class GraphCollectorEdgeTest {
    // Runs tasks inline and counts searches until they are joined.
    @Test
    void inlineSearchesAreCounted() {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.NONE, false, false);
        assertEquals("x", graph.submit(() -> "x", true).join());
        graph.onSearchJoined();
        assertThrows(IllegalStateException.class, graph::onSearchJoined);
        assertThrows(NullPointerException.class, () -> graph.submit(null, false));
        graph.destroy();
    }

    // Forgets a search whose inline task failed.
    @Test
    void failedInlineSearchIsNotCounted() {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.NONE, false, false);
        assertThrows(IllegalArgumentException.class, () -> graph.submit(() -> {
            throw new IllegalArgumentException("boom");
        }, false));
        assertThrows(IllegalStateException.class, graph::onSearchJoined);
        AtomicInteger ran = new AtomicInteger();
        graph.updateSectionMetadata(0, 0, 0, 0L, ran::incrementAndGet);
        graph.runDeferredTasks();
        graph.destroy();
    }

    // Runs async searches on the search thread and defers metadata updates until joined.
    @Test
    void asyncSearchDefersUpdates() throws Exception {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.EVERYTHING, false, false);
        CompletableFuture<String> future = graph.submit(() -> Thread.currentThread().getName(), true);
        assertEquals("Impetus chunk graph search thread", future.get());
        RenderSection section = Mockito.mock(RenderSection.class);
        assertThrows(IllegalStateException.class, () -> graph.attachRenderSection(section));
        assertThrows(IllegalStateException.class, () -> graph.detachRenderSection(section));
        AtomicInteger changed = new AtomicInteger();
        graph.updateSectionMetadata(0, 0, 0, 0L, changed::incrementAndGet);
        graph.runDeferredTasks();
        graph.onSearchJoined();
        graph.runDeferredTasks();
        assertThrows(NullPointerException.class, () -> graph.updateSectionMetadata(0, 0, 0, 0L, null));
        graph.destroy();
        graph.destroy();
        assertNotNull(graph.getLattice());
    }

    // Collector without room for first builds.
    private static VisibleChunkCollector collector(int targetQueueSize) {
        SectionLattice lattice = Mockito.mock(SectionLattice.class);
        RenderSection section = Mockito.mock(RenderSection.class);
        RenderRegion region = Mockito.mock(RenderRegion.class);
        Mockito.when(region.getId()).thenReturn(0);
        Mockito.when(section.getRegion()).thenReturn(region);
        Mockito.when(lattice.sectionAt(Mockito.anyInt())).thenReturn(section);
        VisibleChunkCollector collector = new VisibleChunkCollector(lattice, 1, 2, targetQueueSize, new Vector3i(0, 0, 0));
        int meta = PackedSectionMetadata.toCompactMeta(PackedSectionMetadata.withPendingUpdate(0L, ChunkUpdateType.INITIAL_BUILD));
        for (int i = 0; i < 10; i++) {
            collector.visit(i, 0, i, i, 0, 0, meta, true);
        }
        return collector;
    }

    // Queues no first builds for a zero budget and reports the overflow.
    @Test
    void zeroBudgetQueuesNothing() {
        VisibleChunkCollector collector = collector(0);
        collector.finishRebuildLists();
        ChunkRebuildLists lists = collector.getRebuildLists();
        assertEquals(0, lists.byUpdateType().get(ChunkUpdateType.INITIAL_BUILD).size());
        assertEquals(10, lists.queueOverflowCounts().get(ChunkUpdateType.INITIAL_BUILD));
        assertTrue(lists.hasAdditionalUpdates());
        assertEquals(10, lists.getUpdateCount(ChunkUpdateType.INITIAL_BUILD));
    }

    // Treats a negative budget as zero.
    @Test
    void negativeBudgetQueuesNothing() {
        VisibleChunkCollector collector = collector(-5);
        collector.finishRebuildLists();
        assertEquals(10, collector.getRebuildLists().getUpdateCount(ChunkUpdateType.INITIAL_BUILD));
        assertTrue(collector.getRebuildLists().byUpdateType().get(ChunkUpdateType.INITIAL_BUILD).isEmpty());
    }

    // Queues the nearest first builds within the budget and everything when it is large.
    @Test
    void budgetsSelectTheNearest() {
        VisibleChunkCollector small = collector(3);
        small.finishRebuildLists();
        assertEquals(3, small.getRebuildLists().byUpdateType().get(ChunkUpdateType.INITIAL_BUILD).size());
        assertEquals(7, small.getRebuildLists().queueOverflowCounts().get(ChunkUpdateType.INITIAL_BUILD));
        VisibleChunkCollector all = collector(Integer.MAX_VALUE);
        all.finishRebuildLists();
        assertEquals(10, all.getRebuildLists().byUpdateType().get(ChunkUpdateType.INITIAL_BUILD).size());
        assertFalse(all.getRebuildLists().hasAdditionalUpdates());
        assertTrue(all.getRebuildLists().queueOverflowCounts().isEmpty());
    }

    // Rejects a second finish and early access.
    @Test
    void finishOrder() {
        VisibleChunkCollector collector = collector(1);
        assertThrows(IllegalStateException.class, collector::getRebuildLists);
        collector.finishRebuildLists();
        assertThrows(IllegalStateException.class, collector::finishRebuildLists);
        assertEquals(1, collector.createRenderLists().getNumRegions());
    }
}
