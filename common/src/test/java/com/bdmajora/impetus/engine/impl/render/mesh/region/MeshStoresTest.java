package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.SegmentedAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

public class MeshStoresTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        Statics.set(MeshShaderSupport.class, "supported", null);
    }

    @AfterEach
    void forget() {
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "detected", OsKind.LINUX);
    }

    // Returns a mesh of unit quads.
    public static BuiltSectionMeshParts mesh(int quads, ModelQuadFacing facing) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * MeshChunkVertex.STRIDE);
        long ptr = MemoryUtil.memAddress(vertices.getDirectBuffer());
        var encoder = MeshChunkVertex.INSTANCE.createEncoder();
        var vertex = new com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder.Vertex();
        for (int q = 0; q < quads; q++) {
            float[][] corners = {{q, 0, 0}, {q, 0, 1}, {q + 1, 0, 1}, {q + 1, 0, 0}};
            for (float[] c : corners) {
                vertex.x = c[0];
                vertex.y = c[1];
                vertex.z = c[2];
                ptr = encoder.write(ptr, com.bdmajora.testing.Passes.SOLID_MATERIAL, vertex, 0);
            }
        }
        return new BuiltSectionMeshParts(vertices, null, null, Map.of(facing, new VertexRange(0, quads * 4)));
    }

    @Test
    void sectionGeometryReshapesBuildOutput() {
        SectionGeometry geometry = SectionGeometry.from(mesh(3, ModelQuadFacing.POS_Y));
        assertEquals(3, geometry.quadCount());
        assertEquals(3, geometry.quadsPerFacing()[ModelQuadFacing.POS_Y.ordinal()]);
        assertEquals(0, geometry.baseQuad());
        assertEquals(0, geometry.minX());
        // Checks the box.
        assertEquals(3, geometry.sizeX());
        assertEquals(0, geometry.sizeY());
        assertEquals(1, geometry.sizeZ());
        assertNull(SectionGeometry.from(new BuiltSectionMeshParts(new NativeBuffer(0), null, null, Map.of())));
        assertNull(SectionGeometry.from(new BuiltSectionMeshParts(new NativeBuffer(64), null, null, Map.of(ModelQuadFacing.POS_X, new VertexRange(0, 0)))));
        geometry.delete();
        assertThrows(IllegalStateException.class, () -> geometry.geometry().getDirectBuffer());
    }

    @Test
    void quadArenaAllocatesDenseOrSparse() {
        QuadArena dense = new QuadArena(16 * 4 * 16 * 100, 16);
        assertNotNull(dense.getBuffer());
        int first = dense.alloc(10);
        assertTrue(first > 0);
        assertTrue(dense.canReuse(first, 10));
        assertFalse(dense.canReuse(first, 11));
        assertEquals(11 * 64, dense.getUsedBytes());
        assertEquals(16 * 4 * 16 * 100, dense.getResidentBytes());
        UploadStream stream = new UploadStream(4096);
        assertTrue(dense.beginUpload(stream, first) != 0);
        dense.free(first);
        assertEquals(64, dense.getUsedBytes());
        assertEquals((int) SegmentedAllocator.OUT_OF_SPACE, dense.alloc(100000));
        dense.delete();
        stream.delete();

        // Reruns the support probe.
        Statics.set(OsKind.class, "detected", OsKind.WINDOWS);
        Statics.set(MeshShaderSupport.class, "supported", null);
        QuadArena sparse = new QuadArena(0, 16);
        int quads = sparse.alloc(100);
        assertEquals(1, quads);
        assertTrue(sparse.getResidentBytes() > 0);
        sparse.free(quads);
        sparse.delete();
    }

    @Test
    void storesUploadRemoveAndCompactSections() {
        UploadStream stream = new UploadStream(1 << 20);
        MeshRegionStore regions = new MeshRegionStore(8, stream);
        QuadArena arena = new QuadArena(1 << 20, MeshChunkVertex.STRIDE);
        MeshSectionStore store = new MeshSectionStore(regions, arena, stream);
        assertSame(regions, store.getRegions());
        assertSame(arena, store.getArena());
        assertEquals(8, regions.getMaxRegions());
        assertEquals(0, regions.getRegionCount());
        assertTrue(regions.getRegionBufferAddress() != 0);
        assertTrue(regions.getSectionBufferAddress() != 0);

        store.upload(1, 2, 3, SectionGeometry.from(mesh(2, ModelQuadFacing.POS_Y)));
        store.upload(2, 2, 3, SectionGeometry.from(mesh(4, ModelQuadFacing.UNASSIGNED)));
        store.upload(9, 2, 3, SectionGeometry.from(mesh(1, ModelQuadFacing.NEG_X)));
        assertEquals(2, regions.getRegionCount());
        assertEquals(2, regions.getMaxRegionIndex());
        assertTrue(regions.regionExists(0));
        assertFalse(regions.regionExists(5));
        int extra = regions.allocateSection(3, 2, 3);
        assertTrue(regions.getSectionIndex(extra) >= 0);
        assertThrows(IllegalStateException.class, () -> regions.allocateSection(3, 2, 3));
        store.upload(1, 2, 3, SectionGeometry.from(mesh(2, ModelQuadFacing.POS_Y)));
        store.upload(1, 2, 3, SectionGeometry.from(mesh(5, ModelQuadFacing.POS_Y)));
        store.upload(1, 2, 3, null);
        store.upload(4, 2, 3, SectionGeometry.from(mesh(1, ModelQuadFacing.POS_Z)));
        store.commit();
        stream.commit();
        assertTrue(regions.isRegionVisible(Sections.viewport(0, 0, 0), 0));
        assertFalse(regions.isRegionVisible(Sections.viewport(0, 0, 0), 5));
        assertTrue(regions.distanceTo(0, 0, 0, 0) > 0);
        long key = regions.getRegionKey(1);
        assertEquals(1, com.bdmajora.impetus.engine.impl.util.PositionUtil.unpackSectionX(key));
        store.removeRegion(1);
        store.removeRegion(1);
        store.removeRegion(7);
        assertEquals(1, regions.getRegionCount());
        store.commit();
        regions.removeSection(1 << 8);
        regions.removeSection(7 | (0 << 8));
        assertThrows(IllegalStateException.class, () -> regions.beginSectionUpdate(7));
        // Removing a middle entry compacts the region's dense array
        regions.removeSection(extra);
        store.remove(2, 2, 3);
        store.remove(4, 2, 3);
        store.remove(2, 2, 3);
        store.upload(3, 2, 3, null);
        store.commit();
        assertEquals(0, regions.getRegionCount());
        store.upload(1, 2, 3, SectionGeometry.from(mesh(2, ModelQuadFacing.POS_Y)));
        store.commit();
        regions.delete();
        arena.delete();
        stream.delete();
    }

    // A private field of the store or one of its regions
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    @Test
    void residentTranslucentOrderUsesTheSectionIndicesAndCanBeReplacedOnItsOwn() throws Exception {
        var stream = new UploadStream(1 << 20);
        var regions = new MeshRegionStore(4, stream);
        var arena = new QuadArena(1 << 20, MeshChunkVertex.STRIDE);
        var store = new MeshSectionStore(regions, arena, stream);
        var primitive = QuadPrimitiveType.TRIANGULATED;
        var mesh = mesh(3, ModelQuadFacing.UNASSIGNED);
        var geometry = SectionGeometryPacker.pack(Map.of(Passes.MESH_TRANSLUCENT, mesh));
        var indices = new NativeBuffer(primitive.getIndexBufferSize(3));
        try {
            primitive.generateSortedIndexBuffer(indices.getDirectBuffer(), 3, new SortState.Static(new int[]{2, 0, 1}), 0, 0, 0);
            store.upload(0, 0, 0, geometry, primitive, indices);
            var staging = (com.bdmajora.impetus.engine.impl.render.mesh.gl.MappedUploadBuffer) field(stream, "staging");
            long header = regions.beginSectionUpdate(0);
            int base = MemoryUtil.memGetInt(header + 12);
            int vertexOffset = base * arena.bytesPerQuad();
            int orderOffset = vertexOffset - 3 * Short.BYTES;
            int buffer = arena.getBuffer().getId();
            assertTrue((MemoryUtil.memGetInt(header + 4) & (1 << 26)) != 0);
            stream.commit();
            long orderSource = Mockito.mockingDetails(TestGl.gl()).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("glCopyNamedBufferSubData"))
                    .filter(call -> call.<Integer>getArgument(1) == buffer && call.<Long>getArgument(3) == orderOffset)
                    .findFirst().orElseThrow().getArgument(2);
            assertEquals(2, MemoryUtil.memGetShort(staging.getClientAddress() + orderSource));
            assertEquals(0, MemoryUtil.memGetShort(staging.getClientAddress() + orderSource + 2));
            assertEquals(1, MemoryUtil.memGetShort(staging.getClientAddress() + orderSource + 4));
            Mockito.verify(TestGl.gl()).glCopyNamedBufferSubData(Mockito.eq(staging.getId()), Mockito.eq(buffer),
                    Mockito.anyLong(), Mockito.eq((long) vertexOffset), Mockito.eq((long) geometry.geometry().getLength()));

            Mockito.clearInvocations(TestGl.gl());
            primitive.generateSimpleIndexBuffer(indices.getDirectBuffer(), 3);
            store.uploadTranslucentOrder(0, 0, 0, primitive, indices);
            stream.commit();
            Mockito.verify(TestGl.gl()).glCopyNamedBufferSubData(Mockito.eq(staging.getId()), Mockito.eq(buffer),
                    Mockito.anyLong(), Mockito.eq((long) orderOffset), Mockito.eq(6L));
            Mockito.verify(TestGl.gl(), Mockito.never()).glCopyNamedBufferSubData(Mockito.eq(staging.getId()), Mockito.eq(buffer),
                    Mockito.anyLong(), Mockito.eq((long) vertexOffset), Mockito.anyLong());
            assertEquals(base, MemoryUtil.memGetInt(header + 12));

            store.upload(0, 0, 0, geometry);
            assertEquals(0, MemoryUtil.memGetInt(header + 4) & (1 << 26));
        } finally {
            indices.free();
            geometry.delete();
            mesh.free();
            regions.delete();
            arena.delete();
            stream.delete();
        }
    }

    @Test
    void sectionsKeepThePlacementTimeTheFadeInCountsFrom() throws ReflectiveOperationException {
        UploadStream stream = new UploadStream(1 << 20);
        MeshRegionStore regions = new MeshRegionStore(4, stream);
        assertTrue(regions.getSectionTimeBufferAddress() != 0);
        int before = regions.currentTimeMs();
        regions.allocateSection(0, 0, 0);
        int second = regions.allocateSection(1, 0, 0);
        int third = regions.allocateSection(2, 0, 0);
        long times = (long) field(((Object[]) field(regions, "regions"))[0], "sectionTimes");
        int stamped = MemoryUtil.memGetInt(times);
        assertTrue(stamped >= before && stamped <= regions.currentTimeMs());

        // Checks the kept time.
        MemoryUtil.memPutInt(times + 8, 12345);
        regions.beginSectionUpdate(third);
        assertEquals(12345, MemoryUtil.memGetInt(times + 8));
        // Removing a middle section moves the last one's time along with its header
        regions.removeSection(second);
        assertEquals(1, regions.getSectionIndex(third));
        assertEquals(12345, MemoryUtil.memGetInt(times + 4));
        regions.commit();
        stream.commit();
        regions.delete();
        stream.delete();
    }

    @Test
    void aFullArenaDropsTheSection() {
        UploadStream stream = new UploadStream(1 << 16);
        MeshRegionStore regions = new MeshRegionStore(4, stream);
        QuadArena arena = new QuadArena(4 * MeshChunkVertex.STRIDE * 4, MeshChunkVertex.STRIDE);
        MeshSectionStore store = new MeshSectionStore(regions, arena, stream);
        store.upload(0, 0, 0, SectionGeometry.from(mesh(2, ModelQuadFacing.POS_Y)));
        store.upload(0, 0, 0, SectionGeometry.from(mesh(3, ModelQuadFacing.POS_Y)));
        // A new section that does not fit is dropped while the existing one stays
        store.upload(1, 0, 0, SectionGeometry.from(mesh(50, ModelQuadFacing.POS_Y)));
        assertEquals(1, regions.getRegionCount());
        // Checks an outgrown arena.
        store.upload(0, 0, 0, SectionGeometry.from(mesh(50, ModelQuadFacing.POS_Y)));
        assertEquals(0, regions.getRegionCount());
        regions.delete();
        arena.delete();
        stream.delete();
    }
}
