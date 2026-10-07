package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.testing.TestGl;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class RegionTrackingTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    @Test
    void visibilityHistoryPicksTheRegionSeenLongestAgo() {
        RegionVisibilityTracker tracker = new RegionVisibilityTracker(8);
        NativeBuffer bytes = new NativeBuffer(2);
        long address = MemoryUtil.memAddress(bytes.getDirectBuffer());
        short[] ids = {3, 5};

        // Region 3 is seen every frame, region 5 only in the first
        for (int frame = 0; frame <= RegionVisibilityTracker.MIN_SAMPLES; frame++) {
            MemoryUtil.memPutByte(address, (byte) 1);
            MemoryUtil.memPutByte(address + 1, (byte) (frame == 0 ? 1 : 0));
            tracker.record(ids, 2, address);
        }

        assertEquals(5, tracker.leastRecentlySeen(8, id -> true));
        assertEquals(3, tracker.leastRecentlySeen(8, id -> id != 5));
        tracker.reset(5);
        assertEquals(3, tracker.leastRecentlySeen(8, id -> true));
        tracker.clear();
        // Nothing is trusted again until it has been sampled enough
        assertEquals(-1, tracker.leastRecentlySeen(8, id -> true));
        bytes.free();
    }

    @Test
    void storeReportsUploadsAndAnswersSpatialQueries() {
        UploadStream stream = new UploadStream(1 << 20);
        MeshRegionStore store = new MeshRegionStore(8, stream);
        IntArrayList uploaded = new IntArrayList();

        // Without a listener commits still go through
        store.allocateSection(0, 0, 0);
        store.commit();

        store.setUploadListener(uploaded::add);
        assertTrue(store.hasRegionFor(1, 1, 1));
        assertFalse(store.hasRegionFor(8, 0, 0));
        int section = store.allocateSection(9, 0, 0);
        store.commit();
        assertEquals(1, uploaded.size());

        int regionId = section >>> 8;
        // The second region spans blocks 128..256 on x, 0..64 on y and 0..128 on z
        assertTrue(store.isRegionInCameraAxis(regionId, 200, 500, 500));
        assertTrue(store.isRegionInCameraAxis(regionId, -5, 32, -5));
        assertTrue(store.isRegionInCameraAxis(regionId, -5, 500, 64));
        assertFalse(store.isRegionInCameraAxis(regionId, -5, 500, 500));

        // Its centre sits at section (12, 2, 4)
        assertTrue(store.isWithinChunks(regionId, 4, 8, 0, 0));
        assertFalse(store.isWithinChunks(regionId, 4, 0, 0, 0));
        assertFalse(store.isWithinChunks(regionId, 4, 12, 10, 4));
        assertFalse(store.isWithinChunks(regionId, 4, 12, 2, 20));
        store.delete();
        stream.delete();
    }
}
