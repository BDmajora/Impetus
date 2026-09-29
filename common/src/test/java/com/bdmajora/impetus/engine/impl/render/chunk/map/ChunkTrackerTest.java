package com.bdmajora.impetus.engine.impl.render.chunk.map;

import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTrackerTest {
    private static void fill(ChunkTracker tracker, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
                tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_LIGHT_DATA);
            }
        }
    }

    @Test
    void chunksBecomeReadyOnceTheirNeighboursAreLoaded() {
        ChunkTracker tracker = new ChunkTracker();
        assertThrows(IllegalArgumentException.class, () -> new ChunkTracker(-1));
        tracker.updateMapCenter(0, 0);
        tracker.updateLoadDistance(8);
        fill(tracker, -1, 1);
        tracker.onChunkStatusAdded(0, 0, ChunkStatus.FLAG_ALL);
        assertEquals(1, tracker.getReadyChunks().size());
        assertTrue(tracker.getReadyChunks().contains(PositionUtil.packChunk(0, 0)));
        List<String> events = new ArrayList<>();
        tracker.forEachEvent((x, z) -> events.add("load " + x + "," + z), (x, z) -> events.add("unload " + x + "," + z));
        assertEquals(List.of("load 0,0"), events);
        tracker.onChunkStatusRemoved(1, 1, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        tracker.onChunkStatusRemoved(1, 1, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        assertTrue(tracker.getReadyChunks().isEmpty());
        tracker.onChunkStatusAdded(1, 1, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        events.clear();
        tracker.forEachEvent((x, z) -> events.add("load"), (x, z) -> events.add("unload"));
        assertTrue(events.isEmpty());
        tracker.onChunkStatusRemoved(1, 1, ChunkStatus.FLAG_ALL);
        tracker.onChunkStatusRemoved(5, 5, ChunkStatus.FLAG_ALL);
        events.clear();
        tracker.forEachEvent((x, z) -> events.add("load"), (x, z) -> events.add("unload"));
        assertEquals(List.of("unload"), events);
        assertThrows(UnsupportedOperationException.class, () -> tracker.getReadyChunks().clear());
    }

    @Test
    void changingTheRadiusRecomputesReadiness() {
        ChunkTracker tracker = new ChunkTracker(0);
        fill(tracker, 0, 2);
        assertEquals(9, tracker.getReadyChunks().size());
        assertThrows(IllegalArgumentException.class, () -> tracker.setRequiredNeighborRadius(-1));
        tracker.setRequiredNeighborRadius(0);
        tracker.setRequiredNeighborRadius(1);
        assertEquals(1, tracker.getReadyChunks().size());
        List<String> events = new ArrayList<>();
        tracker.forEachEvent((x, z) -> events.add("load " + x + "," + z), (x, z) -> events.add("unload " + x + "," + z));
        assertEquals(1, events.stream().filter(e -> e.startsWith("load")).count());
        tracker.setRequiredNeighborRadius(0);
        assertEquals(9, tracker.getReadyChunks().size());
        ChunkTracker widened = new ChunkTracker(1);
        fill(widened, 0, 2);
        widened.setRequiredNeighborRadius(2);
        assertTrue(widened.getReadyChunks().isEmpty());
        events.clear();
        widened.forEachEvent((x, z) -> events.add("load"), (x, z) -> events.add("unload"));
        assertTrue(events.isEmpty());
        new ChunkStatus();
        ChunkTrackerHolder world = () -> widened;
        assertSame(widened, ChunkTrackerHolder.get(world));
    }
}
