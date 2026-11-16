package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests visibility encoding and the frustum lane arithmetic.
class VisibilityEdgeTest {
    // Encodes a table where only one pair of faces sees each other.
    @Test
    void encodeSinglePair() {
        for (int from = 0; from < 6; from++) {
            for (int to = 0; to < 6; to++) {
                int f = from, t = to;
                long data = VisibilityEncoding.encode((a, b) -> a == f && b == t);
                assertEquals(1L << VisibilityEncoding.bit(from, to), data);
                assertEquals(GraphDirectionSet.of(to), VisibilityEncoding.getConnections(data));
                assertEquals(GraphDirectionSet.of(to), VisibilityEncoding.getConnections(data, GraphDirectionSet.of(from)));
                assertEquals(0, VisibilityEncoding.getConnections(data, GraphDirectionSet.ALL & ~GraphDirectionSet.of(from)));
            }
        }
        assertThrows(NullPointerException.class, () -> VisibilityEncoding.encode(null));
    }

    // Ignores incoming bits that are not directions.
    @Test
    void connectionsIgnoreStrayBits() {
        long everything = VisibilityEncoding.EVERYTHING;
        assertEquals(GraphDirectionSet.ALL, VisibilityEncoding.getConnections(everything, -1));
        assertEquals(GraphDirectionSet.ALL, VisibilityEncoding.getConnections(everything, GraphDirectionSet.ALL));
        assertEquals(0, VisibilityEncoding.getConnections(everything, 0));
        assertEquals(0, VisibilityEncoding.getConnections(everything, 0xC0));
        assertEquals(0, VisibilityEncoding.getConnections(VisibilityEncoding.NULL, GraphDirectionSet.ALL));
        assertEquals(GraphDirectionSet.ALL, VisibilityEncoding.getConnections(everything));
        assertEquals(0, VisibilityEncoding.getConnections(0L));
    }

    // Prints a header and a row per direction.
    @Test
    void stringify() {
        String text = VisibilityEncoding.stringify(VisibilityEncoding.EVERYTHING);
        assertEquals(7, text.split("\n").length);
        assertTrue(text.contains("D|xxxxxx"));
        assertTrue(VisibilityEncoding.stringify(0L).contains("U|      "));
    }

    // Computes the normalization shift for small and large coordinates.
    @Test
    void normalizationShift() {
        assertEquals(0, FastFrustumClamping.normalizationShift(0));
        assertEquals(0, FastFrustumClamping.normalizationShift(31));
        assertEquals(1, FastFrustumClamping.normalizationShift(32));
        assertEquals(5, FastFrustumClamping.normalizationShift(1023));
        assertEquals(26, FastFrustumClamping.normalizationShift(Integer.MAX_VALUE));
    }

    // Takes the lane wise maximum.
    @Test
    void hullProperties() {
        long a = FastFrustumClamping.FULL;
        assertEquals(a, FastFrustumClamping.hull(a, a));
        assertEquals(a, FastFrustumClamping.hull(a, 0L));
        assertEquals(a, FastFrustumClamping.hull(0L, a));
        assertEquals(0L, FastFrustumClamping.hull(0L, 0L));
        long x = 0b0000000101_0000000011L;
        long y = 0b0000000010_0000000111L;
        assertEquals(FastFrustumClamping.hull(x, y), FastFrustumClamping.hull(y, x));
    }

    // Keeps a view for a cell in front and closes it for the empty aperture.
    @Test
    void clipKeepsOrCloses() {
        assertNotEquals(FastFrustumClamping.EMPTY, FastFrustumClamping.clip(FastFrustumClamping.FULL, 20, 20, 20));
        assertNotEquals(FastFrustumClamping.EMPTY, FastFrustumClamping.clip(FastFrustumClamping.FULL, 31, 1, 1));
        long narrowed = FastFrustumClamping.clip(FastFrustumClamping.FULL, 31, 0, 0);
        assertNotEquals(FastFrustumClamping.EMPTY, narrowed);
        assertEquals(FastFrustumClamping.EMPTY, FastFrustumClamping.clip(narrowed, 0, 31, 31));
    }
}
