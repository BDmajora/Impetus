package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

// Tests that a failed search leaves the manager usable.
class RenderListManagerFailureTest {
    // Graph whose submissions return a failed future.
    private static SectionGraph failingGraph() {
        SectionGraph graph = Mockito.mock(SectionGraph.class);
        Mockito.when(graph.getLattice()).thenReturn(Mockito.mock(SectionLattice.class));
        Mockito.when(graph.submit(Mockito.any(), Mockito.anyBoolean())).thenAnswer(inv -> CompletableFuture.failedFuture(new IllegalStateException("search")));
        return graph;
    }

    // Viewport mock.
    private static Viewport viewport() {
        Viewport viewport = Mockito.mock(Viewport.class);
        Mockito.when(viewport.getChunkCoord()).thenReturn(new Vector3i());
        Mockito.when(viewport.getBlockCoord()).thenReturn(new Vector3i());
        return viewport;
    }

    // Joins a failed search once and accepts the next search.
    @Test
    void failedSearchIsForgotten() {
        SectionGraph graph = failingGraph();
        RenderListManager manager = new RenderListManager(graph, false, AsyncOcclusionMode.NONE, null);
        assertThrows(CompletionException.class, () -> manager.startGraphUpdate(viewport(), 1, 1, 8f, true, 4));
        Mockito.verify(graph, Mockito.times(1)).onSearchJoined();
        assertThrows(CompletionException.class, () -> manager.startGraphUpdate(viewport(), 2, 1, 8f, true, 4));
        Mockito.verify(graph, Mockito.times(2)).onSearchJoined();
        assertEquals(0, manager.getLastUpdatedFrame());
        assertTrue(manager.getRenderLists().getPasses().isEmpty());
        manager.destroy();
        manager.finishPreviousGraphUpdate();
    }

    // Forgets a failed search at destroy and rejects the wrong search kind.
    @Test
    void destroyForgetsFailure() {
        SectionGraph graph = failingGraph();
        RenderListManager manager = new RenderListManager(graph, true, AsyncOcclusionMode.EVERYTHING, null);
        assertThrows(IllegalStateException.class, () -> manager.startGraphUpdate(viewport(), 1, 1, 8f, true, 4));
        manager.startShadowGraphUpdate(viewport(), 1, 1, 8f, null, 4);
        assertThrows(IllegalStateException.class, () -> manager.startShadowGraphUpdate(viewport(), 2, 1, 8f, null, 4));
        CompletionException failure = assertThrows(CompletionException.class, manager::destroy);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        Mockito.verify(graph, Mockito.times(1)).onSearchJoined();
        manager.destroy();
        assertThrows(NullPointerException.class, () -> new RenderListManager(null, false, AsyncOcclusionMode.NONE, null));
        assertEquals("", manager.getTickerDebugString());
        manager.tickVisibleRenders();
        assertFalse(manager.isSectionVisible(0, 0, 0));
    }
}
