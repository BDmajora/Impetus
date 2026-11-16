package com.bdmajora.impetus.engine.impl.gpu.device;

import java.util.Set;

// Description of a GPU and its supported features.
public interface GpuDevice {
    // Returns the graphics API of the device.
    GraphicsBackend backend();

    // Returns the vendor name.
    String vendor();

    // Returns the renderer name.
    String renderer();

    // Returns the API version text.
    String version();

    // Returns the supported features.
    Set<GpuDeviceFeature> supportedFeatures();

    // Returns whether the device supports the feature.
    default boolean supports(GpuDeviceFeature feature) {
        return this.supportedFeatures().contains(feature);
    }
}
