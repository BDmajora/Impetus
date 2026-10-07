package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.SegmentedAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.testing.Sections;
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
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
    }

    // A mesh of quads quads, each a unit square at block (x, 0, 0) for x = 0..quads-1
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
        // Three unit quads span blocks 0..3 on x, so the box is three blocks wide
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

        // The support probe is cached, so it must be re-run on the sparse-friendly OS
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
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
        // A rebuild that outgrows the arena drops its own section too, and with it the region
        store.upload(0, 0, 0, SectionGeometry.from(mesh(50, ModelQuadFacing.POS_Y)));
        assertEquals(0, regions.getRegionCount());
        regions.delete();
        arena.delete();
        stream.delete();
    }
}
