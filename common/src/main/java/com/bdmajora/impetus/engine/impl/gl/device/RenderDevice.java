package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;

// Device that hands out the command list and the GL functions and the capability description of the GL context
public interface RenderDevice {
    // Shared render device that engine code on the render thread draws through
    RenderDevice INSTANCE = new GLRenderDevice();

    // Returns the command list that issues commands to the device
    CommandList createCommandList();

    // Activates the shared device for engine code on the render thread
    static void enterManagedCode() {
        RenderDevice.INSTANCE.makeActive();
    }

    // Deactivates the shared device after engine code on the render thread
    static void exitManagedCode() {
        RenderDevice.INSTANCE.makeInactive();
    }

    // Marks the device active and allows its command list
    void makeActive();

    // Marks the device inactive and refuses its command list
    void makeInactive();

    // Returns the copy and map functions picked for the GL context
    DeviceFunctions getDeviceFunctions();

    // Returns the capability description of the GL context
    GpuDevice getGpuDevice();
}
