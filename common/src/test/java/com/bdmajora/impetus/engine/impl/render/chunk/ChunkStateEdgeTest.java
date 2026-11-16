package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobMetricsTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobResult;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRebuildLists;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkStatus;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests chunk readiness and queue counts and metrics.
class ChunkStateEdgeTest {
    // Collects chunk events as text.
    private static Set<String> drain(ChunkTracker tracker, boolean loads) {
        Set<String> out = new HashSet<>();
        tracker.forEachEvent((x, z) -> { if (loads) out.add(x + "," + z); }, (x, z) -> { if (!loads) out.add(x + "," + z); });
        return out;
    }

    // Reports a chunk ready only when it and its neighbors have both flags.
    @Test
    void readinessNeedsNeighbors() {
        ChunkTracker tracker = new ChunkTracker(1);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
            }
        }
        assertTrue(tracker.getReadyChunks().isEmpty());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_LIGHT_DATA);
            }
        }
        assertEquals(1, tracker.getReadyChunks().size());
        assertEquals(Set.of("0,0"), drain(tracker, true));
        tracker.onChunkStatusRemoved(1, 1, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        assertTrue(tracker.getReadyChunks().isEmpty());
        assertEquals(Set.of("0,0"), drain(tracker, false));
        assertThrows(UnsupportedOperationException.class, () -> tracker.getReadyChunks().clear());
    }

    // Cancels a pending load by an unload and ignores repeated flags.
    @Test
    void queuedEventsCancel() {
        ChunkTracker tracker = new ChunkTracker(0);
        tracker.onChunkStatusAdded(5, 5, ChunkStatus.FLAG_ALL);
        tracker.onChunkStatusAdded(5, 5, ChunkStatus.FLAG_ALL);
        tracker.onChunkStatusAdded(5, 5, 0);
        tracker.onChunkStatusRemoved(5, 5, ChunkStatus.FLAG_ALL);
        tracker.onChunkStatusRemoved(5, 5, ChunkStatus.FLAG_ALL);
        assertTrue(tracker.getReadyChunks().isEmpty());
        assertTrue(drain(tracker, true).isEmpty());
        assertTrue(drain(tracker, false).isEmpty());
        tracker.onChunkStatusRemoved(9, 9, ChunkStatus.FLAG_ALL);
    }

    // Handles extreme and negative chunk coordinates.
    @Test
    void extremeCoordinates() {
        ChunkTracker tracker = new ChunkTracker(0);
        for (int[] c : new int[][]{{Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MIN_VALUE, Integer.MAX_VALUE}, {-1, -1}, {0, 0}}) {
            tracker.onChunkStatusAdded(c[0], c[1], ChunkStatus.FLAG_ALL);
        }
        assertEquals(Set.of(Integer.MAX_VALUE + "," + Integer.MIN_VALUE, Integer.MIN_VALUE + "," + Integer.MAX_VALUE, "-1,-1", "0,0"), drain(tracker, true));
    }

    // Applies a changed radius in both directions and rejects a negative one.
    @Test
    void radiusChanges() {
        ChunkTracker tracker = new ChunkTracker(0);
        assertThrows(IllegalArgumentException.class, () -> new ChunkTracker(-1));
        assertThrows(IllegalArgumentException.class, () -> tracker.setRequiredNeighborRadius(-1));
        tracker.onChunkStatusAdded(0, 0, ChunkStatus.FLAG_ALL);
        assertEquals(Set.of("0,0"), drain(tracker, true));
        tracker.setRequiredNeighborRadius(0);
        tracker.setRequiredNeighborRadius(1);
        assertTrue(tracker.getReadyChunks().isEmpty());
        assertEquals(Set.of("0,0"), drain(tracker, false));
        tracker.setRequiredNeighborRadius(0);
        assertEquals(1, tracker.getReadyChunks().size());
        assertEquals(Set.of("0,0"), drain(tracker, true));
        new ChunkTracker().updateMapCenter(1, 2);
        new ChunkTracker().updateLoadDistance(3);
    }

    // Counts queued and dropped sections and saturates.
    @Test
    void rebuildListCounts() {
        ChunkRebuildLists lists = ChunkRebuildLists.empty();
        assertTrue(lists.isEmpty());
        for (ChunkUpdateType type : ChunkUpdateType.VALUES) {
            assertEquals(0, lists.getUpdateCount(type));
        }
        EnumMap<ChunkUpdateType, Integer> overflow = new EnumMap<>(ChunkUpdateType.class);
        overflow.put(ChunkUpdateType.REBUILD, Integer.MAX_VALUE);
        ChunkRebuildLists dropped = new ChunkRebuildLists(new EnumMap<>(ChunkUpdateType.class), true, overflow);
        assertFalse(dropped.isEmpty());
        assertEquals(Integer.MAX_VALUE, dropped.getUpdateCount(ChunkUpdateType.REBUILD));
        assertEquals(0, dropped.getUpdateCount(ChunkUpdateType.SORT));
        assertEquals(0, dropped.getUpdateCount(null));
        EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> queues = new EnumMap<>(ChunkUpdateType.class);
        queues.put(ChunkUpdateType.SORT, new ArrayDeque<>());
        queues.get(ChunkUpdateType.SORT).add(Mockito.mock(RenderSection.class));
        ChunkRebuildLists one = new ChunkRebuildLists(queues, false, new EnumMap<>(ChunkUpdateType.class));
        assertFalse(one.isEmpty());
        assertEquals(1, one.getUpdateCount(ChunkUpdateType.SORT));
        assertNotSame(ChunkRebuildLists.empty().byUpdateType(), ChunkRebuildLists.empty().byUpdateType());
        assertThrows(NullPointerException.class, () -> new ChunkRebuildLists(null, false, new EnumMap<>(ChunkUpdateType.class)));
        assertThrows(NullPointerException.class, () -> new ChunkRebuildLists(new EnumMap<>(ChunkUpdateType.class), false, null));
    }

    // Output type used by the metrics tests.
    private static final class Output extends ChunkTaskOutput {
        // Creates an output.
        Output() {
            super(null, 0);
        }
    }

    // Collects statistics and the moving average and survives extreme values.
    @Test
    void metrics() {
        ChunkJobMetricsTracker tracker = new ChunkJobMetricsTracker();
        assertEquals(7.0, tracker.getAverageExecutionNanos(Output.class, 7.0));
        assertEquals(7.0, tracker.getAverageExecutionNanos(null, 7.0));
        tracker.collectMetrics(new ChunkJobResult.Success<>(new Output(), -1));
        assertTrue(tracker.getMetrics().isEmpty());
        tracker.collectMetrics(new ChunkJobResult.Success<>(new Output(), 100));
        tracker.collectMetrics(new ChunkJobResult.Success<>(new Output(), 300));
        assertEquals(110.0, tracker.getAverageExecutionNanos(Output.class, 0.0), 1e-9);
        ChunkJobMetricsTracker.MetricsData data = tracker.getMetrics().get(Output.class);
        assertEquals(new ChunkJobMetricsTracker.MetricStats(200, 300, 100), data.getStats());
        assertEquals("avg = 200, max = 300, min = 100", data.getStats().toString());
        assertEquals("avg = <200>, max = <300>, min = <100>", data.getStats().toString(v -> "<" + v + ">"));
        assertThrows(UnsupportedOperationException.class, () -> tracker.getMetrics().clear());
        assertThrows(NullPointerException.class, () -> tracker.collectMetrics(null));
        tracker.tick();
        assertEquals(0, data.getObservationsInLastTimeInterval());
        data.flipInterval();
        assertEquals(2, data.getObservationsInLastTimeInterval());
        assertEquals(new ChunkJobMetricsTracker.MetricStats(0, 0, 0), new ChunkJobMetricsTracker.MetricsData().getStats());
    }

    // Averages without overflow and keeps only the newest observations.
    @Test
    void metricsExtremes() {
        ChunkJobMetricsTracker.MetricsData data = new ChunkJobMetricsTracker.MetricsData();
        data.collect(Long.MAX_VALUE);
        data.collect(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, data.getStats().avg());
        ChunkJobMetricsTracker.MetricsData ring = new ChunkJobMetricsTracker.MetricsData();
        for (int i = 0; i < 10005; i++) {
            ring.collect(i < 5 ? 1_000_000 : 1);
        }
        assertEquals(1, ring.getStats().max());
    }

    // Computes index buffer sizes and rejects negative counts and overflow.
    @Test
    void indexBufferSizes() {
        ChunkPrimitiveType type = new ChunkPrimitiveType() {
            @Override
            public int getVerticesPerPrimitive() {
                return 4;
            }

            @Override
            public int getIndexBufferElementsPerPrimitive() {
                return 6;
            }

            @Override
            public GlPrimitiveType getGlPrimitiveType() {
                return GlPrimitiveType.TRIANGLES;
            }

            @Override
            public void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives) {
            }

            @Override
            public void generateSortedIndexBuffer(ByteBuffer indexBuffer, int numPrimitives, SortState chunkData, float x, float y, float z) {
            }
        };
        assertEquals(0, type.getIndexBufferSize(0));
        assertEquals(24, type.getIndexBufferSize(1));
        assertEquals(24 * 1000, type.getIndexBufferSize(1000));
        assertThrows(IllegalArgumentException.class, () -> type.getIndexBufferSize(-1));
        assertThrows(ArithmeticException.class, () -> type.getIndexBufferSize(Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> type.getIndexBufferSize(Integer.MAX_VALUE / 24 + 1));
        assertTrue(type.getDefines().isEmpty());
    }
}
