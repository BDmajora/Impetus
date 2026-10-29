package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class FunctionsTest {
    private final RenderDevice device = Mockito.mock(RenderDevice.class);

    private void versions(boolean supported) {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt())).thenReturn(supported);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(supported);
    }

    @Test
    void pickBestFallsBackWhenTheDriverLacksFeatures() {
        versions(true);
        assertEquals(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.CORE, BufferMapRangeFunctions.pickBest(device));
        DeviceFunctions functions = new DeviceFunctions(device);
        assertEquals(BufferCopyFunctions.CORE, functions.bufferCopyFunctions());
        assertEquals(BufferMapRangeFunctions.CORE, functions.bufferMapRangeFunctions());
        versions(false);
        assertEquals(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.MAP_FULL_AND_SLICE, BufferMapRangeFunctions.pickBest(device));
        // Either the core version or the extension alone is enough
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_copy_buffer)).thenReturn(true);
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_map_buffer_range)).thenReturn(true);
        assertEquals(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(device));
        assertEquals(BufferMapRangeFunctions.CORE, BufferMapRangeFunctions.pickBest(device));
    }
}
