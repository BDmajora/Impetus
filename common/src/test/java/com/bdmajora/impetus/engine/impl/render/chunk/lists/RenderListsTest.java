package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderListsTest {
    private static final CancellationToken IN_FLIGHT = new CancellationToken() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelled() {}
    };

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

    private RenderSection section(int x, int y, int z) {
        return Sections.section(regions, x, y, z);
    }

    // One search over the sections, collected with the given cap on initial builds
    private VisibleChunkCollector collect(int initialBuildCap, RenderSection... sections) {
        Viewport viewport = Sections.viewport(8, 8, 8);
        SectionLattice lattice = Sections.lattice(viewport, sections);
        VisibleChunkCollector collector = new VisibleChunkCollector(lattice, 1, regions.getRegionIdsLength(), initialBuildCap, viewport.getBlockCoord());
        lattice.findVisible(collector, viewport, Sections.DISTANCE, regions.getRegionIdsLength(), false, false, 1);
        return collector;
    }

    @Test
    void renderListsBucketSectionsByWhatTheyHold() {
        RenderSection geometry = section(0, 0, 0);
        RenderSection sprites = section(1, 0, 0);
        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        mc.animatedSprites.add("flame");
        mc.culledBlockEntities.add(7);
        // Built data is always baked before it reaches a section, which turns the sprite set into the list the ticker reads
        mc.bake();
        sprites.setInfo(mc);
        ChunkRenderList list = new ChunkRenderList(geometry.getRegion());
        assertEquals("[]", list.toString());
        assertNull(list.sectionsWithGeometryIterator());
        assertNull(list.sectionsWithSpritesIterator());
        assertNull(list.sectionsWithEntitiesIterator());
        assertNull(list.sectionsNeedingDynamicSortIterator());
        list.add(geometry.getSectionIndex(), geometry.getVisualsServiceFlags());
        list.add(sprites.getSectionIndex(), sprites.getVisualsServiceFlags());
        list.add(5, 1 << RenderVisualsService.NEEDS_DYNAMIC_SORT);
        assertEquals(3, list.size());
        assertEquals(1, list.getSectionsWithGeometryCount());
        assertEquals(1, list.getSectionsWithSpritesCount());
        assertEquals(1, list.getSectionsWithEntitiesCount());
        assertEquals(geometry.getSectionIndex(), list.getSectionsWithGeometry()[0]);
        assertEquals("[(0, 0, 0)]", list.toString());
        assertEquals(sprites.getSectionIndex(), list.sectionsWithSpritesIterator().nextByteAsInt());
        assertEquals(sprites.getSectionIndex(), list.sectionsWithEntitiesIterator().nextByteAsInt());
        assertEquals(5, list.sectionsNeedingDynamicSortIterator().nextByteAsInt());
        assertSame(geometry.getRegion(), list.getRegion());
        ChunkRenderList twoWide = new ChunkRenderList(geometry.getRegion());
        twoWide.add(geometry.getSectionIndex(), geometry.getVisualsServiceFlags());
        twoWide.add(com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex.pack(1, 2, 3), 1);
        assertEquals("[(0, 0, 0), (1, 2, 3)]", twoWide.toString());
        ChunkRenderList full = new ChunkRenderList(geometry.getRegion());
        for (int i = 0; i < 256; i++) {
            full.add(i, 0);
        }
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> full.add(0, 0));

        List<String> active = new ArrayList<>();
        GenericSectionSpriteTicker<String> counting = new GenericSectionSpriteTicker<>(active::add);
        counting.onRenderListUpdated(List.of(list, new ChunkRenderList(geometry.getRegion())));
        counting.tickVisibleRenders();
        assertEquals(List.of("flame"), active);
        assertEquals("A: 1", counting.getDebugString());
        // An unchanged region is served from the cache; a change to it is picked up
        counting.onRenderListUpdated(List.of(list));
        assertEquals("A: 1", counting.getDebugString());
        geometry.getRegion().removeSection(sprites);
        counting.onRenderListUpdated(List.of(list));
        assertEquals("A: 0", counting.getDebugString());
        // Regions out of sight long enough are dropped from the cache
        for (int i = 0; i < 2048; i++) {
            counting.onRenderListUpdated(List.of());
        }
        assertEquals("A: 0", counting.getDebugString());
        SectionTicker bare = new SectionTicker() {
            @Override
            public void tickVisibleRenders() {}

            @Override
            public void onRenderListUpdated(List<ChunkRenderList> renderLists) {}
        };
        assertEquals("", bare.getDebugString());
    }

    @Test
    void theCollectorListsVisibleSectionsAndQueuesTheNearestBuilds() {
        RenderSection rebuild = section(0, 0, 0);
        RenderSection near = section(1, 0, 0);
        RenderSection building = section(2, 0, 0);
        RenderSection mid = section(4, 0, 0);
        RenderSection farther = section(5, 0, 0);
        RenderSection otherRegion = section(9, 0, 0);
        rebuild.setPendingUpdate(ChunkUpdateType.REBUILD);
        for (RenderSection initial : List.of(near, mid, farther, otherRegion)) {
            initial.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }
        // A section with a build already in flight is not queued again
        building.setPendingUpdate(ChunkUpdateType.SORT);
        building.setBuildCancellationToken(IN_FLIGHT);
        otherRegion.getRegion().createStorage(Passes.TRANSLUCENT, Passes.CONFIG);
        // The walk only travels through loaded sections, so the row has no gaps
        RenderSection[] all = {rebuild, near, building, section(3, 0, 0), mid, farther, section(6, 0, 0), section(7, 0, 0), section(8, 0, 0), otherRegion};

        VisibleChunkCollector capped = collect(1, all);
        assertThrows(IllegalStateException.class, capped::getRebuildLists);
        capped.finishRebuildLists();
        assertThrows(IllegalStateException.class, capped::finishRebuildLists);
        ChunkRebuildLists rebuilds = capped.getRebuildLists();
        assertFalse(rebuilds.isEmpty());
        // Only initial builds are capped; the rest of them wait for the next search
        assertTrue(rebuilds.hasAdditionalUpdates());
        assertEquals(List.of(rebuild), List.copyOf(rebuilds.byUpdateType().get(ChunkUpdateType.REBUILD)));
        assertEquals(List.of(near), List.copyOf(rebuilds.byUpdateType().get(ChunkUpdateType.INITIAL_BUILD)));
        assertEquals(4, rebuilds.getUpdateCount(ChunkUpdateType.INITIAL_BUILD));
        assertEquals(Map.of(ChunkUpdateType.INITIAL_BUILD, 3), rebuilds.queueOverflowCounts());
        assertEquals(0, rebuilds.getUpdateCount(ChunkUpdateType.SORT));

        // A looser cap sorts the whole candidate list instead of selecting from it
        VisibleChunkCollector loose = collect(2, all);
        loose.finishRebuildLists();
        assertEquals(List.of(near, mid), List.copyOf(loose.getRebuildLists().byUpdateType().get(ChunkUpdateType.INITIAL_BUILD)));
        VisibleChunkCollector unlimited = collect(64, all);
        unlimited.finishRebuildLists();
        assertFalse(unlimited.getRebuildLists().hasAdditionalUpdates());
        assertEquals(4, unlimited.getRebuildLists().byUpdateType().get(ChunkUpdateType.INITIAL_BUILD).size());

        // One list per region, in the order the walk reached them
        SortedRenderLists lists = capped.createRenderLists();
        assertEquals(2, lists.getNumRegions());
        assertEquals(2, capped.getSortedRenderLists().size());
        assertSame(rebuild.getRegion(), lists.iterator().next().getRegion());
        assertSame(otherRegion.getRegion(), lists.iterator(true).next().getRegion());
        assertTrue(lists.hasPass(Passes.TRANSLUCENT));
        assertFalse(lists.hasPass(Passes.SOLID));
        assertTrue(lists.hasSortedPass());
        assertEquals(1, lists.getPasses().size());

        assertTrue(ChunkRebuildLists.empty().isEmpty());
        assertNotSame(ChunkRebuildLists.empty().byUpdateType(), ChunkRebuildLists.empty().byUpdateType());
        assertFalse(new ChunkRebuildLists(ChunkRebuildLists.empty().byUpdateType(), true, Map.of()).isEmpty());
        assertFalse(SortedRenderLists.empty().iterator().hasNext());
        assertFalse(SortedRenderLists.empty().hasPass(Passes.SOLID));
        assertFalse(SortedRenderLists.empty().hasSortedPass());
        assertEquals(0, SortedRenderLists.empty().getNumRegions());
        ChunkRenderListIterable anything = new ChunkRenderListIterable() {
            @Override
            public Iterator<ChunkRenderList> iterator(boolean reverse) {
                return lists.iterator(reverse);
            }

            @Override
            public int getNumRegions() {
                return lists.getNumRegions();
            }
        };
        assertTrue(anything.hasPass(Passes.SOLID));
        assertTrue(anything.iterator().hasNext());
    }

    @Test
    void blockEntityDataIsVisitedForVisibleAndGlobalSections() {
        RenderSection holder = section(0, 0, 0);
        RenderSection plain = section(1, 0, 0);
        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        mc.globalBlockEntities.add(1);
        holder.setInfo(mc);
        List<MinecraftBuiltRenderSectionData<?, ?>> seen = new ArrayList<>();
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(Sections.renderLists(regions, Sections.viewport(8, 8, 8), holder, plain), seen::add);
        assertEquals(List.of(mc), seen);
        seen.clear();
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(List.of(holder, plain), seen::add);
        assertEquals(List.of(mc), seen);
        // A section removed from its region after the search is skipped
        VisibleChunkCollector collector = Sections.collect(regions, Sections.viewport(8, 8, 8), 1, holder);
        holder.getRegion().removeSection(holder);
        seen.clear();
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(collector.createRenderLists(), seen::add);
        assertTrue(seen.isEmpty());
    }
}
