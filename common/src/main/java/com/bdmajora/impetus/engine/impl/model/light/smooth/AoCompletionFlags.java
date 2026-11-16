package com.bdmajora.impetus.engine.impl.model.light.smooth;

// Bit flags of the light properties computed for a face.
class AoCompletionFlags {
    // Light data was read from the cache.
    public static final int HAS_LIGHT_DATA = 0b01;

    // Light data was unpacked into floats.
    public static final int HAS_UNPACKED_LIGHT_DATA = 0b10;
}
