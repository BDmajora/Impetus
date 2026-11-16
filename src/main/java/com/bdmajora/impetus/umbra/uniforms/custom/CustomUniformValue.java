package com.bdmajora.impetus.umbra.uniforms.custom;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

// Immutable scalar or vector value of a custom uniform expression.
public final class CustomUniformValue {
    // Components of the value.
    public final float[] components;
    // Number of components.
    public final int width;

    // Wraps a component array.
    private CustomUniformValue(float[] components) {
        this.components = components;
        this.width = components.length;
    }

    // Returns a one component value.
    public static CustomUniformValue scalar(float value) {
        return new CustomUniformValue(new float[] { value });
    }

    // Returns one for true and zero for false.
    public static CustomUniformValue bool(boolean value) {
        return scalar(value ? 1.0f : 0.0f);
    }

    // Returns a value of the components and rejects a width outside 1 to 4.
    public static CustomUniformValue of(float... components) {
        if (components.length < 1 || components.length > 4) {
            throw new IllegalArgumentException("Vector width must be 1-4, was " + components.length);
        }
        return new CustomUniformValue(components);
    }

    // Returns the first component.
    public float x() {
        return this.components[0];
    }

    // Returns whether the first component is not zero.
    public boolean asBoolean() {
        return this.components[0] != 0.0f;
    }

    // Applies an operator per component and spreads a scalar and rejects vectors of different widths.
    public static CustomUniformValue combine(CustomUniformValue a, CustomUniformValue b, DoubleBinaryOperator op) {
        if (a.width != 1 && b.width != 1 && a.width != b.width) {
            throw new IllegalArgumentException("Mismatched vector widths " + a.width + " and " + b.width);
        }
        int width = Math.max(a.width, b.width);
        float[] out = new float[width];
        for (int i = 0; i < width; i++) {
            out[i] = (float) op.applyAsDouble(a.width == 1 ? a.components[0] : a.components[i],
                    b.width == 1 ? b.components[0] : b.components[i]);
        }
        return new CustomUniformValue(out);
    }

    // Applies an operator to every component.
    public CustomUniformValue map(DoubleUnaryOperator op) {
        float[] out = new float[this.width];
        for (int i = 0; i < this.width; i++) {
            out[i] = (float) op.applyAsDouble(this.components[i]);
        }
        return new CustomUniformValue(out);
    }

    // Returns the value as a GLSL vector literal.
    @Override
    public String toString() {
        var sb = new StringBuilder("vec").append(this.width).append('(');
        for (int i = 0; i < this.width; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(this.components[i]);
        }
        return sb.append(')').toString();
    }
}
