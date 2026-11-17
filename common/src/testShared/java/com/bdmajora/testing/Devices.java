package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;

// Hands tests the active shared render device
public final class Devices {
    private Devices() {}

    // Makes the device active.
    public static GLRenderDevice active() {
        GLRenderDevice device = (GLRenderDevice) RenderDevice.INSTANCE;
        device.makeActive();
        return device;
    }
}
