package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
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

    @Test
    void quadDistanceIsMeasuredFromItsCentre() {
        var mesh = MeshStoresTest.mesh(1, ModelQuadFacing.POS_Y);
        long quad = MemoryUtil.memAddress(mesh.vertexBuffer().getDirectBuffer());
        // The unit quad's centre is (0.5, 0, 0.5)
        assertEquals(0f, SectionGeometryPacker.distanceSquared(quad, 0.5f, 0f, 0.5f), 0.01f);
        assertEquals(4f, SectionGeometryPacker.distanceSquared(quad, 0.5f, 2f, 0.5f), 0.01f);
    }
}
