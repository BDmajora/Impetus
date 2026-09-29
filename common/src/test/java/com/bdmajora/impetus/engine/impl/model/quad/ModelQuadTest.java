package com.bdmajora.impetus.engine.impl.model.quad;

import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ModelQuadTest {
    // A unit quad on the top face in canonical order
    public static ModelQuad top() {
        ModelQuad quad = new ModelQuad();
        float[][] v = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        for (int i = 0; i < 4; i++) {
            quad.setX(i, v[i][0]);
            quad.setY(i, v[i][1]);
            quad.setZ(i, v[i][2]);
            quad.setColor(i, -1);
            quad.setTexU(i, i * 0.25f);
            quad.setTexV(i, 1 - i * 0.25f);
            quad.setLight(i, 0xF000F0);
        }
        quad.setLightFace(ModelQuadFacing.POS_Y);
        return quad;
    }

    @Test
    void storesVertexDataAndDerivesNormals() {
        ModelQuad quad = top();
        assertEquals(1f, quad.getX(2));
        assertEquals(1f, quad.getY(2));
        assertEquals(1f, quad.getZ(2));
        assertEquals(-1, quad.getColor(0));
        assertEquals(0.5f, quad.getTexU(2));
        assertEquals(0.5f, quad.getTexV(2));
        assertEquals(0xF000F0, quad.getLight(1));
        assertEquals(ModelQuadFacing.POS_Y, quad.getLightFace());
        assertEquals(ModelQuadFacing.POS_Y, quad.getNormalFace());
        int computed = quad.getComputedFaceNormal();
        assertEquals(1f, NormI8.unpackY(computed), 0.01f);
        assertEquals(computed, quad.getComputedFaceNormal());
        assertEquals(computed, quad.getModFaceNormal());
        assertEquals(0, quad.getForgeNormal(0));
        quad.setForgeNormal(0, 0x7F);
        assertEquals(0x7F, quad.getForgeNormal(0));
        assertEquals(0x7F, quad.getModFaceNormal());
        quad.setFlags(5);
        assertEquals(5, quad.getFlags());
        Object sprite = new Object();
        quad.setSprite(sprite);
        assertSame(sprite, quad.impetus$getSprite());
        quad.setColorIndex(2);
        assertEquals(2, quad.getColorIndex());
        assertTrue(quad.hasColor());
        quad.setColorIndex(-1);
        assertFalse(quad.hasColor());
        assertTrue(quad.hasAmbientOcclusion());
        quad.setHasAmbientOcclusion(false);
        assertFalse(quad.hasAmbientOcclusion());
        assertEquals(0, quad.getVanillaLightEmission());
        ModelQuadView view = org.mockito.Mockito.mock(ModelQuadView.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertTrue(view.hasAmbientOcclusion());
        assertEquals(0, view.getVanillaLightEmission());
        assertTrue(view.hasColor());
        assertThrows(IllegalArgumentException.class, () -> quad.setLightFace(ModelQuadFacing.UNASSIGNED));
        quad.setY(0, 0.5f);
        assertNotEquals(computed, quad.getComputedFaceNormal());
    }
}
