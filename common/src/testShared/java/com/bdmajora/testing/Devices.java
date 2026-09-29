package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;

// Hands tests the active shared render device; the host resetter is captured once here before any test swaps it out
public final class Devices {
    public static final Runnable DEFAULT_RESETTER = GLRenderDevice.VANILLA_STATE_RESETTER;

    private Devices() {}

    // The engine reaches RenderDevice.INSTANCE directly in places, so that is the one made active
    public static GLRenderDevice active() {
        GLRenderDevice.VANILLA_STATE_RESETTER = () -> {};
        GLRenderDevice device = (GLRenderDevice) RenderDevice.INSTANCE;
        device.makeActive();
        return device;
    }
}
