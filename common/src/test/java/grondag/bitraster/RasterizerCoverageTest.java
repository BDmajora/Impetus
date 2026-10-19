package grondag.bitraster;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static grondag.bitraster.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

class RasterizerCoverageTest {
    private static final int[] QUAD = {V000, V001, V010, V011};

    @Test
    void allEdgeDispatchEntriesIntersectTheirHalfPlanes() {
        OrthoRasterizer raster = new OrthoRasterizer(64, 64);
        int[] roles = {EVENT_0_LEFT, EVENT_0_RIGHT, EVENT_0_FLAT};
        int cases = 0;
        for (int a : roles) for (int b : roles) for (int c : roles) for (int d : roles) {
            int[] edgeRoles = {a, b, c, d};
            int key = a | (b << 2) | (c << 4) | (d << 6);
            raster.pos0 = raster.pos1 = raster.pos2 = raster.pos3 = EDGE_POINT;
            raster.eventY0 = 2;
            raster.eventLimit = 18;
            int expectedLeft = 0;
            int expectedRight = raster.pixelWidth;
            for (int edge = 0; edge < 4; edge++) {
                int x = 10 + edge * 7;
                raster.edgeX0[edge] = (long) x << 20;
                raster.edgeStep[edge] = 1L << 20;
                if (edgeRoles[edge] == EVENT_0_LEFT) expectedLeft = Math.max(expectedLeft, x);
                if (edgeRoles[edge] == EVENT_0_RIGHT) expectedRight = Math.min(expectedRight, x);
            }
            assertNotNull(raster.EVENT_FILLERS[key]);
            raster.EVENT_FILLERS[key].apply();
            for (int y = 2; y <= 9; y++) {
                assertEquals(expectedLeft == 0 ? 0 : expectedLeft + y, raster.eventData[y * 2], "left case " + key);
                assertEquals(expectedRight == raster.pixelWidth ? expectedRight : expectedRight + y, raster.eventData[y * 2 + 1], "right case " + key);
            }
            cases++;
        }
        assertEquals(81, cases);
    }

    private static void projected(AbstractRasterizer raster, int slot, int x, int y, float z) {
        raster.vertexData[slot + PV_PX] = x * 16;
        raster.vertexData[slot + PV_PY] = y * 16;
        raster.vertexData[slot + PV_X] = Float.floatToRawIntBits((x - 256) / 256f);
        raster.vertexData[slot + PV_Y] = Float.floatToRawIntBits((y - 256) / 256f);
        raster.vertexData[slot + PV_Z] = Float.floatToRawIntBits(z);
        raster.vertexData[slot + PV_W] = Float.floatToRawIntBits(1);
    }

    private static void rectangle(AbstractRasterizer raster) {
        projected(raster, QUAD[0], 64, 64, 1);
        projected(raster, QUAD[1], 192, 64, 1);
        projected(raster, QUAD[2], 192, 192, 1);
        projected(raster, QUAD[3], 64, 192, 1);
    }

    @Test
    void orthographicQuadsDrawAndTestTheirInteriorWithoutHidingAdjacentPixels() {
        OrthoRasterizer raster = new OrthoRasterizer(64, 64);
        assertNotNull(new OrthoRasterizer());
        Matrix4L identity = new Matrix4L();
        identity.loadIdentity();
        raster.setMatrix(identity);
        raster.setupVertex(V000, 0, 0, 0);
        assertTrue(raster.isPointClear(0, 0, 0));
        assertTrue(raster.setupBoxVertices(QUAD, 0, 0, 0, 1, 1, 1));
        rectangle(raster);
        assertTrue(raster.isQuadPartiallyClear(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
        assertFalse(raster.isQuadPartiallyOccluded(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
        raster.drawQuad(QUAD[0], QUAD[1], QUAD[2], QUAD[3]);
        assertTrue(raster.isPixelSet(100, 100));
        assertFalse(raster.isPixelSet(10, 10));
        assertTrue(raster.isQuadPartiallyOccluded(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
        raster.drawPixel(9, 10);
        assertFalse(raster.isPixelClear(9, 10));
        assertTrue(raster.isPixelClear(10, 10));
        OrthoRasterizer copy = new OrthoRasterizer();
        copy.copyFrom(raster);
        assertTrue(copy.isPixelSet(100, 100));
        assertTrue(copy.isPixelSet(9, 10));
        Arrays.fill(raster.tiles, -1L);
        Arrays.fill(raster.fullBits, -1L);
        assertFalse(raster.isQuadPartiallyClear(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
    }

    @Test
    void aSingleUncoveredPixelKeepsTheQuadVisibleUntilItIsFilled() {
        OrthoRasterizer raster = new OrthoRasterizer(64, 64);
        rectangle(raster);
        Arrays.fill(raster.tiles, -1L);
        Arrays.fill(raster.fullBits, -1L);
        int index = raster.tileIndexFromPixelXY(64, 64);
        raster.tiles[index] &= ~Indexer.pixelMask(64, 64);
        raster.fullBits[index >>> 6] &= ~(1L << (index & 63));
        assertTrue(raster.isQuadPartiallyClear(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
        raster.drawQuad(QUAD[0], QUAD[1], QUAD[2], QUAD[3]);
        assertFalse(raster.isQuadPartiallyClear(QUAD[0], QUAD[1], QUAD[2], QUAD[3]));
    }

    @Test
    void perspectiveNearClippingHandlesEveryContiguousInsideOutsidePattern() {
        PerspectiveRasterizer raster = new PerspectiveRasterizer(64, 64);
        Matrix4L identity = new Matrix4L();
        identity.loadIdentity();
        raster.setMatrix(identity);
        raster.setupVertex(V000, 0, 0, 1);
        assertEquals(0, raster.needsNearClip(V000));
        for (int mask = 0; mask < 16; mask++) {
            if (mask == 5 || mask == 10) continue;
            rectangle(raster);
            for (int i = 0; i < 4; i++) {
                raster.vertexData[QUAD[i] + PV_Z] = Float.floatToRawIntBits((mask & (1 << (3 - i))) == 0 ? 1 : -1);
            }
            int result = raster.prepareBounds(QUAD[0], QUAD[1], QUAD[2], QUAD[3]);
            assertEquals(mask == 15 ? BOUNDS_OUTSIDE_OR_TOO_SMALL : BOUNDS_IN, result, "clip mask " + mask);
            if (mask != 15) {
                assertTrue(raster.minPixelX >= 0 && raster.maxPixelX < raster.pixelWidth);
                assertTrue(raster.minPixelY >= 0 && raster.maxPixelY < raster.pixelHeight);
                assertTrue(raster.minPixelX <= raster.maxPixelX);
            }
        }
    }
}
