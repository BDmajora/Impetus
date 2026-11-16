package com.bdmajora.impetus.umbra.gl.buffer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests the sizes and parsing of shader storage buffer directives.
class StorageDefinitionTest {
    // Returns the fixed size of an absolute directive at every screen size.
    @Test
    void absoluteSizesIgnoreTheScreen() {
        ShaderStorageBufferHolder.Definition fixed = new ShaderStorageBufferHolder.Definition(0, 4096, false, 1, 1);
        assertEquals(4096, fixed.byteSize(1, 1));
        assertEquals(4096, fixed.byteSize(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    // Scales a relative size by the screen and keeps at least one pixel on each axis.
    @Test
    void relativeSizesScaleAndClamp() {
        ShaderStorageBufferHolder.Definition half = new ShaderStorageBufferHolder.Definition(1, 16, true, 0.5f, 0.25f);
        assertEquals(16L * 50 * 25, half.byteSize(100, 100));
        assertEquals(16, half.byteSize(0, 0));
        assertEquals(16, half.byteSize(-5, -5));
        ShaderStorageBufferHolder.Definition nan = new ShaderStorageBufferHolder.Definition(1, 16, true, Float.NaN, 1);
        assertEquals(16L * 10, nan.byteSize(10, 10));
    }

    // Saturates a relative size whose product overflows.
    @Test
    void overflowingSizesSaturate() {
        ShaderStorageBufferHolder.Definition huge = new ShaderStorageBufferHolder.Definition(2, Long.MAX_VALUE / 2, true, 1, 1);
        assertEquals(Long.MAX_VALUE, huge.byteSize(3, 1));
        ShaderStorageBufferHolder.Definition wide = new ShaderStorageBufferHolder.Definition(2, 1L << 40, true, 1000, 1000);
        assertEquals(Long.MAX_VALUE, wide.byteSize(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    // Parses absolute and relative directives and skips malformed ones.
    @Test
    void parsesDirectives() {
        Map<Integer, ShaderStorageBufferHolder.Definition> parsed = ShaderStorageBufferHolder.parseDefinitions(Map.of(
                "bufferObject.0", "1024",
                "bufferObject.3", "8 true 0.5 0.5",
                "bufferObject.x", "8",
                "bufferObject.4", "",
                "other.1", "8"));
        assertEquals(2, parsed.size());
        assertEquals(1024, parsed.get(0).byteSize(1, 1));
        assertEquals(8L * 2 * 2, parsed.get(3).byteSize(4, 4));
    }
}
