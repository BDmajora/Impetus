package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Contract of the uniform collector.
public interface UniformCollector {
    // Adds a float uniform.
    UniformCollector uniform1f(UniformUpdateFrequency frequency, String uniformName, FloatSupplier value);

    // Adds an int uniform.
    UniformCollector uniform1i(UniformUpdateFrequency frequency, String uniformName, IntSupplier value);

    // Adds a two float uniform.
    UniformCollector uniform2f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2f> value);

    // Adds a two int uniform.
    UniformCollector uniform2i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector2i> value);

    // Adds a three float uniform.
    UniformCollector uniform3f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3f> value);

    // Adds a three int uniform.
    UniformCollector uniform3i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector3i> value);

    // Adds a four float uniform.
    UniformCollector uniform4f(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4f> value);

    // Adds a four int uniform.
    UniformCollector uniform4i(UniformUpdateFrequency frequency, String uniformName, Supplier<Vector4i> value);

    // Adds a three by three matrix uniform.
    UniformCollector uniformMatrix3(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix3fc> value);

    // Adds a four by four matrix uniform.
    UniformCollector uniformMatrix(UniformUpdateFrequency frequency, String uniformName, Supplier<Matrix4fc> value);
}
