package com.bdmajora.impetus.engine.impl.gpu.device;

// Optional GPU capabilities the engine checks for.
public enum GpuDeviceFeature {
    // Immutable buffer storage.
    BUFFER_STORAGE,
    // Mappings that stay valid while the buffer is in use.
    PERSISTENT_MAPPING,
    // Multi draw calls with a base vertex.
    MULTI_DRAW_BASE_VERTEX,
    // Multi draw calls that read their commands from a buffer.
    MULTI_DRAW_INDIRECT,
    // Copies between buffers on the GPU.
    BUFFER_COPY,
    // Mapping of a range of a buffer.
    BUFFER_MAP_RANGE
}
