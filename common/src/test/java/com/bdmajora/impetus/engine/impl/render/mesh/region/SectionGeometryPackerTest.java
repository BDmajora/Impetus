package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshVertexLayout;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class SectionGeometryPackerTest {
    // x of a packed quad's first vertex, in blocks
    private static float firstX(SectionGeometry geometry, int quad) {
        long ptr = MemoryUtil.memAddress(geometry.geometry().getDirectBuffer()) + (long) quad * 4 * MeshChunkVertex.STRIDE;
        return MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(ptr));
    }

    @Test
    void translucentQuadsComeFirstFarthestFirstThenOpaqueByFacing() {
        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new LinkedHashMap<>();
        meshes.put(Passes.MESH_SOLID, MeshStoresTest.mesh(2, ModelQuadFacing.POS_Y));
        meshes.put(Passes.MESH_CUTOUT, MeshStoresTest.mesh(1, ModelQuadFacing.NEG_X));
        meshes.put(Passes.MESH_TRANSLUCENT, MeshStoresTest.mesh(3, ModelQuadFacing.UNASSIGNED));

        // Camera well past x = 3, so the quad at x = 0 is the farthest
        SectionGeometry geometry = SectionGeometryPacker.pack(meshes, 10f, 0f, 0.5f);
        assertEquals(6, geometry.quadCount());
        assertEquals(3, geometry.baseQuad());
        assertEquals(2, geometry.quadsPerFacing()[ModelQuadFacing.POS_Y.ordinal()]);
        assertEquals(1, geometry.quadsPerFacing()[ModelQuadFacing.NEG_X.ordinal()]);
        assertEquals(0, geometry.quadsPerFacing()[ModelQuadFacing.UNASSIGNED.ordinal()]);
        assertEquals(0f, firstX(geometry, 0), 0.01f);
        assertEquals(1f, firstX(geometry, 1), 0.01f);
        assertEquals(2f, firstX(geometry, 2), 0.01f);
        // Opaque groups follow in facing order: +Y before -X
        assertEquals(0f, firstX(geometry, 3), 0.01f);
        assertEquals(1f, firstX(geometry, 4), 0.01f);
        assertEquals(0f, firstX(geometry, 5), 0.01f);
        assertEquals(3, geometry.sizeX());
        geometry.delete();
    }

    @Test
    void emptyBuildsPackToNothing() {
        assertNull(SectionGeometryPacker.pack(Map.of(), 0f, 0f, 0f));
        var emptyRange = new BuiltSectionMeshParts(new NativeBuffer(64), null, null, Map.of(ModelQuadFacing.POS_X, new VertexRange(0, 0)));
        assertNull(SectionGeometryPacker.pack(Map.of(Passes.MESH_SOLID, emptyRange), 0f, 0f, 0f));
    }

    @Test
    void passesBuiltInAnotherLayoutAreRefused() {
        var mesh = MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y);
        assertThrows(IllegalStateException.class, () -> SectionGeometryPacker.pack(Map.of(Passes.SOLID, mesh), 0f, 0f, 0f));
    }

    @Test
    void groupsPastSixteenBitsAreCappedRatherThanWrapped() {
        int quads = 0x10000;
        var opaque = new BuiltSectionMeshParts(new NativeBuffer(quads * 4 * MeshChunkVertex.STRIDE), null, null,
                Map.of(ModelQuadFacing.POS_Z, new VertexRange(0, quads * 4)));
        var translucent = new BuiltSectionMeshParts(new NativeBuffer(quads * 4 * MeshChunkVertex.STRIDE), null, null,
                Map.of(ModelQuadFacing.UNASSIGNED, new VertexRange(0, quads * 4)));
        SectionGeometry geometry = SectionGeometryPacker.pack(Map.of(Passes.MESH_SOLID, opaque, Passes.MESH_TRANSLUCENT, translucent), 0f, 0f, 0f);
        assertEquals(0xFFFF * 2, geometry.quadCount());
        assertEquals((short) 0xFFFF, geometry.baseQuad());
        assertEquals((short) 0xFFFF, geometry.quadsPerFacing()[ModelQuadFacing.POS_Z.ordinal()]);
        geometry.delete();
    }

    // A 32-byte mesh layout like the shader pack one: MeshChunkVertex's sixteen bytes, then sixteen of extras
    private static final MeshVertexLayout WIDE = new MeshVertexLayout() {
        private final GlVertexFormat format = GlVertexFormat.builder(32)
                .addElement("a_A", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
                .addElement("a_B", 16, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
                .build();

        @Override
        public float getPositionScale() {
            return 1f;
        }

        @Override
        public float getPositionOffset() {
            return 0f;
        }

        @Override
        public float getTextureScale() {
            return 1f;
        }

        @Override
        public GlVertexFormat getVertexFormat() {
            return this.format;
        }

        @Override
        public ChunkVertexEncoder createEncoder() {
            ChunkVertexEncoder base = MeshChunkVertex.INSTANCE.createEncoder();
            return (ptr, material, vertex, section) -> {
                base.write(ptr, material, vertex, section);
                MemoryUtil.memSet(ptr + 16, 0x5A, 16);
                return ptr + 32;
            };
        }
    };

    private static BuiltSectionMeshParts wideMesh(int quads, ModelQuadFacing facing) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * 32);
        long ptr = MemoryUtil.memAddress(vertices.getDirectBuffer());
        ChunkVertexEncoder encoder = WIDE.createEncoder();
        ChunkVertexEncoder.Vertex vertex = new ChunkVertexEncoder.Vertex();
        for (int q = 0; q < quads; q++) {
            for (float[] c : new float[][]{{q, 2, 0}, {q, 2, 1}, {q + 1, 2, 1}, {q + 1, 2, 0}}) {
                vertex.x = c[0];
                vertex.y = c[1];
                vertex.z = c[2];
                ptr = encoder.write(ptr, Passes.SOLID_MATERIAL, vertex, 0);
            }
        }
        return new BuiltSectionMeshParts(vertices, null, null, Map.of(facing, new VertexRange(0, quads * 4)));
    }

    @Test
    void widerMeshLayoutsPackAtTheirOwnStride() {
        TerrainRenderPass solid = Passes.pass("wide_solid", false, false, WIDE);
        TerrainRenderPass translucent = Passes.pass("wide_translucent", false, true, WIDE);
        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new LinkedHashMap<>();
        meshes.put(solid, wideMesh(2, ModelQuadFacing.POS_Y));
        meshes.put(translucent, wideMesh(2, ModelQuadFacing.UNASSIGNED));

        SectionGeometry geometry = SectionGeometryPacker.packForMeshBackend(meshes, 10f, 2f, 0.5f);
        assertEquals(4, geometry.quadCount());
        assertEquals(4 * 4 * 32, geometry.geometry().getLength());
        assertEquals(2, geometry.minY());
        assertEquals(0, geometry.sizeY());
        // The translucent quads still sort farthest first, read at the 32-byte stride, and carry their extras along
        long base = MemoryUtil.memAddress(geometry.geometry().getDirectBuffer());
        assertEquals(0f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(base)), 0.01f);
        assertEquals(1f, MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(base + 4 * 32)), 0.01f);
        assertEquals((byte) 0x5A, MemoryUtil.memGetByte(base + 16));
        geometry.delete();

        // Passes that disagree on the stride cannot share a blob
        Map<TerrainRenderPass, BuiltSectionMeshParts> mixed = new LinkedHashMap<>();
        mixed.put(solid, wideMesh(1, ModelQuadFacing.POS_Y));
        mixed.put(Passes.MESH_CUTOUT, MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y));
        assertThrows(IllegalStateException.class, () -> SectionGeometryPacker.pack(mixed, 0f, 0f, 0f));
    }

    @Test
    void buildsForTheRasterRendererAreNotPacked() {
        assertNull(SectionGeometryPacker.packForMeshBackend(Map.of(Passes.SOLID, MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y)), 0f, 0f, 0f));
        assertNull(SectionGeometryPacker.packForMeshBackend(Map.of(), 0f, 0f, 0f));
        SectionGeometry packed = SectionGeometryPacker.packForMeshBackend(Map.of(Passes.MESH_SOLID, MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y)), 0f, 0f, 0f);
        assertEquals(1, packed.quadCount());
        packed.delete();
    }

    @Test
    void quadDistanceIsMeasuredFromItsCentre() {
        var mesh = MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y);
        long quad = MemoryUtil.memAddress(mesh.vertexBuffer().getDirectBuffer());
        // The unit quad's centre is (0.5, 0, 0.5)
        assertEquals(0f, SectionGeometryPacker.distanceSquared(quad, MeshChunkVertex.STRIDE, 0.5f, 0f, 0.5f), 0.01f);
        assertEquals(4f, SectionGeometryPacker.distanceSquared(quad, MeshChunkVertex.STRIDE, 0.5f, 2f, 0.5f), 0.01f);
    }
}
