package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RenderSectionTest {
    private com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice device;
    private RenderRegionManager regions;

    @BeforeEach
    void activate() {
        device = Devices.active();
        regions = new RenderRegionManager(device.createCommandList());
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

        TranslucentQuadAnalyzer.SortState dynamic = new TranslucentQuadAnalyzer.SortState(TranslucentQuadAnalyzer.Level.DYNAMIC, new float[3], new float[3], 3, new BitSet(), new Vector3f(), null);
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, dynamic, Passes.CUTOUT, TranslucentQuadAnalyzer.SortState.NONE));
        assertEquals(TranslucentQuadAnalyzer.Level.DYNAMIC, section.getHighestSortingLevel());
        assertTrue(section.isNeedsDynamicTranslucencySorting());
        assertEquals(2, section.getTranslucencySortStates().size());
        section.setNeedsDynamicTranslucencySorting(false);

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
