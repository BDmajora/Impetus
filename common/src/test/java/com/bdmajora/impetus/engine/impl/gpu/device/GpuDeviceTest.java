package com.bdmajora.impetus.engine.impl.gpu.device;

import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferStorageFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.MultidrawFunctions;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GpuDeviceTest {
    @Test
    void captureDerivesFeaturesFromTheFunctionTable() {
        Mockito.when(TestGl.gl().glGetString(0x1F00)).thenReturn("Vendor");
        Mockito.when(TestGl.gl().glGetString(0x1F01)).thenReturn(null);
        Mockito.when(TestGl.gl().glGetString(0x1F02)).thenReturn("4.6");
        GpuDevice full = OpenGlDeviceInfo.capture(new DeviceFunctions(BufferStorageFunctions.CORE, MultidrawFunctions.CORE, BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE));
        assertEquals(EnumSet.allOf(GpuDeviceFeature.class), full.supportedFeatures());
        assertEquals("Vendor", full.vendor());
        assertEquals("", full.renderer());
        assertEquals("4.6", full.version());
        assertEquals(GraphicsBackend.OPENGL, full.backend());
        assertEquals("OpenGL", GraphicsBackend.OPENGL.getDisplayName());
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 3)).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        GpuDevice none = OpenGlDeviceInfo.capture(new DeviceFunctions(BufferStorageFunctions.NONE, MultidrawFunctions.NONE, BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.MAP_FULL_AND_SLICE));
        assertTrue(none.supportedFeatures().isEmpty());
        assertFalse(none.supports(GpuDeviceFeature.BUFFER_COPY));
        GpuDeviceInfo info = new GpuDeviceInfo(GraphicsBackend.VULKAN, "v", "r", "1", Set.of(GpuDeviceFeature.BUFFER_COPY));
        assertThrows(UnsupportedOperationException.class, () -> info.supportedFeatures().clear());
        assertTrue(info.supports(GpuDeviceFeature.BUFFER_COPY));
        assertEquals(info, new GpuDeviceInfo(GraphicsBackend.VULKAN, "v", "r", "1", Set.of(GpuDeviceFeature.BUFFER_COPY)));
        assertNotNull(info.toString());
        assertEquals(info.hashCode(), info.hashCode());
    }
}
