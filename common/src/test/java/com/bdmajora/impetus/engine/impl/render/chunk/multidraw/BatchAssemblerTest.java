package com.bdmajora.impetus.engine.impl.render.chunk.multidraw;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferArena;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferSegment;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.DirectMultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.IndirectMultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.device.MultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class BatchAssemblerTest {
    private static final int FIRST = LocalSectionIndex.pack(0, 0, 0);
    private static final int SECOND = LocalSectionIndex.pack(1, 0, 0);
    private static final int STRIDE = Passes.SOLID.vertexType().getVertexFormat().getStride();

    private GLRenderDevice device;
    private CommandList commands;
    private RenderRegionManager regions;
    private RenderRegion region;
    private GlBufferArena vertices;
    private GlBufferArena indices;

    @BeforeEach
    void activate() {
        device = Devices.active();
        commands = device.createCommandList();
        regions = new RenderRegionManager(commands, Passes.CONFIG);
        region = regions.createForChunk(0, 0, 0);
        vertices = region.createResources(commands).getGeometryArena();
        indices = region.getResources().getOrCreateIndexArena(commands);
        ImpetusRuntimeOptions.multiDrawIndirect = false;
    }

    @AfterEach
    void deactivate() {
        ImpetusRuntimeOptions.multiDrawIndirect = false;
        regions.delete(commands);
        device.makeInactive();
    }

    private static GlBufferSegment upload(GlBufferArena arena, CommandList commands, int bytes) {
        PendingUpload upload = PendingUpload.of(new NativeBuffer(bytes));
        arena.upload(commands, new ArrayList<>(List.of(upload)));
        return upload.getResult();
    }

    // Four vertices in each facing, contiguous in facing order as the build buffers lay them out
    private static Map<ModelQuadFacing, VertexRange> quads(ModelQuadFacing... facings) {
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        int start = 0;
        for (ModelQuadFacing facing : facings) {
            ranges.put(facing, new VertexRange(start, 4));
            start += 4;
        }
        return ranges;
    }

    private int mesh(SectionRenderDataStorage storage, int section, boolean indexed, ModelQuadFacing... facings) {
        GlBufferSegment segment = upload(vertices, commands, facings.length * 4 * STRIDE);
        GlBufferSegment index = indexed ? upload(indices, commands, facings.length * 6 * 4) : null;
        storage.setMeshes(section, segment, index, quads(facings));
        return segment.getOffset();
    }

    private ChunkRenderList list(int... sections) {
        ChunkRenderList list = new ChunkRenderList(region);
        for (int section : sections) {
            list.add(section, 1);
        }
        return list;
    }

    private static int[] command(MultiDrawBatch batch, int i) {
        DirectMultiDrawBatch direct = (DirectMultiDrawBatch) batch;
        return new int[]{MemoryUtil.memGetInt(direct.pBaseVertex + i * 4L), MemoryUtil.memGetInt(direct.pElementCount + i * 4L), (int) MemoryUtil.memGetAddress(direct.pElementPointer + (long) i * 8)};
    }

    @Test
    void unsortedRegionsDrawOneCommandPerContiguousRunOfVisibleFacings() {
        SectionRenderDataStorage storage = region.createStorage(Passes.SOLID, Passes.CONFIG);
        int first = mesh(storage, FIRST, false, ModelQuadFacing.POS_X, ModelQuadFacing.POS_Z);
        int second = mesh(storage, SECOND, false, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Y, ModelQuadFacing.UNASSIGNED);
        ChunkRenderList sections = list(FIRST, SECOND);
        assertNull(BatchAssembler.fillRegion(region, storage, list(), new CameraTransform(8, 8, 8), Passes.SOLID, true));

        // Without culling each section is one command over all its vertices
        MultiDrawBatch all = BatchAssembler.fillRegion(region, storage, sections, new CameraTransform(8, 8, 8), Passes.SOLID, false);
        assertInstanceOf(DirectMultiDrawBatch.class, all);
        assertEquals(2, all.size());
        assertArrayEquals(new int[]{first, 12, 0}, command(all, 0));
        assertArrayEquals(new int[]{second, 18, 0}, command(all, 1));
        all.delete();

        // Far below, west and north every section sees the same faces, one run of them; a section with none of them draws nothing
        MultiDrawBatch below = BatchAssembler.fillRegion(region, storage, sections, new CameraTransform(-1000, -1000, -1000), Passes.SOLID, true);
        assertEquals(1, below.size());
        assertArrayEquals(new int[]{second + 4, 12, 0}, command(below, 0));
        below.delete();

        // Far east, below and south the shared mask splits into four runs; runs that meet are drawn as one command
        MultiDrawBatch split = BatchAssembler.fillRegion(region, storage, sections, new CameraTransform(1000, -1000, 1000), Passes.SOLID, true);
        assertEquals(2, split.size());
        assertArrayEquals(new int[]{first, 12, 0}, command(split, 0));
        assertArrayEquals(new int[]{second, 18, 0}, command(split, 1));
        split.delete();

        // From inside the region every section has its own mask
        MultiDrawBatch inside = BatchAssembler.fillRegion(region, storage, sections, new CameraTransform(8, 8, 8), Passes.SOLID, true);
        assertEquals(2, inside.size());
        assertArrayEquals(new int[]{first, 12, 0}, command(inside, 0));
        assertArrayEquals(new int[]{second + 4, 12, 0}, command(inside, 1));
        inside.delete();
    }

    @Test
    void sortedRegionsDrawEveryFacingFromItsOwnIndicesBackToFront() {
        SectionRenderDataStorage storage = region.createStorage(Passes.TRANSLUCENT, Passes.CONFIG);
        int first = mesh(storage, FIRST, true, ModelQuadFacing.UNASSIGNED);
        int second = mesh(storage, SECOND, true, ModelQuadFacing.POS_X, ModelQuadFacing.UNASSIGNED);
        long secondRow = storage.getDataPointer(SECOND);
        int secondIndices = com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe.Strategy.FULL.getIndexOffset(secondRow, 0);
        int firstIndices = com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe.Strategy.FULL.getIndexOffset(storage.getDataPointer(FIRST), 0);
        // Culling is never applied to a sorted pass, whatever the caller asks
        MultiDrawBatch batch = BatchAssembler.fillRegion(region, storage, list(FIRST, SECOND), new CameraTransform(-1000, -1000, -1000), Passes.TRANSLUCENT, true);
        assertEquals(3, batch.size());
        assertArrayEquals(new int[]{second, 6, secondIndices}, command(batch, 0));
        assertArrayEquals(new int[]{second + 4, 6, secondIndices + 24}, command(batch, 1));
        assertArrayEquals(new int[]{first, 6, firstIndices}, command(batch, 2));
        batch.delete();
    }

    @Test
    void cachedBatchesStayValidUntilTheCameraOrTheSectionsChange() {
        SectionRenderDataStorage storage = region.createStorage(Passes.SOLID, Passes.CONFIG);
        mesh(storage, FIRST, false, ModelQuadFacing.POS_Y);
        mesh(storage, SECOND, false, ModelQuadFacing.POS_Y);
        ChunkRenderList sections = list(FIRST, SECOND);
        AtomicInteger tessellations = new AtomicInteger();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        BatchAssembler.TessellationProvider provider = (commandList, r, pass) -> {
            tessellations.incrementAndGet();
            return tessellation;
        };
        CameraTransform camera = new CameraTransform(8, 8, 8);
        CachedBatch culled = BatchAssembler.createCachedBatch(region, storage, sections, camera, Passes.SOLID, true, commands, provider);
        assertSame(tessellation, culled.getTessellation());
        assertEquals(1, tessellations.get());
        assertEquals(2, culled.getBatch().size());
        byte[] order = sections.getSectionsWithGeometry();
        assertTrue(culled.isValidFor(order, 2, 8, 8, 8));
        // The culling masks hold until the camera crosses a face plane, two blocks inside the section
        assertTrue(culled.isValidFor(order, 2, 13, 8, 8));
        assertFalse(culled.isValidFor(order, 2, 14, 8, 8));
        assertFalse(culled.isValidFor(order, 2, 8, -3, 8));
        assertFalse(culled.isValidFor(order, 1, 8, 8, 8));
        assertFalse(culled.isValidFor(list(SECOND, FIRST).getSectionsWithGeometry(), 2, 8, 8, 8));
        culled.delete();

        // Without culling the batch never depends on the camera
        CachedBatch unculled = BatchAssembler.createCachedBatch(region, storage, sections, camera, Passes.SOLID, false, commands, provider);
        assertTrue(unculled.isValidFor(order, 2, Integer.MIN_VALUE, 0, Integer.MAX_VALUE - 1));
        unculled.delete();
        // Nothing to draw: no batch and no tessellation
        CachedBatch nothing = BatchAssembler.createCachedBatch(region, storage, list(), camera, Passes.SOLID, true, commands, provider);
        assertNull(nothing.getBatch());
        assertNull(nothing.getTessellation());
        nothing.delete();
        // Everything culled from below: an empty batch is uploaded but needs no tessellation
        CachedBatch hidden = BatchAssembler.createCachedBatch(region, storage, sections, new CameraTransform(8, -100, 8), Passes.SOLID, true, commands, provider);
        assertTrue(hidden.getBatch().isEmpty());
        assertNull(hidden.getTessellation());
        hidden.delete();
        assertEquals(2, tessellations.get());

        // With the option on and a capable device, commands live in their own indirect buffer
        ImpetusRuntimeOptions.multiDrawIndirect = true;
        CachedBatch indirect = BatchAssembler.createCachedBatch(region, storage, sections, camera, Passes.SOLID, true, commands, provider);
        assertInstanceOf(IndirectMultiDrawBatch.class, indirect.getBatch());
        indirect.delete();
    }

    @Test
    void facesAndTheirValidityIntervalsFollowTheCamera() {
        // From inside a section every face is visible, with the unassigned facing always on
        assertEquals(ModelQuadFacing.ALL, BatchAssembler.getVisibleFaces(8, 8, 8, 0, 0, 0));
        int fromEast = BatchAssembler.getVisibleFaces(100, 8, 8, 0, 0, 0);
        assertEquals(0, fromEast & (1 << ModelQuadFacing.NEG_X.ordinal()));
        assertNotEquals(0, fromEast & (1 << ModelQuadFacing.POS_X.ordinal()));
        assertNotEquals(0, BatchAssembler.getVisibleFaces(-1000, -1000, -1000, 0, 0, 0) & (1 << ModelQuadFacing.UNASSIGNED.ordinal()));

        // The interval runs from the last face plane at or below the camera to the next one above it
        long inside = BatchAssembler.cameraValidityInterval(8, 0, 8);
        assertEquals(-2, (int) inside);
        assertEquals(14, (int) (inside >>> 32));
        long beyond = BatchAssembler.cameraValidityInterval(500, 0, 8);
        assertEquals(131, (int) beyond);
        assertEquals(Integer.MAX_VALUE, (int) (beyond >>> 32));
        long before = BatchAssembler.cameraValidityInterval(-500, 0, 8);
        assertEquals(Integer.MIN_VALUE, (int) before);
        assertEquals(-2, (int) (before >>> 32));
    }
}
