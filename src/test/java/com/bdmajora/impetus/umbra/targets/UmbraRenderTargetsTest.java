package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

// Tests color target names and the teardown of the render targets.
class UmbraRenderTargetsTest {
    // Maps every in range color target name and rejects the rest.
    @Test
    void colorTargetNamesStayInRange() {
        assertEquals(0, UmbraRenderTargets.colorTargetIndex("colortex0"));
        assertEquals(15, UmbraRenderTargets.colorTargetIndex("colortex15"));
        assertNull(UmbraRenderTargets.colorTargetIndex("colortex16"));
        assertNull(UmbraRenderTargets.colorTargetIndex("colortex-1"));
        assertNull(UmbraRenderTargets.colorTargetIndex("colortex2147483648"));
        assertNull(UmbraRenderTargets.colorTargetIndex("colortex"));
        assertNull(UmbraRenderTargets.colorTargetIndex("colortexA"));
        assertEquals(4, UmbraRenderTargets.colorTargetIndex("gaux1"));
        assertEquals(7, UmbraRenderTargets.colorTargetIndex("gaux4"));
        assertNull(UmbraRenderTargets.colorTargetIndex("gaux5"));
        assertNull(UmbraRenderTargets.colorTargetIndex(""));
    }

    // Deletes every texture after a failed delete and rethrows that failure once.
    @Test
    void teardownContinuesAfterAFailure() {
        UmbraRenderTargets targets = new UmbraRenderTargets(64, 32);
        targets.getOrCreate(0);
        targets.getOrCreate(3);
        int hand = targets.getDepthTextureNoHand().getTextureId();
        IllegalStateException failure = new IllegalStateException("delete");
        doThrow(failure).when(TestGl.gl()).glDeleteTextures(anyInt());
        assertSame(failure, assertThrows(IllegalStateException.class, targets::destroy));
        verify(TestGl.gl(), atLeast(1)).glDeleteTextures(hand);
        reset(TestGl.gl());
        targets.destroy();
        assertThrows(IllegalStateException.class, () -> targets.getOrCreate(1));
    }

    // Keeps the default size and applies relative and absolute custom sizes of at least one pixel.
    @Test
    void customSizes() {
        UmbraRenderTargets targets = new UmbraRenderTargets(100, 50);
        targets.setColorSize(1, 0.5f, 0.001f, new boolean[]{true, true});
        targets.setColorSize(2, 7, 9, new boolean[]{false, false});
        assertEquals(100, targets.getWidth(0));
        assertEquals(50, targets.getWidth(1));
        assertEquals(1, targets.getHeight(1));
        assertEquals(7, targets.getWidth(2));
        assertEquals(9, targets.getHeight(2));
        assertTrue(targets.hasCustomSize(2));
        assertFalse(targets.hasCustomSize(0));
        targets.getOrCreate(2);
        assertThrows(IllegalStateException.class, () -> targets.setColorSize(2, 1, 1, new boolean[]{false, false}));
        targets.destroy();
    }
}
