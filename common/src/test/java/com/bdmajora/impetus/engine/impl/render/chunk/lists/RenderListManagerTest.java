package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Quads;
import com.bdmajora.testing.Sections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenderListManagerTest {
    private static final Viewport CAMERA = Sections.viewport(8, 8, 8);

    private GLRenderDevice device;
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

    // Uploaded translucent geometry with a tree sort, so the section counts towards the pass and sorting statistics
    private void giveGeometry(RenderSection section) {
        var commands = device.createCommandList();
        var resources = section.getRegion().createResources(commands);
        PendingUpload upload = PendingUpload.of(new NativeBuffer(20 * 4));
        resources.getGeometryArena().upload(commands, new ArrayList<>(List.of(upload)));
        section.getRegion().createStorage(Passes.TRANSLUCENT, Passes.CONFIG).setMeshes(section.getSectionIndex(), upload.getResult(), null, Map.of(ModelQuadFacing.UNASSIGNED, new VertexRange(0, 4)));
        PartitionTree tree = (PartitionTree) Quads.record(Quads.straddling(0)).getSortState();
        section.setTranslucencySortStates(Map.of(Passes.TRANSLUCENT, tree), tree.debugIndex());
    }

    @Test
    void synchronousSearchesProduceListsAndStatistics() {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.NONE, false, false);
        RenderListManager manager = new RenderListManager(graph, false, AsyncOcclusionMode.NONE, new GenericSectionSpriteTicker<>(o -> {}));
        RenderSection a = Sections.section(regions, 0, 0, 0);
        RenderSection b = Sections.section(regions, 1, 0, 0);
        RenderSection bare = new RenderSection(a.getRegion(), 0, 0, 1);
        a.getRegion().addSection(bare);
        giveGeometry(a);
        graph.attachRenderSection(a);
        graph.attachRenderSection(b);
        graph.attachRenderSection(bare);
        assertThrows(IllegalStateException.class, () -> graph.attachRenderSection(a));
        assertTrue(manager.isNeedsUpdate());
        assertFalse(manager.isSectionVisible(0, 0, 0));
        // Nothing is installed before the first search places the window, so there is nothing to re-search for
        AtomicInteger changes = new AtomicInteger();
        graph.updateSectionMetadata(0, 0, 1, PackedSectionMetadata.withVisibilityData(bare.getPackedMetadata(), VisibilityEncoding.EVERYTHING), changes::incrementAndGet);
        assertEquals(0, changes.get());

        manager.startGraphUpdate(CAMERA, 1, regions.getRegionIdsLength(), 1000f, true, 8);
        // Once installed, a change to the section's visibility asks for another search, and a repeat does not
        graph.updateSectionMetadata(0, 0, 1, PackedSectionMetadata.withVisibilityData(bare.getPackedMetadata(), VisibilityEncoding.EVERYTHING), changes::incrementAndGet);
        graph.updateSectionMetadata(0, 0, 1, PackedSectionMetadata.withVisibilityData(bare.getPackedMetadata(), VisibilityEncoding.EVERYTHING), changes::incrementAndGet);
        graph.updateSectionMetadata(9, 9, 9, 0L, changes::incrementAndGet);
        assertEquals(1, changes.get());
        assertFalse(manager.isNeedsUpdate());
        assertEquals(1, manager.getLastUpdatedFrame());
        assertTrue(manager.isSectionVisible(0, 0, 0));
        assertTrue(manager.isSectionVisible(1, 0, 0));
        assertTrue(manager.isSectionVisible(0, 0, 1));
        assertFalse(manager.isSectionVisible(5, 5, 5));
        assertTrue(manager.getRenderLists().iterator().hasNext());
        assertTrue(manager.getRebuildLists().isEmpty());
        RenderListManager.RenderListDebugStatistics stats = manager.getDebugStatistics();
        assertSame(stats, manager.getDebugStatistics());
        assertEquals(1, stats.renderPassCounts().getInt(Passes.TRANSLUCENT));
        assertEquals("Sorting: NONE=0, STATIC=0, TREE=1, DYNAMIC=0", stats.getSortingString());
        assertEquals("A: 0", manager.getTickerDebugString());
        manager.tickVisibleRenders();
        assertThrows(IllegalStateException.class, () -> manager.startShadowGraphUpdate(CAMERA, 2, 1, 1000f, null, 8));
        manager.setNeedsUpdate(true);
        manager.finishPreviousGraphUpdate();
        graph.detachRenderSection(b);
        assertThrows(IllegalStateException.class, () -> graph.detachRenderSection(b));
        manager.startGraphUpdate(CAMERA, 2, regions.getRegionIdsLength(), 1000f, true, 8);
        assertFalse(manager.isSectionVisible(1, 0, 0));
        manager.destroy();

        RenderListManager quiet = new RenderListManager(graph, false, AsyncOcclusionMode.NONE, null);
        assertEquals("", quiet.getTickerDebugString());
        quiet.tickVisibleRenders();
        quiet.startGraphUpdate(CAMERA, 3, regions.getRegionIdsLength(), 1000f, true, 8);
        assertEquals(1, quiet.getDebugStatistics().renderPassCounts().size());
        quiet.destroy();
        graph.destroy();
        assertThrows(IllegalStateException.class, graph::onSearchJoined);
    }

    @Test
    void theShadowManagerSearchesTheSameLatticeOnItsOwnState() {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.NONE, true, false);
        RenderListManager terrain = new RenderListManager(graph, false, AsyncOcclusionMode.NONE, null);
        RenderListManager shadow = new RenderListManager(graph, true, AsyncOcclusionMode.NONE, null);
        graph.attachRenderSection(Sections.section(regions, 0, 0, 0));
        graph.attachRenderSection(Sections.section(regions, 0, 1, 0));
        assertThrows(IllegalStateException.class, () -> shadow.startGraphUpdate(CAMERA, 1, 1, 1000f, true, 8));
        terrain.startGraphUpdate(CAMERA, 1, regions.getRegionIdsLength(), 1000f, true, 8);
        shadow.startShadowGraphUpdate(CAMERA, 1, regions.getRegionIdsLength(), 1000f, new org.joml.Vector3f(0, 1, 0), 8);
        assertTrue(shadow.isSectionVisible(0, 1, 0));
        shadow.startShadowGraphUpdate(CAMERA, 2, regions.getRegionIdsLength(), 1000f, null, 8);
        assertTrue(shadow.isSectionVisible(0, 0, 0));
        assertEquals(1, terrain.getLastUpdatedFrame());
        assertEquals(2, shadow.getLastUpdatedFrame());
        terrain.destroy();
        shadow.destroy();
        graph.destroy();
    }

    @Test
    void asyncSearchesDeferLatticeChangesUntilJoined() {
        SectionGraph graph = new SectionGraph(0, 4, AsyncOcclusionMode.EVERYTHING, false, false);
        RenderListManager manager = new RenderListManager(graph, false, AsyncOcclusionMode.EVERYTHING, null);
        RenderSection a = Sections.section(regions, 0, 0, 0);
        graph.attachRenderSection(a);
        manager.startGraphUpdate(CAMERA, 1, regions.getRegionIdsLength(), 1000f, false, 8);
        assertThrows(IllegalStateException.class, () -> manager.startGraphUpdate(CAMERA, 2, regions.getRegionIdsLength(), 1000f, false, 8));
        assertThrows(IllegalStateException.class, () -> graph.attachRenderSection(Sections.section(regions, 2, 0, 0)));
        assertThrows(IllegalStateException.class, () -> graph.detachRenderSection(a));
        AtomicInteger changes = new AtomicInteger();
        graph.updateSectionMetadata(0, 0, 0, PackedSectionMetadata.withVisibilityData(a.getPackedMetadata(), VisibilityEncoding.NULL), changes::incrementAndGet);
        assertEquals(0, changes.get());
        manager.finishPreviousGraphUpdate();
        assertEquals(1, changes.get());
        assertTrue(manager.isSectionVisible(0, 0, 0));
        manager.startGraphUpdate(CAMERA, 2, regions.getRegionIdsLength(), 1000f, false, 8);
        manager.destroy();
        manager.destroy();
        graph.destroy();

        // Only the shadow pass searches off the render thread in this mode
        SectionGraph shadowOnly = new SectionGraph(0, 4, AsyncOcclusionMode.ONLY_SHADOW, true, false);
        RenderListManager inline = new RenderListManager(shadowOnly, false, AsyncOcclusionMode.ONLY_SHADOW, null);
        shadowOnly.attachRenderSection(Sections.section(regions, 0, 2, 0));
        inline.startGraphUpdate(Sections.viewport(8, 40, 8), 1, regions.getRegionIdsLength(), 1000f, false, 8);
        assertTrue(inline.isSectionVisible(0, 2, 0));
        shadowOnly.destroy();
    }
}
