package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Quads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenderSectionTest {
    private com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice device;
    private RenderRegionManager regions;

    @BeforeEach
    void activate() {
        device = Devices.active();
        regions = new RenderRegionManager(device.createCommandList(), Passes.CONFIG);
    }

    @AfterEach
    void deactivate() {
        regions.delete(device.createCommandList());
        device.makeInactive();
    }

    @Test
    void geometryOfTheSection() {
        RenderSection section = new RenderSection(null, 3, -1, 2);
        assertEquals(48, section.getOriginX());
        assertEquals(-16, section.getOriginY());
        assertEquals(32, section.getOriginZ());
        assertEquals(56, section.getCenterX());
        assertEquals(-8, section.getCenterY());
        assertEquals(40, section.getCenterZ());
        assertEquals(3, section.getChunkX());
        assertEquals(-1, section.getChunkY());
        assertEquals(2, section.getChunkZ());
        assertEquals(LocalSectionIndex.pack(3, 3, 2), section.getSectionIndex());
        assertEquals(0f, section.getSquaredDistance(56, -8, 40));
        assertEquals(0.75f, section.getSquaredDistanceFromBlockCenter(56, -8, 40), 1e-5);
        assertTrue(section.isAlignedWithSectionOnGrid(3, 9, 9));
        assertFalse(section.isAlignedWithSectionOnGrid(9, 9, 9));
        assertEquals(PositionUtil.packSection(3, -1, 2), section.positionAsLong());
        assertTrue(section.toString().contains("RenderSection at chunk (3, -1, 2)"));
    }

    @Test
    void lifecycleTracksDataUpdatesAndSorting() {
        RenderRegion region = regions.createForChunk(0, 0, 0);
        RenderSection section = new RenderSection(region, 0, 0, 0);
        region.addSection(section);
        assertFalse(section.isBuilt());
        assertFalse(section.hasAnythingToRender());
        assertNull(section.getBuiltContext());
        assertSame(region, section.getRegion());
        BuiltRenderSectionData data = new BuiltRenderSectionData();
        data.hasBlockGeometry = true;
        assertTrue(section.setInfo(data));
        assertTrue(region.getSectionLoadTimes()[0] > 0);
        assertFalse(section.setInfo(data));
        assertTrue(section.isBuilt());
        assertTrue(section.hasAnythingToRender());
        assertNotEquals(0, section.getVisualsServiceFlags());

        assertTrue(section.requestUpdate(ChunkUpdateType.REBUILD));
        assertFalse(section.requestUpdate(ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.REBUILD, section.getPendingUpdate());
        section.setPendingUpdate(null);
        section.setLastBuiltFrame(3);
        section.setLastSubmittedFrame(4);
        assertEquals(3, section.getLastBuiltFrame());
        assertEquals(4, section.getLastSubmittedFrame());
        section.setLastBuildDurationNanos(9);
        assertEquals(9, section.getLastBuildDurationNanos());

        // Every mirror of the packed word hears about each change to it
        AtomicInteger notified = new AtomicInteger();
        section.setMetadataSink(changed -> {
            assertSame(section, changed);
            notified.incrementAndGet();
        });
        section.setPendingUpdate(ChunkUpdateType.SORT);
        assertEquals(1, notified.get());
        assertEquals(ChunkUpdateType.SORT, PackedSectionMetadata.getPendingUpdate(section.getPackedMetadata()));
        section.setPendingUpdate(null);

        // Only a distance sort makes the section ask to be re-sorted as the camera moves
        assertEquals(RenderSection.SortMode.NONE, section.getSortMode());
        assertEquals(RenderSection.NO_TRANSLUCENT_GEOMETRY, section.getHighestSortingIndex());
        SortState.Dynamic dynamic = new SortState.Dynamic(new float[3]);
        PartitionTree tree = (PartitionTree) Quads.record(Quads.straddling(0)).getSortState();
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, dynamic, Passes.WIDE, tree), dynamic.debugIndex());
        assertEquals(RenderSection.SortMode.DYNAMIC, section.getSortMode());
        assertEquals(3, section.getHighestSortingIndex());
        assertEquals(2, section.getTranslucencySortStates().size());
        assertNotEquals(0, section.getVisualsServiceFlags() & (1 << RenderVisualsService.NEEDS_DYNAMIC_SORT));
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, tree), tree.debugIndex());
        assertTrue(section.isTreeSorted());
        assertEquals(0, section.getVisualsServiceFlags() & (1 << RenderVisualsService.NEEDS_DYNAMIC_SORT));
        section.setTranslucencySortStates(Map.of(), RenderSection.NO_TRANSLUCENT_GEOMETRY);
        assertEquals(RenderSection.SortMode.NONE, section.getSortMode());
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, dynamic), dynamic.debugIndex());
        section.clearTranslucencySortStates();
        assertTrue(section.getTranslucencySortStates().isEmpty());
        assertFalse(section.isTreeSorted());
        assertEquals(VisibilityEncoding.NULL, section.getVisibilityData());
        section.setMetadataSink(null);

        AtomicBoolean cancelled = new AtomicBoolean();
        CancellationToken token = new CancellationToken() {
            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public void setCancelled() {
                cancelled.set(true);
            }
        };
        section.setBuildCancellationToken(token);
        assertSame(token, section.getBuildCancellationToken());
        assertTrue(PackedSectionMetadata.isBuildInFlight(section.getPackedMetadata()));
        assertFalse(section.isDisposed());
        section.delete();
        assertTrue(cancelled.get());
        assertNull(section.getBuildCancellationToken());
        assertTrue(section.isDisposed());
        assertFalse(section.isBuilt());
        new RenderSection(region, 1, 0, 0).delete();
        assertFalse(RenderSection.EMPTY_DATA.hasBlockGeometry);
    }
}
