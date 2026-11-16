package com.bdmajora.impetus.engine.impl.gl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests vertex range validation.
class VertexRangeTest {
    // Stores start and count and compares by value.
    @Test
    void storesValues() {
        VertexRange range = new VertexRange(3, 4);
        assertEquals(3, range.vertexStart());
        assertEquals(4, range.vertexCount());
        assertEquals(range, new VertexRange(3, 4));
        assertEquals(range.hashCode(), new VertexRange(3, 4).hashCode());
        assertNotEquals(range, new VertexRange(4, 3));
        assertNotNull(range.toString());
    }

    // Accepts zero and maximum values.
    @Test
    void acceptsLimits() {
        assertEquals(0, new VertexRange(0, 0).vertexCount());
        assertEquals(Integer.MAX_VALUE, new VertexRange(Integer.MAX_VALUE, Integer.MAX_VALUE).vertexStart());
    }

    // Rejects negative start or count.
    @Test
    void rejectsNegatives() {
        assertThrows(IllegalArgumentException.class, () -> new VertexRange(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new VertexRange(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new VertexRange(Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    // Rejects a negative value paired with every limit of the other value.
    @Test
    void rejectsEveryNegativePairing() {
        for (int other : new int[]{0, 1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE}) {
            int value = other;
            assertThrows(IllegalArgumentException.class, () -> new VertexRange(-1, value));
            assertThrows(IllegalArgumentException.class, () -> new VertexRange(value, Integer.MIN_VALUE));
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new VertexRange(-5, 7));
        assertEquals("Negative vertex range -5 7", error.getMessage());
        assertEquals(new VertexRange(0, Integer.MAX_VALUE), new VertexRange(0, Integer.MAX_VALUE));
    }
}
