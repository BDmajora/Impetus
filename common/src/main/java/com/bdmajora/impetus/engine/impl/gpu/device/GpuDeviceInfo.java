package com.bdmajora.impetus.engine.impl.gpu.device;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

// Immutable description of a GPU.
public record GpuDeviceInfo(GraphicsBackend backend,
                            String vendor,
                            String renderer,
                            String version,
                            Set<GpuDeviceFeature> supportedFeatures) implements GpuDevice {
    // Stores an immutable copy of the features and rejects a null set or null feature.
    public GpuDeviceInfo {
        supportedFeatures = copyFeatures(supportedFeatures);
    }

    // Returns an unmodifiable copy of the features.
    private static Set<GpuDeviceFeature> copyFeatures(Set<GpuDeviceFeature> features) {
        if (features.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(EnumSet.copyOf(features));
    }
}
