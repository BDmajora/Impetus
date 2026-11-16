package com.bdmajora.impetus.engine.impl.gpu.device;

import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests feature capture from the GL context.
class GpuDeviceTest {
    // Derives features from version and extension queries and the function table.
    @Test
    void captureDerivesFeaturesFromTheFunctionTable() {
        Mockito.when(TestGl.gl().glGetString(0x1F00)).thenReturn("Vendor");
        Mockito.when(TestGl.gl().glGetString(0x1F01)).thenReturn(null);
        Mockito.when(TestGl.gl().glGetString(0x1F02)).thenReturn("4.6");
        GpuDevice full = OpenGlDeviceInfo.capture(new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE));
        assertEquals(EnumSet.allOf(GpuDeviceFeature.class), full.supportedFeatures());
        assertEquals("Vendor", full.vendor());
        assertEquals("", full.renderer());
        assertEquals("4.6", full.version());
        assertEquals(GraphicsBackend.OPENGL, full.backend());
        assertEquals("OpenGL", GraphicsBackend.OPENGL.getDisplayName());
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        GpuDevice none = OpenGlDeviceInfo.capture(new DeviceFunctions(BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.MAP_FULL_AND_SLICE));
        assertTrue(none.supportedFeatures().isEmpty());
        assertFalse(none.supports(GpuDeviceFeature.BUFFER_COPY));
        GpuDeviceInfo info = new GpuDeviceInfo(GraphicsBackend.VULKAN, "v", "r", "1", Set.of(GpuDeviceFeature.BUFFER_COPY));
        assertThrows(UnsupportedOperationException.class, () -> info.supportedFeatures().clear());
        assertTrue(info.supports(GpuDeviceFeature.BUFFER_COPY));
        assertEquals(info, new GpuDeviceInfo(GraphicsBackend.VULKAN, "v", "r", "1", Set.of(GpuDeviceFeature.BUFFER_COPY)));
        assertNotNull(info.toString());
        assertEquals(info.hashCode(), info.hashCode());
    }

    // Adds each feature group from an extension alone.
    @Test
    void extensionsAloneEnableFeatures() {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        GpuDevice device = OpenGlDeviceInfo.capture(new DeviceFunctions(BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.MAP_FULL_AND_SLICE));
        assertEquals(EnumSet.of(GpuDeviceFeature.BUFFER_STORAGE, GpuDeviceFeature.PERSISTENT_MAPPING,
                GpuDeviceFeature.MULTI_DRAW_BASE_VERTEX, GpuDeviceFeature.MULTI_DRAW_INDIRECT), device.supportedFeatures());
    }

    // Adds only the copy feature when copies are core and mapping is not.
    @Test
    void copyAndMapFeaturesAreIndependent() {
        Mockito.when(TestGl.gl().isOpenGLVersionSupported(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        assertEquals(EnumSet.of(GpuDeviceFeature.BUFFER_COPY), OpenGlDeviceInfo.capture(
                new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.MAP_FULL_AND_SLICE)).supportedFeatures());
        assertEquals(EnumSet.of(GpuDeviceFeature.BUFFER_MAP_RANGE), OpenGlDeviceInfo.capture(
                new DeviceFunctions(BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.CORE)).supportedFeatures());
    }

    // Returns independent descriptions on repeated captures.
    @Test
    void repeatedCapturesAreEqualAndIndependent() {
        DeviceFunctions functions = new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE);
        GpuDevice first = OpenGlDeviceInfo.capture(functions);
        GpuDevice second = OpenGlDeviceInfo.capture(functions);
        assertEquals(first, second);
        assertNotSame(first, second);
        assertThrows(UnsupportedOperationException.class, () -> first.supportedFeatures().remove(GpuDeviceFeature.BUFFER_COPY));
    }
}
