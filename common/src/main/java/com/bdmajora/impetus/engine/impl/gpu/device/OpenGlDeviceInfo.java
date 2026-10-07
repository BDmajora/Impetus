package com.bdmajora.impetus.engine.impl.gpu.device;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.lwjgl.GLExtension;

import java.util.EnumSet;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public final class OpenGlDeviceInfo {
    private OpenGlDeviceInfo() {
    }

    // Builds a device description from GL capability queries
    public static GpuDevice capture(DeviceFunctions functions) {
        GlContextInfo context = GlContextInfo.capture();
        EnumSet<GpuDeviceFeature> features = EnumSet.noneOf(GpuDeviceFeature.class);

        if (LWJGL.isOpenGLVersionSupported(4, 4) || LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage)) {
            features.add(GpuDeviceFeature.BUFFER_STORAGE);
            features.add(GpuDeviceFeature.PERSISTENT_MAPPING);
        }

        if (LWJGL.isOpenGLVersionSupported(3, 2) || LWJGL.isExtensionSupported(GLExtension.ARB_draw_elements_base_vertex)) {
            features.add(GpuDeviceFeature.MULTI_DRAW_BASE_VERTEX);
        }

        if (LWJGL.isOpenGLVersionSupported(4, 3) || LWJGL.isExtensionSupported(GLExtension.ARB_multi_draw_indirect)) {
            features.add(GpuDeviceFeature.MULTI_DRAW_INDIRECT);
        }

        if (functions.bufferCopyFunctions() == BufferCopyFunctions.CORE) {
            features.add(GpuDeviceFeature.BUFFER_COPY);
        }

        if (functions.bufferMapRangeFunctions() == BufferMapRangeFunctions.CORE) {
            features.add(GpuDeviceFeature.BUFFER_MAP_RANGE);
        }

        return new GpuDeviceInfo(GraphicsBackend.OPENGL, context.vendor(), context.renderer(), context.version(), features);
    }
}
