package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class QuadUtilTest {
    @Test
    void axisAlignedNormalsMapToFacings() {
        assertEquals(ModelQuadFacing.POS_X, QuadUtil.findNormalFace(1, 0, 0));
        assertEquals(ModelQuadFacing.NEG_Y, QuadUtil.findNormalFace(0, -1, 0));
        assertEquals(ModelQuadFacing.POS_Z, QuadUtil.findNormalFace(0, 0, 1));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(0.7f, 0.7f, 0));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(0, 0, 0));
        assertEquals(ModelQuadFacing.UNASSIGNED, QuadUtil.findNormalFace(Float.NaN, 0, 0));
        assertEquals(ModelQuadFacing.NEG_Z, QuadUtil.findNormalFace(NormI8.pack(0, 0, -1)));
    }

    @Test
    void faceNormalOfUnitQuadPointsUp() {
        Vector3f out = new Vector3f();
        QuadUtil.faceNormal(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, out);
        assertEquals(0, out.x, 1e-6);
        assertEquals(1, Math.abs(out.y), 1e-6);
        assertEquals(0, out.z, 1e-6);
        QuadUtil.faceNormal(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, out);
        assertEquals(new Vector3f(), out);
    }

    @Test
    void packedNormalsAgreeAcrossRepresentations() {
        int packed = QuadUtil.packedFaceNormal(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1);
        assertEquals(0, NormI8.unpackX(packed), 1e-2);
        assertEquals(1, Math.abs(NormI8.unpackY(packed)), 1e-2);
        assertEquals(0, QuadUtil.packedFaceNormal(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        float[][] pos = {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
        for (int i = 0; i < 4; i++) {
            quad[i].x = pos[i][0];
            quad[i].y = pos[i][1];
            quad[i].z = pos[i][2];
        }
        assertEquals(packed, QuadUtil.calculateNormal(quad));

        ModelQuadView view = Mockito.mock(ModelQuadView.class);
        for (int i = 0; i < 4; i++) {
            Mockito.when(view.getX(i)).thenReturn(pos[i][0]);
            Mockito.when(view.getY(i)).thenReturn(pos[i][1]);
            Mockito.when(view.getZ(i)).thenReturn(pos[i][2]);
        }
        assertEquals(packed, QuadUtil.calculateNormal(view));
        new QuadUtil();
    }
}
