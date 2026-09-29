package com.bdmajora.impetus.engine.impl.model.quad.properties;

import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ModelQuadPropertiesTest {
    // A full unit face in Minecraft's canonical vertex order, at the far or near side of the axis
    private static ModelQuadView face(ModelQuadFacing facing, int color) {
        float[][] v = switch (facing) {
            case NEG_Y -> new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            case POS_Y -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case NEG_Z -> new float[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
            case POS_Z -> new float[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
            case NEG_X -> new float[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            case POS_X -> new float[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
            default -> new float[][]{{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        };
        return quad(v, color);
    }

    private static ModelQuadView quad(float[][] v, int color) {
        ModelQuadView quad = Mockito.mock(ModelQuadView.class);
        for (int i = 0; i < 4; i++) {
            Mockito.when(quad.getX(i)).thenReturn(v[i][0]);
            Mockito.when(quad.getY(i)).thenReturn(v[i][1]);
            Mockito.when(quad.getZ(i)).thenReturn(v[i][2]);
            Mockito.when(quad.getColor(i)).thenReturn(color);
        }
        return quad;
    }

    @Test
    void fullFacesAreAlignedAndParallelOnEveryAxis() {
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            int flags = ModelQuadFlags.getQuadFlags(face(facing, 0xFFFFFFFF), facing);
            assertTrue(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_PARALLEL), facing.name());
            assertTrue(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_ALIGNED), facing.name());
            assertFalse(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_PARTIAL), facing.name());
            assertTrue(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_POPULATED));
        }
    }

    @Test
    void wrongOrderPartialDegenerateAndTranslucentQuads() {
        float[][] reversed = {{1, 0, 1}, {1, 0, 0}, {0, 0, 0}, {0, 0, 1}};
        int flags = ModelQuadFlags.getQuadFlags(quad(reversed, 0xFFFFFFFF), ModelQuadFacing.NEG_Y, ModelQuadFlags.IS_TRUSTED_SPRITE);
        assertTrue(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_PARTIAL));
        assertTrue(ModelQuadFlags.contains(flags, ModelQuadFlags.IS_PASS_OPTIMIZABLE));

        float[][] small = {{0.2f, 0, 0.8f}, {0.2f, 0, 0.2f}, {0.8f, 0, 0.2f}, {0.8f, 0, 0.8f}};
        assertTrue(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(small, 0xFFFFFFFF), ModelQuadFacing.NEG_Y), ModelQuadFlags.IS_PARTIAL));
        float[][] smallX = {{1, 0.2f, 0.8f}, {1, 0.2f, 0.2f}, {1, 0.8f, 0.2f}, {1, 0.8f, 0.8f}};
        assertTrue(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(smallX, 0xFFFFFFFF), ModelQuadFacing.POS_X), ModelQuadFlags.IS_PARTIAL));
        float[][] smallZ = {{0.2f, 0.8f, 1}, {0.2f, 0.2f, 1}, {0.8f, 0.2f, 1}, {0.8f, 0.8f, 1}};
        assertTrue(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(smallZ, 0xFFFFFFFF), ModelQuadFacing.POS_Z), ModelQuadFlags.IS_PARTIAL));

        float[][] degenerate = {{0, 0, 1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 1}};
        assertTrue(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(degenerate, 0xFFFFFFFF), ModelQuadFacing.NEG_Y), ModelQuadFlags.IS_PARTIAL));

        float[][] raised = {{0, 0.5f, 1}, {0, 0.5f, 0}, {1, 0.5f, 0}, {1, 0.5f, 1}};
        int raisedFlags = ModelQuadFlags.getQuadFlags(quad(raised, 0x80FFFFFF), ModelQuadFacing.NEG_Y, ModelQuadFlags.IS_TRUSTED_SPRITE);
        assertTrue(ModelQuadFlags.contains(raisedFlags, ModelQuadFlags.IS_PARALLEL));
        assertFalse(ModelQuadFlags.contains(raisedFlags, ModelQuadFlags.IS_ALIGNED));
        assertFalse(ModelQuadFlags.contains(raisedFlags, ModelQuadFlags.IS_PASS_OPTIMIZABLE));
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            assertFalse(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(raised, -1), facing), ModelQuadFlags.IS_ALIGNED), facing.name());
        }
        float[][] slanted = {{0, 0, 1}, {0, 1, 0}, {1, 1, 0}, {1, 0, 1}};
        assertFalse(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(quad(slanted, -1), ModelQuadFacing.NEG_Y), ModelQuadFlags.IS_PARALLEL));
        // UNASSIGNED has no axis, so the axis switch trips before its own guard does
        assertThrows(NullPointerException.class, () -> ModelQuadFlags.getQuadFlags(quad(slanted, -1), ModelQuadFacing.UNASSIGNED));
        new ModelQuadFlags();
    }

    @Test
    void bakedQuadsUseTheirOwnVertexCount() {
        BakedQuadView baked = Mockito.mock(BakedQuadView.class);
        Mockito.when(baked.getVerticesCount()).thenReturn(3);
        float[][] v = {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
        for (int i = 0; i < 4; i++) {
            Mockito.when(baked.getX(i)).thenReturn(v[i][0]);
            Mockito.when(baked.getY(i)).thenReturn(v[i][1]);
            Mockito.when(baked.getZ(i)).thenReturn(v[i][2]);
            Mockito.when(baked.getColor(i)).thenReturn(-1);
        }
        assertTrue(ModelQuadFlags.contains(ModelQuadFlags.getQuadFlags(baked, ModelQuadFacing.NEG_Y), ModelQuadFlags.IS_ALIGNED));
        assertSame(baked, BakedQuadView.of(baked));
        assertEquals(1, BakedQuadView.ofList(java.util.List.of(baked)).size());
    }

    @Test
    void orientationAndWinding() {
        assertEquals(1, ModelQuadOrientation.FLIP.getVertexIndex(0));
        assertEquals(0, ModelQuadOrientation.NORMAL.getVertexIndex(0));
        assertEquals(ModelQuadOrientation.NORMAL, ModelQuadOrientation.orientByBrightness(new float[]{1, 0, 1, 0}, new int[4]));
        assertEquals(ModelQuadOrientation.FLIP, ModelQuadOrientation.orientByBrightness(new float[]{0, 1, 0, 1}, new int[4]));
        assertEquals(ModelQuadOrientation.NORMAL, ModelQuadOrientation.orientByBrightness(new float[4], new int[]{0, 1, 0, 1}));
        assertEquals(ModelQuadOrientation.FLIP, ModelQuadOrientation.orientByBrightness(new float[4], new int[]{1, 0, 1, 0}));
        assertArrayEquals(new int[]{0, 1, 2, 2, 3, 0}, ModelQuadWinding.CLOCKWISE.getIndices());
        assertArrayEquals(new int[]{0, 3, 2, 1, 0, 2}, ModelQuadWinding.COUNTERCLOCKWISE.getIndices());
    }
}
