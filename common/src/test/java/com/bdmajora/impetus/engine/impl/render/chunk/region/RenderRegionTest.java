package com.bdmajora.impetus.engine.impl.render.chunk.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobResult;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.TestGl;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenderRegionTest {
    private static final int STRIDE = Passes.SOLID.vertexType().getVertexFormat().getStride();

    private GLRenderDevice device;
    private CommandList commands;
    private RenderRegionManager manager;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        manager = new RenderRegionManager(commands, Passes.CONFIG);
    }

    @AfterEach
    void deactivate() {
        manager.delete(commands);
        device.makeInactive();
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = true;
    }

    private static Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes(TerrainRenderPass pass, boolean indexed) {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> map = new Reference2ReferenceOpenHashMap<>();
        map.put(pass, new BuiltSectionMeshParts(new NativeBuffer(8 * STRIDE), indexed ? new NativeBuffer(12 * 4) : null, null, Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 8))));
        return map;
    }

    private static ChunkJobResult.Success<ChunkTaskOutput> success(ChunkTaskOutput output) {
        return new ChunkJobResult.Success<>(output, 1L);
    }

    @Test
    void regionGeometryAndSectionBookkeeping() {
        RenderRegion region = manager.createForChunk(9, 5, -3);
        assertEquals(8, region.getChunkX());
        assertEquals(4, region.getChunkY());
        assertEquals(-8, region.getChunkZ());
        assertEquals(128, region.getOriginX());
        assertEquals(64, region.getOriginY());
        assertEquals(-128, region.getOriginZ());
        assertEquals(192, region.getCenterX());
        assertEquals(96, region.getCenterY());
        assertEquals(-64, region.getCenterZ());
        assertSame(region, manager.createForChunk(10, 6, -2));
        assertEquals(0, region.getId());
        assertEquals(PositionUtil.packSection(1, 1, -1), RenderRegion.key(1, 1, -1));
        assertTrue(region.isEmpty());
        int revision = region.getDataRevision();
        RenderSection section = new RenderSection(region, 9, 5, -3);
        region.addSection(section);
        assertThrows(IllegalStateException.class, () -> region.addSection(section));
        assertFalse(region.isEmpty());
        assertSame(section, region.getSection(section.getSectionIndex()));
        region.onSectionDataChanged();
        assertEquals(revision + 2, region.getDataRevision());

        // Every pass shares the region's arena, so a pass whose vertex size does not divide its stride is refused
        assertNull(region.getStorage(Passes.SOLID));
        assertFalse(region.hasSectionsInPass(Passes.SOLID));
        assertNotNull(region.createStorage(Passes.SOLID, Passes.CONFIG));
        assertSame(region.getStorage(Passes.SOLID), region.createStorage(Passes.SOLID, Passes.CONFIG));
        assertThrows(IllegalStateException.class, () -> region.createStorage(Passes.WIDE, Passes.CONFIG));
        assertEquals(1, region.getPassSetUpdateCount());
        assertTrue(region.getPasses().contains(Passes.SOLID));
        region.removeMeshes(section.getSectionIndex());
        region.removeEmptyStorages();
        assertEquals(2, region.getPassSetUpdateCount());
        region.removeEmptyStorages();
        region.removeMeshes(0);
        assertEquals(2, region.getPassSetUpdateCount());

        RenderSection wrong = new RenderSection(region, 9, 5, -3);
        assertThrows(IllegalStateException.class, () -> region.removeSection(wrong));
        region.removeSection(section);
        assertThrows(IllegalStateException.class, () -> region.removeSection(section));
        assertTrue(region.isEmpty());
        region.updateSectionLoadTime(section);
        assertTrue(region.getNewestSectionLoadTime() > 0);
        assertEquals(region.getNewestSectionLoadTime(), region.getSectionLoadTimes()[section.getSectionIndex()]);
        assertNull(region.getResources());
    }

    @Test
    void deviceResourcesManageArenasAndTessellations() {
        RenderRegion region = manager.createForChunk(0, 0, 0);
        RenderRegion.DeviceResources resources = region.createResources(commands);
        assertSame(resources, region.createResources(commands));
        assertSame(resources, region.getResources());
        assertNotNull(resources.getVertexBuffer());
        assertThrows(IllegalStateException.class, resources::getIndexBuffer);
        assertNull(resources.getIndexArena());
        assertNotNull(resources.getOrCreateIndexArena(commands));
        assertSame(resources.getIndexArena(), resources.getOrCreateIndexArena(commands));
        assertNotNull(resources.getIndexBuffer());
        assertNotNull(resources.getGeometryArena());

        TerrainRenderPass.TessellationKey key = Passes.SOLID.tessellationKey();
        GlTessellation first = Mockito.mock(GlTessellation.class);
        GlTessellation second = Mockito.mock(GlTessellation.class);
        assertNull(resources.getTessellation(key));
        resources.updateTessellation(commands, key, first);
        resources.updateTessellation(commands, key, second);
        Mockito.verify(first).delete(commands);
        assertSame(second, resources.getTessellation(key));
        // A resized arena invalidates every tessellation bound to it
        region.createStorage(Passes.SOLID, Passes.CONFIG);
        region.refresh(commands);
        Mockito.verify(second).delete(commands);
        assertNull(resources.getTessellation(key));

        // An index arena with live indices stays, an empty one goes
        PendingUpload indices = PendingUpload.of(new NativeBuffer(24));
        resources.getIndexArena().upload(commands, new ArrayList<>(List.of(indices)));
        assertFalse(resources.deleteIndexArenaIfPossible(commands));
        indices.getResult().delete();
        assertTrue(resources.deleteIndexArenaIfPossible(commands));
        assertNull(resources.getIndexArena());
        assertFalse(resources.deleteIndexArenaIfPossible(commands));

        // The region drops an empty index arena, and then the whole resources once the geometry is gone too
        PendingUpload geometry = PendingUpload.of(new NativeBuffer(4 * STRIDE));
        resources.getGeometryArena().upload(commands, new ArrayList<>(List.of(geometry)));
        resources.getOrCreateIndexArena(commands);
        assertFalse(resources.shouldDelete());
        region.update(commands);
        assertSame(resources, region.getResources());
        assertNull(resources.getIndexArena());
        region.update(commands);
        assertSame(resources, region.getResources());
        geometry.getResult().delete();
        assertTrue(resources.shouldDelete());
        region.update(commands);
        assertNull(region.getResources());
        region.update(commands);
        region.createResources(commands).getOrCreateIndexArena(commands);
        region.delete(commands);
        assertNull(region.getResources());
    }

    @Test
    void managerUploadsBuildAndSortResults() {
        AtomicInteger graphUpdates = new AtomicInteger();
        RenderRegion region = manager.createForChunk(0, 0, 0);
        RenderSection section = new RenderSection(region, 0, 0, 0);
        region.addSection(section);
        assertEquals(0, manager.getUploadDurationEstimator().getLastUploadBytes());
        BuiltRenderSectionData info = new BuiltRenderSectionData();
        ChunkBuildOutput build = new ChunkBuildOutput(section, info, meshes(Passes.TRANSLUCENT, true), 1);
        // A new pass in the region changes what the search must list, so the graph is marked
        manager.uploadMeshes(commands, List.of(success(build)), graphUpdates::incrementAndGet);
        assertEquals(1, graphUpdates.get());
        assertNotNull(region.getStorage(Passes.TRANSLUCENT));
        assertEquals(8L * STRIDE + 48, manager.getUploadDurationEstimator().getLastUploadBytes());
        assertTrue(manager.getUploadDurationEstimator().estimateUploadDuration(1000) >= 0);

        Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> sorted = new Reference2ReferenceOpenHashMap<>();
        sorted.put(Passes.TRANSLUCENT, new ChunkSortOutput.SortedMesh(new NativeBuffer(12 * 4)));
        manager.uploadMeshes(commands, List.of(success(new ChunkSortOutput(section, 2, sorted))), graphUpdates::incrementAndGet);
        assertEquals(1, graphUpdates.get());

        // A plain rebuild of a solid pass replaces the section's meshes and adds the pass
        ChunkBuildOutput solid = new ChunkBuildOutput(section, null, meshes(Passes.SOLID, false), 3);
        manager.uploadMeshes(commands, List.of(success(solid)), graphUpdates::incrementAndGet);
        assertEquals(2, graphUpdates.get());
        assertNull(region.getStorage(Passes.TRANSLUCENT));
        manager.uploadMeshes(commands, List.of(), graphUpdates::incrementAndGet);
        manager.uploadMeshes(commands, List.of(success(new ChunkBuildOutput(section, null, new Reference2ReferenceOpenHashMap<>(), 4))), graphUpdates::incrementAndGet);
        ChunkTaskOutput odd = new ChunkTaskOutput(section, 0) {};
        odd.delete();
        assertThrows(IllegalStateException.class, () -> manager.uploadMeshes(commands, List.of(success(odd)), graphUpdates::incrementAndGet));
        assertEquals(1, manager.getLoadedRegions().size());
        assertEquals(1, manager.getRegionIdsLength());
        assertNotNull(manager.getStagingBuffer());
        manager.update();
        region.removeSection(section);
        manager.update();
        assertTrue(manager.getLoadedRegions().isEmpty());
        assertEquals(0, manager.createForChunk(0, 0, 0).getId());
        assertEquals(1, manager.createForChunk(100, 0, 0).getId());

        new ChunkBuildOutput(section, null, meshes(Passes.SOLID, true), 1).delete();
        Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> unused = new Reference2ReferenceOpenHashMap<>();
        unused.put(Passes.SOLID, new ChunkSortOutput.SortedMesh(new NativeBuffer(4)));
        new ChunkSortOutput(section, 1, unused).delete();
        assertEquals(1L, success(build).executionTimeNanos());
        // Drivers without persistent mapping, or a user who turned it off, get the copying staging buffer
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = false;
        new RenderRegionManager(commands, Passes.CONFIG).delete(commands);
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = true;
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 4)).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        new RenderRegionManager(commands, Passes.CONFIG).delete(commands);
    }
}
