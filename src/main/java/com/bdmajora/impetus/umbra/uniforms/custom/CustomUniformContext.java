package com.bdmajora.impetus.umbra.uniforms.custom;

// Contract of the custom uniform context.
public interface CustomUniformContext {
    // Returns the value of a name.
    CustomUniformValue resolve(String name);

    // Returns the state of a smooth call.
    SmoothState smoothState(int index);

    // Returns the frame time.
    float frameTime();

    // State of a smooth function.
    final class SmoothState {
        // Flag set after the first value.
        public boolean initialized;
        // Smoothed value.
        public float value;
    }
}
