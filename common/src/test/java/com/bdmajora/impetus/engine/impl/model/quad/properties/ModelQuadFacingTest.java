package com.bdmajora.impetus.engine.impl.model.quad.properties;

import com.bdmajora.impetus.engine.api.util.NormI8;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelQuadFacingTest {
    @Test
    void oppositesPairUpAndUnassignedIsItsOwn() {
        assertEquals(ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X.getOpposite());
        assertEquals(ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X.getOpposite());
        assertEquals(ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y.getOpposite());
        assertEquals(ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y.getOpposite());
        assertEquals(ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z.getOpposite());
        assertEquals(ModelQuadFacing.POS_Z, ModelQuadFacing.NEG_Z.getOpposite());
        assertEquals(ModelQuadFacing.UNASSIGNED, ModelQuadFacing.UNASSIGNED.getOpposite());
        assertTrue(ModelQuadFacing.POS_X.isDirection());
        assertFalse(ModelQuadFacing.UNASSIGNED.isDirection());
    }

    @Test
    void stepsAxesAndNormals() {
        assertEquals(1, ModelQuadFacing.POS_X.getStepX());
        assertEquals(-1, ModelQuadFacing.NEG_Y.getStepY());
        assertEquals(1, ModelQuadFacing.POS_Z.getStepZ());
        assertEquals(ModelQuadFacing.Axis.Y, ModelQuadFacing.POS_Y.getAxis());
        assertNull(ModelQuadFacing.UNASSIGNED.getAxis());
        assertEquals(NormI8.pack(0, 0, -1), ModelQuadFacing.NEG_Z.getPackedNormal());
        assertEquals(6, ModelQuadFacing.DIRECTIONS.length);
        assertEquals(7, ModelQuadFacing.COUNT);
        assertEquals(127, ModelQuadFacing.ALL);
        assertEquals(ModelQuadFacing.POS_X, ModelQuadFacing.Axis.X.getFacing(true));
        assertEquals(ModelQuadFacing.NEG_X, ModelQuadFacing.Axis.X.getFacing(false));
        assertEquals(ModelQuadFacing.POS_Y, ModelQuadFacing.Axis.Y.getFacing(true));
        assertEquals(ModelQuadFacing.NEG_Y, ModelQuadFacing.Axis.Y.getFacing(false));
        assertEquals(ModelQuadFacing.POS_Z, ModelQuadFacing.Axis.Z.getFacing(true));
        assertEquals(ModelQuadFacing.NEG_Z, ModelQuadFacing.Axis.Z.getFacing(false));
        assertEquals(3, ModelQuadFacing.AXES.length);
    }
}
