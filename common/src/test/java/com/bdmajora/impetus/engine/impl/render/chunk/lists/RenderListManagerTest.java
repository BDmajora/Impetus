package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderListManagerTest {
    private GLRenderDevice device;
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

    private void giveGeometry(RenderSection section) {
        var commands = device.createCommandList();
        var resources = section.getRegion().createResources(Passes.TRANSLUCENT.vertexType().getVertexFormat(), commands);
        PendingUpload upload = PendingUpload.of(new NativeBuffer(20 * 4));
        resources.getGeometryArena().upload(commands, new ArrayList<>(List.of(upload)));
        section.getRegion().createStorage(Passes.TRANSLUCENT).setMeshes(section.getSectionIndex(), upload.getResult(), null, Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 4)));
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, new TranslucentQuadAnalyzer.SortState(TranslucentQuadAnalyzer.Level.DYNAMIC, new float[3], new float[3], 3, new BitSet(), new Vector3f(), null)));
    }

    @Test
    void synchronousGraphUpdatesProduceListsAndStatistics() {
        RenderListManager manager = new RenderListManager(0, 4, false, new GenericSectionSpriteTicker<>(o -> {}));
        RenderSection a = Sections.section(regions, 0, 0, 0);
        RenderSection b = Sections.section(regions, 1, 0, 0);
        RenderSection bare = new RenderSection(a.getRegion(), 0, 0, 1);
        a.getRegion().addSection(bare);
        giveGeometry(a);
        manager.attachRenderSection(a);
        manager.attachRenderSection(b);
        manager.attachRenderSection(bare);
        assertThrows(IllegalStateException.class, () -> manager.attachRenderSection(a));
        assertTrue(manager.isNeedsUpdate());
        manager.updateVisibilityData(0, 0, 0, VisibilityEncoding.EVERYTHING);
        manager.updateVisibilityData(1, 0, 0, VisibilityEncoding.EVERYTHING);
        manager.updateVisibilityData(0, 0, 1, VisibilityEncoding.EVERYTHING);
        manager.updateVisibilityData(9, 9, 9, VisibilityEncoding.EVERYTHING);
        manager.startGraphUpdate(Sections.viewport(8, 8, 8), 1, regions.getRegionIdsLength(), 1000f, true, 8);
        assertFalse(manager.isNeedsUpdate());
        assertEquals(1, manager.getLastUpdatedFrame());
        assertTrue(manager.isSectionVisible(0, 0, 0));
        assertTrue(manager.isSectionVisible(1, 0, 0));
        assertFalse(manager.isSectionVisible(5, 5, 5));
        assertTrue(manager.getRenderLists().iterator().hasNext());
        assertNotNull(manager.getRebuildLists());
        RenderListManager.RenderListDebugStatistics stats = manager.getDebugStatistics();
        assertSame(stats, manager.getDebugStatistics());
        assertEquals(1, stats.renderPassCounts().getInt(Passes.TRANSLUCENT));
        assertTrue(stats.getSortingString().contains("DYNAMIC=1"));
        assertEquals("A: 0", manager.getTickerDebugString());
        manager.tickVisibleRenders();
        manager.setNeedsUpdate(true);
        manager.finishPreviousGraphUpdate();
        manager.detachRenderSection(b);
        assertThrows(IllegalStateException.class, () -> manager.detachRenderSection(b));
        manager.destroy();

        RenderListManager quiet = new RenderListManager(0, 4, false, null);
        assertEquals("", quiet.getTickerDebugString());
        quiet.tickVisibleRenders();
        assertFalse(quiet.isSectionVisible(0, 0, 0));
        quiet.startGraphUpdate(Sections.viewport(8, 8, 8), 1, regions.getRegionIdsLength(), 1000f, true, 8);
        assertEquals(0, quiet.getDebugStatistics().renderPassCounts().size());
        quiet.destroy();
    }

    @Test
    void asyncGraphUpdatesDeferMutations() {
        RenderListManager manager = new RenderListManager(0, 4, true, null);
        RenderSection a = Sections.section(regions, 0, 0, 0);
        manager.attachRenderSection(a);
        manager.startGraphUpdate(Sections.viewport(8, 8, 8), 1, regions.getRegionIdsLength(), 1000f, false, 8);
        assertThrows(IllegalStateException.class, () -> manager.startGraphUpdate(Sections.viewport(8, 8, 8), 2, regions.getRegionIdsLength(), 1000f, false, 8));
        assertThrows(IllegalStateException.class, () -> manager.attachRenderSection(Sections.section(regions, 2, 0, 0)));
        assertThrows(IllegalStateException.class, () -> manager.detachRenderSection(a));
        manager.updateVisibilityData(0, 0, 0, 5L);
        manager.finishPreviousGraphUpdate();
        assertTrue(manager.isSectionVisible(0, 0, 0));
        assertTrue(manager.isNeedsUpdate());
        manager.startGraphUpdate(Sections.viewport(8, 8, 8), 2, regions.getRegionIdsLength(), 1000f, false, 8);
        manager.destroy();
        manager.destroy();
    }
}
