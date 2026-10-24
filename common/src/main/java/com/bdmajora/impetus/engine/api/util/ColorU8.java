package com.bdmajora.impetus.engine.api.util;

// Constants and conversions for colours with 8 bit components
public interface ColorU8 {
    // Number of bits in each colour component
    int COMPONENT_BITS = 8;

    // Mask that keeps one colour component
    int COMPONENT_MASK = (1 << COMPONENT_BITS) - 1;

    // Largest value of a colour component as a float
    float COMPONENT_RANGE = (float) COMPONENT_MASK;

    // Reciprocal of the component range rounded to a float
    float COMPONENT_RANGE_INVERSE = 1.0f / COMPONENT_RANGE;

    // Converts a normalised float to a component between 0 and 255 by truncation and clamps values outside the range with NaN giving 0
    static int normalizedFloatToByte(float value) {
        return Math.max(0, Math.min((int) (value * COMPONENT_RANGE), COMPONENT_MASK));
    }

    // Converts the low byte of an integer to a normalised float between 0 and 1
    static float byteToNormalizedFloat(int value) {
        return (float) ((value & COMPONENT_MASK) * (1.0 / COMPONENT_RANGE));
    }
}