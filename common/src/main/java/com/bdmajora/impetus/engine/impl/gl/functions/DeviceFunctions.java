package com.bdmajora.impetus.engine.impl.gl.functions;

import java.util.Objects;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;

// Copy strategy and map strategy that one GL context uses
public record DeviceFunctions(
        // Strategy that copies byte ranges between buffers
        BufferCopyFunctions bufferCopyFunctions,
        // Strategy that maps byte ranges of a buffer
        BufferMapRangeFunctions bufferMapRangeFunctions) {
    // Rejects a null copy strategy or a null map strategy
    public DeviceFunctions {
        Objects.requireNonNull(bufferCopyFunctions, "Buffer copy functions must not be null");
        Objects.requireNonNull(bufferMapRangeFunctions, "Buffer map range functions must not be null");
    }

    // Creates the pair from the best copy strategy and then the best map strategy of the current GL context
    public DeviceFunctions(RenderDevice device) {
        this(BufferCopyFunctions.pickBest(device), BufferMapRangeFunctions.pickBest(device));
    }
}
