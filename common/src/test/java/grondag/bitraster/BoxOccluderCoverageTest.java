package grondag.bitraster;

import org.junit.jupiter.api.Test;

import static grondag.bitraster.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoxOccluderCoverageTest {
    private static final class Occluder extends BoxOccluder {
        Occluder(AbstractRasterizer raster) { super(raster); }
        @Override public boolean isBoxVisible(int box, int fuzz) { return isBoxVisibleFromPerspective(box, fuzz); }
        @Override public void occludeBox(int box) { occludeFromPerspective(box); }
    }

    @Test
    void everyCameraOctantDispatchesOnlyValidBoxCornersAndCombinesQuadResults() {
        AbstractRasterizer raster = mock(AbstractRasterizer.class);
        Occluder occluder = new Occluder(raster);
        doAnswer(inv -> {
            int corner = inv.<Integer>getArgument(0) / PROJECTED_VERTEX_STRIDE;
            assertTrue(corner >= 0 && corner < 8);
            assertEquals((corner & 4) == 0 ? 1 : 4, inv.<Integer>getArgument(1));
            assertEquals((corner & 2) == 0 ? 2 : 5, inv.<Integer>getArgument(2));
            assertEquals((corner & 1) == 0 ? 3 : 6, inv.<Integer>getArgument(3));
            return null;
        }).when(raster).setupVertex(anyInt(), anyInt(), anyInt(), anyInt());
        for (int x : new int[] {0, EAST, WEST}) for (int y : new int[] {0, UP, DOWN}) for (int z : new int[] {0, NORTH, SOUTH}) {
            int mask = x | y | z;
            when(raster.isQuadPartiallyClear(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(false);
            assertFalse(occluder.partiallyClearTests[mask].apply(1, 2, 3, 4, 5, 6));
            when(raster.isQuadPartiallyClear(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
            assertEquals(mask != 0, occluder.partiallyClearTests[mask].apply(1, 2, 3, 4, 5, 6));
            when(raster.isQuadPartiallyOccluded(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(false);
            assertFalse(occluder.partiallyOccludedTests[mask].apply(1, 2, 3, 4, 5, 6));
            when(raster.isQuadPartiallyOccluded(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
            assertEquals(mask != 0, occluder.partiallyOccludedTests[mask].apply(1, 2, 3, 4, 5, 6));
            clearInvocations(raster);
            occluder.boxDraws[mask].apply(1, 2, 3, 4, 5, 6);
            if (mask == 0) verify(raster, never()).drawQuad(anyInt(), anyInt(), anyInt(), anyInt());
            else verify(raster, atLeastOnce()).drawQuad(anyInt(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    void copiesRetainTheSceneAndInvalidationAdvancesItsVersion() {
        Occluder source = new Occluder(new PerspectiveRasterizer(64, 64));
        assertTrue(source.prepareScene(1, 0, 0, 0, Matrix4L::loadIdentity, Matrix4L::loadIdentity));
        int version = source.occlusionVersion();
        source.prepareRegion(0, 0, 8, 0, 1);
        source.drawNearOccluders(false);
        assertFalse(source.hasNearOccluders());
        assertFalse(source.isEmptyRegionVisible(10000, 10000, 8, 0));
        Occluder copy = new Occluder(new PerspectiveRasterizer());
        copy.copyFrom(source);
        assertEquals(version, copy.occlusionVersion());
        assertEquals(source.toString(), copy.toString());
        assertFalse(copy.prepareScene(1, 0, 0, 0, Matrix4L::loadIdentity, Matrix4L::loadIdentity));
        copy.invalidate();
        assertTrue(copy.prepareScene(1, 0, 0, 0, Matrix4L::loadIdentity, Matrix4L::loadIdentity));
        assertEquals(version + 1, copy.occlusionVersion());
    }
}
