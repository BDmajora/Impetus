package com.bdmajora.impetus.umbra.gl.uniform;

// How often a pack uniform is uploaded.
public enum UniformUpdateFrequency {
    // Updated on every draw.
    DYNAMIC,
    // Updated once when the program is built.
    ONCE,
    // Once per game tick.
    PER_TICK,
    // Once per frame.
    PER_FRAME
}
