package com.bdmajora.impetus.umbra.uniforms.custom;

import com.bdmajora.impetus.umbra.gl.uniform.FloatSupplier;
import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Inputs that custom uniforms can read.
public final class CustomUniformInputs implements UniformCollector {
    private final Map<String, Supplier<CustomUniformValue>> inputs = new HashMap<>();

    // Returns the value of an input.
    public CustomUniformValue resolve(String name) {
        Supplier<CustomUniformValue> supplier = this.inputs.get(name);
        return supplier != null ? supplier.get() : null;
    }

    // Returns the has.
    public boolean has(String name) {
        return this.inputs.containsKey(name);
    }

    // Records a float input.
    @Override
    public UniformCollector uniform1f(UniformUpdateFrequency frequency, String uniformName, FloatSupplier value) {
        this.inputs.put(uniformName, () -> CustomUniformValue.scalar(value.getAsFloat()));
        return this;
    }

    // Records an int input.
    @Override
    public UniformCollector uniform1i(UniformUpdateFrequency frequency, String uniformName, IntSupplier value) {
        this.inputs.put(uniformName, () -> CustomUniformValue.scalar(value.getAsInt()));
        return this;
    }

    // Records a two float input.
    @Override
    public UniformCollector uniform2f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2f> value) {
        this.inputs.put(uniformName, () -> {
            Vector2f v = value.get();
            return CustomUniformValue.of(v.x, v.y);
        });
        return this;
    }

    // Records a two int input.
    @Override
    public UniformCollector uniform2i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2i> value) {
        this.inputs.put(uniformName, () -> {
            Vector2i v = value.get();
            return CustomUniformValue.of(v.x, v.y);
        });
        return this;
    }

    // Records a three float input.
    @Override
    public UniformCollector uniform3f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3f> value) {
        this.inputs.put(uniformName, () -> {
            Vector3f v = value.get();
            return CustomUniformValue.of(v.x, v.y, v.z);
        });
        return this;
    }

    // Records a three int input.
    @Override
    public UniformCollector uniform3i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3i> value) {
        this.inputs.put(uniformName, () -> {
            Vector3i v = value.get();
            return CustomUniformValue.of(v.x, v.y, v.z);
        });
        return this;
    }

    // Records a four float input.
    @Override
    public UniformCollector uniform4f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4f> value) {
        this.inputs.put(uniformName, () -> {
            Vector4f v = value.get();
            return CustomUniformValue.of(v.x, v.y, v.z, v.w);
        });
        return this;
    }

    // Records a four int input.
    @Override
    public UniformCollector uniform4i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4i> value) {
        this.inputs.put(uniformName, () -> {
            Vector4i v = value.get();
            return CustomUniformValue.of(v.x, v.y, v.z, v.w);
        });
        return this;
    }

    // Records a three by three matrix input.
    @Override
    public UniformCollector uniformMatrix3(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix3fc> value) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                final int index = column * 3 + row;
                this.inputs.put(uniformName + "." + column + "." + row, () -> {
                    Matrix3fc matrix = value.get();
                    if (matrix == null) {
                        return CustomUniformValue.scalar(0.0f);
                    }
                    return CustomUniformValue.scalar(matrix.get(new float[9])[index]);
                });
            }
        }
        return this;
    }

    // Records a four by four matrix input.
    @Override
    public UniformCollector uniformMatrix(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix4fc> value) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                final int index = column * 4 + row;
                this.inputs.put(uniformName + "." + column + "." + row, () -> {
                    Matrix4fc matrix = value.get();
                    if (matrix == null) {
                        return CustomUniformValue.scalar(0.0f);
                    }
                    return CustomUniformValue.scalar(matrix.get(new float[16])[index]);
                });
            }
        }
        return this;
    }
}
