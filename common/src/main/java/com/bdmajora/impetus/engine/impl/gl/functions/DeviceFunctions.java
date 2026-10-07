package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;

public record DeviceFunctions(BufferCopyFunctions bufferCopyFunctions,
                              BufferMapRangeFunctions bufferMapRangeFunctions) {
    public DeviceFunctions(RenderDevice device) {
        this(
                BufferCopyFunctions.pickBest(device),
                BufferMapRangeFunctions.pickBest(device)
        );
    }
}
