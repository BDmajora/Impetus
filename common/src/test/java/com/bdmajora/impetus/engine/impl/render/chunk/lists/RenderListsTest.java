package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionNode;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderListsTest {
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

    @Test
    void renderListsBucketSectionsByWhatTheyHold() {
        RenderSection geometry = Sections.section(regions, 0, 0, 0);
        RenderSection sprites = Sections.section(regions, 1, 0, 0);
        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        mc.animatedSprites.add("flame");
        mc.culledBlockEntities.add(7);
        // Built data is always baked before it reaches a section, which turns the sprite set into the list the ticker reads
        mc.bake();
        sprites.setInfo(mc);
        RenderSection empty = new RenderSection(geometry.getRegion(), 2, 0, 0);
        ChunkRenderList list = new ChunkRenderList(geometry.getRegion());
        assertEquals("[]", list.toString());
        assertNull(list.sectionsWithGeometryIterator(false));
        assertNull(list.sectionsWithSpritesIterator());
        assertNull(list.sectionsWithEntitiesIterator());
        list.add(geometry);
        list.add(sprites);
        list.add(empty);
        assertEquals(3, list.size());
        assertEquals(1, list.getSectionsWithGeometryCount());
        assertEquals(1, list.getSectionsWithSpritesCount());
        assertEquals(1, list.getSectionsWithEntitiesCount());
        assertEquals("[(0, 0, 0)]", list.toString());
        assertEquals(sprites.getSectionIndex(), list.sectionsWithSpritesIterator().nextByteAsInt());
        assertEquals(sprites.getSectionIndex(), list.sectionsWithEntitiesIterator().nextByteAsInt());
        assertSame(geometry.getRegion(), list.getRegion());
        ChunkRenderList full = new ChunkRenderList(geometry.getRegion());
        for (int i = 0; i < 256; i++) {
            full.add(empty);
        }
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> full.add(empty));

        GenericSectionSpriteTicker<String> ticker = new GenericSectionSpriteTicker<>(new ArrayList<String>()::add);
        List<String> active = new ArrayList<>();
        GenericSectionSpriteTicker<String> counting = new GenericSectionSpriteTicker<>(active::add);
        counting.onRenderListUpdated(List.of(list, new ChunkRenderList(geometry.getRegion())));
        counting.tickVisibleRenders();
        assertEquals(List.of("flame"), active);
        assertEquals("A: 1", counting.getDebugString());
        RenderSection detached = Sections.section(regions, 3, 0, 0);
        ChunkRenderList stale = new ChunkRenderList(detached.getRegion());
        stale.add(sprites);
        detached.getRegion().removeSection(detached);
        geometry.getRegion().removeSection(sprites);
        counting.onRenderListUpdated(List.of(stale, list));
        assertEquals("A: 0", counting.getDebugString());
        ticker.tickVisibleRenders();
        SectionTicker bare = new SectionTicker() {
            @Override
            public void tickVisibleRenders() {}

            @Override
            public void onRenderListUpdated(List<ChunkRenderList> renderLists) {}
        };
        assertEquals("", bare.getDebugString());
    }

    @Test
    void collectorBuildsSortedAndRebuildLists() {
        RenderSection a = Sections.section(regions, 0, 0, 0);
        RenderSection b = Sections.section(regions, 9, 0, 0);
        RenderSection hidden = Sections.section(regions, 1, 0, 0);
        RenderSection building = Sections.section(regions, 2, 0, 0);
        a.setPendingUpdate(ChunkUpdateType.REBUILD);
        b.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        hidden.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        building.setPendingUpdate(ChunkUpdateType.SORT);
        building.setBuildCancellationToken(new CancellationToken() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void setCancelled() {}
        });
        b.getRegion().createStorage(Passes.TRANSLUCENT);
        VisibleChunkCollector collector = new VisibleChunkCollector(1, regions.getRegionIdsLength(), 1);
        collector.visit(new OcclusionNode(a), true);
        collector.visit(new OcclusionNode(b), true);
        collector.visit(new OcclusionNode(hidden), true);
        collector.visit(new OcclusionNode(building), true);
        collector.visit(new OcclusionNode(new RenderSection(a.getRegion(), 3, 0, 0)), false);
        assertEquals(2, collector.getCollectedRenderLists().size());
        SortedRenderLists lists = collector.createRenderLists();
        assertTrue(lists.hasPass(Passes.TRANSLUCENT));
        assertFalse(lists.hasPass(Passes.SOLID));
        assertTrue(lists.hasSortedPass());
        assertEquals(1, lists.getPasses().size());
        assertTrue(lists.iterator().hasNext());
        assertSame(b.getRegion(), lists.iterator(true).next().getRegion());
        ChunkRebuildLists rebuilds = collector.getRebuildLists();
        assertFalse(rebuilds.isEmpty());
        assertTrue(rebuilds.hasAdditionalUpdates());
        assertEquals(1, rebuilds.getUpdateCount(ChunkUpdateType.REBUILD));
        assertEquals(2, rebuilds.getUpdateCount(ChunkUpdateType.INITIAL_BUILD));
        assertEquals(0, rebuilds.getUpdateCount(ChunkUpdateType.SORT));
        assertTrue(ChunkRebuildLists.EMPTY.isEmpty());
        assertFalse(new ChunkRebuildLists(ChunkRebuildLists.EMPTY.byUpdateType(), true, java.util.Map.of()).isEmpty());
        VisibleChunkCollector quiet = new VisibleChunkCollector(2, regions.getRegionIdsLength(), 8);
        assertTrue(quiet.getRebuildLists().isEmpty());
        assertFalse(quiet.createRenderLists().hasSortedPass());
        assertFalse(SortedRenderLists.empty().iterator().hasNext());
        assertFalse(SortedRenderLists.empty().hasPass(Passes.SOLID));
        ChunkRenderListIterable anything = reverse -> lists.iterator(reverse);
        assertTrue(anything.hasPass(Passes.SOLID));
        assertTrue(anything.iterator().hasNext());

        List<MinecraftBuiltRenderSectionData<?, ?>> seen = new ArrayList<>();
        MinecraftBuiltRenderSectionData<String, Integer> mc = new MinecraftBuiltRenderSectionData<>();
        mc.globalBlockEntities.add(1);
        a.setInfo(mc);
        VisibleChunkCollector again = new VisibleChunkCollector(3, regions.getRegionIdsLength(), 8);
        again.visit(new OcclusionNode(a), true);
        again.visit(new OcclusionNode(b), true);
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(again.createRenderLists(), seen::add);
        assertEquals(List.of(mc), seen);
    }
}
