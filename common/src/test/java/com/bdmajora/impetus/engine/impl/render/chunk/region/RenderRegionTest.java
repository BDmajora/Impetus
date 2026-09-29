package com.bdmajora.impetus.engine.impl.render.chunk.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
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
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenderRegionTest {
    private GLRenderDevice device;
    private CommandList commands;
    private RenderRegionManager manager;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        // Fences answer as signalled so the staging ring reclaims on flip
        Mockito.doAnswer(inv -> {
            inv.<java.nio.IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(com.bdmajora.testing.TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        manager = new RenderRegionManager(commands);
    }

    @AfterEach
    void deactivate() {
        manager.delete(commands);
        device.makeInactive();
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = true;
    }

    private static Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes(TerrainRenderPass pass, boolean indexed) {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> map = new Reference2ReferenceOpenHashMap<>();
        map.put(pass, new BuiltSectionMeshParts(new NativeBuffer(8 * 20), indexed ? new NativeBuffer(12 * 4) : null, null, Map.of(ModelQuadFacing.POS_Y, new VertexRange(0, 8))));
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
        assertEquals(PositionUtil(1, 1, -1), RenderRegion.key(1, 1, -1));
        assertTrue(region.isEmpty());
        RenderSection section = new RenderSection(region, 9, 5, -3);
        region.addSection(section);
        assertThrows(IllegalStateException.class, () -> region.addSection(section));
        assertFalse(region.isEmpty());
        assertSame(section, region.getSection(section.getSectionIndex()));
        assertNull(region.getStorage(Passes.SOLID));
        assertFalse(region.hasSectionsInPass(Passes.SOLID));
        assertNotNull(region.createStorage(Passes.SOLID));
        assertSame(region.getStorage(Passes.SOLID), region.createStorage(Passes.SOLID));
        assertEquals(1, region.getPassSetUpdateCount());
        assertTrue(region.getPasses().contains(Passes.SOLID));
        region.removeMeshes(section.getSectionIndex());
        region.removeEmptyStorages();
        assertEquals(2, region.getPassSetUpdateCount());
        region.removeEmptyStorages();
        region.removeMeshes(0);
        RenderSection wrong = new RenderSection(region, 9, 5, -3);
        assertThrows(IllegalStateException.class, () -> region.removeSection(wrong));
        region.removeSection(section);
        assertThrows(IllegalStateException.class, () -> region.removeSection(section));
        assertTrue(region.isEmpty());
        region.updateSectionLoadTime(section);
        assertTrue(region.getNewestSectionLoadTime() > 0);
        assertNull(region.getResources(ChunkMeshFormats.COMPACT.getVertexFormat()));
        assertTrue(region.getAllResources().isEmpty());
    }

    private static long PositionUtil(int x, int y, int z) {
        return com.bdmajora.impetus.engine.impl.util.PositionUtil.packSection(x, y, z);
    }

    @Test
    void deviceResourcesManageArenasAndTessellations() {
        RenderRegion region = manager.createForChunk(0, 0, 0);
        RenderRegion.DeviceResources resources = region.createResources(ChunkMeshFormats.COMPACT.getVertexFormat(), commands);
        assertSame(resources, region.createResources(ChunkMeshFormats.COMPACT.getVertexFormat(), commands));
        assertSame(resources, region.getResources(ChunkMeshFormats.COMPACT.getVertexFormat()));
        assertEquals(1, region.getAllResources().size());
        assertNotNull(resources.getVertexBuffer());
        assertThrows(IllegalStateException.class, resources::getIndexBuffer);
        assertNull(resources.getIndexArena());
        assertNotNull(resources.getOrCreateIndexArena(commands));
        assertSame(resources.getIndexArena(), resources.getOrCreateIndexArena(commands));
        assertNotNull(resources.getIndexBuffer());
        assertNotNull(resources.getGeometryArena());
        GlTessellation plain = Mockito.mock(GlTessellation.class);
        GlTessellation indexed = Mockito.mock(GlTessellation.class);
        resources.updateTessellation(commands, plain);
        resources.updateTessellation(commands, plain);
        Mockito.verify(plain).delete(commands);
        resources.updateIndexedTessellation(commands, indexed);
        resources.updateIndexedTessellation(commands, indexed);
        assertSame(plain, resources.getTessellation());
        assertSame(indexed, resources.getIndexedTessellation());
        region.refresh(commands);
        assertNull(resources.getTessellation());
        assertNull(resources.getIndexedTessellation());
        resources.deleteTessellations(commands);
        assertTrue(resources.shouldDelete());
        resources.deleteIndexArenaIfPossible(commands);
        assertNull(resources.getIndexArena());
        resources.deleteIndexArenaIfPossible(commands);
        region.update(commands);
        assertTrue(region.getAllResources().isEmpty());
        assertTrue(resources.isDeleted());
        RenderRegion.DeviceResources again = region.createResources(ChunkMeshFormats.VANILLA_LIKE.getVertexFormat(), commands);
        again.getOrCreateIndexArena(commands);
        region.delete(commands);
        assertTrue(again.isDeleted());
    }

    @Test
    void managerUploadsBuildAndSortResults() {
        AtomicInteger graphUpdates = new AtomicInteger();
        RenderRegion region = manager.createForChunk(0, 0, 0);
        RenderSection section = new RenderSection(region, 0, 0, 0);
        region.addSection(section);
        BuiltRenderSectionData info = new BuiltRenderSectionData();
        ChunkBuildOutput build = new ChunkBuildOutput(section, info, meshes(Passes.TRANSLUCENT, true), 1);
        manager.uploadMeshes(commands, List.of(success(build)), graphUpdates::incrementAndGet);
        assertEquals(1, graphUpdates.get());
        assertNotNull(region.getStorage(Passes.TRANSLUCENT));
        assertTrue(manager.getUploadDurationEstimator().estimateUploadDuration(1000) >= 0);

        Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> sorted = new Reference2ReferenceOpenHashMap<>();
        sorted.put(Passes.TRANSLUCENT, new ChunkSortOutput.SortedMesh(new NativeBuffer(12 * 4)));
        manager.uploadMeshes(commands, List.of(success(new ChunkSortOutput(section, 1, sorted))), graphUpdates::incrementAndGet);
        assertEquals(1, graphUpdates.get());
        Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> orphan = new Reference2ReferenceOpenHashMap<>();
        orphan.put(Passes.CUTOUT, new ChunkSortOutput.SortedMesh(new NativeBuffer(4)));
        manager.uploadMeshes(commands, List.of(success(new ChunkSortOutput(section, 1, orphan))), graphUpdates::incrementAndGet);

        ChunkBuildOutput solid = new ChunkBuildOutput(section, null, meshes(Passes.SOLID, false), 1);
        manager.uploadMeshes(commands, List.of(success(solid)), graphUpdates::incrementAndGet);
        manager.uploadMeshes(commands, List.of(), graphUpdates::incrementAndGet);
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

        new ChunkBuildOutput(section, null, meshes(Passes.SOLID, true), 1).delete();
        new ChunkSortOutput(section, 1, sortedFor(Passes.SOLID)).delete();
        ChunkJobResult.Failure<ChunkTaskOutput> runtime = new ChunkJobResult.Failure<>(new IllegalStateException("boom"));
        assertThrows(IllegalStateException.class, runtime::abort);
        ChunkJobResult.Failure<ChunkTaskOutput> checked = new ChunkJobResult.Failure<>(new Exception("checked"));
        assertThrows(RuntimeException.class, checked::abort);
        assertThrows(NullPointerException.class, () -> new ChunkJobResult.Failure<>(null));
        assertEquals(1L, success(build).executionTimeNanos());
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = false;
        new RenderRegionManager(commands).delete(commands);
    }

    private static Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> sortedFor(TerrainRenderPass pass) {
        Reference2ReferenceMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> map = new Reference2ReferenceOpenHashMap<>();
        map.put(pass, new ChunkSortOutput.SortedMesh(new NativeBuffer(4)));
        return map;
    }
}
