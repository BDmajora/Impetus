package com.bdmajora.impetus.umbra.uniforms.custom;

import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;

// Custom uniforms of the active pack.
public final class ActiveCustomUniforms {
    // Active uniforms or null.
    private static CustomUniforms active;

    // Prevents instantiation
    private ActiveCustomUniforms() {
    }

    // Stores the value.
    public static void set(CustomUniforms uniforms) {
        active = uniforms;
    }

    // Drops the active uniforms.
    public static void clear() {
        active = null;
    }

    // Recomputes the active uniforms.
    public static void update() {
        CustomUniforms current = active;
        if (current != null) {
            current.update();
        }
    }

    // Registers the active pack's custom uniforms with a program's collector.
    public static void assignTo(UniformCollector collector) {
        CustomUniforms current = active;
        if (current != null) {
            current.assignTo(collector);
        }
    }

    // Returns the current values by name.
    public static java.util.Map<String, String> snapshot() {
        CustomUniforms current = active;
        return current == null ? java.util.Collections.emptyMap() : current.snapshot();
    }
}
